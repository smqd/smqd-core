package com.thing2x.smqd

import com.thing2x.smqd.QoS.QoS
import com.thing2x.smqd.SessionStore.{InitialData, SessionStoreToken, SubscriptionData}

import scala.concurrent.Future

/**
  * 2018. 7. 2. - Created by Kwon, Yeong Eon
  */
trait SessionStoreDelegate {
  /**
    * create new session
    * @param clientId client identifier
    * @return previous existing MqttSession
    */
  def createSession(clientId: ClientId, cleanSession: Boolean): Future[InitialData]

  def flushSession(token: SessionStoreToken): Future[SmqResult]

  def saveSubscription(token: SessionStoreToken, filterPath: FilterPath, qos: QoS): Unit

  def deleteSubscription(token: SessionStoreToken, filterPath: FilterPath): Unit

  def loadSubscriptions(token: SessionStoreToken): Seq[SubscriptionData]

  def storeBeforeDelivery(token: SessionStoreToken, topicPath: TopicPath, qos: QoS, isReatin: Boolean, msgId: Int, msg: Any)

  def deleteAfterDeliveryAck(token: SessionStoreToken, msgId: Int): Unit

  def updateAfterDeliveryAck(token: SessionStoreToken, msgId: Int): Unit

  def deleteAfterDeliveryComplete(token: SessionStoreToken, msgId: Int): Unit

}

object SessionStore {

  trait SessionStoreToken {
    def clientId: ClientId
    def cleanSession: Boolean
  }

  case class SubscriptionData(filterPath: FilterPath, qos: QoS)
  case class MessageData(topicPath: TopicPath, qos: QoS, msgId: Int, msg: Any, var lastTryTime: Long, var acked: Boolean = false)
  case class InitialData(token: SessionStoreToken, subscriptions: Seq[SubscriptionData])
}

class SessionStore(delegate: SessionStoreDelegate) {
  def createSession(clientId: ClientId, cleanSession: Boolean): Future[InitialData] =
    delegate.createSession(clientId, cleanSession)

  def flushSession(token: SessionStoreToken): Future[SmqResult] =
    delegate.flushSession(token)

  def saveSubscription(token: SessionStoreToken, filterPath: FilterPath, qos: QoS): Unit =
    delegate.saveSubscription(token, filterPath, qos)

  def deleteSubscription(token: SessionStoreToken, filterPath: FilterPath): Unit =
    delegate.deleteSubscription(token, filterPath)

  def loadSubscriptions(token: SessionStoreToken): Seq[SubscriptionData] =
    delegate.loadSubscriptions(token)

  def storeBeforeDelivery(token: SessionStoreToken, topicPath: TopicPath, qos: QoS, isRetain: Boolean, msgId: Int, msg: Any): Unit =
    delegate.storeBeforeDelivery(token, topicPath, qos, isRetain, msgId, msg)

  // QoS = 1
  def deleteAfterDeliveryAck(token: SessionStoreToken, msgId: Int): Unit =
    delegate.deleteAfterDeliveryAck(token, msgId)

  // QoS = 2
  def updateAfterDeliveryAck(token: SessionStoreToken, msgId: Int): Unit =
    delegate.updateAfterDeliveryAck(token, msgId)

  // QoS = 2
  def deleteAfterDeliveryComplete(token: SessionStoreToken, msgId: Int): Unit =
    delegate.deleteAfterDeliveryAck(token, msgId)

}
