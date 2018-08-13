// Copyright 2018 UANGEL
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//     http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package com.thing2x.smqd

import java.io.File

import akka.actor.{ActorRef, ActorSystem, Address, Props}
import akka.cluster.Cluster
import akka.dispatch.MessageDispatcher
import akka.pattern.ask
import akka.stream.{ActorMaterializer, ActorMaterializerSettings, Materializer}
import akka.util.Timeout
import com.codahale.metrics.{MetricRegistry, SharedMetricRegistries}
import com.thing2x.smqd.QoS.QoS
import com.thing2x.smqd.SessionStore.ClientData
import com.thing2x.smqd.UserDelegate.User
import com.thing2x.smqd.fault.FaultNotificationManager
import com.thing2x.smqd.impl.{DefaultClientDelegate, DefaultRegistryDelegate, DefaultSessionStoreDelegate, DefaultUserDelegate}
import com.thing2x.smqd.plugin.{InstanceDefinition, PluginManager, Service}
import com.thing2x.smqd.protocol.{ProtocolNotification, ProtocolNotificationManager}
import com.thing2x.smqd.util.ConfigUtil._
import com.thing2x.smqd.util._
import com.typesafe.config.Config
import com.typesafe.scalalogging.StrictLogging

import scala.concurrent.duration._
import scala.concurrent.{Await, ExecutionContext, Future}
import scala.language.postfixOps

// 2018. 6. 12. - Created by Kwon, Yeong Eon

/**
  * smqd core instance and providing APIs for embedding applications
  */
