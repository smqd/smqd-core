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
import com.thing2x.smqd.plugin.Service
import com.typesafe.config.Config
import com.typesafe.scalalogging.StrictLogging

// 10/7/18 - Created by Kwon, Yeong Eon

/**
  *
  */
class PojoPlugin(name: String, smqd: Smqd, config: Config) extends Service(name, smqd, config) with StrictLogging {

  val message: String = config.getString("message")

  override def start(): Unit = {
    logger.info(s"============== started pojo $message ======")
  }

  override def stop(): Unit = {
    logger.info("============== stopped pojo ======")
  }
}
