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

import java.io._

import com.thing2x.smqd._
import com.thing2x.smqd.plugin.PluginManager.InstallResult
import com.thing2x.smqd.util.ConfigUtil._
import com.typesafe.config.{Config, ConfigFactory, ConfigRenderOptions}
import com.typesafe.scalalogging.StrictLogging

import scala.collection.JavaConverters._
import scala.concurrent.{ExecutionContext, Future}

// 2018. 7. 4. - Created by Kwon, Yeong Eon

object PluginManager extends StrictLogging {
  val STATIC_PKG = "smqd-static"
  val CORE_PKG = "smqd-core"
  val POJO_PKG = "smqd-pojo"

  val PM_CONF_DIR_NAME = "plugins"
  val PM_LIB_DIR_NAME = "plugins"

  def apply(config: Config, coreVersion: String): PluginManager = {
    val pluginDirPath = config.getString("dir")
    val pluginInstanceConfDirPath =
      if (config.hasPath("conf")) {
        config.getString("conf")
      }
      else {
        logger.trace("origin of plugin config: {}", config.origin.filename)
        val configFilePath = config.origin.filename
        if (configFilePath != null) {
          val file = new File(configFilePath)
          if (file.exists && file.getParentFile.canRead && file.getParentFile.canWrite) {
            val pluginConfDir = new File(file.getParentFile, PM_CONF_DIR_NAME)
            pluginConfDir.mkdir()
            pluginConfDir.getPath
          }
          else {
            new File(new File(pluginDirPath), PM_CONF_DIR_NAME).getPath
          }
        }
        else {
          new File(new File(pluginDirPath), PM_CONF_DIR_NAME).getPath
        }
      }
    new PluginManager(pluginDirPath, pluginInstanceConfDirPath, config.getOptionString("manifest"), coreVersion)
  }

  def apply(pluginLibPath: String, pluginConfPath: String, pluginManifestUri: String, coreVersion: String) =
    new PluginManager(pluginLibPath, pluginConfPath, Some(pluginManifestUri), coreVersion)

  def apply(pluginLibPath: String, pluginConfPath: String, pluginManifestUri: Option[String] = None, coreVersion: String = "") =
    new PluginManager(pluginLibPath, pluginConfPath, pluginManifestUri, coreVersion)

  trait InstallResult { def msg: String }
  case class InstallSuccess(msg: String) extends InstallResult
  case class NotInstallable(msg: String)  extends InstallResult
  case class PackageNotFound(msg: String) extends InstallResult
  case class InvalidStateToInstall(msg: String) extends InstallResult
  case class InstallFailure(msg: String, cause: Option[Throwable]) extends InstallResult

  trait ReloadResult { def msg: String }
  case class ReloadSuccess(msg: String) extends ReloadResult
  case class ReloadFailure(msg: String) extends ReloadResult
}

import com.thing2x.smqd.plugin.PluginManager._

class PluginManager(pluginLibPath: String, pluginConfPath: String, pluginManifestUri: Option[String], val coreVersion: String) extends StrictLogging {

  private val repositoryManager: RepositoryManager = new RepositoryManager(this, pluginManifestUri)
  def repositoryDefinitions: Seq[RepositoryDefinition] = repositoryManager.repositoryDefs
  def repositoryDefinition(name: String): Option[RepositoryDefinition] = repositoryManager.repositoryDefinition(name)

  //////////////////////////////////////////////////
  // plugin definitions
  val libDirectory: Option[File] = findRootDir(pluginLibPath)
  val configDirectory: Option[File] = findConfigDir(pluginConfPath)

  private var packageDefs: Seq[PackageDefinition] = libDirectory match {
    case Some(rootDir) =>             // plugin root directory
      findPluginFiles(rootDir)        // plugin files in the root directories
        .map(findPackageLoader)       // plugin loaders
        .flatMap(_.definition)        // to plugin definitions
    case None =>
      findPackageLoader(new File(getClass.getProtectionDomain.getCodeSource.getLocation.getPath)).definition match {
        case Some(d) => Seq(d)
        case None => Nil
      }
  }

  /** all package definitions */
  def packageDefinitions: Seq[PackageDefinition] = packageDefs

  /** all plugin definitions */
  def pluginDefinitions: Seq[PluginDefinition] = packageDefs.flatMap(pd => pd.plugins)
  /** find plugin definitions by package name */
  def pluginDefinitionsInPackage(packageName: String): Seq[PluginDefinition] = packageDefs.filter(_.name == packageName).flatMap(p => p.plugins)
  /** find plugin definitions that has name contains searchName */
  def pluginDefinitions(searchName: String): Seq[PluginDefinition] = packageDefs.flatMap(_.plugins).filter(_.name.contains(searchName))
  /** find plugin definition that has name exactly matached */
  def pluginDefinition(pluginName: String): Option[PluginDefinition] = packageDefs.flatMap(_.plugins).find(_.name == pluginName)

