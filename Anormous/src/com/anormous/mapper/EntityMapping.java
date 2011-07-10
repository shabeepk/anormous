package com.anormous.mapper;

import java.lang.annotation.Annotation;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;

import com.anormous.annotation.Column;
import com.anormous.annotation.IdentityColumn;
import com.anormous.annotation.Association;

public class EntityMapping
{
	private Class<?> entityClass;
	private String mappedTableName;
	private IdColumnMapping idMapping;
	private Map<Property, ColumnMapping> mappedColumns = new HashMap<Property, ColumnMapping>();

	public IdColumnMapping getIdMapping()
	{
		return idMapping;
	}

	public void setIdMapping(IdColumnMapping idMapping)
	{
		this.idMapping = idMapping;
	}

	public Map<Property, ColumnMapping> getMappedColumns()
	{
		return new HashMap<Property, ColumnMapping>(mappedColumns);
	}

	public Class<?> getEntityClass()
	{
		return entityClass;
	}

	public void setEntityClass(Class<?> entityClass)
	{
		this.entityClass = entityClass;
	}

	public String getMappedTableName()
	{
		return mappedTableName;
	}

	public void setMappedTableName(String mappedTableName)
	{
		this.mappedTableName = mappedTableName;
	}

	public void setColumnMapping(Property property, ColumnMapping columnMapping)
	{
		mappedColumns.put(property, columnMapping);
	}

	public ColumnMapping getColumnMapping(Method methodName)
	{
		return mappedColumns.get(methodName);
	}

	public Property getReverseColumnMapping(String column)
	{
		for (Property property : mappedColumns.keySet())
		{
			if (mappedColumns.get(property).equals(column))
				return property;
		}

		return null;
	}

	public static class Property
	{
		private String name;
		private Class<?> entityClass;
		private Method getterMethod;
		private Method setterMethod;
		private Field field;
		private Class<?> type;
		Column columnAnnotation;
		IdentityColumn idAnnotation;
		Association associationAnnotation;

		public Property(String name, Class<?> entityClass, Method getterMethod, Method setterMethod, Field field, Class<?> type, Annotation annotation)
		{
			super();
			this.name = name;
			this.entityClass = entityClass;
			this.getterMethod = getterMethod;
			this.setterMethod = setterMethod;
			this.field = field;
			this.type = type;

			if (annotation instanceof Column)
			{
				this.columnAnnotation = (Column) annotation;
			}
			else if (annotation instanceof IdentityColumn)
			{
				this.idAnnotation = (IdentityColumn) annotation;
			}
			else if (annotation instanceof Association)
			{
				this.associationAnnotation = (Association) annotation;
			}
		}

		public Association getAssociationAnnotation()
		{
			return associationAnnotation;
		}

		public Column getColumnAnnotation()
		{
			return columnAnnotation;
		}

		public IdentityColumn getIdAnnotation()
		{
			return idAnnotation;
		}

		public Field getField()
		{
			return field;
		}

		public void setField(Field field)
		{
			this.field = field;
		}

		public Class<?> getEntityClass()
		{
			return entityClass;
		}

		public void setEntityClass(Class<?> entityClass)
		{
			this.entityClass = entityClass;
		}

		public Class<?> getType()
		{
			return type;
		}

		public void setType(Class<?> type)
		{
			this.type = type;
		}

		public String getName()
		{
			return name;
		}

		public void setName(String name)
		{
			this.name = name;
		}

		public Method getGetterMethod()
		{
			return getterMethod;
		}

		public void setGetterMethod(Method getterMethod)
		{
			this.getterMethod = getterMethod;
		}

		public Method getSetterMethod()
		{
			return setterMethod;
		}

		public void setSetterMethod(Method setterMethod)
		{
			this.setterMethod = setterMethod;
		}

		public Object getValueFrom(Object bean) throws IllegalArgumentException, IllegalAccessException, InvocationTargetException
		{
			if (field != null)
			{
				if (!field.isAccessible())
					field.setAccessible(true);

				return field.get(bean);
			}
			else
				return getterMethod.invoke(bean);
		}

		public void setValueTo(Object bean, Object value) throws IllegalArgumentException, IllegalAccessException, InvocationTargetException
		{
			if (field != null)
			{
				if (!field.isAccessible())
					field.setAccessible(true);

				field.set(bean, value);
			}
			else
				setterMethod.invoke(bean, value);
		}

		@Override
		public boolean equals(Object o)
		{
			if (this.name != null && o != null && o instanceof Property)
			{
				return this.name.equals(((Property) o).name);
			}
			else
			{
				return false;
			}
		}

		@Override
		public int hashCode()
		{
			return this.name.hashCode();
		}
	}

	public static class ColumnMapping
	{
		private Property property;
		private String columnName;
		private Class<?> javaType;
		private String columnType;
		private String defaultValue;
		private String columnSize;

		public Property getProperty()
		{
			return property;
		}

		public void setProperty(Property property)
		{
			this.property = property;
		}

		public String getDefaultValue()
		{
			return defaultValue;
		}

		public void setDefaultValue(String defaultValue)
		{
			this.defaultValue = defaultValue;
		}

		public Class<?> getJavaType()
		{
			return javaType;
		}

		public void setJavaType(Class<?> javaType)
		{
			this.javaType = javaType;
		}

		public String getColumnName()
		{
			return columnName;
		}

		public void setColumnName(String columnName)
		{
			this.columnName = columnName;
		}

		public String getColumnType()
		{
			return columnType;
		}

		public void setColumnType(String columnType)
		{
			this.columnType = columnType;
		}

		public String getColumnSize()
		{
			return columnSize;
		}

		public void setColumnSize(String columnSize)
		{
			this.columnSize = columnSize;
		}
	}

	public static class IdColumnMapping extends ColumnMapping
	{
		private boolean enforce;
		private boolean reuse;

		public boolean isReuse()
		{
			return reuse;
		}

		public void setReuse(boolean reuse)
		{
			this.reuse = reuse;
		}

		public boolean isEnforce()
		{
			return enforce;
		}

		public void setEnforce(boolean enforce)
		{
			this.enforce = enforce;
		}

		public IdColumnMapping()
		{
			this.enforce = true;
		}

		public IdColumnMapping(ColumnMapping columnMapping)
		{
			this.enforce = true;
			this.reuse = false;

			super.setProperty(columnMapping.getProperty());
			super.setColumnName(columnMapping.getColumnName());
			super.setColumnSize(columnMapping.getColumnSize());
			super.setColumnType(columnMapping.getColumnType());
			super.setDefaultValue(columnMapping.getDefaultValue());
		}

		public IdColumnMapping(boolean enforce, boolean reuse, ColumnMapping columnMapping)
		{
			this.enforce = enforce;
			this.reuse = reuse;

			super.setProperty(columnMapping.getProperty());
			super.setColumnName(columnMapping.getColumnName());
			super.setColumnSize(columnMapping.getColumnSize());
			super.setColumnType(columnMapping.getColumnType());
			super.setDefaultValue(columnMapping.getDefaultValue());
		}
	}
}