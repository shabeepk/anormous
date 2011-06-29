package com.anormous.error;

public class InvalidClassTypeException extends AnormousException
{
	/**
	 * 
	 */
	private static final long serialVersionUID = -6666116932995804468L;

	public InvalidClassTypeException()
	{
		super();
	}

	public InvalidClassTypeException(String detailMessage, Throwable throwable)
	{
		super(detailMessage, throwable);
	}

	public InvalidClassTypeException(String detailMessage)
	{
		super(detailMessage);
	}

	public InvalidClassTypeException(Throwable throwable)
	{
		super(throwable);
	}
}