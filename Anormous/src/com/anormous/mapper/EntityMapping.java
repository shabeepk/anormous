package com.anormous.mapper;

import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;

public class EntityMapping
{
	private Class<?> entityClass;
	private String mappedTableName;
	private IdColumnMapping idMapping;
	private Map<Method, ColumnMapping> mappedColumns = new HashMap<Method, ColumnMapping>();

	public IdColumnMapping getIdMapping()
	{
		return idMapping;
	}

	public void setIdMapping(IdColumnMapping idMapping)
	{
		this.idMapping = idMapping;
	}

	public Map<Method, ColumnMapping> getMappedColumns()
	{
		return new HashMap<Method, ColumnMapping>(mappedColumns);
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

	public void setColumnMapping(Method methodName, ColumnMapping columnMapping)
	{
		mappedColumns.put(methodName, columnMapping);
	}

	public ColumnMapping getColumnMapping(Method methodName)
	{
		return mappedColumns.get(methodName);
	}

	public Method getReverseColumnMapping(String column)
	{
		for (Method method : mappedColumns.keySet())
		{
			if (mappedColumns.get(method).equals(column))
				return method;
		}

		return null;
	}

	public static class ColumnMapping
	{
		private Method columnMethod;
		private String columnName;
		private Class<?> javaType;
		private String columnType;
		private String defaultValue;
		private String columnSize;

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

		public Method getColumnMethod()
		{
			return columnMethod;
		}

		public void setColumnMethod(Method columnMethod)
		{
			this.columnMethod = columnMethod;
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

			super.setColumnMethod(columnMapping.getColumnMethod());
			super.setColumnName(columnMapping.getColumnName());
			super.setColumnSize(columnMapping.getColumnSize());
			super.setColumnType(columnMapping.getColumnType());
			super.setDefaultValue(columnMapping.getDefaultValue());
		}

		public IdColumnMapping(boolean enforce, ColumnMapping columnMapping)
		{
			this.enforce = enforce;

			super.setColumnMethod(columnMapping.getColumnMethod());
			super.setColumnName(columnMapping.getColumnName());
			super.setColumnSize(columnMapping.getColumnSize());
			super.setColumnType(columnMapping.getColumnType());
			super.setDefaultValue(columnMapping.getDefaultValue());
		}
	}
}