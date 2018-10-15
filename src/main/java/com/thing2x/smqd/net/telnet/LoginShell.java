package com.thing2x.smqd.net.telnet;

import com.thing2x.smqd.util.StringUtil;
import net.wimpi.telnetd.io.BasicTerminalIO;
import net.wimpi.telnetd.io.toolkit.Editfield;
import net.wimpi.telnetd.net.Connection;
import net.wimpi.telnetd.net.ConnectionEvent;
import net.wimpi.telnetd.shell.Shell;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.EOFException;
import java.io.IOException;

// Created on 2005. 6. 21, Kwon, Yeong Eon

/**
 * This class is an example implmentation of a Shell.<br>
 */
public class LoginShell implements Shell
{
  private static Logger logger = LoggerFactory.getLogger(LoginShell.class);

  private Connection connection;
  private BasicTerminalIO term;

  private String[] allowPattern;
  private LoginShellDelegate delegate;

  public LoginShell()
  {
  }


  public void setDelegate(LoginShellDelegate delegate) { this.delegate = delegate; }
  public void setAllow(String allow)
  {
    allowPattern = StringUtil.split(allow, " , \t\n\r");
  }

  private boolean checkAllow(String address)
  {
    if (allowPattern == null)
      return true;

    for (String pattern : allowPattern)
    {
      if (StringUtil.compareCaseWildExp(address, pattern) == 0)
        return true;
    }

    return false;
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
      String address = con.getConnectionData().getHostAddress();
      if (!checkAllow(address))
      {
        con.close();
        return;
      }

      connection = con;
      term = connection.getTerminalIO();
      // dont forget to register listener
      connection.addConnectionListener(this);

      // clear the screen and start from zero
      term.eraseScreen();
      term.homeCursor();

      Editfield ef = null;

      for (int i = 0; i < 3; i++)
      {
        term.write("Login: ");
        ef = new Editfield(term, "login", 50);
        ef.run();
        String username = ef.getValue();

        term.write("\r\nPassword: ");
        ef = new Editfield(term, "passwd", 50);
        ef.setPasswordField(true);
        ef.run();
        String password = ef.getValue();
        term.flush();

        if (delegate == null)
        {
          if ("admin".equals(username) && "password".equals(password))
          {
            connection.setNextShell("bsh");
            return;
          }
          else if ("dummy".equals(username) && "dummy".equals(password))
          {
            connection.setNextShell("dummy");
            return;
          }
        }
        else
        {
          if (delegate.login(username, password))
          {
            connection.setNextShell("bsh");
            return;
          }
        }

        term.write("\r\nLogin incorrect\r\n\r\n");
      }

      term.homeCursor();
      term.eraseScreen();
      term.write("Goodbye!.\r\n\r\n");
      term.flush();

      connection.close();
    }
    catch (EOFException e)
    {
      logger.info("Client send quit signal.");
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
    return new LoginShell();
  }
}
