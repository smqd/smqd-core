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

package com.thing2x.smqd.trie

import scala.annotation.tailrec
import scala.collection.concurrent.TrieMap
import scala.collection.mutable

/**
  * 2018. 5. 31. - Created by Kwon, Yeong Eon
  */
sealed trait Trie extends Traversable[String] {
  def append(key: String): Unit
  def findByPrefix(prefix: String): Seq[String]
  def contains(word: String): Boolean
  def remove(word: String): Boolean
}

private[trie] class TrieNode( val char: Option[Char] = None,
                              var word: Option[String] = None) extends Trie {
  private[trie] val children: mutable.Map[Char, TrieNode] = new TrieMap[Char, TrieNode]()

  override def append(key: String): Unit = {
    @tailrec
    def appendHelper(node: TrieNode, currentIndex: Int): Unit = {
      if (currentIndex == key.length) {
        node.word = Some(key)
      }
      else {
        val char = key.charAt(currentIndex).toLower
        val result = node.children.getOrElseUpdate(char, { new TrieNode(Some(char)) })

        appendHelper(result, currentIndex + 1)
      }
    }
    appendHelper(this, 0)
  }

  override def findByPrefix(prefix: String): Seq[String] = ???

  override def contains(word: String): Boolean = ???

  override def remove(word: String): Boolean = ???

  override def foreach[U](f: String => U): Unit = ???
}