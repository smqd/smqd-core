
import sbt.Keys._

import scala.sys.process._
import scala.language.postfixOps

val versionString = "0.4.10-SNAPSHOT"

lazy val gitBranch = "git rev-parse --abbrev-ref HEAD".!!.trim
lazy val gitCommitShort = "git rev-parse HEAD | cut -c 1-7".!!.trim
lazy val gitCommitFull = "git rev-parse HEAD".!!.trim

val versionFile       = file("src/main/resources/smqd-core-version.conf")
val logo_             = s"echo logo = SMQD" #> versionFile !
val version_          = s"echo version = $versionString" #>> versionFile !
val commitVersion_    = s"echo commit-version = $gitCommitFull" #>> versionFile !

val `smqd-core` = project.in(file(".")).settings(
  organization := "com.thing2x",
  name := "smqd-core",
  version := versionString,
  scalaVersion := Dependencies.Versions.scala,
  scalacOptions in ThisBuild ++= Seq("-feature", "-deprecation")
).settings(
  // Dependencies
  libraryDependencies ++=
    Dependencies.akka ++
      Dependencies.circe ++
      Dependencies.netty ++
      Dependencies.etcd ++
      Dependencies.metrics ++
      Dependencies.crypto ++
      Dependencies.jwt ++
      Dependencies.telnetd
).settings(
  // ScalaDoc
  scalacOptions in (Compile, doc) ++= Seq(
    "-doc-title", "SMQD",
    "-doc-version", versionString,
    "-skip-packages", "akka.pattern:org.apache"
  )
).settings(
  // Publishing
  publishTo := {
    val nexus = "https://oss.sonatype.org/"
    if (isSnapshot.value)
      Some("snapshots" at nexus + "content/repositories/snapshots")
    else
      Some("releases" at nexus + "service/local/staging/deploy/maven2")
  },
  //credentials += Credentials(Path.userHome / ".sbt" / "sonatype_credentials"),
  credentials += Credentials("Sonatype Nexus Repository Manager", "oss.sonatype.org",
    sys.env.getOrElse("SONATYPE_USER", ""), sys.env.getOrElse("SONATYPE_PASS", "")),
  homepage := Some(url("https://github.com/smqd/")),
  scmInfo := Some(ScmInfo(url("https://github.com/smqd/smqd-core"), "scm:git@github.com:smqd/smqd-core.git")),
  developers := List(
    Developer("OutOfBedlam", "Kwon, Yeong Eon", sys.env.getOrElse("SONATYPE_DEVELOPER_0", ""), url("http://www.uangel.com"))
  ),
  publishArtifact in Test := false, // Not publishing the test artifacts (default)
  publishMavenStyle := true
).settings(
  // sbt fork sqmd process to allow javaOptions parameters from command line
  fork in run := true,
  fork in Test := true,
  javaOptions in Test ++= Seq(
    "-Xmx2G"
  )
).settings(
  // PGP signing
  pgpPublicRing := file("./travis/local.pubring.asc"),
  pgpSecretRing := file("./travis/local.secring.asc"),
  pgpPassphrase := sys.env.get("PGP_PASS").map(_.toArray),
  useGpg := false
).settings(
  // License
  organizationName := "UANGEL",
  startYear := Some(2018),
  licenses += ("Apache-2.0", new URL("https://www.apache.org/licenses/LICENSE-2.0.txt")),
  headerMappings := headerMappings.value + (HeaderFileType.scala -> HeaderCommentStyle.cppStyleLineComment),
  headerMappings := headerMappings.value + (HeaderFileType.conf -> HeaderCommentStyle.hashLineComment)
).enablePlugins(AutomateHeaderPlugin)