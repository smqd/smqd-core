import javax.script.ScriptEngineManager

import scala.collection.JavaConverters._
import com.thing2x.smqd._
import com.thing2x.smqd.net.telnet.BshTerm

val hello = "World"

println("-----------------")
println(s"Hello2 $hello\r\n")


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

TERM.asInstanceOf[BshTerm].println("-------TERM-")
val smqd = SMQD.asInstanceOf[Smqd]
println(s"SMQD: ${smqd.version}")