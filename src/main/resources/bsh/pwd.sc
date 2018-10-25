/*
	@Begin
	print current directory

	Syntax
		ls
	@End
 */

import com.thing2x.smqd.net.telnet.ScShell

val shell: ScShell = $shell.asInstanceOf[ScShell]

println(shell.getWorkingDirectory)
println("")
