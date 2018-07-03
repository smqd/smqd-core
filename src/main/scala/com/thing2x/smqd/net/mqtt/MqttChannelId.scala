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

package com.thing2x.smqd.net.mqtt

import java.net.InetSocketAddress
import java.util.concurrent.atomic.AtomicLong

/**
  * 2018. 5. 30. - Created by Kwon, Yeong Eon
  */

object MqttChannelId {

  private val idSeq = new AtomicLong

  def apply(listenerName: String, remoteAddr : InetSocketAddress, localNodeName: String): MqttChannelId = {
    val id: Long = idSeq.incrementAndGet
    new MqttChannelId(listenerName, id, Some(remoteAddr.toString), Option(localNodeName))
  }

  def apply(listenerName: String, remoteAddr : InetSocketAddress): MqttChannelId = {
    val id: Long = idSeq.incrementAndGet
    new MqttChannelId(listenerName, id, Some(remoteAddr.toString), None)
  }

  def apply(listenerName: String): MqttChannelId = {
    val id: Long = idSeq.incrementAndGet
    new MqttChannelId(listenerName, id, None, None)
  }
}

class MqttChannelId(val listenerName: String, val id: Long, _remoteAddress: Option[String], _localNodeName: Option[String]) {
  override val hashCode: Int = (id ^ (id >>> 32)).toInt

  val nodeName: String = _localNodeName match { case Some(str) => str case _ => "local" }
  val remoteAddress: String = _remoteAddress match { case Some(str) => str case _ => "-"}

  val stringId: String =  s"$nodeName:$listenerName:$id"

  override val toString: String = s"<$stringId>"
}
