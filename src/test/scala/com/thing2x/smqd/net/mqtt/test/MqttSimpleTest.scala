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
import java.util.concurrent.atomic.AtomicInteger

import akka.actor.ActorSystem
import akka.http.scaladsl.testkit.ScalatestRouteTest
import akka.testkit.TestKit
import com.thing2x.smqd.{Smqd, SmqdBuilder}
import com.typesafe.config.{Config, ConfigFactory}
import com.typesafe.scalalogging.StrictLogging
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
      |      # leak.detector.level = PARANOID
      |      leak.detector.level = SIMPLE
      |
      |      local {
      |        enabled = true
      |        address = 0.0.0.0
      |        port = 1883
      |      }
      |
      |      local.secure {
      |        enabled = false
      |        address = 0.0.0.0
      |        port = 4883
      |      }
      |
      |      ws {
      |        enabled = false
      |        address = 0.0.0.0
      |        port = 8086
      |      }
      |
      |      ws.secure {
      |        enabled = false
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
  private val shutdownPromise = Promise[Boolean]

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

      // reducing number of testing messages for ci tool
      //val count = 200000
      val count = 20
      val eol = s"hello world - ${count - 1}"
      val received = new AtomicInteger()

      val subscriber = new MqttClient(broker, "test-sub", new MemoryPersistence())
      subscriber.connect(connOpt)
      subscriber.subscribe("sensor/+/temp", 0, new IMqttMessageListener {
        override def messageArrived(topic: String, message: MqttMessage): Unit = {
          val text = new String(message.getPayload, StandardCharsets.UTF_8)
          //logger.debug(s"Received: $topic, $text")
          assert(text.startsWith("hello world - "))

          received.incrementAndGet()

          if (text == eol)
            promise.success(text == eol)
        }
      })

      val publisher = new MqttClient(broker, "test-pub", new MemoryPersistence())
      publisher.connect(connOpt)

      val t1 = System.currentTimeMillis()
      for( n <- 0 until count) {
        publisher.publish(s"sensor/$n/temp", s"hello world - $n".getBytes(StandardCharsets.UTF_8), 0, false)
      }
      val t2 = System.currentTimeMillis()

      Await.result(promise.future, 60.second)

      val t = (t2 - t1).toDouble
      logger.debug(f"Time ${t/1000}%.3f sec. ${count.toDouble/t}%.4f message/ms  ${received.intValue} received.")

      publisher.disconnect()
      subscriber.disconnect()
    }
  }

  "done" in {
    shutdownPromise.success(true)
  }
}
