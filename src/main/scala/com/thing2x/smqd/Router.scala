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

import java.util.concurrent.atomic.AtomicInteger

import akka.actor.{Actor, ActorRef, Props}
import akka.cluster.Cluster
import akka.cluster.ddata.Replicator._
import akka.cluster.ddata.{DistributedData, ORMultiMap, ORMultiMapKey, SelfUniqueAddress}
import com.thing2x.smqd.ChiefActor.{Ready, ReadyAck}
import com.thing2x.smqd.session.SessionActor.OutboundPublish
import com.typesafe.scalalogging.StrictLogging
import io.circe._

import scala.concurrent.duration._

// 2018. 6. 15. - Created by Kwon, Yeong Eon

trait Router {
  def snapshot: Map[FilterPath, Set[SmqdRoute]]

  def filter(topicPath: TopicPath): Seq[SmqdRoute]
  def routes(topicPath: TopicPath): Seq[ActorRef]
  def routes(rm: RoutableMessage): Unit = this.routes(rm.topicPath).foreach( _ ! rm)

  private[smqd] def addRoute(filterPath: FilterPath): Unit
  private[smqd] def removeRoute(filterPath: FilterPath): Unit
}

case class RoutableMessage(topicPath: TopicPath, msg: Any, isRetain: Boolean = false, isLocal: Boolean = true) extends Serializable {
  val isRemote: Boolean = !isLocal
}

case class SmqdRoute(filterPath: FilterPath, actor: ActorRef, nodeName: String) {
  override def toString: String = s"${filterPath.toString} ${actor.path.toString}"
}

object SmqdRoute {
  implicit val smqRouteEncoder: Encoder[SmqdRoute] = new Encoder[SmqdRoute] {
    override def apply(rt: SmqdRoute): Json = Json.obj(
        ("topic", Json.fromString(rt.filterPath.toString)),
        ("node", Json.fromString(rt.actor.path.toString)))
  }
}

class ClusterModeRouter(verbose: Boolean) extends Router with StrictLogging {

  private var routes: Map[FilterPath, Set[SmqdRoute]] = Map.empty
  private val random: scala.util.Random = new scala.util.Random(System.currentTimeMillis())

  def set(routes: Map[FilterPath, Set[SmqdRoute]]): Unit = {
    this.routes = routes

    if (verbose) {
      logger.trace(
        routes.map{ case (f, s) =>
          s"$f -> ${ s.map(_.nodeName).mkString(", ")
          }"
        }.toSeq.sortWith(_ < _).mkString("\nRoutes\n      ", "\n      ", ""))
    }
  }

  private var replicator: RoutesReplicator = _
  private[smqd] def setReplicator(repl: RoutesReplicator): Unit =
    this.replicator = repl

  private[smqd] def addRoute(filterPath: FilterPath): Unit =
    this.replicator.addRoute(filterPath)

  private[smqd] def removeRoute(filterPath: FilterPath): Unit =
    this.replicator.removeRoute(filterPath)

  def snapshot: Map[FilterPath, Set[SmqdRoute]] = routes

  def filter(topicPath: TopicPath): Seq[SmqdRoute] = {
    // find all routes that matches with the topic
    this.routes.filter{ case (filter, _) => filter.matchFor(topicPath) }.flatMap{ case (_, route) => route}.toList
  }

  def routes(topicPath: TopicPath): Seq[ActorRef] = {
    this.filter(topicPath)
      .groupBy( _.filterPath.prefix )
      .flatMap{ case (prefix, rtList) =>
        prefix match {
          case FilterPathPrefix.NoPrefix =>
            rtList

          case FilterPathPrefix.Queue =>
            val cnt = rtList.size
            val routee = if (cnt == 1) rtList.head else rtList(random.nextInt(cnt))
            List(routee)

          case FilterPathPrefix.Local =>
            rtList

          case FilterPathPrefix.Share =>
            rtList.groupBy( r => r.filterPath.group.get ).map { case(group, list) =>
              list(random.nextInt(list.size))
            }

          case _ => Nil
        }
      }
      .map(r => r.actor).toSeq.distinct  // send a message per a node by 'distinct'
  }
}


object RoutesReplicator {
  val actorName = "routes_replicator"
}

class RoutesReplicator(smqd: Smqd, router: ClusterModeRouter, registry: Registry) extends Actor with StrictLogging {
  private implicit val selfUniqueAddress: SelfUniqueAddress = SelfUniqueAddress(Cluster(context.system).selfUniqueAddress)
  private val replicator = DistributedData(context.system).replicator
  private val FiltersKey = ORMultiMapKey[FilterPath, SmqdRoute]("smqd.filters")

  private val config = smqd.Implicit.system.settings.config

  private val numRegex = "([0-9]+)".r

  private val writeCLTimeout = config.getDuration("smqd.router.write_consistency_timeout").toMillis.milli
  private val writeConsistency = config.getString("smqd.router.write_consistency_level").toLowerCase match {
    case "majority" => WriteMajority(writeCLTimeout)
    case "local"    => WriteLocal
    case "all"      => WriteAll(writeCLTimeout)
    case numRegex(str) => WriteTo(str.toInt, writeCLTimeout)
  }
  private val readCLTimeout = config.getDuration("smqd.router.read_consistency_timeout").toMillis.milli
  private val readConsistency = config.getString("smqd.router.read_consistency_level").toLowerCase match {
    case "majority" => ReadMajority(readCLTimeout)
    case "local"    => ReadLocal
    case "all"      => ReadAll(readCLTimeout)
    case numRegex(str) => ReadFrom(str.toInt, readCLTimeout)
  }

