package com.bossarena.engine;

import lombok.Value;

/**
 * A purely visual projectile. Damage is handled separately by whatever launched it.
 * Each end is either an entity, which it follows, or a fixed tile.
 */
@Value
public class Projectile
{
	/** The entity it leaves from, or null if it leaves from {@link #sourceTile}. */
	Entity source;
	Tile sourceTile;
	/** The entity it flies to, or null if it flies to {@link #targetTile}. */
	Entity target;
	Tile targetTile;
	/** What kind of attack it is, which decides how it is drawn; null for a plain orb. */
	AttackStyle style;
	int launchTick;
	int landTick;
	int argb;
}
