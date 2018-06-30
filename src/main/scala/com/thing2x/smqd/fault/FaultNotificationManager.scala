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

package com.thing2x.smqd.fault

import akka.actor.Actor
import com.typesafe.scalalogging.StrictLogging
import com.thing2x.smqd.ChiefActor.ReadyAck
import com.thing2x.smqd._
import com.thing2x.smqd.util.ClassLoading

/**
  * 2018. 6. 3. - Created by Kwon, Yeong Eon
  */

object FaultNotificationManager {
  val actorName: String = "faults"
  val topic: TopicPath = TPath.parseForTopic("$SYS/faults").get
}

import com.thing2x.smqd.fault.FaultNotificationManager._

class FaultNotificationManager(smqd: Smqd) extends Actor with ClassLoading with StrictLogging {

  override def preStart(): Unit = {
  }

  override def receive: Receive = {
    case ChiefActor.Ready =>
      context.become(receive0)
      sender ! ReadyAck
  }

  def receive0: Receive = {
    case ft: Fault =>
      // publish fault message
      smqd.publish(topic, ft)
  }
}
