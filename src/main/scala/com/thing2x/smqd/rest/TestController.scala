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

package com.thing2x.smqd.rest

import akka.actor.ActorSystem
import akka.http.scaladsl.model.{ContentTypes, MediaTypes, StatusCodes}
import akka.http.scaladsl.server.{Directives, Route}
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.{Sink, StreamConverters}
import com.typesafe.config.Config
import com.typesafe.scalalogging.StrictLogging
import com.thing2x.smqd.Smqd

import scala.concurrent.ExecutionContext
import scala.util.{Failure, Success}

/**
  * 2018. 6. 24. - Created by Kwon, Yeong Eon
  */
class TestController(name: String, smqd: Smqd, config: Config) extends RestController(name, smqd, config) with Directives with StrictLogging {

  override def routes: Route = metrics

  import smqd.Implicit._

  def metrics: Route = {
    ignoreTrailingSlash {
      extractRequestContext { ctx =>
        path("blackhole" / Remaining.?) { remain =>
          val suffix = remain match {
            case Some(str) => str
            case None => ""
          }

          val contentType = ctx.request.entity.getContentType

          val content = if (contentType.mediaType.isText){
            ctx.request.entity.dataBytes.map(bs => bs.utf8String).runFold(new StringBuilder())(_.append(_)).map(_.toString)
          }
          else {
            ctx.request.entity.dataBytes.map(bs => bs.length).runFold(0)(_+_).map(i => i.toString)
          }

          val received = ctx.request.entity.contentLengthOption.getOrElse(0)

          content.onComplete{
            case Success(str) =>
              logger.debug("Blackhole received {} ({} bytes) with {}", str, received, suffix)
            case Failure(ex) =>
              logger.debug("Blackhole failed", ex)
          }
          complete(StatusCodes.OK, s"OK $received bytes received")
        } ~
          path("echo") {
            complete(StatusCodes.OK, "Hello")
          }
      }
    }
  }
}
