package com.anormous.base;

import java.io.Serializable;
import java.lang.reflect.InvocationTargetException;
import java.util.List;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteException;
import android.database.sqlite.SQLiteOpenHelper;
import android.util.Log;

import com.anormous.entity.DefaultEntityMapper;
import com.anormous.entity.EntityMapping;
import com.anormous.entity.IEntityMapper;
import com.anormous.entity.EntityMapping.ColumnMapping;
import com.anormous.error.AnormousException;

public class AnormousSession
{
	private String TAG = this.getClass().toString();
	private SQLiteDatabase db;
	private final SQLiteOpenHelper dbHelper;
	private final IEntityMapper mapper;

	private boolean inTransaction = false;
	private boolean autoCommit = true;
	private int currentOpenMode = -1;

	private static AnormousSession instance;

	public final static int READ = 1;
	public final static int WRITE = 2;

	public synchronized static AnormousSession getInstance(AnormousGenericDBHelper dbHelper, IEntityMapper mapper)
	{
		if (instance == null)
		{
			synchronized (AnormousSession.class)
			{
				if (instance == null)
				{
					if (mapper != null)
					{
						instance = new AnormousSession(dbHelper, mapper);
					}
					else
					{
						instance = new AnormousSession(dbHelper);
					}
				}
			}
		}

		return instance;
	}

	public synchronized static AnormousSession getInstance(AnormousGenericDBHelper dbHelper)
	{
		return getInstance(dbHelper, null);
	}

	private AnormousSession(AnormousGenericDBHelper dbHelper)
	{
		this(dbHelper, new DefaultEntityMapper());
	}

	private AnormousSession(AnormousGenericDBHelper dbHelper, IEntityMapper mapper)
	{
		this.dbHelper = dbHelper;
		this.mapper = mapper;
	}

	public int getCurrentOpenMode()
	{
		return currentOpenMode;
	}

	public boolean isInTransaction()
	{
		return inTransaction;
	}

	public boolean isAutoCommit()
	{
		return autoCommit;
	}

	public void setAutoCommit(boolean autoCommit)
	{
		this.autoCommit = autoCommit;
	}

	public void open(int action) throws SQLiteException
	{
		if (db == null || !db.isOpen())
		{
			try
			{
				if (action == READ)
				{
					db = dbHelper.getReadableDatabase();
				}
				else if (action == WRITE)
				{
					db = dbHelper.getWritableDatabase();
				}
			}
			catch (SQLiteException ex)
			{
				Log.e(TAG, "Error opening database", ex);
			}
		}
	}

	public void close()
	{
		if (db != null && db.isOpen())
		{
			if (db.inTransaction() && inTransaction)
			{
				db.setTransactionSuccessful();
				db.endTransaction();
			}

			db.close();
			inTransaction = false;
		}
	}

	public void begin() throws SQLiteException
	{
		if (db != null && db.isOpen())
		{
			try
			{
				db.beginTransaction();
				inTransaction = true;
			}
			catch (SQLiteException ex)
			{
				Log.e(TAG, "Error opening database", ex);
			}
		}
	}

	public void end()
	{
		if (db != null && db.isOpen() && db.inTransaction() && inTransaction)
		{
			db.setTransactionSuccessful();
			db.endTransaction();

			inTransaction = false;
		}
	}

	public synchronized void insert(Object bean) throws AnormousException
	{
		open(WRITE);

		EntityMapping mapping = mapper.mapClass(bean.getClass());

		syncClassAndTableSchema(mapping);

		ContentValues values = mapper.beanToValues(bean);

		db.insert(mapping.getMappedTableName(), null, values);

		close();
	}

	public synchronized Object update(Object bean, String whereClause, String[] whereArgs) throws AnormousException
	{
		open(WRITE);

		EntityMapping mapping = mapper.mapClass(bean.getClass());

		syncClassAndTableSchema(mapping);

		ContentValues values = mapper.beanToValues(bean);

		db.update(mapping.getMappedTableName(), values, whereClause, whereArgs);

		close();

		return bean;
	}

