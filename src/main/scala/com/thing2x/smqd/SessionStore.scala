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
}

object SessionStore {

  trait SessionStoreToken {
    def clientId: ClientId
    def cleanSession: Boolean
  }

  case class SubscriptionData(filterPath: FilterPath, qos: QoS)
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

}
