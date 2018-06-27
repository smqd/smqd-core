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

package t2x.smqd.util

import akka.actor.{ActorIdentity, ActorRef, ActorSystem, Identify}
import akka.pattern.ask
import akka.util.Timeout
import com.typesafe.scalalogging.StrictLogging
import t2x.smqd.ChiefActor

import scala.concurrent.Await
import scala.concurrent.duration._
import scala.language.postfixOps

/**
  * 2018. 6. 12. - Created by Kwon, Yeong Eon
  */
trait ActorIdentifying extends StrictLogging {

  def identifyActor(path: String)(implicit system: ActorSystem, timeout: Timeout = 2 second): ActorRef = {

    val future = system.actorSelection(path) ? Identify(this)

    Await.result(future, timeout.duration) match {
      case ActorIdentity(_, Some(ref)) =>
        logger.debug(s"Identify $path = $ref")
        ref
      case _ =>
        throw new RuntimeException(s"Can not identify acotr: $path")
    }
  }

  def manager(name: String): String = s"user/${ChiefActor.actorName}/$name"
}
