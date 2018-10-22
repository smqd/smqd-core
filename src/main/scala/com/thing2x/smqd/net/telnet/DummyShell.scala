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


import java.io.IOException

import com.typesafe.scalalogging.StrictLogging
import net.wimpi.telnetd.io.BasicTerminalIO
import net.wimpi.telnetd.net.Connection
import net.wimpi.telnetd.net.ConnectionEvent
import net.wimpi.telnetd.shell.Shell


// 2005. 6. 21 - Created by Kwon, Yeong Eon

/**
  * This class is an example implmentation of a Shell.
  */
object DummyShell {
  def createShell = new DummyShell
}

class DummyShell extends Shell with StrictLogging {
  private var connection: Connection = _
  private var term: BasicTerminalIO = _

  /**
    * Method that runs a shell
    *
    * @param con Connection that runs the shell.
    */
  override def run(con: Connection): Unit = {
    try {
      connection = con
      term = connection.getTerminalIO
      // dont forget to register listener
      connection.addConnectionListener(this)
      // clear the screen and start from zero
      term.eraseScreen()
      term.homeCursor()
      term.write("Your terminal type is " + connection.getConnectionData.getNegotiatedTerminalType)
      term.write("\r\n")
      term.flush()

      term.setSignalling(true)

      var loop = true
      do {
        val c = term.read
        term.write("[" + Integer.toString(c) + "]\r\n")
        if (c == 'q')
          loop = false
      }
      while(loop)

      term.write("Goodbye!.\r\n\r\n")
      term.flush()
      connection.removeConnectionListener(this)
      connection.close()
    } catch {
      case ex: Exception =>
        logger.error("run()", ex)
    }
  }

  // this implements the ConnectionListener!
  override def connectionTimedOut(ce: ConnectionEvent): Unit = {
    try {
      term.write("CONNECTION_TIMEDOUT\r\n")
      term.flush()
      // close connection
      connection.close()
    } catch {
      case ex: Exception =>
        logger.error("connectionTimedOut()", ex)
    }
  }

  override def connectionIdle(ce: ConnectionEvent): Unit = {
    try {
      term.write("CONNECTION_IDLE\r\n")
      term.flush()
    } catch {
      case e: IOException =>
        logger.error("connectionIdle()", e)
    }
  }

  override def connectionLogoutRequest(ce: ConnectionEvent): Unit = {
    try {
      term.write("CONNECTION_LOGOUTREQUEST\r\n")
      term.flush()
    } catch {
      case ex: Exception =>
        logger.error("connectionLogoutRequest()", ex)
    }
  }

  override def connectionSentBreak(ce: ConnectionEvent): Unit = {
    try {
      term.write("CONNECTION_BREAK\r\n")
      term.flush()
    } catch {
      case ex: Exception =>
        logger.error("connectionSentBreak()", ex)
    }
  }
}
