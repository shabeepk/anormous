package com.anormous.error;

public class AnormousException extends Exception
{
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