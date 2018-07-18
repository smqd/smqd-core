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

package com.thing2x.smqd.net.mqtt

import akka.util.Timeout
import com.thing2x.smqd._
import com.thing2x.smqd.fault._
import com.thing2x.smqd.session.SessionActor.InboundDisconnect
import com.thing2x.smqd.session.SessionManagerActor._
import com.thing2x.smqd.session.SessionState
import com.typesafe.scalalogging.StrictLogging
import io.netty.channel.{ChannelHandlerContext, ChannelInboundHandlerAdapter}
import io.netty.handler.codec.mqtt.MqttConnectReturnCode._
import io.netty.handler.codec.mqtt.MqttMessageType._
import io.netty.handler.codec.mqtt.MqttQoS._
import io.netty.handler.codec.mqtt._

import scala.concurrent.Promise
import scala.concurrent.duration._
import scala.language.postfixOps
import scala.util.matching.Regex
import scala.util.{Failure, Success}

/**
  * 2018. 5. 30. - Created by Kwon, Yeong Eon
  */

object MqttConnectHandler{
  def apply(clientIdentifierFormat: Regex) = new MqttConnectHandler(clientIdentifierFormat)
}

class MqttConnectHandler(clientIdentifierFormat: Regex) extends ChannelInboundHandlerAdapter with StrictLogging {

  override def channelRead(channelCtx: ChannelHandlerContext, msg: Any): Unit = {

    msg match {
      //////////////////////////////////
      // CONNECT(1)
      case m: MqttConnectMessage =>
        processConnect(channelCtx, m)

      //////////////////////////////////
      // CONNACK(2)
      case _: MqttConnAckMessage =>
        val sessionCtx = channelCtx.channel.attr(ATTR_SESSION_CTX).get
        sessionCtx.smqd.notifyFault(NotAllowedMqttMessage("CONNACK"))
        channelCtx.close()

      //////////////////////////////////
      // DISCONNECT(14)
      case m: MqttMessage if m.fixedHeader.messageType == DISCONNECT =>
        val session = channelCtx.channel.attr(ATTR_SESSION).get
        val sessionCtx = channelCtx.channel.attr(ATTR_SESSION_CTX).get
        // [MQTT-3.14.4-3] Server MUST discard any Will Message associated with the current connection without publishing
        sessionCtx.will = None
        session ! InboundDisconnect

      //////////////////////////////////
      // PINGREQ(12)
      case m: MqttMessage if m.fixedHeader.messageType == PINGREQ =>
        // [MQTT-3.12.4-1] Server MUST send a PINGRESP Packet in response to a PINGREQ Packet
        val rsp = new MqttMessage(new MqttFixedHeader(PINGRESP, false, AT_MOST_ONCE, false, 0))
        channelCtx.channel.writeAndFlush(rsp)
        channelCtx.fireChannelReadComplete()

      //////////////////////////////////
      // PINGRESP(13)
      case m: MqttMessage if m.fixedHeader.messageType == PINGRESP =>
        val sessionCtx = channelCtx.channel.attr(ATTR_SESSION_CTX).get
        sessionCtx.smqd.notifyFault(NotAllowedMqttMessage("PINGRESP"))
        channelCtx.close()

      //////////////////////////////////
      // other messages
      case _ =>
        channelCtx.fireChannelRead(msg)
    }
  }

