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

import akka.actor.{Actor, ActorRef, PoisonPill}
import akka.util.Timeout
import com.thing2x.smqd.QoS.QoS
import com.thing2x.smqd.fault._
import com.thing2x.smqd.session.SessionActor._
import com.thing2x.smqd.session.SessionManagerActor.{CreateSession, CreateSessionFailure, CreateSessionResult, CreatedSessionSuccess}
import com.thing2x.smqd.session.{SessionActor, SessionState}
import com.thing2x.smqd._
import com.typesafe.scalalogging.StrictLogging
import io.netty.channel.{Channel, ChannelFuture}
import io.netty.handler.codec.mqtt.MqttConnectReturnCode._
import io.netty.handler.codec.mqtt.MqttQoS.{AT_LEAST_ONCE, AT_MOST_ONCE}
import io.netty.handler.codec.mqtt._
import io.netty.handler.timeout.{IdleState, IdleStateEvent, IdleStateHandler}

import scala.jdk.CollectionConverters._
import scala.concurrent.Promise
import scala.concurrent.duration._
import scala.util.matching.Regex
import scala.util.{Failure, Success}

// 2018. 8. 21. - Created by Kwon, Yeong Eon

/** QoS 1)
  *
  * Client              Server            Client              Server
  * |--> Publish     -->|                |<-- Publish     <--|
  * |<-- PublishAck  <--|                |--> PublishAck  -->|
  *
  * QoS 2)
  *
  * Client              Server            Client              Server
  * |--> Publish     -->|                |<-- Publish     <--|
  * |<-- PublishRec  <--|                |--> PublishRec  -->|
  * |--> PublishRel  -->|                |<-- PublishRel  <--|
  * |<-- PublishComp <--|                |--> PublishComp -->|
  */

class Mqtt4ChannelActor(smqdInstance: Smqd, channel: Channel, listenerName: String) extends Actor with StrictLogging {

  private val clientIdentifierFormat: Regex = smqdInstance.config.getString("smqd.registry.client.identifier.format").r

  private var sessionActor: Option[ActorRef] = None
  private val sessionCtx = MqttSessionContext(channel, smqdInstance, listenerName)

  override def preStart(): Unit = {
    channel.attr(ATTR_SESSION_CTX).set(sessionCtx)

    channel.closeFuture().addListener((_: ChannelFuture) => self ! PoisonPill)
  }

  override def postStop(): Unit = {
    logger.debug(
      s"[${sessionCtx.clientId}] channel closed (authorized = ${sessionCtx.authorized}, isCleanSession = ${sessionCtx.cleanSession}, hasWill = ${sessionCtx.will.isDefined})"
    )

    if (channel.isOpen && !channel.eventLoop().isShutdown) {
      channel.close()
    }

    if (sessionCtx.authorized) {
      sessionActor match {
        case Some(actor) =>
          actor ! SessionActor.ChannelClosed(self, sessionCtx.cleanSession)
        case _ =>
      }

      publishWill()
    }
  }

  override def receive: Receive = {
    case m: MqttConnectMessage =>
      // the first control package should be Connect
      context.become(receive0)
      processConnect(m)
    case _ =>
      channel.close()
  }

