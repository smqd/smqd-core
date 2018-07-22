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

package com.thing2x.smqd.plugin.test

import com.thing2x.smqd.Smqd
import com.thing2x.smqd.plugin.{InstanceStatus, Service}
import com.typesafe.config.Config
import com.typesafe.scalalogging.StrictLogging

/**
  * 2018. 7. 4. - Created by Kwon, Yeong Eon
  */
class TakeOnePlugin(name: String, smqd: Smqd, config: Config) extends Service(name, smqd, config) with StrictLogging {

  override def start(): Unit = {
    logger.info("Start take one plugin")
  }

  override def stop(): Unit = {
    logger.info("Stop take one plugin")
  }

  override def status: InstanceStatus = InstanceStatus.FAIL
}
