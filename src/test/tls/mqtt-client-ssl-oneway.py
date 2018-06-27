#!/usr/local/bin/python2

import paho.mqtt.client as mqtt
import ssl, socket

# The callback for when the client receives a CONNACK response from the server.
def on_connect(client, userdata, rc, *extra_params):
   print('Connected with result code '+str(rc))
   # Subscribing in on_connect() means that if we lose the connection and
   # reconnect then subscriptions will be renewed.
   client.subscribe('v1/devices/me/attributes')
   client.subscribe('v1/devices/me/attributes/response/+')
   client.subscribe('v1/devices/me/rpc/request/+')


# The callback for when a PUBLISH message is received from the server.
def on_message(client, userdata, msg):
   print 'Topic: ' + msg.topic + '\nMessage: ' + str(msg.payload)
   if msg.topic.startswith( 'v1/devices/me/rpc/request/'):
       requestId = msg.topic[len('v1/devices/me/rpc/request/'):len(msg.topic)]
       print 'This is a RPC call. RequestID: ' + requestId + '. Going to reply now!'
       client.publish('v1/devices/me/rpc/response/' + requestId, "{\"value1\":\"A\", \"value2\":\"B\"}", 1)


client = mqtt.Client(client_id="py_tsl_1way", clean_session=True, userdata=None, protocol=mqtt.MQTTv311, transport="websockets")
#client = mqtt.Client(client_id="py_tsl_1way", clean_session=True, userdata=None, protocol=mqtt.MQTTv311, transport="tcp")

client.on_connect = on_connect
client.on_message = on_message
client.publish('v1/devices/me/attributes/request/1', "{\"clientKeys\":\"model\"}", 1)

client.tls_set(ca_certs="./keygen/smqd-server.pub.pem", certfile=None, keyfile=None, cert_reqs=ssl.CERT_REQUIRED,
                       tls_version=ssl.PROTOCOL_TLSv1, ciphers=None);

client.username_pw_set("ssluser", "ssluser")
client.tls_insecure_set(False)

#client.connect(socket.gethostname(), 4883, 1)

# TLS
#client.connect('smqd.thing2x.com', 4883, 10)
# WSS
client.connect('smqd.thing2x.com', 8083, 30)

# Blocking call that processes network traffic, dispatches callbacks and
# handles reconnecting.
# Other loop*() functions are available that give a threaded interface and a
# manual interface.
client.loop_forever()
