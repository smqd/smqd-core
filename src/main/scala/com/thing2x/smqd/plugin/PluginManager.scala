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

import java.io.{File, FileOutputStream, InputStreamReader, OutputStreamWriter}
import java.net.{URI, URL, URLClassLoader}

import com.thing2x.smqd._
import com.thing2x.smqd.plugin.PluginManager.InstallResult
import com.typesafe.config.{Config, ConfigFactory}
import com.typesafe.scalalogging.StrictLogging
import sbt.librarymanagement.UnresolvedWarning

import scala.collection.JavaConverters._
import scala.collection.mutable
import scala.concurrent.{ExecutionContext, Future}
/**
  * 2018. 7. 4. - Created by Kwon, Yeong Eon
  */
object PluginManager {
  val STATIC_PKG = "smqd-static"
  val CORE_PKG = "smqd-core"

  def apply(config: Config, coreVersion: String): PluginManager =
    new PluginManager(config.getString("dir"), config.getOptionString("manifest"), coreVersion)

  def apply(pluginDirPath: String, pluginManifestUri: String, coreVersion: String) =
    new PluginManager(pluginDirPath, Some(pluginManifestUri), coreVersion)

  def apply(pluginDirPath: String, pluginManifestUri: Option[String] = None, coreVersion: String = "") =
    new PluginManager(pluginDirPath, pluginManifestUri, coreVersion)

  trait InstallResult { def msg: String }
  case class InstallSuccess(msg: String) extends InstallResult
  case class NotInstallable(msg: String)  extends InstallResult
  case class PackageNotFound(msg: String) extends InstallResult
  case class InvalidStateToInstall(msg: String) extends InstallResult
  case class InstallFailure(msg: String, cause: Option[Throwable]) extends InstallResult
}

import PluginManager._

class PluginManager(pluginDirPath: String, pluginManifestUri: Option[String], coreVersion: String) extends StrictLogging {

  //////////////////////////////////////////////////
  // repository definitions
  private val repositoryDefs =
    PluginRepositoryDefinition(CORE_PKG, "thing2x.com", new URI("https://github.com/smqd"), installable = false) +: // repo def for core plugins (internal)
      PluginRepositoryDefinition(STATIC_PKG, "n/a", new URI("https://github.com/smqd"), installable = false) +:     // repo def for manually installed
      findRepositoryDefinitions(findManifest(pluginManifestUri))

  def repositoryDefinitions: Seq[PluginRepositoryDefinition] = repositoryDefs
  def repositoryDefinition(name: String): Option[PluginRepositoryDefinition] = repositoryDefs.find(p => p.name == name)

  private def findRepositoryDefinitions(conf: Config): Seq[PluginRepositoryDefinition] = {
    val cfs = conf.getConfigList("smqd-plugin.repositories").asScala
    cfs.map(repositoryDefinition)
  }

  private def repositoryDefinition(conf: Config): PluginRepositoryDefinition = {
    val name = conf.getString("package-name")
    val provider = conf.getString("provider")
    logger.trace(s"Plugin manifest has package '$name'")
    if (conf.hasPath("location")) {
      val location = new URI(conf.getString("location"))
      PluginRepositoryDefinition(name, provider, location, installable = true)
    }
    else {
      val group = conf.getString("group")
      val artifact = conf.getString("artifact")
      val version = conf.getString("version")
      PluginRepositoryDefinition(name, provider, group, artifact, version, installable = true)
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
  val rootDirectory: Option[File] = findRootDir(pluginDirPath)

  private var packageDefs: Seq[PluginPackageDefinition] = rootDirectory match {
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
  def packageDefinitions: Seq[PluginPackageDefinition] = packageDefs

  /** all plugin definitions */
  def pluginDefinitions: Seq[PluginDefinition] = packageDefs.flatMap(pd => pd.plugins)
  /** find plugin definitions by package name */
  def pluginDefinitionsInPackage(packageName: String): Seq[PluginDefinition] = packageDefs.filter(_.name == packageName).flatMap(p => p.plugins)
  /** find plugin definitions that has name contains searchName */
  def pluginDefinitions(searchName: String): Seq[PluginDefinition] = packageDefs.flatMap(_.plugins).filter(_.name.contains(searchName))
  /** find plugin definition that has name exactly matached */
  def pluginDefinition(pluginName: String): Option[PluginDefinition] = packageDefs.flatMap(_.plugins).find(_.name == pluginName)

  /** find all plugin instances of the plugin */
  def instances(pluginName: String): Seq[PluginInstance[Plugin]] = packageDefs.flatMap(_.plugins).filter(_.name == pluginName).flatMap(_.instances)
  /** find all plugin instances of the plugin that has name contains searchName */
  def instances(pluginName: String, searchName: String): Seq[PluginInstance[Plugin]] = packageDefs.flatMap(_.plugins).filter(_.name == pluginName).flatMap(_.instances).filter(_.name.contains(searchName))
  /** find the plugin instance by plugin name and instance name */
  def instance(pluginName: String, instanceName: String): Option[PluginInstance[Plugin]] = packageDefs.flatMap(_.plugins).filter(_.name == pluginName).flatMap(_.instances).find(_.name == instanceName)

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
      val urls = jars.map(new File(rootDirectory.get, _)).map(_.toURI.toURL).toArray
      new PluginPackageLoader(urls, getClass.getClassLoader, pver)
    }
    else { // if (file.isFile && file.getPath.endsWith(".jar")) {
      new PluginPackageLoader(Array(file.toURI.toURL), getClass.getClassLoader)
    }
  }

  private def postInstallPluginPackageMeta(metaFile: File)(implicit ec: ExecutionContext): Future[Option[PluginPackageDefinition]] = Future {
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
      logger.info("Plugin directory is {}", file.getPath)
      Some(file)
    }
    else {
      logger.info("Plugin directory is not accessible: {}", rootDir)
      None
    }
  }

