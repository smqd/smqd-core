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

import com.thing2x.smqd.net.telnet.BshDefaultCommandProvider._
import com.thing2x.smqd.util.StringUtil
import com.typesafe.scalalogging.StrictLogging
import javax.script.ScriptException


trait BshCommand {
  def name: String
  def help: String = ""
  def exe(args: Seq[String], shell: BshShell): Unit
}

abstract class BshCommandProvider(root: File, cwd: String) {

  private var _aliases: Map[String, String] = Map.empty

  def aliases: Map[String, String] = _aliases
  def aliases_=(tup: (String, String)): Unit = _aliases += (tup._1 -> tup._2)
  def setAlias(alias: String, real: String): Unit =  _aliases += (alias -> real)

  def alias(alias: String): Option[String] = if (aliases.contains(alias)) Some(aliases(alias)) else None

  def command(cmd: String): Option[BshCommand]
  def command(args: Seq[String]): Option[BshCommand]

  def findAllBshFiles(pattern: String = "*"): Array[File]
  def loadBshFile(fileName: String): Option[Reader]

  private var _workingDirectory: String = cwd
  def workingDirectory: String = _workingDirectory
  def workingDirectory_=(dir: String): String = {
    _workingDirectory = getRelativePath(getRealPath(dir)).getOrElse("/")
    _workingDirectory
  }

  def getRealPath(path: String): String = {
    val file = if (path.startsWith("/")) new File(root, path) else new File(root, _workingDirectory + "/" + path)
    file.getCanonicalPath
  }

  def getRelativePath(realPath: String): Option[String] = {
    val canonicalPath: String = new File(realPath).getCanonicalPath
    if (canonicalPath == root.getCanonicalPath){
      Some("/")
    }
    else if (canonicalPath.startsWith(root.getCanonicalPath)) {
      Some(canonicalPath.substring(root.getCanonicalPath.length).replace('\\', '/'))
    }
    else{
      None
    }
  }

  def canAccess(relativePath: String, isDirectory: Boolean): Boolean = {
    val str = getRealPath(relativePath)
    if (isDirectory && ! new File(str).isDirectory) return false
    str.startsWith(root.getCanonicalPath)
  }
}


object BshDefaultCommandProvider {

  def apply(root: File, cwd: String, paths: Seq[String]) = new BshDefaultCommandProvider(root, cwd, paths)

  object Exit extends BshCommand {
    override val name = "exit"
    override def exe(args: Seq[String], shell: BshShell): Unit = {
      shell.terminal.write("Good bye\r\n")
      shell.terminal.flush()
      shell.exit(0)
    }
  }

  object History extends BshCommand {
    override val name = "history"
    override def exe(args: Seq[String], shell: BshShell): Unit = {
      val hist = shell.history
      hist.zipWithIndex.reverse.foreach{ case (h, idx) =>
        shell.terminal.write(f" ${idx + 1}%6d $h\r\n")
      }
      shell.terminal.flush()
    }
  }

  object ChangeDirectory extends BshCommand {
    override val name = "cd"
    override def exe(args: Seq[String], shell: BshShell): Unit = {
      if (args.length == 1) {
        shell.setWorkingDirectory("/")
      }
      else {
        if (shell.canAccess(args(1), true))
          shell.setWorkingDirectory(args(1))
        else
          shell.terminal.println("Access denied.")
      }
    }
  }

  class Exec(reader: Reader, cmd: String) extends BshCommand {
    override val name = "exec"
    override def exe(args: Seq[String], shell: BshShell): Unit = {
      try {
        shell.interpreter.eval(reader, cmd, args.toArray)
        shell.terminal.flush()
      } catch {
        case e: ScriptException => // throws by javax.script
          shell.terminal.println(s"Command [$cmd] has script error at line: ${e.getLineNumber} column: ${e.getColumnNumber}")
          shell.terminal.println({e.getMessage.split("\n").take(5).mkString("\n")})
        case e: Throwable =>
          shell.terminal.write(s"Command [$cmd] fail. - ${e.getClass.getName}\r\n")
          shell.terminal.write(e.getMessage + "\r\n\r\n")
          shell.terminal.flush()
      }
      finally {
        reader.close()
      }
    }
  }
}


class BshDefaultCommandProvider(root: File, cwd: String, paths: Seq[String]) extends  BshCommandProvider(root, cwd) with StrictLogging {

  private val commands = Seq(Exit, History, ChangeDirectory)

  // default aliases
  aliases = "h" -> "history"
  aliases = "ll" -> "ls"

  override def command(args: Seq[String]): Option[BshCommand] = command(args.head)

  override def command(cmd: String): Option[BshCommand] = {

    def _find(fn: String, ext: String): Option[BshCommand] = {
      val fileName = if (fn.endsWith(ext)) cmd else s"$fn$ext"
      loadBshFile(fileName) match {
        case Some(reader) =>
          Some(new Exec(reader, fileName))
        case None =>
          None
      }
    }

    val found = commands.find(_.name == cmd)
    if (found.isDefined) {
      found
    }
    else {
      _find(cmd, ".sc")
      //_find(cmd, ".sc").orElse ( _find(cmd, ".bsh").orElse( _find(cmd, ".js") ) )
    }
  }

  override def loadBshFile(fileName: String): Option[Reader] = {
    paths.map( new File(_, fileName) ).find(_.exists) match {
      case Some(file) =>
        try {
          Some(new InputStreamReader(new FileInputStream(file)))
        }catch {
          case e : Throwable =>
            logger.warn("Failure for reading file", e)
            None
        }
      case None =>
        paths.flatMap(p => Option(getClass.getClassLoader.getResource(s"$p/$fileName"))).headOption match {
          case Some(url) => Some(new InputStreamReader(url.openStream()))
          case None => None
        }
    }
  }

  /**
    * @param pattern wild expression for bsh file; *.bsh , abc*.bsh, abc?.bsh
    */
  override def findAllBshFiles(pattern: String = "*"): Array[File] = {
    paths.map( new File(_) ).flatMap{ dir =>
      dir.listFiles(new FileFilter() {
        override def accept(file: File): Boolean = {
          if (pattern == null || "*" == pattern) true
          else StringUtil.compareCaseWildExp(file.getName, pattern) == 0
        }
      })
    }.toArray
  }

}
