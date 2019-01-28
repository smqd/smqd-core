package com.thing2x.smqd.util

import java.util.concurrent.TimeUnit

trait HumanReadable {
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

  def humanReadableSize(fileSize: Long): String = {
    if(fileSize <= 0) return "0 B"
    // kilo, Mega, Giga, Tera, Peta, Exa, Zetta, Yotta
    val units: Array[String] = Array("B", "kB", "MB", "GB", "TB", "PB", "EB", "ZB", "YB")
    val digitGroup: Int = (Math.log10(fileSize)/Math.log10(1024)).toInt
    val unit = units(digitGroup)
    if (unit == "B")
      f"${fileSize/Math.pow(1024, digitGroup)}%.0f $unit"
    else
      f"${fileSize/Math.pow(1024, digitGroup)}%.2f $unit"
  }
}
