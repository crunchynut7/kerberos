package com.bossarena;

import com.bossarena.engine.ArenaEngine;
import com.bossarena.engine.Entity;
import java.util.LinkedHashMap;
import java.util.Map;
import lombok.Getter;
import net.runelite.api.Constants;

/** One running fight: the engine, where it sits in the world, and the objects drawing it. */
@Getter
class ArenaSession
{
	static final int CLIENT_TICKS_PER_TICK = Constants.GAME_TICK_LENGTH / Constants.CLIENT_TICK_LENGTH;

	private final ArenaEngine engine;
	private final ArenaView view;
	private final Map<Entity, ActorVisual> visuals = new LinkedHashMap<>();
	private int clientTicks;

	ArenaSession(ArenaEngine engine, ArenaView view)
	{
		this.engine = engine;
		this.view = view;
	}

	/**
	 * Counts one client tick.
	 *
	 * @return true when a full engine tick has elapsed
	 */
	boolean advanceClientTick()
	{
		clientTicks++;
		if (clientTicks >= CLIENT_TICKS_PER_TICK)
		{
			clientTicks = 0;
			return true;
		}
		return false;
	}

	/** How far through the current engine tick we are, from 0 to 1. */
	double progress()
	{
		return (double) clientTicks / CLIENT_TICKS_PER_TICK;
	}
}
