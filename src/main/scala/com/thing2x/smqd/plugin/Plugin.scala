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

abstract class AbstractPlugin(val name: String, val smqdInstance: Smqd, val config: Config) extends Plugin with StrictLogging {

  private[plugin] var definition: Option[InstanceDefinition[_]] = None

  private var v_status = InstanceStatus.STOPPED
  private def setStatus(newStatus: InstanceStatus): Unit = {
    v_status = newStatus
    val path = definition match {
      case Some(instDef) =>
        instDef.path
      case _ =>
        "$SYS/plugins/events/"+name
    }
    val topic = "$SYS/plugins/events/"+path
    val key = path.replaceAll("[/]", ".")
    if (smqdInstance != null)
      smqdInstance.publish(topic, s"""{"$key": {"status":"${v_status.toString}"}}""")
  }

  def status: InstanceStatus = v_status

  private var _cause: Option[Throwable] = None

  def preStarting(): Unit = {
  }

  def postStarted(): Unit = {
  }

  def preStopping(): Unit = {
  }

  def postStopped(): Unit = {
  }

  final def execStart(): Unit = {
    if (status != InstanceStatus.RUNNING) {
      try {
        _cause = None
        preStarting()
        setStatus(InstanceStatus.STARTING)
        start()
        setStatus(InstanceStatus.RUNNING)
        postStarted()
      }
      catch {
        case th: Throwable =>
          failure(th)
      }
    }
  }

  final def execStop(): Unit = {
    if (status != InstanceStatus.STOPPED) {
      try {
        _cause = None
        preStopping()
        setStatus(InstanceStatus.STOPPING)
        stop()
        setStatus(InstanceStatus.STOPPED)
        postStopped()
      }
      catch {
        case th: Throwable =>
          failure(th)
      }
    }
  }

  /**
    * When a plugin meets an exception it should report the exception by calling this method.
    * The plugin override `shouldExitOnFailure` and returns `true`, smqd will shutdown the process
    * with `scala.sys.exit(1)`.
    *
    * @param ex an exception that a plugin whants to report
    */
  final def failure(ex: Throwable): Unit = {
    logger.warn(s"Failure in plugin instance '$name'", ex)
    _cause = Some(ex)
    setStatus(InstanceStatus.FAIL)
    if (shouldExitOnFailure) scala.sys.exit(1)
  }

  /**
    * Retrieve the latest exception.
    *
    * @return the latest exception that has been reported by plugin
    */
  final def failure: Option[Throwable] = _cause

  /**
    * When a plugin occurs an exception, smqd core call `shouldExitOnFailure` and then shutdown
    * whole process if the plugin returns `true`.
    * Subcalss can override this to decide if the process can continue to run or terminate.
    *
    * The default value is `false`
    */
  val shouldExitOnFailure: Boolean = false
}