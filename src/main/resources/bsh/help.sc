/*
	@Begin
	Print help message
	
	Syntax
		help             show all available bsh command list
		help command     show specified command's help message
	@End
 */

import java.io.BufferedReader

import com.thing2x.smqd.Smqd
import com.thing2x.smqd.net.telnet.BshShell

val args: Array[String] = $ctx.ARGS.asInstanceOf[Array[String]]
val shell: BshShell = $ctx.SHELL.asInstanceOf[BshShell]
val smqd: Smqd = $ctx.SMQD.asInstanceOf[Smqd]

if (args.length != 2) {
  val list = shell.findAllBshFiles("*.sc")

  val sb = new StringBuffer("--- Available commands ---\n")
  list.map(_.getName).foreach{ name =>
    sb.append(s"    ${name.substring(0, name.length()-3)}\n")
  }
  print(sb.toString)
}
else
{
  val cmd = args(1)
  val cmdPath = cmd + ".sc"

  val in = shell.loadBshFile(cmdPath)
  if (in == null) {
    println("Command not found : "+cmd+"\n")
  }
  else {
    val reader = new BufferedReader(in)
    var line: String = null
    val sb = new StringBuffer()
    var onFlag = false
    var loop = true

    do {
      line = reader.readLine()
      if (line == null){
        loop = false
      }
      else {
        if (line.indexOf("@Begin") >= 0)
        {
          onFlag = true
        }
        else if (line.indexOf("@End") >= 0)
        {
          onFlag = false
          loop = false
        }
        else if (onFlag){
          line = line.replaceAll("[\t]", "    ");
          sb.append(line).append("\r\n");
        }
      }
    } while (loop)

    in.close()

    println("")
    println(sb.toString)
  }
}
