package t2x.smqd.net.mqtt

import com.typesafe.scalalogging.StrictLogging
import io.netty.channel.{ChannelHandlerContext, ChannelInboundHandlerAdapter}
import io.netty.handler.codec.mqtt.MqttMessageType.{SUBACK, UNSUBACK}
import io.netty.handler.codec.mqtt.MqttQoS._
import io.netty.handler.codec.mqtt._
import t2x.smqd.QoS.QoS
import t2x.smqd.fault._
import t2x.smqd.session.SessionActor.{Subscribe, Subscription, Unsubscribe}

import scala.collection.JavaConverters._
import scala.concurrent.Promise
import scala.util.{Failure, Success}

/**
  * 2018. 6. 1. - Created by Kwon, Yeong Eon
  */
object MqttSubscribeHandler {
  def apply() = new MqttSubscribeHandler()
}

class MqttSubscribeHandler extends ChannelInboundHandlerAdapter with StrictLogging {

  override def channelRead(ctx: ChannelHandlerContext, msg: Any): Unit = {
    msg match {
      //////////////////////////////////
      // SUBSCRIBE(8)
      case m: MqttSubscribeMessage =>
        subscribe(ctx: ChannelHandlerContext, m)
        ctx.fireChannelReadComplete()

      //////////////////////////////////
      // UNSUBSCRIBE(10)
      case m: MqttUnsubscribeMessage =>
        unsubscribe(ctx: ChannelHandlerContext, m)
        ctx.fireChannelReadComplete()

      //////////////////////////////////
      // SUBACK(9), not allowed
      case _: MqttSubAckMessage =>
        val channelCtx = ctx.channel.attr(ATTR_SESSION_CTX).get
        channelCtx.smqd.notifyFault(NotAllowedMqttMessage("SUBACK"))
        ctx.close()

      //////////////////////////////////
      //UNSUBACK(11), not allowed
      case _: MqttUnsubAckMessage =>
        val channelCtx = ctx.channel.attr(ATTR_SESSION_CTX).get
        channelCtx.smqd.notifyFault(NotAllowedMqttMessage("UNSUBACK"))
        ctx.close()

      case _ =>
        ctx.fireChannelRead(msg)
    }
  }

  private def subscribe(ctx: ChannelHandlerContext, m: MqttSubscribeMessage): Unit = {
    val sessionActor = ctx.channel.attr(ATTR_SESSION).get
    val sessionCtx = ctx.channel.attr(ATTR_SESSION_CTX).get

    val vh = m.variableHeader()
    val msgId = vh.messageId()
    val pl = m.payload()
    val subs = pl.topicSubscriptions().asScala

    val subscriptions = subs.map { s =>
      Subscription(s.topicName(), s.qualityOfService())
    }

    import sessionCtx.smqd.gloablDispatcher

    val result = Promise[Seq[QoS]]

    sessionActor ! Subscribe(subscriptions, result)

    result.future.onComplete {
      case Success(qosList) =>
        val acks = qosList.map( _.value )
        ctx.writeAndFlush(
          new MqttSubAckMessage(
            new MqttFixedHeader(SUBACK, false, AT_MOST_ONCE, false, 0),
            MqttMessageIdVariableHeader.from(msgId),
            new MqttSubAckPayload(acks: _*)))

      case Failure(ex) =>
        logger.warn(s"Subscription failed: ${subscriptions.toString}", ex)
        ctx.close()
    }
  }

  private def unsubscribe(ctx: ChannelHandlerContext, m: MqttUnsubscribeMessage): Unit = {
    val sessionActor = ctx.channel.attr(ATTR_SESSION).get
    val sessionCtx = ctx.channel.attr(ATTR_SESSION_CTX).get

    val vh = m.variableHeader()
    val msgId = vh.messageId()
    val pl = m.payload()
    val unsubs = pl.topics().asScala

    import sessionCtx.smqd.gloablDispatcher

    val result = Promise[Seq[Boolean]]

    sessionActor ! Unsubscribe(unsubs, result)

    result.future.onComplete {
      case Success(results) =>
        ctx.writeAndFlush(
          new MqttUnsubAckMessage(
            new MqttFixedHeader(UNSUBACK,false, AT_MOST_ONCE, false, 0),
            MqttMessageIdVariableHeader.from(msgId)))

      case Failure(ex) =>
        logger.warn(s"Unsubscription failed: ${unsubs.toString}", ex)
        ctx.close()
    }
  }
}
