package com.bossarena.engine;

import lombok.Value;

@Value
public class Hitsplat
{
	public enum Kind
	{
		DAMAGE,
		BLOCK,
		HEAL
	}

	Kind kind;
	int amount;
	/** The engine tick the hitsplat appeared on. */
	int tick;
}
