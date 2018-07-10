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
import java.net.{URI, URL, URLClassLoader}

import com.thing2x.smqd._
import com.thing2x.smqd.plugin.PluginManager.InstallResult
import com.thing2x.smqd.plugin.RepositoryDefinition.IvyModule
import com.typesafe.config.{Config, ConfigFactory, ConfigRenderOptions}
import com.typesafe.scalalogging.StrictLogging
import sbt.librarymanagement.UnresolvedWarning

import scala.collection.JavaConverters._
import scala.concurrent.{ExecutionContext, Future}
/**
  * 2018. 7. 4. - Created by Kwon, Yeong Eon
  */
object PluginManager extends StrictLogging {
  val STATIC_PKG = "smqd-static"
  val CORE_PKG = "smqd-core"

  def apply(config: Config, coreVersion: String): PluginManager = {
    val pluginDirPath = config.getString("dir")
    val pluginInstanceConfDirPath =
      if (config.hasPath("conf")) {
        config.getString("conf")
      }
      else {
        logger.trace("origin of plugin config", config.origin.filename)
        val configFilePath = config.origin.filename
        if (configFilePath != null) {
          val file = new File(configFilePath)
          if (file.exists && file.getParentFile.canRead && file.getParentFile.canWrite) {
            val pluginConfDir = new File(file.getParentFile, "plugins")
            pluginConfDir.mkdir()
            pluginConfDir.getPath
          }
          else {
            new File(new File(pluginDirPath), "plugins").getPath
          }
        }
        else {
          new File(new File(pluginDirPath), "plugins").getPath
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
}

import PluginManager._

class PluginManager(pluginLibPath: String, pluginConfPath: String, pluginManifestUri: Option[String], coreVersion: String) extends StrictLogging {

  //////////////////////////////////////////////////
  // repository definitions
  private val repositoryDefs =
  // repo def for core plugins (internal)
  RepositoryDefinition(CORE_PKG, "thing2x.com", new URI("https://github.com/smqd"), installable = false, "smqd core plugins") +:
    // repo def for manually installed
    RepositoryDefinition(STATIC_PKG, "n/a", new URI("https://github.com/smqd"), installable = false, "manually installed plugins") +:
    findRepositoryDefinitions(findManifest(pluginManifestUri))

  def repositoryDefinitions: Seq[RepositoryDefinition] = repositoryDefs
  def repositoryDefinition(name: String): Option[RepositoryDefinition] = repositoryDefs.find(p => p.name == name)

  private def findRepositoryDefinitions(conf: Config): Seq[RepositoryDefinition] = {
    val cfs = conf.getConfigList("smqd-plugin.repositories").asScala
    cfs.map(repositoryDefinition)
  }

  private def repositoryDefinition(conf: Config): RepositoryDefinition = {
    val name = conf.getString("package-name")
    val provider = conf.getString("provider")
    val description = conf.getOptionString("description").getOrElse("n/a")
    logger.trace(s"Plugin manifest has package '$name'")
    if (conf.hasPath("location")) {
      val location = new URI(conf.getString("location"))
      RepositoryDefinition(name, provider, location, installable = true, description)
    }
    else {
      val group = conf.getString("group")
      val artifact = conf.getString("artifact")
      val version = conf.getString("version")
      val resolvers = conf.getOptionStringList("resolvers").getOrElse(new java.util.Vector[String]())
      val module = IvyModule(group, artifact, version, resolvers.asScala.toVector)
      RepositoryDefinition(name, provider, module, installable = true, description)
    }
  }

  private def findManifest(uriPathOpt: Option[String]): Config = {
    // load reference manifest
    val ref = ConfigFactory.parseResources(getClass.getClassLoader, "smqd-plugins.manifest")

    // is custom uri set?
    val uriPath = uriPathOpt match {
      case Some(p) => p
      case None =>
        logger.info("No plugin manifest is defined")
        return ref
    }

    logger.info(s"Plugin manifest is $uriPath")

    try {
      // try first as a file, incase of relative path
      val file = new File(uriPath)
      val uri = if (file.exists && file.canRead) file.toURI else new URI(uriPath)
      // try to find from uri
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
        logger.warn(s"Invalid plugin manifest location: '$uriPath' {}", ex.getMessage)
        ref
    }
  }

  //////////////////////////////////////////////////
  // plugin definitions
  val libDirectory: Option[File] = findRootDir(pluginLibPath)
  val configDirectory: Option[File] = findConfigDir(pluginConfPath)

  private var packageDefs: Seq[PackageDefinition] = libDirectory match {
    case Some(rootDir) =>             // plugin root directory
      findPluginFiles(rootDir)        // plugin files in the root directories
        .map(findPluginPackageLoader) // plugin loaders
        .flatMap(_.definition)        // to plugin definitions
    case None =>
      findPluginPackageLoader(new File(getClass.getProtectionDomain.getCodeSource.getLocation.getPath)).definition match {
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

  private def findPluginPackageLoader(file: File): PluginPackageLoader = {
    if (file.isDirectory) {
      new PluginPackageLoader(Array(file.toURI.toURL), getClass.getClassLoader)
    }
    else if (file.isFile && file.getPath.endsWith(".plugin")) {
      val meta = ConfigFactory.parseFile(file)
      val pver = meta.getString("version")
      val jars = meta.getStringList("resolved").asScala
      val urls = jars.map(new File(libDirectory.get, _)).map(_.toURI.toURL).toArray
      new PluginPackageLoader(urls, getClass.getClassLoader, pver)
    }
    else { // if (file.isFile && file.getPath.endsWith(".jar")) {
      new PluginPackageLoader(Array(file.toURI.toURL), getClass.getClassLoader)
    }
  }

  private def postInstallPluginPackageMeta(metaFile: File)(implicit ec: ExecutionContext): Future[Option[PackageDefinition]] = Future {
    findPluginPackageLoader(metaFile).definition match {
      case Some(pkgDef) =>
        this.packageDefs = this.packageDefs :+ pkgDef
        Some(pkgDef)
      case None =>
        logger.warn(s"Pakcage loading fail: ${metaFile.getPath}")
        None
    }
  }

  private def findPluginFiles(rootDir: File): Seq[File] = {
    new File(getClass.getProtectionDomain.getCodeSource.getLocation.getPath) +:
      rootDir.listFiles{ file =>
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

  private[plugin] class PluginPackageLoader(urls: Array[URL], parent: ClassLoader, packageDefaultVersion: String = "") {
    private val logger = PluginManager.this.logger
    private val resourceLoader = new URLClassLoader(urls, null)
    private val classLoader = new URLClassLoader(urls, parent)
    var config: Config = _

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
        val repo = repositoryDefinition(packageName).getOrElse(repositoryDefinition(STATIC_PKG).get)

        val defaultVersion = if (packageName.equals(CORE_PKG)) coreVersion else packageDefaultVersion

        val plugins = config.getConfigList("package.plugins").asScala.map{ c =>
          val pluginName = c.getString("name")
          val className = c.getString("class")
          val multiInst = c.getOptionBoolean("multi-instantiable").getOrElse(false)
          val version = c.getOptionString("version").getOrElse(defaultVersion)
          val conf = c.getOptionConfig("default-config").getOrElse(emptyConfig)
          val confSchema = c.getOptionConfig("config-schema").getOrElse(emptyConfig)
          val clazz = classLoader.loadClass(className).asInstanceOf[Class[Plugin]]

          logger.trace(s"Plugin '$pluginName' in package '$packageName' from ${clazz.getProtectionDomain.getCodeSource.getLocation}")

          new PluginDefinition(pluginName, clazz, packageName, version, conf, confSchema, multiInst)
        }

        val pkg = new PackageDefinition(packageName, packageVendor, packageDescription, plugins, repo)
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

  ////////////////////////////////////////////////////////
  // create instance
  def createInstaceFromClassOrPlugin[T <: Plugin](smqd: Smqd, dname: String, dconf: Config, classType: Class[T]): (T, Option[InstanceDefinition[T]]) ={
    val category: String = classType match {
      case c if c.isAssignableFrom(classOf[Service]) => "Service"
      case c if c.isAssignableFrom(classOf[BridgeDriver]) => "BridgeDriver"
      case c if c.isAssignableFrom(classOf[Plugin]) => "Plugin"
      case _ => "Unknown type"
    }
    logger.info(s"$category '$dname' loading...")
    val instanceResult = dconf.getOptionString("entry.class") match {
      case Some(className) =>
        val clazz = getClass.getClassLoader.loadClass(className).asInstanceOf[Class[T]]
        val cons = clazz.getConstructor(classOf[String], classOf[Smqd], classOf[Config])
        (cons.newInstance(dname, smqd, dconf.getConfig("config")), None)
      case None =>
        val plugin = dconf.getString("entry.plugin")
        val autoStart = dconf.getOptionBoolean("entry.auto-start").getOrElse(true)
        val pdef = pluginDefinitions(plugin)
        if (pdef.isEmpty) {
          throw new IllegalStateException(s"Undefined plugin: $plugin")
        }
        else {
          val idef = pdef.head.createInstance(dname, smqd, dconf.getOptionConfig("config"), autoStart, classType)
          (idef.instance, Some(idef))
        }
    }
    logger.info(s"$category '$dname' loaded")
    instanceResult
  }

  def loadInstanceFromConfigs(smqd: Smqd): Seq[Plugin] = {
    if (libDirectory.isEmpty) {
      logger.error("Root directory of plugin manager is not defined")
      Nil
    }
    else {
      val rootDir = libDirectory.get
      val instanceConfs = findPluginInstanceFiles(rootDir).map(ConfigFactory.parseFile)

      val instances = instanceConfs.map(loadInstance(smqd, _))
      instances
    }
  }

  def loadInstanceFromConfigFile(smqd: Smqd, confFile: File): Plugin = {
    val conf = ConfigFactory.parseFile(confFile)
    loadInstance(smqd, conf)
  }

  def loadInstance(smqd: Smqd, instanceConfig: Config): Plugin = {
    val instanceName = instanceConfig.getString("instance")
    val pluginName = instanceConfig.getString("entry.plugin")
    val autoStart = instanceConfig.getBoolean("entry.auto-start")

    logger.info(s"Loading plugin '$pluginName' instance '$instanceName' - auto-start: $autoStart")

    createInstaceFromClassOrPlugin(smqd, instanceName, instanceConfig, classOf[Plugin]) match {
      case (_, Some(idef)) if idef.autoStart =>
        idef.instance.execStart()
        idef.instance
      case (_, Some(idef)) =>
        idef.instance
    }
  }

  def createInstanceConfig(smqd: Smqd, pluginName: String, instanceName: String, file: File, conf: Config): Plugin = {
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

  def updateInstanceConfig(smqd: Smqd, pluginName: String, instanceName: String, file: File, conf: Config): Boolean = {
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

  def deleteInstanceConfig(smqd: Smqd, pluginName: String, instanceName: String, file: File): Boolean = {
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

  private def findPluginInstanceFiles(rootDir: File): Seq[File] = {
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
    repositoryDefinition(packageName) match {
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
      Future {
        install_remote(rdef.location.get)
        InstallSuccess(s"Installing package '${rdef.name}")
      }
    }
    else if (rdef.isIvyModule) {
      install_maven(rdef.name, rdef.module.get, libDirectory.get) match {
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

  private def install_remote(uri: URI): Unit = {

  }

  private def install_maven(packageName: String, moduleDef: IvyModule, rootDir: File): Option[File] = {
    import sbt.librarymanagement.syntax._
    import sbt.librarymanagement.ivy._

    val fileRetrieve = new File(rootDir, "ivy")
    val fileCache = new File(rootDir, "ivy/cache")

    val ivyLogger = sbt.util.LogExchange.logger("com.thing2x.smqd.plugin")
    val ivyResolvers = moduleDef.resolvers.map {
      case "sonatype" =>
        sbt.librarymanagement.MavenRepository("sonatype", "https://oss.sonatype.org/content/groups/public", localIfFile = true)
      case url =>
        sbt.librarymanagement.MavenRepository("maven", url, localIfFile = true)
    }

    val ivyConfig = InlineIvyConfiguration().withLog(ivyLogger).withResolutionCacheDir(fileCache).withResolvers(ivyResolvers)
    val lm = IvyDependencyResolution(ivyConfig)

    val module = moduleDef.group % moduleDef.artifact % moduleDef.version excludeAll(
      ExclusionRule("com.thing2x", "smqd-core_2.12"),
      ExclusionRule("org.scala-lang", "scala-library"),
    ) force()

    lm.retrieve(module, scalaModuleInfo = None, fileRetrieve, ivyLogger) match {
      case Left(w: UnresolvedWarning) =>
        val str= w.failedPaths.map(_.toString).mkString("\n", "\n", "\n")
        logger.warn(s"UnresolvedWarning -- $str", w.resolveException)
        None
      case Right(files: Vector[File]) =>
        val prefixLen = rootDir.getPath.length + 1
        val str = files.map(_.getPath.substring(prefixLen)).toSet.mkString("resolved: [\n\"", "\",\n\"", "\"]\n")
        val metaFile = new File(rootDir, packageName + ".plugin")
        val out = new OutputStreamWriter(new FileOutputStream(metaFile))
        out.write(s"package: $packageName\n")
        out.write(s"group: ${moduleDef.group}\n")
        out.write(s"artifact: ${moduleDef.artifact}\n")
        out.write(s"version: ${moduleDef.version}\n")
        out.write(s"download-time: ${System.currentTimeMillis().toString}\n")
        out.write(str)
        out.close()
        Some(metaFile)
    }
  }

}
