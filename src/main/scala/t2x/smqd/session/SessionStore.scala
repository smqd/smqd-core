package t2x.smqd.session

/**
  * 2018. 6. 3. - Created by Kwon, Yeong Eon
  */
trait SessionStoreDelegate {

  /**
    * create new session
    * @param clientId client identifier
    * @return previous existing MqttSession
    */
  //  def createSession(clientId: String): MqttSession

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
}

class SessionStore(delegate: SessionStoreDelegate) {

}