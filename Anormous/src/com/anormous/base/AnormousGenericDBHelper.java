package com.anormous.base;

import android.content.Context;
import android.database.SQLException;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

/**
 * @author sabdullah
 * 
 */
public abstract class AnormousGenericDBHelper extends SQLiteOpenHelper
{
	protected static String DB_PATH_PREFIX = "/data/data/";
	protected static String DB_PATH_SUFFIX = "/databases/";

	protected String dbPath;
	protected String dbName;
	protected Integer dbVersion;
	protected SQLiteDatabase myDataBase;
	protected Context dbContext;

	protected AnormousGenericDBHelper(Context context, String dbName, int dbVersion)
	{
		super(context, dbName, null, dbVersion);

		this.dbPath = DB_PATH_PREFIX + context.getApplicationContext().getPackageName() + DB_PATH_SUFFIX;
		this.dbName = dbName;
		this.dbVersion = dbVersion;
		this.dbContext = context;
	}

	@Override
	public synchronized void close()
	{
		if (myDataBase != null)
			myDataBase.close();

		super.close();
	}

	protected SQLiteDatabase openDatabase(int flags) throws SQLException
	{
		// Open the database
		String myPath = dbPath + dbName;
		myDataBase = SQLiteDatabase.openDatabase(myPath, null, flags);
		return myDataBase;
	}
}