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

import com.typesafe.scalalogging.StrictLogging
import io.netty.channel.{ChannelHandlerContext, ChannelInboundHandlerAdapter}
import io.netty.handler.codec.mqtt.MqttMessageType._
import io.netty.handler.codec.mqtt.MqttQoS._
import io.netty.handler.codec.mqtt._
import com.thing2x.smqd.TPath
import com.thing2x.smqd.fault._
import com.thing2x.smqd.session.SessionActor
import com.thing2x.smqd.session.SessionActor.InboundPublish

import scala.concurrent.Promise
import scala.util.{Failure, Success}

/**
  * 2018. 5. 30. - Created by Kwon, Yeong Eon
  */
object MqttPublishHandler{
  def apply() = new MqttPublishHandler()
}

class MqttPublishHandler extends ChannelInboundHandlerAdapter with StrictLogging {

  override def channelRead(handlerCtx: ChannelHandlerContext, msg: Any): Unit = {
    /*
        QoS 1)

         Client              Server            Client              Server
            |--> Publish     -->|                |<-- Publish     <--|
            |<-- PublishAck  <--|                |--> PublishAck  -->|

        QoS 2)

         Client              Server            Client              Server
            |--> Publish     -->|                |<-- Publish     <--|
            |<-- PublishRec  <--|                |--> PublishRec  -->|
            |--> PublishRel  -->|                |<-- PublishRel  <--|
            |<-- PublishComp <--|                |--> PublishComp -->|
     */
    msg match {
      ///////////////////////////////////
      // PUBLISH(3)
      case m: MqttPublishMessage =>
        publish(handlerCtx, m)
        handlerCtx.fireChannelReadComplete()

      ///////////////////////////////////
      // PUBACK(4)
      case m: MqttPubAckMessage =>
        publishAck(handlerCtx, m)
        handlerCtx.fireChannelReadComplete()

      ///////////////////////////////////
      // PUBREL(6)
      case m: MqttMessage if m.fixedHeader().messageType() == PUBREL =>
        publishRel(handlerCtx, m)
        handlerCtx.fireChannelReadComplete()

      ///////////////////////////////////
      // PUBREC(5)
      case m: MqttMessage if m.fixedHeader().messageType() == PUBREC =>
        publishRec(handlerCtx, m)
        handlerCtx.fireChannelReadComplete()

      ///////////////////////////////////
      // PUBCOMP(7)
      case m: MqttMessage if m.fixedHeader().messageType() == PUBCOMP =>
        publishComp(handlerCtx, m)
        handlerCtx.fireChannelReadComplete()

      case _ =>
        handlerCtx.fireChannelRead(msg)
    }
  }

  //// Scenario: Receiving Message from Client, QoS 0,1,2
  private def publish(handlerCtx: ChannelHandlerContext, m: MqttPublishMessage): Unit = {

    val fh = m.fixedHeader()
    val isDup = fh.isDup
    val qosLevel = fh.qosLevel()
    val isRetain = fh.isRetain
    val vh = m.variableHeader()
    val pktId = vh.packetId()
    val topicName = vh.topicName()
    val payload = m.payload()

    val sessionCtx = handlerCtx.channel.attr(ATTR_SESSION_CTX).get
    val sessionActor = handlerCtx.channel.attr(ATTR_SESSION).get

    TPath.parseForTopic(topicName) match {
      case Some(topicPath) => // valid topic name
        import sessionCtx.smqd.Implicit._

        // check if this client has permission to publish messages on this topic
        //
        // [MQTT-3.3.5-2] If a Server implementation does not authorize a PUBLISH to be performed by a Client;
        // It has no way of informing that Client. It MUST either make a positive acknowledgement, according to the
        // normal QoS rules, or close the Network Connection.
        sessionCtx.smqd.allowPublish(topicPath, sessionCtx.clientId, sessionCtx.userName).onComplete {
          case Success(canPublish) if canPublish =>
            // publishing is authorized
            val array = new Array[Byte](payload.readableBytes)
            payload.readBytes(array)
            payload.release()

            sessionActor ! InboundPublish(topicPath, qosLevel, isRetain, isDup, array, pktId)
          case _ =>
            // publishing is not authorized
            sessionCtx.smqd.notifyFault(InvalidTopicToPublish(sessionCtx.clientId.toString, topicName))
            sessionCtx.close(s"publishing message on prohibited topic $topicName")
        }
      case _ => // invalid topic name
        sessionCtx.smqd.notifyFault(InvalidTopicToPublish(sessionCtx.clientId.toString, topicName))
        handlerCtx.close()
    }
  }

  //// Scenario: Receiving Message from Client, QoS 2
  private def publishRel(handlerCtx: ChannelHandlerContext, m: MqttMessage): Unit = {
    m.variableHeader match {
      case id: MqttMessageIdVariableHeader =>
        val msgId = id.messageId
        handlerCtx.writeAndFlush(new MqttMessage(
          new MqttFixedHeader(PUBCOMP, false, AT_LEAST_ONCE, false, 0),
          MqttMessageIdVariableHeader.from(msgId)))
      case _ => // malformed PUBREL message, no message id
        val sessionCtx = handlerCtx.channel.attr(ATTR_SESSION_CTX).get
        sessionCtx.smqd.notifyFault(MalformedMessage("no message id in PUBREL"))
        handlerCtx.close()
    }
  }

  //// Scenario: Sending Message to Client, QoS 1
  private def publishAck(handlerCtx: ChannelHandlerContext, m: MqttPubAckMessage): Unit = {
    val session = handlerCtx.channel.attr(ATTR_SESSION).get
    val msgId = m.variableHeader.messageId
    session ! SessionActor.OutboundPublishAck(msgId)
  }

  //// Scenario: Sending Message to Client: QoS 2 (part 2)
  private def publishRec(handlerCtx: ChannelHandlerContext, m: MqttMessage): Unit = {
    // send PUBREL
    m.variableHeader match {
      case id: MqttMessageIdVariableHeader =>
        val msgId = id.messageId
        val session = handlerCtx.channel.attr(ATTR_SESSION).get
        session ! SessionActor.OutboundPublishRec(msgId)
      case _ => // maformed PUBREC message, no message id
        val sessionCtx = handlerCtx.channel.attr(ATTR_SESSION_CTX).get
        sessionCtx.smqd.notifyFault(MalformedMessage("no message id in PUBREC"))
        handlerCtx.close()
    }
  }

  //// Scenario: Sending Message to Client: QoS 2 (part 3)
  private def publishComp(handlerCtx: ChannelHandlerContext, m: MqttMessage): Unit = {
    m.variableHeader match {
      case id: MqttMessageIdVariableHeader =>
        val msgId = id.messageId
        val session = handlerCtx.channel.attr(ATTR_SESSION).get
        session ! SessionActor.OutboundPublishComp(msgId)
      case _ =>
        val sessionCtx = handlerCtx.channel.attr(ATTR_SESSION_CTX).get
        sessionCtx.smqd.notifyFault(MalformedMessage("no message id in PUBCOMP"))
        handlerCtx.close()
    }
  }
}

