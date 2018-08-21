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

import com.thing2x.smqd.QoS.QoS
import com.thing2x.smqd._
import com.thing2x.smqd.session.{SessionActor, SessionContext}
import com.typesafe.scalalogging.StrictLogging
import io.netty.buffer.ByteBuf
import io.netty.channel.embedded.EmbeddedChannel
import io.netty.channel.socket.SocketChannel
import io.netty.channel.{Channel, ChannelFuture}
import io.netty.handler.codec.mqtt.MqttMessageType.{PUBACK, PUBREC, PUBREL}
import io.netty.handler.codec.mqtt.MqttQoS.AT_LEAST_ONCE
import io.netty.handler.codec.mqtt._

import scala.concurrent.ExecutionContext.Implicits.global
import scala.language.postfixOps


/**
  * 2018. 6. 3. - Created by Kwon, Yeong Eon
  */
object MqttSessionContext {
  def apply(channel: Channel, smqd: Smqd, listenerName: String) =
    new MqttSessionContext(channel, smqd, listenerName)
}

import com.thing2x.smqd.session.SessionState
import com.thing2x.smqd.session.SessionState.{SessionState => State}

class MqttSessionContext(channel: Channel, val smqd: Smqd, listenerName: String)
  extends SessionContext with StrictLogging {

  val channelId: MqttChannelId = channel match {
    case socketChannel: SocketChannel =>
      MqttChannelId(listenerName, socketChannel.remoteAddress(), smqd.nodeName)
    case _: EmbeddedChannel =>
      MqttChannelId(listenerName)
  }

  private var _state: State = SessionState.Initiated
  override def state: State = _state
  def state_= (state: State): Unit = {
    import SessionState._
    _state = _state match {
      case Failed => Failed
      case Initiated if state != ConnectReceived => Failed
      case ConnectReceived if state != ConnectAcked => Failed
      case ConnectAcked => Failed
      case _ => state
    }
  }

  private var _clientId: ClientId = _
  override def clientId: ClientId = _clientId
  def clientId_= (id: String): Unit = {
    _clientId = ClientId(id, channelId.stringId)
  }
  def clientId_= (id: ClientId): Unit = {
    _clientId = id
  }

  var userName: Option[String] = None
  var password: Option[Array[Byte]] = None
  var authorized: Boolean = false

  private var _isCleanSession: Boolean = false
  override def cleanSession: Boolean = _isCleanSession
  def cleanSession_= (flag: Boolean): Unit = _isCleanSession = flag

  var will: Option[Will] = None

  private var _keepAliveTimeSeconds: Int = 0
  override def keepAliveTimeSeconds: Int = _keepAliveTimeSeconds
  def keepAliveTimeSeconds_= (timeInSeconds: Int): Unit = _keepAliveTimeSeconds = math.min(math.max(timeInSeconds, 0), 0xFFFF)

  /**
    * Called by SessionActor when it removes protocol handlers to prevent infinite echo in logging
    */
  def removeProtocolNotification(): Unit = {
    channel.pipeline.fireUserEventTriggered(RemoveProtocolHandler)
  }

  /**
    * Called by SessionActor when it started in `preStart()`
    */
  override def sessionStarted(): Unit = {
    logger.trace(s"[$clientId] session started")
  }

  /**
    * Called by SessionActor when it stopped in `postStop()`
    */
  override def sessionStopped(): Unit = {
    logger.trace(s"[$clientId] session stopped")
    if (channel.isOpen && !channel.eventLoop().isShutdown)
      channel.close()
  }

  /**
    * Called by SessionActor when it force to close the connection
    * @param reason why SessionActor decide to close the connection
    */
  override def close(reason: String): Unit = {
    logger.trace(s"[$clientId] session disconnect: $reason")
    if (channel.isOpen)
      channel.close()
  }

  override def writePub(topic: String, qos: QoS, isRetain: Boolean, msgId: Int, payload: ByteBuf): Unit = {
    logger.trace(s"[$clientId] Message Deliver: $topic qos:${qos.value} msgId: $msgId length: ${payload.readableBytes}")
    channel.writeAndFlush(
      MqttMessageBuilders.publish.topicName(topic).messageId(msgId)
        .retained(isRetain).qos(qos).payload(payload).build)
  }

  override def writePubRel(msgId: Int): Unit = {
    channel.writeAndFlush(new MqttMessage(
      new MqttFixedHeader(PUBREL, false, AT_LEAST_ONCE, false, 0),
      MqttMessageIdVariableHeader.from(msgId)
    ))
  }

  override def writePubAck(msgId: Int): Unit = {
    channel.writeAndFlush(new MqttPubAckMessage(
      new MqttFixedHeader(PUBACK, false, AT_LEAST_ONCE, false, 0),
      MqttMessageIdVariableHeader.from(msgId)))
  }

  override def writePubRec(msgId: Int): Unit = {
    channel.writeAndFlush(new MqttMessage(
      new MqttFixedHeader(PUBREC, false, AT_LEAST_ONCE, false, 0),
      MqttMessageIdVariableHeader.from(msgId)))
  }
}