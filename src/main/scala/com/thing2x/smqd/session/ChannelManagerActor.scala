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

package com.thing2x.smqd.session

import java.net.{InetSocketAddress, SocketAddress}

import akka.actor.{Actor, ActorRef, Props}
import com.thing2x.smqd.session.ChannelManagerActor.InitChannelBuilder
import com.thing2x.smqd.{ChiefActor, Smqd}
import com.typesafe.scalalogging.StrictLogging
import io.netty.channel.Channel

// 2018. 8. 21. - Created by Kwon, Yeong Eon

/**
  *
  */

object ChannelManagerActor {
  val actorName = "channels"

  case class InitChannelBuilder(builder: ChannelBuilder)
}

class ChannelManagerActor(smqdInstance: Smqd) extends Actor with StrictLogging {

  override def receive: Receive = {
    case ChiefActor.Ready =>
      sender ! ChiefActor.ReadyAck
      context.become(receive0)
  }

  private def addr(addr: SocketAddress): String =
    s"${addr.asInstanceOf[InetSocketAddress].getHostString}:${addr.asInstanceOf[InetSocketAddress].getPort}"

  def createChannelActor(channel: Channel): ActorRef = {
    val name = s"${addr(channel.remoteAddress)}-${addr(channel.localAddress)}"
    context.actorOf(Props(classOf[ChannelActor], channel), name)
  }

  def receive0: Receive = {
    case InitChannelBuilder(builder) =>
      builder.channelManager(this)

    case _ =>
  }
}
