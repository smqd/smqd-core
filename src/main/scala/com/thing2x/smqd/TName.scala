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

package com.thing2x.smqd

import com.thing2x.smqd.FilterPathPrefix.FilterPathPrefix
import com.typesafe.scalalogging.Logger

import scala.annotation.tailrec
import scala.collection.concurrent.TrieMap
import scala.collection.immutable.HashMap
import scala.collection.mutable

/**
  * 2018. 5. 31. - Created by Kwon, Yeong Eon
  */

sealed abstract class TName(val name: String) extends Serializable {
  def isLocalPrefix: Boolean = name == "$local"
  def isQueuePrefix: Boolean = name == "$queue"
  def isSharePrefix: Boolean = name == "$share"

  override def hashCode: Int = name.hashCode

  override def equals(obj: scala.Any): Boolean = {
    if (!obj.isInstanceOf[TName]){
      false
    }
    else{
      val other = obj.asInstanceOf[TName]
      this.name == other.name
    }
  }
}

class TNameToken(name: String) extends TName(name)

class TNameInvalid(name: String, val reason: String) extends TName(name)

class TNameEmpty extends TName("")

abstract class TNameWildcard(name: String) extends TName(name)

object TNameMultiWildcard extends TNameWildcard("#")

object TNameSingleWildcard extends TNameWildcard("+")

object TName {

  def apply(name: String, isLast: Boolean = false): TName = {
    name match {
      // [MQTT-4.7.1-2] The multi-level wildcard character MUST be specified either on its own or following a topic
      // level seperator. In either case it MUST be the last character specified in the Topic Filter
      case "#" if isLast => TNameMultiWildcard
      case "+" => TNameSingleWildcard
      case "" => new TNameEmpty()
      case _ if name.contains('#') => new TNameInvalid(name, "name contains invalid character(#)")
      case _ if name.contains("+") => new TNameInvalid(name, "name contains invalid character(+)")
      case _ if name.contains("/") => new TNameInvalid(name, "name contains invalid character(/)")
      case _ => new TNameToken(name)
    }
  }

  def parse(path: String): Seq[TName] = {

    def _parse(path: String): Seq[TName] = {
      var offset = 0
      val lastIndex = math.max(path.length - 1, 0)

      path.zipWithIndex.flatMap{ case (ch, idx) =>
        if (ch == '/') {
          val from = offset
          offset = idx + 1
          if (idx == lastIndex) {
            Seq(TName(path.substring(from, idx)), TName(""))
          }
          else {
            Some(TName(path.substring(from, idx)))
          }
        }
        else {
          if (idx == lastIndex) {
            Some(TName(path.substring(offset), isLast = true))
          }
          else {
            None
          }
        }
      }
    }

    if (path == "")
      Seq(new TNameToken(""))
    else if (path.head == '/')
      if (path.length == 1)
        Seq(TName(""), TName(""))
      else
        TName("") +: _parse(path.drop(1))
    else
      _parse(path)
  }
}


trait TPath extends Ordered[TPath]{
  def tokens: Seq[TName]
  override val toString: String = {
    tokens.map(_.name).mkString("/")
  }

  override def hashCode(): Int = {
    toString.hashCode
  }

  override def equals(obj: scala.Any): Boolean = {
    if (!obj.isInstanceOf[TPath]){
      false
    }
    else{
      val other = obj.asInstanceOf[TPath]
      if (tokens.size != other.tokens.size) {
        false
      }
      else {
        !tokens.zip(other.tokens).exists(t => t._1 != t._2)
      }
    }
  }

  override def compare(that: TPath): Int = {
    this.toString.compare(that.toString)
  }
}

object TopicPath {
  def apply(path: String): TopicPath = TPath.parseForTopic(path).get
  def parse(path: String): Option[TopicPath] = TPath.parseForTopic(path)
}

case class TopicPath(tokens: Seq[TName]) extends TPath

object FilterPathPrefix extends Enumeration {
  type FilterPathPrefix = Value

  val NoPrefix: FilterPathPrefix = Value("none")
  val Local: FilterPathPrefix = Value("local")
  val Queue: FilterPathPrefix = Value("queue")
  val Share: FilterPathPrefix = Value("share")
}

import FilterPathPrefix._

