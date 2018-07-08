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

import com.typesafe.config.ConfigRenderOptions
import spray.json.{DefaultJsonProtocol, JsArray, JsBoolean, JsObject, JsString, JsValue, JsonParser, RootJsonFormat}

import scala.collection.JavaConverters._

/**
  * 2018. 7. 7. - Created by Kwon, Yeong Eon
  */
package object plugin extends DefaultJsonProtocol {

  implicit object PluginInstanceOrdering extends Ordering[PluginInstance[Plugin]] {
    override def compare(x: PluginInstance[Plugin], y: PluginInstance[Plugin]): Int = {
      x.name.compare(y.name)
    }
  }

  implicit object PluginInstanceFormat extends RootJsonFormat[PluginInstance[Plugin]] {
    override def read(json: JsValue): PluginInstance[Plugin] = ???

    override def write(obj: PluginInstance[Plugin]): JsValue = {
      JsObject(
        "name" -> JsString(obj.name),
        "status" -> JsString(obj.status),
        "plugin" -> JsString(obj.pluginDef.name),
        "package" -> JsString(obj.pluginDef.packageName)
      )
    }
  }

  implicit object PluginDefinitionFormat extends RootJsonFormat[PluginDefinition] {
    override def read(json: JsValue): PluginDefinition = ???
    override def write(obj: PluginDefinition): JsValue = {
      val ptype =
        if (classOf[Service].isAssignableFrom(obj.clazz)) "Service"
        else if (classOf[BridgeDriver].isAssignableFrom(obj.clazz)) "BridgeDriver"
        else "UnknownPlugin"

      val multi = if (obj.multiInstantiable) {
        "MULTIPLE"
      } else {
        "SINGLE"
      }

      val entries = obj.defaultConfig.entrySet().asScala.map { entry =>
        val key = entry.getKey
        val value: com.typesafe.config.ConfigValue = entry.getValue
        val opt = value.render(ConfigRenderOptions.concise())
        key ->JsonParser(opt)
      }.toMap

      val instances = obj.instances.map(inst => PluginInstanceFormat.write(inst)).toVector

      JsObject (
        "name" -> JsString(obj.name),
        "class" ->  JsString(obj.clazz.getCanonicalName),
        "class-archive" -> JsString(obj.clazz.getProtectionDomain.getCodeSource.getLocation.toString),
        "package" -> JsString(obj.packageName),
        "version" -> JsString(obj.version),
        "type" -> JsString(ptype),
        "instantiability" -> JsString(multi),
        "default-config" -> JsObject(entries),
        "instances" -> JsArray(instances)
      )
    }
  }

  implicit object PluginRepositoryDefinitionFormat extends RootJsonFormat[PluginRepositoryDefinition] {
    override def read(json: JsValue): PluginRepositoryDefinition = ???
    override def write(obj: PluginRepositoryDefinition): JsValue = {
      if (obj.location.isDefined) {
        JsObject (
          "name" -> JsString(obj.name),
          "provider" -> JsString(obj.provider),
          "installable" -> JsBoolean(obj.installable),
          "installed" -> JsBoolean(obj.installed),
          "description" -> JsString(obj.description),
          "location" -> JsString(obj.location.get.toString)
        )
      }
      else {
        JsObject (
          "name" -> JsString(obj.name),
          "provider" -> JsString(obj.provider),
          "installable" -> JsBoolean(obj.installable),
          "installed" -> JsBoolean(obj.installed),
          "description" -> JsString(obj.description),
          "group" -> JsString(obj.module.get._1),
          "artifact" -> JsString(obj.module.get._2),
          "version" -> JsString(obj.module.get._3)
        )
      }
    }
  }

  implicit object PluginPackageDefinitionFormat extends RootJsonFormat[PluginPackageDefinition] {
    override def read(json: JsValue): PluginPackageDefinition = ???
    override def write(obj: PluginPackageDefinition): JsValue = {
      JsObject (
        "name"-> JsString(obj.name),
        "vendor" -> JsString(obj.vendor),
        "description" -> JsString(obj.description),
        "origin" -> JsString(obj.repository.location.toString),
        "installable" -> JsBoolean(obj.repository.installable),
        "installed" -> JsBoolean(obj.repository.installed),
        "plugins" -> JsArray(obj.plugins.map(p => PluginDefinitionFormat.write(p)).toVector)
      )
    }
  }


  sealed trait ExecResult
  case class ExecSuccess(message: String) extends ExecResult
  case class ExecFailure(message: String, cause: Option[Throwable]) extends ExecResult
  case class ExecInvalidStatus(message: String) extends ExecResult
  case class ExecUnknownCommand(cmd: String) extends ExecResult
}
