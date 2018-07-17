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

package com.thing2x.smqd.net.http

import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport._
import akka.http.scaladsl.model.headers.{Authorization, OAuth2BearerToken}
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.{AuthorizationFailedRejection, Directive1}
import com.thing2x.smqd.net.http.OAuth2.{OAuth2Claim, OAuth2JwtRefreshToken, OAuth2JwtToken}
import com.typesafe.scalalogging.StrictLogging
import pdi.jwt.{Jwt, JwtAlgorithm, JwtClaim}
import spray.json._

import scala.concurrent.duration.Duration
import scala.util.{Failure, Success}

// 2018. 7. 14. - Created by Kwon, Yeong Eon

/**
  *
  */
object OAuth2 extends DefaultJsonProtocol {
  def apply(secretKey: String, algorithmName: String, tokenExpire: Duration, refreshTokenExpire: Duration): OAuth2 = {
    val algorithm = JwtAlgorithm.fromString(algorithmName)
    new OAuth2(secretKey, tokenExpire, refreshTokenExpire, algorithm)
  }

  case class OAuth2Claim(identifier: String, attributes: Map[String, String] = Map.empty) {
    def contains(key: String): Boolean = attributes.contains(key)
    def getBoolean(key: String): Option[Boolean] = if (attributes.contains(key)) Some(attributes(key).toLowerCase.equals("true")) else None
    def getString(key: String): Option[String] = if (attributes.contains(key)) Some(attributes(key)) else None
    def getInt(key: String): Option[Int] = if (attributes.contains(key)) Some(attributes(key).toInt) else None
    def getLong(key: String): Option[Long] = if (attributes.contains(key)) Some(attributes(key).toLong) else None
  }

  case class OAuth2JwtToken(tokenType: String, accessToken: String, accessTokenExpire: Long, refreshToken: String, refreshTokenExpire: Long)

  implicit val OAuth2ClaimFormat: RootJsonFormat[OAuth2Claim] = jsonFormat2(OAuth2Claim)
  implicit val OAuth2JwtTokenFormat: RootJsonFormat[OAuth2JwtToken] = jsonFormat5(OAuth2JwtToken)

  private[http] case class OAuth2JwtRefreshToken(username: String)
  private[http] implicit val OAuth2JwtRefreshTokenFormat: RootJsonFormat[OAuth2JwtRefreshToken] = jsonFormat1(OAuth2JwtRefreshToken)
}

class OAuth2(secretKey: String, tokenExpire: Duration, refreshTokenExpire: Duration, algorithm: JwtAlgorithm) extends StrictLogging {

  private var isSimulation = false
  private var simulationIdentifier = ""

  private[http] def setSimulationMode(isSimulation: Boolean, simulationIdentifier: String): Unit = {
    this.isSimulation = isSimulation
    this.simulationIdentifier = simulationIdentifier
  }

  private def verifyToken(token: String): Option[OAuth2Claim] = {
    Jwt.decode(token, secretKey, JwtAlgorithm.allHmac()) match {
      case Success(claim) =>
        val decodedClaim = claim.parseJson.convertTo[OAuth2Claim]
        Option(decodedClaim)
      case _ =>
        None
    }
  }

  private def bearerToken: Directive1[Option[String]] =
    for {
      authBearerHeader <- optionalHeaderValueByType(classOf[Authorization]).map(extractBearerToken)
      xAuthCookie      <- optionalCookie("X-Authorization-Token").map(_.map(_.value))
    } yield authBearerHeader.orElse(xAuthCookie)

  private def extractBearerToken(authHeader: Option[Authorization]): Option[String] =
    authHeader match {
      case Some(Authorization(OAuth2BearerToken(header))) => Option(header)
      case _ => None
    }

  def authorized: Directive1[OAuth2Claim] = {
    if (isSimulation) {
      val claim = OAuth2Claim(simulationIdentifier)
      provide(claim)
    }
    else {
      bearerToken.flatMap {
        case Some(token) =>
          verifyToken(token) match {
            case Some(claim: OAuth2Claim) => provide(claim)
            case None => reject(AuthorizationFailedRejection)
          }
        case _ =>  reject(AuthorizationFailedRejection)
      }
    }
  }

  private def issueJwt0(claim: OAuth2Claim): OAuth2JwtToken = {
    val tokenJson = claim.toJson.toString
    val refreshJson = OAuth2JwtRefreshToken(claim.identifier).toJson.toString

    val access = Jwt.encode(JwtClaim(tokenJson).issuedNow.expiresIn(tokenExpire.toSeconds), secretKey, algorithm)
    val refresh = Jwt.encode(JwtClaim(refreshJson).issuedNow.expiresIn(refreshTokenExpire.toSeconds), secretKey, algorithm)

    OAuth2JwtToken("Bearer", access, tokenExpire.toSeconds, refresh, refreshTokenExpire.toSeconds)
  }

  def issueJwt(claim: OAuth2Claim): Directive1[OAuth2JwtToken] = {
    provide(issueJwt0(claim))
  }

  def refreshTokenIdentifier(refreshTokenString: String): Option[String] = {
    Jwt.decode(refreshTokenString, secretKey, JwtAlgorithm.allHmac) match {
      case Success(tokenString) =>
        val refreshToken = tokenString.parseJson.convertTo[OAuth2JwtRefreshToken]
        Option(refreshToken.username)
      case _ =>
        None
    }
  }

  def reissueJwt(claim: OAuth2Claim, refreshTokenString: String): Directive1[OAuth2JwtToken] = {
    Jwt.decode(refreshTokenString, secretKey, JwtAlgorithm.allHmac) match {
      case Success(tokenString) =>
        val refreshToken = tokenString.parseJson.convertTo[OAuth2JwtRefreshToken]
        if (claim.identifier == refreshToken.username) {
          val newJwt = issueJwt0(claim)
          provide(newJwt)
        }
        else {
          reject(AuthorizationFailedRejection)
        }
      case Failure(ex) =>
        reject(AuthorizationFailedRejection)
    }
  }
}
