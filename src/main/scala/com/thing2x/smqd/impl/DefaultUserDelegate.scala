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

package com.thing2x.smqd.impl

import java.io._
import java.util.Properties

import com.thing2x.smqd.UserDelegate.User
import com.thing2x.smqd.fault.{UserAlreadyExists, UserNotExists, UserWrongPassword}
import com.thing2x.smqd.util.SslUtil
import com.thing2x.smqd.{SmqResult, SmqSuccess, UserDelegate}
import com.typesafe.scalalogging.StrictLogging

import scala.collection.JavaConverters._
import scala.concurrent.{ExecutionContext, Future}

// 2018. 7. 18. - Created by Kwon, Yeong Eon

/**
  *
  */
class DefaultUserDelegate(passwdFile: File) extends UserDelegate with StrictLogging {

  logger.info(s"passwd file: ${passwdFile.getPath}")

  val lock = new Object()
  private var checkDefault = false

  private def load: Properties = {
    if (!checkDefault) createDefault
    val p = new Properties()
    val r = new InputStreamReader(new FileInputStream(passwdFile))
    p.load(r)
    r.close()
    p
  }

  private def store(p: Properties): Unit = {
    if (!checkDefault) createDefault
    val w = new OutputStreamWriter(new FileOutputStream(passwdFile))
    p.store(w, "--passwords--")
    w.close()
  }

  private def createDefault: Unit = {
    lock.synchronized {
      checkDefault = true
      if (!passwdFile.exists) {
        val default = new Properties()
        default.setProperty("admin", SslUtil.getSha1Hash("password")) // echo -n "password" | openssl sha1
        store(default)
      }
    }
  }

  override def userLogin(username: String, password: String)(implicit ec: ExecutionContext): Future[SmqResult] = {
    Future {
      lock.synchronized {
        val pw = load.getProperty(username)
        //logger.trace(s"stored pw=${pw} vs. user pw=${SslUtil.getSha1Hash(password)}")
        if (pw != null && pw == SslUtil.getSha1Hash(password)) {
          SmqSuccess
        }
        else {
          UserWrongPassword("Bad username or password ")
        }
      }
    }
  }

  override def userList(implicit ec: ExecutionContext): Future[Seq[UserDelegate.User]] = {
    Future {
      val props = load
      val us = props.stringPropertyNames().asScala
      us.map( k => (k, props.getProperty(k)) ).map(u => User(u._1, u._2)).toSeq
    }
  }

  override def userCreate(user: UserDelegate.User)(implicit ec: ExecutionContext): Future[SmqResult] = {
    Future {
      lock.synchronized {
        val props = load
        if (props.getProperty(user.username) != null) {
          UserAlreadyExists(user.username)
        }
        else {
          props.setProperty(user.username, SslUtil.getSha1Hash(user.password))
          store(props)
          SmqSuccess
        }
      }
    }
  }

  override def userUpdate(user: UserDelegate.User)(implicit ec: ExecutionContext): Future[SmqResult] = {
    Future {
      lock.synchronized {
        val props = load
        if (props.getProperty(user.username) == null) {
          UserNotExists(user.username)
        }
        else {
          props.setProperty(user.username, SslUtil.getSha1Hash(user.password))
          store(props)
          SmqSuccess
        }
      }
    }
  }

  override def userDelete(username: String)(implicit ec: ExecutionContext): Future[SmqResult] = {
    Future {
      lock.synchronized {
        val props = load
        if (props.getProperty(username) == null) {
          SmqSuccess
        }
        else {
          props.remove(username)
          store(props)
          SmqSuccess
        }
      }
    }
  }
}
