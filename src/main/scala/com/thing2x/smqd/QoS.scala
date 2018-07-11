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

package com.thing2x.smqd

// 2018. 6. 8. - Created by Kwon, Yeong Eon

/**
  * Qos Level
  */
object QoS extends Enumeration {
  type QoS = Value

  /** At most once qos level = 0x00 */
  val AtMostOnce: QoS.Value = Value(0x00)
  /** At least once qos level = 0x01 */
  val AtLeastOnce: QoS.Value = Value(0x01)
  /** Exactly once qos level = 0x02 */
  val ExactlyOnce: QoS.Value = Value(0x02)
  /** Returned by smqd when subscription failed, or not allowed = 0x80 */
  val Failure: QoS.Value = Value(0x80)
}

