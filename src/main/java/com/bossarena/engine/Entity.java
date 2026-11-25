package com.bossarena.engine;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.Getter;
import lombok.Setter;

/**
 * Anything that stands on the arena and can be hit: the player or a boss.
 */
@Getter
public abstract class Entity
{
	private final String name;
	/** Footprint is size x size tiles; {@link #getTile()} is its south-west corner. */
	private final int size;
	private final int maxHitpoints;
	private int hitpoints;
	private Tile tile;
	/** Where the entity stood at the start of the current tick. */
	private Tile previousTile;
	/** Tiles stepped through this tick, in order. Empty when it did not move. */
	private final List<Tile> steps = new ArrayList<>();
	private final List<Hitsplat> hitsplats = new ArrayList<>();
	@Setter
	private EntityVisual visual = EntityVisual.builder().build();
	/** The entity this one turns to face when standing still, or null. */
	@Setter
	private Entity facing;
	private int animation = -1;
	/** Incremented each time an animation is requested, so the same animation can replay. */
	private int animationSequence;
	private final Map<String, Pose> poses = new HashMap<>();
	private String overheadText;
	/** The first tick {@link #getOverheadText()} is no longer shown on. */
	private int overheadTextUntilTick;

	protected Entity(String name, int size, int maxHitpoints, Tile tile)
	{
		this.name = name;
		this.size = size;
		this.maxHitpoints = maxHitpoints;
		this.hitpoints = maxHitpoints;
		this.tile = tile;
		this.previousTile = tile;
	}

	public boolean isDead()
	{
		return hitpoints <= 0;
	}

	public List<Tile> getSteps()
	{
		return Collections.unmodifiableList(steps);
	}

	public List<Hitsplat> getHitsplats()
	{
		return Collections.unmodifiableList(hitsplats);
	}

	/** True if the given tile is under this entity. */
	public boolean occupies(Tile t)
	{
		return t.getX() >= tile.getX() && t.getX() < tile.getX() + size
			&& t.getY() >= tile.getY() && t.getY() < tile.getY() + size;
	}

	/** Distance in tiles from a tile to the nearest edge of this entity. 0 means underneath it. */
	public int distanceFrom(Tile t)
	{
		return Math.max(axisGap(t.getX(), tile.getX()), axisGap(t.getY(), tile.getY()));
	}

	/** True if the tile touches this entity's edge without being diagonal to a corner. */
	public boolean isCardinallyAdjacent(Tile t)
	{
		return axisGap(t.getX(), tile.getX()) + axisGap(t.getY(), tile.getY()) == 1;
	}

	private int axisGap(int value, int low)
	{
		int high = low + size - 1;
		return Math.max(0, Math.max(low - value, value - high));
	}

	/**
	 * Starts a pose on one part of the entity. Each part holds one pose at a time, so a boss can,
	 * say, rear a head while a leg is doing something else.
	 *
	 * @param part     which part of the entity is posing, e.g. "head"
	 * @param untilTick the first tick the pose is over on
	 */
	public void pose(String part, String name, int fromTick, int untilTick)
	{
		poses.put(part, new Pose(name, fromTick, untilTick));
	}

	/** The pose last started on a part, which may already be over, or null. */
	public Pose getPose(String part)
	{
		return poses.get(part);
	}

	/** Shows text above the entity until the given tick. */
	public void say(String text, int untilTick)
	{
		overheadText = text;
		overheadTextUntilTick = untilTick;
	}

	/** Requests a one-off animation, such as an attack. */
	public void playAnimation(int animationId)
	{
		animation = animationId;
		animationSequence++;
	}

	void beginTick()
	{
		previousTile = tile;
		steps.clear();
	}

	void step(Tile to)
	{
		tile = to;
		steps.add(to);
	}

	/** Moves without walking, e.g. a boss jumping across the arena. */
	void teleport(Tile to)
	{
		tile = to;
		previousTile = to;
		steps.clear();
	}

	void changeHitpoints(int delta)
	{
		hitpoints = Math.max(0, Math.min(maxHitpoints, hitpoints + delta));
	}

	void addHitsplat(Hitsplat hitsplat)
	{
		hitsplats.add(hitsplat);
	}

	void expireHitsplats(int olderThanTick)
	{
		hitsplats.removeIf(h -> h.getTick() < olderThanTick);
	}
}
