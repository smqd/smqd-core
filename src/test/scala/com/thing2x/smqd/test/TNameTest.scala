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

package com.thing2x.smqd.test

import org.scalatest.FlatSpec
import com.thing2x.smqd.{FilterPath, FilterPathPrefix, TName, TNameInvalid, TNameMultiWildcard, TNameSingleWildcard, TPath, TopicPath}

/**
  * 2018. 5. 31. - Created by Kwon, Yeong Eon
  */
class TNameTest extends FlatSpec {

  "TName and TPath" should "empty" in {
    val ns = TName.parse("")
    assert(ns.size == 1)
    assert(ns.head.name == "")
  }

  it should "/" in {
    val ns = TName.parse("/")
    assert(ns.size == 2)
    assert(ns(0).name == "")
    assert(ns(1).name == "")
  }

  it should "//" in {
    val ns = TName.parse("//")
    assert(ns.size == 3)
    assert(ns(0).name == "")
    assert(ns(1).name == "")
    assert(ns(2).name == "")

    val fp = TPath.parseForFilter(ns)
    assert(fp.isDefined)
    assert(fp.get.toString == "//")
  }

  it should "one" in {
    val ns = TName.parse("one")
    assert(ns.size == 1)
    assert(ns(0).name == "one")
  }

  it should "one/" in {
    val ns = TName.parse("one/")
    assert(ns.size == 2)
    assert(ns(0).name == "one")
    assert(ns(1).name == "")
  }

  it should "/one" in {
    val ns = TName.parse("/one")
    assert(ns.size == 2)
    assert(ns.head.name == "")
    assert(ns.last.name == "one")
  }

  it should "one/two/three" in {
    val ns = TName.parse("one/two/three")
    assert(ns.size == 3)
    assert(ns(0).name == "one")
    assert(ns(1).name == "two")
    assert(ns(2).name == "three")
  }

  it should "one/two/three/" in {
    val ns = TName.parse("one/two/three/")
    assert(ns.size == 4)
    assert(ns(0).name == "one")
    assert(ns(1).name == "two")
    assert(ns(2).name == "three")
    assert(ns(3).name == "")
  }

  it should "/one/two/three/" in {
    val ns = TName.parse("/one/two/three/")
    assert(ns.size == 5)
    assert(ns(0).name == "")
    assert(ns(1).name == "one")
    assert(ns(2).name == "two")
    assert(ns(3).name == "three")
    assert(ns(4).name == "")
  }

  it should "one//three/" in {
    val ns = TName.parse("one//three/")
    assert(ns.size == 4)
    assert(ns(0).name == "one")
    assert(ns(1).name == "")
    assert(ns(2).name == "three")
    assert(ns(3).name == "")
  }

  it should "#" in {
    val ns = TName.parse("#")
    assert(ns.size == 1)
    assert(ns(0).name == "#")
  }

  it should "one/two/#" in {
    val ns = TName.parse("one/two/#")
    assert(ns.size == 3)
    assert(ns(2) == TNameMultiWildcard)

    val tp = TPath.parseForTopic(ns)
    assert(tp.isEmpty)

    val fl = TPath.parseForFilter(ns)
    assert(fl.isDefined)
  }

  it should "one/+/#" in {
    val path = "one/+/#"
    val ns = TName.parse(path)
    assert(ns.size == 3)
    assert(ns(1) == TNameSingleWildcard)
    assert(ns(2) == TNameMultiWildcard)

    val tp = TPath.parseForTopic(ns)
    assert(tp.isEmpty)

    val fl = TPath.parseForFilter(ns)
    assert(fl.isDefined)
    assert(fl.get.toString == path)
  }

  it should "one/#/" in {
    val ns = TName.parse("one/#/")
    assert(ns.size == 3)
    assert(ns(1).isInstanceOf[TNameInvalid])

    val tp = TPath.parseForTopic(ns)
    assert(tp.isEmpty)

    val fl = TPath.parseForFilter(ns)
    assert(fl.isEmpty)
  }

  it should "+/+" in {
    val ns = TName.parse("+/+")
    assert(ns.size == 2)
    assert(ns(0) == TNameSingleWildcard)
    assert(ns(1) == TNameSingleWildcard)

    val fl = TPath.parseForFilter(ns)
    assert(fl.isDefined)
  }

  "Local subscription path" should "parse" in {
    val f = FilterPath("$local/topic")
    assert(f.prefix == FilterPathPrefix.Local, "should be local")

    val t = TopicPath("topic")
    assert(f.matchFor(t), "local match")

    assert(f.toString == "$local/topic")
  }

  "Local subscription path with $SYS" should "parse" in {
    val f = FilterPath("$local/$SYS/topic")
    assert(f.prefix == FilterPathPrefix.Local, "should be local")

    val t = TopicPath("$SYS/topic")

    assert(f.matchFor(t), "local $SYS match")
    assert(f.toString == "$local/$SYS/topic")
  }

  "Queue subscription path" should "parse" in {
    val f = FilterPath("$queue/topic")
    assert(f.prefix == FilterPathPrefix.Queue, "should be queue")
    assert(f.tokens.head.name == "topic")

    val t = TopicPath("topic")
    assert(f.matchFor(t), "queue match")
    assert(f.toString == "$queue/topic")
  }

  "Queue subscription path with wildcard" should "parse" in {
    val f = FilterPath("$queue/topic/+/temp")
    assert(f.prefix == FilterPathPrefix.Queue, "should be queue")

    var tokens = f.tokens
    assert(tokens.head.name == "topic")
    tokens = tokens.tail

    assert(tokens.head.name == "+")
    assert(tokens.head == TNameSingleWildcard)

    tokens = tokens.tail
    assert(tokens.head.name == "temp")

    val t = TopicPath("topic/abc100/temp")
    assert(f.matchFor(t), "queue match")
    assert(f.toString == "$queue/topic/+/temp")
  }

  "Shared subscription path" should "parse" in {
    val f = FilterPath("$share/group/topic")
    assert(f.prefix == FilterPathPrefix.Share, "should be share")
    assert(f.group.isDefined)
    assert(f.group.get == "group")

    val t = TopicPath("topic")

    assert(f.matchFor(t), "share match")
    assert(f.toString == "$share/group/topic")
  }

  "Implicit conversion from string" should "parse" in {
    val fp: FilterPath = "$share/groups/sensors/+/works/#"

    val tp: TopicPath = "sensors/123/works/129"
  }
}
