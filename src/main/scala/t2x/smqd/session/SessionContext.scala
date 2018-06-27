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

package t2x.smqd.session

import io.netty.buffer.ByteBuf
import t2x.smqd.QoS.QoS
import t2x.smqd.SmqResult

/**
  * 2018. 6. 3. - Created by Kwon, Yeong Eon
  */
trait SessionContext {
  def sessionId: SessionId
  def userName: Option[String]
  def password: Option[Array[Byte]]

  def keepAliveTimeSeconds: Int
  def isCleanSession: Boolean

  def sessionStarted(): Unit
  def sessionStopped(): Unit
  def sessionTimeout(): Unit

  def deliver(topic: String, qos: QoS, isRetain: Boolean, msgId: Int, msg: ByteBuf): Unit
}
