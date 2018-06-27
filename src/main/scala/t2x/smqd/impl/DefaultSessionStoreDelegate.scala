package t2x.smqd.impl

import com.typesafe.scalalogging.StrictLogging
import t2x.smqd.session.SessionStoreDelegate

/**
  * 2018. 5. 31. - Created by Kwon, Yeong Eon
  */
class DefaultSessionStoreDelegate extends SessionStoreDelegate with StrictLogging {

  /*
  private val store = mutable.ParHashMap[String, MqttSession]()

  /**
    * @inheritdoc
    */
  override def deleteSession(clientId: String): Option[MqttSession] = {
    store.remove(clientId)
  }

  /**
    * @inheritdoc
    */
  override def createSession(clientId: String): MqttSession = {
    val session = new MqttSession(clientId)
    store.put(clientId, session)
    session
  }

  /**
    * @inheritdoc
    */
  override def readSession(clientId: String): Option[MqttSession] = {
    store.get(clientId)
  }

  /**
    * @inheritdoc
    */
  override def updateSession(clientId: String, session: MqttSession): Unit = {
    store.put(clientId, session)
  }
  */
}
