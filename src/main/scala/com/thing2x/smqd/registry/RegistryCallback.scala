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

import akka.actor.{Actor, ActorRef, Props}
import com.thing2x.smqd.ChiefActor.{Ready, ReadyAck}
import com.thing2x.smqd.registry.RegistryCallbackManagerActor.{CreateCallback, CreateCallbackPF}
import com.thing2x.smqd.{FilterPath, ResponsibleMessage, Smqd, TopicPath}
import com.typesafe.scalalogging.StrictLogging

import scala.concurrent.Promise

// 2018. 6. 18. - Created by Kwon, Yeong Eon

object RegistryCallbackManagerActor {
  val actorName: String = "registry_callbacks"

  case class CreateCallback(filterPath: FilterPath, callback: (TopicPath, Any) => Unit, prommise: Promise[ActorRef])
  case class CreateCallbackPF(filterPath: FilterPath, receive: Actor.Receive, promise: Promise[ActorRef])
}

class RegistryCallbackManagerActor(smqd: Smqd, registry: Registry) extends Actor with StrictLogging {

  override def preStart(): Unit = registry.callbackManager = self

  override def receive: Receive = { case Ready =>
    context.become(receive0)
    sender() ! ReadyAck
  }

  def receive0: Receive = {
    case CreateCallback(filterPath, cb, promise) =>
      val child = context.actorOf(Props(classOf[RegistryCallbackActor], smqd, cb))
      smqd.subscribe(filterPath, child)
      promise.success(child)
    case CreateCallbackPF(filterPath, receive, promise) =>
      val child = context.actorOf(Props(classOf[RegistryCallbackPFActor], smqd, receive))
      smqd.subscribe(filterPath, child)
      promise.success(child)
  }
}

/** Actor works for callback function that subscribes a topic
  * @param callback callback function
  */
class RegistryCallbackActor(smqd: Smqd, callback: (TopicPath, Any) => Unit) extends Actor with StrictLogging {

  override def postStop(): Unit = {
    smqd.unsubscribe(self)
  }

  override def receive: Receive = { case (topicPath: TopicPath, msg) =>
    try {
      callback(topicPath, msg)
    } catch {
      case e: Throwable =>
        msg match {
          // in case of request-response model, reply the exception to the requestor
          // so that request manager can make the promise to complete with the failure
          case ResponsibleMessage(replyTo, _) => smqd.publish(replyTo, e)
          case _                              =>
        }
        throw e
    }
  }
}

/** Actor works for partial function callback that subscribes a topic
  * @param callback callback partial function
  */
class RegistryCallbackPFActor(smqd: Smqd, callback: Actor.Receive) extends Actor with StrictLogging {

  override def postStop(): Unit = {
    smqd.unsubscribe(self)
  }

  override def receive: Receive = {
    case m @ (topicPath: TopicPath, msg) if callback.isDefinedAt(m) =>
      try {
        callback((topicPath, msg))
      } catch {
        case e: Throwable =>
          msg match {
            // in case of request-response model, reply the exception to the requestor
            // so that request manager can make the promise to complete with the failure
            case ResponsibleMessage(replyTo, _) => smqd.publish(replyTo, e)
            case _                              =>
          }
          throw e
      }
    case m if callback.isDefinedAt(m) =>
      callback(m)
  }
}