  /** find all plugin instances of the plugin */
  def instances(pluginName: String): Seq[InstanceDefinition[Plugin]] = packageDefs.flatMap(_.plugins).filter(_.name == pluginName).flatMap(_.instances)
  /** find all plugin instances of the plugin that has name contains searchName */
  def instances(pluginName: String, searchName: String): Seq[InstanceDefinition[Plugin]] = packageDefs.flatMap(_.plugins).filter(_.name == pluginName).flatMap(_.instances).filter(_.name.contains(searchName))
  /** find the plugin instance by plugin name and instance name */
  def instance(pluginName: String, instanceName: String): Option[InstanceDefinition[Plugin]] = packageDefs.flatMap(_.plugins).filter(_.name == pluginName).flatMap(_.instances).find(_.name == instanceName)

  def servicePluginDefinitions: Seq[PluginDefinition] = pluginDefinitions.filter(pd => classOf[Service].isAssignableFrom(pd.clazz))
  def bridgePluginDefinitions: Seq[PluginDefinition] = pluginDefinitions.filter(pd => classOf[BridgeDriver].isAssignableFrom(pd.clazz))

  def definePojoInstanceDefinition[T <: Plugin](smqd: Smqd, instName: String, instConf: Config): Option[InstanceDefinition[T]] = {
    try {
      instConf.getOptionString("entry.class") match {
        case Some(className) =>
          val autoStart = instConf.getOptionBoolean("entry.auto-start").getOrElse(true)
          val clazz = getClass.getClassLoader.loadClass(className).asInstanceOf[Class[Plugin]]
          val pdef = PluginDefinition.nonPluggablePlugin(instName, clazz)
          val idef: InstanceDefinition[T] = pdef.createInstance(instName, smqd, instConf.getOptionConfig("config"), autoStart)
          logger.info(s"Plugin '$instName' loaded as POJO")
          packageDefs.find(_.name == POJO_PKG) match {
            case Some(pkg) => // POJO package가 이미 존재하면 기존 팩키지를 확장하고
              packageDefs = packageDefs.filter(_.name != POJO_PKG) :+ pkg.append(pdef)
            case None => // 없다면 pojo 팩키지를 생성한다.
              packageDefs = packageDefs :+ PackageDefinition(POJO_PKG, "n/a", "POJO plugins", Seq(pdef), null)
          }
          Some(idef)
        case None =>
          logger.info(s"Plugin '$instName' not found as POJO")
          None
      }
    }
    catch {
      case ex: Throwable =>
        logger.error(s"Fail to load and create an instance of plugin '$instName'", ex)
        None
    }
  }

  private def findPackageLoader(file: File): PackageLoader = {
    if (file.isDirectory) {
      new PackageLoader(this, Array(file.toURI.toURL), getClass.getClassLoader)
    }
    else if (file.isFile && file.getPath.endsWith(".plugin")) {
      val meta = ConfigFactory.parseFile(file)
      val pver = meta.getString("version")
      val jars = meta.getStringList("resolved").asScala
      val urls = jars.map(new File(libDirectory.get, _)).map(_.toURI.toURL).toArray
      new PackageLoader(this, urls, getClass.getClassLoader, pver)
    }
    else { // if (file.isFile && file.getPath.endsWith(".jar")) {
      new PackageLoader(this, Array(file.toURI.toURL), getClass.getClassLoader)
    }
  }

  private def replacePackageDefinitions(pdefs: Seq[PackageDefinition], anotherDef: PackageDefinition): Seq[PackageDefinition] = {
    val rt = pdefs.find(_.name == anotherDef.name) match {
      case Some(oldDef) =>
        val plugins = oldDef.plugins
        plugins.flatMap(_.instances) foreach { inst =>
          if (inst.instance.status != InstanceStatus.STOPPED) {
            // stopping instance if it is still running
            logger.warn(s"Force to stop plugin: ${inst.pluginDef.name} ${inst.name}")
            inst.instance.execStop()
          }
        }
        val instStatus = plugins.flatMap(_.instances).map(idef => s"${idef.pluginDef.name} ${idef.name}(${idef.status})")
        logger.info(s"Replacing package '${oldDef.name}' having plugins $instStatus")
        pdefs.filterNot( _ eq oldDef )
      case None =>
        pdefs
    }
    rt :+ anotherDef
  }

