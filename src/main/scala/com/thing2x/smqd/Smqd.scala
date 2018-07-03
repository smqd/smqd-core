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

import akka.actor.{ActorRef, ActorSystem, Address, Props}
import akka.cluster.Cluster
import akka.dispatch.MessageDispatcher
import akka.pattern.ask
import akka.stream.{ActorMaterializer, ActorMaterializerSettings, Materializer}
import akka.util.Timeout
import com.codahale.metrics.{MetricRegistry, SharedMetricRegistries}
import com.thing2x.smqd.QoS.QoS
import com.thing2x.smqd.Smqd._
import com.thing2x.smqd.fault.FaultNotificationManager
import com.thing2x.smqd.protocol.{ProtocolNotification, ProtocolNotificationManager}
import com.thing2x.smqd.util._
import com.typesafe.config.Config
import com.typesafe.scalalogging.StrictLogging
import io.netty.buffer.ByteBuf

import scala.concurrent.duration._
import scala.concurrent.{Await, ExecutionContext, Future, Promise}
import scala.language.postfixOps

/**
  * 2018. 6. 12. - Created by Kwon, Yeong Eon
  */
class Smqd(val config: Config,
           _system: ActorSystem,
           bridgeDriverDefs: Map[String, Config],
           bridgeDefs: List[Config],
           serviceDefs: Map[String, Config],
           authDelegate: AuthDelegate,
           registryDelegate: RegistryDelegate,
           sessionStoreDelegate: SessionStoreDelegate)
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

  private val registry: Registry          = if (isClusterMode) new ClusterModeRegistry(system)  else new LocalModeRegistry(system)
  private val router: Router              = if (isClusterMode) new ClusterModeRouter()          else new LocalModeRouter(registry)
  private val retainer: Retainer          = if (isClusterMode) new ClusterModeRetainer(system)  else new LocalModeRetainer()
  private val sessionStore: SessionStore  = new SessionStore(sessionStoreDelegate)
  private lazy val requestor: Requestor   = new Requestor(this)

  private var chiefActor: ActorRef = _
  private var services: Seq[Service] = Nil
  private var bridgeDrivers: Map[String, BridgeDriver] = Map.empty

  override def start(): Unit = {

    //// start Metric Registries
    Smqd.registerMetricRegistry()

    //// actors
    try {
      chiefActor = system.actorOf(Props(classOf[ChiefActor], this, registry, router, retainer, sessionStore), ChiefActor.actorName)

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

    //// start services
    try {
      services = serviceDefs.map {
        case (cname, sconf) =>
          val className = sconf.getString("entry.class")
          val clazz = getClass.getClassLoader.loadClass(className).asInstanceOf[Class[Service]]
          val cons = clazz.getConstructor(classOf[String], classOf[Smqd], classOf[Config])
          cons.newInstance(cname, this, sconf)
      }.toSeq
      services.foreach{svc =>
        svc.start()
      }
    }
    catch {
      case ex: Throwable =>
        logger.error("Initialization failed", ex)
        System.exit(1)
    }

    //// bridge drivers
    try {
      bridgeDrivers = bridgeDriverDefs.map { case (dname, dconf) =>
        val className = dconf.getString("class")
        val clazz = getClass.getClassLoader.loadClass(className).asInstanceOf[Class[BridgeDriver]]
        val cons = clazz.getConstructor(classOf[String], classOf[Smqd], classOf[Config])
        val driver = cons.newInstance(dname, this, dconf)
        driver.start()
        dname -> driver
      }

      bridgeDefs.foreach { bconf =>
        val driverName = bconf.getString("driver")
        val topic = bconf.getString("topic")
        bridgeDrivers.get(driverName) match {
          case Some(drv) => drv.addBridge(FilterPath(topic), bconf)
          case _ => throw new IllegalArgumentException(s"driver[$driverName] not found")
        }
      }
    }
    catch {
      case ex: Throwable =>
        logger.error("Loading bridge drivers failed", ex)
        System.exit(1)
    }

    //// register shutdown hook for component stop
    scala.sys.addShutdownHook {
      stop()
    }

    //// SMQD started
    logger.info(s"SMQ (ver. $version) Ready.")
  }

  override def stop(): Unit = {
    synchronized {

      bridgeDrivers.foreach { case (_, drv) =>
        drv.stop()
      }

      services.reverse.foreach{ c =>
        try {
          c.stop()
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

  def nodes: Set[NodeInfo] = {
    def memberName(addr: Address): String = {
      if (addr.hasGlobalScope) {
        addr.hostPort
      }
      else {
        logger.debug(">> {}", system.settings.setup)
        this.nodeHostPort
      }
    }

    cluster match {
      case Some(cl) => // cluster mode
        val leaderAddress = cl.state.leader
        cl.state.members.map{ m =>
          NodeInfo(
            memberName(m.address),
            m.status.toString,
            m.roles.map(_.toString),
            m.dataCenter,
            leaderAddress match {
              case Some(addr) =>
                m.address == addr
              case _ =>
                false
            })
        }
      case None => // non-cluster mode
        Set(
          NodeInfo(
            nodeHostPort,
            "Up",
            Set.empty,
            "<non-cluster>",
            isLeader = true
          )
        )
    }
  }

  def service(name: String): Option[Service] = services.find(s => s.name == name)

  private lazy val faultManager: ActorRef = identifyActor("user/"+ChiefActor.actorName+"/"+FaultNotificationManager.actorName)(system)
  def notifyFault(fault: SmqResult): Unit = faultManager ! fault

  private lazy val protocolManager: ActorRef = identifyActor("user/"+ChiefActor.actorName+"/"+ProtocolNotificationManager.actorName)(system)
  def notifyProtocol(proto: ProtocolNotification): Unit = protocolManager ! proto

  def subscribe(filterPath: FilterPath, actor: ActorRef): Unit =
    registry.subscribe(filterPath, actor)

  def subscribe(filterPath: FilterPath, actor: ActorRef, clientId: ClientId, qos: QoS): QoS =
    registry.subscribe(filterPath, actor, Some(clientId), qos)

  def subscribe(filterPath: FilterPath, callback: (TopicPath, Any) => Unit): ActorRef =
    registry.subscribe(filterPath, callback)

  def subscribe(filterPath: FilterPath)(callback: PartialFunction[(TopicPath, Any), Unit]): ActorRef =
    registry.subscribe(filterPath)(callback)

  def unsubscribe(filterPath: FilterPath, actor: ActorRef): Boolean = unsubscribe(actor, Some(filterPath))
  def unsubscribe(actor: ActorRef, filterPath: Option[FilterPath] = None): Boolean =
    filterPath match { case Some(filter) => registry.unsubscribe(filter, actor) case _ => registry.unsubscribeAll(actor) }

  def publish(topicPath: TopicPath, msg: Any, isRetain: Boolean = false): Unit =
    router.routes(RoutableMessage(topicPath, msg, isRetain))

  def publish(rm: RoutableMessage): Unit =
    router.routes(rm)

  def snapshotRoutes: Map[FilterPath, Set[SmqdRoute]] =
    router.snapshot

  def request[T](topicPath: TopicPath, msg: Any)(implicit ec: ExecutionContext, timeout: Timeout): Future[T] =
    requestor.request(topicPath, msg)

  def retain(topicPath: TopicPath, msg: Array[Byte]): Unit =
    retainer.put(topicPath, msg)

  def unretain(topicPath: TopicPath): Unit =
    retainer.remove(topicPath)

  def retainedMessages(filterPath: FilterPath, qos: QoS): Seq[RetainedMessage] =
    retainer.filter(filterPath, qos)

  def allowSubscribe(filterPath: FilterPath, clientId: ClientId, userName: Option[String]): Future[Boolean] = {
    val p = Promise[Boolean]
    p.completeWith( registryDelegate.allowSubscribe(filterPath, clientId, userName) )
    p.future
  }

  def allowPublish(topicPath: TopicPath, clientId: ClientId, userName: Option[String]): Future[Boolean] = {
    val p = Promise[Boolean]
    p.completeWith(registryDelegate.allowPublish(topicPath, clientId, userName))
    p.future
  }

  def authenticate(clientId: ClientId, userName: Option[String], password: Option[Array[Byte]]): Future[SmqResult] = {
    val p = Promise[SmqResult]
    p.completeWith( authDelegate.authenticate(clientId, userName, password) )
    p.future
  }
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

  /**
    *
    * @param address node's address that has format as "system@ipaddress:port"
    * @param status  membership status
    * @param roles   list of roles
    * @param dataCenter data center name
    * @param isLeader true if the node is leader of the cluster
    */
  case class NodeInfo(address: String, status: String, roles: Set[String], dataCenter: String, isLeader: Boolean)
}
