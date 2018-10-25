/*
	@Begin
	print current directory

	Syntax
		ls
	@End
 */

import com.thing2x.smqd.net.telnet.BshShell

val shell: BshShell = $ctx.SHELL.asInstanceOf[BshShell]

println(shell.getWorkingDirectory)
println("")
