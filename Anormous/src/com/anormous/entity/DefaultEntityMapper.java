package com.anormous.entity;

import java.lang.annotation.Annotation;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

import com.anormous.annotation.Column;
import com.anormous.annotation.Table;
import com.anormous.entity.EntityMapping.ColumnMapping;

import android.content.ContentValues;
import android.database.Cursor;
import android.util.Log;

public class DefaultEntityMapper implements IEntityMapper
{
	private static LinkedHashMap<Class, EntityMapping> mapperCache = new LinkedHashMap<Class, EntityMapping>();

	public DefaultEntityMapper()
	{
		super();
	}

	@Override
	public EntityMapping mapClass(Class entityClass)
	{
		if (mapperCache.containsKey(entityClass))
		{
			return mapperCache.get(entityClass);
		}
		else
		{
			EntityMapping mapping = new EntityMapping();

			generateTableNameFromClass(entityClass);

			for (Method method : entityClass.getMethods())
			{
				if (method.getName().startsWith("get") && method.getParameterTypes().length == 0)
				{
					Column mappingAnnotation = (Column) method.getAnnotation(Column.class);

					ColumnMapping columnMapping = new ColumnMapping();

					columnMapping.setColumnMethod(method);
					columnMapping.setJavaType(method.getReturnType());

					if (mappingAnnotation != null)
					{
						columnMapping.setColumnName(mappingAnnotation.value());
						columnMapping.setColumnSize(mappingAnnotation.size());
						columnMapping.setDefaultValue(mappingAnnotation.defaultValue());
						columnMapping.setColumnType(mappingAnnotation.dataType());
					}
					else
					{
						columnMapping.setColumnName(generateColumnNameFromProprety(method));
						columnMapping.setColumnSize("");
						columnMapping.setDefaultValue("");
						columnMapping.setColumnType("VARCHAR");
					}

					mapping.setColumnMapping(method, columnMapping);

					if (method.getName().equals("getId"))
						mapping.setIdMapping(columnMapping);
				}
			}

			mapperCache.put(entityClass, mapping);

			return mapping;
		}
	}

	@Override
	public Object valuesToBean(Cursor cursor, Class entityClass)
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
	public ContentValues beanToValues(Object bean)
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

			Class javaType = columnMapping.getJavaType();

			if (setter != null)
			{
				if (javaType.equals(Boolean.class))
				{
					if ("1".equals(cursor.getString(cursor.getColumnIndex(columnMapping.getColumnName()))) || "true".equals(cursor.getString(cursor.getColumnIndex(columnMapping.getColumnName()))))
						setter.invoke(bean, true);
				}
				else if (javaType.equals(String.class))
				{
					setter.invoke(cursor.getString(cursor.getColumnIndex(columnMapping.getColumnName())));
				}
				else if (javaType.equals(Double.class))
				{
					setter.invoke(new Double(cursor.getString(cursor.getColumnIndex(columnMapping.getColumnName()))));
				}
				else if (javaType.equals(Float.class))
				{
					setter.invoke(new Float(cursor.getString(cursor.getColumnIndex(columnMapping.getColumnName()))));
				}
				else if (javaType.equals(Long.class))
				{
					setter.invoke(new Long(cursor.getString(cursor.getColumnIndex(columnMapping.getColumnName()))));
				}
				else if (javaType.equals(Integer.class))
				{
					setter.invoke(new Integer(cursor.getString(cursor.getColumnIndex(columnMapping.getColumnName()))));
				}
				else if (javaType.equals(Short.class))
				{
					setter.invoke(new Short(cursor.getString(cursor.getColumnIndex(columnMapping.getColumnName()))));
				}
				else if (javaType.equals(Byte.class))
				{
					setter.invoke(new Byte(cursor.getString(cursor.getColumnIndex(columnMapping.getColumnName()))));
				}
				else if (javaType.equals(byte[].class))
				{
					setter.invoke(cursor.getBlob(cursor.getColumnIndex(columnMapping.getColumnName())));
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

	public String generateTableNameFromClass(Class entityClass)
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
			returnValue = camelToDelimited(entityClass.toString());

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
