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
