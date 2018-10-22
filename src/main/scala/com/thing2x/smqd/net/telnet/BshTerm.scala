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

  @throws[IOException]
  def write(ch: Char): Unit

  @throws[IOException]
  def write(str: String): Unit

  @throws[IOException]
  def println(str: String): Unit

  @throws[IOException]
  def flush(): Unit

  @throws[IOException]
  def read: String

  @throws[IOException]
  def setForegroundColor(color: Int): Unit = {
  }

  @throws[IOException]
  def setBackgroundColor(color: Int): Unit = {
  }

  @throws[IOException]
  def setBold(b: Boolean): Unit = {
  }

  @throws[IOException]
  def setItalic(b: Boolean): Unit = {
  }

  @throws[IOException]
  def setUnderlined(b: Boolean): Unit = {
  }

  @throws[IOException]
  def setBlink(b: Boolean): Unit = {
  }

  @throws[IOException]
  def bell(): Unit = {
  }

}


class BshTermBuffer extends BshTerm {

  private var buffer = new StringBuffer

  @throws[IOException]
  override def write(ch: Char): Unit = {
    buffer.append(ch)
  }

  @throws[IOException]
  override def write(str: String): Unit = {
    buffer.append(str)
  }

  @throws[IOException]
  override def println(str: String): Unit = {
    buffer.append(str)
    buffer.append("\r\n")
  }

  @throws[IOException]
  override def flush(): Unit = {
  }

  override def toString: String = buffer.toString

  def reset(): Unit = {
    buffer = new StringBuffer
  }

  @throws[IOException]
  override def read: String = null
}


class BshTermTelnet(term: BasicTerminalIO) extends BshTerm {

  // clear the screen and start from zero
  term.eraseScreen()
  term.homeCursor()

  @throws[IOException]
  override def write(ch: Char): Unit = {
    term.write(ch)
  }

  @throws[IOException]
  override def write(str: String): Unit = {
    term.write(str)
  }

  @throws[IOException]
  override def println(str: String): Unit = {
    term.write(str)
    term.write("\r\n")
  }

  @throws[IOException]
  override def flush(): Unit = {
    term.flush()
  }

  @throws[IOException]
  override def read: String = {
    val ef = new Editfield(term, "confirm", 1)
    ef.run()
    term.write("\r\n")
    term.flush()
    ef.getValue
  }

  @throws[IOException]
  override def setForegroundColor(color: Int): Unit = {
    term.setForegroundColor(color)
  }

  @throws[IOException]
  override def setBackgroundColor(color: Int): Unit = {
    term.setBackgroundColor(color)
  }

  @throws[IOException]
  override def setBold(b: Boolean): Unit = {
    term.setBold(b)
  }

  @throws[IOException]
  override def setItalic(b: Boolean): Unit = {
    term.setItalic(b)
  }

  @throws[IOException]
  override def setUnderlined(b: Boolean): Unit = {
    term.setUnderlined(b)
  }

  @throws[IOException]
  override def setBlink(b: Boolean): Unit = {
    term.setBlink(b)
  }

  @throws[IOException]
  override def bell(): Unit = {
    term.bell()
  }
}
