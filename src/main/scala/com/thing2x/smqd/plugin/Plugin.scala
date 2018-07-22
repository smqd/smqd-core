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

package com.thing2x.smqd.plugin

import com.thing2x.smqd.{LifeCycle, Smqd}
import com.typesafe.config.Config
import com.typesafe.scalalogging.StrictLogging

// 2018. 7. 4. - Created by Kwon, Yeong Eon

trait Plugin extends LifeCycle {
  def name: String
  def status: InstanceStatus

  def execStart(): Unit
  def execStop(): Unit

  def failure: Option[Throwable]
}

abstract class AbstractPlugin(val name: String, val smqd: Smqd, val config: Config) extends Plugin with StrictLogging {

  private var _status = InstanceStatus.STOPPED
  def status: InstanceStatus = _status

  private var _cause: Option[Throwable] = None

  def preStarting(): Unit = {
    _status = InstanceStatus.STARTING
  }

  def postStarted(): Unit = {
    _cause = None
    _status = InstanceStatus.RUNNING
  }

  def preStopping(): Unit = {
    _status = InstanceStatus.STOPPED
  }

  def postStopped(): Unit = {
    _cause = None
    _status = InstanceStatus.STOPPED
  }

  def execStart(): Unit = {
    try {
      preStarting()
      start()
      postStarted()
    }
    catch {
      case th: Throwable =>
        _cause = Some(th)
        _status = InstanceStatus.FAIL
        logger.warn(s"Fail to start plugin instance '$name'", th)
    }
  }

  def execStop(): Unit = {
    try {
      preStopping()
      stop()
      postStopped()
    }
    catch {
      case th: Throwable =>
        _cause = Some(th)
        _status = InstanceStatus.FAIL
        logger.warn(s"Fail to stop plugin instance '$name'", th)
    }
  }

  def failure(ex: Throwable): Unit = {
    logger.warn(s"Failure in plugin instance '$name'", ex)
    _status = InstanceStatus.FAIL
    _cause = Some(ex)
  }

  def failure: Option[Throwable] = _cause
}