  private def processConnect(channelCtx: ChannelHandlerContext, m: MqttConnectMessage): Unit = {

    val sessionCtx = channelCtx.channel.attr(ATTR_SESSION_CTX).get

    // prevent multiple CONNECT messages
    sessionCtx.state = SessionState.ConnectReceived
    if (sessionCtx.state == SessionState.Failed) {
      // [MQTT-3.1.0-2] A client can only send the CONNECT Packet once over a Network Connection.
      // The Server MUST process a second CONNECT Packet sent from Client as a protocol violation and disconnect the Client
      sessionCtx.smqd.notifyFault(MutipleConnectRejected)
      channelCtx.close()
      return
    }

    val vh = m.variableHeader // variable header
    val pl = m.payload // payload

    // validate protocol version
    val protocolName = vh.name
    val protocolLevel = vh.version
    if (protocolLevel > PROTOCOL_LEVEL) {
      // [MQTT-3.1.2-2] The Server MUST respond to the CONNECT Packet with CONNACK return code 0x01
      // (unacceptable_protocol_level) and then disconnect the Client if the Protocol Level is not supported by the Server
      sessionCtx.smqd.notifyFault(UnacceptableProtocolVersion(protocolName, protocolLevel))
      connectAck(channelCtx, CONNECTION_REFUSED_UNACCEPTABLE_PROTOCOL_VERSION, false, true)
      return
    }

    // Client Identifier
    sessionCtx.clientId = pl.clientIdentifier

    // Clean Session
    sessionCtx.cleanSession = vh.isCleanSession

    // Keep Alive Time
    sessionCtx.keepAliveTimeSeconds = vh.keepAliveTimeSeconds()

    // Will
    sessionCtx.will = if (vh.isWillFlag) {
      TPath.parseForTopic(pl.willTopic) match {
        case Some(willPath) =>
          Some(Will(willPath, vh.isWillRetain, pl.willMessageInBytes()))
        case _ =>
          sessionCtx.smqd.notifyFault(InvalidWillTopic(sessionCtx.clientId.toString, pl.willTopic))
          None
      }
    }
    else {
      None
    }

    // Validate Client Identifier Regularations
    if (!isValidClientIdentifierFormat(channelCtx)) {
      // [MQTT-3.1.3-9] If the Server rejects the ClientId it MUST respond to CONNECT Packet with a CONNACK
      // return code 0x02 (Identifier rejected) and then close the Network Connection
      sessionCtx.smqd.notifyFault(IdentifierRejected(sessionCtx.clientId.toString, "clientid is not a valid format"))
      connectAck(channelCtx, CONNECTION_REFUSED_IDENTIFIER_REJECTED, false, true)
      return
    }

    // Authentication
    val hasUserName = vh.hasUserName
    val hasPassword = vh.hasPassword
    sessionCtx.userName = if (hasUserName) Some(pl.userName) else None
    sessionCtx.password = if (hasPassword) Some(pl.passwordInBytes) else None

    import sessionCtx.smqd.Implicit._
    implicit val timeout: Timeout = 2.second

    // [MQTT-3.1.3-9] If the Server rejects the ClientId it MUST respond to the CONNECT Packet with a CONNACK
    //                return code 0x02 (Identifier rejected) and then close the Network Connection
    // [MQTT-3.1.4-2] If the ClientId represents a Client already connected to the Sever then the Server MUST disconnect the existing Client
    // [MQTT-3.1.4-3] The Server MUST perform the processing of CleanSession that is described in section 3.1.2.4
    //                Start message delivery and keep alive monitoring
    sessionCtx.smqd.clientLogin(sessionCtx.clientId, sessionCtx.userName, sessionCtx.password).onComplete {
      case Success(result) if result == SmqSuccess =>
        sessionCtx.authorized = true

        val sessionManager = channelCtx.channel.attr(ATTR_SESSION_MANAGER).get

        // [MQTT-3.1.3-2] Each Client connecting to the Server has a unique ClientId. The ClientId MUST be used by Clients
        // and by Servers to identify state that they hold relating to this MQTT Session between the Client and the Server

        // [MQTT-3.1.2-6] If CleanSession is set to 1, the Client and Server MUST discard any previous Session and start
        // a new one. This Session lasts as long as the Network Connection. State data associated with this Session
        // MUST NOT be resused in any subsequent Session

        // [MQTT-3.2.2-1] If the Server accepts a connection with CleanSession set to 1, the Server MUST set
        // Session Present to 0 in the CONNACK packet in addition to setting a zero return code in CONNACK packet

        // [MQTT-3.1.2-4] If CleanSession is set to 0, the Server MUST resume communications with the Client based
        // on state from the current Session (as Identifieied by the Client identifier). If there is no Session
        // associated with the Client identifier the Server MUST create a new Session.
        // The Client and Server MUST store the Session after the Client and Server are disconnected

        // [MQTT-3.1.2-5] After the disconnection of a Session that had CleanSession to 0, the Server MUST store further
        // QoS1 and QoS2 messages that match any subscriptions that the client had at the time of disconnection as part
        // of the Session state

        // If the Server accepts a connection with CleanSession set to 0, the value set in
        // Session Present depends on whether the Server already has stored Session state for the supplied client ID.
        // [MQTT-3.2.2-2] If the Server has stored Session state, it MUST set Session Present to 1
        // [MQTT-3.2.2-3] If the Server does not have stored Session State, it MUST set Session Present to 0

        // create a new session or restore previous session
        val createResult = Promise[CreateSessionResult]()

        sessionManager ! CreateSession(sessionCtx, sessionCtx.cleanSession, createResult)
        createResult.future.map {
          case r: CreatedSessionSuccess => // success to create a session
            logger.debug(s"[${r.clientId}] Session created, clean session: ${sessionCtx.cleanSession}, session present: ${r.hadPreviousSession}")
            channelCtx.channel.attr(ATTR_SESSION).set(r.sessionActor)
            connectAck(channelCtx, CONNECTION_ACCEPTED, r.hadPreviousSession, close = false)

          case r: CreateSessionFailure => // fail to create a clean session
            logger.debug(s"[${r.clientId}] Session creation failed: ${r.reason}")
            sessionCtx.smqd.notifyFault(MutipleConnectRejected)
            connectAck(channelCtx, CONNECTION_REFUSED_IDENTIFIER_REJECTED, sessionPresent = true, close = true)
        }

      case Success(result) => // if result != SmqSuccess
        sessionCtx.smqd.notifyFault(result)
        val code = result match {
          case _: IdentifierRejected => CONNECTION_REFUSED_IDENTIFIER_REJECTED // 0x02
          case ServerUnavailable => CONNECTION_REFUSED_SERVER_UNAVAILABLE // 0x03
          case _: BadUsernameOrPassword => CONNECTION_REFUSED_BAD_USER_NAME_OR_PASSWORD // 0x04
          case _: NotAuthorized => CONNECTION_REFUSED_NOT_AUTHORIZED // 0x05
          case _ => CONNECTION_REFUSED_NOT_AUTHORIZED // 0x05
        }
        connectAck(channelCtx, code, sessionPresent = false, close = true)

      case Failure(_) =>
        connectAck(channelCtx, CONNECTION_REFUSED_SERVER_UNAVAILABLE, sessionPresent = false, close = true)
    }

    sessionCtx.state = SessionState.ConnectAcked
  }

