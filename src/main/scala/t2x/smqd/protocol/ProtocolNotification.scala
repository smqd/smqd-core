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
