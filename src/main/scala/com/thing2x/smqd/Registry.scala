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

import akka.actor.ActorRef
import com.thing2x.smqd.QoS._
import com.thing2x.smqd.RegistryCallbackManagerActor.{CreateCallback, CreateCallbackPF}
import com.thing2x.smqd.util.ActorIdentifying
import com.typesafe.scalalogging.StrictLogging

import scala.collection.mutable
import scala.concurrent.{Await, Future}

/**
  * 2018. 6. 3. - Created by Kwon, Yeong Eon
  */

trait Registry {
  def subscribe(filterPath: FilterPath, actor: ActorRef, sessionId: Option[ClientId] = None, qos: QoS = QoS.AtMostOnce): QoS
  def unsubscribe(filterPath: FilterPath, actor: ActorRef): Boolean
  def unsubscribeAll(actor: ActorRef): Boolean
  def filter(topicPath: TopicPath): Seq[Registration]

  def subscribe(filterPath: FilterPath, callback: (TopicPath, Any) => Unit): ActorRef
  def subscribe(filterPath: FilterPath)(callback: PartialFunction[(TopicPath, Any), Unit]): ActorRef
}

case class Registration(filterPath: FilterPath, qos: QoS, actor: ActorRef, sessionId: Option[ClientId]) {
  override def toString = s"${filterPath.toString} ($qos) => ${actor.path.toString}"
}

abstract class AbstractRegistry(smqd: Smqd) extends Registry with ActorIdentifying with StrictLogging {

  def subscribe(filterPath: FilterPath, actor: ActorRef, sessionId: Option[ClientId] = None, qos: QoS = QoS.AtMostOnce): QoS = {
    subscribe0(Registration(filterPath, qos, actor, sessionId))
  }

  def unsubscribe(filterPath: FilterPath, actor: ActorRef): Boolean = {
    unsubscribe0(actor, filterPath)
  }

  def unsubscribeAll(actor: ActorRef): Boolean = {
    unsubscribe0(actor)
  }

  protected def subscribe0(reg: Registration): QoS
  protected def unsubscribe0(actor: ActorRef, filterPath: FilterPath = null): Boolean

  import akka.pattern.ask
  import akka.util.Timeout
  import smqd.Implicit._

  import scala.concurrent.duration._
  import scala.language.postfixOps
  private lazy val callbackManager = identifyActor(manager(RegistryCallbackManagerActor.actorName))(system)
  private implicit val timeout: Timeout = 1 second

  def subscribe(filterPath: FilterPath, callback: (TopicPath, Any) => Unit): ActorRef = {
    val f = callbackManager ? CreateCallback(callback)
    val actor = Await.result(f, timeout.duration).asInstanceOf[ActorRef]
    subscribe(filterPath, actor)
    actor
  }

  def subscribe(filterPath: FilterPath)(callback: PartialFunction[(TopicPath, Any), Unit]): ActorRef = {
    val f = callbackManager ? CreateCallbackPF(callback)
    val actor = Await.result(f, timeout.duration).asInstanceOf[ActorRef]
    subscribe(filterPath, actor)
    actor
  }
}

trait RegistryDelegate {
  def allowSubscribe(filterPath: FilterPath, qos: QoS, sessionId: ClientId, userName: Option[String]): Future[QoS]
  def allowPublish(topicPath: TopicPath, sessionId: ClientId, userName: Option[String]): Future[Boolean]
}


final class HashMapRegistry(smqd: Smqd) extends AbstractRegistry(smqd)  {

  protected val registry: mutable.HashMap[FilterPath, List[Registration]] = mutable.HashMap[FilterPath, List[Registration]]()

  def subscribe0(reg: Registration): QoS = {
    //logger.debug("subscribe0 {}{}", reg.actor.path, if (reg.filterPath == null) "" else ": "+reg.filterPath.toString)
    synchronized {
      registry.get(reg.filterPath) match {
        case Some(list) =>
          registry.put(reg.filterPath, reg :: list)
        case None =>
          registry.put(reg.filterPath, List(reg))
          // a fresh new filter registration, so it requires local-node be in routes for cluster-wise delivery
          smqd.addRoute(reg.filterPath)
      }
      // logger.debug(dump)
      reg.qos
    }
  }

  def unsubscribe0(actor: ActorRef, filterPath: FilterPath = null): Boolean = {
    //logger.debug("unsubscribe0 {}{}", actor.path, if (filterPath == null) "" else ": "+filterPath.toString)
    var result = false
    synchronized {
      // filter based unregister
      if (filterPath != null) {
        val entries = registry.get(filterPath)

        entries match {
          case Some(list) =>
            val updated = list.filterNot( _.actor.path == actor.path )
            if (updated.size != list.size) {
              result = true

              registry.put(filterPath, updated)
            }
          case None =>
        }
      }
      // actor based unregister
      else {
        registry.foreach { case (filterPath, list) =>
          val updated = list.filterNot( _.actor.path == actor.path )
          if (updated.isEmpty) {
            registry.remove(filterPath)
            // this topic filter do not have any subscriber anymore, so ask cluster not to route publish message
            smqd.removeRoute(filterPath)
            result = true
          }
          else if (updated.size != list.size) {
            registry.put(filterPath, updated)
            result = true
          }
        }
      }
    }
    // logger.debug(dump)
    result
  }

  private def dump: String = {
    registry.map { case (path, list) =>
      path.toString +list.map(_.actor.path.toString).mkString("\n              ", "\n              ", "")
    }.mkString("\nRegistry Dump\n      ", "\n      ", "\n")
  }

  def filter(topicPath: TopicPath): Seq[Registration] = {
    synchronized {
      registry.filter { case (filterPath, list) =>
        filterPath.matchFor(topicPath)
      }.flatMap { case (filterPath, list) =>
        list
      }.toList
    }
  }
}
