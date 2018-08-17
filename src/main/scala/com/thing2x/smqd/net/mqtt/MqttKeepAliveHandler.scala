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
import io.netty.handler.codec.mqtt.MqttMessage
import io.netty.handler.timeout.{IdleState, IdleStateEvent}

/**
  * 2018. 6. 26. - Created by Kwon, Yeong Eon
  */
object MqttKeepAliveHandler {
  def apply() = new MqttKeepAliveHandler()
}

class MqttKeepAliveHandler extends ChannelInboundHandlerAdapter with StrictLogging {

  override def channelRead(ctx: ChannelHandlerContext, msg: Any): Unit = {
    msg match {
      case m: MqttMessage =>
        if (m.decoderResult().isSuccess) {
          // update last time message time for keep-alive-time validation
          val sessionCtx = ctx.channel.attr(ATTR_SESSION_CTX).get
          sessionCtx.lastTimeMessageReceived = System.currentTimeMillis()
          ctx.fireChannelRead(msg)
        }
        else {
          val sessionCtx = ctx.channel.attr(ATTR_SESSION_CTX).get
          logger.warn(s"[${sessionCtx.clientId}] ${sessionCtx.channelId} unable to parse the mqtt message: $msg")
          ctx.close()
        }
      case _ =>
        val sessionCtx = ctx.channel.attr(ATTR_SESSION_CTX).get
        logger.warn(s"[${sessionCtx.clientId}] ${sessionCtx.channelId} unknown message: $msg")
        ctx.close()
    }
  }

  override def userEventTriggered(ctx: ChannelHandlerContext, evt: scala.Any): Unit = {
    evt match {
      case idle: IdleStateEvent => // event from IdleStateHandler that comes from MqttConnectHandler
        idle.state match {
          case IdleState.READER_IDLE =>
            logger.debug(s"KeepAliveTimeout")
            ctx.close()
          case IdleState.WRITER_IDLE => // not use
            logger.debug(s"Idle State ==> ${idle.isFirst}")
          case IdleState.ALL_IDLE =>
            logger.debug(s"Idle State ==> ${idle.isFirst}")
        }
    }
  }
}
