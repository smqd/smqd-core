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
final class TrieRegistry(smqd: Smqd, debugDump: Boolean) extends Registry with ActorIdentifying {

  private var _callbackManager: ActorRef = _

  override lazy val callbackManager: ActorRef = _callbackManager

  override def callbackManager_=(actor: ActorRef): Unit = _callbackManager = actor

  private val trie: TPathTrie[Registration] = TPathTrie()

  override def subscribe0(reg: Registration): Unit ={
    logger.debug("subscribe0 {}{}", reg.actor.path, if (reg.filterPath == null) "" else ": "+reg.filterPath.toString)

    val nrOfRemains = trie.add(reg.filterPath, reg)
    if (nrOfRemains == 1) { // if it's a new
      smqd.addRoute(reg.filterPath)
    }
    if (debugDump)
      logger.debug(s"\n{}", dump)
  }

  override def unsubscribe0(actor: ActorRef, filterPath: FilterPath = null): Boolean = {
    logger.debug("unsubscribe0 {}{}", actor.path, if (filterPath == null) "" else ": "+filterPath.toString)

    var result = false
    if (filterPath != null) { // filter based unregister
      val nrOfRemains = trie.remove(filterPath){ r => r.actor.path == actor.path}
      if (nrOfRemains == 0)
        smqd.removeRoute(filterPath)

      if (nrOfRemains >= 0) // negative number means non-existing filter path
        result = true
    }
    else { // actor based registration
      val rs = trie.filter(r => r.actor.path == actor.path)
      rs.foreach { r =>
        val noRemains = trie.remove(r.filterPath, r)
        if (noRemains == 0)
          smqd.removeRoute(filterPath)
      }
      result = true
    }

    if (debugDump)
      logger.debug(s"\n{}", dump)
    result
  }

  private def dump: String = {
    val sb = new mutable.StringBuilder()
    trie.dump(sb)
    sb.toString()
  }

  override def filter(topicPath: TopicPath): Seq[Registration] =
    trie.matches(topicPath)

  override def snapshot: Seq[Registration] =
    trie.snapshot
}