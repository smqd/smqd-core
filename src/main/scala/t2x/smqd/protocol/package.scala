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
