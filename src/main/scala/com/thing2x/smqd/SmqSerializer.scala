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

import java.io.{ByteArrayInputStream, ByteArrayOutputStream, ObjectInputStream, ObjectOutputStream}
import java.nio.charset.Charset
import akka.serialization.Serializer
import com.typesafe.scalalogging.StrictLogging
import io.netty.buffer.ByteBuf

// 2018. 6. 15. - Created by Kwon, Yeong Eon

/**
  */
class SmqSerializer extends Serializer with StrictLogging {
  override val identifier: Int = 0xdead + 1

  override val includeManifest: Boolean = false

  override def toBinary(o: AnyRef): Array[Byte] = o match {

    case m: RoutableMessage =>
      val buf = io.netty.buffer.Unpooled.buffer()

      val path: CharSequence = m.topicPath.toString
      val pathLen: Int = path.length

      buf.writeByte('M')
      buf.writeInt(pathLen)
      buf.writeCharSequence(path, Charset.forName("utf-8"))
      buf.writeBoolean(m.isRetain)

      m.msg match {
        case bb: ByteBuf =>
          buf.writeByte('b')
          buf.writeInt(bb.readableBytes)
          bb.markReaderIndex()
          buf.writeBytes(bb)
          bb.resetReaderIndex()

        case arr: Array[Byte] =>
          buf.writeByte('a')
          buf.writeInt(arr.length)
          buf.writeBytes(arr)

        case str: String =>
          buf.writeByte('s')
          val data = str.getBytes("utf-8")
          buf.writeInt(data.length)
          buf.writeBytes(data)

        case obj: AnyRef =>
          val baos = new ByteArrayOutputStream()
          val out = new ObjectOutputStream(baos)
          out.writeObject(obj)
          val arr = baos.toByteArray
          baos.close()

          buf.writeByte('j')
          buf.writeInt(arr.length)
          buf.writeBytes(arr)

        case other =>
          throw new RuntimeException(s"Unsupported payload: ${other.getClass.getCanonicalName}")
      }

      val arr = buf.array()
      buf.release()
      arr

    case m: ByteBuf =>
      val buf = io.netty.buffer.Unpooled.buffer()
      m.markReaderIndex()
      buf.writeByte('B')
      buf.writeInt(m.readableBytes)
      buf.writeBytes(m)
      m.resetReaderIndex()
      val arr = buf.array()
      buf.release()
      arr

    case f: FilterPath =>
      val buf = io.netty.buffer.Unpooled.buffer()
      buf.writeByte('F')
      val ba = f.toByteArray
      buf.writeInt(ba.length)
      buf.writeBytes(ba)
      val arr = buf.array()
      buf.release()
      arr

    case other => throw new IllegalArgumentException(s"${getClass.getName} only serializes RoutedMessage, not [${other.getClass.getName}]")
  }

  override def fromBinary(bytes: Array[Byte], manifest: Option[Class[_]]): AnyRef = {

    val buf = io.netty.buffer.Unpooled.wrappedBuffer(bytes)

    val rt = buf.readByte() match {
      case 'M' => // RoutableMessage
        val pathLen = buf.readInt()
        val path = buf.readCharSequence(pathLen, Charset.forName("utf-8")).toString
        val isRetain = buf.readBoolean()
        val payload =
          buf.readByte() match {
            case 'b' => // ByteBuf
              val dataLen = buf.readInt()
              val data = buf.readBytes(dataLen)
              data
            case 'a' => // Array[Byte]
              val dataLen = buf.readInt()
              val data = new Array[Byte](dataLen)
              buf.readBytes(data)
              data
            case 's' => // String
              val dataLen = buf.readInt()
              val data = new Array[Byte](dataLen)
              buf.readBytes(data)
              new String(data, "utf-8")
            case 'j' => // POJO
              val dataLen = buf.readInt()
              val data = new Array[Byte](dataLen)
              buf.readBytes(data)
              val oin = new ObjectInputStream(new ByteArrayInputStream(data))
              val obj = oin.readObject()
              obj
          }

        // this message is traveling through network, so set isLcoal false
        RoutableMessage(TopicPath.parse(path).get, payload, isRetain, isLocal = false)
      case 'B' => // ByteBuf
        val len = buf.readInt()
        val data = buf.readBytes(len)
        io.netty.buffer.Unpooled.wrappedBuffer(data)

      case 'F' => // FilterPath
        val len = buf.readInt()
        val str = buf.readCharSequence(len, Charset.forName("utf-8")).toString
        FilterPath(str)
    }
    buf.release()
    rt
  }
}
