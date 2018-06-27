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

package t2x.smqd.protocol

import akka.actor.Actor
import com.typesafe.scalalogging.StrictLogging
import t2x.smqd.ChiefActor.{Ready, ReadyAck}
import t2x.smqd._
import t2x.smqd.util.ClassLoading

/**
  * 2018. 6. 3. - Created by Kwon, Yeong Eon
  */
object ProtocolNotificationManager {
  val actorName: String = "protocols"
  val topic: TopicPath = TPath.parseForTopic("$SYS/protocols").get
}

import ProtocolNotificationManager._

class ProtocolNotificationManager(smqd: Smqd) extends Actor with ClassLoading with StrictLogging {

  override def preStart(): Unit = {
  }

  override def receive: Receive = {
    case Ready =>
      context.become(receive0)
      sender ! ReadyAck
  }

  def receive0: Receive = {
    case m: ProtocolNotification =>
      smqd.publish(topic, m)
    case m: Any =>
      logger.warn(s"unhandled message: $m")
  }
}



