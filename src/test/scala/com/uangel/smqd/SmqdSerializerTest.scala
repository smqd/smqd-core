package com.uangel.smqd

import akka.actor.ActorSystem
import akka.serialization.SerializationExtension
import akka.testkit.{ImplicitSender, TestActors, TestKit}
import com.typesafe.config.ConfigFactory
import io.netty.buffer.ByteBuf
import org.scalatest.{BeforeAndAfterAll, Matchers, WordSpecLike}
import t2x.smqd.{RoutableMessage, TPath, TopicPath}

/**
  * 2018. 6. 15. - Created by Kwon, Yeong Eon
  */
class SmqdSerializerTest extends TestKit(ActorSystem("RegistryPerf", ConfigFactory.load("smqd-ref.conf")))
  with ImplicitSender
  with WordSpecLike
  with Matchers
  with BeforeAndAfterAll {


  val actor = system.actorOf(TestActors.blackholeProps)

  "SmqdSerializer" must {
    val serExt = SerializationExtension(system)

    "RoutableMessage with ByteBuf" in {

      val buf = io.netty.buffer.Unpooled.buffer()
      buf.writeBytes("test message".getBytes("utf-8"))

      val org = RoutableMessage(TopicPath("sender/1/temperature"), buf)
      val ser = serExt.findSerializerFor(org)

      val bytes = ser.toBinary(org)

      val bak = ser.fromBinary(bytes, None)

      assert(bak.isInstanceOf[RoutableMessage])
      val rep = bak.asInstanceOf[RoutableMessage]
      assert(compare(org, rep))

      System.out.println("----Org----")
      System.out.println(io.netty.buffer.ByteBufUtil.prettyHexDump(org.msg.asInstanceOf[ByteBuf]))
      System.out.println("----Rep----")
      System.out.println(io.netty.buffer.ByteBufUtil.prettyHexDump(rep.msg.asInstanceOf[ByteBuf]))
    }

    "RoutableMessage with String" in {
      val org = RoutableMessage(TopicPath("sensor/2/humidity"), "한글 메시지")
      val ser = serExt.findSerializerFor(org)

      val bytes = ser.toBinary(org)

      val bak = ser.fromBinary(bytes, None)

      assert(bak.isInstanceOf[RoutableMessage])
      val rep = bak.asInstanceOf[RoutableMessage]
      assert(compare(org, rep))
    }
  }

  private def compare(org: RoutableMessage, rep: RoutableMessage): Boolean = {
    assert(rep.topicPath == org.topicPath)
    assert(rep.isRetain == org.isRetain)
    assert(rep.msg == org.msg)

    true
  }
}
