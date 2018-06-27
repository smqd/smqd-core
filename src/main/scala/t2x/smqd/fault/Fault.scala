package t2x.smqd.fault

import t2x.smqd.SmqFailure

/**
  * 2018. 6. 3. - Created by Kwon, Yeong Eon
  */

@SerialVersionUID(-1)
abstract sealed class Fault extends SmqFailure with Serializable {
}

// top level faults

object SessionFault {
  def apply(sessionId: String, message: String) = new SessionFault(sessionId, message)
}

class SessionFault(val sessionId: String, val message: String) extends Fault {
  override def toString: String = s"[$sessionId] $message"
}

object GeneralFault {
  def apply(message: String) = new GeneralFault(message)
}

class GeneralFault(val message: String) extends Fault

// common - server fault
case class WrongConfiguration(reason: String) extends GeneralFault(reason)

// common - client fault
case class NotAllowedMqttMessage(messageName: String) extends GeneralFault(s"$messageName not allowed")
case class MalformedMessage(reason: String) extends GeneralFault(s"maformed message: $reason")

// CONNECT
case class UnacceptableProtocolVersion(protocolName: String, protocolVersion: Int) extends GeneralFault(s"Unacceptable Protocol or version: $protocolName, $protocolVersion")
case class IdentifierRejected(override val sessionId: String, override val message: String) extends SessionFault(sessionId, message)
case class InvalidWillTopic(override val sessionId: String, topic: String) extends SessionFault(sessionId, s"Invalid will topic[$topic]")
case object MutipleConnectRejected extends GeneralFault("MultipleConnection not allowed")
case object ServerUnavailable extends GeneralFault("Server unavailable")
case class BadUserNameOrPassword(override val sessionId: String, override val message: String) extends SessionFault(sessionId, message)
case class NotAuthorized(override val sessionId: String, override val message: String) extends SessionFault(sessionId, message)

// SUBSCRIBE
case class InvalidTopicNameToSubscribe(override val sessionId: String, topicName: String) extends SessionFault(sessionId, topicName)
case class InvalidTopicNameToUnsubscribe(override val sessionId: String, topicName: String) extends SessionFault(sessionId, topicName)
case class TopicNotAllowedToSubscribe(override val sessionId: String, topicName: String) extends SessionFault(sessionId, topicName)
case class UnknownErrorToSubscribe(override val sessionId: String, topicName: String) extends SessionFault(sessionId, topicName)

// PUBLISH
case class InvalidTopicToPublish(override val sessionId: String, topicName: String) extends SessionFault(sessionId, topicName)