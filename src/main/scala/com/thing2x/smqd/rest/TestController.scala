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

import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport._
import akka.http.scaladsl.model.{HttpHeader, StatusCodes}
import akka.http.scaladsl.server.{Directives, Route}
import com.thing2x.smqd._
import com.typesafe.config.Config
import com.typesafe.scalalogging.StrictLogging
import pdi.jwt.algorithms.JwtHmacAlgorithm
import pdi.jwt.{Jwt, JwtAlgorithm, JwtClaim}
import spray.json._

import scala.concurrent.duration._
import scala.util.matching.Regex
import scala.util.{Failure, Success}

/**
  * 2018. 6. 24. - Created by Kwon, Yeong Eon
  */
class TestController(name: String, smqdInstance: Smqd, config: Config) extends RestController(name, smqdInstance, config) with Directives with StrictLogging {

  override def routes: Route = balckholeAndEcho ~ jwt

  import smqdInstance.Implicit._

  def balckholeAndEcho: Route = {
    ignoreTrailingSlash {
      extractRequestContext { ctx =>
        path("blackhole" / Remaining.?) { remain =>
          val suffix = remain match {
            case Some(str) => str
            case None => ""
          }

          val contentType = ctx.request.entity.getContentType

          val content = if (contentType.mediaType.isText){
            ctx.request.entity.dataBytes.map(bs => bs.utf8String).runFold(new StringBuilder())(_.append(_)).map(_.toString)
          }
          else {
            ctx.request.entity.dataBytes.map(bs => bs.length).runFold(0)(_+_).map(i => i.toString)
          }

          val received = ctx.request.entity.contentLengthOption.getOrElse(0)

          content.onComplete{
            case Success(str) =>
              logger.debug("Blackhole received {} ({} bytes) with {}", str, received, suffix)
            case Failure(ex) =>
              logger.debug("Blackhole failed", ex)
          }
          complete(StatusCodes.OK, s"OK $received bytes received")
        } ~
        path("echo") {
          complete(StatusCodes.OK, "Hello")
        }
      }
    }
  }

  case class LoginRequest(user: String, password: String)
  case class LoginResponse(token_type: String, access_token: String, access_token_expires_in: Long, refresh_token: String, refresh_token_expires_in: Long)
  case class LoginClaim(user: String, tokenType: String)
  case class LoginRefreshToken(user: String, allowRefresh: Boolean)
  case class LoginRefreshRequest(refresh_token: String)
  case class LoginRefreshResponse(token_type: String, access_token: String, access_token_expires_in: Long)

  implicit val LoginRequestFormat = jsonFormat2(LoginRequest)
  implicit val LoginResponseFormat = jsonFormat5(LoginResponse)
  implicit val LoginTokenFormat = jsonFormat2(LoginClaim)
  implicit val LoginRefreshTokenFormat = jsonFormat2(LoginRefreshToken)
  implicit val LoginRefreshRequestFormat = jsonFormat1(LoginRefreshRequest)
  implicit val LoginRefreshResponseFormat = jsonFormat3(LoginRefreshResponse)

  val expiresIn = 3600.second
  val refreshExpiredsIn = 4.hours
  val secretKey = "my_secret_key"
  val algorithm: JwtHmacAlgorithm = JwtAlgorithm.fromString("HS512").asInstanceOf[JwtHmacAlgorithm]

  def jwt: Route = {
    path("oauth" / "login") {
      post {
        entity(as[LoginRequest]) { loginReq =>
          if (loginReq.user == "admin" && loginReq.password == "password") {
            val loginClaim = LoginClaim(loginReq.user, "HMAC")
            val jsonToken = loginClaim.toJson.toString

            val refreshToken = LoginRefreshToken(loginReq.user, allowRefresh = true)
            val jsonRefresh = refreshToken.toJson.toString

            val access = Jwt.encode(JwtClaim(jsonToken).issuedNow.expiresIn(expiresIn.toSeconds), secretKey, algorithm)
            val refresh = Jwt.encode(JwtClaim(jsonRefresh).issuedNow.expiresIn(refreshExpiredsIn.toSeconds), secretKey, algorithm)

            val loginResponse = LoginResponse(loginClaim.tokenType, access, expiresIn.toSeconds, refresh, refreshExpiredsIn.toSeconds)
            complete(StatusCodes.OK, restSuccess(0, loginResponse.toJson))
          }
          else {
            complete(StatusCodes.OK, restError(404, s"Wrong password"))
          }
        }
      }
    } ~
    path( "oauth" / "sanity") {
      headerValue(extractJwt) { loginToken =>
        complete(StatusCodes.OK, restSuccess(0, JsString(s"Hello ${loginToken.user}")))
      }
    } ~
    path("oauth" / "refresh") {
      post {
        entity(as[LoginRefreshRequest]) { refreshReq =>
          headerValue(extractJwt) { loginToken =>
            Jwt.decode(refreshReq.refresh_token, secretKey, Seq(algorithm)) match {
              case Success(refreshTokenString) =>
                val refreshToken = refreshTokenString.parseJson.convertTo[LoginRefreshToken]
                if (refreshToken.allowRefresh && refreshToken.user == loginToken.user) {
                  val access = Jwt.encode(JwtClaim(loginToken.toJson.toString).issuedNow.expiresIn(expiresIn.toSeconds), secretKey, algorithm)
                  val refreshResponse = LoginRefreshResponse("HMAC", access, expiresIn.toSeconds)
                  complete(StatusCodes.OK, restSuccess(0, refreshResponse.toJson))
                }
                else {
                  complete(StatusCodes.Unauthorized, restSuccess(401, JsString("Unauthorized")))
                }
            }
          }
        }
      }
    }
  }

  val authRegex: Regex = """([\S]+)[\s]+([\S]+)""".r

  private def extractJwt: HttpHeader => Option[LoginClaim] = {
    case HttpHeader("authorization", header) =>
      header match {
        case authRegex(typ, token) =>
          typ.toUpperCase match {
            case "HMAC" =>
              Jwt.decode(token, secretKey, Seq(algorithm)) match {
                case Success(loginToken) =>
                  Option(loginToken.parseJson.convertTo[LoginClaim])
                case _ =>
                  None
              }
            case _ =>
              None
          }
      }
    case _ => None
  }
}
