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

package com.thing2x.smqd.net.mqtt.test

import java.nio.charset.StandardCharsets

import akka.actor.ActorSystem
import akka.http.scaladsl.testkit.ScalatestRouteTest
import akka.testkit.TestKit
import com.thing2x.smqd.{Smqd, SmqdBuilder}
import com.typesafe.config.{Config, ConfigFactory}
import com.typesafe.scalalogging.StrictLogging
import io.netty.channel.embedded.EmbeddedChannel
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence
import org.eclipse.paho.client.mqttv3.{IMqttMessageListener, MqttClient, MqttConnectOptions, MqttMessage}
import org.scalatest.{BeforeAndAfterAll, Matchers, WordSpec}

import scala.concurrent.duration._
import scala.concurrent.{Await, Promise}

// 2018. 8. 17. - Created by Kwon, Yeong Eon

/**
  *
  */
class MqttSimpleTest extends WordSpec
  with BeforeAndAfterAll
  with Matchers
  with ScalatestRouteTest
  with StrictLogging {

  val config: Config = ConfigFactory.parseString(
    """
      |smqd {
      |  services=["core-mqtt", "core-protocol"]
      |
      |  core-mqtt {
      |    config {
      |      leak.detector.level = PARANOID
      |
      |      local {
      |        enabled = true
      |        address = 0.0.0.0
      |        port = 1883
      |      }
      |
      |      local.secure {
      |        enabled = true
      |        address = 0.0.0.0
      |        port = 4883
      |      }
      |
      |      ws {
      |        enabled = true
      |        address = 0.0.0.0
      |        port = 8086
      |      }
      |
      |      ws.secure {
      |        enabled = true
      |        address = 0.0.0.0
      |        port = 8083
      |      }
      |    }
      |  }
      |
      |  core-protocol {
      |    config {
      |      coloring = true
      |    }
      |  }
      |}
    """.stripMargin).withFallback(ConfigFactory.parseResources("smqd-ref.conf"))

  override def createActorSystem(): ActorSystem = ActorSystem(actorSystemNameFrom(getClass), config)

  private var smqdInstance: Smqd = _
  private var shutdownPromise = Promise[Boolean]
  private var channel: EmbeddedChannel = _

  override def beforeAll(): Unit = {
    smqdInstance = new SmqdBuilder(config).setActorSystem(system).build()
    smqdInstance.start()
  }

  override def afterAll(): Unit = {
    shutdownPromise.future.onComplete { _ =>
      smqdInstance.stop()
      TestKit.shutdownActorSystem(system)
    }
  }

  val broker = "tcp://127.0.0.1:1883"

  "mqtt v3 protocol handler" must {
    "connect" in {
      val promise = Promise[Boolean]

      val connOpt = new MqttConnectOptions()
      connOpt.setCleanSession(true)
      connOpt.setAutomaticReconnect(true)

      val subscriber = new MqttClient(broker, "test-sub", new MemoryPersistence())
      subscriber.connect(connOpt)
      subscriber.subscribe("sensor/+/temp", 0, new IMqttMessageListener {
        override def messageArrived(topic: String, message: MqttMessage): Unit = {
          val text = new String(message.getPayload, StandardCharsets.UTF_8)
          logger.debug(s"Received: $topic $text")
          assert(text == "hello world")
          promise.success(text == "hello world")
        }
      })

      val publisher = new MqttClient(broker, "test-pub", new MemoryPersistence())
      publisher.connect(connOpt)
      publisher.publish("sensor/1/temp", "hello world".getBytes(StandardCharsets.UTF_8), 0, false)

      Await.result(promise.future, 1.second)

      publisher.disconnect()
      subscriber.disconnect()
    }
  }

  "done" in {
    shutdownPromise.success(true)
  }
}
