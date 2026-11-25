package com.bossarena.engine;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import lombok.Getter;
import lombok.Setter;

/**
 * The static layout of a fight: its size, which tiles are walkable, and how each tile is painted.
 */
public class Arena
{
	@Getter
	private final int width;
	@Getter
	private final int height;
	private final boolean[][] blocked;
	private final Map<Tile, Integer> paint = new HashMap<>();
	private final Map<Character, Tile> markers = new HashMap<>();
	private final Set<Tile> walls = new HashSet<>();
	/** How the ground and walls are dressed, or null for plain outlines. */
	@Getter
	@Setter
	private FloorTheme theme;

	public Arena(int width, int height)
	{
		if (width <= 0 || height <= 0)
		{
			throw new IllegalArgumentException("arena must be at least 1x1");
		}
		this.width = width;
		this.height = height;
		this.blocked = new boolean[width][height];
	}

	/**
	 * Builds an arena from a drawing. The first row is the northern edge.
	 * <ul>
	 * <li>{@code #} is a wall: blocked, and drawn standing up from the floor</li>
	 * <li>{@code .} or a space is open floor</li>
	 * <li>any other character is open floor and is recorded as a marker, see {@link #marker(char)}</li>
	 * </ul>
	 */
	public static Arena parse(String... rows)
	{
		if (rows.length == 0)
		{
			throw new IllegalArgumentException("arena drawing is empty");
		}
		int width = rows[0].length();
		Arena arena = new Arena(width, rows.length);
		for (int row = 0; row < rows.length; row++)
		{
			if (rows[row].length() != width)
			{
				throw new IllegalArgumentException("row " + row + " is not " + width + " tiles wide");
			}
			int y = rows.length - 1 - row;
			for (int x = 0; x < width; x++)
			{
				char c = rows[row].charAt(x);
				if (c == '#')
				{
					arena.blocked[x][y] = true;
					arena.walls.add(new Tile(x, y));
				}
				else if (c != '.' && c != ' ')
				{
					arena.markers.put(c, new Tile(x, y));
				}
			}
		}
		return arena;
	}

	public boolean contains(Tile tile)
	{
		return tile.getX() >= 0 && tile.getY() >= 0 && tile.getX() < width && tile.getY() < height;
	}

	/** True for tiles outside the arena as well as blocked tiles inside it. */
	public boolean isBlocked(Tile tile)
	{
		return !contains(tile) || blocked[tile.getX()][tile.getY()];
	}

	/** True for tiles drawn as a wall. A tile can be blocked without being one, e.g. under a prop. */
	public boolean isWall(Tile tile)
	{
		return walls.contains(tile);
	}

	public void setBlocked(Tile tile, boolean value)
	{
		if (contains(tile))
		{
			blocked[tile.getX()][tile.getY()] = value;
		}
	}

	/** The tile a marker character was drawn on in {@link #parse(String...)}. */
	public Tile marker(char c)
	{
		Tile tile = markers.get(c);
		if (tile == null)
		{
			throw new IllegalArgumentException("arena drawing has no '" + c + "' marker");
		}
		return tile;
	}

	/** Gives a tile a permanent fill colour (ARGB). */
	public void paint(Tile tile, int argb)
	{
		paint.put(tile, argb);
	}

	public Map<Tile, Integer> getPaint()
	{
		return Collections.unmodifiableMap(paint);
	}
}
