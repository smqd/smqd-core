/*
	@Begin
	Show System Informations

	Syntax
		sysinfo thread		Display information for all of threads
	@End
 */

def printGroup(group: ThreadGroup, depth: Int, steps: Array[Int], offset: Int): Unit = {
  val threads = new Array[Thread](group.activeCount())
  val groups = new Array[ThreadGroup](group.activeCount())

  val thCount = group.enumerate(threads, false)
  val grpCount = group.enumerate(groups, false)

  steps(depth) = grpCount

  val sb = new StringBuffer

  for (i <- 0 until depth) {
    if (i == depth - 1)
      sb.append("+--")
    else if (steps(i) > 1)
      sb.append("|  ")
    else
      sb.append("   ")
  }

  val gName = group.getName
  val gDaemon = if (group.isDaemon) "Daemon" else ""

  sb.append(s"+ [$gName] $gDaemon\n")

  for (i <- 0 until thCount) {
    for (n <- 0 until depth) {
      if (steps(n) > 1 && offset < steps(n) - 1)
        sb.append("|  ")
      else
        sb.append("   ")
    }

    if (grpCount > 0)
      sb.append("|")
    else
      sb.append(" ")

    val priority = threads(i).getPriority
    val state = threads(i).getState.toString
    val name = threads(i).getName
    val daemon = if (threads(i).isDaemon)"-Daemon" else ""

    sb.append(s"       [$state, $priority] $name $daemon\n")
  }

  print(sb.toString)

  for (i <- 0 until grpCount) {
    printGroup(groups(i), depth+1, steps, i)
  }
}

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

///////////////// Thread Info ////////////////////
val currentTread = Thread.currentThread()
var group = currentTread.getThreadGroup
var parent: ThreadGroup = group

do {
  parent = group.getParent
  group = if (parent != null) parent else group
} while(parent != null)

val steps = new Array[Int](100)

printGroup(group, 0, steps, 0)

println("")

/////////////// Memory Info /////////////////////
val R: Runtime = Runtime.getRuntime
val pct: Long = (R.totalMemory() - R.freeMemory())*100 / R.totalMemory()

println("Available Processors = "+R.availableProcessors())
println("Max Memory   = "+asSize(R.maxMemory()))
println("Total Memory = "+asSize(R.totalMemory()))
println("Alloc Memory = "+asSize(R.totalMemory() - R.freeMemory())+" ("+pct+"% Used)")
println("")