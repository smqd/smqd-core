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

package com.thing2x.smqd

import com.thing2x.smqd.QoS.QoS
import com.thing2x.smqd.SessionStore.{ClientData, InitialData, SessionStoreToken, SubscriptionData}

import scala.concurrent.Future

// 2018. 8. 13. - Created by Kwon, Yeong Eon

/**
  *
  */
trait SessionStoreDelegate {
  /**
    * create new session
    * @param clientId client identifier
    * @return previous existing MqttSession
    */
  def createSession(clientId: ClientId, cleanSession: Boolean): Future[InitialData]

  def flushSession(token: SessionStoreToken): Future[SmqResult]

  def saveSubscription(token: SessionStoreToken, filterPath: FilterPath, qos: QoS): Future[SmqResult]

  def deleteSubscription(token: SessionStoreToken, filterPath: FilterPath): Future[SmqResult]

  def loadSubscriptions(token: SessionStoreToken): Future[Seq[SubscriptionData]]

  def storeBeforeDelivery(token: SessionStoreToken, topicPath: TopicPath, qos: QoS, isReatin: Boolean, msgId: Int, msg: Any): Future[SmqResult]

  def deleteAfterDeliveryAck(token: SessionStoreToken, msgId: Int): Future[SmqResult]

  def updateAfterDeliveryAck(token: SessionStoreToken, msgId: Int): Future[SmqResult]

  def deleteAfterDeliveryComplete(token: SessionStoreToken, msgId: Int): Future[SmqResult]

  def setSessionState(clientId: ClientId, connected: Boolean): Future[SmqResult]

  def snapshot(search: Option[String]): Future[Seq[ClientData]]
}

