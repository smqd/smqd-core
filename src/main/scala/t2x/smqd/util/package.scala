package t2x.smqd

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
