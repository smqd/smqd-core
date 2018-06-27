package t2x.smqd.rest

import akka.http.scaladsl.server.Route
import com.typesafe.config.Config
import t2x.smqd.Smqd

/**
  * 2018. 6. 20. - Created by Kwon, Yeong Eon
  */
abstract class RestController(name: String, smqd: Smqd, config: Config) extends RestResult {
  def routes: Route
}
