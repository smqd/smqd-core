
import sbt.Keys._
import sbt.StdoutOutput

import scala.sys.process._

val versionString = "0.1.0-SNAPSHOT"

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
  pgpPublicRing := file("./travis/local.pubring.asc"),
  pgpSecretRing := file("./travis/local.secring.asc")
).settings(
  libraryDependencies ++=
    Dependencies.akka ++
      Dependencies.netty ++
      Dependencies.metrics ++
      Dependencies.crypto
)