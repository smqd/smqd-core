package t2x.smqd.net.mqtt

import com.typesafe.scalalogging.StrictLogging
import io.netty.channel.{ChannelHandlerContext, ChannelInboundHandlerAdapter}
import io.netty.handler.codec.mqtt.MqttMessage

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
          logger.warn(s"[${sessionCtx.sessionId}] ${sessionCtx.channelId} unable to parse the mqtt message: $msg")
          ctx.close()
        }
      case _ =>
        val sessionCtx = ctx.channel.attr(ATTR_SESSION_CTX).get
        logger.warn(s"[${sessionCtx.sessionId}] ${sessionCtx.channelId} unknown message: $msg")
        ctx.close()
    }
  }
}
