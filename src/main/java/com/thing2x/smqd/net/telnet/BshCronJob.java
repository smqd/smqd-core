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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

//import x3.util.cron.CronJobBase;
import bsh.Interpreter;

/**
 * CronJob ����ü�� "script" �Ķ���Ϳ� ������ beanshell ����� �����Ѵ�.
 * 
 */
public class BshCronJob // extends CronJobBase
{
	private static Logger logger = LoggerFactory.getLogger(BshCronJob.class);

	private String bshScript;
	private ClassLoader classLoader;

//	@Override
	public void init()
	{
		// this.bshScript = getCronJobContext().getCronJobConfig().getInitParameter("script");
	}

//	@Override
	public void close()
	{
	}

//	@Override
	public void runJob() throws Exception
	{
		if (bshScript == null)
		{
			logger.debug("Script is not set.");
			return;
		}

		try
		{
			Interpreter bshInterpreter = new Interpreter();
			bshInterpreter.setClassLoader(getClassLoader());

			BshTerm term = new BshTermBuffer();
			BshProcessor bshProc = new BshProcessor(bshInterpreter, term, null);

			bshInterpreter.set("TERM", term);
			bshInterpreter.set("SHELL", bshProc);

			logger.debug("Script[{}] running.", bshScript);

			bshProc.process(bshScript, null, -1);

			String reply = bshProc.getTerminal().toString();
			((BshTermBuffer) bshProc.getTerminal()).reset();

			logger.debug("Script[{}] done:{}", bshScript, reply);
		}
		catch (IOException e)
		{
			logger.error("CronBshJob [{}] error", bshScript, e);
		}
	}

	private ClassLoader getClassLoader()
	{
		if (classLoader != null)
			return classLoader;

		ClassLoader cl = Thread.currentThread().getContextClassLoader();
		if (cl != null)
			return cl;

		return getClass().getClassLoader();
	}
}
