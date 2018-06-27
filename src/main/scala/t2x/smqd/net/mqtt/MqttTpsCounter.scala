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

package t2x.smqd.net.mqtt

import java.util.concurrent.atomic.AtomicLong

import com.typesafe.scalalogging.StrictLogging
import io.netty.buffer.{ByteBuf, ByteBufHolder}
import io.netty.channel.ChannelHandler.Sharable
import io.netty.channel.{ChannelDuplexHandler, ChannelHandlerContext, ChannelPromise}
import io.netty.handler.codec.mqtt.MqttConnectMessage
import io.netty.util.ReferenceCountUtil

/**
  * 2018. 5. 29. - Created by Kwon, Yeong Eon
  */
object MqttTpsCounter {
  def apply(enableThrottling: Boolean, readLimit: Long, checkInterval: Long) = new MqttTpsCounter(enableThrottling, readLimit, checkInterval)
}

@Sharable
class MqttTpsCounter(enableThrottling: Boolean, readLimit: Long, checkInterval: Long)
  extends MqttTrafficCounterBase(enableThrottling, readLimit, "tps", 1000) {

  override def calculateSize(msg: Any): Long = 1
}

class MqttTrafficCountEvent(val msg: String)

abstract class MqttTrafficCounterBase( enableThrottling: Boolean,
                                       var readLimit: Long,
                                       val unit: String,
                                       var checkInterval: Long = 1000 )
  extends ChannelDuplexHandler with StrictLogging {

  private val lastCheckTime = new AtomicLong(System.currentTimeMillis())

  private val readCounter = new AtomicLong()
  private val discardCounter = new AtomicLong()

  protected var isSuspended = false
  private var tpsHistory = List[Long]()

  override def channelRead(ctx: ChannelHandlerContext, msg: Any): Unit = {

    val metrics = ctx.channel.attr(ATTR_METRICS).get
    metrics.received.inc()

    if (!enableThrottling) {
      super.channelRead(ctx, msg)
      return
    }

    val calculatedSize = calculateSize(msg)
    val currentTime = System.currentTimeMillis()
    val lastTime = lastCheckTime.get()
    val durationTime = currentTime - lastTime

    if (durationTime >= checkInterval) {
      if(lastCheckTime.compareAndSet(lastTime, currentTime)) {
        val count = readCounter.getAndSet(0)
        val discardedCount = discardCounter.getAndSet(0)

        val tps = count * 1000 / durationTime

        val samples = 10
        tpsHistory = (tpsHistory :+ count).takeRight(samples)
        val total = tpsHistory.sum
        val tpsMid = (total * 1000 / (checkInterval * samples)).toInt

        val previsousSuspend = isSuspended
        // checkInterval동안 단기 suspended 상태 적용 여부
        isSuspended = readLimit > 0 && tpsMid > readLimit

        val stat = f"$unit: $tps%4d : $tpsMid%-4d  ${if (previsousSuspend) "S" else "-"} read: $count%-4d  discard: $discardedCount%-4d"

        if (discardedCount > 0) {
          //          faultActor ! FaultType.faultOverTpsCount(discardedCount, readLimit)
          logger.warn(stat)
        } else {
          logger.debug(stat)
        }
      }
      // ctx.fireUserEventTriggered(new MqttTrafficCountEvent(stat))
    }

    if (isSuspended) {
      discardCounter.addAndGet(calculatedSize)
      msg match {
        case m: MqttConnectMessage => ctx.close()
        case _ =>
      }
      ReferenceCountUtil.release(msg)
    }
    else {
      readCounter.addAndGet(calculatedSize)
      ctx.fireChannelRead(msg)
    }
  }

  override def write(ctx: ChannelHandlerContext, msg: Any, promise: ChannelPromise): Unit = {
    val metrics = ctx.channel.attr(ATTR_METRICS).get
    metrics.sent.inc()

    super.write(ctx, msg, promise)
  }

  def calculateSize(msg: Any): Long = {
    msg match {
      case msg: ByteBuf => msg.readableBytes
      case msg: ByteBufHolder => msg.content.readableBytes
      case _ => 0
    }
  }
}
