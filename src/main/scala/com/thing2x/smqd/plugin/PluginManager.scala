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
import java.net.{URI, URL, URLClassLoader}

import com.thing2x.smqd._
import com.typesafe.config.{Config, ConfigFactory}
import com.typesafe.scalalogging.StrictLogging

import scala.collection.JavaConverters._
/**
  * 2018. 7. 4. - Created by Kwon, Yeong Eon
  */
object PluginManager {
  def apply(pluginDirPath: String, pluginManifestUri: String) = new PluginManager(pluginDirPath, pluginManifestUri)
}

class PluginManager(pluginDirPath: String, pluginManifestUri: String) extends StrictLogging {

  //////////////////////////////////////////////////
  // repository definitions

  private val STATIC_PKG = "smqd.static"
  private val CORE_PKG = "smqd.core"

  private val repositoryDefs =
    PluginRepositoryDefinition(CORE_PKG, new URI("https://github.com/smqd"), "n/a", installable = false) +:       // repo def for core plugins (internal)
      PluginRepositoryDefinition(STATIC_PKG, new URI("https://github.com/smqd"), "n/a", installable = false) +:   // repo def for manually installed
      findRepositoryDefinitions(findManifest(pluginManifestUri))

  def repositoryDefinitions: Seq[PluginRepositoryDefinition] = repositoryDefs
  def repositoryDefinition(name: String): Option[PluginRepositoryDefinition] = repositoryDefs.find(p => p.name == name)

  private def findRepositoryDefinitions(conf: Config): Seq[PluginRepositoryDefinition] = {
    val cfs = conf.getConfigList("smqd-plugin.repositories").asScala
    cfs.map(repositoryDefinition)
  }

  private def repositoryDefinition(conf: Config): PluginRepositoryDefinition = {
    val name = conf.getString("name")
    val location = new URI(conf.getString("location"))
    val provider = conf.getString("provider")
    PluginRepositoryDefinition(name, location, provider, installable = true)
  }

  private def findManifest(uriPath: String): Config = {
    val ref = ConfigFactory.parseResources("smqd-plugins.manifest")
    if (uriPath == null || uriPath == "") {
      logger.info("No plugin manifest is defined")
      return ref
    }
    try {
      val uri = new URI(uriPath)
      val in = uri.toURL.openStream()

      if (in == null){
        logger.warn(s"Can not access plugin manifest: ${uri.toString}")
        ref
      }
      else {
        ConfigFactory.parseReader(new InputStreamReader(in)).withFallback(ref)
      }
    } catch {
      case ex: Throwable =>
        logger.warn(s"Invalid plugin manifest location: $uriPath", ex)
        ref
    }
  }

  //////////////////////////////////////////////////
  // plugin definitions
  private val pluginDefs = findRootDir(pluginDirPath) match {
    case Some(rootDir) =>            // plugin root directory
      findPluginFiles(rootDir)       // plugin files in the root directories
        .map(findPluginLoader)       // plugin loaders
        .flatMap(_.definition)       // to plugin definitions
    case None =>
      findPluginLoader(getClass.getProtectionDomain.getCodeSource.getLocation).definition match {
        case Some(d) => Seq(d)
        case None => Nil
      }
  }

  /** all package definitions */
  def packageDefinitions: Seq[PluginPackageDefinition] = pluginDefs
  /** all plugin definitions */
  def pluginDefinitions: Seq[PluginDefinition] = pluginDefs.flatMap(pd => pd.plugins)
  /** find plugin definitions by package name*/
  def pluginDefinitionsInPackage(packageName: String): Seq[PluginDefinition] = pluginDefs.filter(pd => pd.name == packageName).flatMap(p => p.plugins)
  def pluginDefinitions(pluginName: String): Seq[PluginDefinition] = pluginDefs.flatMap(pd => pd.plugins).filter(p => p.name == pluginName)

  def servicePluginDefinitions: Seq[PluginDefinition] = pluginDefinitions.filter(pd => classOf[SmqServicePlugin].isAssignableFrom(pd.clazz))
  def bridgePluginDefinitions: Seq[PluginDefinition] = pluginDefinitions.filter(pd => classOf[SmqBridgePlugin].isAssignableFrom(pd.clazz))

  private def findPluginLoader(url: URL): PluginPackageLoader =
    new PluginPackageLoader(url, getClass.getClassLoader)
  private def findPluginLoader(file: File): PluginPackageLoader =
    new PluginPackageLoader(file.toURI.toURL, getClass.getClassLoader)

  private def findPluginFiles(rootDir: File): Seq[File] =
    rootDir.listFiles(file =>  file.canRead && ((file.getName.endsWith(".jar") && file.isFile) || file.isDirectory))

  private def findRootDir(rootDir: String): Option[File] = {
    val file = new File(rootDir)
    if (file.isDirectory && file.canRead) {
      logger.info("Plugin directory is {}", rootDir)
      Some(file)
    }
    else {
      logger.info("Plugin directory is not accessible: {}", rootDir)
      None
    }
  }

  private[plugin] class PluginPackageLoader(url: URL, parent: ClassLoader) {
    private val logger = PluginManager.this.logger
    private val resourceLoader = new URLClassLoader(Array(url), null)
    private val classLoader = new URLClassLoader(Array(url), parent)
    var config: Config = _

    private val emptyConfig = ConfigFactory.parseString("")

    def definition: Option[PluginPackageDefinition] = {
      try {
        val in = resourceLoader.getResourceAsStream("smqd-plugin.conf")
        if (in == null) return None

        config = ConfigFactory.parseReader(new InputStreamReader(in))
        in.close()

        val packageName = config.getString("package.name")
        val packageVendor = config.getOptionString("package.vendor").getOrElse("")
        val packageDescription = config.getOptionString("package.descrioption").getOrElse("")
        val repo = repositoryDefinition(packageName).getOrElse(repositoryDefinition(STATIC_PKG).get)

        val plugins = config.getConfigList("package.plugins").asScala.map{ c =>
          val pluginName = c.getString("name")
          val className = c.getString("class")
          val multiplicable = c.getOptionBoolean("multiplicable").getOrElse(false)
          val version = c.getOptionString("version").getOrElse("")
          val conf = c.getOptionConfig("default-config").getOrElse(emptyConfig)
          val clazz = classLoader.loadClass(className).asInstanceOf[Class[SmqPlugin]]

          PluginDefinition(pluginName, clazz, version, conf, multiplicable)
        }

        logger.trace(s"Plugin candidate '$packageName' in ${url.getPath}")

        val pkg = PluginPackageDefinition(packageName, packageVendor, packageDescription, plugins, repo)
        repo.setInstalledPackage(pkg)
        Some(pkg)
      }
      catch {
        case ex: Throwable =>
          logger.warn(s"Plugin fail to loading: ${url.getPath}", ex)
          None
      }
    }
  }
}
