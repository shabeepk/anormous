package com.anormous.base;

import android.content.Context;

/**
 * @author sabdullah
 * 
 */
public abstract class DefaultDBHelper extends AutoReplaceDBHelper
{
	protected DefaultDBHelper(Context context, String dbName, int dbVersion)
	{
		super(context, dbName, dbVersion);
	}
}