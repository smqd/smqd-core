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

package t2x.smqd

import spray.json.{JsObject, JsString, JsValue, RootJsonFormat}

/**
  * 2018. 6. 26. - Created by Kwon, Yeong Eon
  */
package object protocol {

  implicit object ProtocolNotificationFormat extends RootJsonFormat[ProtocolNotification] {

    override def read(json: JsValue): ProtocolNotification = {
      json.asJsObject.getFields("channelId", "messageType", "clientId", "message", "direction") match {
        case Seq(JsString(channelId), JsString(messageType), JsString(clientId), JsString(message), JsString(direction)) =>
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

    override def write(pn: ProtocolNotification): JsValue = JsObject (
      "channelId" -> JsString(pn.channelId),
      "messageType" -> JsString(pn.messageType),
      "clientId" -> JsString(pn.clientId),
      "message" -> JsString(pn.message),
      "direction" -> JsString(pn.direction.toString)
    )
  }

}
