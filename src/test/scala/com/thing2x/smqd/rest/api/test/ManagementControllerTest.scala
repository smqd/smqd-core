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

package com.thing2x.smqd.rest.api.test

import akka.http.scaladsl.model.StatusCodes
import com.thing2x.smqd.NodeInfo
import com.thing2x.smqd.plugin.PluginDefinition
import com.typesafe.scalalogging.StrictLogging

import io.circe.generic.auto._
import io.circe.parser._
import io.circe.Json
import com.thing2x.smqd.plugin._

// 2018. 7. 15. - Created by Kwon, Yeong Eon

/**
  *
  */
class ManagementControllerTest extends CoreApiTesting with StrictLogging {

  "version" should {
    "version" in {
      Get("/api/v1/management/version") ~> routes ~> check {
        status shouldEqual StatusCodes.OK
        val rsp = asCoreApiResponseWithMap(entityAs[String])
        assert(rsp.code == 0)
        assert(rsp.result("nodename").as[String].getOrElse(null) == "smqd-test-node")
        assert(rsp.result("jvm").as[String].getOrElse(null).length > 0)
        assert(rsp.result("commitVersion").as[String].getOrElse(null).length > 0)
        assert(rsp.result("os").as[String].getOrElse(null).length > 0)
        assert(rsp.result("version").as[String].getOrElse(null) == smqdInstance.version)
      }
    }

    "simple" in {
      Get("/api/v1/management/version?fmt=version") ~> routes ~> check {
        status shouldEqual StatusCodes.OK
        val rsp = asCoreApiResponseWithMap(entityAs[String])
        assert(rsp.code == 0)
        assert(rsp.result("version").as[String].getOrElse(null) == smqdInstance.version)
      }
    }

    "commit" in {
      Get("/api/v1/management/version?fmt=commit") ~> routes ~> check {
        status shouldEqual StatusCodes.OK
        val rsp = asCoreApiResponseWithMap(entityAs[String])
        assert(rsp.code == 0)
        assert(rsp.result("commitVersion").as[String].getOrElse(null).length > 10)
      }
    }
  }

  "nodes" should {
    "list" in {
      Get("/api/v1/management/nodes") ~> routes ~> check {
        status shouldEqual StatusCodes.OK
        val rsp = asCoreApiResponseWithType[Seq[NodeInfo]](entityAs[String])
        assert(rsp.code == 0)
        assert(rsp.result.nonEmpty)
        assert(rsp.result.head.nodeName == "smqd-test-node")
      }
    }

    "single node" in {
      Get("/api/v1/management/nodes/smqd-test-node") ~> routes ~> check {
        status shouldEqual StatusCodes.OK
        val rsp = asCoreApiResponseWithType[NodeInfo](entityAs[String])
        assert(rsp.code == 0)
        assert(rsp.result.nodeName == "smqd-test-node")
        assert(rsp.result.api.isDefined)
        assert(rsp.result.api.get.address.get.startsWith("http://127.0.0.1:"))
      }
    }
  }

  "plugins" should {
    "list" in {
      Get("/api/v1/management/plugins") ~> routes ~> check {
        status shouldEqual StatusCodes.OK
        val rsp = asCoreApiResponseWithMap(entityAs[String])
        assert(rsp.code == 0)
        assert(rsp.result("current_page").as[Int].getOrElse(-1) == 1)
        assert(rsp.result("objects").as[Seq[Json]].getOrElse(Nil).nonEmpty)
      }
    }
  }

  "done" in {
    shutdown()
  }
}
