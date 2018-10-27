/*
	@Begin
	manage smqd routing

	Syntax
		route <command> <options>

		   command
		     list : list current routing table

		   options
		     -n, --node <node name> : list only contins specified node
		     -t, --topic <topic>    : list routing information that can match with the speicifed topic
	@End
 */

import com.thing2x.smqd.Smqd
import com.thing2x.smqd.net.telnet.ScShell

import scala.annotation.tailrec

val args: Array[String] = $args
val shell: ScShell = $shell
val smqd: Smqd = shell.smqd

var cmd = "list"
var unknownOption = false
var node: Option[String] = None
var topic: Option[String] = None

// parse args
if (args.length > 1) {
  cmd = args(1)
  Route.parseOpt(args.drop(2))
}

if (!unknownOption) {
  // execute command
  cmd match {
    case "list" =>
      Route.list()
    case _ =>
      println(s"Unknown command: $cmd\n")
  }
}

println()

object Route {
  @tailrec
  def parseOpt(params: Array[String]): Unit = {
    if (params.nonEmpty) {
      val next = params.head match {
        case "--node" | "-n" =>
          node = Some(params(1))
          params.drop(2)
        case "--topic" | "-t" =>
          topic = Some(params(1))
          params.drop(2)
        case un =>
          println(s"Unknown option : $un\n")
          unknownOption = true
          return
      }
      parseOpt(next)
    }
  }

  def list(): Unit = {
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
