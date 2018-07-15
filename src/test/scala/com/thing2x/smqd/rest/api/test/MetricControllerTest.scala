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
import com.typesafe.scalalogging.StrictLogging

// 2018. 7. 15. - Created by Kwon, Yeong Eon

/**
  *
  */
class MetricControllerTest extends CoreApiTesting with StrictLogging {

  "Metrics" should {
    "get-all" in {
      Get("/api/v1/metrics") ~> routes ~> check {
        status shouldEqual StatusCodes.OK
        val rsp = asCoreApiResponseAsMap(entityAs[String])
        assert(rsp.code == 0)
        assert(rsp.result.nonEmpty)
      }
    }

    "get-jvm/cpu" in {
      Get("/api/v1/metrics/jvm/cpu") ~> routes ~> check {
        status shouldEqual StatusCodes.OK
        val rsp = asCoreApiResponseAsMap(entityAs[String])
        assert(rsp.code == 0)
        assert(rsp.result.nonEmpty)
      }
    }
  }

}
