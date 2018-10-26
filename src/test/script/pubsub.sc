import com.thing2x.smqd.Smqd
import com.thing2x.smqd.net.telnet.ScShell
import javax.script.ScriptEngine

val shell: ScShell = $shell
val engine: ScriptEngine = $engine
val args: Array[String] = $args
val ctx: scala.Dynamic = $ctx

val smqd: Smqd = shell.smqd


val sub = smqd.subscribe("sensor/#") {
  case (path, msg) =>
    shell.printStream.println(s">>>> Message Recv: topic = $path, msg = $msg")
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

