/*
 * Created on 2005. 10. 19
 */
package com.thing2x.smqd.net.telnet;

import java.io.IOException;

import net.wimpi.telnetd.io.BasicTerminalIO;
import net.wimpi.telnetd.io.toolkit.Editfield;

public class BshTermTelnet extends BshTerm
{
	private BasicTerminalIO term;

	public BshTermTelnet(BasicTerminalIO term)
			throws IOException
	{
		this.term = term;

		// clear the screen and start from zero
		term.eraseScreen();
		term.homeCursor();
	}

	public synchronized void write(char ch) throws IOException
	{
		term.write(ch);
	}

	public void write(String str) throws IOException
	{
		term.write(str);
	}
	
	public void println(String str) throws IOException
	{
		term.write(str);
		term.write("\r\n");
	}

	public void flush() throws IOException
	{
		term.flush();
	}

	public String read() throws IOException
	{
		Editfield ef = new Editfield(term, "confirm", 1);
		ef.run();
		term.write("\r\n");
		term.flush();
		return ef.getValue();
	}

	public void setForegroundColor(int color) throws IOException
	{
		term.setForegroundColor(color);
	}

	public void setBackgroundColor(int color) throws IOException
	{
		term.setBackgroundColor(color);
	}

	public void setBold(boolean b) throws IOException
	{
		term.setBold(b);
	}

	public void setItalic(boolean b) throws IOException
	{
		term.setItalic(b);
	}

	public void setUnderlined(boolean b) throws IOException
	{
		term.setUnderlined(b);
	}

	public void setBlink(boolean b) throws IOException
	{
		term.setBlink(b);
	}

	public void bell() throws IOException
	{
		term.bell();
	}

}
