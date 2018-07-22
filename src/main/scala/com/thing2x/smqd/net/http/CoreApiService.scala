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

import com.thing2x.smqd._
import com.typesafe.config.Config
import com.typesafe.scalalogging.StrictLogging

// 2018. 7. 9. - Created by Kwon, Yeong Eon

class CoreApiService(name: String, smqdInstance: Smqd, config: Config) extends HttpService(name, smqdInstance, config) with StrictLogging {
  private val smqdPrefix: String = config.getOptionString("prefix").getOrElse("")

  private var localEndpoint: Option[String] = None
  private var secureEndpoint: Option[String] = None
  override def endpoint: EndpointInfo = EndpointInfo(localEndpoint, secureEndpoint)

  override def localBound(address: InetSocketAddress): Unit = {
    localEndpoint = Some(s"http://$localAddress:${address.getPort}/${trimSlash(smqdPrefix)}")
    smqdInstance.setApiEndpoint(EndpointInfo(localEndpoint, secureEndpoint))
  }

  override def localSecureBound(address: InetSocketAddress): Unit = {
    secureEndpoint = Some(s"https://$localSecureAddress:${address.getPort}/${trimSlash(smqdPrefix)}")
    smqdInstance.setApiEndpoint(EndpointInfo(localEndpoint, secureEndpoint))
  }

  override val shouldExitOnFailure: Boolean = true
}
