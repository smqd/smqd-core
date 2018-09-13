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
import com.thing2x.smqd.util.ActorIdentifying
import com.typesafe.scalalogging.StrictLogging
import spray.json.{DefaultJsonProtocol, JsNumber, JsObject, JsString, JsValue, RootJsonFormat}

import scala.collection.mutable
import scala.concurrent.Await

// 2018. 6. 3. - Created by Kwon, Yeong Eon

/**
  * Subscription management registry. Hold all subscriber's [[Registration]]
  */
trait Registry {
  def subscribe(filterPath: FilterPath, actor: ActorRef, sessionId: Option[ClientId] = None, qos: QoS = QoS.AtMostOnce): QoS
  def unsubscribe(filterPath: FilterPath, actor: ActorRef): Boolean
  def unsubscribe(actor: ActorRef): Boolean
  def filter(topicPath: TopicPath): Seq[Registration]

  def subscribe(filterPath: FilterPath, callback: (TopicPath, Any) => Unit): ActorRef
  def subscribe(filterPath: FilterPath)(callback: PartialFunction[(TopicPath, Any), Unit]): ActorRef

  def snapshot: Seq[Registration]
}
