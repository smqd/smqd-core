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

import io.netty.channel.{ChannelHandler, ChannelPipeline}
import io.netty.handler.codec.mqtt.{MqttDecoder, MqttEncoder}
import io.netty.handler.ssl.SslHandler
import io.netty.handler.timeout.IdleStateHandler
import javax.net.ssl.SSLEngine

import scala.util.matching.Regex

/**
  * 2018. 6. 26. - Created by Kwon, Yeong Eon
  */
trait MqttPipelineAppender {

  def appendMqttPipeline(pipeline: ChannelPipeline,
                         sslEngine: Option[SSLEngine],
                         channelBpsCounter: ChannelHandler,
                         channelTpsCounter: ChannelHandler,
                         messageMaxSize: Int,
                         clientIdentifierFormat: Regex): Unit = {

    pipeline.addLast(HANDLER_CHANNEL_BPS, channelBpsCounter)

    if (sslEngine.isDefined) {
      pipeline.addLast(HANDLER_SSL, new SslHandler(sslEngine.get))
    }

    pipeline.addLast(HANDLER_DECODING, new MqttDecoder(messageMaxSize))
    pipeline.addLast(HANDLER_ENCODING, MqttEncoder.INSTANCE)

    //pipeline.addLast("loggingHandler", new io.netty.handler.logging.LoggingHandler("mqtt.logger", LogLevel.INFO))

    pipeline.addLast(HANDLER_CHANNEL_TPS, channelTpsCounter)

    pipeline.addLast(HANDLER_IDLE_STATE, new IdleStateHandler(7, 0, 0))

    pipeline.addLast(HANDLER_KEEPALIVE, MqttKeepAliveHandler())
    pipeline.addLast(HANDLER_PROTO_OUT, MqttProtocolOutboundHandler())
    pipeline.addLast(HANDLER_PROTO_IN, MqttProtocolInboundHandler())
    pipeline.addLast(HANDLER_PUBLISH, MqttPublishHandler())
    pipeline.addLast(HANDLER_SUBSCRIBE, MqttSubscribeHandler())
    pipeline.addLast(HANDLER_CONNECT, MqttConnectHandler(clientIdentifierFormat))
    pipeline.addLast(HANDLER_EXCEPTION, MqttExceptionHandler())
  }
}
