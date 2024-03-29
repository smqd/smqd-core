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

package com.thing2x.smqd.impl

import java.io.File

import akka.actor.ActorSystem
import com.thing2x.smqd._
import com.thing2x.smqd.registry.RegistryDelegate
import com.typesafe.config.Config

import scala.concurrent.ExecutionContext

// 2018. 8. 13. - Created by Kwon, Yeong Eon

/**
  *
  */
class DefaultFacilityFactory(config: Config, system: ActorSystem, ec: ExecutionContext) extends FacilityFactory {

  protected val configDirectory: File = {
    val confFile = System.getProperty("config.file")
    if (confFile == null) new File(".")
    else new File(confFile).getParentFile
  }

  override def userDelegate: UserDelegate = {
    new DefaultUserDelegate(new File(configDirectory, "passwd"))
  }

    override def clientDelegate: ClientDelegate = {
      new DefaultClientDelegate()
    }

    override def registryDelegate: RegistryDelegate = {
      new DefaultRegistryDelegate()
    }

    override def sessionStoreDelegate: SessionStoreDelegate = {
      implicit val executionContext: ExecutionContext = ec
      new DefaultSessionStoreDelegate()
    }

    override def release(): Unit = {
  }
}
