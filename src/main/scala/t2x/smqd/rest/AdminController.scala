package t2x.smqd.rest

import akka.http.scaladsl.server.{Directives, Route}
import com.typesafe.config.Config
import com.typesafe.scalalogging.StrictLogging
import t2x.smqd.Smqd

/**
  * 2018. 6. 20. - Created by Kwon, Yeong Eon
  */
class AdminController(name: String, smqd: Smqd, config: Config) extends RestController(name, smqd, config) with Directives with StrictLogging {
  val routes: Route = get {
    getFromResource("admin")
  }
}
