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
import akka.pattern._
import akka.util.Timeout
import com.thing2x.smqd._
import com.thing2x.smqd.session.ChannelManagerActor.{CreateChannelActorRequest, CreateChannelActorResponse}
import com.thing2x.smqd.session.{ChannelManagerActor, SessionManagerActor}
import com.thing2x.smqd.util.ActorIdentifying
import com.typesafe.scalalogging.StrictLogging
import io.netty.channel.socket.SocketChannel
import io.netty.channel.{ChannelHandler, ChannelHandlerContext}
import io.netty.handler.codec.http.HttpServerCodec
import io.netty.handler.ssl.SslHandler

import scala.concurrent.{Await, Future}
import scala.concurrent.duration._
import scala.language.postfixOps

// 2018. 5. 30. - Created by Kwon, Yeong Eon

/**
  *
  */
class MqttWsChannelInitializer(smqd: Smqd,
                               listenerName: String,
                               sslProvider: Option[TlsProvider],
                               channelBpsCounter: ChannelHandler,
                               channelTpsCounter: ChannelHandler,
                               messageMaxSize: Int,
                               metrics: MqttMetrics)
  extends io.netty.channel.ChannelInitializer[SocketChannel]
    with ActorIdentifying
    with StrictLogging {

  import smqd.Implicit._

  private val sessionManager: ActorRef = identifyManagerActor(SessionManagerActor.actorName)
  private val channelManagerActor: ActorRef = identifyManagerActor(ChannelManagerActor.actorName)

  override def initChannel(ch: SocketChannel): Unit = {

    val pipeline = ch.pipeline

    val sslEngine = sslProvider match {
      case Some(provider) => provider.sslEngine
      case _ => None
    }

    if (sslEngine.isDefined) {
      pipeline.addLast(HANDLER_SSL, new SslHandler(sslEngine.get))
    }

    pipeline.addLast("httpServerCodec", new HttpServerCodec)
    pipeline.addLast("wsMqttHandshaker", new MqttWsHandshakeHandler(channelBpsCounter, channelTpsCounter, messageMaxSize))

    implicit val timeout: Timeout = 2.second
    val f = channelManagerActor.ask(CreateChannelActorRequest(ch, listenerName)).asInstanceOf[Future[CreateChannelActorResponse]]
    // intended synchronous waiting
    val channelActor = Await.result(f, timeout.duration).channelActor

    ch.attr(ATTR_CHANNEL_ACTOR).set(channelActor)
    ch.attr(ATTR_SESSION_MANAGER).set(sessionManager)
    ch.attr(ATTR_METRICS).set(metrics)
  }

  override def exceptionCaught(ctx: ChannelHandlerContext, cause: Throwable): Unit = {
    logger.error("Unexpected exception", cause)
    ctx.close()
  }
}
