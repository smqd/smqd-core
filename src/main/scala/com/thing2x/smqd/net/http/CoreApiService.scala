package com.thing2x.smqd.net.http

import com.thing2x.smqd._
import com.typesafe.config.Config
import com.typesafe.scalalogging.StrictLogging

/**
  * 2018. 7. 9. - Created by Kwon, Yeong Eon
  */
class CoreApiService(name: String, smqd: Smqd, config: Config) extends HttpService(name, smqd, config) with StrictLogging {
  private val smqdPrefix: String = config.getOptionString("prefix").getOrElse("")

  private var localEndpoint: Option[String] = None
  private var secureEndpoint: Option[String] = None
  def endpoint: EndpointInfo = EndpointInfo(localEndpoint, secureEndpoint)

  override def start(): Unit = {
    super.start()

    def trimSlash(p: String): String = rtrimSlash(ltrimSlash(p))

    def ltrimSlash(p: String): String = if (p.startsWith("/")) p.substring(1) else p
    def rtrimSlash(p: String): String = if (p.endsWith("/")) p.substring(0, p.length - 1) else p

    localEndpoint = Some(s"http://$localAddress:$localPort/${trimSlash(smqdPrefix)}")
    smqd.setApiEndpoint(EndpointInfo(localEndpoint, secureEndpoint))

    if (localSecureEnabled) {
      secureEndpoint = Some(s"https://$localSecureAddress:$localSecurePort/${trimSlash(smqdPrefix)}")
      smqd.setApiEndpoint(EndpointInfo(localEndpoint, secureEndpoint))
    }
  }
}
