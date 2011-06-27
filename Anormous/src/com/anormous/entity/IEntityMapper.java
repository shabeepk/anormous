package com.anormous.entity;

import android.content.ContentValues;
import android.database.Cursor;

public interface IEntityMapper
{
	EntityMapping mapClass(Class entityClass);

	ContentValues beanToValues(Object bean);

	Object valuesToBean(Cursor cursor, Class entityClass);
}
