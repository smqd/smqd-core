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

import com.thing2x.smqd.registry.TPathTrie
import com.thing2x.smqd.{FilterPath, TopicPath}
import com.typesafe.scalalogging.StrictLogging
import org.scalatest.FlatSpec

import scala.util.Random

// 2018. 7. 11. - Created by Kwon, Yeong Eon

class TPathTriePerfTest extends FlatSpec with StrictLogging{
  val trie = TPathTrie[String]()
  val sbuilder = new StringBuilder()
  var savedShape = ""
  var initialCount = 0

  "TPathTrie" should "append children" in {
    trie.add(FilterPath(""), context = "empty")
    trie.add(FilterPath("#"), context = "#")
    trie.add(FilterPath("sensor/+/temp"), context="temp:1")
    trie.add(FilterPath("sensor/+/temp"), context="temp:2")
    trie.add(FilterPath("sensor/+/temp"), context="temp:3")
    trie.add(FilterPath("$queue/sensor/+/temp"), context="temp:q1")
    trie.add(FilterPath("$queue/sensor/+/temp"), context="temp:q2")
    trie.add(FilterPath("sensor/abc/temp"), context="abc:temp")
    trie.add(FilterPath("sensor/abc"), context="abc")
    trie.add(FilterPath("sensor/#"), context="sensor/#")
    trie.add(FilterPath("houses/mine/bedroom/1/humidity"), "roomhum1")
    trie.add(FilterPath("houses/mine/bedroom/2/humidity"), "roomhum2")
    trie.add(FilterPath("houses/mine/bedroom/3/humidity"), "roomhum3")
    trie.add(FilterPath("houses/mine/bedroom/1/temp"), "roomtemp1")
    trie.add(FilterPath("houses/mine/bedroom/2/temp"), "roomtemp2")
    trie.add(FilterPath("houses/mine/bedroom/3/temp"), "roomtemp3")
    trie.add(FilterPath("/buildings/+/maybe"), context="building")
    trie.add(FilterPath("/buildings/+/maybe/#"), context="building-all")
    trie.add(FilterPath("$local/$SYS/protocols/#"), context="protocol")

    initialCount = trie.snapshot.length

    trie.dump(sbuilder)
    savedShape = sbuilder.toString
    //logger.info(s"\n${sbuilder.toString}")
  }

  it should "matches" in {
    var m1 = trie.matches(TopicPath("sensor/1/temp"))
    assert(m1.toSet == Set("#", "temp:1", "temp:2", "temp:3", "temp:q1", "temp:q2", "sensor/#"))

    m1 = trie.matches(TopicPath("sensor/1/xyz"))
    assert(m1.toSet == Set("#", "sensor/#"))

    m1 = trie.matches(TopicPath("/builds/1"))
    assert(m1.toSet == Set("#"))

    m1 = trie.matches(TopicPath("/buildings/1/maybe"))
    assert(m1.toSet == Set("#", "building", "building-all"))

    m1 = trie.matches(TopicPath("/buildings/1/maybe/abc/xyz"))
    assert(m1.toSet == Set("#", "building-all"))

    m1 = trie.matches(TopicPath("$SYS/protocols"))
    assert(m1.toSet == Set("#", "protocol"))
  }

  def time[T](str: String)(thunk: => T): T = {
    val t1 = System.currentTimeMillis
    val x = thunk
    val t2 = System.currentTimeMillis
    logger.info(str + "... " + (t2 - t1) + " ms.")
    x
  }

  /* exclude perf test case it is deadly slower when you run it by 'sbt run', but it works fine on intellij */
//  val groupRange = 1000
//  val filterRange = 1000
//  val repeats = 1000000
  val groupRange = 100
  val filterRange = 100
  val repeats = 10000

  it should "handle massive filters" in {

    time(s"insert $groupRange topics that have $filterRange filters") {
      for {
        g <- 1 to groupRange
        n <- 1 to filterRange
      } {
        trie.add(FilterPath(s"massive/sensors/$g/$n/#"), s"massive-$g-$n")
      }
    }

    val random = Random.javaRandomToRandom(new java.util.Random())
    time(s"random matches filter $repeats times") {
      1 to repeats foreach { _ =>
        val g = random.nextInt(groupRange) + 1
        val n = random.nextInt(filterRange) + 1
        trie.matches(TopicPath(s"massive/sensors/$g/$n/temp"))
      }
    }

    time(s"insert filters ${groupRange * filterRange}") {
      1 to filterRange * groupRange foreach { i =>
        trie.add(FilterPath(s"massive/devices/$i/#"), s"device-$i")
      }
    }

    time("one time filter") {
      val m1 = trie.matches(TopicPath(s"massive/devices/100/temp"))
      assert(m1.toSet == Set("#", s"device-100"))
    }

    time(s"random matches filter $repeats times") {
      1 to repeats foreach { _ =>
        val n = random.nextInt(filterRange) + 1
        val m1 = trie.matches(TopicPath(s"massive/devices/$n/temp"))
        assert(m1.toSet == Set("#", s"device-$n"))
      }
    }
  }

  it should "count snapshot" in {
    time("count total filters with snapshot") {
      val s = trie.snapshot
      assert(s.length == (groupRange * filterRange)*2 + initialCount)
      //logger.info(s"===========+> ${s.length}")
    }
  }

  it should "handle massive removes" in {
    time(s"remove $groupRange topics that have $filterRange filters") {
      for {
        g <- 1 to groupRange
        n <- 1 to filterRange
      } {
        trie.remove(FilterPath(s"massive/sensors/$g/$n/#"), s"massive-$g-$n")
      }
    }

    time(s"remove filters ${groupRange * filterRange}") {
      1 to filterRange * groupRange foreach { i =>
        trie.remove(FilterPath(s"massive/devices/$i/#"), s"device-$i")
      }
    }

    val s = trie.snapshot
    assert(s.length == initialCount)

    sbuilder.clear()
    trie.dump(sbuilder)
    val currentShape = sbuilder.toString
    //logger.info(s"\n{}", currentShape)
    assert(savedShape == currentShape)
  }

  it should "take snapshot" in {
    trie.snapshot
  }
}
