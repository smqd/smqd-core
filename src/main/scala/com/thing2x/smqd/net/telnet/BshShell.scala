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

import java.io.{EOFException, File, IOException}
import java.net.SocketException

import bsh.Interpreter
import com.thing2x.smqd.util.StringUtil
import com.typesafe.scalalogging.StrictLogging
import net.wimpi.telnetd.net.{Connection, ConnectionEvent}
import net.wimpi.telnetd.shell.Shell

import scala.collection.JavaConverters._

trait BshShellDelegate {
  def prepare(shell: BshShell, bshInterpreter: Interpreter): Unit
  def scriptPaths(shell: BshShell): Seq[String]
}


object BshShell {
  private var delegate: BshShellDelegate = null

  def setDelegate(delegate: BshShellDelegate): Unit = {
    this.delegate = delegate
  }

  def createShell = new BshShell
}

class BshShell extends Shell with StrictLogging {
  private var _connection: Connection = _
  private var _terminal: BshTerm = _
  private var isAlive: Boolean = true
  private var _commandProvider: BshCommandProvider = _
  private var _interpreter: Interpreter = _
  private var _history: Seq[String] = Nil
  private var _historyOffset: Int = -1

  private val prompt = "bsh"

  override def run(con: Connection): Unit = {
    try {
      _connection = con

      // don't forget to register listener
      _connection.addConnectionListener(this)

      _terminal = new BshTermTelnet(_connection.getTerminalIO)

      _interpreter = new Interpreter
      _interpreter.setClassLoader(this.classLoader)
      _interpreter.set("CONNECTION", _connection)
      _interpreter.set("TERM", _terminal)
      _interpreter.set("SHELL", this)
      _interpreter.set("ENV", new java.util.Hashtable())

      _commandProvider = BshDefaultCommandProvider(rootDirectory, "/", this.scriptPaths)

      // We just read any key
      _terminal.write("Bean Shell ready!\r\n")
      _terminal.flush()

      val ef = new BshCommandField(_connection.getTerminalIO, "cmd", 1024, 100)
      var cmd = ""

      do {
        _terminal.write(s"$prompt ${_commandProvider.workingDirectory} > ")
        _terminal.flush()
        ef.run()
        _terminal.write("\r\n")
        _terminal.flush()
        cmd = ef.getValue
        ef.clear()

        _history = ef.getHistory.asScala
        _historyOffset = ef.getHistoryOffset

        val args = StringUtil.split(cmd, " \t\r\n").toSeq

        if (args.nonEmpty && args.head.length > 0) {

          val finalArgs = _commandProvider.alias(args.head) match {
            case Some(newCmd) =>  newCmd +: args.drop(1)
            case None => args
          }

          _commandProvider.command(finalArgs) match {
            case Some(command) =>
              command.exe(finalArgs, this)
            case None =>
              _terminal.println(s"Command not found: ${args.head}")
          }
        }
      }
      while ( _connection.isActive && isAlive )

      _connection.removeConnectionListener(this)

      onConnectionClose()
    }
    catch {
      case _: EOFException =>
        logger.info("Client send quit signal.")
      case _: SocketException =>
        logger.info("Client close the connection.")
      case ex: Exception =>
        logger.error("run()", ex)
    }
  }

  def exit(code: Int): Unit = {
    isAlive = false
  }

  private def classLoader: ClassLoader = {
    if (Thread.currentThread.getContextClassLoader == null)
      getClass.getClassLoader
    else
      Thread.currentThread.getContextClassLoader
  }

  private def scriptPaths: Seq[String] = {
    if (BshShell.delegate != null) {
      BshShell.delegate.prepare(this, _interpreter)
      BshShell.delegate.scriptPaths(this)
    } else {
      Nil
    }
  }

  private def rootDirectory: File = {
    new File(getClass.getProtectionDomain.getCodeSource.getLocation.getPath).getParentFile
  }

  private def onConnectionClose(): Unit = {
    _interpreter.get("ENV").asInstanceOf[java.util.Hashtable[String, AnyRef]].clear()

    if (_connection.isActive) {
      _terminal.write("\r\nLog out.\r\n\r\n")
      _terminal.flush()
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
      _terminal.write("\r\nCONNECTION_IDLE\r\n")
      _terminal.flush()
    } catch {
      case e: IOException =>
        logger.error("connectionIdle()", e)
    }
  }

  override def connectionLogoutRequest(ce: ConnectionEvent): Unit = {
    try {
      _terminal.write("\r\nCONNECTION_LOGOUTREQUEST\r\n")
      _terminal.flush()
    } catch {
      case ex: Exception =>
        logger.error("connectionLogoutRequest()", ex)
    }
  }

  override def connectionSentBreak(ce: ConnectionEvent): Unit = {
    try {
      _terminal.write("\r\nCONNECTION_BREAK\r\n")
      _terminal.flush()
    } catch {
      case ex: Exception =>
        logger.error("connectionSentBreak()", ex)
    }
  }

  /**
    * @param pattern wild expression for bsh file; *.bsh , abc*.bsh, abc?.bsh
    */
  def loadAllBshFiles(pattern: String): Array[File] = _commandProvider.findAllBshFiles(pattern)

  def canAccess(relPath: String, isDirectory: Boolean): Boolean = _commandProvider.canAccess(relPath, isDirectory)

  def getRealPath(relPath: String): String = _commandProvider.getRealPath(relPath)

  def getRelativePath(realPath: String): String = _commandProvider.getRelativePath(realPath).orNull

  def getWorkingDirectory: String = _commandProvider.workingDirectory
  def setWorkingDirectory(path: String): Unit = _commandProvider.workingDirectory = path

  def terminal: BshTerm = _terminal

  def history: Seq[String] = _history
//  def history_=(h: Seq[String]): Unit = _history = h

  def historyOffset: Int = -1
//  def historyOffset_=(offset: Int): Unit = _historyOffset = offset

  def interpreter: Interpreter = _interpreter
//  def interpreter_=(interpreter: Interpreter): Unit = _interpreter = interpreter

  def commandProvider: BshCommandProvider = _commandProvider
//  def commandProvider_=(provider: BshCommandProvider): Unit = _commandProvider = provider

  def connection: Connection = _connection
}
