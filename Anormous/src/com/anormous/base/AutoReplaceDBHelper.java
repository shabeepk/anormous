package com.anormous.base;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import android.content.Context;
import android.database.SQLException;
import android.database.sqlite.SQLiteDatabase;
import android.util.Log;

/**
 * @author sabdullah
 * 
 */
public abstract class AutoReplaceDBHelper extends AnormousGenericDBHelper
{
	private boolean dbCopyHappened = false;

	protected AutoReplaceDBHelper(Context context, String dbName, int dbVersion)
	{
		super(context, dbName, dbVersion);
	}

	@Override
	public synchronized SQLiteDatabase getReadableDatabase()
	{
		myDataBase = super.getReadableDatabase();

		if (dbCopyHappened)
		{
			myDataBase = openDatabase(SQLiteDatabase.OPEN_READONLY);
			Log.i(this.getClass().toString(), "Setting Database Version");
			myDataBase.setVersion(dbVersion);
		}

		dbCopyHappened = false;

		return myDataBase;
	}

	@Override
	public synchronized SQLiteDatabase getWritableDatabase()
	{
		myDataBase = super.getWritableDatabase();

		if (dbCopyHappened)
		{
			myDataBase = openDatabase(SQLiteDatabase.OPEN_READWRITE);
			Log.i(this.getClass().toString(), "Setting Database Version");
			myDataBase.setVersion(dbVersion);
		}

		dbCopyHappened = false;

		return myDataBase;
	}

	@Override
	public void onCreate(SQLiteDatabase db)
	{
		Log.i(this.getClass().toString(), "Database OnCreate Called");

		try
		{
			copyDBFileFromAssetsToAppFolder(db);
		}
		catch (IOException ex)
		{
			Log.e(this.getClass().toString(), "Error Creating Database", ex);

			throw new Error("Error copying database");
		}
	}

	@Override
	public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion)
	{
		Log.i(this.getClass().toString(), "Database OnUpgrade Called");
		Log.i(this.getClass().toString(), "oldVersion = " + oldVersion + ", newVersion = " + newVersion);

		if (oldVersion < newVersion)
		{
			// Older version of DB Found
			Log.i(this.getClass().toString(), "Refreshing DB to version : " + newVersion);

			dbContext.deleteDatabase(dbName);

			onCreate(db);
		}
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

	private void copyDBFileFromAssetsToAppFolder(SQLiteDatabase db) throws IOException
	{
		Log.i(this.getClass().toString(), "Copying pre created database to folder : " + dbPath);

		dbCopyHappened = true;
		// Open your local db as the input stream
		InputStream myInput = dbContext.getAssets().open(dbName);

		// Path to the just created empty db
		String outFileName = dbPath + dbName;

		File currentDB = new File(outFileName);

		Log.i(this.getClass().toString(), "DB File Existance Status : " + currentDB.exists());

		currentDB.delete();

		// Open the empty db as the output stream
		OutputStream myOutput = new FileOutputStream(outFileName);

		// transfer bytes from the inputfile to the outputfile
		byte[] buffer = new byte[10240];
		int length;
		while ((length = myInput.read(buffer)) > 0)
		{
			myOutput.write(buffer, 0, length);
		}

		// Close the streams
		myOutput.flush();
		myOutput.close();
		myInput.close();
	}
}