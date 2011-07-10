package com.anormous.mapper;

import android.content.ContentValues;
import android.database.Cursor;

import com.anormous.error.AnormousException;

public interface IEntityMapper
{
	<T> EntityMapping<T> mapClass(Class<T> entityClass) throws AnormousException;

	ContentValues beanToValues(Object bean) throws AnormousException;

	<T> T valuesToBean(Cursor cursor, Class<T> entityClass) throws AnormousException;

	String forwardMapColumnNames(String query, Class<?> entityClass) throws AnormousException;

	String generateCreateTableStatement(Class<?> entityClass) throws AnormousException;
}
