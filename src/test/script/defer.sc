import com.thing2x.smqd.net.telnet.ScShell

val shell: ScShell = $shell

val hello = "World"

// defer execution of code block
shell.defer {
  println(s"######## deferred ########")
  println(s"This is deferred message: Hello $hello -- 2")
  println()
}

println()
println("-----------------")
println(s"Hello $hello")
println()