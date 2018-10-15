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

import bsh.EvalError;
import bsh.Interpreter;
import bsh.NameSpace;
import com.thing2x.smqd.util.StringUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.util.ArrayList;
import java.util.Hashtable;
import java.util.Vector;

public class BshProcessor
{
  private Interpreter bshInterpreter;
  private Hashtable<Object, Object> env = new Hashtable<>();
  private String rootPath;
  private File pwd;
  private BshTerm term;
  private String prompt = "bsh";
  private String[] scriptPaths;

  private static Logger logger = LoggerFactory.getLogger(LoginShell.class);

  public BshProcessor(Interpreter bshInterpreter, BshTerm term, String[] scriptPaths)
          throws EvalError
  {
    String location = BshProcessor.class.getProtectionDomain().getCodeSource().getLocation().getPath();
    File locationFile = new File(location);
    File root = new File(locationFile.getParent());
    try
    {
      this.rootPath = root.getCanonicalPath();
      this.pwd = root;
      this.scriptPaths = scriptPaths;

      logger.debug("Root Path: {}", rootPath);
    }
    catch (IOException e)
    {
      e.printStackTrace();
    }

		this.bshInterpreter = bshInterpreter;
		this.term = term;

		bshInterpreter.set("ENV", env);
		env.put("alias h", "history");
		env.put("alias ll", "ls");
		env.put("alias l", "ls");
}

  public BshTerm getTerminal()
  {
    return term;
  }

  public boolean process(String cmd) throws Exception
  {
    return process(cmd, null, -1);
  }

  public boolean process(String cmd, Vector<String> hist, int histOffset) throws Exception
  {
    if ((cmd = internalCommand(cmd, hist, histOffset)) == null)
    {
      term.flush();
      return true;
    }

    String[] args = StringUtil.split(cmd, " \t\r\n");

    if ("exit".equals(args[0]))
    {
      term.flush();
      return false;
    }
//		else if ("procadm".equals(args[0]))
//		{
//			ProcAdminBsh.exec((BshTerm) bshInterpreter.get("TERM"), (X3RPCClient) bshInterpreter.get("X3CLIENT"), args);
//			return true;
//		}
//		else if ("curoadm".equals(args[0]))
//		{
//			CuroAdminBsh.exec((BshTerm) bshInterpreter.get("TERM"), (X3RPCClient) bshInterpreter.get("X3CLIENT"), args);
//			return true;
//		}
//		else if (args.length == 2 && "help".equals(args[0]) && "procadm".equals(args[1]))
//		{
//			ProcAdminBsh.help((BshTerm) bshInterpreter.get("TERM"));
//			return true;
//		}
//		else if (args.length == 2 && "help".equals(args[0]) && "curoadm".equals(args[1]))
//		{
//			CuroAdminBsh.help((BshTerm) bshInterpreter.get("TERM"));
//			return true;
//		}
    else
    {
      externalCommand(args);
      return true;
    }
  }

  public void externalCommand(String[] args) throws Exception
  {
    String fileName = args[0];

    if (!args[0].endsWith(".bsh"))
      fileName = args[0] + ".bsh";

    try
    {
      Reader reader = loadBshFile(fileName);
      bshInterpreter.setNameSpace(new NameSpace(bshInterpreter.getNameSpace(), fileName));
      bshInterpreter.set("ARGS", args);
      bshInterpreter.eval(reader);
      reader.close();

      term.flush();
    }
    catch (FileNotFoundException e)
    {
      term.write("Command [" + fileName + "] not found\r\n\r\n");
      term.flush();
    }
    catch (Throwable e)
    {
      term.write("Command [" + fileName + "] fail.\r\n");
      term.write(e.getMessage() + "\r\n\r\n");
      term.flush();
    }
  }

  public Reader loadBshFile(String fileName)
  {
    Reader rt = null;

    for (int n = 0; scriptPaths != null && n < scriptPaths.length && rt == null; n ++) {
      String[] dirs = StringUtil.split(scriptPaths[n], ";");
      for (int i = 0; dirs != null && i < dirs.length && rt == null; i++) {
        File file = new File(dirs[i], fileName);
        if (file.exists()) {
          try {
            rt = new InputStreamReader(new FileInputStream(file));
          } catch (Exception e) {
            logger.warn("File io fail", e);
          }
        }
      }
    }

    if (rt ==  null) {
      InputStream in = getClass().getClassLoader().getResourceAsStream("bsh/"+fileName);
      if (in != null) {
        rt = new InputStreamReader(in);
      }
    }

    return rt;
  }

  /**
   * @param pattern wild expression for bsh file; *.bsh , abc*.bsh, abc?.bsh
   */
  public File[] loadAllBshFiles(final String pattern)
  {
    File[] rt;
    ArrayList<File> list = new ArrayList<>();

    String prop = System.getProperty("bsh.path", null);
    if (prop == null || prop.length() == 0)
      return new File[0];

    String[] dirs = StringUtil.split(prop, ";");
    for (int i = 0; dirs != null && i < dirs.length; i++)
    {
      File f = new File(dirs[i]);
      File[] children = f.listFiles(new FileFilter() {
        public boolean accept(File file)
        {
          if (pattern == null || "*".equals(pattern))
            return true;

          String fileName = file.getName();
          if (StringUtil.compareCaseWildExp(fileName, pattern) == 0)
            return true;
          return false;
        }
      });

      for (File child : children)
      {
        list.add(child);
      }
    }
    rt = new File[list.size()];
    return list.toArray(rt);
  }

