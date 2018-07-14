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

package com.thing2x.smqd.rest.test

import akka.actor.ActorSystem
import akka.testkit.{ImplicitSender, TestKit}
import com.thing2x.smqd.{Smqd, SmqdBuilder}
import com.thing2x.smqd.net.http.HttpService
import com.typesafe.config.{Config, ConfigFactory}
import com.typesafe.scalalogging.StrictLogging
import org.scalatest._
import akka.http.scaladsl.model.{HttpEntity, StatusCodes}
import akka.http.scaladsl.testkit.ScalatestRouteTest
import akka.http.scaladsl.server._
import Directives._
import org.scalatest.WordSpec

import scala.concurrent.Promise

// 2018. 7. 15. - Created by Kwon, Yeong Eon

/**
  *
  */
class TestControllerTest extends WordSpec
  with BeforeAndAfterAll
  with Matchers
  with ScalatestRouteTest
  with StrictLogging {

  private val config = ConfigFactory.parseString(
    """
      |akka.actor.provider=local
      |smqd {
      |  services=["rest-test"]
      |
      |  rest-test {
      |    entry.plugin="thing2x-core-http"
      |    entry.auto-start: true
      |    config: {
      |      "cors" : {
      |        "enabled" : true
      |      },
      |      "local" : {
      |        "enabled": true
      |        "port" : 0
      |        "address" : "127.0.0.1"
      |        "secure" : {
      |            "enabled" : false
      |        }
      |      }
      |      "routes" : {
      |        "test" : {
      |            "class" : "com.thing2x.smqd.rest.TestController"
      |            "prefix" : "test"
      |        }
      |      }
      |    }
      |  }
      |}
    """.stripMargin).withFallback(ConfigFactory.parseResources("smqd-ref.conf"))

  override def createActorSystem(): ActorSystem = ActorSystem(actorSystemNameFrom(getClass), config)

  var smqdInstance: Smqd = _
  var routes: Route = _
  val shutdownPromise = Promise[Boolean]

  override def beforeAll(): Unit = {

    smqdInstance = new SmqdBuilder(config)
      .setActorSystem(system)
      .build()

    smqdInstance.start()
    routes = smqdInstance.service("rest-test").get.asInstanceOf[HttpService].routes
  }

  override def afterAll(): Unit = {
    shutdownPromise.future.onComplete { _ =>
      smqdInstance.stop()
      TestKit.shutdownActorSystem(system)
    }
  }

  "TestController" should {
    "blackhole" in {
      Get("/test/blackhole", HttpEntity.Empty) ~> routes ~> check {
        status shouldEqual StatusCodes.OK
        responseAs[String] shouldEqual "OK 0 bytes received"
      }
    }

    "echo" in {
      Post("/test/echo") ~> routes ~> check {
        status shouldEqual StatusCodes.OK
        responseAs[String] shouldEqual "Hello"
      }

      Post("/test/echo/World") ~> routes ~> check {
        status shouldEqual StatusCodes.OK
        responseAs[String] shouldEqual "Hello World"
      }
    }

    "done" in {
      shutdownPromise.success(true)
    }
  }
}
