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

import java.util.concurrent.TimeUnit

trait HumanReadable {
  private val units = List((TimeUnit.DAYS, "days"), (TimeUnit.HOURS, "hours"), (TimeUnit.MINUTES, "minutes"), (TimeUnit.SECONDS, "seconds"))

  def humanReadableTime(timediff: Long): String = {
    val init = ("", timediff)
    units
      .foldLeft(init) { case (acc, next) =>
        val (human, rest) = acc
        val (unit, name) = next
        val res = unit.convert(rest, TimeUnit.MILLISECONDS)
        val str = if (res > 0) human + " " + res + " " + name else human
        val diff = rest - TimeUnit.MILLISECONDS.convert(res, unit)
        (str, diff)
      }
      ._1
      .trim
  }

  def humanReadableSize(fileSize: Long): String = {
    if (fileSize <= 0) return "0 B"
    // kilo, Mega, Giga, Tera, Peta, Exa, Zetta, Yotta
    val units: Array[String] = Array("B", "kB", "MB", "GB", "TB", "PB", "EB", "ZB", "YB")
    val digitGroup: Int = (fileSize.toDouble / 1024.toDouble).toInt
    val unit = units(digitGroup)
    if (unit == "B")
      f"${fileSize / Math.pow(1024, digitGroup)}%.0f $unit"
    else
      f"${fileSize / Math.pow(1024, digitGroup)}%.2f $unit"
  }
}