class Smqd(val config: Config,
           _system: ActorSystem,
           serviceDefs: Map[String, Config],
           userDelegateOption: Option[UserDelegate] = None,
           clientDelegateOption: Option[ClientDelegate] = None,
           registryDelegateOption: Option[RegistryDelegate] = None,
           sessionStoreDelegateOption: Option[SessionStoreDelegate] = None)
  extends LifeCycle
    with ActorIdentifying
    with JvmAware
    with AkkaSystemAware
    with StrictLogging {

  object Implicit {
    implicit val system: ActorSystem = _system
    private val materializerSettings = ActorMaterializerSettings.create(system)
    implicit val materializer: Materializer = ActorMaterializer(materializerSettings, system.name)
    implicit val gloablDispatcher: MessageDispatcher = system.dispatchers.defaultGlobalDispatcher
  }

  import Implicit._

  //logger.trace("Origin of configuration: {}", config.origin.description())

  val version: String = config.getString("smqd.version")
  val commitVersion: String = config.getString("smqd.commit-version")
  val nodeName: String = config.getString("smqd.node_name")
  val isClusterMode: Boolean = super.isClusterMode
  val cluster: Option[Cluster] = super.cluster
  val nodeHostPort: String = super.localNodeHostPort(nodeName)
  val nodeAddress: Address = super.localNodeAddress(nodeName)
  def uptime: Duration = super.uptime
  def uptimeString: String = super.uptimeString
  val tlsProvider: Option[TlsProvider] = TlsProvider(config.getOptionConfig("smqd.tls"))
  val pluginManager = PluginManager(config.getConfig("smqd.plugin"), version)

  val facilityFactory = FacilityFactory(config)

  private val userDelegate = userDelegateOption.getOrElse(facilityFactory.userDelegate)
  private val clientDelegate = clientDelegateOption.getOrElse(facilityFactory.clientDelegate)
  private val registryDelegate = registryDelegateOption.getOrElse(facilityFactory.registryDelegate)
  private val sessionStoreDelegate = sessionStoreDelegateOption.getOrElse(facilityFactory.sessionStoreDelegate)

  private val registry       = new TrieRegistry(this, config.getBoolean("smqd.registry.verbose"))
  private val router         = if (isClusterMode) new ClusterModeRouter(config.getBoolean("smqd.router.verbose"))  else new LocalModeRouter(registry)
  private val retainer       = if (isClusterMode) new ClusterModeRetainer()  else new LocalModeRetainer()
  private val sessionStore   = new SessionStore(this, sessionStoreDelegate)
  private val requestor      = new Requestor()

  private var chiefActor: ActorRef = _

  override def start(): Unit = {

    //// start Metric Registries
    Smqd.registerMetricRegistry()

    //// actors
    try {
      chiefActor = system.actorOf(Props(classOf[ChiefActor], this, requestor, registry, router, retainer, sessionStore), ChiefActor.actorName)

      implicit val readyTimeout: Timeout = 3 second
      val future = chiefActor ? ChiefActor.Ready
      Await.result(future, readyTimeout.duration) match {
        case ChiefActor.ReadyAck =>
          logger.info("ActorSystem ready.")
      }
    }
    catch {
      case ex: Throwable =>
        logger.error("ActorSystem failed", ex)
        System.exit(1)
    }

    //// core facilities and actor system are ready.
    //// then loading plugins
    ////
    //// display list of repositories for information
    pluginManager.logRepositoryDefinitions()

    try {
      //// start services
      serviceDefs.foreach { case (sname, sconf) =>
        InstanceDefinition.defineInstance(this, sname, sconf) match {
          case Some(idef) =>
            if (idef.autoStart)
              idef.instance.execStart()
          case None =>
            logger.error(s"Service not found: $sname")
        }
      }

      //// load plugin instances
      pluginManager.findInstanceConfigs.foreach { pconf =>
        pluginManager.loadInstance(this, pconf) match {
          case None =>
            logger.error(s"Plugin loading filaure...")
          case _ =>  // already started by plugin manager if plugin has auto-start=true
        }
      }
    }
    catch {
      case ex: Throwable =>
        logger.error("Initialization failed", ex)
        System.exit(1)
    }

    //// register shutdown hook for component stop
    scala.sys.addShutdownHook {
      stop()
    }

    //// SMQD started
    logger.info(s"SMQD ($version) is Ready.")
  }

  override def stop(): Unit = {
    synchronized {

      pluginManager.pluginDefinitions.reverse.flatMap(_.instances).foreach { p =>
        try {
          p.instance.execStop()
        }
        catch {
          case ex: Throwable =>
            logger.error("Stopping failed", ex)
        }
      }

      cluster match {
        case Some(cl) =>
          cl.leave(cl.selfAddress)
        case None =>
      }

      system.stop(chiefActor)
    }
  }

  private var chief: ChiefActor = _
  private[smqd] def setChiefActor(chief: ChiefActor): Unit = this.chief = chief

  private var apiEndpoint0: Option[EndpointInfo] = None
  private[smqd] def setApiEndpoint(endpoint: EndpointInfo): Unit = this.apiEndpoint0 = Option(endpoint)
  def apiEndpoint: Option[EndpointInfo] = apiEndpoint0

  def nodes: Future[Seq[NodeInfo]] = chief.nodeInfo
  def node(nodeName: String): Future[Option[NodeInfo]] = chief.nodeInfo(nodeName: String)

  def service(name: String): Option[Service] = pluginManager.servicePluginDefinitions.flatMap(_.instances).find(pi => pi.instance.name == name).map(p => p.instance.asInstanceOf[Service])

  private lazy val faultManager: ActorRef = identifyActor("user/"+ChiefActor.actorName+"/"+FaultNotificationManager.actorName)(system)
  def notifyFault(fault: SmqResult): Unit = faultManager ! fault

  private lazy val protocolManager: ActorRef = identifyActor("user/"+ChiefActor.actorName+"/"+ProtocolNotificationManager.actorName)(system)
  def notifyProtocol(proto: ProtocolNotification): Unit = protocolManager ! proto

  def snapshotSessions(search: Option[String] = None): Future[Seq[ClientData]] =
    sessionStore.snapshot(search)

  def snapshotRegistrations: Seq[Registration] =
    registry.snapshot

  def subscribe(filterPath: FilterPath, actor: ActorRef): Unit =
    registry.subscribe(filterPath, actor)

  def subscribe(filterPath: FilterPath, actor: ActorRef, clientId: ClientId, qos: QoS): QoS =
    registry.subscribe(filterPath, actor, Some(clientId), qos)

  def subscribe(filterPath: FilterPath, callback: (TopicPath, Any) => Unit): ActorRef =
    registry.subscribe(filterPath, callback)

  def subscribe(filterPath: FilterPath)(callback: PartialFunction[(TopicPath, Any), Unit]): ActorRef =
    registry.subscribe(filterPath)(callback)

  /** Java API */
  def subscribe(filterPath: FilterPath, receivable: MessageReceivable): ActorRef =
    registry.subscribe(filterPath){ case (topic, msg) => receivable.onMessage(topic, msg) }

  def unsubscribe(filterPath: FilterPath, actor: ActorRef): Boolean = unsubscribe(actor, Some(filterPath))
  def unsubscribe(actor: ActorRef, filterPath: Option[FilterPath] = None): Boolean =
    filterPath match { case Some(filter) => registry.unsubscribe(filter, actor) case _ => registry.unsubscribeAll(actor) }

  def publish(topicPath: TopicPath, message: Any, isRetain: Boolean = false): Unit =
    router.routes(RoutableMessage(topicPath, message, isRetain))

  def publish(rm: RoutableMessage): Unit =
    router.routes(rm)

  /** Java API */
  def publish(topicPath: TopicPath, message: java.lang.Object): Unit =
    router.routes(RoutableMessage(topicPath, message))

  def snapshotRoutes: Map[FilterPath, Set[SmqdRoute]] =
    router.snapshot

  private[smqd] def addRoute(filterPath: FilterPath): Unit = if (isClusterMode) router.addRoute(filterPath)
  private[smqd] def removeRoute(filterPath: FilterPath): Unit = if (isClusterMode) router.removeRoute(filterPath)

  def request[T](topicPath: TopicPath, expect: Class[T], msg: Any)(implicit ec: ExecutionContext, timeout: Timeout): Future[T] =
    requestor.request(topicPath, msg)

  def retain(topicPath: TopicPath, msg: Array[Byte]): Unit =
    retainer.put(topicPath, msg)

  def unretain(topicPath: TopicPath): Unit =
    retainer.remove(topicPath)

  def retainedMessages(filterPath: FilterPath, qos: QoS): Seq[RetainedMessage] =
    retainer.filter(filterPath, qos)

  def allowSubscribe(filterPath: FilterPath, qos: QoS, clientId: ClientId, userName: Option[String]): Future[QoS] =
    registryDelegate.allowSubscribe(filterPath, qos, clientId, userName)

  def allowPublish(topicPath: TopicPath, clientId: ClientId, userName: Option[String]): Future[Boolean] =
    registryDelegate.allowPublish(topicPath, clientId, userName)

  def clientLogin(clientId: ClientId, userName: Option[String], password: Option[Array[Byte]]): Future[SmqResult] =
    clientDelegate.clientLogin(clientId, userName, password)

  def userLogin(username: String, password: String): Future[SmqResult] =
    userDelegate.userLogin(username, password)

  def userList: Future[Seq[User]] =
    userDelegate.userList

  def userCreate(user: User): Future[SmqResult] =
    userDelegate.userCreate(user)

  def userUpdate(user: User): Future[SmqResult] =
    userDelegate.userUpdate(user)

  def userDelete(username: String): Future[SmqResult] =
    userDelegate.userDelete(username)
}

object Smqd {

  private[smqd] def registerMetricRegistry(): Unit = {
    // lock is required if multiple smqd instnaces exist in a JVM
    synchronized{
      if (SharedMetricRegistries.tryGetDefault == null) {
        SharedMetricRegistries.setDefault("smqd", new MetricRegistry())
      }
    }
  }

}
