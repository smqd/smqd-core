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
import io.netty.buffer.ByteBuf
import io.netty.channel._
import io.netty.handler.codec.http.HttpRequest
import io.netty.handler.codec.http.websocketx._

/**
  * 2018. 6. 25. - Created by Kwon, Yeong Eon
  */
class MqttWsHandshakeHandler(channelBpsCounter: ChannelHandler,
                             channelTpsCounter: ChannelHandler,
                             messageMaxSize: Int)
  extends ChannelInboundHandlerAdapter
    with MqttPipelineAppender
    with StrictLogging {

  private var handshaker: WebSocketServerHandshaker = _

  override def channelRead(ctx: ChannelHandlerContext, msg: scala.Any): Unit = {
    msg match {
      case req: HttpRequest =>
        val headers = req.headers()
        val connection = headers.get("Connection")
        val upgrade = headers.get("Upgrade")

        logger.trace(s"Recv Http Request ${ctx.channel.toString}, Connection: $connection, Upgrade: $upgrade")

        if (connection == null || upgrade == null) {
          ctx.close()
          return
        }

        if (!connection.equalsIgnoreCase("Upgrade") &&
          !upgrade.equalsIgnoreCase("WebSocket")) {
          ctx.close()
          return
        }

        //logger.trace("Handshaking....")

        //Do the Handshake to upgrade connection from HTTP to WebSocket protocol
        val wsUrl = "ws://"+headers.get("Host")+req.uri
        val wsFactory = new WebSocketServerHandshakerFactory(wsUrl, "mqtt", true)

        handshaker = wsFactory.newHandshaker(req)
        if (handshaker == null) {
          WebSocketServerHandshakerFactory.sendUnsupportedVersionResponse(ctx.channel)
        }
        else {
          handshaker.handshake(ctx.channel, req).addListener { future: ChannelFuture =>
            if (future.isSuccess) {
              //logger.debug("Handshake has done")

              val pipeline = ctx.pipeline

              //Adding new handler to the existing pipeline to handle WebSocket Messages
              pipeline.replace(MqttWsHandshakeHandler.this, "WebSocketFrameInbound", new MqttWsFrameInboundHandler())
              pipeline.addLast("WebSocketFrameOutbound", new MqttWsFrameOutboundHandler())

              appendMqttPipeline(pipeline, None, channelBpsCounter, channelTpsCounter, messageMaxSize)
            }
            else {
              logger.debug("Handshake failed", future.cause)
              ctx.close()
            }
          }
        }

      case _ =>
        logger.debug("Incoming request is unknown")
        ctx.close()
    }
  }
}

class MqttWsFrameInboundHandler extends ChannelInboundHandlerAdapter with StrictLogging {

  private var lastFrameType = "n/a" // "bin", "txt", "n/a"

  override def channelRead(ctx: ChannelHandlerContext, msg: Any): Unit = {
    msg match {
      case bf: BinaryWebSocketFrame =>
        //logger.trace(s"BinaryWebSocketFrame Received : ${bf.content.readableBytes} bytes")
        ctx.fireChannelRead(bf.content)
        lastFrameType = "bin"

      case tf: TextWebSocketFrame =>
        //logger.trace(s"TextWebSocketFrame Received : ${tf.text}")
        ctx.fireChannelRead(tf.text)
        lastFrameType = "txt"

      case cf: ContinuationWebSocketFrame =>
        lastFrameType match {
          case "bin" =>
            ctx.fireChannelRead(cf.content)
          case "txt" =>
            ctx.fireChannelRead(cf.text)
          case _ =>
            logger.debug("Invalid state of continuation frame")
            ctx.close()
        }

      case ping: PingWebSocketFrame =>
        logger.trace(s"PingWebSocketFrame Received : ${ping.content}")
        ctx.fireChannelReadComplete()

      case pong: PongWebSocketFrame =>
        logger.trace(s"PongWebSocketFrame Received : ${pong.content}")
        ctx.fireChannelReadComplete()

      case cf: CloseWebSocketFrame =>
        logger.trace(s"CloseWebSocketFrame Received")
        ctx.close()

      case _ =>
        logger.debug(s"Received unsupported WebSocketFrame: ${msg.getClass.getName}")
        ctx.close()
    }
  }
}

class MqttWsFrameOutboundHandler extends ChannelOutboundHandlerAdapter with StrictLogging {

  override def write(ctx: ChannelHandlerContext, msg: scala.Any, promise: ChannelPromise): Unit = {
    msg match {
      case bb: ByteBuf =>
        ///logger.trace(s"BinaryWebSocketFrame Sending : ${bb.readableBytes} bytes")
        ctx.write(new BinaryWebSocketFrame(bb))

      case _ =>
        logger.debug(s"Sending unsupported WebSocketFrame: ${msg.getClass.getName}")
    }
  }
}