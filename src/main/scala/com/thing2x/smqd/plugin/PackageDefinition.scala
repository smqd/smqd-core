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

/**
  * 2018. 7. 9. - Created by Kwon, Yeong Eon
  */
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
    this.name match {
      case "smqd-core" if that.name == "smqd-core" => 0
      case "smqd-core" => 1
      case _ => this.name.compare(that.name)
    }
  }
}
