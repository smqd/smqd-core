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
