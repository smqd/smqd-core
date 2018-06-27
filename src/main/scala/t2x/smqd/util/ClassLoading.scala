package t2x.smqd.util

import com.typesafe.scalalogging.StrictLogging

/**
  * 2018. 6. 3. - Created by Kwon, Yeong Eon
  */
trait ClassLoading extends StrictLogging {
  def loadCustomClass[A](className: String): A = {
    try {
      val clazz = this.getClass.getClassLoader.loadClass(className)
      clazz.newInstance().asInstanceOf[A]
    }
    catch {
      case ex: Throwable =>
        logger.error(s"Class loading failure: $className", ex)
        throw ex
    }
  }
}
