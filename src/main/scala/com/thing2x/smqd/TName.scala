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

class TNameMultiWildcard extends TNameWildcard("#")

class TNameSingleWildcard extends TNameWildcard("+")

object TName {

  def apply(name: String, isLast: Boolean = false): TName = {
    name match {
      // [MQTT-4.7.1-2] The multi-level wildcard character MUST be specified either on its own or following a topic
      // level seperator. In either case it MUST be the last character specified in the Topic Filter
      case "#" if isLast => new TNameMultiWildcard()
      case "+" => new TNameSingleWildcard()
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


trait TPath {
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

      case _: TNameMultiWildcard =>
        if (p.nonEmpty) return true

      case _: TNameSingleWildcard =>
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
      case n: TNameMultiWildcard =>
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