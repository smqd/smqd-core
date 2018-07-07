package com.thing2x.smqd

import com.typesafe.config.ConfigRenderOptions
import spray.json.{DefaultJsonProtocol, JsArray, JsBoolean, JsObject, JsString, JsValue, JsonParser, RootJsonFormat}

import scala.collection.JavaConverters._

/**
  * 2018. 7. 7. - Created by Kwon, Yeong Eon
  */
package object plugin extends DefaultJsonProtocol {

  implicit object PluginInstanceFormat extends RootJsonFormat[PluginInstance[SmqPlugin]] {
    override def read(json: JsValue): PluginInstance[SmqPlugin] = ???

    override def write(obj: PluginInstance[SmqPlugin]): JsValue = {
      JsObject(
        "name" -> JsString(obj.name),
        "status" -> JsString(obj.status)
      )
    }
  }

  implicit object PluginDefinitionFormat extends RootJsonFormat[PluginDefinition] {
    override def read(json: JsValue): PluginDefinition = ???
    override def write(obj: PluginDefinition): JsValue = {
      val ptype =
        if (classOf[SmqServicePlugin].isAssignableFrom(obj.clazz)) "ServicePlugin"
        else if (classOf[SmqBridgeDriverPlugin].isAssignableFrom(obj.clazz)) "BridgeDriverPlugin"
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
      JsObject (
        "name" -> JsString(obj.name),
        "location" -> JsString(obj.location.toString),
        "provider" -> JsString(obj.provider),
        "installable" -> JsBoolean(obj.installable),
        "installed" -> JsBoolean(obj.installed)
      )
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
}
