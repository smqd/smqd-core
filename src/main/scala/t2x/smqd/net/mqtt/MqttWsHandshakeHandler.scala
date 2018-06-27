package t2x.smqd.net.mqtt

import com.typesafe.scalalogging.StrictLogging
import io.netty.buffer.ByteBuf
import io.netty.channel._
import io.netty.handler.codec.http.HttpRequest
import io.netty.handler.codec.http.websocketx._
import io.netty.handler.codec.mqtt.{MqttDecoder, MqttEncoder}
import io.netty.handler.timeout.IdleStateHandler

import scala.util.matching.Regex

/**
  * 2018. 6. 25. - Created by Kwon, Yeong Eon
  */
class MqttWsHandshakeHandler(channelBpsCounter: ChannelHandler,
                             channelTpsCounter: ChannelHandler,
                             messageMaxSize: Int,
                             clientIdentifierFormat: Regex)
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

        if (!connection.equalsIgnoreCase("Upgrade") &&
          !upgrade.equalsIgnoreCase("WebSocket")) {
          ctx.close()
          return
        }

        logger.trace("Handshaking....")

        //Do the Handshake to upgrade connection from HTTP to WebSocket protocol
        val wsUrl = "ws://"+headers.get("Host")+req.uri
        val wsFactory = new WebSocketServerHandshakerFactory(wsUrl, "mqtt", true)

        handshaker = wsFactory.newHandshaker(req)
        if (handshaker == null) {
          WebSocketServerHandshakerFactory.sendUnsupportedVersionResponse(ctx.channel)
        }
        else {
          handshaker.handshake(ctx.channel, req).addListener { future: ChannelFuture =>
            logger.debug("Handshake is done")

            val pipeline = ctx.pipeline

            //Adding new handler to the existing pipeline to handle WebSocket Messages
            pipeline.replace(MqttWsHandshakeHandler.this, "WebSocketFrameInbound", new MqttWsFrameInboundHandler())
            pipeline.addLast("WebSocketFrameOutbound", new MqttWsFrameOutboundHandler())

            appendMqttPipeline(pipeline, None, channelBpsCounter, channelTpsCounter, messageMaxSize, clientIdentifierFormat)
          }
        }

      case _ =>
        logger.debug("Incoming request is unknown")
        ctx.close()
    }
  }
}

class MqttWsFrameInboundHandler extends ChannelInboundHandlerAdapter with StrictLogging {

  override def channelRead(ctx: ChannelHandlerContext, msg: Any): Unit = {
    msg match {
      case bf: BinaryWebSocketFrame =>
        logger.trace(s"BinaryWebSocketFrame Received : ${bf.content.readableBytes} bytes")
        ctx.fireChannelRead(bf.content)

      case tf: TextWebSocketFrame =>
        logger.trace(s"TextWebSocketFrame Received : ${tf.text}")
        ctx.fireChannelRead(tf.text)

      case ping: PingWebSocketFrame =>
        logger.trace(s"PingWebSocketFrame Received : ${ping.content}")

      case pong: PongWebSocketFrame =>
        logger.trace(s"PongWebSocketFrame Received : ${pong.content}")

      case cf: CloseWebSocketFrame =>
        logger.trace(s"CloseWebSocketFrame Received : ${cf.reasonText} ;${cf.statusCode}")

      case _ =>
        logger.debug(s"Received unsupported WebSocketFrame: ${msg.getClass.getName}")
    }
  }
}

class MqttWsFrameOutboundHandler extends ChannelOutboundHandlerAdapter with StrictLogging {

  override def write(ctx: ChannelHandlerContext, msg: scala.Any, promise: ChannelPromise): Unit = {
    msg match {
      case bb: ByteBuf =>
        logger.trace(s"BinaryWebSocketFrame Sending : ${bb.readableBytes} bytes")
        ctx.write(new BinaryWebSocketFrame(bb))

      case _ =>
        logger.debug(s"Sending unsupported WebSocketFrame: ${msg.getClass.getName}")
    }
  }
}