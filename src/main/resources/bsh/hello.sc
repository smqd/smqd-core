
import com.thing2x.smqd.Smqd
import com.thing2x.smqd.net.telnet.BshShell
import javax.script.ScriptEngineManager

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

val hello = "World"

println("-----------------")
println(s"Hello3 $hello")
println("-----------------")
println(s"ENGINE(1): ${$engine}")
println(s"CTX(1): ${$ctx}")

println("-----------------")
val smqd: Smqd = $ctx.SMQD.asInstanceOf[Smqd]
println(s"SMQD: ${smqd}")
println(s"SMQD: ${com.thing2x.smqd.net.telnet.TelnetService.smqdInstance}")

val shell: BshShell = $ctx.SHELL.asInstanceOf[BshShell]
println(s"SHELL: ${shell}")

println("")