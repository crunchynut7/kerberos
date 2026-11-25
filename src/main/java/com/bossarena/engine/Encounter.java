package com.bossarena.engine;

/**
 * The definition of one fight. Implement this to add a boss: describe the arena, choose the
 * player's stats, and spawn bosses with their behaviour in {@link #start(ArenaEngine)}.
 */
public interface Encounter
{
	String name();

	/** Called once per run; must return a fresh arena each time. */
	Arena createArena();

	Tile playerStart(Arena arena);

	CombatStats playerStats();

	/** Spawn bosses and set up anything else the fight needs. */
	void start(ArenaEngine engine);

	/** Called once per tick before boss behaviours, for fight-wide logic such as phases. */
	default void tick(ArenaEngine engine)
	{
	}
}
