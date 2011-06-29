package com.anormous.helper;

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.util.Log;

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
		Log.i(this.getClass().toString(), "Database OnCreate Called");
	}

	@Override
	public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion)
	{
		Log.i(this.getClass().toString(), "Database OnUpgrade Called");
		Log.i(this.getClass().toString(), "oldVersion = " + oldVersion + ", newVersion = " + newVersion);
	}
}