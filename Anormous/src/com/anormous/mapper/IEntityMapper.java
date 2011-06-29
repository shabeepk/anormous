package com.anormous.mapper;

import com.anormous.error.AnormousException;

import android.content.ContentValues;
import android.database.Cursor;

public interface IEntityMapper
{
	EntityMapping mapClass(Class<?> entityClass) throws AnormousException;

	ContentValues beanToValues(Object bean) throws AnormousException;

	Object valuesToBean(Cursor cursor, Class<?> entityClass) throws AnormousException;
}
