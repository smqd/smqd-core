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

package com.thing2x.smqd.rest

import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.server.{Directives, Route}
import com.thing2x.smqd.net.http.HttpServiceContext
import com.thing2x.smqd.net.http.OAuth2.{OAuth2Claim, OAuth2RefreshClaim}
import com.typesafe.scalalogging.StrictLogging
import io.circe._
import io.circe.generic.auto._
import io.circe.syntax._

import scala.util.{Failure, Success}

// 2018. 6. 24. - Created by Kwon, Yeong Eon

class TestController(name: String, context: HttpServiceContext) extends RestController(name, context) with Directives with StrictLogging {

  override def routes: Route = balckholeAndEcho ~ jwt

  import context.smqdInstance.Implicit._

  def balckholeAndEcho: Route = {
    ignoreTrailingSlash {
      extractRequestContext { ctx =>
        path("blackhole" / Remaining.?) { remain =>
          val suffix = remain match {
            case Some(str) => str
            case None => ""
          }

          val contentType = ctx.request.entity.getContentType

          val content = if (contentType.mediaType.isText) {
            ctx.request.entity.dataBytes.map(bs => bs.utf8String).runFold(new StringBuilder())(_.append(_)).map(_.toString)
          }
          else {
            ctx.request.entity.dataBytes.map(bs => bs.length).runFold(0)(_ + _).map(i => i.toString)
          }

          val received = ctx.request.entity.contentLengthOption.getOrElse(0)

          content.onComplete {
            case Success(str) =>
              logger.debug("Blackhole received {} ({} bytes) with {}", str, received, suffix)
            case Failure(ex) =>
              logger.debug("Blackhole failed", ex)
          }
          complete(StatusCodes.OK, s"OK $received bytes received")
        } ~
        path("echo" / Remaining.?) {
          case Some(msg) if msg.length > 0 =>
            complete(StatusCodes.OK, s"Hello $msg")
          case _ =>
            complete(StatusCodes.OK, s"Hello")
        }
      }
    }
  }

  import com.thing2x.smqd.util.FailFastCirceSupport._

  case class LoginRequest(user: String, password: String)
  case class LoginResponse(token_type: String, access_token: String, access_token_expires_in: Long, refresh_token: String, refresh_token_expires_in: Long)

  case class LoginRefreshRequest(refresh_token: String)
  case class LoginRefreshResponse(token_type: String, access_token: String, access_token_expires_in: Long, refresh_token: String, refresh_token_expires_in: Long)

  private val oauth2 = context.oauth2

  def jwt: Route = {
    path("oauth2" / "login") {
      post {
        entity(as[LoginRequest]) { loginReq =>
          if (loginReq.user == "admin" && loginReq.password == "password") {
            val claim = OAuth2Claim(loginReq.user, Map("allow-refresh" -> "true"))
            val refreshClaim = OAuth2RefreshClaim(loginReq.user, Map("allow-refresh" -> "true"))
            oauth2.issueJwt(claim, refreshClaim) { jwt =>
              val response = LoginResponse(jwt.tokenType, jwt.accessToken, jwt.accessTokenExpire, jwt.refreshToken, jwt.refreshTokenExpire)
              complete(StatusCodes.OK, restSuccess(0, response.asJson))
            }
          }
          else {
            complete(StatusCodes.OK, restError(404, s"Wrong password"))
          }
        }
      }
    } ~
    path("oauth2" / "sanity") {
      oauth2.authorized {
        case claim: OAuth2Claim =>
          complete(StatusCodes.OK, restSuccess(0, Json.obj(
            ("result", Json.fromString(s"Hello! your identifier is '${claim.identifier}'")))))
        case _ =>
          complete(StatusCodes.Unauthorized, restError(401, "Unauthorized"))
      }
    } ~
    path("oauth2" / "refresh") {
      oauth2.authorized { claim =>
        post {
          entity(as[LoginRefreshRequest]) { refreshReq =>
            if (claim.getBoolean("allow-refresh").getOrElse(false)) {
              val newClaim = OAuth2Claim(claim.identifier, claim.attributes)
              oauth2.reissueJwt(newClaim, refreshReq.refresh_token) { jwt =>
                val response = LoginRefreshResponse(jwt.tokenType, jwt.accessToken, jwt.accessTokenExpire, jwt.refreshToken, jwt.refreshTokenExpire)
                complete(StatusCodes.OK, restSuccess(0, response.asJson))
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
