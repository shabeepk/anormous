package com.anormous.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Retention(RetentionPolicy.RUNTIME)
@Target({ ElementType.METHOD, ElementType.FIELD })
public @interface IdentityColumn
{
	String value();

	String size() default "";

	String dataType() default "";

	String defaultValue() default "";

	boolean enforce() default true;

	boolean reuse() default false;
}