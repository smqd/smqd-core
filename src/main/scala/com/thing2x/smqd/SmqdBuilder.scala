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

import akka.actor.{ActorSystem, Address, AddressFromURIString}
import akka.cluster.Cluster
import com.thing2x.smqd.discovery.{EtcdClusterDiscovery, FailedClusterDiscovery, ManualClusterDiscovery, StaticClusterDiscovery}
import com.thing2x.smqd.registry.RegistryDelegate
import com.thing2x.smqd.util.ClassLoading
import com.typesafe.config.Config

import scala.collection.JavaConverters._
import scala.concurrent.duration._
import scala.concurrent.{Await, ExecutionContext}
import scala.util.Success

// 2018. 6. 12. - Created by Kwon, Yeong Eon

/**
  * smqd instance builder
  */
object SmqdBuilder {
  def apply(config: Config): SmqdBuilder = new SmqdBuilder(config)
}

class SmqdBuilder(config: Config) extends ClassLoading {

  private var clientDelegate: ClientDelegate = _
  private var registryDelegate: RegistryDelegate = _
  private var sessionStoreDelegate: SessionStoreDelegate = _
  private var userDelegate: UserDelegate = _

  private var system: ActorSystem = _

  private var serviceDefs: Map[String, Config] = _

  def setUserDelegate(userDelegate: UserDelegate): SmqdBuilder = {
    this.userDelegate = userDelegate
    this
  }

  def setClientDelegate(clientDelegate: ClientDelegate): SmqdBuilder = {
    this.clientDelegate = clientDelegate
    this
  }

  def setRegistryDelegate(registryDelegate: RegistryDelegate): SmqdBuilder = {
    this.registryDelegate = registryDelegate
    this
  }

  def setSessionStoreDelegate(sessionStoreDelegate: SessionStoreDelegate): SmqdBuilder = {
    this.sessionStoreDelegate = sessionStoreDelegate
    this
  }

  def setActorSystem(system: ActorSystem): SmqdBuilder = {
    this.system = system
    this
  }

  def setServices(serviceDefs: Map[String, Config]): SmqdBuilder = {
    this.serviceDefs = serviceDefs
    this
  }

  def build(): Smqd = {
    var isClusterMode = false

    if (system == null) {
      // create actor system
      system = ActorSystem.create(config.getString("smqd.actor_system_name"), config)
      isClusterMode = system.settings.ProviderClass match {
        case "akka.cluster.ClusterActorRefProvider" => true
        case _ => false
      }

      // joining cluster
      if (isClusterMode) {
        // cluster discovery is blocking operation as intended
        val seeds = waitClusterDiscovery(system, config)
        val cluster = Cluster(system)
        if (seeds.isEmpty)
          throw new IllegalStateException("Clsuter seeds not found")
        cluster.joinSeedNodes(seeds.toList)
      }
    }
    else {
      isClusterMode = system.settings.ProviderClass match {
        case "akka.cluster.ClusterActorRefProvider" => true
        case _ => false
      }
    }

    if (isClusterMode) {
      logger.info("Clustering is enabled")
    }
    else {
      logger.info("Clustering is disabled")
    }

    if (userDelegate == null && config.hasPath("smqd.delegates.user"))
      userDelegate = loadCustomClass[UserDelegate](config.getString("smqd.delegates.user"))
    if (clientDelegate == null && config.hasPath("smqd.delegates.client"))
      clientDelegate = loadCustomClass[ClientDelegate](config.getString("smqd.delegates.client"))
    if (registryDelegate == null && config.hasPath("smqd.delegates.registry"))
      registryDelegate = loadCustomClass[RegistryDelegate](config.getString("smqd.delegates.registry"))
    if (sessionStoreDelegate == null && config.hasPath("smqd.delegates.sessionstore"))
      sessionStoreDelegate = loadCustomClass[SessionStoreDelegate](config.getString("smqd.delegates.sessionstore"))

    //// load services
    serviceDefs = if (this.serviceDefs == null || serviceDefs.isEmpty) {
      val serviceNames = if (this.serviceDefs == null) config.getStringList("smqd.services").asScala else Nil
      logger.debug("Services try loading service config: {}", serviceNames.mkString("[", ", ", "]"))
      Map( serviceNames.map { sname =>
        val sconf = config.getConfig("smqd."+sname)
        sname -> sconf
      }: _*)
    }
    else {
      serviceDefs
    }
    logger.info("Services to load: {}", serviceDefs.map{ case (name, _) => name}.mkString(", "))

    //// create an instance
    new Smqd(config,
      system,
      serviceDefs,
      Option(userDelegate),
      Option(clientDelegate),
      Option(registryDelegate),
      Option(sessionStoreDelegate))
  }

  // cluster discovery is blocking operation as intended
  private def waitClusterDiscovery(system: ActorSystem, config: Config): Seq[Address] = {

    implicit val ec: ExecutionContext = system.dispatcher
    implicit val sys: ActorSystem = system

    val discovery = config.getString("smqd.cluster.discovery")
    val discoveryTimeout = config.getDuration("smqd.cluster.discovery_timeout").toMillis.millis
    val nodeName = config.getString("smqd.node_name")
    val selfAddress = {
      val sys  = config.getString("smqd.actor_system_name")
      val host = config.getString("akka.remote.netty.tcp.hostname")
      val port = config.getInt("akka.remote.netty.tcp.port")
      AddressFromURIString.parse(s"akka.tcp://$sys@$host:$port")
    }

    logger.info("cluster discovery mode: {}", discovery)

    val cfg = config.getConfig("smqd.cluster." + discovery)

    val seeds = discovery match {
      case "manual" =>
        new ManualClusterDiscovery(cfg).seeds
      case "static" =>
        new StaticClusterDiscovery(cfg).seeds
      case "etcd" =>
        new EtcdClusterDiscovery(cfg, nodeName, selfAddress).seeds
      case _ =>
        new FailedClusterDiscovery(cfg).seeds
    }

    Await.ready(seeds, discoveryTimeout).value.get match {
      case Success(ss) => ss
      case _ => Nil
    }
  }
}
