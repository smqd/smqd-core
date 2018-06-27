package t2x.smqd.net.mqtt

import com.typesafe.scalalogging.StrictLogging
import io.netty.channel.{ChannelHandlerAdapter, ChannelHandlerContext}

/**
  * 2018. 6. 26. - Created by Kwon, Yeong Eon
  */
object MqttExceptionHandler {
  def apply() = new MqttExceptionHandler()
}

class MqttExceptionHandler extends ChannelHandlerAdapter with StrictLogging {

  override def exceptionCaught(ctx: ChannelHandlerContext, cause: Throwable): Unit = {
    val channelCtx = ctx.channel.attr(ATTR_SESSION_CTX).get
    if (channelCtx != null) {
      val channelId = channelCtx.channelId
      val sessionId = channelCtx.sessionId

      logger.error(s"[$sessionId] $channelId Unexpected Exception", cause)
    }
    else {
      logger.error("Unexpected exception", cause)
    }
    ctx.close()
  }
}
