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

package com.thing2x.smqd.util

import akka.stream.Materializer
import com.typesafe.config.Config
import play.api.libs.ws.WSClientConfig
import play.api.libs.ws.ahc.{AhcWSClientConfigFactory, StandaloneAhcWSClient}
import com.thing2x.smqd._
import com.typesafe.sslconfig.ssl.SSLConfigSettings
import play.api.libs.ws.ahc.cache.AhcHttpCache

import scala.concurrent.duration._

/**
  * 2018. 7. 1. - Created by Kwon, Yeong Eon
  */
trait AhcWSClientAware {

  def createAhcWSClient(config: Option[Config])(implicit materializer: Materializer): StandaloneAhcWSClient = {
    val sslConfigSettings = SSLConfigSettings()

    val cfg = config match {
      case Some(c) =>
        val wsConfig = WSClientConfig(
          c.getOptionDuration("connection_timeout").getOrElse(2.minutes),
          c.getOptionDuration("idle_timeout").getOrElse(2.minutes),
          c.getOptionDuration("request_timeout").getOrElse(2.minutes),
          c.getOptionBoolean("follow_redirects").getOrElse(true),
          c.getOptionBoolean("use_proxy_properites").getOrElse(true),
          c.getOptionString("user_agent"),
          c.getOptionBoolean("compression_enabled").getOrElse(false),
          sslConfigSettings)
        AhcWSClientConfigFactory.forClientConfig(wsConfig)

      case _ =>
        val wsConfig = WSClientConfig( 2.minute, 2.minute, 2.minute,
          followRedirects = true,
          useProxyProperties = true,
          Some("smqd http client"),
          compressionEnabled = false,
          sslConfigSettings)
        AhcWSClientConfigFactory.forClientConfig(wsConfig)
    }

    val httpCache: Option[AhcHttpCache] = None

    StandaloneAhcWSClient.apply(cfg, httpCache)
  }
}
