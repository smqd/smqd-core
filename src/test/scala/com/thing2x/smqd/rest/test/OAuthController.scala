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

import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.server.{Directives, Route}
import com.thing2x.smqd.net.http.HttpServiceContext
import com.thing2x.smqd.rest.RestController
import com.thing2x.smqd.rest.api.UserController.{LoginRefreshRequest, LoginRefreshResponse, LoginRequest, LoginResponse}
import com.typesafe.scalalogging.StrictLogging
import com.thing2x.smqd.net.http.OAuth2.{OAuth2Claim, OAuth2RefreshClaim}
import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport._
import com.thing2x.smqd.SmqSuccess
import spray.json.{JsObject, JsString}

import scala.util.Success

// 9/28/18 - Created by Kwon, Yeong Eon

/**
  *
  */
class OAuthController(name: String, context: HttpServiceContext) extends RestController(name, context)
  with Directives with StrictLogging {

  override def routes: Route = login ~ refresh ~ context.oauth2.authorized{ claim => sanity(claim) }

  def login: Route = {
    path("login") {
      post {
        entity(as[LoginRequest]) { loginReq =>
          val login = context.smqdInstance.userLogin(loginReq.user, loginReq.password)
          onComplete(login) {
            case Success(SmqSuccess(userInfo)) =>
              val claim = OAuth2Claim(loginReq.user, userInfo + ("issuer" -> "smqd-http-test"))
              val refreshClaim = OAuth2RefreshClaim(loginReq.user, Map("issuer" -> "smqd-http-test"))
              context.oauth2.issueJwt(claim, refreshClaim) { jwt =>
                val response = LoginResponse(jwt.tokenType, jwt.accessToken, jwt.accessTokenExpire, jwt.refreshToken, jwt.refreshTokenExpire)
                complete(StatusCodes.OK, restSuccess(0, response.toJson))
              }
            case _ =>
              complete(StatusCodes.Unauthorized, restError(500, "Not Implemented"))
          }
        }
      }
    }
  }

  def sanity(claim: OAuth2Claim): Route = {
    path("sanity") {
      complete(StatusCodes.OK, restSuccess(0, JsObject(
        "identifier" -> JsString(claim.identifier)
      )))
    }
  }

  def refresh: Route = {
    path("refresh") {
      post {
        entity(as[LoginRefreshRequest]) { refreshReq =>
          context.oauth2.refreshToken(refreshReq.refresh_token) match {
            case Some(OAuth2RefreshClaim(identifier, attributes)) =>
              val previousValue = attributes("issuer") // re-use issuer value from refresh token
              //logger.info(s"============> attributes issuer='$previousValue'")
              val newClaim = OAuth2Claim(identifier, Map("issuer" -> previousValue, "refreshed" -> "true"))
              context.oauth2.reissueJwt(newClaim, refreshReq.refresh_token) { jwt =>
                val response = LoginRefreshResponse(jwt.tokenType, jwt.accessToken, jwt.accessTokenExpire, jwt.refreshToken, jwt.refreshTokenExpire)
                complete(StatusCodes.OK, restSuccess(0, response.toJson))
              }
            case _ =>
              complete(StatusCodes.Unauthorized, restError(401, "Invalid refresh token"))
          }
        }
      }
    }
  }
}