/*
 * Created on 2005. 10. 19
 */
package com.thing2x.smqd.net.telnet;

import java.io.IOException;

public class BshTermBuffer extends BshTerm
{
	private StringBuffer buffer;

	public BshTermBuffer()
			throws IOException
	{
		this.buffer = new StringBuffer();
	}

	public synchronized void write(char ch) throws IOException
	{
		buffer.append(ch);
	}

	public synchronized void write(String str) throws IOException
	{
		buffer.append(str);
	}

	public synchronized void println(String str) throws IOException
	{
		buffer.append(str);
		buffer.append("\r\n");
	}

	public void flush() throws IOException
	{
	}

	public synchronized String toString()
	{
		return buffer.toString();
	}

	public void reset()
	{
		buffer = new StringBuffer();
	}

	public synchronized String read() throws IOException
	{
		return null;
	}
}
