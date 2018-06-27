package t2x.smqd.net.mqtt

import com.typesafe.scalalogging.StrictLogging
import io.netty.buffer.ByteBuf
import io.netty.channel.socket.SocketChannel
import io.netty.channel.{ChannelFuture, ChannelHandlerContext}
import io.netty.handler.codec.mqtt._
import t2x.smqd.QoS.QoS
import t2x.smqd._
import t2x.smqd.session.{SessionActor, SessionContext, SessionId}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.language.postfixOps


/**
  * 2018. 6. 3. - Created by Kwon, Yeong Eon
  */
object MqttSessionContext {
  def apply(channelContext: ChannelHandlerContext, smqd: Smqd) = new MqttSessionContext(channelContext, smqd)
}

class MqttSessionContext(channelContext: ChannelHandlerContext, val smqd: Smqd) extends SessionContext with StrictLogging {
  val channelId = MqttChannelId(channelContext.channel().asInstanceOf[SocketChannel].remoteAddress())

  private var _haveConnectMessage: Boolean = false
  def haveConnectMessage: Boolean = _haveConnectMessage
  def haveConnectMessage_= (flag: Boolean): Unit = {
    if (_haveConnectMessage) return // allow only one time change
    _haveConnectMessage = flag
  }

  private var _clientId: SessionId = _
  override def sessionId: SessionId = _clientId
  def sessionId_= (id: String): Unit = {
    _clientId = SessionId(id)
  }
  def sessionId_= (id: SessionId): Unit = {
    _clientId = id
  }

  var userName: Option[String] = None
  var password: Option[Array[Byte]] = None
  var authorized: Boolean = false

  private var _isCleanSession: Boolean = false
  override def isCleanSession: Boolean = _isCleanSession
  def isCleanSession_= (flag: Boolean): Unit = _isCleanSession = flag

  var will: Option[Will] = None

  private def publishWill(ctx: ChannelHandlerContext): Unit = {
    if (!authorized) return

    will match {
      case Some(w) =>
        logger.debug(s"[$sessionId] $channelId publish Will: [${w.topicPath}] isRetain=${w.retain} msg=${w.msg}")
        smqd.publish(RoutableMessage(w.topicPath, io.netty.buffer.Unpooled.copiedBuffer(w.msg), w.retain))
      case _ =>
    }
  }

  private var _lastTimeMessageReceived: Long = 0
  def lastTimeMessageReceived: Long = _lastTimeMessageReceived
  def lastTimeMessageReceived_= (time: Long): Unit = {
    _lastTimeMessageReceived = time

    val session = channelContext.channel.attr(ATTR_SESSION).get
    if (session != null) // only when a session actor exists
      session ! SessionActor.UpdateTimer
  }

  private var _keepAliveTimeSeconds: Int = 60
  override def keepAliveTimeSeconds: Int = _keepAliveTimeSeconds
  def keepAliveTimeSeconds_= (timeInSeconds: Int): Unit = _keepAliveTimeSeconds = math.min(math.max(timeInSeconds, 0), 0xFFFF)

  def cancelProtocolNotification(): Unit = {
    channelContext.pipeline.remove(PROTO_OUT_HANDLER)
    channelContext.pipeline.remove(PROTO_IN_HANDLER)
  }

  override def sessionStarted(): Unit = {
    logger.trace(s"[$sessionId] $channelId session started")
  }

  override def sessionStopped(): Unit = {
    logger.trace(s"[$sessionId] $channelId session stopped")
    channelContext.close()
  }

  override def sessionTimeout(): Unit = {
    logger.trace(s"[$sessionId] $channelId session timedout")
    channelContext.close()
  }

  channelContext.channel.closeFuture.addListener(channelClosed)

  private def channelClosed(future: ChannelFuture): Unit = {
    logger.debug(s"[$sessionId] $channelId channel closed (authorized = $authorized, isCleanSession = $isCleanSession, hasWill = ${will.isDefined})")

    if (authorized) {
      val session = channelContext.channel.attr(ATTR_SESSION).getAndSet(null)
      if (session != null)
        session ! SessionActor.ChannelClosed(isCleanSession)

      publishWill(channelContext)
    }
  }

  override def deliver(topic: String, qos: QoS, isRetain: Boolean, msgId: Int, msg: ByteBuf): Unit = {
    logger.trace(s"[$sessionId] $channelId Message Deliver: $topic qos:${qos.value} msgId: ($msgId) ${msg.capacity}")
    channelContext.channel.writeAndFlush(new MqttPublishMessage(
      new MqttFixedHeader(MqttMessageType.PUBLISH, false, qos, isRetain, 0),
      new MqttPublishVariableHeader(topic, msgId),
      msg
    ))
  }

  def deliverAck(msgId: Int): Unit = {
    logger.trace(s"[$sessionId] $channelId Message Ack: (mid: $msgId)")
    val session = channelContext.channel.attr(ATTR_SESSION).get
    session ! SessionActor.OutboundPublishAck(msgId)
  }

  def deliverRec(msgId: Int): Unit = {
    logger.trace(s"[$sessionId] $channelId Message Rec: (mid: $msgId)")
    val session = channelContext.channel.attr(ATTR_SESSION).get
    session ! SessionActor.OutboundPublishRec(msgId)
  }

  def deliverComp(msgId: Int): Unit = {
    logger.trace(s"[$sessionId] $channelId Message Comp: (mid: $msgId)")
    val session = channelContext.channel.attr(ATTR_SESSION).get
    session ! SessionActor.OutboundPublishComp(msgId)
  }
}