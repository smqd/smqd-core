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

package com.thing2x.smqd.util

import akka.actor.{Actor, ActorRef, Cancellable}
import com.thing2x.smqd.Smqd
import com.thing2x.smqd.plugin.Service
import com.typesafe.config.Config
import com.typesafe.scalalogging.StrictLogging

import scala.collection.JavaConverters._
import scala.concurrent.ExecutionContext
import scala.concurrent.duration._
import scala.language.postfixOps
import scala.sys.process._

// 10/11/18 - Created by Kwon, Yeong Eon

/**
  *{{{
  *   services = [hchk]
  *
  *   hchk = {
  *      entry.class = "com.thing2x.smqd.util.HealthChecker
  *
  *      config = {
  *          check_list = [
  *             {
  *                name = haproxy
  *                command = "killall -0 haproxy"
  *                expect_exit = 0
  *                interval = 3 seconds
  *             },
  *             {
  *                name = process_name
  *                command = "script to check the process's health"
  *                expect_exit = 0        # 0 means the process is running, consider others as failure
  *                interval = 2 seconds   # how often execute the script
  *             },
  *          ]
  *      }
  *   }
  *}}}
  *
  * How to get the healthy information
  *
  * {{{
  *   smqd.service("hchk").get.asInstanceOf[HealthChecker].getStatus("haproxy")
  * }}}
  *
  * or register listerner actor
  *
  * {{{
  *   smqd.service("hchk").get.asInstanceOf[HealthChecker].addListener("haproxy", self)
  * }}}
  */
object HealthChecker {

  sealed trait HealthStatus

  object HealthStatus {
    case object Success extends HealthStatus
    case object Failure extends HealthStatus
    case object Unknown extends HealthStatus
    case object JobNotFound extends HealthStatus
    case class ScriptFailure(message: String) extends HealthStatus
  }

  private [HealthChecker] case class Job(name: String,
                                         var status: HealthStatus,
                                         var token: Option[Cancellable] = None,
                                         var output: Option[String] = None,
                                         var listeners: Seq[ActorRef] = Nil)

  class ProcessGrabber extends ProcessLogger {
    private[this] val sb = new StringBuilder
    def buffer[T](f: => T): T = f
    def error(s: => String): Unit = sb append s
    def info(s: => String): Unit = sb append s
    def err(s: => String): Unit = error(s)
    def out(s: => String): Unit = info(s)

    def result: String = sb.toString
  }

  case class HealthStatusChanged(from: HealthStatus, to: HealthStatus)
}

import com.thing2x.smqd.util.HealthChecker._

class HealthChecker(name: String, smqd: Smqd, config: Config) extends Service (name, smqd, config) with StrictLogging {

  private var jobs: Seq[Job] = Seq.empty

  override def start(): Unit = {
    val checkListConfs = config.getConfigList("check_list").asScala

    jobs = checkListConfs.map { conf =>
      val name = conf.getString("name")
      val command = conf.getString("command")
      val expectExit = conf.getInt("expect_exit")
      val interval = Duration.fromNanos(conf.getDuration("interval").toNanos)

      implicit val ec: ExecutionContext = smqd.Implicit.gloablDispatcher

      // 초기 상태: Unknown
      val job = Job(name, HealthStatus.Unknown)

      val runnable = new Runnable{
        override def run(): Unit = {
          val previousStatus = job.status
          val grabber = new ProcessGrabber()

          try {
            val result = command.!(grabber)
            if (result == expectExit) job.status = HealthStatus.Success
            else job.status = HealthStatus.Failure
          }
          catch {
            case e: Exception =>
              job.status = HealthStatus.ScriptFailure(e.getMessage)
          }


          job.output = Some(grabber.result)

          // status changed, or script failure
          if (previousStatus != job.status || job.status.isInstanceOf[HealthStatus.ScriptFailure]) {
            val noti = HealthStatusChanged(previousStatus, job.status)
            // notify status change to listeners
            job.listeners.foreach( _.tell(noti, Actor.noSender) )

            if (job.status.isInstanceOf[HealthStatus.ScriptFailure])
              logger.warn(s"HealthChecker '$name' script failure: ${job.status}")
            else
              logger.debug(s"HealthChecker '$name' status changed from '$previousStatus' to '${job.status}' ${job.output}")
          }
        }
      }

      job.token = Option(smqd.Implicit.system.scheduler.schedule(interval, interval, runnable))
      job
    }
  }

  override def stop(): Unit = {
    jobs.foreach(job => job.token.map(_.cancel))
  }

  def getStatus(name: String): HealthStatus = {
    jobs.find(_.name == name) match {
      case Some(job) =>
        job.status
      case None =>
        HealthStatus.JobNotFound
    }
  }

  /**
    * whenever the status of 'name' process has changed, HealthChecker will fire a notification message
    * `HealthStatusChanged` to the 'actor'
    */
  def addListener(name: String, actor: ActorRef): HealthStatus = {
    jobs.find(_.name == name) match {
      case Some(job) =>
        job.listeners :+= actor
        job.status
      case None =>
        HealthStatus.JobNotFound
    }
  }
}
