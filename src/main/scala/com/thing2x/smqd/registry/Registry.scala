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

package com.thing2x.smqd.registry

import akka.actor.{Actor, ActorRef}
import com.thing2x.smqd.QoS._
import com.thing2x.smqd._
import com.thing2x.smqd.registry.RegistryCallbackManagerActor.{CreateCallback, CreateCallbackPF}
import com.typesafe.scalalogging.StrictLogging

import scala.collection.mutable
import scala.concurrent.{Future, Promise}
import scala.language.postfixOps

// 2018. 6. 3. - Created by Kwon, Yeong Eon

/** Subscription management registry. Hold all subscriber's [[Registration]]
  */
object Registry {
  type RegistryCallback = PartialFunction[(TopicPath, Any), Unit]

  def apply(smqd: Smqd, debugDump: Boolean) = new Registry(smqd, debugDump)
}

class Registry(smqd: Smqd, debugDump: Boolean) extends StrictLogging {

  private var _callbackManager: ActorRef = _
  private[registry] def callbackManager: ActorRef = _callbackManager
  private[registry] def callbackManager_=(actor: ActorRef): Unit = _callbackManager = actor

  private val trie: TPathTrie[Registration] = TPathTrie()

  def subscribe(filterPath: FilterPath, callback: (TopicPath, Any) => Unit): Future[ActorRef] = {
    val promise = Promise[ActorRef]()
    _callbackManager ! CreateCallback(filterPath, callback, promise)
    promise.future
  }

  def subscribe(filterPath: FilterPath)(receive: Actor.Receive): Future[ActorRef] = {
    val promise = Promise[ActorRef]()
    _callbackManager ! CreateCallbackPF(filterPath, receive, promise)
    promise.future
  }

  def subscribe(filterPath: FilterPath, actor: ActorRef, clientId: Option[ClientId] = None, qos: QoS = QoS.AtMostOnce): Unit =
    subscribe0(Registration(filterPath, qos, actor, clientId))

  private def subscribe0(reg: Registration): Unit = {
    logger.debug("subscribe0 {}{}", reg.actor.path, if (reg.filterPath == null) "" else ": " + reg.filterPath.toString)

    val nrOfRemains = trie.add(reg.filterPath, reg)
    if (nrOfRemains == 1) { // if it's a new
      smqd.addRoute(reg.filterPath)
    }
    if (debugDump)
      logger.debug(s"\n{}", dump)
  }

  def unsubscribe(filterPath: FilterPath, actor: ActorRef): Boolean = unsubscribe0(actor, filterPath)

  def unsubscribe(actor: ActorRef): Boolean = unsubscribe0(actor)

  private def unsubscribe0(actor: ActorRef, filterPath: FilterPath = null): Boolean = {
    logger.debug("unsubscribe0 {}{}", actor.path, if (filterPath == null) "" else ": " + filterPath.toString)

    var result = false
    if (filterPath != null) { // filter based unregister
      val nrOfRemains = trie.remove(filterPath) { r => r.actor.path == actor.path }
      if (nrOfRemains == 0)
        smqd.removeRoute(filterPath)

      if (nrOfRemains >= 0) // negative number means non-existing filter path
        result = true
    } else { // actor based registration
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

  def filter(topicPath: TopicPath): Seq[Registration] =
    trie.matches(topicPath)

  def snapshot: Seq[Registration] =
    trie.snapshot

  private def dump: String = {
    val sb = new mutable.StringBuilder()
    trie.dump(sb)
    sb.toString()
  }

}
