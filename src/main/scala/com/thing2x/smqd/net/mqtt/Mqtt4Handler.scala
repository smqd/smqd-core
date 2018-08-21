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
import io.netty.handler.codec.mqtt._
import io.netty.handler.timeout.IdleStateEvent

/**
  * 2018. 5. 30. - Created by Kwon, Yeong Eon
  */

object Mqtt4Handler{
  def apply() = new Mqtt4Handler
}

class Mqtt4Handler extends ChannelInboundHandlerAdapter with StrictLogging {

  override def channelRead(handlerCtx: ChannelHandlerContext, msg: Any): Unit = {
    msg match {
      case m: MqttMessage =>
        handlerCtx.fireChannelReadComplete()
        val channelActor = handlerCtx.channel.attr(ATTR_CHANNEL_ACTOR).get
        channelActor ! m
      case _ =>
        handlerCtx.fireChannelRead(msg)
    }
  }

  override def userEventTriggered(handlerCtx: ChannelHandlerContext, evt: Any): Unit = {
    evt match {
      case idle: IdleStateEvent => // event from IdleStateHandler that comes from MqttConnectHandler
        val channelActor = handlerCtx.channel.attr(ATTR_CHANNEL_ACTOR).get
        channelActor ! idle
      case _ =>
        handlerCtx.fireUserEventTriggered(evt)
    }
  }

}
