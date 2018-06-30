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

import akka.pattern.ask
import akka.util.Timeout
import com.typesafe.scalalogging.StrictLogging
import io.netty.channel.{ChannelHandlerContext, ChannelInboundHandlerAdapter}
import io.netty.handler.codec.mqtt.MqttConnectReturnCode._
import io.netty.handler.codec.mqtt.MqttMessageType._
import io.netty.handler.codec.mqtt.MqttQoS._
import io.netty.handler.codec.mqtt._
import com.thing2x.smqd._

import scala.util.{Failure, Success}
import com.thing2x.smqd.fault._
import com.thing2x.smqd.session.SessionManagerActor.{CreateSession, FindSession, SessionCreated, SessionFound}

import scala.concurrent.Future
import scala.concurrent.duration._
import scala.language.postfixOps
import scala.util.matching.Regex

/**
  * 2018. 5. 30. - Created by Kwon, Yeong Eon
  */

object MqttConnectHandler{
  def apply(clientIdentifierFormat: Regex) = new MqttConnectHandler(clientIdentifierFormat)
}

class MqttConnectHandler(clientIdentifierFormat: Regex) extends ChannelInboundHandlerAdapter with StrictLogging {

  override def channelRead(handlerCtx: ChannelHandlerContext, msg: Any): Unit = {

    msg match {
      //////////////////////////////////
      // CONNECT(1)
      case m: MqttConnectMessage =>
        processConnect(handlerCtx, m)

      //////////////////////////////////
      // CONNACK(2)
      case _: MqttConnAckMessage =>
        val channelCtx = handlerCtx.channel.attr(ATTR_SESSION_CTX).get
        channelCtx.smqd.notifyFault(NotAllowedMqttMessage("CONNACK"))
        handlerCtx.close()

      //////////////////////////////////
      // DISCONNECT(14)
      case m: MqttMessage if m.fixedHeader.messageType == DISCONNECT =>
        val session = handlerCtx.channel.attr(ATTR_SESSION).get
        val channelCtx = handlerCtx.channel.attr(ATTR_SESSION_CTX).get
        // [MQTT-3.14.4-3] Server MUST discard any Will Message associated with the current connection without publishing
        channelCtx.will = None
        handlerCtx.close()

      //////////////////////////////////
      // PINGREQ(12)
      case m: MqttMessage if m.fixedHeader.messageType == PINGREQ =>
        val session = handlerCtx.channel.attr(ATTR_SESSION).get
        // [MQTT-3.12.4-1] Server MUST send a PINGRESP Packet in response to a PINGREQ Packet
        val rsp = new MqttMessage(new MqttFixedHeader(PINGRESP, false, AT_MOST_ONCE, false, 0))
        handlerCtx.channel.writeAndFlush(rsp)
        handlerCtx.fireChannelReadComplete()

      //////////////////////////////////
      // PINGRESP(13)
      case m: MqttMessage if m.fixedHeader.messageType == PINGRESP =>
        val channelCtx = handlerCtx.channel.attr(ATTR_SESSION_CTX).get
        channelCtx.smqd.notifyFault(NotAllowedMqttMessage("PINGRESP"))
        handlerCtx.close()

      //////////////////////////////////
      // other messages
      case _ =>
        handlerCtx.fireChannelRead(msg)
    }
  }

