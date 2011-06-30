package com.anormous.mapper;

import android.content.ContentValues;
import android.database.Cursor;

import com.anormous.error.AnormousException;

public interface IEntityMapper
{
	EntityMapping mapClass(Class<?> entityClass) throws AnormousException;

	ContentValues beanToValues(Object bean) throws AnormousException;

	Object valuesToBean(Cursor cursor, Class<?> entityClass) throws AnormousException;

	String forwardMapColumnNames(String query, Class<?> entityClass) throws AnormousException;

	String generateCreateTableStatement(Class<?> entityClass) throws AnormousException;
}
