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
