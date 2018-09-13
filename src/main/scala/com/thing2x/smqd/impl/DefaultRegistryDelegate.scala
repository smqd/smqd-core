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

package com.thing2x.smqd.impl

import com.thing2x.smqd.QoS.QoS
import com.thing2x.smqd.registry.RegistryDelegate
import com.typesafe.scalalogging.StrictLogging
import com.thing2x.smqd.{ClientId, _}

import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global

// 2018. 6. 1. - Created by Kwon, Yeong Eon

class DefaultRegistryDelegate extends RegistryDelegate with StrictLogging {
  def allowSubscribe(filterPath: FilterPath, qos: QoS, sessionId: ClientId, userName: Option[String]): Future[QoS] = Future(qos)
  def allowPublish(topicPath: TopicPath, sessionId: ClientId, userName: Option[String]): Future[Boolean] = Future(true)
}
