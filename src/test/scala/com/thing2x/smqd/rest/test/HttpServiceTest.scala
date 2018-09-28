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
import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport._
import akka.http.scaladsl.model.headers.{Authorization, OAuth2BearerToken}
import akka.http.scaladsl.model.{ContentTypes, HttpEntity, StatusCodes}
import akka.http.scaladsl.server._
import akka.http.scaladsl.testkit.ScalatestRouteTest
import akka.testkit.TestKit
import com.thing2x.smqd.net.http.HttpService
import com.thing2x.smqd.rest.api.UserController.LoginResponse
import com.thing2x.smqd.{Smqd, SmqdBuilder}
import com.typesafe.config.ConfigFactory
import com.typesafe.scalalogging.StrictLogging
import org.scalatest.{WordSpec, _}
import spray.json.{JsNumber, JsonParser, ParserInput}

import scala.concurrent.Promise

// 2018. 7. 15. - Created by Kwon, Yeong Eon


class HttpServiceTest extends WordSpec
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
      |      "routes" : [
      |        {
      |            "name" : "test"
      |            "class" : "com.thing2x.smqd.rest.TestController"
      |            "prefix" : "test"
      |        },
      |        {
      |            "name" : "login"
      |            "class" : "com.thing2x.smqd.rest.test.OAuthController"
      |            "prefix" : "test"
      |        }
      |      ]
      |    }
      |  }
      |}
    """.stripMargin).withFallback(ConfigFactory.parseResources("smqd-ref.conf"))

  override def createActorSystem(): ActorSystem = ActorSystem(actorSystemNameFrom(getClass), config)

  var smqdInstance: Smqd = _
  var routes: Route = _
  val shutdownPromise: Promise[Boolean] = Promise[Boolean]

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

  "TestController" must {
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
  }

  "OAuthController" must {
    var token: String = ""
    var refreshToken: String = ""

    "login" in {
      val loginReq = HttpEntity.apply(ContentTypes.`application/json`,
        """
          |{
          |   "user": "admin",
          |   "password": "password"
          |}
        """.stripMargin)

      Post("/test/login", loginReq) ~> routes ~> check {
        status shouldEqual StatusCodes.OK
        // parse json response
        val response = entityAs[String]
        val responseJson = JsonParser(ParserInput(response)).asJsObject

        // check result code from json response
        val responseCode = responseJson.getFields("code").head.asInstanceOf[JsNumber]
        assert(responseCode.value.intValue == 0)

        // check result body as a LoginResponse
        val loginRsp = responseJson.getFields("result").head.convertTo[LoginResponse]
        // logger.info(s"===> ${loginRsp.access_token}")
        token = loginRsp.access_token
        refreshToken = loginRsp.refresh_token
      }
    }

    "sanity" in {
      //logger.info(s"===> token: $token")
      Get("/test/sanity").addHeader(Authorization(OAuth2BearerToken(token))) ~> routes ~> check {
        status shouldEqual StatusCodes.OK
        //logger.info("============>")
      }
    }

    "refresh" in {
      logger.info(s"===> refresh: $refreshToken")
      val refreshReq = HttpEntity(ContentTypes.`application/json`,
        s"""
          |{
          |  "refresh_token": "$refreshToken"
          |}
        """.stripMargin)

      Post("/test/refresh", refreshReq) ~> routes ~> check {
        status shouldEqual StatusCodes.OK
        val response = entityAs[String]
        val responseJson = JsonParser(ParserInput(response)).asJsObject
        val loginRsp = responseJson.getFields("result").head.convertTo[LoginResponse]
        val newToken = loginRsp.access_token
        assert(newToken != token)
        token = newToken
      }
    }

    "refresh_sanity" in {
      Get("/test/sanity").addHeader(Authorization(OAuth2BearerToken(token))) ~> routes ~> check {
        status shouldEqual StatusCodes.OK
        //logger.info("============>")
      }
    }
  }

  "HttpService" must {
    "shutdown" in {
      shutdownPromise.success(true)
    }
  }
}
