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

package com.thing2x.smqd.fault

import com.typesafe.config.Config
import com.typesafe.scalalogging.StrictLogging
import com.thing2x.smqd.{FilterPath, Service, Smqd, TopicPath}

/**
  * 2018. 6. 19. - Created by Kwon, Yeong Eon
  */
class DefaultFaultListener(name: String, smqd: Smqd, config: Option[Config]) extends Service(name, smqd, config) with StrictLogging {

  private var _status: Status.Status = Status.UNKNOWN
  override def status: Status.Status = _status

  override def start(): Unit = {
    _status = Status.STARTING
    val topic = config.get.getString("subscribe.topic")
    smqd.subscribe(FilterPath(topic)) {
      case (topicPath, msg) => onFault(topicPath, msg)
    }
    _status = Status.RUNNING
  }

  override def stop(): Unit = {
    _status = Status.STOPPING
    _status = Status.STOPPED
  }

  def onFault(topic: TopicPath, msg: Any): Unit = {
    if (!msg.isInstanceOf[Fault]) return
    val ft = msg.asInstanceOf[Fault]

    ft match {
      case f: SessionFault =>
        logger.warn(s"[${f.sessionId}] FAULT: ${f.message}")

      case ServerUnavailable =>
        logger.warn(s"FAULT: Auth Failed: $ft")

      case f: Fault =>
        logger.warn(s"FAULT: ${f.getClass.getSimpleName}")

      case _ =>
        logger.warn(s"FAULT: unknown fault: $ft")
    }
  }
}
