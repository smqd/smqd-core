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

  override def channelRead(ctx: ChannelHandlerContext, msg: Any): Unit = {
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
        publish(ctx, m)
        ctx.fireChannelReadComplete()

      ///////////////////////////////////
      // PUBACK(4)
      case m: MqttPubAckMessage =>
        publishAck(ctx, m)
        ctx.fireChannelReadComplete()

      ///////////////////////////////////
      // PUBREL(6)
      case m: MqttMessage if m.fixedHeader().messageType() == PUBREL =>
        publishRel(ctx, m)
        ctx.fireChannelReadComplete()

      ///////////////////////////////////
      // PUBREC(5)
      case m: MqttMessage if m.fixedHeader().messageType() == PUBREC =>
        publishRec(ctx, m)
        ctx.fireChannelReadComplete()

      ///////////////////////////////////
      // PUBCOMP(7)
      case m: MqttMessage if m.fixedHeader().messageType() == PUBCOMP =>
        publishComp(ctx, m)
        ctx.fireChannelReadComplete()

      case _ =>
        ctx.fireChannelRead(msg)
    }
  }

  //// Scenario: Receiving Message from Client, QoS 0,1,2
  private def publish(ctx: ChannelHandlerContext, m: MqttPublishMessage): Unit = {

    val fh = m.fixedHeader()
    val isDup = fh.isDup
    val qosLevel = fh.qosLevel()
    val isRetain = fh.isRetain
    val vh = m.variableHeader()
    val pktId = vh.packetId()
    val topicName = vh.topicName()
    val payload = m.payload()

    val topicPath = TPath.parseForTopic(topicName) match {
      case Some(tp) => tp
      case _ => // invalid topic name
        val sessionCtx = ctx.channel.attr(ATTR_SESSION_CTX).get
        sessionCtx.smqd.notifyFault(InvalidTopicToPublish(sessionCtx.clientId.toString, topicName))
        ctx.close()
        return
    }

    // AuthorizePublish
    val sessionCtx = ctx.channel.attr(ATTR_SESSION_CTX).get
    import sessionCtx.smqd.Implicit._

    sessionCtx.smqd.allowPublish(topicPath, sessionCtx.clientId, sessionCtx.userName).onComplete {

      case Success(canPublish) if canPublish =>
        // this client has permission to publish message on this topic
        //
        // [MQTT-3.3.5-2] If a Server implementation does not authorize a PUBLISH to be performed by a Client;
        // It has no way of informing that Client. It MUST either make a positive acknowledgement, according to the
        // normal QoS rules, or close the Network Connection.
        val sessionActor = ctx.channel.attr(ATTR_SESSION).get
        val promise = Promise[Unit]
        sessionActor ! InboundPublish(topicPath, qosLevel, isRetain, payload, promise)

        promise.future.onComplete {
          case Success(_) =>
            qosLevel match {
              case AT_MOST_ONCE =>  // 0,  no ack

              case AT_LEAST_ONCE => // 1,  ack
                ctx.channel.writeAndFlush(new MqttPubAckMessage(
                  new MqttFixedHeader(PUBACK, false, AT_LEAST_ONCE, false, 0),
                  MqttMessageIdVariableHeader.from(pktId)))

              case EXACTLY_ONCE => // 2, exactly_once
                ctx.channel.writeAndFlush(new MqttMessage(
                  new MqttFixedHeader(PUBREC, false, AT_LEAST_ONCE, false, 0),
                  MqttMessageIdVariableHeader.from(pktId)))

              case _ =>
            }

          case Failure(_) =>
        }

      case _ =>
        sessionCtx.smqd.notifyFault(InvalidTopicToPublish(sessionCtx.clientId.toString, topicName))
        ctx.close()
    }
  }

  //// Scenario: Receiving Message from Client, QoS 2
  private def publishRel(ctx: ChannelHandlerContext, m: MqttMessage): Unit = {
    m.variableHeader match {
      case id: MqttMessageIdVariableHeader =>
        val msgId = id.messageId
        ctx.writeAndFlush(new MqttMessage(
          new MqttFixedHeader(PUBCOMP, false, AT_LEAST_ONCE, false, 0),
          MqttMessageIdVariableHeader.from(msgId)))
      case _ => // malformed PUBREL message, no message id
        val channelCtx = ctx.channel.attr(ATTR_SESSION_CTX).get
        channelCtx.smqd.notifyFault(MalformedMessage("no message id in PUBREL"))
        ctx.close()
    }
  }

  //// Scenario: Sending Message to Client, QoS 1
  private def publishAck(ctx: ChannelHandlerContext, m: MqttPubAckMessage): Unit = {
    val channelCtx = ctx.channel.attr(ATTR_SESSION_CTX).get
    val msgId = m.variableHeader().messageId()
    channelCtx.deliverAck(msgId)
  }

  //// Scenario: Sending Message to Client: QoS 2 (part 2)
  private def publishRec(ctx: ChannelHandlerContext, m: MqttMessage): Unit = {
    // send PUBREL
    m.variableHeader match {
      case id: MqttMessageIdVariableHeader =>
        val msgId = id.messageId
        val channelCtx = ctx.channel.attr(ATTR_SESSION_CTX).get
        channelCtx.deliverRec(msgId)
        ctx.writeAndFlush(new MqttMessage(
          new MqttFixedHeader(PUBREL, false, AT_LEAST_ONCE, false, 0),
          MqttMessageIdVariableHeader.from(msgId)
        ))
      case _ => // maformed PUBREC message, no message id
        val channelCtx = ctx.channel.attr(ATTR_SESSION_CTX).get
        channelCtx.smqd.notifyFault(MalformedMessage("no message id in PUBREC"))
        ctx.close()
    }
  }

  //// Scenario: Sending Message to Client: QoS 2 (part 3)
  private def publishComp(ctx: ChannelHandlerContext, m: MqttMessage): Unit = {
    m.variableHeader match {
      case id: MqttMessageIdVariableHeader =>
        val msgId = id.messageId
        val channelCtx = ctx.channel.attr(ATTR_SESSION_CTX).get
        channelCtx.deliverComp(msgId)
      case _ =>
        val channelCtx = ctx.channel.attr(ATTR_SESSION_CTX).get
        channelCtx.smqd.notifyFault(MalformedMessage("no message id in PUBCOMP"))
        ctx.close()
    }
  }
}

