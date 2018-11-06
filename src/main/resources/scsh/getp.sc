
/*
	@Begin
		Display System properties
	Syntax
		getp <property_name> Display specified system property's value
		getp all Display all system proeprties
	@End
 */

import java.util.Properties

import com.thing2x.smqd.net.telnet.ScShell

import scala.collection.JavaConverters._

val args: Array[String] = $args

def printkv(k: String, v: String) = {
  var r = v
  r = r.replaceAll("\\n", "\\\\n")
  r = r.replaceAll("\\r", "\\\\r")
  println(s"$k = $r")
}

if (args.length != 2) {
  println("Usage: getp <property_name> | all")
}
else {
  val param = args(1)

  if ("all".equalsIgnoreCase(param)) {
    val prop: Properties = System.getProperties
    val arr: Seq[String] = prop.propertyNames().asScala.map(_.toString).toSeq.sorted

    //println(a)
    arr.foreach { k =>
      printkv(k, System.getProperty(k, "<null>"))
    }
  }
  else {
    printkv(param, System.getProperty(param, "<null>"))
  }
}

