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

package com.thing2x.smqd.net.http

import akka.http.scaladsl.Http.ServerBinding
import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import akka.http.scaladsl.server.directives.LoggingMagnet
import akka.http.scaladsl.settings.ServerSettings
import akka.http.scaladsl.{ConnectionContext, Http, server}
import akka.stream.scaladsl.Sink
import com.thing2x.smqd._
import com.thing2x.smqd.plugin.Service
import com.thing2x.smqd.rest.RestController
import com.typesafe.config.Config
import com.typesafe.scalalogging.StrictLogging

import scala.collection.JavaConverters._
import scala.concurrent.Future
import scala.language.postfixOps
import scala.util.{Failure, Success}

/**
  * 2018. 6. 19. - Created by Kwon, Yeong Eon
  */
class HttpService(name: String, smqd: Smqd, config: Config) extends Service(name, smqd, config) with StrictLogging with CORSHandler {

  val corsEnabled: Boolean = config.getOptionBoolean("cors.enabled").getOrElse(true)
  val localEnabled: Boolean = config.getOptionBoolean("local.enabled").getOrElse(true)
  val localAddress: String = config.getOptionString("local.address").getOrElse("127.0.0.1")
  val localPort: Int = config.getOptionInt("local.port").getOrElse(80)
  val localBindAddress: String = config.getOptionString("local.bind.address").getOrElse(localAddress)
  val localBindPort: Int = config.getOptionInt("local.bind.port").getOrElse(localPort)

  val localSecureEnabled: Boolean = config.getOptionBoolean("local.secure.enabled").getOrElse(false)
  val localSecureAddress: String = config.getOptionString("local.secure.address").getOrElse("127.0.0.1")
  val localSecurePort: Int = config.getOptionInt("local.secure.port").getOrElse(443)
  val localSecureBindAddress: String = config.getOptionString("local.secure.address").getOrElse(localSecureAddress)
  val localSecureBindPort: Int = config.getOptionInt("local.secure.port").getOrElse(localSecurePort)

  private var bindingFuture: Future[ServerBinding] = _
  private var finalRoutes: Route = _

  override def start(): Unit = {
    logger.info(s"Http Service [$name] Starting...")
    logger.debug(s"Http Service [$name] local enabled : $localEnabled")
    logger.debug(s"Http Service [$name] local address : $localAddress:$localPort")
    logger.debug(s"Http Service [$name] local bind    : $localBindAddress:$localBindPort")
    logger.debug(s"Http Service [$name] secure enabled: $localSecureEnabled")
    logger.debug(s"Http Service [$name] secure address: $localSecureAddress:$localSecurePort")
    logger.debug(s"Http Service [$name] secure bind   : $localSecureBindAddress:$localSecureBindPort")

    import smqd.Implicit._

    val logAdapter: HttpServiceLogger = new HttpServiceLogger(logger, name)

    // load routes configuration
    val routes = loadRouteFromConfig(config.getConfig("routes"))

    // merge all routes into a single route value
    // then encapsulate with log directives
    finalRoutes = logRequestResult(LoggingMagnet(_ => logAdapter.accessLog(System.nanoTime))) {
      val rs = if (routes.isEmpty) {
        emptyRoute
      }
      else if (routes.size == 1)
        routes.head
      else {
        routes.tail.foldLeft(routes.head)((prev, r) => prev ~ r)
      }

      if (corsEnabled) corsHandler(rs) else rs
    }

    val handler = Route.asyncHandler(finalRoutes)

    if (localEnabled) {
      val serverSource = Http().bind(localBindAddress, localBindPort, ConnectionContext.noEncryption(), ServerSettings(system), logAdapter)
      bindingFuture = serverSource.to(Sink.foreach{ connection =>
        connection.handleWithAsyncHandler(httpRequest => handler(httpRequest))
      }).run()

      bindingFuture.onComplete {
        case Success(b) =>
          logger.info(s"Http Service [$name] Started. listening ${b.localAddress}")
        case Failure(e) =>
          logger.error(s"Http Service [$name] Failed", e)
          scala.sys.exit(-1)
      }
    }

    smqd.tlsProvider match {
      case Some(tlsProvider) if localSecureEnabled =>
        tlsProvider.sslContext match {
          case Some(sslContext) =>
            val connectionContext = ConnectionContext.https(sslContext)
            val serverSource = Http().bind(localSecureBindAddress, localSecureBindPort, connectionContext, ServerSettings(system), logAdapter)
            bindingFuture = serverSource.to(Sink.foreach{ connection =>
              connection.handleWithAsyncHandler(httpRequest => handler(httpRequest))
            }).run()

            bindingFuture.onComplete {
              case Success(b) =>
                logger.info(s"Http Service [$name] Started. listening ${b.localAddress}")
              case Failure(e) =>
                logger.error(s"Http Service [$name] Failed", e)
                scala.sys.exit(-1)
            }
          case _ =>
        }
      case _ =>
    }
  }

  override def stop(): Unit = {
    logger.info(s"Http Service [$name] Stopping...")

    import smqd.Implicit._
    bindingFuture.flatMap(_.unbind()) // trigger unbinding from the port
    logger.info(s"Http Service [$name] Stopped.")
  }

  def routes: Route = finalRoutes

  private def loadRouteFromConfig(config: Config): Set[server.Route] = {
    val names = config.entrySet().asScala.map(entry => entry.getKey)
      .filter(key => key.endsWith(".prefix"))
      .map( k => k.substring(0, k.length - ".prefix".length))
      .filter(k => config.hasPath(k+".class"))
    logger.info(s"[$name] routes = "+names.mkString(", "))
    names.map{ rname =>
      val conf = config.getConfig(rname)
      val className = conf.getString("class")
      val prefix = conf.getString("prefix")
      val tokens = prefix.split(Array('/', '"')).filterNot( _ == "") // split prefix into token array
      val clazz = getClass.getClassLoader.loadClass(className)    // load a class that inherits RestController
      val cons = clazz.getConstructor(classOf[String], classOf[Smqd], classOf[Config]) // find construct that has parameters (String, Smqd, Config)
      val ctrl = cons.newInstance(rname, smqd, conf).asInstanceOf[RestController] // create instance of RestController

      logger.debug(s"[$name] add route $rname: $prefix = $className")

      // make pathPrefix routes from tokens
      tokens.foldRight(ctrl.routes) { (tok, routes) => pathPrefix(tok)(routes)}
    }.toSet
  }

  private val emptyRoute: Route = {
    get {
      complete(StatusCodes.InternalServerError, "There is no way to go.")
    }
  }
}
