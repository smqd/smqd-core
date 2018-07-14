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

package com.thing2x.smqd.rest.api.test

import akka.actor.ActorSystem
import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.server._
import akka.http.scaladsl.testkit.ScalatestRouteTest
import akka.testkit.TestKit
import com.thing2x.smqd.net.http.HttpService
import com.thing2x.smqd.{Smqd, SmqdBuilder}
import com.typesafe.config.ConfigFactory
import com.typesafe.scalalogging.StrictLogging
import org.scalatest.{WordSpec, _}
import spray.json._

import scala.concurrent.Promise

// 2018. 7. 15. - Created by Kwon, Yeong Eon

/**
  *
  */
class MetricControllerTest extends WordSpec
  with BeforeAndAfterAll
  with Matchers
  with ScalatestRouteTest
  with CoreApiResponseAware
  with StrictLogging {

  private val config = ConfigFactory.parseString(
    """
      |akka.actor.provider=local
      |smqd {
      |  services=["api-test"]
      |
      |  api-test {
      |    entry.plugin="thing2x-core-api"
      |    config: {
      |      "cors" : {
      |        "enabled" : true
      |      },
      |      "local" : {
      |        "port" : 0
      |        "address" : "127.0.0.1"
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
    routes = smqdInstance.service("api-test").get.asInstanceOf[HttpService].routes
  }

  override def afterAll(): Unit = {
    shutdownPromise.future.onComplete { _ =>
      smqdInstance.stop()
      TestKit.shutdownActorSystem(system)
    }
  }

  "Metrics" should {
    "get-all" in {
      Get("/api/v1/metrics") ~> routes ~> check {
        status shouldEqual StatusCodes.OK
        val rsp = asCoreApiResponse(entityAs[String])
        assert(rsp.code == 0)
        assert(rsp.result.nonEmpty)
      }
    }

    "get-jvm/cpu" in {
      Get("/api/v1/metrics/jvm/cpu") ~> routes ~> check {
        status shouldEqual StatusCodes.OK
        val rsp = asCoreApiResponse(entityAs[String])
        assert(rsp.code == 0)
        assert(rsp.result.nonEmpty)
      }
    }
  }

  "done" can {
    "terminate" in {
      shutdownPromise.success(true)
    }
  }
}
