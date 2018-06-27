package t2x.smqd

import com.typesafe.config.Config

/**
  * 2018. 6. 3. - Created by Kwon, Yeong Eon
  */
abstract class Service(name: String, smqd: Smqd, config: Config) extends LifeCycle {

  def start(): Unit = {

  }

  def stop(): Unit = {

  }
}
