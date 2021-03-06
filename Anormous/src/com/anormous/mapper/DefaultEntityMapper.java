package com.anormous.mapper;

import java.lang.annotation.Annotation;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.Map;

import android.content.ContentValues;
import android.database.Cursor;

import com.anormous.annotation.Association.AssociationType;
import com.anormous.annotation.Column;
import com.anormous.annotation.IdentityColumn;
import com.anormous.annotation.Association;
import com.anormous.annotation.Ignore;
import com.anormous.annotation.Table;
import com.anormous.error.AnormousException;
import com.anormous.error.InvalidClassTypeException;
import com.anormous.logger.Logger;
import com.anormous.mapper.EntityMapping.ColumnMapping;
import com.anormous.mapper.EntityMapping.IdColumnMapping;
import com.anormous.mapper.EntityMapping.Property;

public class DefaultEntityMapper implements IEntityMapper
{
	private static final String TAG = "DefaultEntityMapper";
	private static Map<Class<?>, EntityMapping<?>> mapperCache = new LinkedHashMap<Class<?>, EntityMapping<?>>();

	public DefaultEntityMapper()
	{
		super();
	}

	@Override
	public String forwardMapColumnNames(String query, Class<?> entityClass) throws AnormousException
	{
		EntityMapping<?> mapping = mapClass(entityClass);

		String result = query;

		for (ColumnMapping columnMapping : mapping.getMappedColumns().values())
		{
			String propertyName = columnMapping.getProperty().getName();
			result = result.replaceAll(propertyName, columnMapping.getColumnName());
		}

		return result;
	}

