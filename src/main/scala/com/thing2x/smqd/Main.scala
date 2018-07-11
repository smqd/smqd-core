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

package com.thing2x.smqd

import com.typesafe.scalalogging.StrictLogging

// 2018. 5. 29. - Created by Kwon, Yeong Eon

/**
  * Default main class to launching a smqd process
  */
object Main extends App with  SmqMainBase with StrictLogging {

  override val dumpEnvNames = Seq(
    "config.file",
    "logback.configurationFile",
    "java.net.preferIPv4Stack",
    "java.net.preferIPv6Addresses"
  )

  try{
    val smqd = super.buildSmqd()
    smqd.start()
  }
  catch {
    case ex: Throwable =>
      logger.error("starting failed", ex)
      System.exit(1)
  }
}