  private def postInstallPluginPackageJar(jarFile: File)(implicit ec: ExecutionContext): Future[Option[PackageDefinition]] = Future {
    findPackageLoader(jarFile).definition match {
      case Some(pkgDef) =>
        this.packageDefs = replacePackageDefinitions(this.packageDefs, pkgDef)
        Some(pkgDef)
      case None =>
        None
    }
  }

  private def postInstallPluginPackageMeta(metaFile: File)(implicit ec: ExecutionContext): Future[Option[PackageDefinition]] = Future {
    findPackageLoader(metaFile).definition match {
      case Some(pkgDef) =>
        this.packageDefs = replacePackageDefinitions(this.packageDefs, pkgDef)
        Some(pkgDef)
      case None =>
        logger.warn(s"Pakcage loading fail: ${metaFile.getPath}")
        None
    }
  }

  /** find candidates archive/meta/directory for plugin */
  private def findPluginFiles(rootDir: File): Seq[File] = {
    val codeBase = getClass.getProtectionDomain.getCodeSource.getLocation.getPath
    new File(codeBase) +: rootDir.listFiles{ file =>
      val filename = file.getName
      if (!file.canRead) false
      else if (filename.endsWith(".jar") && file.isFile) true
      else if (filename.endsWith(".plugin") && file.isFile) true
      else if (file.isDirectory) true
      else false
    }
  }

  private def findRootDir(rootDir: String): Option[File] = {
    val file = new File(rootDir)
    if (file.isDirectory && file.canRead && file.canWrite) {
      logger.info("Plugin repo directory is {}", file.getPath)
      Some(file)
    }
    else {
      logger.info("Plugin repo directory is not accessible: {}", rootDir)
      None
    }
  }

  private def findConfigDir(confDir: String): Option[File] = {
    val file = new File(confDir)
    if (file.isDirectory && file.canRead && file.canWrite) {
      logger.info("Plugin config directory is {}", file.getPath)
      Some(file)
    }
    else {
      logger.info("Plugin config directory is not accessible: {}", confDir)
      None
    }
  }

  def logRepositoryDefinitions(): Unit = {
    //// display list of repositories for information
    repositoryDefinitions.foreach { repo =>
      repo.packageDefinition match {
        case Some(pkg) =>
          val inst = if (repo.installed) "installed" else if (repo.installable) "installable" else "non-installable"
          val info = pkg.plugins.map( _.name).mkString(", ")
          val size = pkg.plugins.size
          logger.info(s"Plugin package '${repo.name}' has $size $inst plugin${ if(size > 1) "s" else ""}: $info")
        case None =>
          logger.info(s"Plugin package '${repo.name}' is not installed")
      }
    }
  }

  ////////////////////////////////////////////////////////
  // create instance

  def findInstanceConfigs: Seq[Config] = {
    if (libDirectory.isEmpty) {
      logger.warn("Root directory of plugin manager is not defined")
      Nil
    }
    else {
      val rootDir = libDirectory.get
      findInstanceConfigFiles(rootDir).map(ConfigFactory.parseFile)
    }
  }

  /** define an instance with the given config, and start it if auto-start is true in the config */
  def loadInstance(smqd: Smqd, instanceConfig: Config): Option[InstanceDefinition[Plugin]] = {
    val instanceName = instanceConfig.getString("instance")
    val pluginName = instanceConfig.getString("entry.plugin")
    val autoStart = instanceConfig.getBoolean("entry.auto-start")

    logger.info(s"Loading plugin '$pluginName' instance '$instanceName' - auto-start: $autoStart")

    InstanceDefinition.defineInstance(smqd, instanceName, instanceConfig).map { idef =>
      if (idef.autoStart)
        idef.instance.execStart()
      idef
    }
  }

  /** read instance config from the given file, then define an instance, and start it if auto-start is true in the config */
  def loadInstanceFromConfigFile(smqd: Smqd, configFile: File): Option[InstanceDefinition[Plugin]] = {
    val conf = ConfigFactory.parseFile(configFile)
    loadInstance(smqd, conf)
  }

