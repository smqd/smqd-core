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

package t2x.smqd.impl

import com.typesafe.scalalogging.StrictLogging
import t2x.smqd.fault.BadUserNameOrPassword
import t2x.smqd.{AuthDelegate, SmqResult, SmqSuccess}

import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global

/**
  * 2018. 5. 31. - Created by Kwon, Yeong Eon
  */
class DefaultAuthDelegate extends AuthDelegate with StrictLogging {

  override def authenticate(clientId: String, userName: Option[String], password: Option[Array[Byte]]): Future[SmqResult] = {

    Future {
      logger.debug(s"[$clientId] userName: $userName password: $password")

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
