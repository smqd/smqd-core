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

/**
  * 2018. 7. 4. - Created by Kwon, Yeong Eon
  */
trait Plugin extends LifeCycle {
  def name: String
  def status: InstanceStatus

  def execStart(): Unit
  def execStop(): Unit
}

abstract class PluginLifeCycle extends Plugin {
  private var _status = InstanceStatus.STOPPED

  def status: InstanceStatus = _status

  def preStarting(): Unit = {
    _status = InstanceStatus.STARTING
  }

  def postStarted(): Unit = {
    _status = InstanceStatus.RUNNING
  }

  def preStopping(): Unit = {
    _status = InstanceStatus.STOPPED
  }

  def postStopped(): Unit = {
    _status = InstanceStatus.STOPPED
  }

  def execStart(): Unit = {
    preStarting()
    start()
    postStarted()
  }

  def execStop(): Unit = {
    preStopping()
    stop()
    postStopped()
  }
}