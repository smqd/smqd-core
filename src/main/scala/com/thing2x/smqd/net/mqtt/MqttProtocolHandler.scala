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
import io.netty.channel._
import io.netty.handler.codec.mqtt.MqttMessageType._
import io.netty.handler.codec.mqtt._
import com.thing2x.smqd.protocol._
import com.thing2x.smqd.session.SessionState

import scala.collection.JavaConverters._

/**
  * 2018. 5. 30. - Created by Kwon, Yeong Eon
  */
object MqttProtocolInboundHandler{
  def apply() = new MqttProtocolInboundHandler()
}

class MqttProtocolInboundHandler extends ChannelInboundHandlerAdapter with MqttProtocolNotifier with StrictLogging {

  override def channelRead(ctx: ChannelHandlerContext, msg: Any): Unit = {
    msg match {
      case m: MqttMessage =>
        notifyMessage(ctx, m, Recv)
        ctx.fireChannelRead(msg)
    }
  }

  override def userEventTriggered(ctx: ChannelHandlerContext, evt: scala.Any): Unit = {
    evt match {
      case RemoveProtocolHandler =>
        ctx.pipeline.remove(HANDLER_PROTO_OUT)
        ctx.pipeline.remove(HANDLER_PROTO_IN)
      case _ =>
        ctx.fireUserEventTriggered(evt)
    }
  }
}

object MqttProtocolOutboundHandler{
  def apply() = new MqttProtocolOutboundHandler()
}

class MqttProtocolOutboundHandler extends ChannelOutboundHandlerAdapter with MqttProtocolNotifier with StrictLogging {
  override def write(ctx: ChannelHandlerContext, msg: Any, promise: ChannelPromise): Unit = {
    msg match {
      case m: MqttMessage =>
        notifyMessage(ctx, m, Send)
    }

    super.write(ctx, msg, promise)
  }
}

trait MqttProtocolNotifier {

  def notifyMessage(handlerContext: ChannelHandlerContext, msg: MqttMessage, dir: ProtocolDirection): Unit = {
    val channelCtx = handlerContext.channel.attr(ATTR_SESSION_CTX).get
    val clientId = if (channelCtx.state >= SessionState.ConnectReceived) {
      channelCtx.clientId.toString
    } else {
      msg match {
        case _: MqttConnectMessage =>
          msg.asInstanceOf[MqttConnectMessage].payload.clientIdentifier
        case _ => "unknown_clientId"
      }
    }
    val channelId = channelCtx.channelId.stringId
    val msgType = msg.fixedHeader.messageType
    val msgDebug = msg match {
      case m: MqttConnectMessage => s"(cleanSession: ${m.variableHeader.isCleanSession}, will: ${m.variableHeader.isWillFlag})"
      case m: MqttConnAckMessage => s"(${m.variableHeader.connectReturnCode}, sessionPresent: ${m.variableHeader.isSessionPresent})"
      case _: MqttMessage if msgType == DISCONNECT => ""
      case _: MqttMessage if msgType == PINGREQ => ""
      case _: MqttMessage if msgType == PINGRESP => ""
      case _: MqttMessage if msgType == PUBREL || msgType == PUBREC || msgType == PUBCOMP =>
        msg.variableHeader match {
          case id: MqttMessageIdVariableHeader =>
            s"(mid: ${id.messageId.toString})"
          case _ => ""
        }
      case m: MqttSubscribeMessage => s"(mid: ${m.variableHeader.messageId}, ${stringify(m.payload.topicSubscriptions.asScala)})"
      case m: MqttSubAckMessage => s"(mid: ${m.variableHeader.messageId}, grantedQoS: ${m.payload.grantedQoSLevels()})"
      case m: MqttUnsubscribeMessage => s"(mid: ${m.variableHeader.messageId})"
      case m: MqttPublishMessage =>
        val fh = m.fixedHeader()
        val vh = m.variableHeader()
        s"(mid:${vh.packetId}, d:${fh.isDup}, q:${fh.qosLevel.value}, r:${fh.isRetain}, topic: ${vh.topicName})"
      case m: MqttPubAckMessage =>
        val vh = m.variableHeader()
        s"(mid:${vh.messageId})"
      case _ => msg.toString
    }

    channelCtx.smqd.notifyProtocol(MqttProtocolNotification(dir, clientId, channelId, msgType.toString, msgDebug))
  }


  private def stringify(subs: Seq[MqttTopicSubscription]): String = {
    subs.map(stringify).mkString("[", ",", "]")
  }

  private def stringify(sub: MqttTopicSubscription): String = {
    s"${sub.topicName} (${sub.qualityOfService().value})"
  }
}

case class MqttProtocolNotification(direction: ProtocolDirection, clientId: String, channelId: String, messageType: String, message: String) extends ProtocolNotification