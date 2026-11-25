package com.bossarena.engine;

import lombok.Getter;
import lombok.Setter;

@Getter
public class Boss extends Entity
{
	/** Runs once per tick while the boss is alive. May be null for a passive boss. */
	@Setter
	private BossBehaviour behaviour;

	Boss(String name, int size, int maxHitpoints, Tile tile)
	{
		super(name, size, maxHitpoints, tile);
	}
}
