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

package com.thing2x.smqd.rest.api

import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport._
import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.server.{Directives, Route}
import com.thing2x.smqd.net.http.HttpServiceContext
import com.thing2x.smqd.net.http.OAuth2.OAuth2Claim
import com.thing2x.smqd.rest.RestController
import com.typesafe.scalalogging.StrictLogging
import spray.json.{DefaultJsonProtocol, JsObject, JsString, RootJsonFormat}

// 2018. 7. 15. - Created by Kwon, Yeong Eon

/**
  *
  */
object LoginController extends DefaultJsonProtocol {
  case class LoginRequest(user: String, password: String)
  case class LoginResponse(token_type: String, access_token: String, access_token_expires_in: Long, refresh_token: String, refresh_token_expires_in: Long)

  case class LoginRefreshRequest(refresh_token: String)
  case class LoginRefreshResponse(token_type: String, access_token: String, access_token_expires_in: Long, refresh_token: String, refresh_token_expires_in: Long)

  implicit val LoginRequestFormat: RootJsonFormat[LoginRequest] = jsonFormat2(LoginRequest)
  implicit val LoginResponseFormat: RootJsonFormat[LoginResponse] = jsonFormat5(LoginResponse)
  implicit val LoginRefreshRequestFormat: RootJsonFormat[LoginRefreshRequest] = jsonFormat1(LoginRefreshRequest)
  implicit val LoginRefreshResponseFormat: RootJsonFormat[LoginRefreshResponse] = jsonFormat5(LoginRefreshResponse)
}

import com.thing2x.smqd.rest.api.LoginController._

class LoginController(name: String, context: HttpServiceContext) extends RestController(name, context) with Directives with StrictLogging   {
  override def routes: Route = login ~ sanity ~ refresh

  private val oauth2 = context.oauth2

  def login: Route = {
    path("login") {
      post {
        entity(as[LoginRequest]) { loginReq =>
          if (loginReq.user == "admin" && loginReq.password == "password") {
            val claim = OAuth2Claim(loginReq.user, Map("allow-refresh" -> "true"))
            oauth2.issueJwt(claim) { jwt =>
              val response = LoginResponse(jwt.tokenType, jwt.accessToken, jwt.accessTokenExpire, jwt.refreshToken, jwt.refreshTokenExpire)
              complete(StatusCodes.OK, restSuccess(0, response.toJson))
            }
          }
          else {
            complete(StatusCodes.Unauthorized, restError(401, s"Wrong password"))
          }
        }
      }
    }
  }

  def sanity: Route = {
    path("sanity") {
      oauth2.authorized {
        case claim: OAuth2Claim =>
          complete(StatusCodes.OK, restSuccess(0, JsObject(
              "identifier" -> JsString(claim.identifier)
            )))
        case _ =>
          complete(StatusCodes.Unauthorized, restError(401, "Unauthorized"))
      }
    }
  }

  def refresh: Route = {
    path("refresh") {
      oauth2.authorized { claim =>
        post {
          entity(as[LoginRefreshRequest]) { refreshReq =>
            if (claim.getBoolean("allow-refresh").getOrElse(false)) {
              val newClaim = OAuth2Claim(claim.identifier, claim.attributes)
              oauth2.reissueJwt(newClaim, refreshReq.refresh_token) { jwt =>
                val response = LoginRefreshResponse(jwt.tokenType, jwt.accessToken, jwt.accessTokenExpire, jwt.refreshToken, jwt.refreshTokenExpire)
                complete(StatusCodes.OK, restSuccess(0, response.toJson))
              }
            }
            else {
              complete(StatusCodes.Unauthorized, restError(401, "Token refresh is not allowed"))
            }
          }
        }
      }
    }
  }
}
