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

package com.thing2x.smqd.net

import akka.actor.ActorRef
import io.netty.handler.codec.mqtt.MqttQoS
import io.netty.util.AttributeKey
import com.thing2x.smqd.QoS
import com.thing2x.smqd.QoS._

import scala.language.implicitConversions

/**
  * 2018. 6. 3. - Created by Kwon, Yeong Eon
  */
package object mqtt {
  val TLS = "TLS"
  val PROTOCOL_LEVEL = 4

  val HANDLER_CHANNEL_BPS = "channel.traffic.bps.handler"
  val HANDLER_CHANNEL_TPS = "channel.traffic.tps.handler"
  val HANDLER_IDLE_STATE = "idle.state.handler"

  val HANDLER_SSL = "ssl.handler"
  val HANDLER_DECODING = "decoder.handler"
  val HANDLER_ENCODING = "encoder.handler"

  val HANDLER_KEEPALIVE = "keepalive.handler"
  val HANDLER_PROTO_OUT = "protocol.outbound.handler"
  val HANDLER_PROTO_IN = "protocol.inbound.handler"
  val HANDLER_PUBLISH = "publish.handler"
  val HANDLER_SUBSCRIBE = "subscribe.handler"
  val HANDLER_CONNECT = "connect.handler"

  val HANDLER_EXCEPTION = "exception.handler"

  val ATTR_SESSION_CTX: AttributeKey[MqttSessionContext] = AttributeKey.newInstance("attr.session.context")

  val ATTR_METRICS: AttributeKey[MqttMetrics] = AttributeKey.newInstance("attr.metrics")

  val ATTR_SESSION_MANAGER: AttributeKey[ActorRef] = AttributeKey.newInstance("attr.session.manager")
  val ATTR_SESSION: AttributeKey[ActorRef] = AttributeKey.newInstance("attr.session.self")
  val ATTR_CHANNEL_ACTOR: AttributeKey[ActorRef] = AttributeKey.newInstance("attr.channel.actor")

  implicit def toMqttQoS(qos: QoS.Value): MqttQoS = qos match {
    case QoS.AtMostOnce => MqttQoS.AT_MOST_ONCE
    case QoS.AtLeastOnce => MqttQoS.AT_LEAST_ONCE
    case QoS.ExactlyOnce => MqttQoS.EXACTLY_ONCE
    case _ => MqttQoS.FAILURE
  }

  implicit def toQoS(mqttQoS: MqttQoS): QoS.Value = mqttQoS.value match {
    case 0x00 => AtMostOnce
    case 0x01 => AtLeastOnce
    case 0x02 => ExactlyOnce
    case _ => Failure
  }

  case object RemoveProtocolHandler
}
