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

import scala.concurrent.{ExecutionContext, Future}

/**
  * 2018. 7. 9. - Created by Kwon, Yeong Eon
  */
object PluginInstance {
  def apply[T <: Plugin](instance: T, pluginDef: PluginDefinition) = new PluginInstance(instance, pluginDef)
}

class PluginInstance[+T <: Plugin](val instance: T, val pluginDef: PluginDefinition) {
  val name: String = instance.name

  /** UNKNOWN, STOPPED, STOPPING, STARTING, RUNNING */
  def status: String = instance.status.toString

  /** packageName / pluginName / instanceName */
  val path: String = s"${pluginDef.packageName}/${pluginDef.name}/$name"

  def exec(cmd: String)(implicit ec: ExecutionContext): Future[ExecResult] = Future {
    try {
      cmd match {
        case "start" =>
          instance.status match {
            case InstanceStatus.UNKNOWN | InstanceStatus.STOPPED =>
              instance.execStart()
              ExecSuccess(s"Instance '$name' is ${instance.status}")
            case status =>
              ExecInvalidStatus(s"Instance '$name' is $status")
          }
        case "stop" =>
          instance.status match {
            case InstanceStatus.UNKNOWN | InstanceStatus.RUNNING =>
              instance.execStop()
              ExecSuccess(s"Instance '$name' is ${instance.status}")
            case status =>
              ExecInvalidStatus(s"Instance '$name' is $status")
          }
        case _ =>
          ExecUnknownCommand(cmd)
      }
    }
    catch {
      case ex: Throwable => ExecFailure(s"Fail to $cmd instance '$name", Some(ex))
    }
  }
}


