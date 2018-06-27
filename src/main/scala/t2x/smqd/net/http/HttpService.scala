package t2x.smqd.net.http

import akka.actor.ActorSystem
import akka.http.scaladsl.Http.ServerBinding
import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import akka.http.scaladsl.server.directives.LoggingMagnet
import akka.http.scaladsl.settings.ServerSettings
import akka.http.scaladsl.{ConnectionContext, Http, server}
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.Sink
import com.typesafe.config.Config
import com.typesafe.scalalogging.StrictLogging
import t2x.smqd.rest.RestController
import t2x.smqd.{Service, Smqd}

import scala.collection.JavaConverters._
import scala.concurrent.{ExecutionContext, Future}
import scala.language.postfixOps
import scala.util.{Failure, Success}

/**
  * 2018. 6. 19. - Created by Kwon, Yeong Eon
  */
class HttpService(name: String, smqd: Smqd, config: Config) extends Service(name, smqd, config) with StrictLogging {

  val localBindAddress: String = config.getString("local.address")
  val localBindPort: Int = config.getInt("local.port")

  private var bindingFuture: Future[ServerBinding] = _

  override def start(): Unit = {
    logger.info(s"Http Service [$name] Starting...")

    implicit val system: ActorSystem = smqd.system
    implicit val materializer: ActorMaterializer = ActorMaterializer()

    val logAdapter: HttpServiceLogger = new HttpServiceLogger(logger, name)

    // load routes configuration
    val routes = loadRouteFromConfig(config.getConfig("routes"))

    // merge all routes into a single route value
    // then encapsulate with log directives
    val finalRoutes = logRequestResult(LoggingMagnet(_ => logAdapter.accessLog(System.nanoTime))) {
      if (routes.isEmpty) {
        emptyRoute
      }
      else if (routes.size == 1)
        routes.head
      else {
        routes.tail.foldLeft(routes.head)((prev, r) => prev ~ r)
      }
    }

    val serverSource = Http().bind(localBindAddress, localBindPort, ConnectionContext.noEncryption(), ServerSettings(system), logAdapter)
    val handler = Route.asyncHandler(finalRoutes)
    bindingFuture = serverSource.to(Sink.foreach{ connection =>
      connection.handleWithAsyncHandler(httpRequest => handler(httpRequest))
    }).run()

    implicit val executionContext: ExecutionContext = smqd.gloablDispatcher
    bindingFuture.onComplete {
      case Success(b) =>
        logger.info(s"Http Service [$name] Started. listening ${b.localAddress}")
      case Failure(e) =>
        logger.error(s"Http Service [$name] Failed", e)
        scala.sys.exit(-1)
    }
  }

  override def stop(): Unit = {
    logger.info(s"Http Service [$name] Stopping...")

    implicit val executionContext: ExecutionContext = smqd.gloablDispatcher
    bindingFuture.flatMap(_.unbind()) // trigger unbinding from the port
    logger.info(s"Http Service [$name] Stopped.")
  }

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
      complete(StatusCodes.InternalServerError, "There is no way.")
    }
  }
}
