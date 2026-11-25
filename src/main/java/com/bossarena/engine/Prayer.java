package com.bossarena.engine;

public enum Prayer
{
	NONE,
	PROTECT_FROM_MELEE,
	PROTECT_FROM_MISSILES,
	PROTECT_FROM_MAGIC;

	public boolean blocks(AttackStyle style)
	{
		switch (this)
		{
			case PROTECT_FROM_MELEE:
				return style == AttackStyle.MELEE;
			case PROTECT_FROM_MISSILES:
				return style == AttackStyle.RANGED;
			case PROTECT_FROM_MAGIC:
				return style == AttackStyle.MAGIC;
			default:
				return false;
		}
	}
}
