/*
 * Created on 2005. 7. 21
 */
package com.thing2x.smqd.net.telnet;

import java.io.PrintStream;

public class BshPrintStream extends PrintStream
{
	public BshPrintStream(BshTerm term)
	{
		super(new BshOutputStream(term));
	}
}