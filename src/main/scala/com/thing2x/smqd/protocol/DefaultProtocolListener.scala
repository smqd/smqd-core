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

package com.thing2x.smqd.protocol

import akka.actor.ActorRef
import com.typesafe.config.Config
import com.typesafe.scalalogging.StrictLogging
import com.thing2x.smqd._

import scala.io.AnsiColor

/**
  * 2018. 5. 31. - Created by Kwon, Yeong Eon
  */
class DefaultProtocolListener(name: String, smqd: Smqd, config: Config) extends Service(name, smqd, config) with StrictLogging {

  import AnsiColor._
  private val colors = Seq(
    RED, GREEN, YELLOW, BLUE, MAGENTA, CYAN
  )

  private def colored_do(str: String, hash: Int): String = s"${colors(math.abs(hash % colors.length))}$str$RESET"
  private def colored_no(str: String, hash: Int): String = str

  private var colored:(String, Int) => String = colored_no

  private var subr: Option[ActorRef] = None

  override def start(): Unit = {
    val topic = config.getString("subscribe.topic")
    val s = smqd.subscribe(FilterPath(topic)) {
      case (topicPath, msg) => notified(topicPath, msg)
    }
    subr = Some(s)

    val coloring = config.getBoolean("coloring")
    colored = if(coloring) colored_do else colored_no
  }

  override def stop(): Unit = {
    subr match {
      case Some(s) => smqd.unsubscribe(s)
      case _ =>
    }
  }

  def notified(topicPath: TopicPath, msg: Any): Unit = {
    msg match {
      case m: ProtocolNotification =>
        val channelId = m.channelId
        val clientId = if (m.clientId.contains("@")) m.clientId else channelId + "@" + m.clientId
        val dirType = s"${if(m.direction == Recv) "Recv" else if(m.direction == Send) "Send" else "---" }"
        logger.debug(s"${colored(s"[$clientId] $dirType ${m.messageType}", channelId.hashCode)} ${m.message}")

      case _ =>
    }
  }
}
