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
import java.net.URI
import java.nio.file.FileSystems

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.{HttpRequest, HttpResponse, Uri}
import akka.stream.scaladsl.{FileIO, Sink, Source}
import akka.stream.{IOResult, Materializer}
import com.thing2x.smqd.plugin.PluginManager.{CORE_PKG, STATIC_PKG}
import com.thing2x.smqd.plugin.RepositoryDefinition.MavenModule
import com.thing2x.smqd.util.ConfigUtil._
import com.typesafe.config.{Config, ConfigFactory}
import com.typesafe.scalalogging.StrictLogging
import sbt.librarymanagement.UnresolvedWarning

import scala.jdk.CollectionConverters._
import scala.concurrent.Future
import scala.util.Try

// 2018. 7. 10. - Created by Kwon, Yeong Eon

class RepositoryManager(pm: PluginManager, pluginManifestUri: Option[String]) extends StrictLogging {
  //////////////////////////////////////////////////
  // repository definitions
  private[plugin] val repositoryDefs =
    // repo def for core plugins (internal)
    RepositoryDefinition(CORE_PKG, "http://www.thing2x.com", new URI("https://github.com/smqd"), installable = false, "smqd core plugins") +:
      // repo def for manually installed
      RepositoryDefinition(STATIC_PKG, "n/a", new URI("https://github.com/smqd"), installable = false, "manually installed plugins") +:
      findRepositoryDefinitions(findManifest(pluginManifestUri))

  def repositoryDefinitions: Seq[RepositoryDefinition] = repositoryDefs
  def repositoryDefinition(name: String): Option[RepositoryDefinition] = repositoryDefs.find(p => p.name == name)

  private def findRepositoryDefinitions(conf: Config): Seq[RepositoryDefinition] = {
    val cfs = conf.getConfigList("smqd-plugin.repositories").asScala
    cfs.map(repositoryDefinition).toSeq
  }

  private def repositoryDefinition(conf: Config): RepositoryDefinition = {
    val name = conf.getString("package-name")
    val provider = conf.getString("provider")
    val description = conf.getOptionString("description").getOrElse("n/a")
    logger.trace(s"Plugin manifest has package '$name'")
    if (conf.hasPath("location")) {
      val location = new URI(conf.getString("location"))
      RepositoryDefinition(name, provider, location, installable = true, description)
    } else {
      val group = conf.getString("group")
      val artifact = conf.getString("artifact")
      val version = conf.getString("version")
      val resolvers = conf.getOptionStringList("resolvers").getOrElse(new java.util.Vector[String]())
      val module = MavenModule(group, artifact, version, resolvers.asScala.toVector)
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

      if (in == null) {
        logger.warn(s"Can not access plugin manifest: ${uri.toString}")
        ref
      } else {
        ConfigFactory.parseReader(new InputStreamReader(in)).withFallback(ref)
      }
    } catch {
      case ex: Throwable =>
        logger.warn(s"Invalid plugin manifest location: '$uriPath' {}", ex.getMessage)
        ref
    }
  }

  private[plugin] def installHttp(uri: URI, rootDir: File)(implicit system: ActorSystem, mat: Materializer): Option[File] = {
    // TODO: not tested
    def writeFile(file: File)(httpResponse: HttpResponse): Future[IOResult] = {
      httpResponse.entity.dataBytes.runWith(FileIO.toPath(FileSystems.getDefault.getPath(file.getAbsolutePath)))
    }

    def responseOrFail[T](in: (Try[HttpResponse], T)): (HttpResponse, T) = in match {
      case (responseTry, context) => (responseTry.get, context)
    }

    try {
      val url = uri.toURL
      val filename = url.getFile
      if (filename.endsWith(".jar")) {
        val file = new File(rootDir, filename)

        val request = HttpRequest(uri = Uri(uri.toString))
        val source = Source.single(request, ())
        val requestResponseFlow = Http().superPool[Unit]()

        source
          .via(requestResponseFlow)
          .map(responseOrFail)
          .map(_._1)
          .runWith(Sink.foreach(writeFile(file)))
        Some(file)
      } else {
        None
      }
    } catch {
      case ex: Throwable =>
        logger.error(s"Fail to download plugin '${uri.toString}")
        None
    }
  }

  private[plugin] def installMaven(packageName: String, moduleDef: MavenModule, rootDir: File): Option[File] = {
    import sbt.librarymanagement.ivy._
    import sbt.librarymanagement.syntax._

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
    val isSnapshot = moduleDef.artifact.toUpperCase.endsWith("-SNAPSHOT")
    val exclusionRules = Array(ExclusionRule("com.thing2x", "smqd-core_2.12"), ExclusionRule("org.scala-lang", "scala-library"))

    val module =
      if (isSnapshot)
        stringToOrganization(moduleDef.group).%(moduleDef.artifact).%(moduleDef.version).changing().excludeAll(exclusionRules.toIndexedSeq: _*).force()
      else
        stringToOrganization(moduleDef.group).%(moduleDef.artifact).%(moduleDef.version).excludeAll(exclusionRules.toIndexedSeq: _*).force()

    lm.retrieve(module, scalaModuleInfo = None, fileRetrieve, ivyLogger) match {
      case Left(w: UnresolvedWarning) =>
        val str = w.failedPaths.map(_.toString).mkString("\n", "\n", "\n")
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
