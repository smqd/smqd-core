
import com.thing2x.smqd.Smqd
import com.thing2x.smqd.net.telnet.ScShell
import javax.script.{ScriptEngine, ScriptEngineManager}

import scala.collection.JavaConverters._

def printScriptEngines(): Unit = {
  val manager = new ScriptEngineManager
  val factories = manager.getEngineFactories.asScala

  for (factory <- factories) {
    println("===========================")
    println(factory.getEngineName)
    println(factory.getEngineVersion)
    println(factory.getLanguageName)
    println(factory.getLanguageVersion)
    println(factory.getExtensions)
    println(factory.getMimeTypes)
    println(factory.getNames)
  }
}

val shell: ScShell = $shell
val engine: ScriptEngine = $engine
val args: Array[String] = $args
val ctx: scala.Dynamic = $ctx

val smqd: Smqd        = shell.smqd
val username: String  = shell.username

val hello = "World"

// defer execution of code block
shell.defer {
  shell.terminal.println(s"######## deferred ########")
  shell.terminal.println(s"hello = $hello")
  println(s"############################4 - $hello")
}

println("-----------------")
println(s"Hello3 $hello")
println("-----------------")
println(s"ENGINE(1): $engine")
println(s"CTX(1): $ctx")

println("-----------------")
println(s"SMQD(1): $smqd")
println(s"SHELL(1): $shell")
println(s"ARGS(1): $args")

println("-----------------")
println(s"username: $username")

println("-----------------")
println(s"username: ${ctx}")

//println(s"SMQD: ${com.thing2x.smqd.net.telnet.TelnetService.smqdInstance}")
//
//val shell: BshShell = $ctx.SHELL.asInstanceOf[BshShell]
//println(s"SHELL: ${shell}")

println("")