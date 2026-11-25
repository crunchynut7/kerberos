package com.bossarena.engine;

public enum AttackStyle
{
	MELEE,
	RANGED,
	MAGIC,
	/** Cannot be reduced by a protection prayer. */
	TYPELESS;

	/** Ticks between an attack being launched and its hitsplat, for an attacker {@code distance} tiles away. */
	public int hitDelay(int distance)
	{
		switch (this)
		{
			case RANGED:
				return 1 + (3 + distance) / 6;
			case MAGIC:
				return 1 + (1 + distance) / 3;
			default:
				return 1;
		}
	}
}
