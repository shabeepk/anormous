package com.anormous.session;

import java.lang.reflect.InvocationTargetException;
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

import com.anormous.error.AnormousException;
import com.anormous.error.DuplicateKeyViolationException;
import com.anormous.helper.AnormousGenericDBHelper;
import com.anormous.mapper.DefaultEntityMapper;
import com.anormous.mapper.EntityMapping;
import com.anormous.mapper.IEntityMapper;
import com.anormous.mapper.EntityMapping.ColumnMapping;
import com.anormous.mapper.EntityMapping.Property;

public class AnormousSession
{
	@SuppressWarnings("unused")
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

	public synchronized void open(int action) throws AnormousException
	{
		try
		{
			if (db == null || !db.isOpen())
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
		}
		catch (Exception ex)
		{
			throw new AnormousException("Database open operation failed", ex);
		}
	}

	public synchronized void close() throws AnormousException
	{
		try
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
		catch (Exception ex)
		{
			throw new AnormousException("Database close operation failed", ex);
		}
	}

	private boolean autoOpened = false;

	public synchronized void begin() throws AnormousException
	{
		try
		{
			if (db == null || !db.isOpen())
			{
				open(WRITE);
				autoOpened = true;
			}

			if (db != null && db.isOpen() && !inTransaction)
			{
				db.beginTransaction();
				inTransaction = true;
			}
		}
		catch (Exception ex)
		{
			throw new AnormousException("Transaction begin failed", ex);
		}
	}

	public synchronized void end() throws AnormousException
	{
		try
		{
			if (db != null && db.isOpen() && db.inTransaction() && inTransaction)
			{
				db.setTransactionSuccessful();
				db.endTransaction();

				inTransaction = false;
			}

			if (autoOpened)
			{
				autoOpened = false;

				close();
			}
		}
		catch (Exception ex)
		{
			throw new AnormousException("Transaction end failed", ex);
		}
	}

	public synchronized void rollback() throws AnormousException
	{
		try
		{
			if (db != null && db.isOpen() && db.inTransaction() && inTransaction)
			{
				db.endTransaction();

				inTransaction = false;
			}

			if (autoOpened)
			{
				autoOpened = false;

				close();
			}
		}
		catch (Exception ex)
		{
			throw new AnormousException("Transaction rollback failed", ex);
		}
	}

	public synchronized void insert(Object bean) throws AnormousException
	{
		try
		{
			boolean opened = autoOpen(WRITE);

			EntityMapping<?> mapping = mapper.mapClass(bean.getClass());

			syncClassAndTableSchema(mapping);

			// Huge performance hit!!! Lets rely on DB primary key enforcement.
			// Log.d(this.getClass().toString(),
			// "Checking duplicate constraint");
			// enforceUniqueConstraint(bean, mapping);

			ContentValues values = mapper.beanToValues(bean);

			Property property = mapping.getIdMapping().getProperty();

			Long rowId = db.insert(mapping.getMappedTableName(), null, values);

			if (rowId != null && rowId != -1)
			{
				List<?> result = select(mapping.getEntityClass(), "rowid = ?", new String[] { rowId.toString() });

				if (result.size() > 0)
				{
					property.setValueTo(bean, property.getValueFrom(bean));
				}
			}

			autoClose(opened);
		}
		catch (AnormousException ex)
		{
			throw ex;
		}
		catch (Exception ex)
		{
			throw new AnormousException("Database insert operation failed", ex);
		}
	}

	@SuppressWarnings("unused")
	private void enforceUniqueConstraint(Object bean, EntityMapping<?> mapping) throws AnormousException, DuplicateKeyViolationException
	{
		if (mapping.getIdMapping() != null && mapping.getIdMapping().isEnforce())
		{
			Object objectId;

			try
			{
				objectId = mapping.getIdMapping().getProperty().getValueFrom(bean);
			}
			catch (IllegalArgumentException e)
			{
				Log.e(this.getClass().toString(), "Error occured while performing insertion", e);

				throw new AnormousException("Error occured while performing insertion", e);
			}
			catch (IllegalAccessException e)
			{
				Log.e(this.getClass().toString(), "Error occured while performing insertion", e);

				throw new AnormousException("Error occured while performing insertion", e);
			}
			catch (InvocationTargetException e)
			{
				Log.e(this.getClass().toString(), "Error occured while performing insertion", e);

				throw new AnormousException("Error occured while performing insertion", e);
			}

			if (alreadyExists(mapping, objectId))
				throw new DuplicateKeyViolationException("Duplicate records for class : " + mapping.getEntityClass() + " with id : " + objectId);
		}
	}

