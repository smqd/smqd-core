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

import com.thing2x.smqd.Smqd
import com.typesafe.config.Config
import com.typesafe.scalalogging.StrictLogging

import scala.concurrent.{ExecutionContext, Future}
import com.thing2x.smqd._

// 2018. 7. 9. - Created by Kwon, Yeong Eon

object InstanceDefinition extends StrictLogging {
  def apply[T <: Plugin](instance: T, pluginDef: PluginDefinition, autoStart: Boolean) = new InstanceDefinition(instance, pluginDef, autoStart)

  private def pluginCategoryOf(clazz: Class[_]): String = {
    if (classOf[Service].isAssignableFrom(clazz)) "Service"
    else if (classOf[BridgeDriver].isAssignableFrom(clazz)) "BirdgeDriver"
    else "Unknown type"
  }

  def defineInstance(smqd: Smqd, instName: String, instConf: Config): Option[InstanceDefinition[Plugin]] = {
    var category = "Unknown type"
    logger.info(s"Plugin '$instName' loading...")
    instConf.getOptionString("entry.class") match {
      case Some(className) =>
        try {
          val autoStart = instConf.getOptionBoolean("entry.auto-start").getOrElse(true)
          val clazz = getClass.getClassLoader.loadClass(className).asInstanceOf[Class[Plugin]]
          val cons = clazz.getConstructor(classOf[String], classOf[Smqd], classOf[Config])
          val inst = cons.newInstance(instName, smqd, instConf.getConfig("config"))
          val pdef = PluginDefinition.nonPluggablePlugin(instName, clazz)
          val idef = InstanceDefinition(inst, pdef, autoStart)
          category = pluginCategoryOf(clazz)
          logger.info(s"Plugin '$instName' loaded as $category")
          Some(idef)
        }
        catch {
          case ex: Throwable =>
            logger.error(s"Fail to load and create an instance of plugin '$instName'", ex)
            None
        }
      case None =>
        val plugin = instConf.getString("entry.plugin")
        val autoStart = instConf.getOptionBoolean("entry.auto-start").getOrElse(true)
        smqd.pluginManager.pluginDefinition(plugin) match {
          case Some(pdef) =>
            val idef: InstanceDefinition[Plugin] = pdef.createInstance(instName, smqd, instConf.getOptionConfig("config"), autoStart)
            category = pluginCategoryOf(pdef.clazz)
            logger.info(s"Plugin '$instName' loaded as $category")
            Some(idef)
          case None =>
            logger.error(s"Plugin not found '$plugin' '$instName'")
            None
        }
    }
  }
}

class InstanceDefinition[+T <: Plugin](val instance: T, val pluginDef: PluginDefinition, val autoStart: Boolean) {
  val name: String = instance.name

  /** FAIL, STOPPED, STOPPING, STARTING, RUNNING */
  def status: String = instance.status.toString

  /** packageName / pluginName / instanceName */
  val path: String = s"${pluginDef.packageName}/${pluginDef.name}/$name"

  def exec(cmd: String)(implicit ec: ExecutionContext): Future[ExecResult] = Future {
    try {
      cmd match {
        case "start" =>
          instance.status match {
            case InstanceStatus.FAIL | InstanceStatus.STOPPED =>
              instance.execStart()
              ExecSuccess(s"Instance '$name' is ${instance.status}")
            case status =>
              ExecInvalidStatus(s"Instance '$name' is $status")
          }
        case "stop" =>
          instance.status match {
            case InstanceStatus.FAIL | InstanceStatus.RUNNING =>
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


