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

import java.net.InetSocketAddress

import akka.actor.ActorSystem
import akka.http.scaladsl.Http.ServerBinding
import akka.http.scaladsl.model.{HttpRequest, HttpResponse, StatusCodes}
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import akka.http.scaladsl.server.directives.LoggingMagnet
import akka.http.scaladsl.settings.ServerSettings
import akka.http.scaladsl.{ConnectionContext, Http, server}
import akka.stream.Materializer
import akka.stream.scaladsl.Sink
import com.thing2x.smqd._
import com.thing2x.smqd.plugin.Service
import com.thing2x.smqd.rest.RestController
import com.thing2x.smqd.util.ConfigUtil._
import com.typesafe.config.Config
import com.typesafe.scalalogging.StrictLogging

import scala.collection.JavaConverters._
import scala.concurrent.{ExecutionContext, Future}
import scala.concurrent.duration._
import scala.language.postfixOps
import scala.util.{Failure, Success}

// 2018. 6. 19. - Created by Kwon, Yeong Eon

class HttpService(name: String, smqdInstance: Smqd, config: Config) extends Service(name, smqdInstance, config) with StrictLogging with CORSHandler {

  val corsEnabled: Boolean = config.getOptionBoolean("cors.enabled").getOrElse(true)
  val localEnabled: Boolean = config.getOptionBoolean("local.enabled").getOrElse(true)
  val localAddress: String = config.getOptionString("local.address").getOrElse("127.0.0.1")
  val localPort: Int = config.getOptionInt("local.port").getOrElse(80)
  val localBindAddress: String = config.getOptionString("local.bind.address").getOrElse("0.0.0.0")
  val localBindPort: Int = config.getOptionInt("local.bind.port").getOrElse(localPort)
  val localParallelism: Int = config.getOptionInt("local.parallelism").getOrElse(1)

  val localSecureEnabled: Boolean = config.getOptionBoolean("local.secure.enabled").getOrElse(false)
  val localSecureAddress: String = config.getOptionString("local.secure.address").getOrElse("127.0.0.1")
  val localSecurePort: Int = config.getOptionInt("local.secure.port").getOrElse(443)
  val localSecureBindAddress: String = config.getOptionString("local.secure.bind.address").getOrElse("0.0.0.0")
  val localSecureBindPort: Int = config.getOptionInt("local.secure.bind.port").getOrElse(localSecurePort)
  val localSecureParallelism: Int = config.getOptionInt("local.secure.parallelism").getOrElse(1)

  private val oauth2SecretKey: String = config.getOptionString("oauth2.secret_key").getOrElse("default_key")
  val oauth2TokenExpire: Duration = config.getOptionDuration("oauth2.token_expire").getOrElse(30.minutes)
  val oauth2RefreshTokenExpire: Duration = config.getOptionDuration("oauth2.refresh_token_expire").getOrElse(4.hours)
  val oauth2Algorithm: String = config.getOptionString("oauth2.algorithm").getOrElse("HS256")

  val oauth2 = OAuth2(oauth2SecretKey, oauth2Algorithm, oauth2TokenExpire, oauth2RefreshTokenExpire)
  setAuthSimulationMode(config.getOptionBoolean("oauth2.simulation_mode").getOrElse(false),
    config.getOptionString("oauth2.simulation_identifier").getOrElse("admin"))

  private var binding: Option[ServerBinding] = None
  private var tlsBinding: Option[ServerBinding] = None
  private var finalRoutes: Route = _

  private var localEndpoint: Option[String] = None
  private var secureEndpoint: Option[String] = None
  // Endpoint address of this http server
  def endpoint: EndpointInfo = EndpointInfo(localEndpoint, secureEndpoint)

