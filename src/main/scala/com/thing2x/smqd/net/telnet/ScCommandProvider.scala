package com.thing2x.smqd.net.telnet

import java.io.{File, Reader}

// 10/25/18 - Created by Kwon, Yeong Eon

/**
  *
  */
abstract class ScCommandProvider(root: File, cwd: String) {

  private var _aliases: Map[String, String] = Map.empty

  def aliases: Map[String, String] = _aliases
  def aliases_=(tup: (String, String)): Unit = _aliases += (tup._1 -> tup._2)
  def setAlias(alias: String, real: String): Unit =  _aliases += (alias -> real)

  def alias(alias: String): Option[String] = if (aliases.contains(alias)) Some(aliases(alias)) else None

  def command(cmd: String): Option[ScCommand]
  def command(args: Seq[String]): Option[ScCommand]

  def findAllScriptFiles(pattern: String = "*"): Array[File]
  def loadScriptFile(fileName: String): Option[Reader]

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
