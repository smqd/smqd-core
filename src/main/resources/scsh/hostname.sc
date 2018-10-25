/*
	@Begin
		Display Host System Name
	Syntax
		hostname
	@End
 */

val localhost: java.net.InetAddress = java.net.InetAddress.getLocalHost
println("")
println(localhost.getHostName)
println("")