  private def processConnect(channelCtx: ChannelHandlerContext, m: MqttConnectMessage): Unit = {

    val sessionCtx = channelCtx.channel.attr(ATTR_SESSION_CTX).get

    // prevent multiple CONNECT messages
    if (sessionCtx.haveConnectMessage) {
      // [MQTT-3.1.0-2] A client can only send the CONNECT Packet once over a Network Connection.
      // The Server MUST process a second CONNECT Packet sent from Client as a protocol violation and disconnect the Client
      sessionCtx.smqd.notifyFault(MutipleConnectRejected)
      channelCtx.close()
      return
    }
    else {
      sessionCtx.haveConnectMessage = true
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

      channelCtx.writeAndFlush(new MqttConnAckMessage(
        new MqttFixedHeader(CONNACK, false, AT_MOST_ONCE, false, 0),
        new MqttConnAckVariableHeader(CONNECTION_REFUSED_UNACCEPTABLE_PROTOCOL_VERSION, false)))
      channelCtx.close()
      return
    }

    // Client Identifier
    sessionCtx.sessionId = pl.clientIdentifier

    // Clean Session
    sessionCtx.isCleanSession = vh.isCleanSession

    // Keep Alive Time
    sessionCtx.keepAliveTimeSeconds = vh.keepAliveTimeSeconds()

    // Will
    sessionCtx.will = if (vh.isWillFlag) {
      TPath.parseForTopic(pl.willTopic) match {
        case Some(willPath) =>
          Some(Will(willPath, vh.isWillRetain, pl.willMessageInBytes()))
        case _ =>
          sessionCtx.smqd.notifyFault(InvalidWillTopic(sessionCtx.sessionId.toString, pl.willTopic))
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
      sessionCtx.smqd.notifyFault(IdentifierRejected(sessionCtx.sessionId.toString, "clientid is not a valid format"))
      channelCtx.writeAndFlush(
        new MqttConnAckMessage(
          new MqttFixedHeader(CONNACK, false, AT_MOST_ONCE, false, 0),
          new MqttConnAckVariableHeader(MqttConnectReturnCode.CONNECTION_REFUSED_IDENTIFIER_REJECTED, false)))
      channelCtx.close()
      return
    }

    // Authentication
    val hasUserName = vh.hasUserName
    val hasPassword = vh.hasPassword
    sessionCtx.userName = if (hasUserName) Some(pl.userName) else None
    sessionCtx.password = if (hasPassword) Some(pl.passwordInBytes) else None

    import sessionCtx.smqd.gloablDispatcher

    // [MQTT-3.1.3-9] If the Server rejects the ClientId it MUST respond to the CONNECT Packet with a CONNACK
    //                return code 0x02 (Identifier rejected) and then close the Network Connection
    // [MQTT-3.1.4-2] If the ClientId represents a Client already connected to the Sever then the Server MUST disconnect the existing Client
    // [MQTT-3.1.4-3] The Server MUST perform the processing of CleanSession that is described in section 3.1.2.4
    //                Start message delivery and keep alive monitoring
    sessionCtx.smqd.authenticate(sessionCtx.sessionId.toString, sessionCtx.userName, sessionCtx.password).onComplete {
      case Success(result) if result == SmqSuccess =>
        sessionCtx.authorized = true
        // create a new session or restore previous session
        val sessionPresentFuture = getOrCreateSession(channelCtx)
        sessionPresentFuture map { sessionPresent =>
          // send CONNACK
          channelCtx.channel.writeAndFlush(
            new MqttConnAckMessage(
              new MqttFixedHeader(CONNACK, false, AT_MOST_ONCE, false, 0),
              new MqttConnAckVariableHeader(CONNECTION_ACCEPTED, sessionPresent)))

          channelCtx.fireChannelReadComplete()
        }

      case Success(result) =>
        sessionCtx.smqd.notifyFault(result)
        val code = result match {
          case _: IdentifierRejected => CONNECTION_REFUSED_IDENTIFIER_REJECTED // 0x02
          case ServerUnavailable => CONNECTION_REFUSED_SERVER_UNAVAILABLE // 0x03
          case _: BadUserNameOrPassword => CONNECTION_REFUSED_BAD_USER_NAME_OR_PASSWORD // 0x04
          case _: NotAuthorized => CONNECTION_REFUSED_NOT_AUTHORIZED // 0x05
          case _ => CONNECTION_REFUSED_NOT_AUTHORIZED // 0x05
        }
        channelCtx.channel.writeAndFlush(
          new MqttConnAckMessage(
            new MqttFixedHeader(CONNACK, false, AT_MOST_ONCE, false, 0),
            new MqttConnAckVariableHeader(code, false)))
        channelCtx.fireChannelReadComplete()

      case Failure(_) =>
        channelCtx.fireChannelReadComplete()
        channelCtx.close()
    }
  }


  private def isValidClientIdentifierFormat(handlerCtx: ChannelHandlerContext): Boolean = {

    val channelCtx = handlerCtx.channel.attr(ATTR_SESSION_CTX).get

    // [MQTT-3.1.3-3] The Client Identifier (ClientId) MUST be present and MUST be the first field in the CONNECT packet payload
    // [MQTT-3.1.3-4] The ClientId MUST be a UTF-8 encoded string
    // [MQTT-3.1.3-5] allows only [0-9a-zA-Z]{0-23}
    // The Server MAY allow ClientId's that contain more than 23 encoded bytes
    // The Server MAY allow ClientId's that contain characters not included in the list given above

    channelCtx.sessionId.id match {
      case clientIdentifierFormat(_*) =>
        // [MQTT-3.1.3-7] If the Client supplies a zero-byte ClientId, the Client MUST also set CleanSession to 1
        // [MQTT-3.1.3-8] If the Client supplies a zero-byte ClientId with CleanSession set to 0, the Server MUST respond
        // to the CONNECT packet with a CONNACK return code 0x02(Identifier rejected) and then close the NetworkConnection
        if (channelCtx.sessionId.id.length == 0) {
          if (channelCtx.isCleanSession) {
            false
          }
          else {
            // [MQTT-3.1.3-6] A Server MAY allow a Client to supply zero-length ClientId, however if it does
            // so the Server MUST treat this as a special case and assign a unique ClientId to that Client.
            // It MUST then process the CONNECT packet as if the Client had provided that unique ClientId
            val newClientId = channelCtx.channelId.stringId+"@"+handlerCtx.channel.localAddress.toString
            channelCtx.sessionId = newClientId
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

  /**
    * MqttChannelHandler and ChannelHandlerContext's life is as long as the physical connection. But a session can
    * lives longer across a connection after another connection
    *
    * this method is called when CONNECT packet pass the authentication process, apply mqtt cleanSession rule
    *
    * @return true if previous session exists, then restored or removed correctly according to the cleanSession option
    *         false if previous session doesn't exist
    */
  private def getOrCreateSession(channelCtx: ChannelHandlerContext): Future[Boolean] = {
    val sessionCtx = channelCtx.channel.attr(ATTR_SESSION_CTX).get
    val sessionManager = channelCtx.channel.attr(ATTR_SESSION_MANAGER).get

    // [MQTT-3.1.3-2] Each Client connecting to the Server has a unique ClientId. The ClientId MUST be used by Clients
    // and by Servers to identify state that they hold relating to this MQTT Session between the Client and the Server

    if (sessionCtx.isCleanSession){
      // [MQTT-3.1.2-6] If CleanSession is set to 1, the Client and Server MUST discard any previous Session and start
      // a new one. This Session lasts as long as the Network Connection. State data associated with this Session
      // MUST NOT be resused in any subsequent Session

      // [MQTT-3.2.2-1] If the Server accepts a connection with CleanSession set to 1, the Server MUST set
      // Session Present to 0 in the CONNACK packet in addition to setting a zero return code in CONNACK packet

      import sessionCtx.smqd.gloablDispatcher
      implicit val timeout: Timeout = 1.second
      sessionManager ? CreateSession(sessionCtx) map {
        case SessionCreated(clientId, sessionActor) =>
          logger.debug(s"[$clientId] Session created(1) ${sessionActor.path}")
          channelCtx.channel.attr(ATTR_SESSION).set(sessionActor)
          false
      }
    }
    else {
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

      implicit val timeout: Timeout = 1 second
      implicit val ec = sessionCtx.smqd.system.dispatchers.defaultGlobalDispatcher
      val clientId = sessionCtx.sessionId.id

      sessionManager ? FindSession(sessionCtx, createIfNotExist = true) map {
        case SessionFound(_, sessionActor) =>
          // TODO: restore previous subscriptions
          logger.debug(s"[$clientId] Session found ${sessionActor.path}")
          channelCtx.channel.attr(ATTR_SESSION).set(sessionActor)
          true
        case SessionCreated(_, sessionActor) =>
          logger.debug(s"[$clientId] Session created(2) ${sessionActor.path}")
          channelCtx.channel.attr(ATTR_SESSION).set(sessionActor)
          false
      }
    }
  }
}
