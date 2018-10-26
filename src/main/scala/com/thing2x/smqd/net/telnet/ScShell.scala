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

import java.io.{EOFException, File, IOException, Reader}
import java.net.SocketException

import com.thing2x.smqd.Smqd
import com.typesafe.scalalogging.StrictLogging
import net.wimpi.telnetd.io.BasicTerminalIO
import net.wimpi.telnetd.net.{Connection, ConnectionEvent}
import net.wimpi.telnetd.shell.Shell

import scala.collection.JavaConverters._

object ScShell {
  private var delegate: Option[ScShellDelegate] = None

  def setDelegate(delegate: ScShellDelegate): Unit = {
    this.delegate = Option(delegate)
  }

  def createShell = new ScShell

}

class ScShell extends Shell with StrictLogging {
  private var _connection: Connection = _
  private var _terminal: ScTerm = _
  private var isAlive: Boolean = true
  private var _commandProvider: ScCommandProvider = _
  private var _scripter: ScEngine = _
  private var _history: Seq[String] = Nil
  private var _historyOffset: Int = -1

  private val prompt = "scsh"

  def username: String = {
    val env = _connection.getConnectionData.getEnvironment.asInstanceOf[java.util.HashMap[String, String]]
    env.get("username")
  }

  def smqd: Smqd = TelnetService.smqdInstance

  override def run(con: Connection): Unit = {
    try {
      _connection = con

      // don't forget to register listener
      _connection.addConnectionListener(this)

      _terminal = new ScTerm(_connection.getTerminalIO)

      _terminal.printStream.print("Loading shell......")

      _scripter = ScEngine()
      _scripter.setWriter(_terminal.writer)
      _scripter.setErrorWriter(_terminal.writer)
      _scripter.setReader(_terminal.reader)

      _scripter.bind("$shell", this)

      _terminal.clear()

      _commandProvider = ScDefaultCommandProvider(rootDirectory, "/", TelnetService.paths)

      // We just read any key
      _terminal.printStream.println("Shell ready!")
      _terminal.printStream.println()

      val ef = new BshCommandField(_connection.getTerminalIO, "cmd", 1024, 100)
      var cmd = ""

      ScShell.delegate.foreach(_.beforeShellStart(this))

      do {
        _terminal.printStream.print(s"$prompt ${_commandProvider.workingDirectory} > ")
        _terminal.printStream.flush()
        ef.run()
        _terminal.printStream.println()
        _terminal.printStream.flush()
        cmd = ef.getValue
        ef.clear()

        _history = ef.getHistory.asScala
        _historyOffset = ef.getHistoryOffset

        val args = cmd.split("\\s+").toSeq

        if (args.nonEmpty && args.head.length > 0) {

          val finalArgs = _commandProvider.alias(args.head) match {
            case Some(newCmd) =>  newCmd +: args.drop(1)
            case None => args
          }

          _commandProvider.command(finalArgs) match {
            case Some(command) =>
              try command.exe(finalArgs, this)
              // execute defferred lamda
              finally evalDeferred()
            case None =>
              _terminal.printStream.println(s"Command not found: ${args.head}")
          }
        }
      }
      while ( _connection.isActive && isAlive )
    }
    catch {
      case _: EOFException =>
        logger.info("Client send quit signal.")
      case _: SocketException =>
        logger.info("Client close the connection.")
      case ex: Exception =>
        logger.error("run()", ex)
    }
    finally {
      _connection.removeConnectionListener(this)
      ScShell.delegate.foreach(_.afterShellStop(this))
      onConnectionClose()
    }
  }

  def exit(code: Int): Unit = {
    isAlive = false
  }

  class Defer[T](ev: => T) {
    def eval: T = ev
  }

  private var deferred: Seq[Defer[Unit]] = Nil

  def defer(lamda: => Unit): Unit = {
    deferred = deferred :+ new Defer(lamda)
  }

  private def evalDeferred(): Unit = {
    deferred.foreach{ defer =>
      Console.withOut(_terminal.outputStream) {
        Console.withErr(_terminal.outputStream) {
          try{
            defer.eval
          }
          catch {
            case e: Throwable =>
              logger.debug("deferred blocks", e)
          }
        }
      }
    }
    deferred = Nil
  }

  private def classLoader: ClassLoader = {
    if (Thread.currentThread.getContextClassLoader == null)
      getClass.getClassLoader
    else
      Thread.currentThread.getContextClassLoader
  }

  private def rootDirectory: File = {
    new File(getClass.getProtectionDomain.getCodeSource.getLocation.getPath).getParentFile
  }

  private def onConnectionClose(): Unit = {

    if (_connection.isActive) {
      _terminal.printStream.println("\nLog out.\n")
      _terminal.printStream.flush()
    }
  }

  override def connectionTimedOut(ce: ConnectionEvent): Unit = {
    try
      onConnectionClose()
    catch {
      case ex: Exception =>
        logger.error("clear env failure", ex)
    }
    try
      _connection.close()
    catch {
      case ex: Exception =>
        logger.error("close connection failure", ex)
    }
  }

  override def connectionIdle(ce: ConnectionEvent): Unit = {
    try {
      _terminal.printStream.println("\nCONNECTION_IDLE\n")
    } catch {
      case e: IOException =>
        logger.error("connectionIdle()", e)
    }
  }

  override def connectionLogoutRequest(ce: ConnectionEvent): Unit = {
    try {
      _terminal.printStream.println("\nCONNECTION_LOGOUTREQUEST\n")
    } catch {
      case ex: Exception =>
        logger.error("connectionLogoutRequest()", ex)
    }
  }

  override def connectionSentBreak(ce: ConnectionEvent): Unit = {
    try {
      _terminal.printStream.println("\nCONNECTION_BREAK\n")
    } catch {
      case ex: Exception =>
        logger.error("connectionSentBreak()", ex)
    }
  }

  /**
    * @param pattern wild expression for scsh file; *.scsh , abc*.scsh, abc?.scsh
    */
  def findAllBshFiles(pattern: String): Array[File] = _commandProvider.findAllScriptFiles(pattern)

  def loadBshFile(file: String): Reader = _commandProvider.loadScriptFile(file).orNull

  def canAccess(relativePath: String, isDirectory: Boolean): Boolean = _commandProvider.canAccess(relativePath, isDirectory)

  def getRealPath(relativePath: String): String = _commandProvider.getRealPath(relativePath)

  def getRelativePath(realPath: String): String = _commandProvider.getRelativePath(realPath).orNull

  def getWorkingDirectory: String = _commandProvider.workingDirectory
  def setWorkingDirectory(relativePath: String): Unit = _commandProvider.workingDirectory = relativePath

  def termIO: BasicTerminalIO = _connection.getTerminalIO

  def inputStream: java.io.InputStream = _terminal.inputStream
  def outputStream: java.io.OutputStream = _terminal.outputStream
  def printStream: java.io.PrintStream = _terminal.printStream
  def writer: java.io.Writer = _terminal.writer
  def reader: java.io.Reader = _terminal.reader

  def history: Seq[String] = _history
  //  def history_=(h: Seq[String]): Unit = _history = h

  def historyOffset: Int = -1
  //  def historyOffset_=(offset: Int): Unit = _historyOffset = offset

  def interpreter: ScEngine = _scripter
  //  def interpreter_=(interpreter: Interpreter): Unit = _interpreter = interpreter

  def commandProvider: ScCommandProvider = _commandProvider
  //  def commandProvider_=(provider: BshCommandProvider): Unit = _commandProvider = provider

  def connection: Connection = _connection
}
