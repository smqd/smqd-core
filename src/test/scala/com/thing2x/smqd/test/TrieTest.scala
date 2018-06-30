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

import scala.collection.concurrent.TrieMap

/**
  * 2018. 5. 31. - Created by Kwon, Yeong Eon
  */
class TrieTest extends FlatSpec {

  val map = TrieMap[String, Int]()

  "TrieTest" should "create zero size" in {
    assert(map.isEmpty)
  }

  it should "insert new node" in {

  }
}
