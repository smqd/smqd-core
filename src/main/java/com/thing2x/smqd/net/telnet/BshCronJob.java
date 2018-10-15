/*
 * Created on Oct 31, 2005
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