	@SuppressWarnings("unchecked")
	@Override
	public <T> EntityMapping<T> mapClass(Class<T> entityClass) throws AnormousException
	{
		if (mapperCache.containsKey(entityClass))
		{
			EntityMapping<?> entityMapping = mapperCache.get(entityClass);

			return (EntityMapping<T>) entityMapping;
		}
		else
		{
			if (entityClass.isAnonymousClass() || entityClass.isArray() || entityClass.isEnum() || entityClass.isAnnotation() || entityClass.isLocalClass())
				throw new InvalidClassTypeException("Class [" + entityClass.getName() + "] can not be persisted. It may be because the class is one of anonymous class, array, enum, local or annotation.");

			EntityMapping<T> mapping = new EntityMapping<T>();

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

		for (Method getter : entityClass.getMethods())
		{
			if ((getter.getName().startsWith("get") || getter.getName().startsWith("is")) && getter.getParameterTypes().length == 0)
			{
				String propertyName = getter.getName().startsWith("get") ? getter.getName().substring(3) : getter.getName().substring(2);
				propertyName = Character.toLowerCase(propertyName.charAt(0)) + propertyName.substring(1);

				property = new Property(propertyName, entityClass, getter, null, null, getter.getReturnType(), null);

				if (setAnnotation(property))
				{
					result.put(property.getName(), property);

					try
					{
						Field field = entityClass.getField(propertyName);
						property.setField(field);
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
		}

		for (Field field : entityClass.getFields())
		{
			String propertyName = field.getName();

			if (propertyName.equals("class"))
				continue;

			if (!result.containsKey(propertyName))
			{
				if (!field.isAccessible())
					field.setAccessible(true);

				property = new Property(propertyName, entityClass, null, null, field, field.getType(), null);

				if (setAnnotation(property))
					result.put(property.getName(), property);
			}
		}

		return result;
	}

	public boolean setAnnotation(Property property)
	{
		Column columnAnnotation = null;
		IdentityColumn idAnnotation = null;
		Association associationAnnotation = null;

		if (property.getField() != null)
		{
			if (property.getField().getAnnotation(Ignore.class) != null)
				return false;

			if (property.getField().getAnnotation(Column.class) != null)
				columnAnnotation = property.getField().getAnnotation(Column.class);
			if (property.getField().getAnnotation(IdentityColumn.class) != null)
				idAnnotation = property.getField().getAnnotation(IdentityColumn.class);
			if (property.getField().getAnnotation(Association.class) != null)
				associationAnnotation = property.getField().getAnnotation(Association.class);
		}

		if (property.getGetterMethod() != null)
		{
			if (property.getGetterMethod().getAnnotation(Ignore.class) != null)
				return false;

			if (property.getGetterMethod().getAnnotation(Column.class) != null)
				columnAnnotation = property.getGetterMethod().getAnnotation(Column.class);
			if (property.getGetterMethod().getAnnotation(IdentityColumn.class) != null)
				idAnnotation = property.getGetterMethod().getAnnotation(IdentityColumn.class);
			if (property.getGetterMethod().getAnnotation(Association.class) != null)
				associationAnnotation = property.getGetterMethod().getAnnotation(Association.class);
		}

		property.setAnnotation(idAnnotation != null ? idAnnotation : (columnAnnotation != null ? columnAnnotation : associationAnnotation));

		return true;
	}

	@Override
	public String generateCreateTableStatement(Class<?> entityClass) throws AnormousException
	{
		EntityMapping<?> mapping = mapClass(entityClass);

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
		else if (property.getType().equals(Date.class))
		{
			type = "NUMERIC";
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
			if ((method.getName().startsWith("get") || method.getName().startsWith("is")) && method.getParameterTypes().length == 0 && !method.getName().equals("getClass"))
			{
				Method setter = entityClass.getMethod(method.getName().replace(method.getName().startsWith("get") ? "get" : "is", "set"), method.getReturnType());

				return setter;
			}
		}
		catch (SecurityException e)
		{
			Logger.d(TAG, "Ignoring potential property : " + method, e);
		}
		catch (NoSuchMethodException e)
		{
			Logger.d(TAG, "Ignoring potential property : " + method, e);
		}

		return null;
	}

	@Override
	public <T> T valuesToBean(Cursor cursor, Class<T> entityClass) throws AnormousException
	{
		T bean;

		try
		{
			bean = entityClass.newInstance();

			EntityMapping<T> mapping = mapClass(entityClass);

			Map<Property, ColumnMapping> columnMappings = mapping.getMappedColumns();

			for (Property property : columnMappings.keySet())
			{
				ColumnMapping columnMapping = columnMappings.get(property);

				resolveTypeAndSetToBean(cursor, columnMapping, bean);
			}

			Logger.d(TAG, "Bean loaded: " + bean);

			return bean;
		}
		catch (InstantiationException e)
		{
			Logger.w(TAG, "Error mapping db result to to bean", e);
		}
		catch (IllegalAccessException e)
		{
			Logger.w(TAG, "Error mapping db result to bean", e);
		}

		return null;
	}

	@Override
	public ContentValues beanToValues(Object bean) throws AnormousException
	{
		ContentValues contentValues = new ContentValues();

		EntityMapping<?> mapping = mapClass(bean.getClass());

		Map<Property, ColumnMapping> columnMappings = mapping.getMappedColumns();

		for (Property property : columnMappings.keySet())
		{
			ColumnMapping columnMapping = columnMappings.get(property);

			resolveTypeAndGetFromBean(contentValues, columnMapping, bean);
		}

		return contentValues;
	}

	private void resolveTypeAndSetToBean(Cursor cursor, ColumnMapping columnMapping, Object bean)
	{
		try
		{
			Property property = columnMapping.getProperty();
			int index = cursor.getColumnIndex(columnMapping.getColumnName());

			if (property.getType().equals(byte[].class))
			{
				// BLOB TYPES
				property.setValueTo(bean, cursor.isNull(index) ? null : cursor.getBlob(index));
			}
			else
			{
				// TYPES STORED AS STRING
				String value = cursor.isNull(index) ? null : cursor.getString(index);

				Logger.d(TAG, "Setting value " + value + " for " + columnMapping.getColumnName());

				if (value != null && (property.getType().equals(String.class) || value.length() > 0) && !value.equals("null"))
				{
					if (property.getType().equals(Boolean.class) || property.getType().equals(Boolean.TYPE))
					{
						property.setValueTo(bean, "1".equals(value) || "true".equals(value));
					}
					else if (property.getType().equals(Date.class))
					{
						property.setValueTo(bean, new Date(new Long(value)));
					}
					else if (property.getType().equals(String.class))
					{
						property.setValueTo(bean, value);
					}
					else if (property.getType().equals(Double.class) || property.getType().equals(Double.TYPE))
					{
						property.setValueTo(bean, new Double(value));
					}
					else if (property.getType().equals(Float.class) || property.getType().equals(Float.TYPE))
					{
						property.setValueTo(bean, new Float(value));
					}
					else if (property.getType().equals(Long.class) || property.getType().equals(Long.TYPE))
					{
						property.setValueTo(bean, new Long(value));
					}
					else if (property.getType().equals(Integer.class) || property.getType().equals(Integer.TYPE))
					{
						property.setValueTo(bean, new Integer(value));
					}
					else if (property.getType().equals(Short.class) || property.getType().equals(Short.TYPE))
					{
						property.setValueTo(bean, new Short(value));
					}
					else if (property.getType().equals(Byte.class) || property.getType().equals(Byte.TYPE))
					{
						property.setValueTo(bean, new Byte(value));
					}
					else
					{
						Logger.i(TAG, "Couldn't match type for value: " + value + ", type: " + property.getType());
					}
				}
				else
				{
					property.setValueTo(bean, null);

					Logger.i(TAG, "Null value: " + value + ", type: " + property.getType());
				}
			}
		}
		catch (IllegalArgumentException e)
		{
			Logger.d(TAG, "Error getting value for column mapping : " + columnMapping, e);
		}
		catch (IllegalAccessException e)
		{
			Logger.d(TAG, "Error getting value for column mapping : " + columnMapping, e);
		}
		catch (InvocationTargetException e)
		{
			Logger.d(TAG, "Can not resolve setter for mapping : " + columnMapping, e);
		}
		catch (SecurityException e)
		{
			Logger.w(TAG, "Can not resolve setter for mapping : " + columnMapping, e);
		}
	}

	private void resolveTypeAndGetFromBean(ContentValues contentValues, ColumnMapping columnMapping, Object bean)
	{
		try
		{
			Object value = columnMapping.getProperty().getValueFrom(bean);

			Logger.d(TAG, "Got value " + value + " for " + columnMapping.getColumnName());

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
				else if (value instanceof Date)
				{
					contentValues.put(columnMapping.getColumnName(), (Long) ((Date) value).getTime());
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
			Logger.d(TAG, "Error getting value for column mapping : " + columnMapping, e);
		}
		catch (IllegalAccessException e)
		{
			Logger.d(TAG, "Error getting value for column mapping : " + columnMapping, e);
		}
		catch (InvocationTargetException e)
		{
			Logger.d(TAG, "Error getting value for column mapping : " + columnMapping, e);
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
