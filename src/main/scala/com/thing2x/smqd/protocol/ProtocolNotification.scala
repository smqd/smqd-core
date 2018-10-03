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

package com.thing2x.smqd.protocol

import io.circe.Decoder.Result
import io.circe._

/**
  * 2018. 6. 3. - Created by Kwon, Yeong Eon
  */

sealed trait ProtocolDirection
case object Recv extends ProtocolDirection
case object Send extends ProtocolDirection
case object Neut extends ProtocolDirection

trait ProtocolNotification {
  val channelId: String
  val messageType: String
  val clientId: String
  val message: String
  val direction: ProtocolDirection
}

object ProtocolNotification {

  implicit val protocolNotificationEncoder: Encoder[ProtocolNotification] = new Encoder[ProtocolNotification] {
    override def apply(pn: ProtocolNotification): Json = Json.obj(
      ("channelId",   Json.fromString(pn.channelId)),
      ("messageType", Json.fromString(pn.messageType)),
      ("clientId",    Json.fromString(pn.clientId)),
      ("message",     Json.fromString(pn.message)),
      ("direction",   Json.fromString(pn.direction.toString)))
  }

  implicit val protocolNotificationDecoder: Decoder[ProtocolNotification] = new Decoder[ProtocolNotification] {
    override def apply(c: HCursor): Result[ProtocolNotification] = {
      for {
        channelId <- c.downField("channelId").as[String]
        messageType <- c.downField("messageType").as[String]
        clientId <- c.downField("clientId").as[String]
        message <- c.downField("message").as[String]
        direction <- c.downField("direction").as[String]
      } yield {
        direction match {
          case "Recv" =>
            SmqRecvMessage(clientId, channelId, messageType, message)
          case "Send" =>
            SmqSendMessage(clientId, channelId, messageType, message)
          case "neut" =>
            SmqNeutMessage(clientId, channelId, messageType, message)
        }
      }
    }
  }
}

case class SmqRecvMessage(clientId: String, channelId: String, messageType: String, message: String) extends ProtocolNotification {
  override val direction: ProtocolDirection = Recv
}

case class SmqSendMessage(clientId: String, channelId: String, messageType: String, message: String) extends ProtocolNotification {
  override val direction: ProtocolDirection = Send
}

case class SmqNeutMessage(clientId: String, channelId: String, messageType: String, message: String) extends ProtocolNotification {
  override val direction: ProtocolDirection = Neut
}
