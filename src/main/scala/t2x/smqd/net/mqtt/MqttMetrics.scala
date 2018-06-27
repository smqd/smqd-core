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
