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

/**
  * 2018. 6. 3. - Created by Kwon, Yeong Eon
  */
trait ProtocolNotification {
  val channelId: String
  val messageType: String
  val clientId: String
  val message: String
  val direction: ProtocolDirection
}

sealed trait ProtocolDirection
case object Recv extends ProtocolDirection
case object Send extends ProtocolDirection
case object Neut extends ProtocolDirection

case class SmqRecvMessage(clientId: String, channelId: String, messageType: String, message: String) extends ProtocolNotification {
  override val direction: ProtocolDirection = Recv
}

case class SmqSendMessage(clientId: String, channelId: String, messageType: String, message: String) extends ProtocolNotification {
  override val direction: ProtocolDirection = Send
}

case class SmqNeutMessage(clientId: String, channelId: String, messageType: String, message: String) extends ProtocolNotification {
  override val direction: ProtocolDirection = Neut
}
