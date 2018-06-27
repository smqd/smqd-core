package t2x.smqd

/**
  * 2018. 6. 8. - Created by Kwon, Yeong Eon
  */

object QoS extends Enumeration {
  type QoS = Value

  val AtMostOnce: QoS.Value = Value(0x00)
  val AtLeastOnce: QoS.Value = Value(0x01)
  val ExactlyOnce: QoS.Value = Value(0x02)
  val Failure: QoS.Value = Value(0x80)
}