  def receive0: Receive = {
    case m: MqttPublishMessage =>
      processPublish(m)
    case m: MqttPubAckMessage =>
      processPublishAck(m)
    case m: MqttSubscribeMessage =>
      processSubscribe(m)
    case m: MqttUnsubscribeMessage =>
      processUnsubscribe(m)
    case m: MqttMessage if m.fixedHeader.messageType == MqttMessageType.PUBREL =>
      processPublishRel(m)
    case m: MqttMessage if m.fixedHeader.messageType == MqttMessageType.PUBREC =>
      processPublishRec(m)
    case m: MqttMessage if m.fixedHeader.messageType == MqttMessageType.PUBCOMP =>
      processPublishComp(m)
    case m: MqttMessage if m.fixedHeader.messageType == MqttMessageType.DISCONNECT =>
      processDisconnect(m)
    case m: MqttMessage if m.fixedHeader.messageType == MqttMessageType.PINGREQ =>
      processPing(m)
    case evt: IdleStateEvent =>
      processIdleStateEvent(evt)

    // PINGRESP(13), not allowed
    case m: MqttMessage if m.fixedHeader.messageType == MqttMessageType.PINGRESP =>
      smqdInstance.notifyFault(NotAllowedMqttMessage("PINGRESP"))
      channel.close()
    // SUBACK(9), not allowed
    case _: MqttSubAckMessage =>
      smqdInstance.notifyFault(NotAllowedMqttMessage("SUBACK"))
      channel.close()
    // UNSUBACK(11), not allowed
    case _: MqttUnsubAckMessage =>
      smqdInstance.notifyFault(NotAllowedMqttMessage("UNSUBACK"))
      channel.close()
    // CONNACK(2), not allowed
    case _: MqttConnAckMessage =>
      smqdInstance.notifyFault(NotAllowedMqttMessage("CONNACK"))
      channel.close()
    // not allowed multiple CONNECT messages
    case _: MqttConnectMessage =>
      // [MQTT-3.1.0-2] A client can only send the CONNECT Packet once over a Network Connection.
      // The Server MUST process a second CONNECT Packet sent from Client as a protocol violation and disconnect the Client
      smqdInstance.notifyFault(MutipleConnectRejected)
      channel.close()
  }

  /*
  private def challengeChannel(replyTo: ActorRef, challenge: NewSessionChallenge): Unit = {
    logger.trace(s"[${sessionCtx.clientId}] challenged new channel by ${challenge.by}")
    if (challengingActor.isEmpty && (sessionCtx.state == SessionState.ConnectAcked || sessionCtx.state == SessionState.Failed)) {
      // if requestor (SessionManager) is in the same local node, it sends promise directly
      // in other case (remote node), reply as normal message
      replyTo ! NewSessionChallengeAccepted(sessionCtx.clientId)
      // the actor picks its successor
      challengingActor = Some(replyTo)
    }
    else {
      // if the session actor is during the CONNECT process or has already successor(challengingAcotr),
      // makes other challengers failed.
      replyTo ! NewSessionChallengeDenied(sessionCtx.clientId)
    }
  }
   */

