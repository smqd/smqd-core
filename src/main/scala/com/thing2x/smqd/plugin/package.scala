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

import java.io.{ByteArrayOutputStream, OutputStreamWriter, PrintWriter}

import io.circe._
import io.circe.syntax._

// 2018. 7. 7. - Created by Kwon, Yeong Eon

package object plugin {

  implicit object PluginInstanceOrdering extends Ordering[InstanceDefinition[Plugin]] {
    override def compare(x: InstanceDefinition[Plugin], y: InstanceDefinition[Plugin]): Int = {
      x.name.compare(y.name)
    }
  }

  implicit val pluginInstanceEncoder: Encoder[InstanceDefinition[Plugin]] = new Encoder[InstanceDefinition[Plugin]] {
    final def apply(obj: InstanceDefinition[Plugin]): Json = {
      val entities = Map(
        "name" -> Json.fromString(obj.name),
        "status" -> Json.fromString(obj.status),
        "auto-start" -> Json.fromBoolean(obj.autoStart),
        "plugin" -> Json.fromString(obj.pluginDef.name),
        "package" -> Json.fromString(obj.pluginDef.packageName)
      )

      obj.instance.status match {
        case InstanceStatus.FAIL if obj.instance.failure.isDefined =>
          val cause = obj.instance.failure.get
          val buffer = new ByteArrayOutputStream(4096)
          val pw = new PrintWriter(new OutputStreamWriter(buffer))
          cause.printStackTrace(pw)
          pw.close()
          val stack = new String(buffer.toString)

          (entities + ("failure" -> Json.obj(
            ("message", Json.fromString(cause.getMessage)),
            ("stack", Json.fromString(stack))
          ))).asJson
        case _ =>
          entities.asJson
      }
    }
  }

  implicit val pluginDefinitionEncoder: Encoder[PluginDefinition] = new Encoder[PluginDefinition] {
    final def apply(obj: PluginDefinition): Json = {
      val ptype =
        if (classOf[Service].isAssignableFrom(obj.clazz)) "Service"
        else if (classOf[BridgeDriver].isAssignableFrom(obj.clazz)) "BridgeDriver"
        else "UnknownPlugin"

      val multi = if (obj.multiInstantiable) {
        "MULTIPLE"
      } else {
        "SINGLE"
      }

      val instances = obj.instances.map(_.asJson)(Ordering.by[Json, String](_.name))

      Json.obj(
        ("name", Json.fromString(obj.name)),
        ("class", Json.fromString(obj.clazz.getCanonicalName)),
        ("class-archive", Json.fromString(obj.clazz.getProtectionDomain.getCodeSource.getLocation.toString)),
        ("package", Json.fromString(obj.packageName)),
        ("version", Json.fromString(obj.version)),
        ("type", Json.fromString(ptype)),
        ("instantiability", Json.fromString(multi)),
        ("instances", instances.asJson)
      )
    }
  }

  implicit val pluginRepositoryDefinitionEncoder: Encoder[RepositoryDefinition] = new Encoder[RepositoryDefinition] {
    override def apply(obj: RepositoryDefinition): Json = {
      if (obj.location.isDefined) {
        Json.obj(
          ("name", Json.fromString(obj.name)),
          ("provider", Json.fromString(obj.provider)),
          ("installable", Json.fromBoolean(obj.installable)),
          ("installed", Json.fromBoolean(obj.installed)),
          ("description", Json.fromString(obj.description)),
          ("location", Json.fromString(obj.location.get.toString))
        )
      } else {
        Json.obj(
          ("name", Json.fromString(obj.name)),
          ("provider", Json.fromString(obj.provider)),
          ("installable", Json.fromBoolean(obj.installable)),
          ("installed", Json.fromBoolean(obj.installed)),
          ("description", Json.fromString(obj.description)),
          ("group", Json.fromString(obj.module.get.group)),
          ("artifact", Json.fromString(obj.module.get.artifact)),
          ("version", Json.fromString(obj.module.get.version)),
          ("reolvers", obj.module.get.resolvers.asJson)
        )
      }
    }
  }

  implicit val pluginPackageDefinitionEncoder: Encoder[PackageDefinition] = new Encoder[PackageDefinition] {
    final def apply(obj: PackageDefinition): Json = Json.obj(
      ("name", Json.fromString(obj.name)),
      ("vendor", Json.fromString(obj.vendor)),
      ("description", Json.fromString(obj.description)),
      ("origin", Json.fromString(obj.repository.location.toString)),
      ("installable", Json.fromBoolean(obj.repository.installable)),
      ("installed", Json.fromBoolean(obj.repository.installed)),
      ("plugins", obj.plugins.map(_.asJson).asJson)
    )
  }

  sealed trait ExecResult
  case class ExecSuccess(message: String) extends ExecResult
  case class ExecFailure(message: String, cause: Option[Throwable]) extends ExecResult
  case class ExecInvalidStatus(message: String) extends ExecResult
  case class ExecUnknownCommand(cmd: String) extends ExecResult
}
