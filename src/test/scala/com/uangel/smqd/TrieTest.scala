package t2x.smqd

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
