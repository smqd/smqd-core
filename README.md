# SMQD core

smqd is scalable message queue daemon and also asynchronous messaging framework

[![Build Status](https://travis-ci.org/smqd/smqd-core.svg?branch=develop)](https://travis-ci.org/smqd/smqd-core)
[ ![Download](https://api.bintray.com/packages/smqd/smqd/smqd-core_2.12/images/download.svg?version=0.1.0) ](https://bintray.com/smqd/smqd/smqd-core_2.12/0.1.0/link)

## Usage

SMQD is a Scalable Message Broker, runing as an standalone and can be embeded in a scala application

```scala
    resolvers += Resolver.bintrayRepo("smqd", "smqd")

    libraryDependencies += "t2x.smqd" %% "smqd-core" % "0.1.0"
```

### Features

- [x] Mqtt 3.1.1 (protocol level 0x04)
- [x] Mqtt over TLS (mqtts, wss)
- [x] Mqtt over Websockets
- [x] Clustering (inter-nodes message routing)
- [x] Local Topic
- [x] Queued Topic
- [x] Shared Topic
- [x] System Topic ($SYS)
- [x] Request & Response pattern in embed mode
- [x] Http RESTful API (http, https)
- [x] Bridge - Mqtt
- [x] Bridge - Http

#### Local Subscription: 

SMQD will not make cluster ranged routes for local subscription, and only deliver the messages on the node

```
    sub '$local/topic'
    pub 'topic'
```

#### Queue Subscription (Load balancing)

```
    sub '$queue/topic'
    pub 'topic'
```

#### Shared Subscription (Load balancing among the same group subscribers

```
    sub '$share/<group>/topic'
    pub 'topic'
```

#### System topics

- [x] $SYS/faults : system fault messages
- [x] $SYS/protocols : MQTT network control packet tracking

#### Embeded Mode

> SMQD's API is not stable yet which means application code using embeded SMQD may be changed according to the evolving SMQD

* how to initialize

simplest way

```scala
val config = ConfigFactory.load("application.conf")
val smqd = SmqdBuilder(config).build()

smqd.start()

scala.sys.addShutdownHook {
    smqd.stop()
}
```

customized way

```scala
  val config = ConfigFactory.load("application.conf")
  val system = ActorSystem.create("smqd", config)

  val services: Map[String, Config] = ...

  val smqd = SmqdBuilder(config)
    .setActorSystem(system)
    .setAuthDelegate(new DefaultAuthDelegate())
    .setRegistryDelegate(new DefaultRegistryDelegate())
    .setSessionStoreDelegate(new DefaultSessionStore())
    .setServices(services)
    .build()

  smqd.start()

  scala.sys.addShutdownHook {
    smqd.stop()
    system.terminate()
  }
```

#### how to subscribe

* standard mqtt client subscription

```
mosquitto_sub -t sensor/1/# -h 127.0.0.1 -p 1883 -u user -P user -d -V mqttv311
```

* actor subscription

```scala
class SubsriberActor extends Actor {
  override def receive: Receive = {
    case (topic: TopicPath, msg: Any) =>
      printlns"====> ${msg}")
      origin ! msg + " World"
  }
}

val myActor = system.actorOf(Props(classOf[SubsriberActor]), "myActor")

smqd.subscribe("registry/test/#", myActor)

1 to 100 foreach { i =>
    smqd.publish(s"registry/test/$i/temp", s"Hello")
}

smqd.unsubscribeAll(myActor)
```

* callback subscription

```scala
val subr = smqd.subscribe("registry/test/+/temp"){
    case (topic, msg) =>
        logger.info(s"====> ${topic} ${msg}")
}

1 to 100 foreach { i =>
    smqd.publish(s"registry/test/$i/temp", s"Hello World - $i")
}

smqd.unsubscribeAll(subr)
```

#### how to publish

* standard mqtt client publish

```
mosquitto_pub -t sensor/1/temp -h 127.0.0.1 -p 1883 -m "test message" -u user -P user -d -V mqttv311 -q 2
```

* publish api for embeded mode

```scala
smqd.publish("my/topic", "Hello Message")
```

#### how to use request-response pattern

```scala
implicit val timeout: Timeout = 1 second
implicit val ec: ExecutionContext = system.dispatcher

val f: Future[String] = smqd.request[String]("request/func", "Hello")

f.onComplete {
    case Success(str) =>
        println("Ok Responsed: {}", str)
    case Failure(ex) =>
        println("exception", ex)
}
```

### Mqtt over WebSocket

```
$ npm install mqtt --save
```

#### how to subscribe via ws

```javascript
var mqtt = require('mqtt')
var client  = mqtt.connect('ws://127.0.0.1:8086')

client.on('connect', function () {
  client.subscribe('sensor/+/temperature')
})

client.on('message', function (topic, message) {
  // message is Buffer
  console.log(message.toString())
})
```

#### how to publish via ws

```javascript
var mqtt = require('mqtt')
var client  = mqtt.connect('ws://127.0.0.1:8086')

client.on('connect', function () {
  client.publish('sensor/1/temperature', '10')
  client.publish('sensor/2/temperature', '20')
  client.publish('sensor/3/temperature', '30')
})

```


### Bridges

To use bridge, smqd requires driver definition (BridgeDriver) and bridge config (Bridge).
In general, the bridge and it's driver configurations are defined in `smqd.bridge` sections in the config file
```
smqd {
  bridge {
    drivers = [ // Array of driver definitions
        {
            name = <drive name>
            class = <driver implementation class name>
            // Each BridgeDriver can have its driver configuration
        },
        {
            // another driver definition
        }
    ]

    bridges = [ // Array of bridge configuration
        {
            topic = "sensor/+/temperature" // topic filter that the bridge will subscribe
            driver = <driver name>
            // Each BridgeDriver has its own bridge configuration
        },
        {
            // another bridge definition
        }
    ]
  }
}
```

- [smqd-bridge-mqtt](http://github.com/smqd/smqd-bridge-mqtt/)
- [smqd-bridge-http](http://github.com/smqd/smqd-bridge-http/)

### Configuration

- [Reference config](src/main/resources/smqd-ref.conf)