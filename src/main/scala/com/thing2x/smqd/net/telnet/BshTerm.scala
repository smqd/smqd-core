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

import net.wimpi.telnetd.io.BasicTerminalIO
import net.wimpi.telnetd.io.toolkit.Editfield

trait BshTerm {

  val BLACK = 30
  val RED = 31
  val GREEN = 32
  val YELLOW = 33
  val BLUE = 34
  val MAGENTA = 35
  val CYAN = 36
  val WHITE = 37

  def write(ch: Char): Unit
  def write(str: String): Unit
  def write(b: Byte): Unit
  def write(buf: Array[Byte]): Unit = buf.foreach( write )

  def print(str: String): Unit
  def println(str: String): Unit

  def read: String

  def flush(): Unit = { }

  def setForegroundColor(color: Int): Unit = { }
  def setBackgroundColor(color: Int): Unit = { }

  def setBold(b: Boolean): Unit = { }
  def setItalic(b: Boolean): Unit = { }
  def setUnderlined(b: Boolean): Unit = { }
  def setBlink(b: Boolean): Unit = { }

  def bell(): Unit = { }
}


class BshTermBuffer extends BshTerm {

  private var buffer = new StringBuffer

  override def write(ch: Char): Unit = buffer.append(ch)
  override def write(str: String): Unit = buffer.append(str)
  override def write(b: Byte): Unit = buffer.append(b)

  override def print(str: String): Unit = buffer.append(str)
  override def println(str: String): Unit = buffer.append(str).append("\r\n")

  override def toString: String = buffer.toString

  def reset(): Unit = buffer = new StringBuffer

  override def read: String = null
}


class BshTermTelnet(term: BasicTerminalIO) extends BshTerm {

  // clear the screen and start from zero
  term.eraseScreen()
  term.homeCursor()

  override def write(ch: Char): Unit = term.write(ch)
  override def write(str: String): Unit = term.write(str)
  override def write(b: Byte): Unit = term.write(b)

  override def print(str: String): Unit = term.write(str)
  override def println(str: String): Unit = term.write(s"$str\r\n")

  override def flush(): Unit = term.flush()

  override def read: String = {
    val ef = new Editfield(term, "confirm", 1)
    ef.run()
    term.write("\r\n")
    term.flush()
    ef.getValue
  }

  override def setForegroundColor(color: Int): Unit = term.setForegroundColor(color)
  override def setBackgroundColor(color: Int): Unit = term.setBackgroundColor(color)

  override def setBold(b: Boolean): Unit = term.setBold(b)
  override def setItalic(b: Boolean): Unit = term.setItalic(b)
  override def setUnderlined(b: Boolean): Unit = term.setUnderlined(b)
  override def setBlink(b: Boolean): Unit = term.setBlink(b)

  override def bell(): Unit = term.bell()
}
