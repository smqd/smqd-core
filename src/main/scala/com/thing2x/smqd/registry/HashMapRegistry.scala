// Copyright 2018 UANGEL
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//     http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package com.thing2x.smqd.registry

import akka.actor.ActorRef
import com.thing2x.smqd.util.ActorIdentifying
import com.thing2x.smqd.{FilterPath, Smqd, TopicPath}

import scala.collection.mutable

// 2018. 9. 13. - Created by Kwon, Yeong Eon

/**
  *
  */
final class HashMapRegistry(smqd: Smqd, debugDump: Boolean) extends Registry with ActorIdentifying {

  private var _callbackManager: ActorRef = _

  override lazy val callbackManager: ActorRef = _callbackManager

  override def callbackManager_=(actor: ActorRef): Unit = _callbackManager = actor

  protected val registry: mutable.HashMap[FilterPath, Set[Registration]] = mutable.HashMap[FilterPath, Set[Registration]]()

  override def subscribe0(reg: Registration): Unit = {
    //logger.debug("subscribe0 {}{}", reg.actor.path, if (reg.filterPath == null) "" else ": "+reg.filterPath.toString)
    synchronized {
      registry.get(reg.filterPath) match {
        case Some(list) =>
          registry.put(reg.filterPath, list + reg)
        case None =>
          registry.put(reg.filterPath, Set(reg))
          // a fresh new filter registration, so it requires local-node be in routes for cluster-wise delivery
          smqd.addRoute(reg.filterPath)
      }
      if (debugDump)
        logger.debug(dump)
    }
  }

  override def unsubscribe0(actor: ActorRef, filterPath: FilterPath = null): Boolean = {
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
            smqd.removeRoute(filterPath)
            result = true
          }
          else if (updated.size != list.size) {
            registry.put(filterPath, updated)
            result = true
          }
        }
      }
    }
    if (debugDump)
      logger.debug(dump)
    result
  }

  private def dump: String = {
    registry.map { case (path, list) =>
      path.toString +list.map(_.actor.path.toString).mkString("\n              ", "\n              ", "")
    }.mkString("\nRegistry Dump\n      ", "\n      ", "\n")
  }

  override def filter(topicPath: TopicPath): Seq[Registration] = {
    synchronized {
      registry.filter { case (filterPath, list) =>
        filterPath.matchFor(topicPath)
      }.flatMap { case (filterPath, list) =>
        list
      }.toList
    }
  }

  override def snapshot: Seq[Registration] = {
    registry.values.flatten.toSeq
  }
}
