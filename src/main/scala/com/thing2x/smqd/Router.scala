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

import akka.actor.{Actor, ActorRef}
import com.thing2x.smqd.ChiefActor.{Ready, ReadyAck}
import com.thing2x.smqd.session.SessionActor.OutboundPublish
import com.typesafe.scalalogging.StrictLogging

/**
  * 2018. 6. 15. - Created by Kwon, Yeong Eon
  */
trait Router {
  def set(routes: Map[FilterPath, Set[SmqdRoute]]): Unit
  def snapshot: Map[FilterPath, Set[SmqdRoute]]

  def filter(topicPath: TopicPath): Seq[SmqdRoute]
  def routes(topicPath: TopicPath): Seq[ActorRef]
  def routes(rm: RoutableMessage): Unit = this.routes(rm.topicPath).foreach( _ ! rm)
}

case class RoutableMessage(topicPath: TopicPath, msg: Any, isRetain: Boolean = false, isLocal: Boolean = true) extends Serializable {
  val isRemote: Boolean = !isLocal
}

case class SmqdRoute(filterPath: FilterPath, actor: ActorRef) {
  override def toString: String = s"${filterPath.toString} ${actor.path.toString}"
}

class ClusterModeRouter extends Router with StrictLogging {

  private var routes: Map[FilterPath, Set[SmqdRoute]] = Map.empty
  private val random: scala.util.Random = new scala.util.Random(System.currentTimeMillis())

  def set(routes: Map[FilterPath, Set[SmqdRoute]]): Unit = {
    this.routes = routes

    /*
    logger.trace(
      routes.map{ case (f, s) =>
        s"$f -> ${ s.map( r =>
          r.actor.path.toStringWithAddress(r.actor.path.address)).mkString("\n            ", "\n            ", "")
        }"
      }.mkString("\nRoutes\n      ", "\n      ", ""))
      */
  }

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

trait SendingOutboundPublish {
  def sendOutboundPubish(reg: Registration, rm: RoutableMessage): Unit = {
    if (reg.sessionId.isDefined) { // session associated subscriber, which means this is not a internal actor (callback)
      reg.actor ! OutboundPublish(rm.topicPath, reg.qos, rm.isRetain, rm.msg)
    }
    else { // no session, this subscriber is internal actor (callback)
      reg.actor ! (rm.topicPath, rm.msg)
    }
  }
}

object ClusterAwareLocalRouter {
  val actorName: String = "node-router"
}

class ClusterAwareLocalRouter(registry: Registry) extends Actor with SendingOutboundPublish with StrictLogging {

  private val random: scala.util.Random = new scala.util.Random(System.currentTimeMillis())

  override def receive: Receive = {
    case Ready =>
      context.become(receive0)
      sender ! ReadyAck
  }

  def receive0: Receive = {
    case rm: RoutableMessage =>

      // local messaging
      registry.filter(rm.topicPath).groupBy(_.filterPath.prefix).foreach { case (prefix, regList) =>
        prefix match {
          case FilterPathPrefix.NoPrefix =>
            regList.foreach { reg => sendOutboundPubish(reg, rm) }

          case FilterPathPrefix.Local if rm.isLocal =>
            regList.foreach { reg => sendOutboundPubish(reg, rm) }

          case FilterPathPrefix.Local if rm.isRemote =>
            // local subscriptions, if a message arrived from a remote node, simply ignore

          case FilterPathPrefix.Queue =>
            val dest = if (regList.size == 1) {
              regList.head
            }
            else {
              regList(random.nextInt(regList.size))
            }

            sendOutboundPubish(dest, rm)

          case FilterPathPrefix.Share =>
            regList.groupBy(reg => reg.filterPath.group.get).foreach{ case (group, seq) =>
              // send one message per a group
              sendOutboundPubish(seq(random.nextInt(seq.size)), rm)
            }
        }
      }
    case msg =>
      logger.info("Unhandled Message {}", msg)
  }
}

class LocalModeRouter(registry: Registry) extends Router with SendingOutboundPublish with StrictLogging {

  override def set(routes: Map[FilterPath, Set[SmqdRoute]]): Unit = ???

  override def snapshot: Map[FilterPath, Set[SmqdRoute]] = ???

  override def filter(topicPath: TopicPath): Seq[SmqdRoute] = ???

  override def routes(topicPath: TopicPath): Seq[ActorRef] = ???

  override def routes(rm: RoutableMessage): Unit = {
    registry.filter(rm.topicPath).foreach{ reg => sendOutboundPubish(reg, rm) }
  }
}