/*
 * Created on 2005. 10. 19
 */
package com.thing2x.smqd.net.telnet;

import java.io.IOException;

public abstract class BshTerm
{
	public abstract void write(char ch) throws IOException;

	public abstract void write(String str) throws IOException;

	public abstract void println(String str) throws IOException;

	public abstract void flush() throws IOException;

	public abstract String read() throws IOException;

	public void setForegroundColor(int color) throws IOException
	{
	}

	public void setBackgroundColor(int color) throws IOException
	{
	}

	public void setBold(boolean b) throws IOException
	{
	}

	public void setItalic(boolean b) throws IOException
	{
	}

	public void setUnderlined(boolean b) throws IOException
	{
	}

	public void setBlink(boolean b) throws IOException
	{
	}

	public void bell() throws IOException
	{
	}

	/**
	 * Black
	 */
	public static final int BLACK = 30;

	/**
	 * Red
	 */
	public static final int RED = 31;

	/**
	 * Green
	 */
	public static final int GREEN = 32;

	/**
	 * Yellow
	 */
	public static final int YELLOW = 33;

	/**
	 * Blue
	 */
	public static final int BLUE = 34;

	/**
	 * Magenta
	 */
	public static final int MAGENTA = 35;

	/**
	 * Cyan
	 */
	public static final int CYAN = 36;

	/**
	 * White
	 */
	public static final int WHITE = 37;
}
