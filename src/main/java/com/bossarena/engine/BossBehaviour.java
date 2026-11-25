package com.bossarena.engine;

@FunctionalInterface
public interface BossBehaviour
{
	/** Called once per tick, after the player has moved and attacked. */
	void tick(ArenaEngine engine, Boss boss);
}
