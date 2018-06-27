package t2x.smqd.net.mqtt

import akka.actor.ActorRef
import com.typesafe.scalalogging.StrictLogging
import io.netty.channel.socket.SocketChannel
import io.netty.channel.{ChannelHandler, ChannelHandlerContext, ChannelInitializer, ChannelPipeline}
import io.netty.handler.codec.mqtt._
import io.netty.handler.timeout.IdleStateHandler
import javax.net.ssl.SSLEngine
import t2x.smqd._
import t2x.smqd.session.SessionManagerActor
import t2x.smqd.util.ActorIdentifying

import scala.concurrent.ExecutionContext.Implicits.global
import scala.language.postfixOps
import scala.util.matching.Regex

/**
  * 2018. 5. 30. - Created by Kwon, Yeong Eon
  */

class MqttChannelInitializer(smqd: Smqd,
                             sslProvider: Option[SmqTlsProvider],
                             channelBpsCounter: ChannelHandler,
                             channelTpsCounter: ChannelHandler,
                             messageMaxSize: Int,
                             clientIdentifierFormat: Regex,
                             defaultKeepAliveTime: Int,
                             metrics: MqttMetrics)
  extends ChannelInitializer[SocketChannel]
    with MqttPipelineAppender
    with ActorIdentifying
    with StrictLogging {

  private val sessionManager: ActorRef = identifyActor("user/"+ChiefActor.actorName+"/"+SessionManagerActor.actorName)(smqd.system)

  override def initChannel(ch: SocketChannel): Unit = {

    val pipeline = ch.pipeline
    val sslEngine = sslProvider match {
      case Some(provider) => provider.sslEngine
      case _ => None
    }
    appendMqttPipeline(pipeline, sslEngine, channelBpsCounter, channelTpsCounter, messageMaxSize, clientIdentifierFormat)
  }

  override def handlerAdded(ctx: ChannelHandlerContext): Unit = {
    super.handlerAdded(ctx)

    val channelContext = MqttSessionContext(ctx, smqd)
    channelContext.keepAliveTimeSeconds = defaultKeepAliveTime

    ctx.channel.attr(ATTR_SESSION_CTX).set(channelContext)
    ctx.channel.attr(ATTR_SESSION_MANAGER).set(sessionManager)
    ctx.channel.attr(ATTR_METRICS).set(metrics)
  }

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
