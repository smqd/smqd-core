package t2x.smqd

import java.io.{File, InputStreamReader}

import com.typesafe.config.{Config, ConfigFactory}
import com.typesafe.scalalogging.StrictLogging

import scala.concurrent.ExecutionContext.Implicits.global
import scala.language.postfixOps

/**
  * 2018. 5. 29. - Created by Kwon, Yeong Eon
  */
object Main extends App with StrictLogging {

  //// for debug purpose /////
  def dumpEnv(): Unit = {
    Seq(
      "config.file",
      "logback.configurationFile",
      "java.net.preferIPv4Stack",
      "java.net.preferIPv6Addresses"
    ).foreach{ k =>
      val v = System.getProperty(k, "<not defined>")
      logger.trace(s"-D$k = $v")
    }
  }
  //// build configuration ///////

  // override for rebranding
  val logo: String =
    """
      | ____   __  __   ___   ____
      |/ ___| |  \/  | / _ \ |  _ \
      |\___ \ | |\/| || | | || | | |
      | ___) || |  | || |_| || |_| |
      ||____/ |_|  |_| \__\_\|____/ """.stripMargin

  // print out logo first then load config,
  // because there is chances of failure for loading config,
  private val config = buildConfig()

  // override for rebranding
  val versionString: String = ""

  if (config == null) {
    logger.info(logo+"\n")
    scala.sys.exit(-1)
  }
  else {
    val smqdVersion: String = config.getString("smqd.version")
    logger.info(s"$logo $versionString($smqdVersion)\n")
  }

  dumpEnv()

  private val smqd: Smqd = try {
    SmqdBuilder(config).build()
  }
  catch {
    case ex: Throwable =>
      logger.error("initialization failed", ex)
      System.exit(1)
      null
  }

  try{
    smqd.start()
  }
  catch {
    case ex: Throwable =>
      logger.error("starting failed", ex)
      System.exit(1)
  }

  private def buildConfig(): Config = {
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
        scala.sys.error(s"config.file=$configFilePath not found.")
        return null
      }
      val configReader = new InputStreamReader(in)
      ConfigFactory.parseReader(configReader).withFallback(smqdRefConfig).withFallback(akkaRefConfig).resolve()
    }
    else {
      ConfigFactory.load("smqd").withFallback(smqdRefConfig).withFallback(akkaRefConfig).resolve()
    }
  }
}
