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

package t2x.smqd

import java.io.{File, InputStreamReader}

import com.typesafe.config.{Config, ConfigFactory}
import com.typesafe.scalalogging.StrictLogging

/**
  * 2018. 6. 28. - Created by Kwon, Yeong Eon
  */
trait SmqMainBase extends StrictLogging {
  def dumpEnvNames: Seq[String] = Nil

  // override for rebranding logo and application's version
  def logo: String = ""
  def versionString = ""
  def configBasename = "smqd"

  def buildSmqd(): Smqd = {
    val config = buildConfig()
    // print out logo first then load config,
    // because there is chances to fail while loading config,
    if (config == null) {
      logger.info(logo+"\n")
      scala.sys.exit(-1)
    }
    else {
      val smqdVersion: String = config.getString("smqd.version")
      logger.info(s"$logo $versionString(smqd: $smqdVersion)\n")
    }

    //// for debug purpose /////
    dumpEnvNames.foreach{ k =>
      val v = System.getProperty(k, "<not defined>")
      logger.trace(s"-D$k = $v")
    }
    //// build configuration ///////

    try {
      SmqdBuilder(config).build()
    }
    catch {
      case ex: Throwable =>
        logger.error("initialization failed", ex)
        System.exit(1)
        null
    }
  }

  // override this to change the way of loading config
  def buildConfig(): Config = {
    val akkaRefConfig = ConfigFactory.load( "reference.conf")
    val smqdRefConfig = ConfigFactory.load("smqd-ref.conf")

    val configFilePath = System.getProperty("config.file")
    val configResourcePath = System.getProperty("config.resource")

    if (configFilePath != null) {
      val configFile = new File(configFilePath)

      if (!configFile.exists) {
        logger.error(s"config.file=$configFilePath not found.")
        scala.sys.error(s"config.file=$configFilePath not found.")
        return null
      }

      ConfigFactory.parseFile(configFile).withFallback(smqdRefConfig).withFallback(akkaRefConfig).resolve()
    }
    else if (configResourcePath != null) {
      val in = getClass.getClassLoader.getResourceAsStream(configResourcePath)
      if (in == null) {
        logger.error(s"config.resource=$configResourcePath not found.")
        scala.sys.error(s"config.resource=$configFilePath not found.")
        return null
      }
      val configReader = new InputStreamReader(in)
      ConfigFactory.parseReader(configReader).withFallback(smqdRefConfig).withFallback(akkaRefConfig).resolve()
    }
    else {
      ConfigFactory.load(configBasename).withFallback(smqdRefConfig).withFallback(akkaRefConfig).resolve()
    }
  }
}

/**
  * Java API
  */
class SmqMain extends SmqMainBase

