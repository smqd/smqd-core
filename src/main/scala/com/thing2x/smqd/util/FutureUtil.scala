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

import akka.actor.ActorSystem
import akka.util.Timeout
import akka.pattern._

import scala.concurrent.{ExecutionContext, Future}

// 2018. 8. 13. - Created by Kwon, Yeong Eon

/**
  *
  */
object FutureUtil {
  implicit class FutureWithTimeout[T](f: Future[T]) {
    def withTimeout(exception: => Throwable)(implicit timeout: Timeout, system: ActorSystem, ec: ExecutionContext): Future[T] = {
      Future.firstCompletedOf(Seq(f, after(timeout.duration, system.scheduler)(Future.failed(exception))))
    }
  }
}