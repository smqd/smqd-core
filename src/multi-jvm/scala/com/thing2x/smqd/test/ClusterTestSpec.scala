package com.thing2x.smqd.test

import akka.remote.testkit.{MultiNodeSpec, MultiNodeSpecCallbacks}
import org.scalatest.BeforeAndAfterAll
import org.scalatest.matchers.must.Matchers
import org.scalatest.wordspec.AnyWordSpecLike

// 2018. 9. 12. - Created by Kwon, Yeong Eon

/**
  */
trait ClusterTestSpec extends MultiNodeSpecCallbacks with AnyWordSpecLike with Matchers with BeforeAndAfterAll { self: MultiNodeSpec =>

  override def beforeAll(): Unit = multiNodeSpecBeforeAll()

  override def afterAll(): Unit = multiNodeSpecAfterAll()
}
