package com.anormous.error;

public class AnormousException extends Exception
{
	/**
	 * 
	 */
	private static final long serialVersionUID = -6666116932995804468L;

	public AnormousException()
	{
		super();
	}

	public AnormousException(String detailMessage, Throwable throwable)
	{
		super(detailMessage, throwable);
	}

	public AnormousException(String detailMessage)
	{
		super(detailMessage);
	}

	public AnormousException(Throwable throwable)
	{
		super(throwable);
	}
}