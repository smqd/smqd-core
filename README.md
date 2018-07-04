# SMQD core

[![License](http://img.shields.io/:license-apache-blue.svg)](http://www.apache.org/licenses/LICENSE-2.0.html)
[![Build Status](https://travis-ci.org/smqd/smqd-core.svg?branch=develop)](https://travis-ci.org/smqd/smqd-core)
[![Sonatype Nexus (Releases)](https://img.shields.io/nexus/r/https/oss.sonatype.org/com.thing2x/smqd-core_2.12.svg)](https://oss.sonatype.org/content/groups/public/com/thing2x/smqd-core_2.12/)
[![Sonatype Nexus (Snapshots)](https://img.shields.io/nexus/s/https/oss.sonatype.org/com.thing2x/smqd-core_2.12.svg)](https://oss.sonatype.org/content/groups/public/com/thing2x/smqd-core_2.12/)

SMQD :: Scala MQtt Daemon

## Usage

```scala
    libraryDependencies += "com.thing2x" %% "smqd-core" % "x.y.z"
```

If you want to try snapshot version, add a resolver for soatype repository

```scala
    resolvers += Resolver.sonatypeRepo("public")
```

## Features

- [x] Mqtt 3.1.1 (protocol level 0x04)
- [x] Mqtt over TLS (mqtt, mqtts)
- [x] Mqtt over Websockets (ws, wss)
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

#### Shared Subscription (Load balancing among the same group subscribers)

```
    sub '$share/<group>/topic'
    pub 'topic'
```

#### System topics

- [x] $SYS/faults : system fault messages
- [x] $SYS/protocols : MQTT network control packet tracking


* how to subscribe to smqd via mqtt

```
mosquitto_sub -t sensor/1/# -h 127.0.0.1 -p 1883 -u user -P user -d -V mqttv311
```

* how to publish to smqd via mqtt

```
mosquitto_pub -t sensor/1/temp -h 127.0.0.1 -p 1883 -m "test message" -u user -P user -d -V mqttv311 -q 2
```

#### Mqtt over WebSocket

* how to subscribe to smqd via ws

```
$ npm install mqtt --save
```

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

* how to publish to smqd via ws

```javascript
var mqtt = require('mqtt')
var client  = mqtt.connect('ws://127.0.0.1:8086')

client.on('connect', function () {
  client.publish('sensor/1/temperature', '10')
  client.publish('sensor/2/temperature', '20')
  client.publish('sensor/3/temperature', '30')
})

```

## Embeded Mode

> SMQD is work-in-progress and may break backward compatibility.


### Initialize

#### Simplest way

```scala
val config = ConfigFactory.load("application.conf")
val smqd = SmqdBuilder(config).build()

smqd.start()

scala.sys.addShutdownHook {
    smqd.stop()
}
```

#### Customized way

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

#### Subscribe API

* actor subscription

```scala
class SubsriberActor extends Actor {
  override def receive: Receive = {
    case (topic: TopicPath, msg: Any) =>
      printlns"====> ${topic} ${msg}")
  }
}

val myActor = system.actorOf(Props(classOf[SubsriberActor]), "myActor")

smqd.subscribe("registry/test/#", myActor)

1 to 100 foreach { i =>
    smqd.publish(s"registry/test/$i/temp", s"Hello")
}

smqd.unsubscribe(myActor)
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

smqd.unsubscribe(subr)
```

#### Publish API

```scala
smqd.publish("my/topic", "Hello Message")
```

#### Request-response API

```scala
implicit val timeout: Timeout = 1 second
implicit val ec: ExecutionContext = system.dispatcher

val f: Future[String] = smqd.request[String]("request/func", classOf[String], "Hello")

f.onComplete {
    case Success(str) =>
        println("Ok Responsed: {}", str)
    case Failure(ex) =>
        println("exception", ex)
}
```

## Cluster

### non-cluster mode

smqd runs non-cluster mode by default. It is configured by akka's provider.
If the provider is set as 'local', other settings of cluster (`smqd.cluster.*`) are ignored

```
akka.actor.provider = local
```

### cluster mode

To start smqd in cluster mode, akka provider should be `cluster`

```
akka.actor.provider = cluster
```

And akka cluster provider requires seed-nodes information for bootstrapping.
smqd supports multiple ways discovering seed-nodes addresses to akka.

#### static

The static discovery method is the default of akka cluster. Please refer the seed-nodes fundamental from
[akka documents](https://doc.akka.io/docs/akka/current/cluster-usage.html#joining-to-seed-nodes).
The only differencie from original akka cluster is using `smqd.static.seeds` instead of `akka.cluster.seed-nodes`.
The other considerations should be same as the akka document says

> Since smqd manages the cluster configuration and bootstrapping procedure by itself,
Do not use `akka.cluster.seed-nodes` in configuration.

```
smqd {
  cluster {
      discovery = static
      static {
        seeds = ["192.168.1.101:2551", "192.168.1.102:2551", "192.168.1.103:2551"]
      }
  }
}
```

#### etcd

You can use etcd as a storage of dynamic seed-node information. In this discovery mode,
you don't need to manage the addresses of nodes in the cluster

```
smqd {
  cluster {
      discovery = etcd
      etcd {
        server = "http://192.168.1.105:2379"
        prefix = /smqd/cluster/seeds
        node_ttl = 1m
      }
  }
}
```

## Customize behaviors

#### Client Authentication

Every application has its own policy to authenticate client's connections. SMQD put this role under `AuthDelegate`.
Application that needs to customize the authentication policy shlould implement `AuthDelegate`.
The following code is SMQD's default AuthDelegate implimentation.

There are three parameters for the method `authenticate()`.
`clientId` represents client-id that is defined in MQTT specification.
And `userName` and `password` are `Option` as MQTT protocol.
If your application doesn't want to allow zero-length clientId or empty `userName`,
just return `BaseNameOrpassword` instead of `SmqSuccess`

> The AuthDelegate is called only when a client is connecting to the SMQD via network as mqtt client.
> Internal publishing/subscription by api is not a subject of the authentication

```scala
class MyAuthDelegate extends com.thing2x.smqd.AuthDelegate {
  override def authenticate(clientId: String, userName: Option[String], password: Option[Array[Byte]]): Future[SmqResult] = {
    Future {
      println(s"[$clientId] userName: $userName password: $password")
      if (userName.isDefined && password.isDefined) {
        if (userName.get == new String(password.get, "utf-8"))
          SmqSuccess
        else
          BadUserNameOrPassword(clientId, "Bad user name or password ")
      }
      else {
        SmqSuccess
      }
    }
  }
}
```

There are two ways to register your `AuthDelegate`.

1) Change configuration to replace `AuthDelegate`.

> In this case your customized AuthDelegate class shouldn't have any parameter to instanciate.

```
smqd {
  delegates {
    authentication = com.sample.MyAuthDelegate
  }
}
```

2) `SmqdBuilder` has `setAuthDelegate()` that takes an instance of `AuthDelegate`.

> If your `AuthDelegate` needs parameters to instanciate, this is the way to do.

```scala
val smqd = SmqdBuilder(config)
    .setAuthDelegate(new MyAuthDelegate())
    .build()
```

## Bridges

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