  override def start(): Unit = {
    logger.info(s"[$name] Starting...")
    logger.debug(s"[$name] local enabled : $localEnabled")
    if (localEnabled) {
      logger.debug(s"[$name] local address : $localAddress:$localPort")
      logger.debug(s"[$name] local bind    : $localBindAddress:$localBindPort")
    }
    logger.debug(s"[$name] secure enabled: $localSecureEnabled")
    if (localSecureEnabled) {
      logger.debug(s"[$name] secure address: $localSecureAddress:$localSecurePort")
      logger.debug(s"[$name] secure bind   : $localSecureBindAddress:$localSecureBindPort")
    }

    implicit val ec: ExecutionContext = smqdInstance.Implicit.gloablDispatcher
    implicit val actorSystem: ActorSystem = smqdInstance.Implicit.system
    implicit val materializer: Materializer = smqdInstance.Implicit.materializer

    val logAdapter: HttpServiceLogger = new HttpServiceLogger(logger, name)

    // load routes configuration or from `loadRoutes()` of sub class
    val rs = if (config.hasPath("routes")) loadRouteFromConfig(config.getConfigList("routes").asScala) else loadRoutes(config)
    val cs = if (config.hasPath("statics")) loadStaticFromConfig(config.getConfigList("statics").asScala) else loadStatics(config)

    val routes = rs ++ cs

    // merge all routes into a single route value
    // then encapsulate with log directives
    finalRoutes = {
      val rs = if (routes.isEmpty) emptyRoute
      else if (routes.size == 1) routes.head
      else routes.tail.foldLeft(routes.head)((prev, r) => prev ~ r)

      if (corsEnabled) corsHandler(rs) else rs
    }

    def handler(remoteAddress: InetSocketAddress): HttpRequest => Future[HttpResponse] = {
      val routes = logRequestResult(LoggingMagnet(_ => logAdapter.accessLog(System.nanoTime, remoteAddress))) {
        finalRoutes
      }
      Route.asyncHandler(routes)
    }

    if (localEnabled) {
      val serverSource = Http().bind(localBindAddress, localBindPort, ConnectionContext.noEncryption(), ServerSettings(actorSystem), logAdapter)
      val bindingFuture = serverSource.to(Sink.foreach{ connection =>
        connection.handleWithAsyncHandler(handler(connection.remoteAddress), localParallelism)
      }).run()

      bindingFuture.onComplete {
        case Success(b) =>
          binding = Some(b)
          localEndpoint = Some(s"http://$localAddress:${b.localAddress.getPort}")
          localBound(b.localAddress)
          logger.info(s"[$name] Started. listening ${b.localAddress}")
        case Failure(e) =>
          // report the exception instead of quick death
          // then AbstractPlugin will catch it, so that plugin manager can mark the plugin instance with failure
          failure(new Exception(s"Bind fail /$localBindAddress:$localBindPort", e))
      }
    }

    smqdInstance.tlsProvider match {
      case Some(tlsProvider) if localSecureEnabled =>
        tlsProvider.sslContext match {
          case Some(sslContext) =>
            val connectionContext = ConnectionContext.https(sslContext)
            val serverSource = Http().bind(localSecureBindAddress, localSecureBindPort, connectionContext, ServerSettings(actorSystem), logAdapter)
            val tlsBindingFuture = serverSource.to(Sink.foreach{ connection =>
              connection.handleWithAsyncHandler(handler(connection.remoteAddress), localSecureParallelism)
            }).run()

            tlsBindingFuture.onComplete {
              case Success(b) =>
                tlsBinding = Some(b)
                secureEndpoint = Some(s"https://$localSecureAddress:${b.localAddress.getPort}")
                localSecureBound(b.localAddress)
                logger.info(s"[$name] Started. listening ${b.localAddress}")
              case Failure(e) =>
                // report the exception instead of quick death
                // then AbstractPlugin will catch it, so that plugin manager can mark the plugin instance with failure
                failure(new Exception(s"Bind fail /$localSecureBindAddress:$localSecureBindPort", e))
            }
          case _ =>
        }
      case _ =>
    }
  }

