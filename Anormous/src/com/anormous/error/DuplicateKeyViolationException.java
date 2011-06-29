package com.anormous.error;

public class DuplicateKeyViolationException extends AnormousException
{
	/**
	 * 
	 */
	private static final long serialVersionUID = -6666116932995804468L;

	public DuplicateKeyViolationException()
	{
		super();
	}

	public DuplicateKeyViolationException(String detailMessage, Throwable throwable)
	{
		super(detailMessage, throwable);
	}

	public DuplicateKeyViolationException(String detailMessage)
	{
		super(detailMessage);
	}

	public DuplicateKeyViolationException(Throwable throwable)
	{
		super(throwable);
	}
}