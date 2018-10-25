package com.thing2x.smqd.net.telnet

import java.io.{Reader, Writer}

import com.typesafe.scalalogging.StrictLogging
import javax.script._

class ScalaEngine extends StrictLogging {
  val engine = new ScriptEngineManager().getEngineByName("scala").asInstanceOf[ScriptEngine with Compilable]
  val context = engine.getContext

  def setClassLoader(classLoader: ClassLoader): Unit = Unit //engine.setClassLoader(classLoader)
  def setWriter(writer: Writer): Unit = context.setWriter(writer)
  def setErrorWriter(writer: Writer): Unit = context.setErrorWriter(writer)

  def set(key: String, value: AnyRef): Unit = set0(key, value.getClass.getCanonicalName, value)

  private def set0(name: String, valueType: String, value: AnyRef): Unit = {
    context.setAttribute(name, value, ScriptContext.ENGINE_SCOPE)

    /* to preserve the type of value, use IMain */
    //import scala.tools.nsc.interpreter.IMain
    //val intp: IMain = engine.asInstanceOf[scala.tools.nsc.interpreter.Scripted].intp
  }

  def eval(reader: Reader, cmd: String, args: Array[String] = Array.empty): Unit = {
    set("$args", args)
    engine.eval(reader, context)
//    import scala.tools.nsc.interpreter.IMain
//    import scala.tools.nsc.util._
//    val intp: IMain = engine.asInstanceOf[scala.tools.nsc.interpreter.Scripted].intp
//    intp.interpret(stringFromReader(reader), true)
  }
}