  private def processConnect(m: MqttConnectMessage): Unit = {
    sessionCtx.state = SessionState.ConnectReceived

    val vh = m.variableHeader // variable header
    val pl = m.payload // payload

    // validate protocol version
    val protocolName = vh.name
    val protocolLevel = vh.version
    if (protocolLevel > PROTOCOL_LEVEL) {
      // [MQTT-3.1.2-2] The Server MUST respond to the CONNECT Packet with CONNACK return code 0x01
      // (unacceptable_protocol_level) and then disconnect the Client if the Protocol Level is not supported by the Server
      smqdInstance.notifyFault(UnacceptableProtocolVersion(protocolName, protocolLevel))
      channel.writeAndFlush(
        MqttMessageBuilders.connAck
          .returnCode(CONNECTION_REFUSED_UNACCEPTABLE_PROTOCOL_VERSION)
          .sessionPresent(false)
          .build
      )
      channel.close()
      return
    }

    // Client Identifier
    sessionCtx.clientId = pl.clientIdentifier

    // Clean Session
    sessionCtx.cleanSession = vh.isCleanSession

    // Keep Alive Time - replace existing IdleStateHandler with time value from Connect message.
    //                   The timeout event 'IdleStateEvent' will come to 'userEventTriggered()' in Mqtt4Handler.
    val keepAliveTimeSeconds = vh.keepAliveTimeSeconds
    channel.pipeline.replace(HANDLER_IDLE_STATE, HANDLER_IDLE_STATE, new IdleStateHandler((keepAliveTimeSeconds * 1.5).toInt, 0, 0))

    // Will
    sessionCtx.will = if (vh.isWillFlag) {
      TPath.parseForTopic(pl.willTopic) match {
        case Some(willPath) =>
          Some(Will(willPath, vh.isWillRetain, pl.willMessageInBytes()))
        case _ =>
          smqdInstance.notifyFault(InvalidWillTopic(sessionCtx.clientId.toString, pl.willTopic))
          None
      }
    } else {
      None
    }

    // Validate Client Identifier Regularations
    if (!isValidClientIdentifierFormat) {
      // [MQTT-3.1.3-9] If the Server rejects the ClientId it MUST respond to CONNECT Packet with a CONNACK
      // return code 0x02 (Identifier rejected) and then close the Network Connection
      smqdInstance.notifyFault(IdentifierRejected(sessionCtx.clientId.toString, "clientid is not a valid format"))
      channel.writeAndFlush(
        MqttMessageBuilders.connAck
          .returnCode(CONNECTION_REFUSED_IDENTIFIER_REJECTED)
          .sessionPresent(false)
          .build
      )
      channel.close()
      return
    }

    // Authentication
    val hasUserName = vh.hasUserName
    val hasPassword = vh.hasPassword
    sessionCtx.userName = if (hasUserName) Some(pl.userName) else None
    sessionCtx.password = if (hasPassword) Some(pl.passwordInBytes) else None

    import smqdInstance.Implicit._
    implicit val timeout: Timeout = 2.second

    // [MQTT-3.1.3-9] If the Server rejects the ClientId it MUST respond to the CONNECT Packet with a CONNACK
    //                return code 0x02 (Identifier rejected) and then close the Network Connection
    // [MQTT-3.1.4-2] If the ClientId represents a Client already connected to the Sever then the Server MUST disconnect the existing Client
    // [MQTT-3.1.4-3] The Server MUST perform the processing of CleanSession that is described in section 3.1.2.4
    //                Start message delivery and keep alive monitoring
    smqdInstance.clientLogin(sessionCtx.clientId, sessionCtx.userName, sessionCtx.password).onComplete {
      case Success(SmqSuccess(_)) =>
        sessionCtx.authorized = true

        val sessionManager = channel.attr(ATTR_SESSION_MANAGER).get

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
            sessionActor = Some(r.sessionActor)
            channel.writeAndFlush(
              MqttMessageBuilders.connAck
                .returnCode(CONNECTION_ACCEPTED)
                .sessionPresent(r.hadPreviousSession)
                .build
            )
            r.sessionActor ! SessionActor.ChannelOpened(self)

          case r: CreateSessionFailure => // fail to create a clean session
            logger.debug(s"[${r.clientId}] Session creation failed: ${r.reason}")
            smqdInstance.notifyFault(MutipleConnectRejected)
            channel.writeAndFlush(
              MqttMessageBuilders.connAck
                .returnCode(CONNECTION_REFUSED_IDENTIFIER_REJECTED)
                .sessionPresent(true)
                .build
            )
            channel.close()
        }

      case Success(result) => // if result != SmqSuccess
        smqdInstance.notifyFault(result)
        val code = result match {
          case _: IdentifierRejected    => CONNECTION_REFUSED_IDENTIFIER_REJECTED // 0x02
          case ServerUnavailable        => CONNECTION_REFUSED_SERVER_UNAVAILABLE // 0x03
          case _: BadUsernameOrPassword => CONNECTION_REFUSED_BAD_USER_NAME_OR_PASSWORD // 0x04
          case _: NotAuthorized         => CONNECTION_REFUSED_NOT_AUTHORIZED // 0x05
          case _                        => CONNECTION_REFUSED_NOT_AUTHORIZED // 0x05
        }
        channel.writeAndFlush(
          MqttMessageBuilders.connAck
            .returnCode(code)
            .sessionPresent(false)
            .build
        )
        channel.close()

      case Failure(_) =>
        channel.writeAndFlush(
          MqttMessageBuilders.connAck
            .returnCode(CONNECTION_REFUSED_SERVER_UNAVAILABLE)
            .sessionPresent(false)
            .build
        )
        channel.close()
    }

