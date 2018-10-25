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

import java.io.{Reader, Writer}

import com.thing2x.smqd.net.telnet.ScripterEngine._
import javax.script._

object Scripter {
  def apply(): Scripter = new Scripter()
}

class Scripter {

  val context = new SimpleScriptContext

  //  private val engines = Map(
  //    SCALA -> ScripterEngine(SCALA),
  //    JAVA -> ScripterEngine(JAVA),
  //    JS -> ScripterEngine(JS)
  //  )

  private val engines = TelnetService.scriptEngines

  def setWriter(writer: Writer): Unit = context.setWriter(writer)
  def setErrorWriter(writer: Writer): Unit = context.setErrorWriter(writer)

  def set(key: String, value: AnyRef): Unit = context.getBindings(ScriptContext.ENGINE_SCOPE).put(key, value)

  def setClassLoader(classLoader: ClassLoader): Unit = engines.foreach(_.setClassLoader(classLoader))

  def eval(reader: Reader, cmd: String): Unit = {
    if (cmd.endsWith(".bsh")) {
      engines.find(_.lang == JAVA).get.eval(reader, context)
    }
    else if (cmd.endsWith(".sc")) {
      engines.find(_.lang == SCALA).get.eval(reader, context)
    }
    else if (cmd.endsWith(".js")) {
      engines.find(_.lang == JS).get.eval(reader, context)
    }
  }
}


object ScripterEngine {
  sealed trait Lang
  case object SCALA extends Lang
  case object JAVA extends Lang
  case object JS extends Lang

  def apply(lang: Lang): ScripterEngine =
    if (lang == JAVA) new JavaEngine(JAVA)
    else if (lang == JS) new JsEngine(JS)
    else new ScEngine(SCALA)
}

trait ScripterEngine {
  def lang: Lang
  def setClassLoader(classLoader: ClassLoader): Unit
  def eval(reader: Reader, context: ScriptContext)
  def set(name: String, valueType: String, value: AnyRef): Unit
}


//////////////////////////////////////////////////
// Scala

class ScEngine(val lang: Lang) extends ScripterEngine {

  // engine implementation is `scala.tools.nsc.interpreter.Scripted`
  private val engine = new ScriptEngineManager().getEngineByName("scala").asInstanceOf[ScriptEngine with Compilable]

  override def set(name: String, valueType: String, value: AnyRef): Unit = {
    /* to preserve the type of value, use IMain */
    import scala.tools.nsc.interpreter.IMain
    val intp: IMain = engine.asInstanceOf[scala.tools.nsc.interpreter.Scripted].intp
    intp.bind(name, valueType, value)
  }

  override def setClassLoader(classLoader: ClassLoader): Unit = Unit

  override def eval(reader: Reader, context: ScriptContext): Unit = engine.eval(reader, context)
}

//////////////////////////////////////////////////
// JavaScript

class JsEngine(val lang: Lang) extends ScripterEngine {

  private val engine = new ScriptEngineManager().getEngineByName("nashorn")

  def set(name: String, valueType: String, value: AnyRef): Unit = engine.put(name, value)

  override def setClassLoader(classLoader: ClassLoader): Unit = Unit

  override def eval(reader: Reader, context: ScriptContext): Unit = engine.eval(reader, context)
}

//////////////////////////////////////////////////
// JAVA

class JavaEngine(val lang: Lang) extends ScripterEngine {

  private val engine = new ScriptEngineManager().getEngineByName("bsh")

  def set(name: String, valueType: String, value: AnyRef): Unit = engine.put(name, value)

  override def setClassLoader(classLoader: ClassLoader): Unit = Unit

  override def eval(reader: Reader, context: ScriptContext): Unit = engine.eval(reader, context)
}