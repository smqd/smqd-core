/*
	@Begin
	System shutdown
	
	Syntax
		shutdown <option, ...>
		option
		    <no-option>    shutdown system after confirm again.
		    now            shutdown system immediately without confirm.
		    force          shutdown system without cleaning process 
		                   -- CAUTION -- Use for emergency situation only.
	@End
 */

import com.thing2x.smqd.Smqd
import com.thing2x.smqd.net.telnet.ScShell

val args: Array[String] = $args
val shell: ScShell = $shell
val smqd: Smqd = shell.smqd

var optNow = false
var optForce = false

args.foreach { opt =>
	if (opt == "now") optNow = true
	if (opt == "force") optForce = true
}

var shutdownConfirm = false

if (optNow) {
	shutdownConfirm = true
}
else {
	var loop = true
	do {
		if (optForce)
			print("\n**CONFIRM** System SHUTDOWN now? [y/n] ")
		else
			print("\n**CAUTION** System SHUTDOWN immediately? [y/n] ")

		val ans = shell.inputStream.read().toChar
		println(s"$ans")
		if (ans == 'Y' || ans == 'y')
		{
			shutdownConfirm = true
			loop = false
		}
		else
		{
			shutdownConfirm = false
			loop = false
		}
	} while(loop)
}

if (shutdownConfirm) {
	if (optForce)
	{
		println("\nSystem shutdown... immediately...\n")
		System.exit(0);
	}
	else
	{
		println("\nSystem is going to shutdown.....\n")
		smqd.Implicit.system.terminate()
		System.exit(0)
	}
}
