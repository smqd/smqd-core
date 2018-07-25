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
import com.codahale.metrics.{Counter, Metric, SharedMetricRegistries}
import com.thing2x.smqd.ChiefActor.{Ready, ReadyAck}
import com.thing2x.smqd.Smqd
import com.thing2x.smqd.util.MetricActor.Tick
import com.typesafe.scalalogging.StrictLogging

import scala.concurrent.duration._
import scala.collection.JavaConverters._

// 2018. 7. 25. - Created by Kwon, Yeong Eon

/**
  *
  */
object MetricActor {
  val actorName = "metrics_reporter"

  case object Tick
}

class MetricActor(smqdInstance: Smqd) extends Actor with StrictLogging {
  private var schedule: Option[Cancellable] = None

  override def preStart(): Unit = {
    import context.dispatcher
    val sc = context.system.scheduler.schedule(5.second, 10.second, self, Tick)
    schedule = Option(sc)
  }

  override def postStop(): Unit = {
    schedule.map(_.cancel())
    schedule = None
  }

  override def receive: Receive = {
    case Ready =>
      if (schedule.isDefined) // become is working only the scheduler is registered
        context.become(receive0)
      sender ! ReadyAck
  }

  private def receive0: Receive = {
    case Tick =>
      report()
  }

  private def report(): Unit = {
    val registry = SharedMetricRegistries.getDefault

    val prefix = "$SYS/metric/data/"

    // Counters
    val counters = registry.getCounters.asScala
    counters.foreach{ case (key: String, counter: Counter) =>
      val topic = prefix + key.replaceAll("\\.", "/")
      val value = counter.getCount
      smqdInstance.publish(topic, s"""{"$key":$value}""")
    }

    // Gauges
    val gauges = registry.getGauges.asScala
    gauges.foreach { case (key: String, gauge) =>
      val topic = prefix + key.replaceAll("\\.", "/")
      val value = gauge.getValue
      smqdInstance.publish(topic, s"""{"$key":$value}""")
    }
  }
}