	public synchronized Object update(Object bean) throws AnormousException
	{
		EntityMapping mapping = mapper.mapClass(bean.getClass());

		if (mapping.getIdMapping() != null)
		{
			String value;
			try
			{
				value = mapping.getIdMapping().getColumnMethod().invoke(bean).toString();
			}
			catch (IllegalArgumentException e)
			{
				throw new AnormousException("Error resolving bean id value", e);
			}
			catch (IllegalAccessException e)
			{
				throw new AnormousException("Error resolving bean id value", e);
			}
			catch (InvocationTargetException e)
			{
				throw new AnormousException("Error resolving bean id value", e);
			}

			return update(bean, mapping.getIdMapping().getColumnName() + " = ?", new String[] { value });
		}
		else
		{
			throw new AnormousException("Error updating object, can not find an id mapping for the entity");
		}
	}

	public synchronized void delete(Object bean, String whereClause, String[] whereArgs) throws AnormousException
	{
		open(WRITE);

		EntityMapping mapping = mapper.mapClass(bean.getClass());

		syncClassAndTableSchema(mapping);

		db.delete(mapping.getMappedTableName(), whereClause, whereArgs);

		close();
	}

	public synchronized void delete(Object bean) throws AnormousException
	{
		EntityMapping mapping = mapper.mapClass(bean.getClass());

		if (mapping.getIdMapping() != null)
		{
			String value;
			try
			{
				value = mapping.getIdMapping().getColumnMethod().invoke(bean).toString();
			}
			catch (IllegalArgumentException e)
			{
				throw new AnormousException("Error resolving bean id value", e);
			}
			catch (IllegalAccessException e)
			{
				throw new AnormousException("Error resolving bean id value", e);
			}
			catch (InvocationTargetException e)
			{
				throw new AnormousException("Error resolving bean id value", e);
			}

			delete(bean, mapping.getIdMapping().getColumnName() + " = ?", new String[] { value });
		}
		else
		{
			throw new AnormousException("Error updating object, can not find an id mapping for the entity");
		}
	}

	public synchronized void executeUpdate(String sql) throws AnormousException
	{
		boolean openAndClose = !db.isOpen();

		if (openAndClose)
		{
			open(WRITE);

			begin();
		}

		db.execSQL(sql);

		if (openAndClose)
		{
			end();

			close();
		}
	}

	public synchronized Cursor rawQuery(String sql, String[] selectionArgs) throws AnormousException
	{
		boolean openAndClose = !db.isOpen();

		if (openAndClose)
		{
			open(WRITE);
		}

		Cursor result = db.rawQuery(sql, selectionArgs);

		if (openAndClose)
		{
			close();
		}

		return result;
	}

	public synchronized List<Object> select(String query, Class entityClass)
	{
		List<Object> result = null;

		open(READ);

		EntityMapping mapping = mapper.mapClass(entityClass);

		close();

		return result;
	}

	private boolean syncClassAndTableSchema(EntityMapping mapping)
	{
		boolean tableExists = db.rawQuery("SELECT name FROM sqlite_master WHERE type='table' AND name= ?;", new String[] { mapping.getMappedTableName() }).moveToFirst();

		if (!tableExists)
		{
			StringBuffer sql = new StringBuffer("CREATE TABLE " + mapping.getMappedTableName() + " ( ");
			boolean first = true;

			for (ColumnMapping columnMapping : mapping.getMappedColumns().values())
			{
				if (!first)
					sql.append(",");

				sql.append(columnMapping.getColumnName());
				sql.append(" ");
				sql.append(columnMapping.getColumnType());
				sql.append(columnMapping.getColumnSize().length() > 0 ? "(" + columnMapping.getColumnSize() + ")" : "");
				sql.append(columnMapping.getDefaultValue().length() > 0 ? " DEFAULT " + columnMapping.getDefaultValue() : "");

				first = false;
			}

			sql.append(");");

			db.execSQL(sql.toString(), null);
		}

		return true;
	}
}