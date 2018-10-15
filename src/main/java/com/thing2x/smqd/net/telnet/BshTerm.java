/*
 * Copyright 2018 UANGEL
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
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
