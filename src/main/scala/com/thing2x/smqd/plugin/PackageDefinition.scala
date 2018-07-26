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

package com.thing2x.smqd.plugin

// 2018. 7. 9. - Created by Kwon, Yeong Eon

object PackageDefinition {
  def apply(name: String, vendor: String, description: String, plugins: Seq[PluginDefinition], repository: RepositoryDefinition): PackageDefinition
    = new PackageDefinition(name, vendor, description, plugins, repository)
}

class PackageDefinition(val name: String, val vendor: String, val description: String,
                        val plugins: Seq[PluginDefinition],
                        val repository: RepositoryDefinition)
  extends Ordered[PackageDefinition] {

  override def equals(other: Any): Boolean = {
    if (!other.isInstanceOf[PackageDefinition]) return false
    val r = other.asInstanceOf[PackageDefinition]

    this.name == r.name
  }

  override def compare(that: PackageDefinition): Int = {
    // make smqd-core package come first
    (this.name, that.name) match {
      case (PluginManager.CORE_PKG, _) => -1
      case (_, PluginManager.CORE_PKG) => 1
      case (l, r) if l.startsWith("smqd-") && r.startsWith("smqd") => l.compare(r)
      case (l, r) if l.startsWith("smqd-") && !r.startsWith("smqd") => -1
      case (l, r) if !l.startsWith("smqd-") && r.startsWith("smqd") => 1
      case _ => this.name.compare(that.name)
    }
  }
}