	public synchronized <T> T update(T bean, String whereClause, String[] whereArgs) throws AnormousException
	{
		try
		{
			boolean opened = autoOpen(WRITE);

			EntityMapping<?> mapping = mapper.mapClass(bean.getClass());

			ContentValues values = mapper.beanToValues(bean);

			whereClause = mapper.forwardMapColumnNames(whereClause, bean.getClass());

			db.update(mapping.getMappedTableName(), values, whereClause, whereArgs);

			autoClose(opened);

			return bean;
		}
		catch (AnormousException ex)
		{
			throw ex;
		}
		catch (Exception ex)
		{
			throw new AnormousException("Database update operation failed", ex);
		}
	}

	public synchronized <T> T update(T bean) throws AnormousException
	{
		try
		{
			EntityMapping<?> mapping = mapper.mapClass(bean.getClass());

			if (mapping.getIdMapping() != null)
			{
				String value;

				try
				{
					value = mapping.getIdMapping().getProperty().getValueFrom(bean).toString();
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
		catch (AnormousException ex)
		{
			throw ex;
		}
		catch (Exception ex)
		{
			throw new AnormousException("Database update operation failed", ex);
		}
	}

	public synchronized void delete(Object bean, String whereClause, String[] whereArgs) throws AnormousException
	{
		try
		{
			boolean opened = autoOpen(WRITE);

			EntityMapping<?> mapping = mapper.mapClass(bean.getClass());

			whereClause = mapper.forwardMapColumnNames(whereClause, bean.getClass());

			db.delete(mapping.getMappedTableName(), whereClause, whereArgs);

			autoClose(opened);
		}
		catch (AnormousException ex)
		{
			throw ex;
		}
		catch (Exception ex)
		{
			throw new AnormousException("Database delete operation failed", ex);
		}
	}

	public synchronized void delete(Object bean) throws AnormousException
	{
		try
		{
			EntityMapping<?> mapping = mapper.mapClass(bean.getClass());

			if (mapping.getIdMapping() != null)
			{
				String value;

				try
				{
					value = mapping.getIdMapping().getProperty().getValueFrom(bean).toString();
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
		catch (AnormousException ex)
		{
			throw ex;
		}
		catch (Exception ex)
		{
			throw new AnormousException("Database delete operation failed", ex);
		}
	}

	public synchronized void executeUpdate(String sql) throws AnormousException
	{
		try
		{
			boolean opened = autoOpen(WRITE);

			db.execSQL(sql);

			autoClose(opened);

		}
		catch (AnormousException ex)
		{
			throw ex;
		}
		catch (Exception ex)
		{
			throw new AnormousException("Database update operation failed", ex);
		}
	}

	public synchronized List<String[]> rawQuery(String sql, String[] selectionArgs) throws AnormousException
	{
		try
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
		catch (AnormousException ex)
		{
			throw ex;
		}
		catch (Exception ex)
		{
			throw new AnormousException("Database query operation failed", ex);
		}
	}

	public synchronized <T> List<T> select(boolean distinct, Class<T> entityClass) throws AnormousException
	{
		return select(distinct, entityClass, null, null, null, null, null, null);
	}

	public synchronized <T> List<T> select(boolean distinct, Class<T> entityClass, Object id) throws AnormousException
	{
		return select(distinct, entityClass, "Id = ?", new String[] { id + "" }, null, null, null, null);
	}

	public synchronized <T> List<T> select(boolean distinct, Class<T> entityClass, String whereClause, String whereArgs[]) throws AnormousException
	{
		return select(distinct, entityClass, whereClause, whereArgs, null, null, null, null);
	}

	public synchronized <T> List<T> select(boolean distinct, Class<T> entityClass, String whereClause, String whereArgs[], String groupBy, String having) throws AnormousException
	{
		return select(distinct, entityClass, whereClause, whereArgs, groupBy, having, null, null);
	}

	public synchronized <T> List<T> select(boolean distinct, Class<T> entityClass, String whereClause, String whereArgs[], String groupBy, String having, String orderBy) throws AnormousException
	{
		return select(distinct, entityClass, whereClause, whereArgs, groupBy, having, orderBy, null);
	}

	public synchronized <T> List<T> select(Class<T> entityClass) throws AnormousException
	{
		return select(false, entityClass, null, null, null, null, null, null);
	}

	public synchronized <T> List<T> select(Class<T> entityClass, Object id) throws AnormousException
	{
		return select(false, entityClass, "Id = ?", new String[] { id + "" }, null, null, null, null);
	}

	public synchronized <T> List<T> select(Class<T> entityClass, String whereClause, String whereArgs[]) throws AnormousException
	{
		return select(false, entityClass, whereClause, whereArgs, null, null, null, null);
	}

	public synchronized <T> List<T> select(Class<T> entityClass, String whereClause, String whereArgs[], String groupBy, String having) throws AnormousException
	{
		return select(false, entityClass, whereClause, whereArgs, groupBy, having, null, null);
	}

	public synchronized <T> List<T> select(Class<T> entityClass, String whereClause, String[] whereArgs, String groupBy, String having, String orderBy) throws AnormousException
	{
		return select(false, entityClass, whereClause, whereArgs, groupBy, having, orderBy, null);
	}

	public synchronized <T> List<T> select(Class<T> entityClass, String whereClause, String[] whereArgs, String groupBy, String having, String orderBy, String limit) throws AnormousException
	{
		return select(false, entityClass, whereClause, whereArgs, groupBy, having, orderBy, limit);
	}

	public synchronized <T> List<T> select(boolean distinct, Class<T> entityClass, String whereClause, String[] whereArgs, String groupBy, String having, String orderBy, String limit) throws AnormousException
	{
		try
		{
			List<T> result = new ArrayList<T>();

			boolean opened = autoOpen(READ);

			EntityMapping<?> mapping = mapper.mapClass(entityClass);

			String tableName = mapping.getMappedTableName();

			Map<Property, ColumnMapping> mappedColumns = mapping.getMappedColumns();
			Collection<ColumnMapping> columnMappings = mappedColumns.values();

			String[] columns = new String[mappedColumns.size()];

			int count = 0;

			for (ColumnMapping columnMapping : columnMappings)
			{
				columns[count++] = columnMapping.getColumnName();
			}

			if (whereClause != null)
			{
				whereClause = mapper.forwardMapColumnNames(whereClause, entityClass);
			}

			if (groupBy != null)
			{
				groupBy = mapper.forwardMapColumnNames(groupBy, entityClass);
			}

			if (having != null)
			{
				having = mapper.forwardMapColumnNames(having, entityClass);
			}

			if (orderBy != null)
			{
				orderBy = mapper.forwardMapColumnNames(orderBy, entityClass);
			}

			Cursor cursor = db.query(distinct, tableName, columns, whereClause, whereArgs, groupBy, having, orderBy, limit);

			while (cursor.moveToNext())
			{
				T objBean = mapper.valuesToBean(cursor, entityClass);

				if (objBean != null)
				{
					result.add(objBean);
				}
			}

			autoClose(opened);

			return result;
		}
		catch (AnormousException ex)
		{
			throw ex;
		}
		catch (Exception ex)
		{
			throw new AnormousException("Database select operation failed", ex);
		}
	}

	private boolean alreadyExists(EntityMapping<?> mapping, Object id) throws SQLiteException
	{
		String tableName = mapping.getMappedTableName();

		Cursor cursor = db.query(tableName, new String[] { mapping.getIdMapping().getColumnName() }, mapping.getIdMapping().getColumnName() + " = ? ", new String[] { id + "" }, null, null, null);

		return cursor.moveToFirst();
	}

	private boolean syncClassAndTableSchema(EntityMapping<?> mapping) throws AnormousException
	{
		try
		{
			if (!mapping.isTableExists())
			{
				boolean tableExists = db.rawQuery("SELECT name FROM sqlite_master WHERE type='table' AND name= ?;", new String[] { mapping.getMappedTableName() }).moveToFirst();

				if (!tableExists)
				{
					db.execSQL(mapper.generateCreateTableStatement(mapping.getEntityClass()));
				}

				mapping.setTableExists(true);
			}

			return true;
		}
		catch (AnormousException ex)
		{
			throw ex;
		}
		catch (Exception ex)
		{
			throw new AnormousException("Error occured while trying to create table for entity : " + mapping.getEntityClass(), ex);
		}
	}

	private boolean autoOpen(int mode) throws AnormousException
	{
		if (db == null || !db.isOpen())
		{
			open(mode);

			return true;
		}

		if (!inTransaction)
		{
			if (mode == WRITE)
				begin();
		}

		return false;
	}

	private boolean autoClose(boolean opened) throws AnormousException
	{
		if (opened)
		{
			end();

			close();

			return true;
		}

		return false;
	}
}