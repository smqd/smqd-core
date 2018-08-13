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

import com.typesafe.config.Config

import scala.concurrent.ExecutionContext

// 2018. 8. 13. - Created by Kwon, Yeong Eon

/**
  *
  */
trait FacilityFactory {
  def userDelegate: UserDelegate
  def clientDelegate: ClientDelegate
  def registryDelegate: RegistryDelegate
  def sessionStoreDelegate: SessionStoreDelegate
  def release(): Unit
}

object FacilityFactory {
  def apply(config: Config)(implicit ec: ExecutionContext): FacilityFactory = {
    val factoryClassName = config.getString("smqd.facility_factory")
    val factoryClass = this.getClass.getClassLoader.loadClass(factoryClassName)
    val factoryConstructor = factoryClass.getConstructor(classOf[Config], classOf[ExecutionContext])
    factoryConstructor.newInstance(config, ec).asInstanceOf[FacilityFactory]
  }
}
