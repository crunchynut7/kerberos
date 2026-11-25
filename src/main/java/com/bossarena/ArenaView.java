package com.bossarena;

import com.bossarena.engine.Entity;
import com.bossarena.engine.Tile;
import java.util.List;
import net.runelite.api.Client;
import net.runelite.api.Perspective;
import net.runelite.api.WorldView;
import net.runelite.api.coords.LocalPoint;
import net.runelite.api.coords.WorldPoint;

/**
 * Maps arena tiles onto the game scene. The arena is anchored to a fixed world tile when the fight
 * starts, so it stays put if the scene reloads.
 */
class ArenaView
{
	/** World location of arena tile (0, 0). */
	private final WorldPoint origin;

	ArenaView(WorldPoint origin)
	{
		this.origin = origin;
	}

	int plane()
	{
		return origin.getPlane();
	}

	/**
	 * The scene position of the centre of a {@code size} x {@code size} footprint whose south-west
	 * tile is at the (possibly fractional) arena coordinates given.
	 *
	 * @return null if that position is outside the loaded scene
	 */
	LocalPoint toLocal(Client client, double x, double y, int size)
	{
		WorldView worldView = client.getTopLevelWorldView();
		if (worldView == null || worldView.getPlane() != origin.getPlane())
		{
			return null;
		}
		double sceneX = origin.getX() - worldView.getBaseX() + x;
		double sceneY = origin.getY() - worldView.getBaseY() + y;
		if (sceneX < 0 || sceneY < 0 || sceneX + size > worldView.getSizeX() || sceneY + size > worldView.getSizeY())
		{
			return null;
		}
		int half = size * Perspective.LOCAL_HALF_TILE_SIZE;
		return new LocalPoint(
			(int) Math.round(sceneX * Perspective.LOCAL_TILE_SIZE) + half,
			(int) Math.round(sceneY * Perspective.LOCAL_TILE_SIZE) + half,
			worldView);
	}

	LocalPoint toLocal(Client client, Tile tile, int size)
	{
		return toLocal(client, tile.getX(), tile.getY(), size);
	}

	/**
	 * Where an entity appears part-way through a tick, as arena coordinates of its south-west tile.
	 *
	 * @param progress 0 at the start of the tick, 1 at the end
	 */
	static double[] interpolate(Entity entity, double progress)
	{
		List<Tile> steps = entity.getSteps();
		if (steps.isEmpty())
		{
			return new double[]{entity.getTile().getX(), entity.getTile().getY()};
		}
		double scaled = Math.max(0, Math.min(1, progress)) * steps.size();
		int index = Math.min((int) scaled, steps.size() - 1);
		double fraction = scaled - index;
		Tile from = index == 0 ? entity.getPreviousTile() : steps.get(index - 1);
		Tile to = steps.get(index);
		return new double[]{
			from.getX() + (to.getX() - from.getX()) * fraction,
			from.getY() + (to.getY() - from.getY()) * fraction,
		};
	}

	/** The centre of an entity's footprint part-way through a tick, in arena coordinates. */
	static double[] centre(Entity entity, double progress)
	{
		double[] corner = interpolate(entity, progress);
		double half = (entity.getSize() - 1) / 2.0;
		return new double[]{corner[0] + half, corner[1] + half};
	}
}
