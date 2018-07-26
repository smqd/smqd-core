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

import java.io.{File, InputStreamReader}
import java.net.{URL, URLClassLoader}

import com.thing2x.smqd._
import com.thing2x.smqd.plugin.PluginManager._
import com.typesafe.config.{Config, ConfigFactory}
import com.typesafe.scalalogging.StrictLogging

import scala.collection.JavaConverters._

// 2018. 7. 10. - Created by Kwon, Yeong Eon

private[plugin] object PackageLoader {
  def apply(pm: PluginManager, file: File): PackageLoader = {
    if (file.isDirectory) {
      new PackageLoader(pm, Array(file.toURI.toURL), getClass.getClassLoader)
    }
    else if (file.isFile && file.getPath.endsWith(".plugin")) {
      val meta = ConfigFactory.parseFile(file)
      val pver = meta.getString("version")
      val jars = meta.getStringList("resolved").asScala
      val urls = jars.map(new File(pm.libDirectory.get, _)).map(_.toURI.toURL).toArray
      new PackageLoader(pm, urls, getClass.getClassLoader, pver)
    }
    else { // if (file.isFile && file.getPath.endsWith(".jar")) {
      new PackageLoader(pm, Array(file.toURI.toURL), getClass.getClassLoader)
    }
  }
}

private[plugin] class PackageLoader(pm: PluginManager, urls: Array[URL], parent: ClassLoader, packageDefaultVersion: String = "") extends StrictLogging {
  private val resourceLoader = new URLClassLoader(urls, null)
  private val classLoader = new URLClassLoader(urls, parent)
  private var config: Config = _

  private val emptyConfig = ConfigFactory.parseString("")

  def definition: Option[PackageDefinition] = {
    try {
      val in = resourceLoader.getResourceAsStream("smqd-plugin.conf")
      if (in == null) return None

      config = ConfigFactory.parseReader(new InputStreamReader(in))
      in.close()

      val packageName = config.getString("package.name")
      val packageVendor = config.getOptionString("package.vendor").getOrElse("")
      val packageDescription = config.getOptionString("package.description").getOrElse("")
      val repo = pm.repositoryDefinition(packageName).getOrElse(pm.repositoryDefinition(STATIC_PKG).get)

      val defaultVersion = if (packageName.equals(CORE_PKG)) pm.coreVersion else packageDefaultVersion

      val plugins = config.getConfigList("package.plugins").asScala.map{ c =>
        val pluginName = c.getString("name")
        val className = c.getString("class")
        val multiInst = c.getOptionBoolean("multi-instantiable").getOrElse(false)
        val version = c.getOptionString("version").getOrElse(defaultVersion)
        val conf = c.getOptionConfig("default-config").getOrElse(emptyConfig)
        val confSchema = c.getOptionConfig("config-schema").getOrElse(emptyConfig)
        val clazz = classLoader.loadClass(className).asInstanceOf[Class[Plugin]]

        logger.trace(s"Plugin '$pluginName' in package '$packageName' from ${clazz.getProtectionDomain.getCodeSource.getLocation}")

        PluginDefinition(pluginName, clazz, packageName, version, multiInst, conf, confSchema)
      }

      val pkg = PackageDefinition(packageName, packageVendor, packageDescription, plugins, repo)
      repo.setInstalledPackage(pkg)
      Some(pkg)
    }
    catch {
      case ex: Throwable =>
        logger.warn(s"Plugin fail to loading", ex)
        None
    }
  }
}


