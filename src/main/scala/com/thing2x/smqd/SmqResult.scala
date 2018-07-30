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

package com.thing2x.smqd

// 2018. 6. 3. - Created by Kwon, Yeong Eon

/**
  * Top level trait of all kind of success and fault messages in smqd
  */
sealed trait SmqResult {
}

object SmqSuccess extends SmqResult

trait SmqFailure extends SmqResult

case class SmqSuccessWithData(info:Map[String,String]) extends SmqResult
