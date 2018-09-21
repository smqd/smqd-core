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

import akka.http.scaladsl.model.{ContentTypes, HttpEntity, StatusCodes}
import com.typesafe.scalalogging.StrictLogging

// 2018. 7. 15. - Created by Kwon, Yeong Eon

/**
  *
  */
class UserControllerTest extends CoreApiTesting with StrictLogging {

  "Login" should {
    "login" in {
      val loginReq = HttpEntity.apply(
        """
          |{
          |   "user": "admin",
          |   "password": "password"
          |}
        """.stripMargin).withContentType(ContentTypes.`application/json`)

      Post("/api/v1/auth/login", loginReq ) ~> routes ~> check {

        status shouldEqual StatusCodes.OK
        val rsp = asCoreApiResponseAsMap(entityAs[String])
        assert(rsp.code == 0)
        assert(rsp.result("access_token").convertTo[String].length > 30)

        logger.info(s"access_token = {}", rsp.result("access_token").convertTo[String])
        logger.info(s"refresh_token = {}", rsp.result("refresh_token").convertTo[String])
        logger.info(s"token_type = {}", rsp.result("token_type").convertTo[String])
        logger.info(s"access_token_expires_in = {}", rsp.result("access_token_expires_in").convertTo[Long])
        logger.info(s"refresh_token_expires_in = {}", rsp.result("refresh_token_expires_in").convertTo[Long])

        val tokenType = rsp.result("token_type").convertTo[String]
        assert(tokenType == "Bearer")

        val accessTokenExpire = rsp.result("access_token_expires_in").convertTo[Long]
        assert(accessTokenExpire == 1800)

        val refreshTokenExpire = rsp.result("refresh_token_expires_in").convertTo[Long]
        assert(refreshTokenExpire == 14400)
      }
    }
  }

  "done" in {
    shutdown()
  }

}
