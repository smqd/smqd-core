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

package com.thing2x.smqd.util

import akka.actor.{Actor, Cancellable}
import com.codahale.metrics._
import com.thing2x.smqd.ChiefActor.{Ready, ReadyAck}
import com.thing2x.smqd.Smqd
import com.thing2x.smqd.util.MetricActor.Tick
import com.typesafe.scalalogging.StrictLogging

import scala.concurrent.duration._
import scala.jdk.CollectionConverters._

import io.circe.syntax._
import io.circe.generic.auto._

// 2018. 7. 25. - Created by Kwon, Yeong Eon

/**
  */
object MetricActor {
  val actorName = "metrics_reporter"

  case class SnapshotValue(
      mean: Double,
      max: Long,
      min: Long,
      median: Double,
      stdDev: Double,
      quantile75: Double,
      quantile95: Double,
      quantile98: Double,
      quantile99: Double,
      quantile999: Double
  )
  case class HistogramValue(count: Long, snapshot: SnapshotValue)
  case class RateValue(meanRate: Double, oneMinuteRate: Double, fifteenMinuteRate: Double, fiveMinuteRate: Double)
  case class MeterValue(count: Long, rates: RateValue)
  case class TimerValue(count: Long, rates: RateValue, snapshot: SnapshotValue)

  case object Tick

  case class Config(initialDelay: FiniteDuration = 5.seconds, delay: FiniteDuration = 10.seconds)
}

import MetricActor._

class MetricActor(smqdInstance: Smqd, config: Config) extends Actor with StrictLogging {
  private var schedule: Option[Cancellable] = None

  override def preStart(): Unit = {
    import context.dispatcher
    val sc = context.system.scheduler.scheduleAtFixedRate(config.initialDelay, config.delay, self, Tick)
    schedule = Option(sc)
  }

  override def postStop(): Unit = {
    schedule.map(_.cancel())
    schedule = None
  }

  override def receive: Receive = { case Ready =>
    if (schedule.isDefined) // become is working only the scheduler is registered
      context.become(receive0)
    sender() ! ReadyAck
  }

  private def receive0: Receive = { case Tick =>
    report()
  }

  private def report(): Unit = {
    val registry = SharedMetricRegistries.getDefault

    val prefix = "$SYS/metric/data/"

    def keyPrefix(key: String) = prefix + key.replaceAll("\\.", "/")

    // Counters
    registry.getCounters.asScala.foreach { case (key: String, counter: Counter) =>
      val topic = keyPrefix(key)
      val value = counter.getCount
      smqdInstance.publish(topic, s"""{"$key":$value}""")
    }

    // Gauges
    registry.getGauges.asScala.foreach { case (key: String, gauge) =>
      val topic = keyPrefix(key)
      val value = gauge.getValue
      smqdInstance.publish(topic, s"""{"$key":$value}""")
    }

    // Histograms
    registry.getHistograms.asScala.foreach { case (key: String, hist: Histogram) =>
      val topic = keyPrefix(key)
      val ss = hist.getSnapshot
      val v = HistogramValue(
        hist.getCount,
        SnapshotValue(
          ss.getMean,
          ss.getMax,
          ss.getMin,
          ss.getMedian,
          ss.getStdDev,
          ss.get75thPercentile,
          ss.get95thPercentile,
          ss.get98thPercentile,
          ss.get99thPercentile,
          ss.get999thPercentile
        )
      )
      smqdInstance.publish(topic, s"""{"$key":${v.asJson.noSpaces}}""")
    }

    // Meters
    registry.getMeters.asScala.foreach { case (key: String, meter: Meter) =>
      val topic = keyPrefix(key)
      val v = MeterValue(meter.getCount, RateValue(meter.getMeanRate, meter.getOneMinuteRate, meter.getFiveMinuteRate, meter.getFifteenMinuteRate))
      smqdInstance.publish(topic, s"""{"$key":${v.asJson.noSpaces}}""")
    }

    // Timers
    registry.getTimers.asScala.foreach { case (key: String, timer: Timer) =>
      val topic = keyPrefix(key)
      val ss = timer.getSnapshot
      val v = TimerValue(
        timer.getCount,
        RateValue(timer.getMeanRate, timer.getOneMinuteRate, timer.getFiveMinuteRate, timer.getFifteenMinuteRate),
        SnapshotValue(
          ss.getMean,
          ss.getMax,
          ss.getMin,
          ss.getMedian,
          ss.getStdDev,
          ss.get75thPercentile,
          ss.get95thPercentile,
          ss.get98thPercentile,
          ss.get99thPercentile,
          ss.get999thPercentile
        )
      )
      smqdInstance.publish(topic, s"""{"$key":${v.asJson.noSpaces}}""")
    }
  }
}