  private String internalCommand(String cmdLine, Vector<String> hist, int histOffset) throws IOException
  {
    while (true)
    {
      String[] args = StringUtil.split(cmdLine, " \t\r\n");
      if (args == null || args.length == 0)
      {
        return null;
      }

      String cmd = args[0];
      String param = "";

      if (cmdLine.length() > cmd.length())
        param = cmdLine.substring(cmd.length());

      if (cmd.startsWith("!!") && cmd.length() > 2)
      {
        cmd = "!!";
        param = cmdLine.substring(2);
      }

      String alias = (String) env.get("alias " + cmd);
      if (alias != null)
      {
        cmd = alias;
        cmdLine = cmd + " " + param;
      }

      if (hist != null)
      {
        if ("history".equals(cmd))
        {
          int size = hist.size();
          for (int i = size; i > 0; i--)
          {
            term.write(" " + StringUtil.sprintf("%6d", Integer.toString(size - i + 1 + histOffset)) + "  "
                    + hist.get(i - 1) + "\r\n");
          }
          return null;
        }
        else if ("!!".equals(cmd))
        {
          if (hist.size() <= 1)
          {
            term.write("history empty\r\n");
            return null;
          }

          if ((cmdLine = hist.get(1)) == null)
          {
            term.write("history internal error-101\r\n");
            return null;
          }
          cmdLine += param;
          hist.set(0, cmdLine);
          continue;
        }
        else if (cmd.startsWith("!"))
        {
          if (cmd.length() < 2)
          {
            term.write(cmdLine + ": syntax error\r\n");
            return null;
          }
          else
          {
            String key = cmd.substring(1);
            int idx = -1;
            try
            {
              idx = Integer.parseInt(key) - histOffset;
            }
            catch (Exception e)
            {
            }

            if (idx > 0 && hist.size() > idx)
            {
              if ((cmdLine = hist.get(hist.size() - idx)) == null)
              {
                term.write(cmdLine + ": event not found\r\n");
                return null;
              }

              cmdLine += param;
              hist.set(0, cmdLine);
              continue;
            }
            else
            {
              for (int i = 1; i < hist.size(); i++)
              {
                if (hist.get(i).startsWith(key))
                {
                  if ((cmdLine = hist.get(i)) == null)
                    return null;

                  hist.set(0, cmdLine);
                  return cmdLine;
                }
              }

              term.write(cmdLine + ": event not found\r\n");
              return null;
            }
          }
        }
      }

      if ("pwd".equals(cmd))
      {
        term.write(getPwd() + "\r\n");
        return null;
      }
      else if ("conn".equals(cmd) || "connect".equals(cmd))
      {
        if (args.length == 3)
        {
          prompt = args[1].toUpperCase() + " " + args[2].toUpperCase();
        }
        else
        {
          term.write("Usage: connect [Group] [Process]\r\n");
        }
        return null;
      }
      else if ("disconn".equals(cmd) || "disconnect".equals(cmd))
      {
        prompt = "bsh";
        return null;
      }
      else if ("exit".equals(cmd))
      {
        if (!prompt.equals("bsh"))
        {
          term.write("disconnect first and then do exit.\r\n");
          return null;
        }
        else
          return cmdLine;
      }
      else if ("".equals(cmd))
      {
        hist.remove(0);
        return null;
      }
      else
      {
        if (!prompt.equals("bsh"))
        {
          if ("help".equals(cmd))
          {
            return cmdLine;
          }
          else if ("procadm".equals(cmd))
          {
            if (args.length > 1 && prompt.startsWith(args[1].toUpperCase()))
            {
              return cmdLine;
            }
            else
            {
              String rt = "procadm " + prompt;
              for (int i = 1; i < args.length; i++)
                rt += " " + args[i];

              return rt;
            }
          }
          else
          {
            return "run " + prompt.toLowerCase() + " " + cmdLine;
          }
        }

        return cmdLine;
      }
    }
  }

  public boolean canAccess(String relPath, boolean isDirectory)
  {
    String str = getRealPath(relPath);
    if (isDirectory && !(new File(str)).isDirectory())
      return false;
    return str.startsWith(rootPath);
  }

  public String getRealPath(String relPath)
  {
    File file = null;

    if (relPath.startsWith("/"))
    {
      file = new File(rootPath, relPath);
    }
    else
    {
      file = new File(pwd, relPath);
    }

    try
    {
      return file.getCanonicalPath();
    }
    catch (IOException e)
    {
    }
    return null;
  }

  public String getRelativePath(String realPath)
  {
    String canonicalPath = null;

    try
    {
      canonicalPath = (new File(realPath)).getCanonicalPath();
    }
    catch (IOException e)
    {
    }
    if (canonicalPath == null)
      return null;

    if (canonicalPath.startsWith(rootPath))
    {
      return realPath.substring(rootPath.length()).replace('\\', '/');
    }
    return null;
  }

  public String getPwd()
  {
    try
    {
      String path = pwd.getCanonicalPath().substring(rootPath.length());
      if (path.length() == 0)
        return "/";
      else
        return path.replace('\\', '/');
    }
    catch (IOException e)
    {
    }
    return null;
  }

  public String getPrompt()
  {
    return prompt;
  }

  public String setPwd(String relPath)
  {
    if (canAccess(relPath, true))
      pwd = new File(getRealPath(relPath));

    return getPwd();
  }

}
