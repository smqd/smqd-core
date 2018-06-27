
import sbt.Keys._

import scala.sys.process._

val versionString = "0.2.0-SNAPSHOT"

lazy val gitBranch = "git rev-parse --abbrev-ref HEAD".!!.trim
lazy val gitCommitShort = "git rev-parse HEAD | cut -c 1-7".!!.trim
lazy val gitCommitFull = "git rev-parse HEAD".!!.trim

val versionFile       = s"echo version = $versionString" #> file("src/main/resources/smqd-core-version.conf") !
val commitVersionFile = s"echo commit-version = $gitCommitFull" #>> file("src/main/resources/smqd-core-version.conf") !

val `smqd-core` = project.in(file(".")).settings(
  organization := "t2x.smqd",
  name := "smqd-core",
  version := versionString,
  scalaVersion := Dependencies.Versions.scala,
  scalacOptions in ThisBuild ++= Seq("-feature", "-deprecation")
).settings(
  // Dependencies
  libraryDependencies ++=
    Dependencies.akka ++
      Dependencies.netty ++
      Dependencies.metrics ++
      Dependencies.crypto
).settings(
  // Publishing
  publishTo := Some(
    "bintray" at "https://api.bintray.com/maven/smqd/"+"smqd/smqd-core_2.12/;publish=1"),
  credentials += Credentials(Path.userHome / ".sbt" / "bintray_credentials"),
  publishMavenStyle := true
).settings(
  // PGP signing
  credentials += Credentials(Path.userHome / ".sbt" / "pgp_credentials"),
  pgpPublicRing := file("./travis/local.pubring.asc"),
  pgpSecretRing := file("./travis/local.secring.asc")
).settings(
  // License
  organizationName := "UANGEL",
  startYear := Some(2018),
  licenses += ("Apache-2.0", new URL("https://www.apache.org/licenses/LICENSE-2.0.txt")),
  headerMappings := headerMappings.value + (HeaderFileType.scala -> HeaderCommentStyle.cppStyleLineComment),
  headerMappings := headerMappings.value + (HeaderFileType.conf -> HeaderCommentStyle.hashLineComment)
).enablePlugins(AutomateHeaderPlugin)