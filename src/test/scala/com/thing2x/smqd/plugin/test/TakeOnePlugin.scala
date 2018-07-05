package com.thing2x.smqd.plugin.test

import com.thing2x.smqd.Smqd
import com.thing2x.smqd.plugin.SmqServicePlugin
import com.typesafe.config.Config
import com.typesafe.scalalogging.StrictLogging

/**
  * 2018. 7. 4. - Created by Kwon, Yeong Eon
  */
class TakeOnePlugin(name: String, smqd: Smqd, config: Config) extends SmqServicePlugin(name, smqd, config) with StrictLogging {

  override def start(): Unit = {
    logger.info("Start take one plugin")
  }

  override def stop(): Unit = {
    logger.info("Stop take one plugin")
  }
}
