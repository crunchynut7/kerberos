package com.bossarena.engine;

import lombok.Value;

/**
 * Something an entity is acting out for a while, such as rearing a head. The renderer decides
 * what a pose looks like; the engine only says which one and when.
 */
@Value
public class Pose
{
	String name;
	int startTick;
	/** The first tick the pose is over on. */
	int endTick;

	/**
	 * How far through the pose a moment is, from 0 to 1.
	 *
	 * @param time the tick, plus the fraction of the way to the next one
	 * @return a negative number if the pose is not playing at that moment
	 */
	public double phase(double time)
	{
		if (time < startTick || time >= endTick)
		{
			return -1;
		}
		return (time - startTick) / (endTick - startTick);
	}
}
