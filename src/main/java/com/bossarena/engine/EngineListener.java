package com.bossarena.engine;

/** Callbacks for things the renderer may want to react to as they happen. */
public interface EngineListener
{
	default void onMessage(String message)
	{
	}

	default void onStateChanged(ArenaEngine.State state)
	{
	}
}
