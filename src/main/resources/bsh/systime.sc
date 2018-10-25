/*
	@Begin
	Display server time

	Syntax
		systime
	@End
 */

import java.text.SimpleDateFormat

val fmt14 = new SimpleDateFormat("yyyy/MM/dd HH:mm:ss")

println("")
println(fmt14.format(new java.util.Date(System.currentTimeMillis())))
println("")