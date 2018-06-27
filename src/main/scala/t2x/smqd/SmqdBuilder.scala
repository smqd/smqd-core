package t2x.smqd

import akka.actor.{ActorSystem, AddressFromURIString}
import akka.cluster.Cluster
import com.typesafe.config.Config
import t2x.smqd.session.SessionStoreDelegate
import t2x.smqd.util.ClassLoading

import scala.collection.JavaConverters._

/**
  * 2018. 6. 12. - Created by Kwon, Yeong Eon
  */
object SmqdBuilder {
  def apply(config: Config): SmqdBuilder = new SmqdBuilder(config)
}
class SmqdBuilder(config: Config) extends ClassLoading {

  private var authDelegate: AuthDelegate = _
  private var registryDelegate: RegistryDelegate = _
  private var sessionStoreDelegate: SessionStoreDelegate = _

  private var system: ActorSystem = _

  private var serviceDefs: Map[String, Config] = _

  def setAuthDelegate(authDelegate: AuthDelegate): SmqdBuilder = {
    this.authDelegate = authDelegate
    this
  }

  def setRegistryDelegate(registryDelegate: RegistryDelegate): SmqdBuilder = {
    this.registryDelegate = registryDelegate
    this
  }

  def setSessionStoreDelegate(sessionStoreDelegate: SessionStoreDelegate): SmqdBuilder = {
    this.sessionStoreDelegate = sessionStoreDelegate
    this
  }

  def setActorSystem(system: ActorSystem): SmqdBuilder = {
    this.system = system
    this
  }

  def setServices(serviceDefs: Map[String, Config]): SmqdBuilder = {
    this.serviceDefs = serviceDefs
    this
  }

  def build(): Smqd = {
    var isClusterMode = false

    if (system == null) {
      system = ActorSystem.create(config.getString("smqd.cluster.name"), config)
      isClusterMode = system.settings.ProviderClass match {
        case "akka.cluster.ClusterActorRefProvider" => true
        case _ => false
      }

      if (isClusterMode) {
        val cluster = Cluster(system)
        // no leader, so try to join
        val discovery = config.getString("smqd.cluster.discovery")
        val seeds = discovery match {
          case "static" =>
            config.getStringList("smqd.cluster.static.seeds").asScala
              .map("akka.tcp://"+system.name+"@"+_)
              .map(AddressFromURIString.parse)
              .toList
          case _ =>
            Nil
        }
        cluster.joinSeedNodes(seeds)
      }
    }
    else {
      isClusterMode = system.settings.ProviderClass match {
        case "akka.cluster.ClusterActorRefProvider" => true
        case _ => false
      }
    }

    if (isClusterMode) {
      logger.info("Clustering is enabled")
    }
    else {
      logger.info("Clustering is disabled")
    }

    if (authDelegate == null)
      authDelegate = loadCustomClass[AuthDelegate](config.getString("smqd.delegates.authentication"))
    if (registryDelegate == null)
      registryDelegate = loadCustomClass[RegistryDelegate](config.getString("smqd.delegates.registry"))
    if (sessionStoreDelegate == null)
      sessionStoreDelegate = loadCustomClass[SessionStoreDelegate](config.getString("smqd.delegates.sessionstore"))

    //// load bridge settings
    val bridgeDriverDefs = config.getObjectList("smqd.bridge.drivers").asScala.map(_.toConfig).map{c => c.getString("name") -> c}.toMap
    val bridgeDefs = config.getObjectList("smqd.bridge.bridges").asScala.map(_.toConfig).toList

    //// load services
    serviceDefs = if (this.serviceDefs == null || serviceDefs.isEmpty) {
      val serviceNames = if (this.serviceDefs == null) config.getStringList("smqd.services").asScala else Nil
      Map( serviceNames.map { sname =>
        val sconf = config.getConfig("smqd."+sname)
        sname -> sconf
      }: _*)
    }
    else {
      serviceDefs
    }
    logger.info("Services: {}", serviceDefs.map{ case (name, _) => name}.mkString(", "))

    //// create an instance
    new Smqd(config,
      system,
      bridgeDriverDefs,
      bridgeDefs,
      serviceDefs,
      authDelegate,
      registryDelegate,
      sessionStoreDelegate)
  }
}
