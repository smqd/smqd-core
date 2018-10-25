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
import com.thing2x.smqd.net.telnet.{BshShell, BshTerm}

val args: Array[String] = $ctx.ARGS.asInstanceOf[Array[String]]
val term: BshTerm = $ctx.SHELL.asInstanceOf[BshShell].terminal
val smqd: Smqd = $ctx.SMQD.asInstanceOf[Smqd]

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
			print("\r\n**CONFIRM** System SHUTDOWN now? [y/n] ")
		else
			print("\r\n**CAUTION** System SHUTDOWN immediately? [y/n] ")
			
		val ans = term.read
		if ("Y".equalsIgnoreCase(ans))
		{
			shutdownConfirm = true
			loop = false
		}
		else
		{
			shutdownConfirm = false;
			loop = false
		}
	} while(loop)
}

if (shutdownConfirm) {
	if (optForce)
	{
		println("\r\n**WARN** System exit... immediately...\r\n");
		System.exit(0);
	}
	else
	{
		println("\r\n**CAUTION** System is going to shutdown.....\r\n");
		smqd.Implicit.system.terminate()
		System.exit(0)
	}
}
