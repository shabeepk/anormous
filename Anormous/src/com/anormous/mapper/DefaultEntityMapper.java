package com.anormous.mapper;

import java.lang.annotation.Annotation;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.LinkedHashMap;
import java.util.Map;

import android.content.ContentValues;
import android.database.Cursor;
import android.util.Log;

import com.anormous.annotation.Association.AssociationType;
import com.anormous.annotation.Column;
import com.anormous.annotation.IdentityColumn;
import com.anormous.annotation.Association;
import com.anormous.annotation.Table;
import com.anormous.error.AnormousException;
import com.anormous.error.InvalidClassTypeException;
import com.anormous.mapper.EntityMapping.ColumnMapping;
import com.anormous.mapper.EntityMapping.IdColumnMapping;
import com.anormous.mapper.EntityMapping.Property;

public class DefaultEntityMapper implements IEntityMapper
{
	private static LinkedHashMap<Class<?>, EntityMapping> mapperCache = new LinkedHashMap<Class<?>, EntityMapping>();

	public DefaultEntityMapper()
	{
		super();
	}

	@Override
	public String forwardMapColumnNames(String query, Class<?> entityClass) throws AnormousException
	{
		EntityMapping mapping = mapClass(entityClass);

		String result = query;

		for (ColumnMapping columnMapping : mapping.getMappedColumns().values())
		{
			String propertyName = columnMapping.getProperty().getName();
			result = result.replaceAll(propertyName, columnMapping.getColumnName());
		}

		return result;
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

			Map<String, Property> properties = getAllPropertiesForClass(entityClass);

			IdColumnMapping potentialId = null;

			for (String propertyName : properties.keySet())
			{
				Property property = properties.get(propertyName);
				boolean isIdMapping = false;

				Column columnAnnotation = property.getColumnAnnotation();
				IdentityColumn idAnnotation = property.getIdAnnotation();
				Association associationAnnotation = property.getAssociationAnnotation();

				ColumnMapping columnMapping = new ColumnMapping();

				columnMapping.setProperty(property);
				columnMapping.setJavaType(property.getType());

				String columnName = generateColumnNameFromProprety(propertyName);
				String columnSize = "";
				String defaultValue = "";
				String columnType = resolveSQLType(property);

				if (idAnnotation != null)
				{
					if (idAnnotation.value() != null && idAnnotation.value().length() > 0)
					{
						columnName = idAnnotation.value();
					}

					if (idAnnotation.size() != null && idAnnotation.size().length() > 0)
						columnSize = idAnnotation.size();

					if (idAnnotation.defaultValue() != null && idAnnotation.defaultValue().length() > 0)
						defaultValue = idAnnotation.defaultValue();

					if (idAnnotation.dataType() != null && idAnnotation.dataType().length() > 0)
						columnType = idAnnotation.dataType();

					isIdMapping = true;
				}
				else if (columnAnnotation != null)
				{
					if (columnAnnotation.value() != null && columnAnnotation.value().length() > 0)
					{
						columnName = columnAnnotation.value();
					}

					if (columnAnnotation.size() != null && columnAnnotation.size().length() > 0)
						columnSize = columnAnnotation.size();

					if (columnAnnotation.defaultValue() != null && columnAnnotation.defaultValue().length() > 0)
						defaultValue = columnAnnotation.defaultValue();

					if (columnAnnotation.dataType() != null && columnAnnotation.dataType().length() > 0)
						columnType = columnAnnotation.dataType();
				}
				else if (associationAnnotation != null)
				{
					if (associationAnnotation.value() != null && associationAnnotation.value().length() > 0)
					{
						columnName = associationAnnotation.value();
					}

					@SuppressWarnings("unused")
					Class<?> associativeClass = associationAnnotation.associativeClass();
					@SuppressWarnings("unused")
					AssociationType type = associationAnnotation.type();
				}

				columnMapping.setColumnName(columnName);
				columnMapping.setColumnSize(columnSize);
				columnMapping.setDefaultValue(defaultValue);
				columnMapping.setColumnType(columnType);

				if (isIdMapping && mapping.getIdMapping() == null)
				{
					IdColumnMapping idColumnMapping = new IdColumnMapping(idAnnotation.enforce(), idAnnotation.reuse(), columnMapping);

					mapping.setIdMapping(idColumnMapping);
					mapping.setColumnMapping(property, idColumnMapping);
				}
				else if (propertyName.equals("id"))
				{
					potentialId = new IdColumnMapping(columnMapping);
				}
				else
				{
					mapping.setColumnMapping(property, columnMapping);
				}
			}

			if (potentialId != null && mapping.getIdMapping() == null)
			{
				mapping.setIdMapping(potentialId);
				mapping.setColumnMapping(potentialId.getProperty(), potentialId);
			}

			mapperCache.put(entityClass, mapping);

			return mapping;
		}
	}

	private Map<String, Property> getAllPropertiesForClass(Class<?> entityClass)
	{
		LinkedHashMap<String, Property> result = new LinkedHashMap<String, EntityMapping.Property>();

		Property property = null;

		for (Field field : entityClass.getFields())
		{
			String propertyName = field.getName();
			Column columnAnnotation = field.getAnnotation(Column.class);
			IdentityColumn idAnnotation = field.getAnnotation(IdentityColumn.class);

			if (!field.isAccessible())
				field.setAccessible(true);

			property = new Property(propertyName, entityClass, null, null, field, field.getType(), idAnnotation != null ? idAnnotation : columnAnnotation);
		}

		for (Method getter : entityClass.getMethods())
		{
			String propertyName = getter.getName().substring(3);
			propertyName = Character.toLowerCase(propertyName.charAt(0)) + propertyName.substring(1);

			Column columnAnnotation = getter.getAnnotation(Column.class);
			IdentityColumn idAnnotation = getter.getAnnotation(IdentityColumn.class);

			if (!result.containsKey(propertyName))
			{
				property = new Property(propertyName, entityClass, getter, null, null, getter.getReturnType(), idAnnotation != null ? idAnnotation : columnAnnotation);

				try
				{
					property.setField(entityClass.getField(propertyName));
				}
				catch (NoSuchFieldException ex)
				{

				}

				Method setter = null;
				if ((setter = getSetterIfValidGetter(entityClass, getter)) != null)
				{
					property.setSetterMethod(setter);
				}
			}
		}

		if (property != null)
			result.put(property.getName(), property);

		return result;
	}

	@Override
	public String generateCreateTableStatement(Class<?> entityClass) throws AnormousException
	{
		EntityMapping mapping = mapClass(entityClass);

		StringBuffer sql = new StringBuffer("CREATE TABLE " + mapping.getMappedTableName() + " ( ");
		boolean first = true;

		for (ColumnMapping columnMapping : mapping.getMappedColumns().values())
		{
			if (!first)
				sql.append(",");

			sql.append(generateColumnDeclaration(columnMapping));

			first = false;
		}

		sql.append(");");

		return sql.toString();
	}

	private String generateColumnDeclaration(ColumnMapping columnMapping)
	{
		StringBuffer columnSql = new StringBuffer();

		columnSql.append(columnMapping.getColumnName());
		columnSql.append(" ");
		columnSql.append(columnMapping.getColumnType());
		columnSql.append(columnMapping.getColumnSize().length() > 0 ? "(" + columnMapping.getColumnSize() + ")" : "");
		if (columnMapping.getColumnType().equalsIgnoreCase("INTEGER") && columnMapping instanceof IdColumnMapping)
		{
			columnSql.append(" PRIMARY KEY ");

			if (((IdColumnMapping) columnMapping).isReuse())
				columnSql.append(" AUTOINCREMENT ");
		}
		columnSql.append(columnMapping.getDefaultValue().length() > 0 ? " DEFAULT " + columnMapping.getDefaultValue() : "");

		return columnSql.toString();
	}

	private String resolveSQLType(Property property)
	{
		String type = "VARCHAR";

		if (property.getType().equals(Integer.class))
		{
			type = "INTEGER";
		}
		else if (property.getType().equals(Long.class))
		{
			type = "INTEGER";
		}
		else if (property.getType().equals(Short.class))
		{
			type = "INTEGER";
		}
		else if (property.getType().equals(Byte.class))
		{
			type = "INTEGER";
		}
		else if (property.getType().equals(Double.class))
		{
			type = "NUMERIC";
		}
		else if (property.getType().equals(Float.class))
		{
			type = "NUMERIC";
		}
		else if (property.getType().equals(byte[].class))
		{
			type = "BLOB";
		}

		return type;
	}

	private Method getSetterIfValidGetter(Class<?> entityClass, Method method)
	{
		try
		{
			if (method.getName().startsWith("get") && method.getParameterTypes().length == 0 && !method.getName().equals("getClass"))
			{
				Method setter = entityClass.getMethod(method.getName().replace("get", "set"), method.getReturnType());

				return setter;
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

		return null;
	}

	@Override
	public Object valuesToBean(Cursor cursor, Class<?> entityClass) throws AnormousException
	{
		Object bean;
		try
		{
			bean = entityClass.newInstance();

			EntityMapping mapping = mapClass(entityClass);

			Map<Property, ColumnMapping> columnMappings = mapping.getMappedColumns();

			for (Property property : columnMappings.keySet())
			{
				ColumnMapping columnMapping = columnMappings.get(property);

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

		Map<Property, ColumnMapping> columnMappings = mapping.getMappedColumns();

		for (Property property : columnMappings.keySet())
		{
			ColumnMapping columnMapping = columnMappings.get(property);

			resolveTypeAndPut(contentValues, columnMapping, bean);
		}

		return contentValues;
	}

	private void resolveTypeAndGet(Cursor cursor, ColumnMapping columnMapping, Object bean)
	{
		try
		{
			Property property = columnMapping.getProperty();

			if (property.getType().equals(Boolean.class))
			{
				String value = cursor.getString(cursor.getColumnIndex(columnMapping.getColumnName()));

				property.setValueTo(bean, "1".equals(value) || "true".equals(value));
			}
			else if (property.getType().equals(String.class))
			{
				property.setValueTo(bean, cursor.getString(cursor.getColumnIndex(columnMapping.getColumnName())));
			}
			else if (property.getType().equals(Double.class))
			{
				property.setValueTo(bean, new Double(cursor.getString(cursor.getColumnIndex(columnMapping.getColumnName()))));
			}
			else if (property.getType().equals(Float.class))
			{
				property.setValueTo(bean, new Float(cursor.getString(cursor.getColumnIndex(columnMapping.getColumnName()))));
			}
			else if (property.getType().equals(Long.class))
			{
				property.setValueTo(bean, new Long(cursor.getString(cursor.getColumnIndex(columnMapping.getColumnName()))));
			}
			else if (property.getType().equals(Integer.class))
			{
				property.setValueTo(bean, new Integer(cursor.getString(cursor.getColumnIndex(columnMapping.getColumnName()))));
			}
			else if (property.getType().equals(Short.class))
			{
				property.setValueTo(bean, new Short(cursor.getString(cursor.getColumnIndex(columnMapping.getColumnName()))));
			}
			else if (property.getType().equals(Byte.class))
			{
				property.setValueTo(bean, new Byte(cursor.getString(cursor.getColumnIndex(columnMapping.getColumnName()))));
			}
			else if (property.getType().equals(byte[].class))
			{
				property.setValueTo(bean, cursor.getBlob(cursor.getColumnIndex(columnMapping.getColumnName())));
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
	}

	private void resolveTypeAndPut(ContentValues contentValues, ColumnMapping columnMapping, Object bean)
	{
		try
		{
			Object value = columnMapping.getProperty().getValueFrom(bean);

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

	public String generateColumnNameFromProprety(String propertyName)
	{
		String returnValue = null;

		returnValue = camelToDelimited(propertyName);

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
