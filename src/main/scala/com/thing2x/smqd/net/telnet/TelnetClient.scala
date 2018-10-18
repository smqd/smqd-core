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

package com.thing2x.smqd.net.telnet


import java.io._
import java.net.UnknownHostException
import java.nio.charset.StandardCharsets
import java.util.concurrent.TimeoutException

import com.thing2x.smqd.net.telnet.TelnetClient._
import com.typesafe.scalalogging.StrictLogging
import io.netty.buffer.{ByteBufUtil, Unpooled}
import org.apache.commons.net.telnet.{EchoOptionHandler, SuppressGAOptionHandler, TerminalTypeOptionHandler, TelnetClient => CommonTelnetClient}

import scala.concurrent.duration._
import scala.concurrent.{Await, ExecutionContext, Future}
import scala.util.matching.Regex

object TelnetClient {

  class Config {
    var host: String = "127.0.0.1"
    var port: Int = 23
    var debug: Boolean = false

    var autoLogin: Boolean = false

    var loginPrompt: Regex = ".*ogin:".r
    var login: String = ""

    var passwordPrompt: Regex = ".assword:".r
    var password: String = ""

    var shellPrompt: Regex = ".*$ ".r
  }

  object Builder {
    def apply(): Builder = new Builder()
  }

  class Builder {
    private val config = new Config()

    def withHost(host: String): Builder = { config.host = host; this }
    def withPort(port: Int): Builder = { config.port = port; this }

    def withDebug(debug: Boolean): Builder = { config.debug = debug; this }

    def withAutoLogin(autoLogin: Boolean): Builder = { config.autoLogin = autoLogin; this }

    def withLogin(login: String): Builder = { config.login = login; this }
    def withLoginPrompt(loginPrompt: Regex): Builder = {config.loginPrompt = loginPrompt; this }

    def withPassword(password: String): Builder = { config.password = password; this }
    def withPasswordPrompt(passwordPrompt: Regex): Builder = {config.passwordPrompt = passwordPrompt; this }

    def withShellPrompt(prompt: Regex): Builder = { config.shellPrompt = prompt; this }

    def build(): TelnetClient = {
      new TelnetClient(config)
    }
  }

  sealed trait Result

  sealed trait ConnectResult extends Result
  object Connected extends ConnectResult
  case class UnknownHost(host: String) extends ConnectResult
  case class LoginFailure(reason: String) extends ConnectResult
  case class SecurityFailure(reason: SecurityException) extends ConnectResult
  case class NetworkFailure(reason: IOException) extends ConnectResult

  sealed trait DisconnectResult extends Result
  object Disconnected extends DisconnectResult

  sealed trait ExpectResult
  case class Expected(prompt: String, text: String) extends ExpectResult

  sealed trait ExecResult extends Result
  case class ExecSuccess(text: String) extends ExecResult
  case class ExecFailure(text: String) extends ExecResult

  case class Timeout(message: String) extends ConnectResult with DisconnectResult with ExpectResult with ExecResult
}

class TelnetClient(config: TelnetClient.Config) extends StrictLogging {

  private var client: Option[CommonTelnetClient] = None

  private val buffer = new Array[Byte](4096)
  private var head = 0
  private var tail = 0

  /**
    * connect to remote host
    */
  def connect()(implicit ec: ExecutionContext, timeout: Duration): ConnectResult = {
    val f = Future {

      if (config.autoLogin) {
        connect0()
        expect(config.loginPrompt) match {
          case Expected(prompt, _) =>
            writeLine(config.login)

            expect(config.passwordPrompt) match {
              case Expected(prompt, _) =>
                logger.trace(s"password prompt: '$prompt'")
                writeLine(config.password)

                expect(config.shellPrompt) match {
                  case Expected(prompt, _) =>
                    logger.trace(s"shell prompt: '$prompt'")
                    Connected
                  case m =>
                    LoginFailure(m.toString)
                }
              case m =>
                LoginFailure(m.toString)
            }
          case m =>
            LoginFailure(m.toString)
        }
      }
      else {
        connect0()
      }
    }

    try {
      Await.result(f, timeout)
    }
    catch {
      case _: TimeoutException =>
        Timeout(s"Timeout during login, the remainings in the buffer is '${expectingBuffer.toString}'")
    }

  }

  private def connect0(): ConnectResult = {
    var result: ConnectResult = null

    try {
      val cli = new CommonTelnetClient()

      val ttopt = new TerminalTypeOptionHandler("VT100", false, false, false, false)
      val echoopt = new EchoOptionHandler(false, false, false, false)
      val gaopt = new SuppressGAOptionHandler(false, true, false, false)

      cli.addOptionHandler(ttopt)
      cli.addOptionHandler(echoopt)
      cli.addOptionHandler(gaopt)

      cli.connect(config.host, config.port)

      client = Option(cli)

      result = Connected
    }
    catch {
      case _: UnknownHostException =>
        result = UnknownHost(config.host)
      case e: SecurityException =>
        result = SecurityFailure(e)
      case e: IOException =>
        result = NetworkFailure(e)
    }

    result
  }

