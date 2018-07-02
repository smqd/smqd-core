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

package com.thing2x.smqd.impl

import com.thing2x.smqd.impl.DefaultSessionStoreDelegate._
import com.thing2x.smqd.{ClientId, SessionStoreDelegate, SessionStoreToken, SmqResult, SmqSuccess}
import com.typesafe.scalalogging.StrictLogging

import scala.collection.mutable
import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global

/**
  * 2018. 5. 31. - Created by Kwon, Yeong Eon
  */
object DefaultSessionStoreDelegate {

  case class Token(clientId: ClientId, cleanSession: Boolean) extends SessionStoreToken

  case class SessionData(clientId: ClientId)

}

class DefaultSessionStoreDelegate extends SessionStoreDelegate with StrictLogging {

  private val map: mutable.HashMap[ClientId, SessionData] = new mutable.HashMap()

  /** @inheritdoc */
  override def createSession(clientId: ClientId, cleanSession: Boolean): Future[SessionStoreToken] = Future {
    val token = Token(clientId, cleanSession)

    def createSession0(clientId: ClientId): Unit = {
      logger.trace(s"[$clientId] *** createSessionData, cleanSession: {}", cleanSession)
      val info = SessionData(clientId)
      map.put(clientId, info)
    }

    if (cleanSession) {
      // always create new session if cleanSession = true
      createSession0(clientId)
    }
    else {
      // try to restore previous session if cleanSession = false
      map.get(clientId) match {
        case Some(_) => // resotre previous session
          // There is nothing to do since DefaultSessionStoreDelegate is using HashMap
          // If you implement the delegate based rdbms, need to deserialization something
        case None => // create new session if it doesn't exist
          createSession0(clientId)
      }
    }

    token
  }

  override def flushSession(token: SessionStoreToken): Future[SmqResult] = Future {
    logger.trace(s"[${token.clientId}] *** flushSessionData, cleanSession: {}", token.cleanSession)
    SmqSuccess
  }
}
