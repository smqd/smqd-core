package t2x.smqd.net.mqtt

import com.codahale.metrics.{Counter, MetricRegistry, SharedMetricRegistries}

/**
  * 2018. 6. 21. - Created by Kwon, Yeong Eon
  */
class MqttMetrics(name: String, registry: MetricRegistry = SharedMetricRegistries.getDefault) {
  val byteReceived: Counter  = registry.counter(s"$name.messages.byte.received")
  val byteSent: Counter      = registry.counter(s"$name.messages.byte.sent")
  val received: Counter      = registry.counter(s"$name.messages.received")
  val sent: Counter          = registry.counter(s"$name.messages.sent")

}
