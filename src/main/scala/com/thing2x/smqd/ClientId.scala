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

/**
  * 2018. 6. 11. - Created by Kwon, Yeong Eon
  */
object ClientId {
  def apply(id: String, channelId: Option[String]) = new ClientId(id, channelId)
  def apply(id: String, channelId: String) = new ClientId(id, Option(channelId))

  def fromActorName(actorName: String): String = {
    if (actorName.startsWith("_")) {
      actorName.substring(1)
    }
    else {
      actorName
    }
  }

  // The actor name must not be empty or start with $, but it may contain URL encoded characters
  def toActorName(id: String): String = "_" + id
}

class ClientId(val id: String, val channelId: Option[String] = None) extends Serializable {
  val actorName: String = ClientId.toActorName(id)

  override def toString: String = if (channelId.isEmpty) id + "@_" else id + "@" + channelId.get

  override def hashCode(): Int = id.hashCode
}