  private val blindRoutingThreshold = config.getInt("smqd.router.blind_routing_threshold")
  private var blindRoutingFlag = false
  private val routesCount = new AtomicInteger(0)

  private val localRouter = context.actorOf(Props(classOf[ClusterAwareLocalRouter], registry), "node-router")

  override def preStart(): Unit = {
    replicator ! Subscribe(FiltersKey, self)
  }

  override def postStop(): Unit = {
    replicator ! Unsubscribe(FiltersKey, self)
  }

  override def receive: Receive = {
    case Ready =>
      context.become(receive0)
      router.setReplicator(this)
      sender ! ReadyAck
  }

  def receive0: Receive = {
    case c @ Changed(FiltersKey) =>
      val data = c.get(FiltersKey)
      router.set(data.entries)
  }

  private[smqd] def addRoute(filter: FilterPath): Unit = {
    if (blindRoutingFlag) {
      // do nothing
    }
    else {
      if (blindRoutingThreshold != 0 && routesCount.incrementAndGet() >= blindRoutingThreshold) {
        if (!blindRoutingFlag) { // enable blind routing
          blindRoutingFlag = true
          logger.warn("Blind routing triggered: > {}", blindRoutingThreshold)
          replicator ! Update(FiltersKey, ORMultiMap.empty[FilterPath, SmqdRoute], writeConsistency)(m => m.addBinding(selfUniqueAddress, "#", SmqdRoute("#", localRouter, smqd.nodeName)))
        }
      }
      else {
        replicator ! Update(FiltersKey, ORMultiMap.empty[FilterPath, SmqdRoute], writeConsistency)(m => m.addBinding(selfUniqueAddress, filter, SmqdRoute(filter, localRouter, smqd.nodeName)))
      }
    }
  }

  private[smqd] def removeRoute(filter: FilterPath): Unit = {
    if (blindRoutingFlag) {
      // do nothing
    }
    else {
      replicator ! Update(FiltersKey, ORMultiMap.empty[FilterPath, SmqdRoute], writeConsistency)(m => m.removeBinding(selfUniqueAddress, filter, SmqdRoute(filter, localRouter, smqd.nodeName)))
    }
  }
}



trait SendingOutboundPublish {

  def sendOutboundPublishByTypes(filteredList: Seq[Registration], rm: RoutableMessage)(implicit random: scala.util.Random): Unit = {
    val matchedRegList = filteredList.groupBy(_.filterPath.prefix).flatMap { case (prefix, regList) =>
      prefix match {
        case FilterPathPrefix.NoPrefix =>
          regList

        case FilterPathPrefix.Local if rm.isLocal =>
          regList

        case FilterPathPrefix.Local if rm.isRemote =>
          // local subscriptions, if a message arrived from a remote node, simply ignore
          Seq.empty

        case FilterPathPrefix.Queue =>
          val dest = if (regList.size == 1) {
            regList.head
          }
          else {
            regList(random.nextInt(regList.size))
          }
          Seq(dest)

        case FilterPathPrefix.Share =>
          regList.groupBy(reg => reg.filterPath.group.get).map{ case (_, seq) =>
            // send one message per a group
            seq(random.nextInt(seq.size))
          }.toSeq

        case _ =>
          Seq.empty
      }
    }.toSeq

    sendOutboundPublish(matchedRegList, rm)
  }

  // [MQTT-3.3.5-1] Filtering overlap subscriptions,
  // send a message for a subscriber with maximum QoS of all the matching subscriptions.
  def sendOutboundPublish(regList: Seq[Registration], rm: RoutableMessage): Unit = {
    regList.groupBy(reg => reg.actor)
      .foreach{ case (_, regs) =>
        val reg = regs.sortWith((l, r) => l.qos > r.qos).head
        sendOutboundPubish(reg, rm)
      }
  }

  def sendOutboundPubish(reg: Registration, rm: RoutableMessage): Unit = {
    if (reg.clientId.isDefined) { // session associated subscriber, which means this is not a internal actor (callback)
      reg.actor ! OutboundPublish(rm.topicPath, reg.qos, rm.isRetain, rm.msg)
    }
    else { // no session, this subscriber is internal actor (callback)
      reg.actor ! (rm.topicPath, rm.msg)
    }
  }
}

class ClusterAwareLocalRouter(registry: Registry) extends Actor with SendingOutboundPublish with StrictLogging {

  private val random: scala.util.Random = new scala.util.Random(System.currentTimeMillis())

  override def receive: Receive = {
    case rm: RoutableMessage =>
      // local messaging
      sendOutboundPublishByTypes(registry.filter(rm.topicPath), rm)(random)
    case msg =>
      logger.info("Unhandled Message {}", msg)
  }
}

class LocalModeRouter(registry: Registry) extends Router with SendingOutboundPublish with StrictLogging {

  private val random: scala.util.Random = new scala.util.Random(System.currentTimeMillis())

  override def snapshot: Map[FilterPath, Set[SmqdRoute]] = Map.empty

  override def filter(topicPath: TopicPath): Seq[SmqdRoute] = Nil

  override def routes(topicPath: TopicPath): Seq[ActorRef] = Nil

  override private[smqd] def addRoute(filterPath: FilterPath): Unit = { } // do nothing

  override private[smqd] def removeRoute(filterPath: FilterPath): Unit = { } // do nothing

  override def routes(rm: RoutableMessage): Unit = {
    sendOutboundPublishByTypes(registry.filter(rm.topicPath), rm)(random)
  }
}