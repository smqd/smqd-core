
import com.thing2x.smqd.Smqd
import com.thing2x.smqd.net.telnet.ScShell
import javax.script.ScriptEngine
import net.wimpi.telnetd.io.TerminalIO

val shell: ScShell = $shell
val engine: ScriptEngine = $engine
val args: Array[String] = $args
val ctx: scala.Dynamic = $ctx

val smqd: Smqd        = shell.smqd
val username: String  = shell.username

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

//println(s"Shell.inputStream = ${shell.inputStream.read()}")
//val ans = Console.in.read().toChar

println(s"term: ${shell.connection.getTerminalIO.asInstanceOf[TerminalIO].getTerminal.getClass.getName}")

println("")
