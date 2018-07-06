package com.thing2x.smqd.rest.api

import akka.http.scaladsl.server.Directives
import com.thing2x.smqd.Smqd
import com.thing2x.smqd.rest.RestController
import com.typesafe.config.Config
import com.typesafe.scalalogging.StrictLogging
import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport._
import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.server.{Directives, Route}
import com.thing2x.smqd._
import com.thing2x.smqd.rest.RestController
import com.typesafe.config.Config
import com.typesafe.scalalogging.StrictLogging
import spray.json._

import scala.collection.immutable.SortedSet


/**
  * 2018. 7. 6. - Created by Kwon, Yeong Eon
  */
class PluginController(name: String, smqd: Smqd, config: Config) extends RestController(name, smqd, config) with Directives with StrictLogging {

  override def routes: Route = plugins

  private def plugins: Route = {
    ignoreTrailingSlash {
      path("plugins") {
        get { getPlugins(None)  }
      } ~
        path("plugins" / Remaining.?) { pluginName =>
          get { getPlugins(pluginName) }
        }
    }
  }

  private def getPlugins(pluginName: Option[String]): Route = {
    complete(StatusCodes.OK, restError(1, "not implemented"))
  }

}
