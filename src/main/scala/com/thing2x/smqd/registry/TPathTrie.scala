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

import com.thing2x.smqd.{TName, TNameMultiWildcard, TNameSingleWildcard, TPath}

import scala.annotation.tailrec
import scala.collection.concurrent.TrieMap
import scala.collection.mutable

// 2018. 9. 13. - Created by Kwon, Yeong Eon

/**
  *
  */
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
        contexts.synchronized {
          contexts += context
          contexts.length
        }
      }
      else {
        val current = des.head
        val child = children.synchronized {
          children.get(current) match {
            case Some(kid) =>
              kid
            case _ =>
              val kid = new Node(current)
              children.put(current, kid)
              kid
          }
        }
        child.addDescendants(des.tail, context)
      }
    }

    final def removeDescendants(des: Seq[TName])(contextMatcher: T => Boolean): Int = {
      if (des.isEmpty) {
        contexts.synchronized {
          contexts.find(contextMatcher) match {
            case Some(ctx) => contexts -= ctx
            case _ =>
          }
          contexts.length
        }
      }
      else {
        children.synchronized {
          val current = des.head
          children.get(current) match {
            case Some(child) =>
              val noOfContextsChildHas = child.removeDescendants(des.tail)(contextMatcher)
              if (child.contexts.isEmpty && child.children.isEmpty) {
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
    }

    /**
      * @return 0 >= number of remaining contexts, < 0 means non-existing path
      */
    final def removeDescendants(des: Seq[TName], context: T): Int = {
      if (des.isEmpty) {
        contexts.synchronized {
          contexts -= context
          contexts.length
        }
      }
      else {
        children.synchronized {
          val current = des.head
          children.get(current) match {
            case Some(child) =>
              val noOfContextsChildHas = child.removeDescendants(des.tail, context)
              if (child.contexts.isEmpty && child.children.isEmpty) {
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

