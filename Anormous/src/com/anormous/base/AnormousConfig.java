package com.anormous.base;

import com.anormous.entity.DefaultEntityMapper;
import com.anormous.entity.IEntityMapper;

public class AnormousConfig
{
	private static AnormousConfig anormousConfig;

	private IEntityMapper entityMapper;

	public synchronized static AnormousConfig initialize()
	{
		if (anormousConfig == null)
			anormousConfig = new AnormousConfig();

		return anormousConfig;
	}

	private AnormousConfig()
	{
		entityMapper = new DefaultEntityMapper();
	}

	public IEntityMapper getEntityMapper()
	{
		return entityMapper;
	}

	public void setEntityMapper(IEntityMapper entityMapper)
	{
		this.entityMapper = entityMapper;
	}
}
