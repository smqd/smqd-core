package com.thing2x.smqd.session

// 2018. 9. 6. - Created by Kwon, Yeong Eon

/**
  * Enumeration for state of a session
  */
object SessionState extends Enumeration {
  type SessionState = super.Value
  val Failed, Initiated, ConnectReceived, ConnectAcked = Value
}

