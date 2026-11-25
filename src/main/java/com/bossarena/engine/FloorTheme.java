package com.bossarena.engine;

import lombok.Builder;
import lombok.Value;

/**
 * How an arena's ground and walls are dressed. An arena without a theme is drawn as plain
 * outlines over whatever ground is really there.
 */
@Value
@Builder
public class FloorTheme
{
	/** Colour (ARGB) of the paving stones. Each stone varies a little around it. */
	@Builder.Default
	int stoneArgb = 0xFF77726B;
	/** Colour (ARGB) of the gaps between stones. */
	@Builder.Default
	int mortarArgb = 0xFF2E2B28;
	/** Colour (ARGB) of the top of a wall. Its sides are drawn darker. */
	@Builder.Default
	int wallArgb = 0xFF5A554F;
	/** How tall walls stand, in scene units. A tile is 128 units wide. */
	@Builder.Default
	int wallHeight = 70;
}
