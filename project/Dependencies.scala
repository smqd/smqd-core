import sbt._

/**
  * 2018. 5. 29. - Created by Kwon, Yeong Eon
  */
object Dependencies {

  object Versions {
    val scala = "2.12.7"
    val akka = "2.5.17"
    val akkaHttp = "10.1.5"
    val netty = "4.1.30.Final"
    val alpakka = "0.20"
  }

  val akka: Seq[ModuleID] = Seq(
    //////////////////////////////////
    // akka actor
    "com.typesafe.akka" %% "akka-actor" % Versions.akka,
    //////////////////////////////////
    // akka cluster and distributed data
    "com.typesafe.akka" %% "akka-cluster" % Versions.akka,
    "com.typesafe.akka" %% "akka-distributed-data" % Versions.akka,
    //////////////////////////////////
    // akka http
    "com.typesafe.akka" %% "akka-http"   % Versions.akkaHttp,
    //////////////////////////////////
    // akka Stream
    "com.typesafe.akka" %% "akka-stream" % Versions.akka,
    //////////////////////////////////
    // alpakka Stream
    "com.lightbend.akka" %% "akka-stream-alpakka-mqtt" % Versions.alpakka,
    //////////////////////////////////
    // plugin package management
    "org.scala-sbt" %% "librarymanagement-ivy" % "1.2.0-M3",
    //////////////////////////////////
    // Logging
    "com.typesafe.akka" %% "akka-slf4j" % Versions.akka force(),
    "ch.qos.logback" % "logback-classic" % "1.2.3", // 01-Apr-2017 updated
    "com.typesafe.scala-logging" %% "scala-logging" % "3.9.0",
    //////////////////////////////////
    // Test
    "com.typesafe.akka" %% "akka-testkit" % Versions.akka % Test,
    "com.typesafe.akka" %% "akka-stream-testkit" % Versions.akka % Test,
    "com.typesafe.akka" %% "akka-http-testkit" % Versions.akkaHttp % Test,
    "org.scalatest" %% "scalatest" % "3.0.5" % Test,
    "org.eclipse.paho" % "org.eclipse.paho.client.mqttv3" % "1.0.2" % Test
  )

  val circe: Seq[ModuleID] = Seq(
    "io.circe" %% "circe-core",
    "io.circe" %% "circe-generic",
    "io.circe" %% "circe-parser"
  ).map( _ % "0.9.3")

  val netty: Seq[ModuleID] = Seq(
    "io.netty" % "netty-buffer" % Versions.netty,
    "io.netty" % "netty-codec-mqtt" % Versions.netty,
    "io.netty" % "netty-codec-http" % Versions.netty,
    "io.netty" % "netty-handler" % Versions.netty,
    "io.netty" % "netty-transport-native-epoll" % Versions.netty classifier "linux-x86_64",  // for Linux
    "io.netty" % "netty-transport-native-kqueue" % Versions.netty classifier "osx-x86_64",  // for macOS
    "io.netty" % "netty-resolver-dns" % Versions.netty
  )

  val etcd: Seq[ModuleID] = Seq(
    "org.mousio" % "etcd4j" % "2.16.0" excludeAll ExclusionRule(organization = "io.netty") force()
  )

  val telnetd: Seq[ModuleID] = Seq(
    "net.wimpi" % "telnetd-x" % "2.1.1",
    "commons-net" % "commons-net" % "3.6",
//    "org.scala-lang" % "scala-compiler" % Versions.scala,
    "org.slf4j" % "log4j-over-slf4j" % "1.7.7"
  )

  val crypto: Seq[ModuleID] = Seq(
    "org.bouncycastle" % "bcprov-jdk15on" % "1.60"
  )

  val metricsVersion = "4.1.0-rc2" // updated 04-May-2018
  val metrics: Seq[ModuleID] = Seq(
    "io.dropwizard.metrics" % "metrics-core" % metricsVersion,
    "io.dropwizard.metrics" % "metrics-logback" % metricsVersion,
    "io.dropwizard.metrics" % "metrics-jvm" % metricsVersion
  )

  val jwt: Seq[ModuleID] = Seq(
    "com.pauldijou" %% "jwt-core" % "0.16.0"
  )
}
