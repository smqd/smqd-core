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

import com.thing2x.smqd.SmqFailure
import io.circe.Decoder.Result
import io.circe._

/**
  * 2018. 6. 3. - Created by Kwon, Yeong Eon
  */

@SerialVersionUID(-1)
abstract sealed class Fault extends SmqFailure with Serializable {
}

object Fault {

  implicit val faultEncoder: Encoder[Fault] = new Encoder[Fault] {
    override def apply(ft: Fault): Json = {
      ft match {
        case sf: SessionFault =>
          Json.obj (
            ("fault",     Json.fromString(sf.getClass.getName)),
            ("sessionId", Json.fromString(sf.sessionId)),
            ("message",   Json.fromString(sf.message))
          )
        case gf: GeneralFault =>
          Json.obj (
            ("fault",   Json.fromString(gf.getClass.getName)),
            ("message", Json.fromString(gf.message))
          )
        case _ =>
          Json.obj (
            ("fault", Json.fromString(ft.getClass.getName))
          )
      }
    }
  }

  implicit val faultDecoder: Decoder[Fault] = new Decoder[Fault] {
    override def apply(c: HCursor): Result[Fault] = {
      for {
        fault <- c.downField("fault").as[String]
        sessionId <- c.downField("sessionId").as[String]
        message <- c.downField("message").as[String]
      } yield {
        fault match {
          case "com.thing2x.smqd.fault.SessionFault" =>
            SessionFault(sessionId, message)
          case _ =>
            GeneralFault(fault)
        }
      }
    }
  }
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
case class BadUsernameOrPassword(override val sessionId: String, override val message: String) extends SessionFault(sessionId, message)
case class NotAuthorized(override val sessionId: String, override val message: String) extends SessionFault(sessionId, message)

// SUBSCRIBE
case class InvalidTopicNameToSubscribe(override val sessionId: String, topicName: String) extends SessionFault(sessionId, topicName)
case class InvalidTopicNameToUnsubscribe(override val sessionId: String, topicName: String) extends SessionFault(sessionId, topicName)
case class TopicNotAllowedToSubscribe(override val sessionId: String, topicName: String) extends SessionFault(sessionId, topicName)
case class UnknownErrorToSubscribe(override val sessionId: String, topicName: String) extends SessionFault(sessionId, topicName)

// PUBLISH
case class InvalidTopicToPublish(override val sessionId: String, topicName: String) extends SessionFault(sessionId, topicName)

// User
case class UserWrongPassword(reason: String) extends GeneralFault(reason)
case class UserAlreadyExists(username: String) extends GeneralFault(s"User $username already exists")
case class UserNotExists(username: String) extends GeneralFault(s"User $username does not exist")