  /** save the instance config into the given file, then define an instance, and start it if auto-start is true in the config */
  def createInstanceConfigFile(smqd: Smqd, pluginName: String, instanceName: String, file: File, conf: Config): Option[InstanceDefinition[Plugin]] = {
    val autoStart = conf.getBoolean("auto-start")
    val subConfig = conf.getConfig("config")

    val out = new OutputStreamWriter(new FileOutputStream(file))
    val option = ConfigRenderOptions.defaults.setJson(true).setComments(false).setOriginComments(false)

    out.write(s"instance: $instanceName\n\n")
    out.write(s"entry.plugin: $pluginName\n")
    out.write(s"entry.auto-start: $autoStart\n")
    out.write(s"config: ${subConfig.root.render(option)}")
    out.close()

    loadInstanceFromConfigFile(smqd, file)
  }

  /** overwrite the instance config file, then define the instance, and start it if auto-start is true in the config */
  def updateInstanceConfigFile(smqd: Smqd, pluginName: String, instanceName: String, file: File, conf: Config): Boolean = {
    pluginDefinition(pluginName) match {
      case Some(pdef) =>
        if (pdef.removeInstance(instanceName)) {
          val autoStart = conf.getBoolean("auto-start")
          val subConfig = conf.getConfig("config")

          val out = new OutputStreamWriter(new FileOutputStream(file))
          val option = ConfigRenderOptions.defaults.setJson(true).setComments(false).setOriginComments(false)

          out.write(s"instance: $instanceName\n\n")
          out.write(s"entry.plugin: $pluginName\n")
          out.write(s"entry.auto-start: $autoStart\n")
          out.write(s"config: ${subConfig.root.render(option)}")
          out.close()

          loadInstanceFromConfigFile(smqd, file)
          true
        }
        else {
          false
        }
      case None =>
        false
    }
  }

  def deleteInstanceConfigFile(smqd: Smqd, pluginName: String, instanceName: String, file: File): Boolean = {
    pluginDefinition(pluginName) match {
      case Some(pdef) =>
        if (pdef.removeInstance(instanceName)) {
          file.delete()
        }
        else {
          false
        }
      case None =>
        false
    }
  }

  private def findInstanceConfigFiles(rootDir: File): Seq[File] = {
    if (configDirectory.isEmpty)
      return Nil
    val confDir = configDirectory.get
    if (confDir.exists() && confDir.isDirectory && confDir.canRead) confDir.listFiles(new FileFilter {
      override def accept(file: File): Boolean = file.isFile && file.getName.endsWith(".conf")
    })
    else {
      Nil
    }
  }

  ////////////////////////////////////////////////////////
  // install package

  def installPackage(smqd: Smqd, packageName: String)(implicit ec: ExecutionContext): Future[InstallResult] = {
    repositoryManager.repositoryDefinition(packageName) match {
      case Some(rdef) =>
        installPackage(smqd, rdef)
      case None =>
        Future { PackageNotFound(s"Package $packageName not found") }
    }
  }

  def installPackage(smqd: Smqd, rdef: RepositoryDefinition)(implicit ec: ExecutionContext): Future[InstallResult] = {
    if (!rdef.installable) {
      Future{ NotInstallable(s"Package '${rdef.name}' is not installable") }
    }
    else if (libDirectory.isEmpty) {
      Future{ InvalidStateToInstall(s"Plugin manager's root directory is not defined")}
    }
    else if (rdef.isRemoteFile) {
      import smqd.Implicit._
      repositoryManager.installHttp(rdef.location.get, libDirectory.get) match {
        case Some(file) =>
          postInstallPluginPackageJar(file) map {
            case Some(pkgDef) =>
              InstallSuccess(s"Installed package '${pkgDef.name}")
            case None =>
              InstallFailure("Install failure to load jar", None)
          }
        case None =>
          Future { InstallFailure(s"Install failure", None) }
      }
    }
    else if (rdef.isMavenModule) {
      repositoryManager.installMaven(rdef.name, rdef.module.get, libDirectory.get) match {
        case Some(meta) =>
          postInstallPluginPackageMeta(meta) map {
            case Some(pkgDef) =>
              InstallSuccess(s"Installed package '${pkgDef.name}")
            case None =>
              InstallFailure("Install failure to load meta", None)
          }
        case None =>
          Future { InstallFailure(s"Install failure", None) }
      }
    }
    else {
      Future { InvalidStateToInstall(s"Package '${rdef.name}' has no valid repository information") }
    }
  }

  def reloadPackage(smqd: Smqd, rdef: RepositoryDefinition)(implicit ec: ExecutionContext): Future[ReloadResult] = {
    Future {
      rdef.packageDefinition match {
        case Some(_) =>
//          pdef.plugins.foreach { pl =>
//          }
          ReloadFailure(s"Not implemented")
        case None =>
          ReloadFailure(s"Plugin '${rdef.name} not found")
      }
    }
  }
}
