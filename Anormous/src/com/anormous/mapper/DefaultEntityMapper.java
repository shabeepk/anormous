package com.anormous.mapper;

import java.lang.annotation.Annotation;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.LinkedHashMap;
import java.util.Map;

import android.content.ContentValues;
import android.database.Cursor;
import android.util.Log;

import com.anormous.annotation.Column;
import com.anormous.annotation.IdentityColumn;
import com.anormous.annotation.Table;
import com.anormous.error.AnormousException;
import com.anormous.error.InvalidClassTypeException;
import com.anormous.mapper.EntityMapping.ColumnMapping;
import com.anormous.mapper.EntityMapping.IdColumnMapping;

public class DefaultEntityMapper implements IEntityMapper
{
	private static LinkedHashMap<Class<?>, EntityMapping> mapperCache = new LinkedHashMap<Class<?>, EntityMapping>();

	public DefaultEntityMapper()
	{
		super();
	}

	@Override
	public EntityMapping mapClass(Class<?> entityClass) throws AnormousException
	{
		if (mapperCache.containsKey(entityClass))
		{
			return mapperCache.get(entityClass);
		}
		else
		{
			if (entityClass.isAnonymousClass() || entityClass.isArray() || entityClass.isEnum() || entityClass.isAnnotation() || entityClass.isLocalClass())
				throw new InvalidClassTypeException("Class [" + entityClass.getName() + "] can not be persisted. It may be because the class is one of anonymous class, array, enum, local or annotation.");

			EntityMapping mapping = new EntityMapping();

			mapping.setEntityClass(entityClass);
			mapping.setMappedTableName(generateTableNameFromClass(entityClass));

			IdColumnMapping potentialId = null;

			for (Method method : entityClass.getMethods())
			{
				if (isValidProperty(entityClass, method))
				{
					Column columnAnnotation = (Column) method.getAnnotation(Column.class);
					IdentityColumn idAnnotation = (IdentityColumn) method.getAnnotation(IdentityColumn.class);

					ColumnMapping columnMapping = new ColumnMapping();

					columnMapping.setColumnMethod(method);
					columnMapping.setJavaType(method.getReturnType());

					String columnName = generateColumnNameFromProprety(method);
					String columnSize = "";
					String defaultValue = "";
					String columnType = "VARCHAR";

					if (idAnnotation != null)
					{
						columnName = idAnnotation.value();

						if (idAnnotation.size() != null && idAnnotation.size().length() > 0)
							columnSize = idAnnotation.size();

						if (idAnnotation.defaultValue() != null && idAnnotation.defaultValue().length() > 0)
							defaultValue = idAnnotation.defaultValue();

						if (idAnnotation.dataType() != null && idAnnotation.dataType().length() > 0)
							columnType = idAnnotation.dataType();

						if (mapping.getIdMapping() == null)
							mapping.setIdMapping(new IdColumnMapping(idAnnotation.enforce(), columnMapping));
					}
					else if (columnAnnotation != null)
					{
						columnName = columnAnnotation.value();

						if (columnAnnotation.size() != null && columnAnnotation.size().length() > 0)
							columnSize = columnAnnotation.size();

						if (columnAnnotation.defaultValue() != null && columnAnnotation.defaultValue().length() > 0)
							defaultValue = columnAnnotation.defaultValue();

						if (columnAnnotation.dataType() != null && columnAnnotation.dataType().length() > 0)
							columnType = columnAnnotation.dataType();
					}

					columnMapping.setColumnName(columnName);
					columnMapping.setColumnSize(columnSize);
					columnMapping.setDefaultValue(defaultValue);
					columnMapping.setColumnType(columnType);

					mapping.setColumnMapping(method, columnMapping);

					if (method.getName().equals("getId"))
						potentialId = new IdColumnMapping(columnMapping);
				}
			}

			if (potentialId != null && mapping.getIdMapping() == null)
				mapping.setIdMapping(potentialId);

			mapperCache.put(entityClass, mapping);

			return mapping;
		}
	}