  override def stop(): Unit = {
    logger.info(s"[$name] Stopping...")

    implicit val ec: ExecutionContext = smqdInstance.Implicit.gloablDispatcher
    implicit val actorSystem: ActorSystem = smqdInstance.Implicit.system
    implicit val materializer: Materializer = smqdInstance.Implicit.materializer

    binding match {
      case Some(b) =>
        try {
          b.unbind().onComplete { // trigger unbinding from the port
            _ => logger.debug(s"[$name] unbind ${b.localAddress.toString} done.")
          }
        }
        catch {
          case ex: java.util.NoSuchElementException =>
            logger.warn(s"[$name] Binding was unbound before it was completely finished")
          case ex: Throwable =>
            logger.warn(s"[$name] plain tcp port unbind failed.", ex)
        }
      case None =>
    }

    tlsBinding match {
      case Some(b) =>
      try {
        b.unbind().onComplete { // trigger unbinding from the port
          _ => logger.info(s"[$name] unbind ${b.localAddress.toString} done.")
        }
      }
      catch {
        case ex: java.util.NoSuchElementException =>
          logger.warn(s"[$name] Binding was unbound before it was completely finished")
        case ex: Throwable =>
          logger.warn(s"[$name] tls tcp port unbind failed.", ex)
      }
      case None =>
    }

    logger.info(s"[$name] Stopped.")
  }

  def setAuthSimulationMode(isSimulationMode: Boolean, simulationIdentifier: String): Unit = {
    oauth2.setSimulationMode(isSimulationMode, simulationIdentifier)
  }

  protected def trimSlash(p: String): String = rtrimSlash(ltrimSlash(p))
  protected def ltrimSlash(p: String): String = if (p.startsWith("/")) p.substring(1) else p
  protected def rtrimSlash(p: String): String = if (p.endsWith("/")) p.substring(0, p.length - 1) else p

  def localBound(address: InetSocketAddress): Unit = {
  }

  def localSecureBound(address: InetSocketAddress): Unit = {
  }

  def routes: Route = finalRoutes

  private def loadRouteFromConfig(configList: Seq[Config]): Seq[server.Route] = {
    configList.map { conf =>
      val rname = conf.getString("name")
      val prefix = conf.getString("prefix")
      val className = conf.getString("class")

      loadRoute(rname, prefix, className, conf)
    }
  }

  protected def loadRoute(rname: String, prefix: String, className: String, conf: Config): server.Route = {
    val tokens = prefix.split(Array('/', '"')).filterNot( _ == "") // split prefix into token array
    smqdInstance.loadClass(className, recursive = true) match { // load a class that inherits RestController
      case Some(clazz) =>
        val ctrl = try {
          val context = new HttpServiceContext(this, oauth2, smqdInstance, conf)
          val cons = clazz.getConstructor(classOf[String], classOf[HttpServiceContext]) // find construct that has parameters(String, HttpServiceContext)
          cons.newInstance(rname, context).asInstanceOf[RestController]
        } catch {
          case _: NoSuchMethodException =>
            val cons = clazz.getConstructor(classOf[String], classOf[Smqd], classOf[Config]) // find construct that has parameters (String, Smqd, Config)
            logger.warn(s"!!Warning!! controller '$className' has deprecated constructor (String, Smqd, Config), update it with new constructor api (String, HttpServiceContext)")
            cons.newInstance(rname, smqdInstance, conf).asInstanceOf[RestController] // create instance of RestController
        }

        logger.debug(s"[$name] add route $rname: $prefix = $className")

        // make pathPrefix routes from tokens
        tokens.foldRight(ctrl.routes) { (tok, routes) => pathPrefix(tok)(routes)}
      case None =>
        throw new ClassNotFoundException(s"Controller class '$className' not found")
    }
  }

  protected def loadRoutes(conf: Config): Seq[server.Route] = Nil

  private def loadStaticFromConfig(configList: Seq[Config]): Seq[server.Route] = {
    configList.map { conf =>
      val rname = conf.getString("name")
      val prefix = conf.getString("prefix")

      logger.debug(s"[$name] add static $rname: $prefix = ResourceController")

      loadStatic(rname, prefix, conf)
    }
  }

  protected def loadStatic(rname: String, prefix: String, conf: Config): server.Route = {
    val className = "com.thing2x.smqd.rest.ResourceController"

    loadRoute(rname, prefix, className, conf)
  }

  protected def loadStatics(conf: Config): Seq[server.Route] = Nil

  private val emptyRoute: Route = {
    get {
      complete(StatusCodes.InternalServerError, "It works, but there is no way to go.")
    }
  }
}
