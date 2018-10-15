package com.thing2x.smqd.net.telnet;
// 10/15/18 - Created by Kwon, Yeong Eon

import bsh.Interpreter;

/**
 *
 */
public interface BshShellDelegate {
  public void prepare(BshShell shell, Interpreter bshInterpreter);
  public String[] scriptPaths(BshShell shell);
}
