package com.bossarena.engine;

import java.util.Set;
import lombok.Builder;
import lombok.Singular;
import lombok.Value;

/**
 * A set of tiles that warns for a while and then damages the player standing on them.
 * Used for things like falling rocks, floor fire or a sweeping beam.
 * A hazard with a maximum damage of 0 hurts nothing and is just a coloured flash on the floor.
 */
@Value
@Builder
public class TileHazard
{
	@Singular
	Set<Tile> tiles;
	/** Ticks the hazard is shown as a warning before it starts dealing damage. */
	@Builder.Default
	int warningTicks = 2;
	/** Ticks the hazard deals damage for once the warning ends. */
	@Builder.Default
	int activeTicks = 1;
	@Builder.Default
	int minDamage = 10;
	@Builder.Default
	int maxDamage = 20;
	@Builder.Default
	int warningArgb = 0x60FFA500;
	@Builder.Default
	int activeArgb = 0x90FF0000;
}
