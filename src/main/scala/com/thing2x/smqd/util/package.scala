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

import java.util.concurrent.TimeUnit

/**
  * 2018. 6. 24. - Created by Kwon, Yeong Eon
  */
package object util {
  private val units = List(
    (TimeUnit.DAYS,"days"),
    (TimeUnit.HOURS,"hours"),
    (TimeUnit.MINUTES,"minutes"),
    (TimeUnit.SECONDS,"seconds"))

  def humanReadableTime(timediff: Long): String = {
    val init = ("", timediff)
    units.foldLeft(init){ case (acc,next) =>
      val (human, rest) = acc
      val (unit, name) = next
      val res = unit.convert(rest,TimeUnit.MILLISECONDS)
      val str = if (res > 0) human + " " + res + " " + name else human
      val diff = rest - TimeUnit.MILLISECONDS.convert(res,unit)
      (str,diff)
    }._1.trim
  }
}
