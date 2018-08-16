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
import com.thing2x.smqd._
import com.thing2x.smqd.QoS._
import com.thing2x.smqd.RegistryCallbackManagerActor.{CreateCallback, CreateCallbackPF}
import com.thing2x.smqd.util.ActorIdentifying
import com.typesafe.scalalogging.StrictLogging
import spray.json.{DefaultJsonProtocol, JsNumber, JsObject, JsString, JsValue, RootJsonFormat}

import scala.collection.mutable
import scala.concurrent.{Await, Future}

// 2018. 6. 3. - Created by Kwon, Yeong Eon

/**
  * Subscription management registry. Hold all subscriber's [[Registration]]
  */
trait Registry {
  def subscribe(filterPath: FilterPath, actor: ActorRef, sessionId: Option[ClientId] = None, qos: QoS = QoS.AtMostOnce): QoS
  def unsubscribe(filterPath: FilterPath, actor: ActorRef): Boolean
  def unsubscribeAll(actor: ActorRef): Boolean
  def filter(topicPath: TopicPath): Seq[Registration]

  def subscribe(filterPath: FilterPath, callback: (TopicPath, Any) => Unit): ActorRef
  def subscribe(filterPath: FilterPath)(callback: PartialFunction[(TopicPath, Any), Unit]): ActorRef

  def snapshot: Seq[Registration]
}

/**
  * Represents a subscription
  *
  * @param filterPath  subscribed topic filter
  * @param qos         subscribed qos level [[QoS]]
  * @param actor       Actor that wraps actual subscriber (mqtt client, actor, callback function)
  * @param clientId    Holding remote client's [[ClientId]], only presets if the subscription is made by remote mqtt client.
  */
case class Registration(filterPath: FilterPath, qos: QoS, actor: ActorRef, clientId: Option[ClientId]) extends Ordered[Registration] {
  override def toString = s"${filterPath.toString} ($qos) => ${actor.path.toString}"

  override def equals(obj: scala.Any): Boolean = {
    obj match {
      case other: Registration =>
        actor == other.actor
      case _ => false
    }
  }

  override def compare(that: Registration): Int = {
    (this.clientId, that.clientId) match {
      case (Some(l), Some(r)) => l.id.compareToIgnoreCase(r.id)
      case (Some(_), None) => -1
      case (None, Some(_)) => 1
      case _ =>  this.actor.path.toString.compareToIgnoreCase(that.actor.path.toString)
    }
  }
}

object Registration extends DefaultJsonProtocol {
  implicit object RegistrationFormat extends RootJsonFormat[com.thing2x.smqd.Registration] {
    override def read(json: JsValue): Registration = ???
    override def write(rt: Registration): JsValue = {
      if (rt.clientId.isDefined) {
        val channelId = rt.clientId.get.channelId
        JsObject(
          "topic" -> JsString(rt.filterPath.toString),
          "qos" -> JsNumber(rt.qos.id),
          "clientId" -> JsString(rt.clientId.get.id),
          "channelId" -> JsString(channelId.getOrElse("n/a")))
      }
      else {
        JsObject(
          "topic" -> JsString(rt.filterPath.toString),
          "qos" -> JsNumber(rt.qos.id),
          "actor" -> JsString(rt.actor.path.toString))
      }
    }
  }
}

abstract class AbstractRegistry(smqd: Smqd) extends Registry with ActorIdentifying with StrictLogging {

  def subscribe(filterPath: FilterPath, actor: ActorRef, clientId: Option[ClientId] = None, qos: QoS = QoS.AtMostOnce): QoS = {
    subscribe0(Registration(filterPath, qos, actor, clientId))
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
  private lazy val callbackManager = identifyManagerActor(RegistryCallbackManagerActor.actorName)
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

final class HashMapRegistry(smqd: Smqd, debugDump: Boolean) extends AbstractRegistry(smqd)  {

  protected val registry: mutable.HashMap[FilterPath, Set[Registration]] = mutable.HashMap[FilterPath, Set[Registration]]()

  def subscribe0(reg: Registration): QoS = {
    //logger.debug("subscribe0 {}{}", reg.actor.path, if (reg.filterPath == null) "" else ": "+reg.filterPath.toString)
    synchronized {
      registry.get(reg.filterPath) match {
        case Some(list) =>
          registry.put(reg.filterPath, list + reg)
        case None =>
          registry.put(reg.filterPath, Set(reg))
          // a fresh new filter registration, so it requires local-node be in routes for cluster-wise delivery
          smqd.addRoute(reg.filterPath)
      }
      if (debugDump)
        logger.debug(dump)
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
    if (debugDump)
      logger.debug(dump)
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

  def snapshot: Seq[Registration] = {
    registry.values.flatten.toSeq
  }
}

final class TrieRegistry(smqd: Smqd, debugDump: Boolean) extends AbstractRegistry(smqd) {
  private val trie: TPathTrie[Registration] = TPathTrie()

  def subscribe0(reg: Registration): QoS ={
    logger.debug("subscribe0 {}{}", reg.actor.path, if (reg.filterPath == null) "" else ": "+reg.filterPath.toString)

    val noRemains = trie.add(reg.filterPath, reg)
    if (noRemains == 1) { // if it's a new
      smqd.addRoute(reg.filterPath)
    }
    if (debugDump)
      logger.debug(s"\n{}", dump)
    reg.qos
  }

  def unsubscribe0(actor: ActorRef, filterPath: FilterPath = null): Boolean = {
    logger.debug("unsubscribe0 {}{}", actor.path, if (filterPath == null) "" else ": "+filterPath.toString)

    var result = false
    if (filterPath != null) { // filter based unregister
      val noRemains = trie.remove(filterPath){r => r.actor.path == actor.path}
      if (noRemains == 0)
        smqd.removeRoute(filterPath)

      if (noRemains >= 0) // negative number means non-existing filter path
        result = true
    }
    else { // actor based registration
      val rs = trie.filter(r => r.actor.path == actor.path)
      rs.foreach { r =>
        val noRemains = trie.remove(r.filterPath, r)
        if (noRemains == 0)
          smqd.removeRoute(filterPath)
      }
      result = true
    }

    if (debugDump)
      logger.debug(s"\n{}", dump)
    result
  }

  private def dump: String = {
    val sb = new mutable.StringBuilder()
    trie.dump(sb)
    sb.toString()
  }

  def filter(topicPath: TopicPath): Seq[Registration] =
    trie.matches(topicPath)

  def snapshot: Seq[Registration] = {
    trie.snapshot
  }
}