  private def connectAck(channelCtx: ChannelHandlerContext, returnCode: MqttConnectReturnCode, sessionPresent: Boolean, close: Boolean): Unit = {
    channelCtx.channel.writeAndFlush(
      new MqttConnAckMessage(
        new MqttFixedHeader(CONNACK, false, AT_MOST_ONCE, false, 0),
        new MqttConnAckVariableHeader(returnCode, sessionPresent)))

    channelCtx.fireChannelReadComplete()
    if (close) channelCtx.close()
  }

  private def isValidClientIdentifierFormat(channelCtx: ChannelHandlerContext): Boolean = {

    val sessionCtx = channelCtx.channel.attr(ATTR_SESSION_CTX).get

    // [MQTT-3.1.3-3] The Client Identifier (ClientId) MUST be present and MUST be the first field in the CONNECT packet payload
    // [MQTT-3.1.3-4] The ClientId MUST be a UTF-8 encoded string
    // [MQTT-3.1.3-5] allows only [0-9a-zA-Z]{0-23}
    // The Server MAY allow ClientId's that contain more than 23 encoded bytes
    // The Server MAY allow ClientId's that contain characters not included in the list given above

    sessionCtx.clientId.id match {
      case clientIdentifierFormat(_*) =>
        // [MQTT-3.1.3-7] If the Client supplies a zero-byte ClientId, the Client MUST also set CleanSession to 1
        // [MQTT-3.1.3-8] If the Client supplies a zero-byte ClientId with CleanSession set to 0, the Server MUST respond
        // to the CONNECT packet with a CONNACK return code 0x02(Identifier rejected) and then close the NetworkConnection
        if (sessionCtx.clientId.id.length == 0) {
          if (sessionCtx.cleanSession) {
            false
          }
          else {
            // [MQTT-3.1.3-6] A Server MAY allow a Client to supply zero-length ClientId, however if it does
            // so the Server MUST treat this as a special case and assign a unique ClientId to that Client.
            // It MUST then process the CONNECT packet as if the Client had provided that unique ClientId
            val newClientId = sessionCtx.channelId.stringId+"."+channelCtx.channel.localAddress.toString
            sessionCtx.clientId = newClientId
            true
          }
        }
        else {
          true
        }
      case _ =>
        false
    }
  }
}
