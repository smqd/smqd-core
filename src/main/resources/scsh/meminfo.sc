/*
	@Begin
	Show Memory Usage Informations
	
	Syntax
		meminfo Display memory usage
	@End
 */

def asSize(size: Long): String = {
	if (size > 1024*1024*1024)
		(size/(1024*1024*1024))+"G"
	else if (size > 1024*1024)
		(size/(1024*1024)) + "M"
	else if (size > 1024)
		(size/1024) + "K"
	else
		size.toString
}

/////////////// Memory Info /////////////////////
val R: Runtime = Runtime.getRuntime
val pct = (R.totalMemory() - R.freeMemory())*100 / R.totalMemory()

println("")
println("Available Processors = "+R.availableProcessors())
println("Max Memory   = "+asSize(R.maxMemory()))
println("Total Memory = "+asSize(R.totalMemory()))
println("Alloc Memory = "+asSize(R.totalMemory() - R.freeMemory())+" ("+pct+"% Used)")
println("")
