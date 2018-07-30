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
import com.thing2x.smqd.{SmqSuccess, SmqSuccessWithData}
import com.thing2x.smqd.UserDelegate.User
import com.thing2x.smqd.net.http.HttpServiceContext
import com.thing2x.smqd.net.http.OAuth2.OAuth2Claim
import com.thing2x.smqd.rest.RestController
import com.typesafe.scalalogging.StrictLogging
import spray.json._

import scala.util.Success

// 2018. 7. 15. - Created by Kwon, Yeong Eon

/**
  *
  */
object UserController extends DefaultJsonProtocol {
  case class LoginRequest(user: String, password: String)
  case class LoginResponse(token_type: String, access_token: String, access_token_expires_in: Long, refresh_token: String, refresh_token_expires_in: Long)

  case class LoginRefreshRequest(refresh_token: String)
  case class LoginRefreshResponse(token_type: String, access_token: String, access_token_expires_in: Long, refresh_token: String, refresh_token_expires_in: Long)

  implicit val LoginRequestFormat: RootJsonFormat[LoginRequest] = jsonFormat2(LoginRequest)
  implicit val LoginResponseFormat: RootJsonFormat[LoginResponse] = jsonFormat5(LoginResponse)
  implicit val LoginRefreshRequestFormat: RootJsonFormat[LoginRefreshRequest] = jsonFormat1(LoginRefreshRequest)
  implicit val LoginRefreshResponseFormat: RootJsonFormat[LoginRefreshResponse] = jsonFormat5(LoginRefreshResponse)

  case class UserUpdateRequest(password: String)
  implicit val UserFormat: RootJsonFormat[User] = jsonFormat2(User)
  implicit val userUpdateFormat: RootJsonFormat[UserUpdateRequest] = jsonFormat1(UserUpdateRequest)
}

import com.thing2x.smqd.rest.api.UserController._

class UserController(name: String, context: HttpServiceContext) extends RestController(name, context) with Directives with StrictLogging   {
  override def routes: Route = login ~ refresh ~ context.oauth2.authorized{ claim => sanity(claim) ~ users}

  def login: Route = {
    path("login") {
      post {
        entity(as[LoginRequest]) { loginReq =>
          val login = context.smqdInstance.userLogin(loginReq.user, loginReq.password)
          onComplete (login) {
            case  Success(result) if result == SmqSuccess | result.isInstanceOf[SmqSuccessWithData] =>
              val additionalInfo = result match {
                case SmqSuccessWithData(info) => info
                case _ => Map("issuer" -> "smqd-core")
              }

              val claim = OAuth2Claim(loginReq.user, additionalInfo)
              context.oauth2.issueJwt(claim) { jwt =>
                val response = LoginResponse(jwt.tokenType, jwt.accessToken, jwt.accessTokenExpire, jwt.refreshToken, jwt.refreshTokenExpire)
                complete(StatusCodes.OK, restSuccess(0, response.toJson))
              }

            case _ =>
              complete(StatusCodes.Unauthorized, restError(401, s"Bad username or password"))
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
          context.oauth2.refreshTokenIdentifier(refreshReq.refresh_token) match {
            case Some(identifier) =>
              val newClaim = OAuth2Claim(identifier, Map("issuer"-> "smqd-core"))
              context.oauth2.reissueJwt(newClaim, refreshReq.refresh_token) { jwt =>
                val response = LoginRefreshResponse(jwt.tokenType, jwt.accessToken, jwt.accessTokenExpire, jwt.refreshToken, jwt.refreshTokenExpire)
                complete(StatusCodes.OK, restSuccess(0, response.toJson))
              }
            case _ =>
              complete(StatusCodes.Unauthorized, restError(401, s"Invalid refresh token"))
          }
        }
      }
    }
  }

  def users: Route = {
    path("users") {
      get { // list all users
        onComplete(context.smqdInstance.userList) {
          case Success(list) =>
            complete(StatusCodes.OK, restSuccess(0, JsArray(list.map(_.toJson).toVector)))
          case _ =>
            complete(StatusCodes.InternalServerError, restError(500, "Unknown server error"))
        }
      } ~
      post { // create a user
        entity(as[User]) { user =>
          onComplete(context.smqdInstance.userCreate(user)) {
            case Success(SmqSuccess) =>
              complete(StatusCodes.OK, restError(0, "User created"))
            case _ =>
              complete(StatusCodes.InternalServerError, restError(500, "Unknown server error"))
          }
        }
      }
    } ~
    path("users" / Remaining) { pUsername =>
      get { // get a user
        onComplete(context.smqdInstance.userList) {
          case Success(list) =>
            list.find(u => u.username == pUsername) match {
              case Some(user) =>
                complete(StatusCodes.OK, restSuccess(0, user.toJson))
              case _ =>
                complete(StatusCodes.NotFound, restError(404, s"User '$pUsername' not found"))
            }
          case _ =>
            complete(StatusCodes.InternalServerError, restError(500, "Unknown server error"))
        }
      } ~
      delete { // delete a user
        onComplete(context.smqdInstance.userDelete(pUsername)) {
          case Success(SmqSuccess) =>
            complete(StatusCodes.OK, restSuccess(0, JsString(s"User '$pUsername' deleted")))
          case _ =>
            complete(StatusCodes.InternalServerError, restError(500, "Unknown server error"))
        }
      } ~
      patch { // update a user
        entity(as[UserUpdateRequest]) { update =>
          onComplete(context.smqdInstance.userUpdate(User(pUsername, update.password))) {
            case Success(SmqSuccess) =>
              complete(StatusCodes.OK, restSuccess(0, JsString(s"User '$pUsername' updated")))
            case _ =>
              complete(StatusCodes.NotImplemented, restError(501, "Not implemented"))
          }
        }
      }
    }
  }
}
