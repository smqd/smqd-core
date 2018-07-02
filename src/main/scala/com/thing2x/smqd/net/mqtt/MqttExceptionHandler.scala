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

package com.thing2x.smqd.net.mqtt

import com.typesafe.scalalogging.StrictLogging
import io.netty.channel.{ChannelHandlerAdapter, ChannelHandlerContext}

/**
  * 2018. 6. 26. - Created by Kwon, Yeong Eon
  */
object MqttExceptionHandler {
  def apply() = new MqttExceptionHandler()
}

class MqttExceptionHandler extends ChannelHandlerAdapter with StrictLogging {

  override def exceptionCaught(ctx: ChannelHandlerContext, cause: Throwable): Unit = {
    val channelCtx = ctx.channel.attr(ATTR_SESSION_CTX).get
    if (channelCtx != null) {
      val channelId = channelCtx.channelId
      val sessionId = channelCtx.clientId

      logger.error(s"[$sessionId] $channelId Unexpected Exception", cause)
    }
    else {
      logger.error("Unexpected exception", cause)
    }
    ctx.close()
  }
}
