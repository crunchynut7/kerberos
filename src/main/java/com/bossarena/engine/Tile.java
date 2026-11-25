package com.bossarena.engine;

import lombok.Value;

/**
 * A tile in arena coordinates. (0, 0) is the south-west corner; x grows east and y grows north.
 */
@Value
public class Tile
{
	int x;
	int y;

	public Tile plus(int dx, int dy)
	{
		return new Tile(x + dx, y + dy);
	}

	/** Chebyshev distance, i.e. the number of walking steps on an open grid. */
	public int distanceTo(Tile other)
	{
		return Math.max(Math.abs(x - other.x), Math.abs(y - other.y));
	}
}
