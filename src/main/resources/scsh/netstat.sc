/*
	@Begin
	Show network interface status

	Syntax
		netstat
	@End
 */

import java.net.NetworkInterface
import scala.jdk.CollectionConverters._

NetworkInterface.getNetworkInterfaces.asScala.foreach { ni =>
  println(s"  ${ni.getName} : ${ni.getDisplayName}")
  ni.getInetAddresses.asScala.foreach { ia =>
    println(s"        ${ia.getHostAddress}")
  }
  println("")
}