object FilterPath {
  def apply(path: String): FilterPath = TPath.parseForFilter(path).get
  def parse(path: String): Option[FilterPath] = TPath.parseForFilter(path)
}

case class FilterPath(tokens: Seq[TName], prefix: FilterPathPrefix = NoPrefix, group: Option[String] = None) extends TPath {
  val isLocal: Boolean = prefix == Local
  val isShared: Boolean = prefix == Queue || prefix == Share

  def matchFor(path: TopicPath): Boolean = {
    var p = path.tokens

    tokens.foreach {
      case t: TNameToken =>
        if (t.name != p.head.name) return false
        p = p.tail

      case TNameMultiWildcard =>
        if (p.nonEmpty) return true

      case TNameSingleWildcard =>
        if (p.isEmpty) return false
        p = p.tail

      case _ =>
        return false
    }

    p.isEmpty
  }

  override val toString: String = {
    val prefixStr = prefix match {
      case Local => "$local/"
      case Queue => "$queue/"
      case Share => "$share/" + group.get + "/"
      case NoPrefix => ""
    }

    tokens.map(_.name).mkString(prefixStr, "/", "")
  }

  def debug: String = {
    val prefixStr = prefix match {
      case Local => "[$local]"
      case Queue => "[$queue]"
      case Share => "[$share (" + group.get + ")]"
      case NoPrefix => "-"
    }

    tokens.map(_.name).mkString(prefixStr, ", ", "")
  }

  def toByteArray: Array[Byte] = {
    this.toString.getBytes("utf-8")
  }

  override def hashCode(): Int = {
    toString.hashCode
  }

  override def equals(obj: scala.Any): Boolean = {
    if (!obj.isInstanceOf[FilterPath]){
      false
    }
    else{
      val other = obj.asInstanceOf[FilterPath]
      if (prefix != other.prefix) {
        false
      }
      else {
        super.equals(other)
      }
    }
  }
}

object TPath {

  def parseForFilter(path: String): Option[FilterPath] = {
    val tokens = TName.parse(path)
    parseForFilter(tokens)
  }

  def parseForFilter(tokens: Seq[TName]): Option[FilterPath] = {
    tokens.foreach {
      case _: TNameInvalid =>
        return None
      case n @ TNameMultiWildcard =>
        // '#' can be only the last place
        if (tokens.indexOf(n) != tokens.length - 1)
          return None
      case _ =>
    }

    // Local Subscription: $local/topic
    //     sub $local/topic
    //     pub topic
    //
    // Queue Subscription (Load balancing)
    //     sub $queue/topic
    //     pub topic
    //
    // Shared Subscription (Load balancing among the same group subscribers
    //     sub $share/<group>/topic
    //     pub topic
    if (tokens.head.isLocalPrefix) {
      Some(FilterPath(tokens.tail, Local))
    }
    else if (tokens.head.isQueuePrefix) {
      Some(FilterPath(tokens.tail, Queue))
    }
    else if (tokens.head.isSharePrefix) {
      val group = tokens.tail.head.name
      Some(FilterPath(tokens.tail.tail, Share, group=Some(group)))
    }
    else {
      Some(FilterPath(tokens, NoPrefix))
    }
  }

  def parseForTopic(path: String): Option[TopicPath] = {
    val tokens = TName.parse(path)
    parseForTopic(tokens)
  }

  def parseForTopic(tokens: Seq[TName]): Option[TopicPath] = {
    // [MQTT-4.7.1-1] The wildcard characters can be used in Topic Filters, but MUST NOT be used within a Topic Name

    tokens.foreach {
      case _: TNameInvalid =>
        return None
      case _: TNameWildcard =>
        return None
      case _ =>
    }
    Some(TopicPath(tokens))
  }
}

object TPathTrie {
  def apply[T]() = new TPathTrie[T]()
}

class TPathTrie[T] {

  private class Node(token: TName) {
    private val children: TrieMap[TName, Node] = TrieMap.empty
    private val contexts: mutable.ListBuffer[T] = mutable.ListBuffer()

    /**
      * @return number of current number of contexts
      */
    @tailrec
    final def addDescendants(des: Seq[TName], context: T): Int = {
      if (des.isEmpty) {
        contexts += context
        contexts.length
      }
      else {
        val current = des.head
        val child = children.get(current) match {
          case Some(kid) =>
            kid
          case _ =>
            val kid = new Node(current)
            children.put(current, kid)
            kid
        }
        child.addDescendants(des.tail, context)
      }
    }

