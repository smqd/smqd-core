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
import com.typesafe.scalalogging.StrictLogging
import spray.json._

// 2018. 7. 15. - Created by Kwon, Yeong Eon

/**
  *
  */
class ManagementControllerTest extends CoreApiTesting with StrictLogging {

  "version" should {
    "version" in {
      Get("/api/v1/management/version") ~> routes ~> check {
        status shouldEqual StatusCodes.OK
        val rsp = asCoreApiResponseAsMap(entityAs[String])
        assert(rsp.code == 0)
        assert(rsp.result("nodename").convertTo[String] == "smqd-test-node")
        assert(rsp.result("jvm").convertTo[String].length > 0)
        assert(rsp.result("commitVersion").convertTo[String].length > 0)
        assert(rsp.result("os").convertTo[String].length > 0)
        assert(rsp.result("version").convertTo[String] == smqdInstance.version)
      }
    }

    "simple" in {
      Get("/api/v1/management/version?fmt=version") ~> routes ~> check {
        status shouldEqual StatusCodes.OK
        val rsp = asCoreApiResponseAsMap(entityAs[String])
        assert(rsp.code == 0)
        assert(rsp.result("version").convertTo[String] == smqdInstance.version)
      }
    }

    "commit" in {
      Get("/api/v1/management/version?fmt=commit") ~> routes ~> check {
        status shouldEqual StatusCodes.OK
        val rsp = asCoreApiResponseAsMap(entityAs[String])
        assert(rsp.code == 0)
        assert(rsp.result("commitVersion").convertTo[String].length > 10)
      }
    }
  }

  "nodes" should {
    "list" in {
      Get("/api/v1/management/nodes") ~> routes ~> check {
        status shouldEqual StatusCodes.OK
        val json = entityAs[String].parseJson

        val rsp = json.convertTo[CoreApiResponse]
        assert(rsp.code == 0)

        val nodeInfoList = rsp.result.convertTo[Seq[NodeInfo]]
        assert(nodeInfoList.nonEmpty)
        assert(nodeInfoList.head.nodeName == "smqd-test-node")
      }
    }

    "single node" in {
      Get("/api/v1/management/nodes/smqd-test-node") ~> routes ~> check {
        status shouldEqual StatusCodes.OK
        val json = entityAs[String].parseJson

        val rsp = json.convertTo[CoreApiResponse]
        assert(rsp.code == 0)

        val nodeInfo = rsp.result.convertTo[NodeInfo]
        assert(nodeInfo.nodeName == "smqd-test-node")
        assert(nodeInfo.api.isDefined)
        logger.info(s"================+> ${nodeInfo.api.get.address.get}")
      }
    }
  }
}
