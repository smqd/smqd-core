package com.thing2x.smqd.net.telnet

// 10/25/18 - Created by Kwon, Yeong Eon

/**
  *
  */
trait ScShellDelegate {
  def beforeShellStart(shell: ScShell): Unit
  def afterShellStop(shell: ScShell): Unit
}
