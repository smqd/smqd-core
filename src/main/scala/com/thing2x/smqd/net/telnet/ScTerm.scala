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

import com.typesafe.scalalogging.StrictLogging
import net.wimpi.telnetd.io.BasicTerminalIO

object ScTerm {
  val BLACK = 30
  val RED = 31
  val GREEN = 32
  val YELLOW = 33
  val BLUE = 34
  val MAGENTA = 35
  val CYAN = 36
  val WHITE = 37
}

class ScTerm(term: BasicTerminalIO) extends StrictLogging {

  // clear the screen and start from zero
  term.eraseScreen()
  term.homeCursor()

  def setForegroundColor(color: Int): Unit = term.setForegroundColor(color)
  def setBackgroundColor(color: Int): Unit = term.setBackgroundColor(color)

  def setBold(b: Boolean): Unit = term.setBold(b)
  def setItalic(b: Boolean): Unit = term.setItalic(b)
  def setUnderlined(b: Boolean): Unit = term.setUnderlined(b)
  def setBlink(b: Boolean): Unit = term.setBlink(b)

  def bell(): Unit = term.bell()

  def clear(): Unit = {
    term.eraseScreen()
    term.homeCursor()
  }

  val writer: Writer = new Writer {
    override def write(cbuf: Array[Char], off: Int, len: Int): Unit = term.write(new String(cbuf, off, len))
    override def flush(): Unit = term.flush()
    override def close(): Unit = Unit
  }

  val outputStream: OutputStream = new scala.tools.nsc.interpreter.WriterOutputStream(writer)

  val printStream: PrintStream = new PrintStream(outputStream)

  val inputStream: InputStream = new java.io.InputStream {
    override def read(): Int = {
      val i = term.read()
      logger.info(s"===============>${i} ${i.toChar}")
      i
    }
  }

  val reader: Reader = new java.io.InputStreamReader(inputStream)
}
