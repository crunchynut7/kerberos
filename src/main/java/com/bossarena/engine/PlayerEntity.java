package com.bossarena.engine;

import java.util.ArrayDeque;
import java.util.Deque;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;

@Getter
public class PlayerEntity extends Entity
{
	private final CombatStats stats;
	@Setter(AccessLevel.PACKAGE)
	private Prayer prayer = Prayer.NONE;
	@Setter(AccessLevel.PACKAGE)
	private boolean running = true;
	/** The boss being attacked, or null. */
	@Setter(AccessLevel.PACKAGE)
	private Boss target;
	/** The tile the player is walking to, or null. */
	@Setter(AccessLevel.PACKAGE)
	private Tile destination;
	@Getter(AccessLevel.PACKAGE)
	private final Deque<Tile> path = new ArrayDeque<>();
	/** The first tick the player may attack again on. */
	@Setter(AccessLevel.PACKAGE)
	private int nextAttackTick;

	PlayerEntity(CombatStats stats, Tile tile)
	{
		super("Player", 1, stats.getMaxHitpoints(), tile);
		this.stats = stats;
	}
}
