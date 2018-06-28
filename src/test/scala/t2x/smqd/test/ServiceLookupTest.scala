package t2x.smqd.test

import akka.actor.ActorSystem
import akka.testkit.{ImplicitSender, TestKit}
import com.typesafe.config.ConfigFactory
import com.typesafe.scalalogging.StrictLogging
import org.scalatest.{BeforeAndAfterAll, Matchers, WordSpecLike}
import t2x.smqd.SmqdBuilder
import t2x.smqd.net.http.HttpService

/**
  * 2018. 6. 28. - Created by Kwon, Yeong Eon
  */
class ServiceLookupTest extends TestKit(ActorSystem("smqd", ConfigFactory.parseString(
  """
    |akka.actor.provider=local
    |akka.cluster.seed-nodes=["akka.tcp://smqd@127.0.0.1:2551"]
    |
    |smqd.services=["core-api", "core-fault"]
  """.stripMargin).withFallback(ConfigFactory.load("smqd-ref.conf"))))
  with ImplicitSender
  with WordSpecLike
  with Matchers
  with BeforeAndAfterAll
  with StrictLogging {

  val smqd = new SmqdBuilder(system.settings.config)
    .setActorSystem(system)
    .build()
  smqd.start()

  "Service lookup" must {
    "find core-api" in {
      smqd.service("core-api") match {
        case Some(httpService: HttpService) =>
          assert(httpService.routes != null)
        case _ =>
          fail("Service core-api not found")
      }
    }
  }
}
