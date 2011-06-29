package com.anormous.base;

import java.io.Serializable;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;

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

	public synchronized void open(int action) throws SQLiteException
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

	public synchronized void close()
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

	public synchronized void begin() throws SQLiteException
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

	public synchronized void end()
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
		boolean opened = autoOpen(WRITE);

		EntityMapping mapping = mapper.mapClass(bean.getClass());

		syncClassAndTableSchema(mapping);

		ContentValues values = mapper.beanToValues(bean);

		db.insert(mapping.getMappedTableName(), null, values);

		autoClose(opened);
	}

	public synchronized Object update(Object bean, String whereClause, String[] whereArgs) throws AnormousException
	{
		boolean opened = autoOpen(WRITE);

		EntityMapping mapping = mapper.mapClass(bean.getClass());

		syncClassAndTableSchema(mapping);

		ContentValues values = mapper.beanToValues(bean);

		whereClause = forwardMapColumnNames(whereClause, mapping.getMappedColumns().values());

		db.update(mapping.getMappedTableName(), values, whereClause, whereArgs);

		autoClose(opened);

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
		boolean opened = autoOpen(WRITE);

		EntityMapping mapping = mapper.mapClass(bean.getClass());

		syncClassAndTableSchema(mapping);

		whereClause = forwardMapColumnNames(whereClause, mapping.getMappedColumns().values());

		db.delete(mapping.getMappedTableName(), whereClause, whereArgs);

		autoClose(opened);
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
		boolean opened = autoOpen(WRITE);

		db.execSQL(sql);

		autoClose(opened);
	}

	public synchronized List<String[]> rawQuery(String sql, String[] selectionArgs) throws AnormousException
	{
		boolean opened = autoOpen(READ);

		Cursor cursor = db.rawQuery(sql, selectionArgs);

		List<String[]> result = new ArrayList<String[]>();

		while (cursor.moveToNext())
		{
			String[] resultArray = new String[cursor.getColumnCount()];

			for (int i = 0; i < resultArray.length; i++)
			{
				resultArray[i] = cursor.getString(i);
			}

			result.add(resultArray);
		}

		autoClose(opened);

		return result;
	}

	public synchronized List<Object> selectAll(Class entityClass)
	{
		return select(entityClass, null, null, null, null, null);
	}

	public synchronized List<Object> select(Class entityClass, String whereClause, String whereArgs[])
	{
		return select(entityClass, whereClause, whereArgs, null, null, null);
	}

	public synchronized List<Object> select(Class entityClass, String whereClause, String whereArgs[], String groupBy, String having)
	{
		return select(entityClass, whereClause, whereArgs, groupBy, having, null);
	}

	public synchronized List<Object> select(Class entityClass, String whereClause, String[] whereArgs, String groupBy, String having, String orderBy)
	{
		List<Object> result = null;

		boolean opened = autoOpen(READ);

		EntityMapping mapping = mapper.mapClass(entityClass);

		String tableName = mapping.getMappedTableName();

		Map<Method, ColumnMapping> mappedColumns = mapping.getMappedColumns();
		Collection<ColumnMapping> columnMappings = mappedColumns.values();

		String[] columns = new String[mappedColumns.size()];

		int count = 0;

		for (ColumnMapping columnMapping : columnMappings)
		{
			columns[count++] = columnMapping.getColumnName();
		}

		whereClause = forwardMapColumnNames(whereClause, columnMappings);

		Cursor cursor = db.query(tableName, columns, whereClause, whereArgs, groupBy, having, orderBy);

		while (cursor.moveToNext())
		{
			Object objBean = mapper.valuesToBean(cursor, entityClass);

			if (objBean != null)
			{
				result.add(objBean);
			}
		}

		autoClose(opened);

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

	private String forwardMapColumnNames(String query, Collection<ColumnMapping> mappings)
	{
		String result = query;

		for (ColumnMapping columnMapping : mappings)
		{
			String propertyName = columnMapping.getColumnMethod().getName().substring(3);
			propertyName = Character.toLowerCase(propertyName.charAt(0)) + propertyName.substring(1);

			result = result.replaceAll(propertyName, columnMapping.getColumnName());
		}

		return result;
	}

	private boolean autoOpen(int mode)
	{
		if (!db.isOpen())
		{
			open(mode);

			return true;
		}

		if (mode == WRITE)
			begin();

		return false;
	}

	private boolean autoClose(boolean opened)
	{
		if (!opened)
		{
			end();

			close();

			return true;
		}

		return false;
	}
}