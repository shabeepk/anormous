package com.anormous.helper;

import com.anormous.logger.Logger;

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;

/**
 * @author sabdullah
 * 
 */
public abstract class DefaultDBHelper extends AnormousGenericDBHelper
{
	protected DefaultDBHelper(Context context, String dbName, int dbVersion)
	{
		super(context, dbName, dbVersion);
	}

	@Override
	public void onCreate(SQLiteDatabase db)
	{
		Logger.i(this.getClass().toString(), "Database OnCreate Called");
	}

	@Override
	public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion)
	{
		Logger.i(this.getClass().toString(), "Database OnUpgrade Called");
		Logger.i(this.getClass().toString(), "oldVersion = " + oldVersion + ", newVersion = " + newVersion);
	}
}