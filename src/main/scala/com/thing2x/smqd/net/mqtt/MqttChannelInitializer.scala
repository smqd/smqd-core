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

import akka.actor.ActorRef
import com.thing2x.smqd._
import com.thing2x.smqd.session.{ChannelManagerActor, SessionManagerActor}
import com.thing2x.smqd.util.ActorIdentifying
import com.typesafe.scalalogging.StrictLogging
import io.netty.channel.socket.SocketChannel
import io.netty.channel.{ChannelHandler, ChannelHandlerContext}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.language.postfixOps
import scala.util.matching.Regex

/**
  * 2018. 5. 30. - Created by Kwon, Yeong Eon
  */

class MqttChannelInitializer(smqd: Smqd,
                             listenerName: String,
                             sslProvider: Option[TlsProvider],
                             channelBpsCounter: ChannelHandler,
                             channelTpsCounter: ChannelHandler,
                             messageMaxSize: Int,
                             clientIdentifierFormat: Regex,
                             metrics: MqttMetrics)
  extends io.netty.channel.ChannelInitializer[SocketChannel]
    with com.thing2x.smqd.session.ChannelBuilder
    with MqttPipelineAppender
    with ActorIdentifying
    with StrictLogging {

  import smqd.Implicit._
  private val sessionManager: ActorRef = identifyManagerActor(SessionManagerActor.actorName)
  private val channelManagerActor: ActorRef = identifyManagerActor(ChannelManagerActor.actorName)
  private var channelManager: ChannelManagerActor = _

  channelManagerActor ! ChannelManagerActor.InitChannelBuilder(this)

  override def channelManager(manager: ChannelManagerActor): Unit = channelManager = manager

  override def initChannel(ch: SocketChannel): Unit = {
    val pipeline = ch.pipeline
    val sslEngine = sslProvider match {
      case Some(provider) => provider.sslEngine
      case _ => None
    }
    appendMqttPipeline(pipeline, sslEngine, channelBpsCounter, channelTpsCounter, messageMaxSize, clientIdentifierFormat)

    val sessionCtx = MqttSessionContext(ch, smqd, listenerName)
    val channelActor = channelManager.createChannelActor(ch)

    ch.attr(ATTR_CHANNEL_ACTOR).set(channelActor)
    ch.attr(ATTR_SESSION_CTX).set(sessionCtx)
    ch.attr(ATTR_SESSION_MANAGER).set(sessionManager)
    ch.attr(ATTR_METRICS).set(metrics)
  }

  override def exceptionCaught(ctx: ChannelHandlerContext, cause: Throwable): Unit = {
    val channelCtx = ctx.channel.attr(ATTR_SESSION_CTX).get
    if (channelCtx != null) {
      val channelId = channelCtx.channelId
      val sessionId = channelCtx.clientId

      logger.error(s"[$sessionId] $channelId Unexpected Exception", cause)
    }
    else {
      logger.error("Unexpected exception", cause)
    }
    ctx.close()
  }
}
