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

import com.thing2x.smqd.QoS.QoS
import com.thing2x.smqd.SessionStore._
import com.thing2x.smqd.impl.DefaultSessionStoreDelegate._
import com.thing2x.smqd._
import com.typesafe.scalalogging.StrictLogging

import scala.collection.mutable
import scala.concurrent.{ExecutionContext, Future}

// 2018. 5. 31. - Created by Kwon, Yeong Eon

object DefaultSessionStoreDelegate {

  case class Token(clientId: ClientId, cleanSession: Boolean) extends SessionStoreToken

  case class SessionData(clientId: ClientId, subscriptions: mutable.Set[SubscriptionData], messages: mutable.Queue[MessageData], var online: Boolean)
}

class DefaultSessionStoreDelegate(implicit ec: ExecutionContext) extends SessionStoreDelegate with StrictLogging {

  private val map: mutable.HashMap[String, SessionData] = new mutable.HashMap()

  override def createSession(clientId: ClientId, cleanSession: Boolean): Future[InitialData] = Future {
    val token = Token(clientId, cleanSession)

    val subscriptions = if (cleanSession) {
      // always create new session if cleanSession = true
      logger.trace(s"[$clientId] *** clearSessionData")
      val data = SessionData(clientId, mutable.Set.empty, mutable.Queue.empty, online = false)
      map.put(clientId.id, data)
      Nil
    }
    else {
      // try to restore previous session if cleanSession = false
      map.get(clientId.id) match {
        case Some(data) => // resotre previous session
          // There is nothing to do since DefaultSessionStoreDelegate is using HashMap
          // If you implement the delegate based rdbms, need to deserialize data
          logger.trace(s"[$clientId] *** restoreSessionData")
          val filtered = data.subscriptions.filter{ s => s.qos == QoS.AtLeastOnce || s.qos == QoS.ExactlyOnce }.toSeq
          data.subscriptions.clear()
          data.subscriptions ++= filtered
          filtered

        case None => // create new session if it doesn't exist
          logger.trace(s"[$clientId] *** createSessionData")
          val data = SessionData(clientId, mutable.Set.empty, mutable.Queue.empty, online = false)
          map.put(clientId.id, data)
          Nil
      }
    }

    InitialData(token, subscriptions)
  }

  override def flushSession(token: SessionStoreToken): Future[SmqResult] = Future {
    if (token.cleanSession) {
      SmqSuccess()
    }
    else {
      logger.trace(s"[${token.clientId}] *** flushSessionData")
      SmqSuccess()
    }
  }

  override def loadSubscriptions(token: SessionStoreToken): Seq[SubscriptionData] = {
    if (token.cleanSession) {
      Nil
    }
    else {
      map.get(token.clientId.id) match {
        case Some(data: SessionData) => data.subscriptions.toSeq
        case _ => Nil
      }
    }
  }

  override def saveSubscription(token: SessionStoreToken, filterPath: FilterPath, qos: QoS): Future[SmqResult] = Future {
    //logger.trace(s"============> (+) ${token.cleanSession} ${filterPath.toString} ${qos}")
    map.get(token.clientId.id) match {
      case Some(data: SessionData) =>
        data.subscriptions += SubscriptionData(filterPath, qos)
      case _ =>
    }
    SmqSuccess()
  }

  override def deleteSubscription(token: SessionStoreToken, filterPath: FilterPath): Future[SmqResult] = Future {
    //logger.trace(s"============> (-) ${filterPath.toString}")
    map.get(token.clientId.id) match {
      case Some(data: SessionData) =>
        val removing = data.subscriptions.filter( _.filterPath == filterPath)
        data.subscriptions --= removing
      case _ =>
    }
    SmqSuccess()
  }

  override def storeBeforeDelivery(token: SessionStoreToken, topicPath: TopicPath, qos: QoS, isReatin: Boolean, msgId: Int, msg: Any): Future[SmqResult] = Future {
    if (qos == QoS.AtLeastOnce || qos == QoS.ExactlyOnce) {
      map.get(token.clientId.id) match {
        case Some(data: SessionData) =>
          data.messages.enqueue(MessageData(topicPath, qos, msgId, msg, System.currentTimeMillis))
        case _ =>
      }
    }
    SmqSuccess()
  }

  override def deleteAfterDeliveryAck(token: SessionStoreToken, msgId: Int): Future[SmqResult] = Future {
    map.get(token.clientId.id) match {
      case Some(data: SessionData) =>
        data.messages.dequeueFirst(d => d.msgId == msgId)
      case _ =>
    }
    SmqSuccess()
  }

  override def updateAfterDeliveryAck(token: SessionStoreToken, msgId: Int): Future[SmqResult] = Future {
    map.get(token.clientId.id) match {
      case Some(data: SessionData) =>
        data.messages.find(d => d.msgId == msgId) match {
          case Some(msg) =>
            msg.acked = true
            msg.lastTryTime = System.currentTimeMillis()
          case _ =>
        }
      case _ =>
    }
    SmqSuccess()
  }

  override def deleteAfterDeliveryComplete(token: SessionStoreToken, msgId: Int): Future[SmqResult] = Future {
    map.get(token.clientId.id) match {
      case Some(data: SessionData) =>
        data.messages.dequeueFirst(d => d.msgId == msgId)
      case _ =>
    }
    SmqSuccess()
  }

  override def setSessionState(clientId: ClientId, connected: Boolean): Future[SmqResult] = Future {
    // logger.trace(s"===> session: [$clientId] ${ if(connected) "connected" else "disconnected"}")
    map.get(clientId.id) match {
      case Some(data: SessionData) =>
        data.online = connected
      case _ =>
    }
    SmqSuccess()
  }

  override def snapshot(search: Option[String] = None): Future[Seq[ClientData]] = Future {
    map.filter{ case (_, v) => v.online }.map{ case (_, v) => ClientData(v.clientId, v.subscriptions.toSeq, v.messages.size ) }.toSeq
  }
}
