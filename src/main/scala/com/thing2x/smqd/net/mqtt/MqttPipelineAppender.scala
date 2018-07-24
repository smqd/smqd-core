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

    pipeline.addLast(CHANNEL_BPS_HANDLER, channelBpsCounter)

    if (sslEngine.isDefined) {
      pipeline.addLast(SSL_HANDLER, new SslHandler(sslEngine.get))
    }

    pipeline.addLast(DECODING_HANDLER, new MqttDecoder(messageMaxSize))
    pipeline.addLast(ENCODING_HANDLER, MqttEncoder.INSTANCE)

    //pipeline.addLast("loggingHandler", new io.netty.handler.logging.LoggingHandler("mqtt.logger", LogLevel.INFO))

    pipeline.addLast(CHANNEL_TPS_HANDLER, channelTpsCounter)

    pipeline.addLast(IDLE_STATE_HANDLER, new IdleStateHandler(7, 0, 0))

    pipeline.addLast(KEEPALIVE_HANDLER, MqttKeepAliveHandler())
    pipeline.addLast(PROTO_OUT_HANDLER, MqttProtocolOutboundHandler())
    pipeline.addLast(PROTO_IN_HANDLER, MqttProtocolInboundHandler())
    pipeline.addLast(PUBLISH_HANDLER, MqttPublishHandler())
    pipeline.addLast(SUBSCRIBE_HANDLER, MqttSubscribeHandler())
    pipeline.addLast(CONNECT_HANDER, MqttConnectHandler(clientIdentifierFormat))
    pipeline.addLast("exception.handler", MqttExceptionHandler())
  }
}
