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

package com.thing2x.smqd.session

import com.thing2x.smqd.ClientId
import com.thing2x.smqd.QoS.QoS
import io.netty.buffer.ByteBuf

/**
  * 2018. 6. 3. - Created by Kwon, Yeong Eon
  */
trait SessionContext {
  def clientId: ClientId
  def userName: Option[String]
  def password: Option[Array[Byte]]

  def keepAliveTimeSeconds: Int
  def cleanSession: Boolean

  def state: SessionState.SessionState

  def close(reason: String): Unit

  def writePub(topic: String, qos: QoS, isRetain: Boolean, msgId: Int, payload: ByteBuf): Unit
  def writePubRel(msgId: Int): Unit
  def writePubAck(msgId: Int): Unit
  def writePubRec(msgId: Int): Unit
}

object SessionState extends Enumeration {
  type SessionState = super.Value
  val Failed, Initiated, ConnectReceived, ConnectAcked = Value
}

