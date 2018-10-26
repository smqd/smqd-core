
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
  println(s"This is deferred message with 'println'")
}

val sub = smqd.subscribe("sensor/#") {
  case (path, msg) =>
    shell.terminal.println(s">>>> Message Recv: topic = $path, msg = $msg")
}
println("Subscribed.")

shell.defer {
  smqd.unsubscribe(sub)
  println("Unsubscribed.")
}

Thread.sleep(200)
smqd.publish("sensor/123", "message - 1")
smqd.publish("sensor/456", "message - 2")
Thread.sleep(200)

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

//println(s"SMQD: ${com.thing2x.smqd.net.telnet.TelnetService.smqdInstance}")
//
//val shell: BshShell = $ctx.SHELL.asInstanceOf[BshShell]
//println(s"SHELL: ${shell}")

println("")