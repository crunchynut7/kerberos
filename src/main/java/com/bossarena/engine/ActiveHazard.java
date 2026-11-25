package com.bossarena.engine;

import lombok.Value;

/** A {@link TileHazard} that has been placed on the arena. */
@Value
public class ActiveHazard
{
	TileHazard hazard;
	int spawnTick;

	/** The first tick the hazard deals damage on. */
	public int activeFromTick()
	{
		return spawnTick + hazard.getWarningTicks();
	}

	/** The first tick the hazard is gone on. */
	public int expiresTick()
	{
		return activeFromTick() + hazard.getActiveTicks();
	}

	public boolean isActive(int tick)
	{
		return tick >= activeFromTick() && tick < expiresTick();
	}
}