	private boolean isValidProperty(Class<?> entityClass, Method method)
	{
		try
		{
			if (method.getName().startsWith("get") && method.getParameterTypes().length == 0 && !method.getName().equals("getClass"))
			{
				Method setter = entityClass.getMethod(method.getName().replace("get", "set"), method.getReturnType());

				return setter != null;
			}
		}
		catch (SecurityException e)
		{
			Log.d(this.getClass().toString(), "Ignoring potential property : " + method, e);
		}
		catch (NoSuchMethodException e)
		{
			Log.d(this.getClass().toString(), "Ignoring potential property : " + method, e);
		}

		return false;
	}

	@Override
	public Object valuesToBean(Cursor cursor, Class<?> entityClass) throws AnormousException
	{
		Object bean;
		try
		{
			bean = entityClass.newInstance();

			EntityMapping mapping = mapClass(entityClass);

			Map<Method, ColumnMapping> columnMappings = mapping.getMappedColumns();

			for (Method method : columnMappings.keySet())
			{
				ColumnMapping columnMapping = columnMappings.get(method);

				resolveTypeAndGet(cursor, columnMapping, bean);
			}

			return bean;
		}
		catch (InstantiationException e)
		{
			Log.w(this.getClass().toString(), "Error mapping db result to to bean", e);
		}
		catch (IllegalAccessException e)
		{
			Log.w(this.getClass().toString(), "Error mapping db result to bean", e);
		}

		return null;
	}

	@Override
	public ContentValues beanToValues(Object bean) throws AnormousException
	{
		ContentValues contentValues = new ContentValues();

		EntityMapping mapping = mapClass(bean.getClass());

		Map<Method, ColumnMapping> columnMappings = mapping.getMappedColumns();

		for (Method method : columnMappings.keySet())
		{
			ColumnMapping columnMapping = columnMappings.get(method);

			resolveTypeAndPut(contentValues, columnMapping, bean);
		}

		return contentValues;
	}

	private void resolveTypeAndGet(Cursor cursor, ColumnMapping columnMapping, Object bean)
	{
		Method setter;

		try
		{
			setter = bean.getClass().getMethod(columnMapping.getColumnMethod().getName().replace("get", "set"), columnMapping.getJavaType());

			Class<?> javaType = columnMapping.getJavaType();

			if (setter != null)
			{
				if (javaType.equals(Boolean.class))
				{
					String value = cursor.getString(cursor.getColumnIndex(columnMapping.getColumnName()));

					setter.invoke(bean, "1".equals(value) || "true".equals(value));
				}
				else if (javaType.equals(String.class))
				{
					setter.invoke(bean, cursor.getString(cursor.getColumnIndex(columnMapping.getColumnName())));
				}
				else if (javaType.equals(Double.class))
				{
					setter.invoke(bean, new Double(cursor.getString(cursor.getColumnIndex(columnMapping.getColumnName()))));
				}
				else if (javaType.equals(Float.class))
				{
					setter.invoke(bean, new Float(cursor.getString(cursor.getColumnIndex(columnMapping.getColumnName()))));
				}
				else if (javaType.equals(Long.class))
				{
					setter.invoke(bean, new Long(cursor.getString(cursor.getColumnIndex(columnMapping.getColumnName()))));
				}
				else if (javaType.equals(Integer.class))
				{
					setter.invoke(bean, new Integer(cursor.getString(cursor.getColumnIndex(columnMapping.getColumnName()))));
				}
				else if (javaType.equals(Short.class))
				{
					setter.invoke(bean, new Short(cursor.getString(cursor.getColumnIndex(columnMapping.getColumnName()))));
				}
				else if (javaType.equals(Byte.class))
				{
					setter.invoke(bean, new Byte(cursor.getString(cursor.getColumnIndex(columnMapping.getColumnName()))));
				}
				else if (javaType.equals(byte[].class))
				{
					setter.invoke(bean, cursor.getBlob(cursor.getColumnIndex(columnMapping.getColumnName())));
				}
			}
			else
			{
				Log.w(this.getClass().toString(), "Can not resolve setter for mapping : " + columnMapping);
			}
		}
		catch (IllegalArgumentException e)
		{
			Log.d(this.getClass().toString(), "Error getting value for column mapping : " + columnMapping, e);
		}
		catch (IllegalAccessException e)
		{
			Log.d(this.getClass().toString(), "Error getting value for column mapping : " + columnMapping, e);
		}
		catch (InvocationTargetException e)
		{
			Log.d(this.getClass().toString(), "Can not resolve setter for mapping : " + columnMapping, e);
		}
		catch (SecurityException e)
		{
			Log.w(this.getClass().toString(), "Can not resolve setter for mapping : " + columnMapping, e);
		}
		catch (NoSuchMethodException e)
		{
			Log.w(this.getClass().toString(), "Can not resolve setter for mapping : " + columnMapping, e);
		}

	}

