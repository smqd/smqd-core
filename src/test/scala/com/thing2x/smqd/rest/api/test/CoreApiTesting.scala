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
import spray.json.{DefaultJsonProtocol, JsValue, RootJsonFormat}
import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport._
import akka.http.scaladsl.server.Route
import akka.http.scaladsl.testkit.{RouteTestTimeout, ScalatestRouteTest}
import akka.testkit.TestKit
import com.thing2x.smqd.{EndpointInfo, NodeInfo, Smqd, SmqdBuilder}
import com.thing2x.smqd.net.http.HttpService
import com.typesafe.config.{Config, ConfigFactory}
import com.typesafe.scalalogging.StrictLogging
import org.scalatest.{BeforeAndAfterAll, Matchers, WordSpec}
import spray.json._

import scala.concurrent.duration._
import scala.concurrent.Promise

// 2018. 7. 15. - Created by Kwon, Yeong Eon

/**
  *
  */

abstract class CoreApiTesting extends WordSpec
  with BeforeAndAfterAll
  with Matchers
  with ScalatestRouteTest
  with DefaultJsonProtocol
  with StrictLogging {

  case class CoreApiResponseAsMap(code: Int, result: Map[String, JsValue])
  implicit val CoreApiResponseAsMapFormat: RootJsonFormat[CoreApiResponseAsMap] = jsonFormat2(CoreApiResponseAsMap)

  def asCoreApiResponseAsMap(jsonString: String): CoreApiResponseAsMap = {
    logger.debug(jsonString)
    val json = jsonString.parseJson
    json.convertTo[CoreApiResponseAsMap]
  }

  case class CoreApiResponse(code: Int, result: JsValue)
  implicit val CorePaiResponseFormat: RootJsonFormat[CoreApiResponse] = jsonFormat2(CoreApiResponse)

  implicit val EndpointInfoFormat: RootJsonFormat[EndpointInfo] = jsonFormat2(EndpointInfo)
  implicit val NodeInfoFormat: RootJsonFormat[NodeInfo] = jsonFormat7(NodeInfo)

  val config: Config = ConfigFactory.parseString(
    """
      |akka.actor.provider=local
      |smqd {
      |  node_name = "smqd-test-node"
      |  services=["api-test"]
      |
      |  api-test {
      |    entry.plugin="thing2x-core-api"
      |    config: {
      |      cors : {
      |        enabled : true
      |      },
      |      oauth2 {
      |        simulation_mode = true
      |        simulation_identifier = admin
      |      },
      |      local : {
      |        port : 0
      |        address : "127.0.0.1"
      |      }
      |    }
      |  }
      |}
    """.stripMargin).withFallback(ConfigFactory.parseResources("smqd-ref.conf"))

  override def createActorSystem(): ActorSystem = ActorSystem(actorSystemNameFrom(getClass), config)

  var smqdInstance: Smqd = _
  var routes: Route = _
  val shutdownPromise: Promise[Boolean] = Promise[Boolean]

  implicit def default(implicit system: ActorSystem): RouteTestTimeout = RouteTestTimeout(5.seconds)

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

  def shutdown(): Unit = {
    shutdownPromise.success(true)
  }
}
