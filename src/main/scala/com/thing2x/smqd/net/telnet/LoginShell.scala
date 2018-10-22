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

import java.io.{EOFException, IOException}
import java.net.InetAddress

import com.typesafe.scalalogging.StrictLogging
import net.wimpi.telnetd.io.BasicTerminalIO
import net.wimpi.telnetd.io.toolkit.Editfield
import net.wimpi.telnetd.net.{Connection, ConnectionEvent}
import net.wimpi.telnetd.shell.Shell

// 10/15/18 - Created by Kwon, Yeong Eon

object LoginShell {

  trait Delegate {
    def login(shell: LoginShell, user: String, password: String): Boolean = true
    def allow(shell: LoginShell, remoteAddress: InetAddress): Boolean = {
      //val address = remoteAddress.getHostName
      //StringUtil.compareCaseWildExp(address, pattern) == 0
      true
    }
  }

  def setDelegate(delegate: LoginShell.Delegate): Unit = {
    this.delegate = Option(delegate)
  }

  private var delegate: Option[LoginShell.Delegate] = None

  def createShell: Shell = new LoginShell(delegate)
}

/**
  *
  */
class LoginShell(delegate: Option[LoginShell.Delegate]) extends Shell with StrictLogging {

  private var term: BasicTerminalIO = _
  private var connection: Connection = _

  override def run(conn: Connection): Unit = {
    try {
      val address = conn.getConnectionData.getInetAddress
      if (!(delegate.isDefined && delegate.get.allow(this, address))) {
        conn.close()
        return
      }

      connection = conn
      term = connection.getTerminalIO

      // dont forget to register listener
      connection.addConnectionListener(this)

      // clear the screen and start from zero
      term.eraseScreen()
      term.homeCursor()

      // allow retry 3 times
      val logined = 1 to 3 find { _ =>
        term.write("Login: ")
        var ef = new Editfield(term, "login", 50)
        ef.run()
        val username = ef.getValue

        term.write("\r\nPassword: ")
        ef = new Editfield(term, "passwd", 50)
        ef.setPasswordField(true)
        ef.run()
        val password = ef.getValue
        term.flush()

        if ("dummy" == username && "dummy" == password) {
          connection.setNextShell("dummy")
          true
        }
        else if (delegate.isEmpty && "admin" == username && "password" == password) {
          connection.setNextShell("bsh")
          true
        }
        else if (delegate.isDefined && delegate.get.login(this, username, password)) {
          connection.setNextShell("bsh")
          true
        }
        else {
          term.write("\r\n")
          false
        }
      }

      connection.removeConnectionListener(this)

      if (logined.isEmpty) {
        term.write("\r\nLogin incorrect\r\n\r\n")

        term.homeCursor()
        term.eraseScreen()
        term.write("Goodbye!.\r\n\r\n")
        term.flush()

        connection.close()
      }
    } catch {
      case _: EOFException =>
        logger.info("Client send quit signal.")
      case ex: Exception =>
        logger.error("LoginShell", ex.getMessage)
    }
  }

  override def connectionIdle(connectionEvent: ConnectionEvent): Unit = try {
    term.write("CONNECTION_IDLE")
    term.flush()
  } catch {
    case e: IOException =>
      logger.error("connectionIdle()", e)
  }

  override def connectionTimedOut(connectionEvent: ConnectionEvent): Unit = try {
    term.write("CONNECTION_TIMEDOUT")
    term.flush()
    // close connection
    connection.close()
  } catch {
    case ex: Exception =>
      logger.error("connectionTimedOut()", ex)
  }


  override def connectionLogoutRequest(connectionEvent: ConnectionEvent): Unit = try {
    term.write("CONNECTION_LOGOUTREQUEST")
    term.flush()
  } catch {
    case ex: Exception =>
      logger.error("connectionLogoutRequest()", ex)
  }

  override def connectionSentBreak(connectionEvent: ConnectionEvent): Unit = try {
    term.write("CONNECTION_BREAK")
    term.flush()
  } catch {
    case ex: Exception =>
      logger.error("connectionSentBreak()", ex)
  }
}
