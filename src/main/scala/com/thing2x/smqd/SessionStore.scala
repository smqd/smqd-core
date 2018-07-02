package com.thing2x.smqd

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
  def createSession(clientId: ClientId, cleanSession: Boolean): Future[SessionStoreToken]

  def flushSession(token: SessionStoreToken): Future[SmqResult]

  /*
    /**
      * read previously stored session
      * @param clientId client identifier
      * @return loaded session
      */
    //  def readSession(clientId: String): Option[MqttSession]

    /**
      * Remove current session that is associated with client identifier
      * @param clientId client identifier
      * @return removed MqttSession
      */
    //  def deleteSession(clientId: String): Option[MqttSession]

    /**
      * Store current session when client is disconnected
      * @param clientId client identifier
      * @param session MqttSession
      */
    //  def updateSession(clientId: String, session: MqttSession): Unit
  */

}

trait SessionStoreToken {
  def clientId: ClientId
  def cleanSession: Boolean
}

class SessionStore(delegate: SessionStoreDelegate) {
  def createSession(clientId: ClientId, cleanSession: Boolean): Future[SessionStoreToken] =
    delegate.createSession(clientId, cleanSession)

  def flushSession(token: SessionStoreToken): Future[SmqResult] =
    delegate.flushSession(token)
}
