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

import akka.actor.ActorRef
import com.thing2x.smqd.QoS._
import com.thing2x.smqd._
import com.thing2x.smqd.registry.RegistryCallbackManagerActor.{CreateCallback, CreateCallbackPF}

import scala.concurrent.{Future, Promise}
import scala.language.postfixOps

// 2018. 6. 3. - Created by Kwon, Yeong Eon

/**
  * Subscription management registry. Hold all subscriber's [[Registration]]
  */
object Registry {
  type RegistryCallback = PartialFunction[(TopicPath, Any), Unit]
}

trait Registry {

  private[registry] def callbackManager: ActorRef

  def subscribe(filterPath: FilterPath, callback: (TopicPath, Any) => Unit): Future[ActorRef] = {
    val promise = Promise[ActorRef]
    callbackManager ! CreateCallback(filterPath, callback, promise)
    promise.future
  }

  def subscribe(filterPath: FilterPath)(callback: Registry.RegistryCallback): Future[ActorRef] = {
    val promise = Promise[ActorRef]
    callbackManager ! CreateCallbackPF(filterPath, callback, promise)
    promise.future
  }

  def subscribe(filterPath: FilterPath, actor: ActorRef, clientId: Option[ClientId] = None, qos: QoS = QoS.AtMostOnce): Unit =
    subscribe0(Registration(filterPath, qos, actor, clientId))

  def subscribe0(reg: Registration): Unit

  def unsubscribe(filterPath: FilterPath, actor: ActorRef): Boolean = unsubscribe0(actor, filterPath)

  def unsubscribe(actor: ActorRef): Boolean = unsubscribe0(actor)

  def unsubscribe0(actor: ActorRef, filterPath: FilterPath = null): Boolean = false

  def filter(topicPath: TopicPath): Seq[Registration] = Nil

  def snapshot: Seq[Registration] = Nil
}