  /**
    * disconnect
    */
  def disconnect()(implicit ec: ExecutionContext, timeout: Duration): DisconnectResult = {
    val f = Future {
      disconnect0()
    }
    Await.result(f, timeout)
  }

  private def disconnect0(): DisconnectResult = {
    try {
      client.foreach { cli =>
        cli.disconnect()
      }
    }
    catch {
      case _: IOException =>  // ignore - socket already closed
      // callback(NetworkFailure(e))
    }
    Disconnected
  }

  def flush(): Unit = {
    client.foreach(_.getOutputStream.flush())
  }

  /**
    * write a line
    */
  def writeLine(s: String): Unit = {
    write(s"$s\r\n")
    flush()
  }

  def write(s: String): Unit = {
    if (config.debug) {
      logger.trace("SEND: {}", s.trim)
    }
    client.foreach(_.getOutputStream.write(s.getBytes(StandardCharsets.UTF_8)))
  }


  private def compactBuffer0(): Unit = {
    if (head > 0) { // buffer compaction
      Array.copy(buffer, head, buffer, 0, tail - head)
      tail = tail - head
      head = 0
    }
  }

  private def read0(): Int = buffer.synchronized {
    val in = client.get.getInputStream

    if (tail > head) {
      val ch = buffer(head)
      head += 1
      ch
    }
    else {
      val cnt = in.read(buffer, tail, buffer.length - tail)
      if (cnt == -1) {
        return -1
      }
      tail += cnt

      val ch = buffer(head)
      head += 1
      ch
    }
  }

  /**
    * read a line
    */
  def readLine()(implicit ec: ExecutionContext, timeout: Duration): String = {
    val f = Future { readLine0() }
    Await.result(f, timeout)
  }

  private def readLine0(): String = buffer.synchronized {
    var loop = true
    var rt: String = null
    var prev: Int = 0

    val readLineBuffer = new StringBuffer()

    val bb = if (config.debug) Unpooled.buffer(1024) else null

    do {
      val ch = read0()
      if (ch == '\n') {
        if (prev == '\r')
          readLineBuffer.deleteCharAt(readLineBuffer.length - 1)

        rt = readLineBuffer.toString
        loop = false

        if (config.debug) {
          bb.writeByte(ch)
          logger.trace("RECV:\n{}", ByteBufUtil.prettyHexDump(bb))
        }
      }
      else if (ch == -1) {
        if (readLineBuffer.length > 0) {
          rt = readLineBuffer.toString
        }
        loop = false
      }
      else {
        if (config.debug) {
          bb.writeByte(ch)
        }
        readLineBuffer.append(ch.toChar)
        prev = ch
      }
    }
    while(loop)

    compactBuffer0()

    rt
  }

  /**
    * expect for a string that specified by regular expression
    */
  def expect(prompt: Regex)(implicit ec: ExecutionContext, timeout: Duration): ExpectResult = {
    val f = Future { expect0(prompt) }
    try {
      Await.result(f, timeout)
    }
    catch {
      case _: TimeoutException =>
        Timeout(s"Timeout when client is expecting for '${prompt.toString}', the remainings in the buffer is '${expectingBuffer.toString}'")
    }
  }

  private val textBuffer = new StringBuffer()
  private val expectingBuffer = new StringBuffer()

  private def expect0(prompt: Regex): ExpectResult = buffer.synchronized {
    var result: ExpectResult = null
    var loop = true

    textBuffer.setLength(0)
    expectingBuffer.setLength(0)
    val debugBuffer = if (config.debug) Unpooled.buffer(1024) else null

    do {
      val str = expectingBuffer.toString
      prompt.findFirstIn(str) match {
        case Some(_) =>
          result = Expected(str, textBuffer.toString)
          loop = false
        case _ =>
          if (str.endsWith("\n")) {
            expectingBuffer.setLength(0)
            val trimmed = if (str.endsWith("\r\n")) str.substring(0, str.length-2) else str.substring(0, str.length-1)
            if (config.debug){
              logger.trace("RECV:\n{}", ByteBufUtil.prettyHexDump(debugBuffer))
              debugBuffer.clear()
            }

            textBuffer.append(trimmed).append("\n")
          }
          else {
            val code = read0()
            if (config.debug) debugBuffer.writeByte(code)
            expectingBuffer.append(code.toChar)
          }
      }
    }
    while(loop)
    compactBuffer0()

    result
  }

  def exec(cmd: String, expectingPrompt: Regex = config.shellPrompt)(implicit ec: ExecutionContext, timeout: Duration): ExecResult = {
    writeLine(cmd)
    expect(expectingPrompt) match {
      case Expected(_, text) => ExecSuccess(text)
      case t: Timeout => t
      case m => ExecFailure(m.toString)
    }
  }
}
