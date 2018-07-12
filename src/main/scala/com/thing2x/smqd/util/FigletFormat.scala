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

// 2018. 7. 10. - Created by Kwon, Yeong Eon

object FigletFormat {
  def figlet(text: String): String = {
    try {
      val url = getClass.getClassLoader.getResource("figlet/standard.flf")
      val font = new FigletFont(url)
      val result = font.convert(text, false, true, 40)
      if(result.endsWith("\n\n")) result.substring(0, result.length - 3) else result
    } catch {
      case _: Throwable =>
        ""
    }
  }
}
