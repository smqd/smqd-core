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

import com.thing2x.smqd.fault.BadUserPassword
import com.thing2x.smqd.{SmqResult, SmqSuccess, UserDelegate}

import scala.concurrent.{ExecutionContext, Future, Promise}

// 2018. 7. 18. - Created by Kwon, Yeong Eon

/**
  *
  */
class DefaultUserDelegate extends UserDelegate {

  override def userLogin(username: String, password: String)(implicit ec: ExecutionContext): Future[SmqResult] = {
    Future {
      if (username == "admin" && password == "password") {
        SmqSuccess
      }
      else {
        BadUserPassword("Bad username or password ")
      }
    }
  }

  override def userCreate(username: String, password: String)(implicit ec: ExecutionContext): Future[SmqResult] = ???

  override def userUpdate(username: String, password: String)(implicit ec: ExecutionContext): Future[SmqResult] = ???

  override def userRemove(username: String)(implicit ec: ExecutionContext): Future[SmqResult] = ???
}
