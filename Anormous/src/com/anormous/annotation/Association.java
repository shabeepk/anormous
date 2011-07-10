package com.anormous.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Retention(RetentionPolicy.RUNTIME)
@Target({ ElementType.FIELD, ElementType.METHOD })
public @interface Association
{
	String value() default "";

	Class<?> associativeClass();

	AssociationType type() default AssociationType.ONE_TO_MANY;

	public static enum AssociationType
	{
		ONE_TO_ONE, ONE_TO_MANY, MANY_TO_ONE, MANY_TO_MANY;
	}
}
