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

package com.thing2x.smqd.net.telnet.test


import akka.actor.ActorSystem
import akka.http.scaladsl.testkit.ScalatestRouteTest
import akka.testkit.TestKit
import com.thing2x.smqd.net.telnet.TelnetClient
import com.thing2x.smqd.net.telnet.TelnetClient._
import com.thing2x.smqd.{Smqd, SmqdBuilder}
import com.typesafe.config.{Config, ConfigFactory}
import com.typesafe.scalalogging.StrictLogging
import org.scalatest.{BeforeAndAfterAll, FlatSpec}

import scala.concurrent.Promise
import scala.concurrent.duration._

class TelnetClientTest extends FlatSpec
  with BeforeAndAfterAll
  with ScalatestRouteTest
  with StrictLogging {

  // configure smqd's telnet server
  val config: Config = ConfigFactory.parseString(
    """
      |smqd {
      |  services=["core-telnetd"]
      |}
    """.stripMargin).withFallback(ConfigFactory.parseResources("smqd-ref.conf"))

  val telnetTestConf = ConfigFactory.parseString(
    """
      |host=127.0.0.1
      |port=6623
      |user=admin
      |password=password
    """.stripMargin)

  private val telnetConf = ConfigFactory.parseResources("telnet-test-dev.conf").withFallback(telnetTestConf)

  private val login = telnetConf.getString("user")
  private val passwd = telnetConf.getString("password")

  private val bshPrompt = ".*bsh.*> ".r  // linuxPrompt = "^.*[$] ".r

  private var smqdInstance: Smqd = _
  private val shutdownPromise = Promise[Boolean]

  override def createActorSystem(): ActorSystem = ActorSystem(actorSystemNameFrom(getClass), config)

  override def beforeAll(): Unit = {
    smqdInstance = new SmqdBuilder(config).setActorSystem(system).build()
    smqdInstance.start()
  }

  override def afterAll(): Unit = {
    shutdownPromise.future.onComplete { _ =>
      smqdInstance.stop()
      TestKit.shutdownActorSystem(system)
    }
  }

  "TelnetClient" should "work in step by step procedure" in {
    implicit val timeout: Duration = 15.seconds

    val client = TelnetClient.Builder()
      .withHost(telnetConf.getString("host"))
      .withPort(telnetConf.getInt("port"))
      .withDebug(false)
      .withAutoLogin(false)
      .withSocketProtocol()
      .build()

    val con = client.connect()
    assert(con == Connected)

    logger.debug("synchronous expect Login")
    client.expect(".*ogin:".r) match {
      case Expected(prompt, text) =>
        logger.info(s"==============> $prompt")
        client.writeLine(login)
      case _ =>
        fail()
    }

    client.expect(".*assword:".r) match {
      case Expected(prompt, text) =>
        logger.info(s"==============> $prompt")
        client.writeLine(passwd)
      case _ =>
        fail()
    }

    client.expect(bshPrompt) match {
      case Expected(prompt, text) =>
        logger.info(s"==============> $prompt")
        client.writeLine("sysinfo")
      case m =>
        logger.error(m.toString)
        fail()
    }

    client.expect(bshPrompt) match {
      case Expected(_, text) =>
        logger.info(s"==============> $text")
      case _ =>
        fail()
    }

    client.writeLine("")
    client.expect(bshPrompt) match {
      case Expected(prompt, text) =>
        client.writeLine("exit")
      //        logger.debug("disconnect")
      //        client.disconnect()
      case _ =>
        fail()
    }
  }

  it should "work with auto login" in {
    implicit val timeout: Duration = 3.seconds

    val client = TelnetClient.Builder()
      .withHost(telnetConf.getString("host"))
      .withPort(telnetConf.getInt("port"))
      .withDebug(false)
      .withAutoLogin(true)
      .withLogin(login)
      .withPassword(passwd)
      .withLoginPrompt(".*ogin:".r)
      .withPasswordPrompt(".*assword:".r)
      .withShellPrompt(bshPrompt)
      .build()

    val con = client.connect()
    assert(con == Connected)
    logger.info("LOGIN success")

    client.exec("systime") match {
      case ExecSuccess(text) =>
        logger.info(s"EXEC systime : '${text.trim}'")
      case _ =>
        fail()
    }

    client.exec("gc") match {
      case ExecSuccess(text) =>
        logger.info(s"EXEC gc : ${text.trim}")
      case _ =>
        fail()
    }

    client.exec("") match {
      case ExecSuccess(text) =>
        client.writeLine("exit")
      case _ =>
        fail()
    }
  }

  it should "shutdown" in {
    shutdownPromise.success(true)
  }
}