    final def removeDescendants(des: Seq[TName])(contextMatcher: T => Boolean): Int = {
      if (des.isEmpty) {
        contexts.find(contextMatcher) match {
          case Some(ctx) => contexts -= ctx
          case _ =>
        }
        contexts.length
      }
      else {
        val current = des.head
        children.get(current) match {
          case Some(child) =>
            val noOfContextsChildHas = child.removeDescendants(des.tail)(contextMatcher)
            if (noOfContextsChildHas == 0) {
              children.remove(current)
            }
            noOfContextsChildHas
          case _ => // Question: can we silently ignore?
            if (children.isEmpty)
              contexts.length * -1 // ask parent not to remove me, i still have contexts
            else
              children.size * -1  // ask parent not to remove me, i still have children
        }
      }
    }

    /**
      * @return 0 >= number of remaining contexts, < 0 means non-existing path
      */
    final def removeDescendants(des: Seq[TName], context: T): Int = {
      if (des.isEmpty) {
        contexts -= context
        contexts.length
      }
      else {
        val current = des.head
        children.get(current) match {
          case Some(child) =>
            val noOfContextsChildHas = child.removeDescendants(des.tail, context)
            if (noOfContextsChildHas == 0) {
              children.remove(current)
            }
            noOfContextsChildHas
          case _ => // Question: can we silently ignore?
            if (children.isEmpty)
              contexts.length * -1 // ask parent not to remove me, i still have contexts
            else
              children.size * -1  // ask parent not to remove me, i still have children
        }
      }
    }

    /** this method is the key point of performance for delivering published message to subscribers */
    final def matches(des: Seq[TName]): Seq[T] = {
      if (des.isEmpty || this.token == TNameMultiWildcard) {
        children.get(TNameMultiWildcard) match {
          case Some(multi) =>
            // filter that ends with '#'. e.g) filter 'sensor/#' should match with topic 'sensor'
            multi.contexts ++ contexts
          case None =>
            contexts
        }
      }
      else {
        val current = des.head
        val remains = des.tail

        val exact = children.get(current).map(_.matches(remains))
        val multi = children.get(TNameMultiWildcard).map(_.matches(remains))
        val single = children.get(TNameSingleWildcard).map(_.matches(remains))

        (exact :: multi :: single :: Nil).filterNot(_.isEmpty).flatMap(_.get)
      }
    }

    final def snapshot: Seq[T] = {
      Nil ++ contexts.seq ++ children.values.flatMap(_.snapshot)
    }

    final def dump(lvl: Int, sb: StringBuilder): Unit = {
      if (lvl == 0) {
        sb.append("<root>\n")
      }
      else {
        (0 until lvl).foldLeft(sb)((sb, _) => sb.append("  "))
        sb.append("").append(if(token.name.length == 0) "''" else token.name).append("\t")
        if (contexts.nonEmpty) sb.append("=> ")
        contexts.foldLeft(sb)((sb, ctx) => sb.append("[").append(ctx.toString).append("] "))
        sb.append("\n")
      }

      children.foreach{ case (_, child) =>
        child.dump(lvl+1, sb)
      }
    }
  }

  private val root = new Node(TName(""))

  def add(path: TPath, context: T): Int =
    add(path.tokens, context)

  def add(tokens: Seq[TName], context: T): Int =
    root.addDescendants(tokens, context)

  def remove(path: TPath, context: T): Int =
    remove(path.tokens, context)

  def remove(tokens: Seq[TName], context: T): Int =
    root.removeDescendants(tokens, context)

  def remove(path: TPath)(contextMatcher: T => Boolean): Int =
    remove(path.tokens)(contextMatcher)

  def remove(tokens: Seq[TName])(contextMatcher: T => Boolean): Int =
    root.removeDescendants(tokens)(contextMatcher)

  def matches(path: TPath): Seq[T] =
    matches(path.tokens)

  def matches(tokens: Seq[TName]): Seq[T] =
    root.matches(tokens)

  def snapshot: Seq[T] =
    root.snapshot

  def filter(matcher: T => Boolean): Seq[T] =
    snapshot.filter(matcher)

  /** only for debugging purpose */
  def dump(sb: StringBuilder): Unit = {
    root.dump(0, sb)
  }
}