  private[plugin] class PluginPackageLoader(urls: Array[URL], parent: ClassLoader, packageDefaultVersion: String = "") {
    private val logger = PluginManager.this.logger
    private val resourceLoader = new URLClassLoader(urls, null)
    private val classLoader = new URLClassLoader(urls, parent)
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
        val packageDescription = config.getOptionString("package.description").getOrElse("")
        val repo = repositoryDefinition(packageName).getOrElse(repositoryDefinition(STATIC_PKG).get)

        val defaultVersion = if (packageName.equals(CORE_PKG)) coreVersion else packageDefaultVersion

        val plugins = config.getConfigList("package.plugins").asScala.map{ c =>
          val pluginName = c.getString("name")
          val className = c.getString("class")
          val multiInst = c.getOptionBoolean("multi-instantiable").getOrElse(false)
          val version = c.getOptionString("version").getOrElse(defaultVersion)
          val conf = c.getOptionConfig("default-config").getOrElse(emptyConfig)
          val clazz = classLoader.loadClass(className).asInstanceOf[Class[Plugin]]

          logger.trace(s"Plugin '$pluginName' in package '$packageName' from ${clazz.getProtectionDomain.getCodeSource.getLocation}")

          new PluginDefinition(pluginName, clazz, packageName, version, conf, multiInst)
        }

        val pkg = new PluginPackageDefinition(packageName, packageVendor, packageDescription, plugins, repo)
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
  def createInstaceFromClassOrPlugin[T <: Plugin](smqd: Smqd, dname: String, dconf: Config, classType: Class[T]): T ={
    val category: String = classType match {
      case c if c.isAssignableFrom(classOf[Service]) => "Service"
      case c if c.isAssignableFrom(classOf[BridgeDriver]) => "BridgeDriver"
      case _ => "Unknown type"
    }
    logger.info(s"$category '$dname' loading...")
    val instance = dconf.getOptionString("entry.class") match {
      case Some(className) =>
        val clazz = getClass.getClassLoader.loadClass(className).asInstanceOf[Class[T]]
        val cons = clazz.getConstructor(classOf[String], classOf[Smqd], classOf[Config])
        cons.newInstance(dname, smqd, dconf.getConfig("config"))
      case None =>
        val plugin = dconf.getString("entry.plugin")
        val pdef = pluginDefinitions(plugin)
        if (pdef.isEmpty) {
          throw new IllegalStateException(s"Undefined plugin: $plugin")
        }
        else {
          pdef.head.createInstance(dname, smqd, dconf.getOptionConfig("config"), classType)
        }
    }
    logger.info(s"$category '$dname' loaded")
    instance
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

  def installPackage(smqd: Smqd, rdef: PluginRepositoryDefinition)(implicit ec: ExecutionContext): Future[InstallResult] = {
    if (!rdef.installable) {
      Future{ NotInstallable(s"Package '${rdef.name}' is not installable") }
    }
    else if (rootDirectory.isEmpty) {
      Future{ InvalidStateToInstall(s"Plugin manager's root directory is not defined")}
    }
    else if (rdef.isRemoteFile) {
      Future {
        install_remote(rdef.location.get)
        InstallSuccess(s"Installing package '${rdef.name}")
      }
    }
    else if (rdef.isIvyModule) {
      install_ivy(rdef.name, rdef.module.get._1, rdef.module.get._2, rdef.module.get._3, rootDirectory.get) match {
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

  private def install_ivy(packageName: String, group: String, artifact: String, version: String, rootDir: File): Option[File] = {
    import sbt.librarymanagement.syntax._
    import sbt.librarymanagement.ivy._

    val fileRetrieve = new File(rootDir, "ivy")
    val fileCache = new File(rootDir, "ivy/cache")

    val ivyLogger = sbt.util.LogExchange.logger("com.thing2x.smqd.plugin")
    val ivyConfig = InlineIvyConfiguration().withLog(ivyLogger).withResolutionCacheDir(fileCache)
    val lm = IvyDependencyResolution(ivyConfig)

    val module = group % artifact % version

    lm.retrieve(module, scalaModuleInfo = None, fileRetrieve, ivyLogger) match {
      case Left(w: UnresolvedWarning) =>
        val str= w.failedPaths.map(_.toString).mkString("\n", "\n", "\n")
        logger.warn(s"UnresolvedWarning -- $str", w.resolveException)
        None
      case Right(files: Vector[File]) =>
        val prefixLen = rootDir.getPath.length + 1
        val str = files.map(_.getPath.substring(prefixLen)).mkString("resolved: [\n\"", "\",\n\"", "\"]\n")
        val metaFile = new File(rootDir, packageName + ".plugin")
        val out = new OutputStreamWriter(new FileOutputStream(metaFile))
        out.write(s"package: $packageName\n")
        out.write(s"group: $group\n")
        out.write(s"artifact: $artifact\n")
        out.write(s"version: $version\n")
        out.write(s"download-time: ${System.currentTimeMillis().toString}\n")
        out.write(str)
        out.close()
        Some(metaFile)
    }
  }

}
