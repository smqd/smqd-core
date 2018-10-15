/*
 * Copyright 2018 UANGEL
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.thing2x.smqd.net.telnet;

import java.io.EOFException;
import java.io.File;
import java.io.IOException;
import java.io.Reader;
import java.net.SocketException;

import net.wimpi.telnetd.net.Connection;
import net.wimpi.telnetd.net.ConnectionEvent;
import net.wimpi.telnetd.shell.Shell;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

//import x3.net.cluster.X3RPCClient;
import bsh.Interpreter;

/**
 * This class is an example implmentation of a Shell.<br>
 */
public class BshShell implements Shell
{
  private static Logger logger = LoggerFactory.getLogger(BshShell.class);

  private Connection connection;
  private BshTerm term;
  private BshProcessor processor;

  private static BshShellDelegate delegate;

  public BshShell()
  {
  }


  public static void setDelegate(BshShellDelegate delegate) {
    BshShell.delegate = delegate;
  }

  /**
   * Method that runs a shell
   *
   * @param con Connection that runs the shell.
   */
  public void run(Connection con)
  {
    try
    {
      connection = con;
      // mycon.setNextShell("nothing");
      // dont forget to register listener
      connection.addConnectionListener(this);

      term = new BshTermTelnet(connection.getTerminalIO());

      // We just read any key
      term.write("Bean Shell ready!\r\n");
      term.flush();

      BshCommandField ef = null;
      ef = new BshCommandField(connection.getTerminalIO(), "cmd", 1024, 100);

      ClassLoader classLoader = null;

      classLoader = Thread.currentThread().getContextClassLoader();

      if (classLoader == null)
        classLoader = getClass().getClassLoader();

      Interpreter bshInterpreter = new Interpreter();
      bshInterpreter.setClassLoader(classLoader);
      bshInterpreter.set("CONNECTION", connection);
      bshInterpreter.set("TERM", term);
      bshInterpreter.set("SHELL", this);

      String[] scriptPaths = null;

      if (delegate != null) {
        delegate.prepare(this, bshInterpreter);
        scriptPaths = delegate.scriptPaths(this);
      }

      processor = new BshProcessor(bshInterpreter, term, scriptPaths);

      String cmd = "";
      boolean doContinue = true;

      do
      {
        term.write(processor.getPrompt() + " " + processor.getPwd() + "> ");
        term.flush();
        ef.run();
        term.write("\r\n");
        term.flush();

        if ((cmd = ef.getValue()) == null)
          continue;
        ef.clear();

        doContinue = processor.process(cmd, ef.getHistory(), ef.getHistoryOffset());

      } while (connection.isActive() && doContinue);

      onConnectionClose();
    }
    catch (EOFException e)
    {
      logger.info("Client send quit signal.");
    }
    catch (SocketException e)
    {
      logger.info("Client close the connection.");
    }
    catch (Exception ex)
    {
      logger.error("run()", ex);
    }
  }

  private void onConnectionClose() throws Exception
  {
    processor.externalCommand(new String[]
            {
                    "env", "clean"
            });

    if (connection.isActive())
    {
      term.write("\r\nLog out.\r\n\r\n");
      term.flush();
    }
  }

  public void connectionTimedOut(ConnectionEvent ce)
  {
    try
    {
      onConnectionClose();
    }
    catch (Exception ex)
    {
      logger.error("clear env failure", ex);
    }

    try
    {
      connection.close();
    }
    catch (Exception ex)
    {
      logger.error("close connection failure", ex);
    }
  }

  public void connectionIdle(ConnectionEvent ce)
  {
    try
    {
      term.write("\r\nCONNECTION_IDLE\r\n");
      term.flush();
    }
    catch (IOException e)
    {
      logger.error("connectionIdle()", e);
    }

  }

  public void connectionLogoutRequest(ConnectionEvent ce)
  {
    try
    {
      term.write("\r\nCONNECTION_LOGOUTREQUEST\r\n");
      term.flush();
    }
    catch (Exception ex)
    {
      logger.error("connectionLogoutRequest()", ex);
    }
  }

  public void connectionSentBreak(ConnectionEvent ce)
  {
    try
    {
      term.write("\r\nCONNECTION_BREAK\r\n");
      term.flush();
    }
    catch (Exception ex)
    {
      logger.error("connectionSentBreak()", ex);
    }
  }

  public static Shell createShell()
  {
    return new BshShell();
  }

  /**
   * @param pattern wild expression for bsh file; *.bsh , abc*.bsh, abc?.bsh
   */
  public File[] loadAllBshFiles(final String pattern)
  {
    return processor.loadAllBshFiles(pattern);
  }

  public Reader loadBshFile(final String filename) { return processor.loadBshFile(filename); }

  public boolean canAccess(String relPath, boolean isDirectory)
  {
    return processor.canAccess(relPath, isDirectory);
  }

  public String getRealPath(String relPath)
  {
    return processor.getRealPath(relPath);
  }

  public String getRelativePath(String realPath)
  {
    return processor.getRelativePath(realPath);
  }

  public String getPwd()
  {
    return processor.getPwd();
  }

  public String setPwd(String relPath)
  {
    return processor.setPwd(relPath);
  }
}