    sessionCtx.state = SessionState.ConnectAcked
  }

  private def processDisconnect(m: MqttMessage): Unit = {
    // [MQTT-3.14.4-3] Server MUST discard any Will Message associated with the current connection without publishing
    sessionCtx.will = None
    // FIXME: it is better to make the client to close socket first, to avoid TIME_WAIT
    sessionCtx.close("received Disconnect")
  }

  private def processPing(m: MqttMessage): Unit = {
    // [MQTT-3.12.4-1] Server MUST send a PINGRESP Packet in response to a PINGREQ Packet
    val rsp = new MqttMessage(new MqttFixedHeader(MqttMessageType.PINGRESP, false, AT_MOST_ONCE, false, 0))
    channel.writeAndFlush(rsp)
  }

  private def processSubscribe(m: MqttSubscribeMessage): Unit = {
    val vh = m.variableHeader()
    val msgId = vh.messageId()
    val pl = m.payload()
    val subs = pl.topicSubscriptions().asScala

    val subscriptions = subs.map { s =>
      Subscription(s.topicName(), s.qualityOfService())
    }.toSeq

    import smqdInstance.Implicit._

    val result = Promise[Seq[QoS]]()

    sessionActor match {
      case Some(actor) => actor ! Subscribe(subscriptions, result)
      case _           => logger.error("no session actor exists")
    }

    result.future.onComplete {
      case Success(qosList) =>
        val acks = qosList.map(_.value())
        channel.writeAndFlush(
          new MqttSubAckMessage(
            new MqttFixedHeader(MqttMessageType.SUBACK, false, AT_MOST_ONCE, false, 0),
            MqttMessageIdVariableHeader.from(msgId),
            new MqttSubAckPayload(acks: _*)
          )
        )

      case Failure(ex) =>
        logger.warn(s"Subscription failed: ${subscriptions.toString}", ex)
        channel.close()
    }
  }

  private def processUnsubscribe(m: MqttUnsubscribeMessage): Unit = {
    val vh = m.variableHeader()
    val msgId = vh.messageId()
    val pl = m.payload()
    val unsubs = pl.topics().asScala

    import smqdInstance.Implicit._

    val result = Promise[Seq[Boolean]]()

    sessionActor match {
      case Some(actor) => actor ! Unsubscribe(unsubs.toSeq, result)
      case _           => logger.error("no session actor exists")
    }

    result.future.onComplete {
      case Success(_) =>
        channel.writeAndFlush(new MqttUnsubAckMessage(new MqttFixedHeader(MqttMessageType.UNSUBACK, false, AT_MOST_ONCE, false, 0), MqttMessageIdVariableHeader.from(msgId)))

      case Failure(ex) =>
        logger.warn(s"Unsubscription failed: ${unsubs.toString}", ex)
        channel.close()
    }
  }

  //// Scenario: Receiving Message from Client, QoS 0,1,2
  private def processPublish(m: MqttPublishMessage): Unit = {

    val fh = m.fixedHeader()
    val isDup = fh.isDup
    val qosLevel = fh.qosLevel()
    val isRetain = fh.isRetain
    val vh = m.variableHeader()
    val pktId = vh.packetId()
    val topicName = vh.topicName()
    val payload = m.payload()

    TPath.parseForTopic(topicName) match {
      case Some(topicPath) => // valid topic name
        import smqdInstance.Implicit._

        // check if this client has permission to publish messages on this topic
        //
        // [MQTT-3.3.5-2] If a Server implementation does not authorize a PUBLISH to be performed by a Client;
        // It has no way of informing that Client. It MUST either make a positive acknowledgement, according to the
        // normal QoS rules, or close the Network Connection.
        smqdInstance.allowPublish(topicPath, sessionCtx.clientId, sessionCtx.userName).onComplete {
          case Success(canPublish) if canPublish =>
            // publishing is authorized
            val array = new Array[Byte](payload.readableBytes)
            payload.readBytes(array)
            payload.release()
            sessionActor match {
              case Some(actor) =>
                //logger.info(s"=========================== ${actor.path}")
                actor ! InboundPublish(topicPath, qosLevel, isRetain, isDup, array, pktId)
              case _ =>
                logger.error("no session actor exists")
            }
          case _ =>
            // publishing is not authorized
            smqdInstance.notifyFault(InvalidTopicToPublish(sessionCtx.clientId.toString, topicName))
            sessionCtx.close(s"publishing message on prohibited topic $topicName")
        }
      case _ => // invalid topic name
        smqdInstance.notifyFault(InvalidTopicToPublish(sessionCtx.clientId.toString, topicName))
        channel.close()
    }
  }

  //// Scenario: Receiving Message from Client, QoS 2
  private def processPublishRel(m: MqttMessage): Unit = {
    m.variableHeader match {
      case id: MqttMessageIdVariableHeader =>
        val msgId = id.messageId
        channel.writeAndFlush(new MqttMessage(new MqttFixedHeader(MqttMessageType.PUBCOMP, false, AT_LEAST_ONCE, false, 0), MqttMessageIdVariableHeader.from(msgId)))
      case _ => // malformed PUBREL message, no message id
        smqdInstance.notifyFault(MalformedMessage("no message id in PUBREL"))
        channel.close()
    }
  }

  //// Scenario: Sending Message to Client, QoS 1
  private def processPublishAck(m: MqttPubAckMessage): Unit = {
    sessionActor match {
      case Some(actor) =>
        val msgId = m.variableHeader.messageId
        actor ! SessionActor.OutboundPublishAck(msgId)
      case _ => logger.error("no session actor exists")
    }
  }

  //// Scenario: Sending Message to Client: QoS 2 (part 2)
  private def processPublishRec(m: MqttMessage): Unit = {
    // send PUBREL
    m.variableHeader match {
      case id: MqttMessageIdVariableHeader =>
        sessionActor match {
          case Some(actor) =>
            val msgId = id.messageId
            actor ! SessionActor.OutboundPublishRec(msgId)
          case _ => logger.error("no session actor exists")
        }
      case _ => // maformed PUBREC message, no message id
        smqdInstance.notifyFault(MalformedMessage("no message id in PUBREC"))
        channel.close()
    }
  }

  //// Scenario: Sending Message to Client: QoS 2 (part 3)
  private def processPublishComp(m: MqttMessage): Unit = {
    m.variableHeader match {
      case id: MqttMessageIdVariableHeader =>
        sessionActor match {
          case Some(actor) =>
            val msgId = id.messageId
            actor ! SessionActor.OutboundPublishComp(msgId)
          case _ => logger.error("no session actor exists")
        }
      case _ =>
        smqdInstance.notifyFault(MalformedMessage("no message id in PUBCOMP"))
        channel.close()
    }
  }

  private def publishWill(): Unit = {
    if (sessionCtx.authorized) {
      sessionCtx.will match {
        case Some(w) =>
          logger.debug(s"[${sessionCtx.clientId}] publish Will: [${w.topicPath}] isRetain=${w.retain} msg=${w.msg}")
          smqdInstance.publish(RoutableMessage(w.topicPath, io.netty.buffer.Unpooled.copiedBuffer(w.msg), w.retain))
        case _ =>
      }
    }
  }

  private def processIdleStateEvent(evt: IdleStateEvent): Unit = {
    evt.state match {
      case IdleState.READER_IDLE =>
        logger.debug(s"reader idle exceed close connection - ${channel.remoteAddress.toString}")
        channel.close()
      case IdleState.WRITER_IDLE => // not use yet - never happen
        logger.debug(s"Idle State ==> ${evt.isFirst}")
      case IdleState.ALL_IDLE => // not use yet - never happen
        logger.debug(s"Idle State ==> ${evt.isFirst}")
    }
  }

  private def isValidClientIdentifierFormat: Boolean = {
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
          } else {
            // [MQTT-3.1.3-6] A Server MAY allow a Client to supply zero-length ClientId, however if it does
            // so the Server MUST treat this as a special case and assign a unique ClientId to that Client.
            // It MUST then process the CONNECT packet as if the Client had provided that unique ClientId
            val newClientId = sessionCtx.channelId.stringId + "." + channel.localAddress.toString
            sessionCtx.clientId = newClientId
            true
          }
        } else {
          true
        }
      case _ =>
        false
    }
  }
}
