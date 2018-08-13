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

// 2018. 8. 13. - Created by Kwon, Yeong Eon

/**
  *
  * @param nodeName node's name
  * @param api core-api address of the node
  * @param address node's clustering address that has format as "system@ipaddress:port"
  * @param status  clustering membership status
  * @param roles   list of roles
  * @param dataCenter data center name
  * @param isLeader true if the node is leader of the cluster
  */
case class NodeInfo(nodeName: String, api: Option[EndpointInfo], address: String, status: String, roles: Set[String], dataCenter: String, isLeader: Boolean)

case class EndpointInfo(address: Option[String], secureAddress: Option[String])

