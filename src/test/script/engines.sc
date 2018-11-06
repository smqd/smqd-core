import javax.script.ScriptEngineManager
import scala.collection.JavaConverters._

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
