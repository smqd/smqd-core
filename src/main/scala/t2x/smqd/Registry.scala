package t2x.smqd

import akka.actor.{ActorRef, ActorSystem}
import com.typesafe.scalalogging.StrictLogging
import t2x.smqd.QoS._
import t2x.smqd.RegistryCallbackManagerActor.{CreateCallback, CreateCallbackPF}
import t2x.smqd.replica.ReplicationActor
import t2x.smqd.session.SessionId
import t2x.smqd.util.ActorIdentifying

import scala.collection.mutable
import scala.concurrent.{Await, ExecutionContext, Future}

/**
  * 2018. 6. 3. - Created by Kwon, Yeong Eon
  */

trait Registry {
  def subscribe(filterPath: FilterPath, actor: ActorRef, sessionId: Option[SessionId] = None, qos: QoS = QoS.AtMostOnce): QoS
  def unsubscribe(filterPath: FilterPath, actor: ActorRef): Boolean
  def unsubscribeAll(actor: ActorRef): Boolean
  def filter(topicPath: TopicPath): Seq[Registration]

  def subscribe(filterPath: FilterPath, callback: (TopicPath, Any) => Unit): ActorRef
  def subscribe(filterPath: FilterPath)(callback: PartialFunction[(TopicPath, Any), Unit]): ActorRef
}

case class Registration(filterPath: FilterPath, qos: QoS, actor: ActorRef, sessionId: Option[SessionId]) {
  override def toString = s"${filterPath.toString} ($qos) => ${actor.path.toString}"
}

abstract class AbstractRegistry(system: ActorSystem) extends Registry with ActorIdentifying with StrictLogging {

  def subscribe(filterPath: FilterPath, actor: ActorRef, sessionId: Option[SessionId] = None, qos: QoS = QoS.AtMostOnce): QoS = {
    subscribe0(Registration(filterPath, qos, actor, sessionId))
  }

  def unsubscribe(filterPath: FilterPath, actor: ActorRef): Boolean = {
    unsubscribe0(actor, filterPath)
  }

  def unsubscribeAll(actor: ActorRef): Boolean = {
    unsubscribe0(actor)
  }

  protected def subscribe0(reg: Registration): QoS
  protected def unsubscribe0(actor: ActorRef, filterPath: FilterPath = null): Boolean

  import akka.pattern.ask
  import akka.util.Timeout

  import scala.concurrent.duration._
  import scala.language.postfixOps

  private lazy val callbackManager = identifyActor(manager(RegistryCallbackManagerActor.actorName))(system)
  private implicit val ec: ExecutionContext = system.dispatchers.defaultGlobalDispatcher
  private implicit val timeout: Timeout = 1 second

  def subscribe(filterPath: FilterPath, callback: (TopicPath, Any) => Unit): ActorRef = {
    val f = callbackManager ? CreateCallback(callback)
    val actor = Await.result(f, timeout.duration).asInstanceOf[ActorRef]
    subscribe(filterPath, actor)
    actor
  }

  def subscribe(filterPath: FilterPath)(callback: PartialFunction[(TopicPath, Any), Unit]): ActorRef = {
    val f = callbackManager ? CreateCallbackPF(callback)
    val actor = Await.result(f, timeout.duration).asInstanceOf[ActorRef]
    subscribe(filterPath, actor)
    actor
  }
}

trait RegistryDelegate {
  def allowSubscribe(filterPath: FilterPath, sessionId: SessionId, userName: Option[String]): Future[Boolean]
  def allowPublish(topicPath: TopicPath, sessionId: SessionId, userName: Option[String]): Future[Boolean]
}

trait HashMapRegistry extends StrictLogging {
  protected def registry: mutable.HashMap[FilterPath, List[Registration]]

  def addRoute(filterPath: FilterPath): Unit = ???
  def removeRoute(filterPath: FilterPath): Unit = ???

  def subscribe0(reg: Registration): QoS = {
    //logger.debug("subscribe0 {}{}", reg.actor.path, if (reg.filterPath == null) "" else ": "+reg.filterPath.toString)
    synchronized {
      registry.get(reg.filterPath) match {
        case Some(list) =>
          registry.put(reg.filterPath, reg :: list)
        case None =>
          registry.put(reg.filterPath, List(reg))
          addRoute(reg.filterPath)
      }
      // logger.debug(dump)
      reg.qos
    }
  }

  def unsubscribe0(actor: ActorRef, filterPath: FilterPath = null): Boolean = {
    //logger.debug("unsubscribe0 {}{}", actor.path, if (filterPath == null) "" else ": "+filterPath.toString)
    var result = false
    synchronized {
      // filter based unregister
      if (filterPath != null) {
        val entries = registry.get(filterPath)

        entries match {
          case Some(list) =>
            val updated = list.filterNot( _.actor.path == actor.path )
            if (updated.size != list.size) {
              result = true

              registry.put(filterPath, updated)
            }
          case None =>
        }
      }
      // actor based unregister
      else {
        registry.foreach { case (filterPath, list) =>
          val updated = list.filterNot( _.actor.path == actor.path )
          if (updated.isEmpty) {
            registry.remove(filterPath)
            // this topic filter do not have any subscriber anymore, so ask cluster not to route publish message
            removeRoute(filterPath)
            result = true
          }
          else if (updated.size != list.size) {
            registry.put(filterPath, updated)
            result = true
          }
        }
      }
    }
    // logger.debug(dump)
    result
  }

  private def dump: String = {
    registry.map { case (path, list) =>
      path.toString +list.map(_.actor.path.toString).mkString("\n              ", "\n              ", "")
    }.mkString("\nRegistry Dump\n      ", "\n      ", "\n")
  }

  def filter(topicPath: TopicPath): Seq[Registration] = {
    synchronized {
      registry.filter { case (filterPath, list) =>
        filterPath.matchFor(topicPath)
      }.flatMap { case (filterPath, list) =>
        list
      }.toList
    }
  }
}

final class LocalModeRegistry(system: ActorSystem) extends AbstractRegistry(system) with HashMapRegistry {
  protected val registry: mutable.HashMap[FilterPath, List[Registration]] = mutable.HashMap[FilterPath, List[Registration]]()
  override def addRoute(filterPath: FilterPath): Unit = Unit
  override def removeRoute(filterPath: FilterPath): Unit = Unit
}

final class ClusterModeRegistry(system: ActorSystem) extends AbstractRegistry(system) with ActorIdentifying with HashMapRegistry {
  protected val registry: mutable.HashMap[FilterPath, List[Registration]] = mutable.HashMap[FilterPath, List[Registration]]()

  private lazy val ddManager: ActorRef = identifyActor(manager(ReplicationActor.actorName))(system)

  override def addRoute(filterPath: FilterPath): Unit = ddManager ! ReplicationActor.AddRoute(filterPath)
  override def removeRoute(filterPath: FilterPath): Unit = ddManager ! ReplicationActor.RemoveRoute(filterPath)
}

