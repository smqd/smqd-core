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

import akka.actor.{Actor, ActorRef, Cancellable}
import com.codahale.metrics.{Gauge, MetricRegistry, SharedMetricRegistries}
import com.thing2x.smqd.ChiefActor.{Ready, ReadyAck}
import com.thing2x.smqd.util.JvmMonitoringActor.Tick
import com.typesafe.scalalogging.StrictLogging

import scala.concurrent.duration._

// 2018. 7. 12. - Created by Kwon, Yeong Eon

/**
  *
  */
object JvmMonitoringActor {
  val actorName = "jvm_monitor"

  case object Tick

  trait Provider {
    def heapTotal: Long
    def heapEdenSpace: Long
    def heapSurvivorSpace: Long
    def heapOldGen: Long
    def heapUsed: Long
    def cpuLoad: Double
    def threadCount: Int
    def fdCount: Long
  }

  private var provider: Option[Provider] = None

  def setProvider(p: Provider): Boolean = this.synchronized {
    if (provider.isDefined) {
      // do nothing, this happens when multiple smqd instances exist in a JVM.
      // but no need to run multiple jvm metrics collector in a vm
      // so only take caore of the first provider.
      false
    }
    else {
      provider = Option(p)

      val registry = SharedMetricRegistries.getDefault

      registry.register(MetricRegistry.name("jvm.heap.total"), new Gauge[Long]{
        override def getValue: Long = if (provider.isDefined) provider.get.heapTotal else 0
      })
      registry.register(MetricRegistry.name("jvm.heap.eden_space"), new Gauge[Long]{
        override def getValue: Long = if (provider.isDefined) provider.get.heapEdenSpace else 0
      })
      registry.register(MetricRegistry.name("jvm.heap.survivor_space"), new Gauge[Long]{
        override def getValue: Long = if (provider.isDefined) provider.get.heapSurvivorSpace else 0
      })
      registry.register(MetricRegistry.name("jvm.heap.old_gen"), new Gauge[Long]{
        override def getValue: Long = if (provider.isDefined) provider.get.heapOldGen else 0
      })
      registry.register(MetricRegistry.name("jvm.heap.used"), new Gauge[Long]{
        override def getValue: Long = if (provider.isDefined) provider.get.heapUsed else 0
      })
      registry.register(MetricRegistry.name("jvm.cpu.load"), new Gauge[Double]{
        override def getValue: Double = if (provider.isDefined) provider.get.cpuLoad else 0
      })
      registry.register(MetricRegistry.name("jvm.thread.count"), new Gauge[Int]{
        override def getValue: Int = if (provider.isDefined) provider.get.threadCount else 0
      })
      registry.register(MetricRegistry.name("jvm.fd.count"), new Gauge[Long]{
        override def getValue: Long = if (provider.isDefined) provider.get.fdCount else 0
      })
      true
    }
  }
}

class JvmMonitoringActor extends Actor with StrictLogging with JvmAware with JvmMonitoringActor.Provider {

  var heapTotal: Long = 0
  var heapEdenSpace: Long = 0
  var heapSurvivorSpace: Long = 0
  var heapOldGen: Long = 0
  var heapUsed: Long = 0
  var cpuLoad: Double = 0
  var threadCount: Int = 0
  var fdCount: Long = 0

  private var schedule: Option[Cancellable] = None

  override def preStart(): Unit = {
    if (JvmMonitoringActor.setProvider(this)) { // schedulre is registered only when 'provider' is accepted
      import context.dispatcher
      val sc = context.system.scheduler.schedule(5.second, 10.second, self, Tick)
      schedule = Option(sc)
    }
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
    case Tick =>
      // ignore
  }

  def receive0: Receive = {
    case Tick =>
      var total: Long = 0
      var eden: Long = 0
      var suvivor: Long = 0
      var oldgen: Long = 0
      var used: Long = 0

      javaMemoryPoolUsage.foreach { usage =>
        usage.`type` match {
          case "Heap memory" =>
            used += usage.used
            if (usage.max > 0)
              total += usage.max

            val ln = usage.name.toLowerCase
            if (ln.contains("eden"))
              eden = usage.used
            else if (ln.contains("survivor"))
              suvivor = usage.used
            else if (ln.contains("old"))
              oldgen = usage.used
          case _ => // ignore
        }
      }

      this.heapTotal = total
      this.heapEdenSpace = eden
      this.heapSurvivorSpace = suvivor
      this.heapOldGen = oldgen
      this.heapUsed = used

      this.cpuLoad = javaCpuUsage

      this.threadCount = javaThreadCount

      this.fdCount = javaOperatingSystem.fd.getOrElse(0)
  }
}
