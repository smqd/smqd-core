/*
	@Begin
  manage smqd routing

  Usage: route [list] [options]

  Command: list [options]
    print current routes settings
      -n, --node <value>      list routes that contains specific node
      -t, --topic <value>     list routes that match for the speicific topic
	@End
 */

import com.thing2x.smqd.Smqd
import com.thing2x.smqd.net.telnet.ScShell

val args: Array[String] = $args
val shell: ScShell = $shell
val smqd: Smqd = shell.smqd

case class Setting(command: String = "", node: Option[String] = None, topic: Option[String] = None)

val parser = new scopt.OptionParser[Setting]("route") {
  override def terminate(exitState: Either[String, Unit]): Unit = Unit

  head(" ")

  help("help").text("prints this message")

  cmd("list").action( (_, s) => s.copy(command = "list") ).text("print current routes settings").children(
    opt[String]('n', "node").action( (x, s) => s.copy(node = Option(x)) ).text("list routes that contains specific node"),
    opt[String]('t', "topic").action( (x, s) => s.copy(topic = Option(x)) ).text("list routes that match for the speicific topic")
  )
}

parser.parse(args.tail, Setting()) match {
  case Some(setting) =>
    setting.command match {
      case "list" =>
        println()
        Route.list(setting.node, setting.topic)
      case _ =>
    }

  case None =>
}

println()

object Route {
  def list(node: Option[String], topic: Option[String]): Unit = {
    smqd.snapshotRoutes
      // check if routes contains the specified node
      .filter(s => if (node.isDefined) s._2.exists(_.nodeName == node.get) else true)
      // check if routes matched with the specified topic
      .filter(s => if (topic.isDefined) s._1.matchFor(topic.get) else true)
      // then print out
      .foreach {
      case (filter, routes) =>
        println(f"${filter.toString}%-25s -->  ${routes.map(_.nodeName).mkString(", ")}")
      case _ =>
        println("Internal error")
    }
  }
}
