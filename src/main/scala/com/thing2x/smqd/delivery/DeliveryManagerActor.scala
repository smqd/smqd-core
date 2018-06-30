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

package com.thing2x.smqd.delivery

import akka.actor.Actor
import com.typesafe.scalalogging.StrictLogging
import com.thing2x.smqd.ChiefActor.{Ready, ReadyAck}

/**
  * 2018. 6. 8. - Created by Kwon, Yeong Eon
  */
object DeliveryManagerActor {
  val actorName = "delivery"
}

// this actor is in charge delivery publish message in local scope, the incomming messages are routed by Inter Nodes Router
class DeliveryManagerActor extends Actor with StrictLogging {
  override def receive: Receive = {
    case Ready =>
      context.become(receive0)
      sender ! ReadyAck
  }

  def receive0: Receive = {
    case msg =>
      logger.info("Unhandled Message {}", msg)
  }
}
