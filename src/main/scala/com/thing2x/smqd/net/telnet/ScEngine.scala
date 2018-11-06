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

import com.typesafe.scalalogging.StrictLogging
import javax.script._

import scala.collection.JavaConverters._
import scala.tools.nsc.interpreter.IMain

object ScEngine extends StrictLogging {
  def apply(): ScEngine = new ScEngine()

  def debugAvailableEngines(): Unit = {
    val factoryManager = new ScriptEngineManager()
    factoryManager.getEngineFactories.asScala.foreach{ ef =>
      logger.debug(s"${ef.getEngineName}, ${ef.getEngineVersion}, ${ef.getLanguageName}, ${ef.getNames}")
    }
  }
}

class ScEngine extends StrictLogging {

  private val engine = new ScriptEngineManager().getEngineByName("scala").asInstanceOf[ScriptEngine with Compilable]
  private val context = engine.getContext

  private[telnet] val intp: IMain = engine.asInstanceOf[scala.tools.nsc.interpreter.Scripted].intp
  def setWriter(writer: Writer): Unit = context.setWriter(writer)
  def setErrorWriter(writer: Writer): Unit = context.setErrorWriter(writer)
  def setReader(reader: Reader): Unit = context.setReader(reader)

  def setAttribute(name: String, value: AnyRef): Unit = {
    context.setAttribute(name, value, ScriptContext.ENGINE_SCOPE)
  }

  def bind(name: String, value: AnyRef): Unit = {
    bind(name, value.getClass.getCanonicalName, value)
  }

  def bind(name: String, valueType: String, value: AnyRef): Unit = {
    /* to preserve the type of value, use IMain */
    intp beQuietDuring {
      intp.bind(name, valueType, value)
    }
  }

  def eval(reader: Reader, cmd: String, args: Array[String] = Array.empty): Unit = {
    bind("$args", "Array[String]", args)
    engine.eval(reader, context)
//    import scala.tools.nsc.interpreter.IMain
//    import scala.tools.nsc.util._
//    val intp: IMain = engine.asInstanceOf[scala.tools.nsc.interpreter.Scripted].intp
//    intp.interpret(stringFromReader(reader), true)
  }
}
