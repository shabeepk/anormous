package com.anormous.logger;

import android.util.Log;

public class Logger
{
	public static enum Level
	{
		DEBUG, VERBOSE, INFO, WARN, ERROR, WTF
	}

	public static Level LEVEL = Level.WARN;

	private static boolean canLog(Level level)
	{
		return level.ordinal() >= LEVEL.ordinal();
	}

	public static void i(String tag, String msg)
	{
		if (canLog(Level.INFO))
			Log.i(tag, msg);
	}

	public static void i(String tag, String msg, Throwable tr)
	{
		if (canLog(Level.INFO))
			Log.i(tag, msg, tr);
	}

	public static void e(String tag, String msg)
	{
		if (canLog(Level.ERROR))
			Log.e(tag, msg);
	}

	public static void e(String tag, String msg, Throwable tr)
	{
		if (canLog(Level.ERROR))
			Log.e(tag, msg, tr);
	}

	public static void d(String tag, String msg)
	{
		if (canLog(Level.DEBUG))
			Log.d(tag, msg);
	}

	public static void d(String tag, String msg, Throwable tr)
	{
		if (canLog(Level.DEBUG))
			Log.d(tag, msg, tr);
	}

	public static void v(String tag, String msg)
	{
		if (canLog(Level.VERBOSE))
			Log.v(tag, msg);
	}

	public static void v(String tag, String msg, Throwable tr)
	{
		if (canLog(Level.VERBOSE))
			Log.v(tag, msg, tr);
	}

	public static void w(String tag, String msg)
	{
		if (canLog(Level.WARN))
			Log.w(tag, msg);
	}

	public static void w(String tag, String msg, Throwable tr)
	{
		if (canLog(Level.WARN))
			Log.w(tag, msg, tr);
	}

	public static void wtf(String tag, String msg)
	{
		if (canLog(Level.WTF))
			Log.wtf(tag, msg);
	}

	public static void wtf(String tag, String msg, Throwable tr)
	{
		if (canLog(Level.WTF))
			Log.wtf(tag, msg, tr);
	}
}
