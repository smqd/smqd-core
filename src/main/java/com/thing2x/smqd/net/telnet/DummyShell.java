package com.thing2x.smqd.net.telnet;

import java.io.IOException;

import net.wimpi.telnetd.io.BasicTerminalIO;
import net.wimpi.telnetd.net.Connection;
import net.wimpi.telnetd.net.ConnectionEvent;
import net.wimpi.telnetd.shell.Shell;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

// 2005. 6. 21 - Created by Kwon, Yeong Eon

/**
 * This class is an example implmentation of a Shell.
 */
public class DummyShell implements Shell {
  private static Logger logger = LoggerFactory.getLogger(LoginShell.class);

  private Connection connection;
  private BasicTerminalIO term;

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
      term = connection.getTerminalIO();
      // dont forget to register listener
      connection.addConnectionListener(this);

      // clear the screen and start from zero
      term.eraseScreen();
      term.homeCursor();

      term.write("Your terminal type is "
              + connection.getConnectionData().getNegotiatedTerminalType());
      term.write("\r\n");
      term.flush();

      while (true)
      {
        int c = term.read();

        if (c == 'q')
        {
          break;
        }
        else
        {
          term.write("[" + Integer.toString(c) + "]\r\n");
        }
      }

      term.homeCursor();
      term.eraseScreen();
      term.write("Goodbye!.\r\n\r\n");
      term.flush();

      connection.close();
    }
    catch (Exception ex)
    {
      logger.error("run()", ex);
    }
  }

  // this implements the ConnectionListener!
  public void connectionTimedOut(ConnectionEvent ce)
  {
    try
    {
      term.write("CONNECTION_TIMEDOUT");
      term.flush();
      // close connection
      connection.close();
    }
    catch (Exception ex)
    {
      logger.error("connectionTimedOut()", ex);
    }
  }

  public void connectionIdle(ConnectionEvent ce)
  {
    try
    {
      term.write("CONNECTION_IDLE");
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
      term.write("CONNECTION_LOGOUTREQUEST");
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
      term.write("CONNECTION_BREAK");
      term.flush();
    }
    catch (Exception ex)
    {
      logger.error("connectionSentBreak()", ex);
    }
  }

  public static Shell createShell()
  {
    return new DummyShell();
  }
}
