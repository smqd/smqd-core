/*
	@Begin
	Show content of file
	
	Syntax
		cat <option> <file name>
		
		-n  number all output lines
	@End
 */


import java.io.File

import com.thing2x.smqd.net.telnet.ScShell

import scala.io.Source

val args: Array[String] = $args
val shell: ScShell = $shell

if (args.length < 2) {
	println("refer : help cat\r\n");
}
else {

	var fileName: String = null
	var number: Boolean = false

	args.foreach{ opt =>
		if (opt == "-n")
			number = true
		else
			fileName = opt
	}

	val  realPath = shell.getRealPath(fileName)

	val ff = new File(realPath)
	if (!ff.exists || !ff.canRead) {
		println(s"Access failure $fileName\n")
	}
	else {
		val s = Source.fromFile(ff, "utf-8")
		val lines = if (number) {
			s.getLines.zipWithIndex.map{case (line, idx) =>	f"${idx+1}%04d  $line"}
		}
		else {
			s.getLines
		}

		println(lines.mkString("\n"))
		s.close()
	}
}