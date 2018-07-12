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

import java.lang.management.ManagementFactory

import com.thing2x.smqd.util.JvmAware.{JvmMemoryPoolUsage, JvmOperatingSystem}

import scala.collection.JavaConverters._

// 2018. 7. 1. - Created by Kwon, Yeong Eon

/**
  * Getting runtime information of the JVM
  */
object JvmAware {
  case class JvmMemoryPoolUsage(name: String, `type`: String, used: Long, max: Long)
  case class JvmOperatingSystem(name: String, version: String, arch: String, processors: Int)
}

trait JvmAware {
  def javaVersionString: String = {
    val mxb = ManagementFactory.getRuntimeMXBean
    s"${mxb.getVmName} ${mxb.getSpecVersion} (${mxb.getVmVendor} ${mxb.getVmVersion})"
  }

  def javaMemoryPoolUsage: Seq[JvmMemoryPoolUsage] = {
    val memoryPoolMXBeans = ManagementFactory.getMemoryPoolMXBeans
    memoryPoolMXBeans.asScala.map { mb =>
      val usage = mb.getUsage
      JvmMemoryPoolUsage(mb.getName, mb.getType.toString, usage.getUsed, usage.getMax)
    }
  }

  def javaOperatingSystem: JvmOperatingSystem = {
    val osMXBean = ManagementFactory.getOperatingSystemMXBean
    JvmOperatingSystem(osMXBean.getName, osMXBean.getVersion, osMXBean.getArch, osMXBean.getAvailableProcessors)
  }

  def javaCpuUsage: Double = {
    val osMXBean = ManagementFactory.getOperatingSystemMXBean
    osMXBean.getSystemLoadAverage
  }
}
