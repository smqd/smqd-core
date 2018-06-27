package t2x.smqd.net

import akka.actor.ActorRef
import io.netty.handler.codec.mqtt.MqttQoS
import io.netty.util.AttributeKey
import t2x.smqd.QoS
import t2x.smqd.QoS._

import scala.language.implicitConversions

/**
  * 2018. 6. 3. - Created by Kwon, Yeong Eon
  */
package object mqtt {
  val TLS = "TLS"
  val PROTOCOL_LEVEL = 4

  val CHANNEL_BPS_HANDLER = "channel.traffic.bps.handler"
  val CHANNEL_TPS_HANDLER = "channel.traffic.tps.handler"
  val IDLE_STATE_HANDLER = "idle.state.handler"

  val SSL_HANDLER = "ssl.handler"
  val DECODING_HANDLER = "decoder.handler"
  val ENCODING_HANDLER = "encoder.handler"

  val KEEPALIVE_HANDLER = "keepalive.handler"
  val PROTO_OUT_HANDLER = "protocol.outbound.handler"
  val PROTO_IN_HANDLER = "protocol.inbound.handler"
  val PUBLISH_HANDLER = "publish.handler"
  val SUBSCRIBE_HANDLER = "subscribe.handler"
  val CONNECT_HANDER = "connect.handler"

  val ATTR_SESSION_CTX: AttributeKey[MqttSessionContext] = AttributeKey.newInstance("attr.session.context")

  val ATTR_METRICS: AttributeKey[MqttMetrics] = AttributeKey.newInstance("attr.metrics")

  val ATTR_SESSION_MANAGER: AttributeKey[ActorRef] = AttributeKey.newInstance("attr.session.manager")
  val ATTR_SESSION: AttributeKey[ActorRef] = AttributeKey.newInstance("attr.session.self")

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
}
