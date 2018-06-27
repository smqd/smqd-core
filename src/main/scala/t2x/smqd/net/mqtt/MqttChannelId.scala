package t2x.smqd.net.mqtt

import java.net.InetSocketAddress
import java.util.concurrent.atomic.AtomicLong

/**
  * 2018. 5. 30. - Created by Kwon, Yeong Eon
  */

object MqttChannelId {

  private val idSeq = new AtomicLong

  def apply(remoteAddr : InetSocketAddress): MqttChannelId = {
    val id: Long = idSeq.incrementAndGet
    new MqttChannelId(id, Some(remoteAddr.toString))
  }

  def apply(): MqttChannelId = {
    val id: Long = idSeq.incrementAndGet
    new MqttChannelId(id, None)
  }
}

class MqttChannelId(id: Long, remoteAddress: Option[String]) {
  override val hashCode: Int = (id ^ (id >>> 32)).toInt

  val stringId: String = "mqtt"+id

  override val toString: String = s"<mqtt-$id>"
}
