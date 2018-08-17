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

import akka.actor.ActorSystem
import akka.http.scaladsl.testkit.ScalatestRouteTest
import akka.testkit.TestKit
import com.thing2x.smqd.session.SessionManagerActor
import com.thing2x.smqd.{Smqd, SmqdBuilder}
import com.typesafe.config.{Config, ConfigFactory}
import com.typesafe.scalalogging.StrictLogging
import io.netty.channel.embedded.EmbeddedChannel
import io.netty.handler.codec.mqtt.{MqttDecoder, MqttEncoder}
import io.netty.handler.timeout.IdleStateHandler
import org.scalatest.{BeforeAndAfterAll, Matchers, WordSpec}
import spray.json.DefaultJsonProtocol

import scala.concurrent.Promise

// 2018. 8. 17. - Created by Kwon, Yeong Eon

/**
  *
  */
class MqttSimpleTest extends WordSpec
  with BeforeAndAfterAll
  with Matchers
  with ScalatestRouteTest
  with DefaultJsonProtocol
  with StrictLogging {

  val config: Config = ConfigFactory.parseString(
    """
      |
    """.stripMargin).withFallback(ConfigFactory.parseResources("smqd-ref.conf"))

  override def createActorSystem(): ActorSystem = ActorSystem(actorSystemNameFrom(getClass), config)

  private var smqdInstance: Smqd = _
  private var shutdownPromise = Promise[Boolean]
  private var channel: EmbeddedChannel = _

  override def beforeAll(): Unit = {

    smqdInstance = new SmqdBuilder(config)
      .setActorSystem(system)
      .build()

    smqdInstance.start()

    val sessionManager = smqdInstance.identifyManagerActor(SessionManagerActor.actorName)

    val messageMaxSize = 4 * 1024 * 1024
    val clientIdentifierFormat = "[0-9a-zA-Z-.*+:@&=,!~';.]+[0-9a-zA-Z-_*$%?+:@&=,!~';./]*".r

    val listenerName = "mqtt:test:embedded-1"
    val metrics = new MqttMetrics(listenerName.replace(":", "."))

    val channelBpsCounter = new MqttBpsCounter(true, 1*1024*1024, 30, 5)
    val channelTpsCounter = new MqttTpsCounter(true, 0, 30)

    channel = new EmbeddedChannel()
    val pipeline = channel.pipeline()
    pipeline.addLast(CHANNEL_BPS_HANDLER, channelBpsCounter)

    //    if (sslEngine.isDefined) {
    //      pipeline.addLast(SSL_HANDLER, new SslHandler(sslEngine.get))
    //    }

    pipeline.addLast(DECODING_HANDLER, new MqttDecoder(messageMaxSize))
    pipeline.addLast(ENCODING_HANDLER, MqttEncoder.INSTANCE)

    //pipeline.addLast("loggingHandler", new io.netty.handler.logging.LoggingHandler("mqtt.logger", LogLevel.INFO))

    pipeline.addLast(CHANNEL_TPS_HANDLER, channelTpsCounter)

    pipeline.addLast(IDLE_STATE_HANDLER, new IdleStateHandler(7, 0, 0))

    pipeline.addLast(KEEPALIVE_HANDLER, MqttKeepAliveHandler())
    pipeline.addLast(PROTO_OUT_HANDLER, MqttProtocolOutboundHandler())
    pipeline.addLast(PROTO_IN_HANDLER, MqttProtocolInboundHandler())
    pipeline.addLast(PUBLISH_HANDLER, MqttPublishHandler())
    pipeline.addLast(SUBSCRIBE_HANDLER, MqttSubscribeHandler())
    pipeline.addLast(CONNECT_HANDER, MqttConnectHandler(clientIdentifierFormat))
    pipeline.addLast(EXCEPTION_HANDLER, MqttExceptionHandler())

    val sessionCtx = MqttSessionContext(channel, smqdInstance, listenerName)
    sessionCtx.keepAliveTimeSeconds = 60

    channel.attr(ATTR_SESSION_CTX).set(sessionCtx)
    channel.attr(ATTR_SESSION_MANAGER).set(sessionManager)
    channel.attr(ATTR_METRICS).set(metrics)
  }

  override def afterAll(): Unit = {
    shutdownPromise.future.onComplete { _ =>
      smqdInstance.stop()
      TestKit.shutdownActorSystem(system)
    }
  }

  def shutdown(): Unit = {
    shutdownPromise.success(true)
  }

  "mqtt protocol handler" must {
    "connect" in {
      //assert(channel.writeInbound("Some message"))
    }
  }

  "done" in {
    shutdown()
  }
}
