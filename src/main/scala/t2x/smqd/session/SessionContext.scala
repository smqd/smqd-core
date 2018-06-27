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
