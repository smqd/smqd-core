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

import com.thing2x.smqd.fault.BadUserNameOrPassword
import com.thing2x.smqd.{ClientDelegate, ClientId, SmqResult, SmqSuccess}
import com.typesafe.scalalogging.StrictLogging

import scala.concurrent.{ExecutionContext, Future}

/**
  * 2018. 5. 31. - Created by Kwon, Yeong Eon
  */
class DefaultClientDelegate extends ClientDelegate with StrictLogging {

  override def clientLogin(clientId: ClientId, userName: Option[String], password: Option[Array[Byte]])(implicit ec: ExecutionContext = scala.concurrent.ExecutionContext.Implicits.global): Future[SmqResult] = {

    Future {
      logger.debug(s"[$clientId] userName: $userName password: $password")

      if (userName.isDefined && password.isDefined) {
        if (userName.get == new String(password.get, "utf-8")) // username == password
          SmqSuccess
        else
          BadUserNameOrPassword(clientId.id, "Bad user name or password ")
      }
      else if (userName.isEmpty && password.isEmpty) { // allow anonymous
        SmqSuccess
      }
      else {
        SmqSuccess
      }
    }

  }
}