	private void resolveTypeAndPut(ContentValues contentValues, ColumnMapping columnMapping, Object bean)
	{
		try
		{
			Object value = columnMapping.getColumnMethod().invoke(bean);

			if (value == null)
			{
				contentValues.putNull(columnMapping.getColumnName());
			}
			else
			{
				if (value instanceof Boolean)
				{
					contentValues.put(columnMapping.getColumnName(), (Boolean) value);
				}
				else if (value instanceof String)
				{
					contentValues.put(columnMapping.getColumnName(), (String) value);
				}
				else if (value instanceof Double)
				{
					contentValues.put(columnMapping.getColumnName(), (Double) value);
				}
				else if (value instanceof Float)
				{
					contentValues.put(columnMapping.getColumnName(), (Float) value);
				}
				else if (value instanceof Long)
				{
					contentValues.put(columnMapping.getColumnName(), (Long) value);
				}
				else if (value instanceof Integer)
				{
					contentValues.put(columnMapping.getColumnName(), (Integer) value);
				}
				else if (value instanceof Short)
				{
					contentValues.put(columnMapping.getColumnName(), (Short) value);
				}
				else if (value instanceof Byte)
				{
					contentValues.put(columnMapping.getColumnName(), (Byte) value);
				}
				else if (value instanceof byte[])
				{
					contentValues.put(columnMapping.getColumnName(), (byte[]) value);
				}
				else
				{
					contentValues.put(columnMapping.getColumnName(), value.toString());
				}
			}
		}
		catch (IllegalArgumentException e)
		{
			Log.d(this.getClass().toString(), "Error getting value for column mapping : " + columnMapping, e);
		}
		catch (IllegalAccessException e)
		{
			Log.d(this.getClass().toString(), "Error getting value for column mapping : " + columnMapping, e);
		}
		catch (InvocationTargetException e)
		{
			Log.d(this.getClass().toString(), "Error getting value for column mapping : " + columnMapping, e);
		}
	}

	public String generateTableNameFromClass(Class<?> entityClass)
	{
		String returnValue = null;

		for (Annotation annotation : entityClass.getAnnotations())
		{
			if (annotation instanceof Table)
			{
				returnValue = ((Table) annotation).value();
			}
		}

		if (returnValue == null)
			returnValue = camelToDelimited(entityClass.getCanonicalName());

		return returnValue;
	}

	public String generateColumnNameFromProprety(Method method)
	{
		String returnValue = null;

		returnValue = camelToDelimited(method.getName().substring(3));

		return returnValue;
	}

	public String delimitedToName(String delimited)
	{
		return delimited.replaceAll("_", "\\.");
	}

	public String camelToDelimited(String name)
	{
		return name.replaceAll("\\.", "_");
	}
}
