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
import java.net.{InetSocketAddress, Socket, SocketAddress, UnknownHostException}
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

    var bufferSize: Int = 4096

    var autoLogin: Boolean = false

    var loginPrompt: Regex = ".*ogin:".r
    var login: String = ""

    var passwordPrompt: Regex = ".assword:".r
    var password: String = ""

    var shellPrompt: Regex = ".*$ ".r
  }

  trait ProtocolSupport {
    def connect(host: String, port: Int)
    def disconnect()
    def flush()
    def inputStream(): InputStream
    def outputStream(): OutputStream
  }

  class TelnetProtocolSupport extends ProtocolSupport {
    private val client = new CommonTelnetClient()

    override def connect(host: String, port: Int): Unit = {
      val ttopt = new TerminalTypeOptionHandler("VT100", false, false, false, false)
      val echoopt = new EchoOptionHandler(false, false, false, false)
      val gaopt = new SuppressGAOptionHandler(false, true, false, false)

      client.addOptionHandler(ttopt)
      client.addOptionHandler(echoopt)
      client.addOptionHandler(gaopt)
      client.connect(host, port)
    }

    override def disconnect(): Unit = client.disconnect()

    override def flush(): Unit = client.getOutputStream.flush()

    override def inputStream(): InputStream = client.getInputStream

    override def outputStream(): OutputStream = client.getOutputStream
  }

  class SocketProtocolSupport extends ProtocolSupport {
    private val socket = new Socket()

    override def connect(host: String, port: Int): Unit =
      socket.connect(new InetSocketAddress(host, port), 5000)

    override def disconnect(): Unit = socket.close()

    override def flush(): Unit = socket.getOutputStream.flush()

    override def inputStream(): InputStream = socket.getInputStream

    override def outputStream(): OutputStream = socket.getOutputStream
  }

  object Builder {
    def apply(): Builder = new Builder()
  }

  class Builder {
    private val config = new Config()

    private var protocol = "TELENET"

    def withSocketProtocol(): Builder = { protocol = "SOCKET"; this }
    def withTelnetProtocol(): Builder = { protocol = "TELNET"; this }

    def withHost(host: String): Builder = { config.host = host; this }
    def withPort(port: Int): Builder = { config.port = port; this }

    def withDebug(debug: Boolean): Builder = { config.debug = debug; this }

    def withAutoLogin(autoLogin: Boolean): Builder = { config.autoLogin = autoLogin; this }

    def withLogin(login: String): Builder = { config.login = login; this }
    def withLoginPrompt(loginPrompt: Regex): Builder = {config.loginPrompt = loginPrompt; this }

    def withPassword(password: String): Builder = { config.password = password; this }
    def withPasswordPrompt(passwordPrompt: Regex): Builder = {config.passwordPrompt = passwordPrompt; this }

    def withShellPrompt(prompt: Regex): Builder = { config.shellPrompt = prompt; this }

    def withBufferSize(size: Int): Builder = { config.bufferSize = size; this }

    def build(): TelnetClient = {
      if (protocol == "TELNET")
        new TelnetClient(config, new TelnetProtocolSupport())
      else
        new TelnetClient(config, new SocketProtocolSupport())
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

class TelnetClient(config: TelnetClient.Config, client: ProtocolSupport) extends StrictLogging {

  private val buffer = new Array[Byte](config.bufferSize)
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
                logger.trace(s"password expectingPrompt: '$prompt'")
                writeLine(config.password)

                expect(config.shellPrompt) match {
                  case Expected(prompt, _) =>
                    logger.trace(s"shell expectingPrompt: '$prompt'")
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
      client.connect(config.host, config.port)
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
      client.disconnect()
    }
    catch {
      case _: IOException =>  // ignore - socket already closed
      // callback(NetworkFailure(e))
    }
    Disconnected
  }

  def flush(): Unit = {
    client.flush()
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
    client.outputStream.write(s.getBytes(StandardCharsets.UTF_8))
  }


  private def compactBuffer0(): Unit = buffer.synchronized {
    logger.trace(s"buffer compaction before, head=$head, tail=$tail")
    if (head > 0) { // buffer compaction
      Array.copy(buffer, head, buffer, 0, tail - head)
      tail = tail - head
      head = 0
    }
    logger.trace(s"buffer compaction after, head=$head, tail=$tail")
  }

  private def read0(): Int = buffer.synchronized {
    val in = client.inputStream
    var readCount = 0

    if (tail > head) {
      head += 1
      buffer(head - 1)
    }
    else {
      readCount = in.read(buffer, tail, buffer.length - tail)
      if (readCount <= 0) {
        return -1
      }
      else {
        tail += readCount

        head += 1
        logger.trace(s"buffer read, head=$head, tail=$tail, readCount=$readCount")
        buffer(head - 1)
      }
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
          logger.trace(s"RECV buffer(head: $head, tail: $tail)\n${ByteBufUtil.prettyHexDump(bb)}")
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
  def expect(expectingPrompt: Regex, expectingContent: Option[Regex] = None)(implicit ec: ExecutionContext, timeout: Duration): ExpectResult = {
    val f = Future { expect0(expectingPrompt, expectingContent) }
    try {
      Await.result(f, timeout)
    }
    catch {
      case _: TimeoutException =>
        Timeout(s"Timeout when client is expecting for '${expectingPrompt.toString}'${if (expectingContent.isDefined) s" containing '${expectingContent.toString}'" else ""}, the remainings in the buffer is '${expectingBuffer.toString}'")
    }
  }

  private val textBuffer = new StringBuffer()
  private val expectingBuffer = new StringBuffer()

  private def expect0(prompt: Regex, content: Option[Regex] = None): ExpectResult = buffer.synchronized {
    var result: ExpectResult = null
    var loop = true

    textBuffer.setLength(0)
    expectingBuffer.setLength(0)

    val debugBuffer = if (config.debug) Unpooled.buffer(1024) else null

    def appendDebugBuffer(c: Int): Unit = if (config.debug) debugBuffer.writeByte(c)

    def dumpDebugBuffer(): Unit = {
      if (config.debug){
        logger.trace(s"RECV buffer(head: $head, tail: $tail)\n${ByteBufUtil.prettyHexDump(debugBuffer)}")
        debugBuffer.clear()
      }
    }

    do {
      val str = expectingBuffer.toString
      prompt.findFirstIn(str) match {
        case Some(_) if content.isEmpty => // find the prompt
          result = Expected(str, textBuffer.toString)
          loop = false
          dumpDebugBuffer()
        case Some(_) if content.isDefined => // find the prompt, then check if the expected content is contained
          val text = textBuffer.toString
          content.get.findFirstIn(text) match {
            case Some(_) =>
              result = Expected(str, text)
              loop = false
            case _ => // text doesn't contain the expected content
              // abandon the text after leaving log
              logger.trace(s"Content not found '${content.get.toString}' in $text")
              // reset the buffers
              textBuffer.setLength(0)
              expectingBuffer.setLength(0)
              compactBuffer0()
          }
          dumpDebugBuffer()
        case _ =>
          if (str.endsWith("\n")) {
            expectingBuffer.setLength(0)
            val trimmed = if (str.endsWith("\r\n")) str.substring(0, str.length-2) else str.substring(0, str.length-1)
            textBuffer.append(trimmed).append("\n")
            dumpDebugBuffer()
          }
          else {
            val code = read0()
            expectingBuffer.append(code.toChar)
            appendDebugBuffer(code)
          }
      }
    }
    while(loop)
    compactBuffer0()

    result
  }

  def exec(cmd: String, expectingPrompt: Regex = config.shellPrompt, expectingContent: Option[Regex] = None)(implicit ec: ExecutionContext, timeout: Duration): ExecResult = {
    writeLine(cmd)
    expect(expectingPrompt, expectingContent) match {
      case Expected(_, text) => ExecSuccess(text)
      case t: Timeout => t
      case m => ExecFailure(m.toString)
    }
  }
}
