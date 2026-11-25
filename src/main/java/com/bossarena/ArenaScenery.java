package com.bossarena;

import com.bossarena.engine.FloorTheme;
import com.bossarena.engine.Tile;
import java.awt.AlphaComposite;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Composite;
import java.awt.Graphics2D;
import java.awt.Polygon;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Draws the arena's dressing: paving, walls and shields. Everything is built from points placed in
 * the scene and projected to the screen, so it follows the camera and scales with zoom.
 */
final class ArenaScenery
{
	/** Turns a position in the scene into a position on screen. */
	@FunctionalInterface
	interface Projector
	{
		/**
		 * @param x      scene units east
		 * @param y      scene units north
		 * @param height scene units above the ground at that spot
		 * @return null if the position is not on screen
		 */
		java.awt.Point project(int x, int y, int height);
	}

	private static final int HALF_TILE = 64;

	/** Paving stones within one tile as {u0, v0, u1, v1}: two courses, the second offset by half a stone. */
	private static final float[][] STONES = {
		{0f, 0f, 0.5f, 0.5f},
		{0.5f, 0f, 1f, 0.5f},
		{0f, 0.5f, 0.25f, 1f},
		{0.25f, 0.5f, 0.75f, 1f},
		{0.75f, 0.5f, 1f, 1f},
	};
	private static final int WEST_HALF_STONE = 2;
	private static final int EAST_HALF_STONE = 4;
	/** Noise index for a stone shared between two tiles; any value that is not an index into {@link #STONES}. */
	private static final int SHARED_STONE = 9;
	/** Half the width of the gap between stones, as a fraction of a tile. */
	private static final float MORTAR = 0.035f;
	private static final float LANE_TINT = 0.28f;

	/**
	 * A heater shield's outline as {across, up}: across runs from -1 to 1, up from 0 at the point
	 * to 1 at the top edge.
	 */
	private static final float[][] SHIELD = {
		{-1f, 1f}, {-0.5f, 0.93f}, {0f, 1f}, {0.5f, 0.93f}, {1f, 1f},
		{1f, 0.5f}, {0.82f, 0.26f}, {0.46f, 0.08f}, {0f, 0f}, {-0.46f, 0.08f}, {-0.82f, 0.26f}, {-1f, 0.5f},
	};
	private static final int SHIELD_HALF_WIDTH = 50;
	private static final int SHIELD_HEIGHT = 165;
	private static final int SHIELD_LIFT = 8;
	private static final int SHIELD_HALF_DEPTH = 13;
	private static final float PANEL_SCALE = 0.7f;
	private static final float PANEL_CENTRE_UP = 0.56f;
	private static final int UNLIT_METAL = 0x6C6C72;
	private static final Color SHIELD_EDGE = new Color(18, 18, 20);
	private static final Color SHIELD_RIM = new Color(52, 50, 54);
	private static final BasicStroke OUTLINE = new BasicStroke(2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND);
	private static final float[] GLOW_WIDTHS = {16f, 10f, 5f};
	private static final int[] GLOW_ALPHAS = {45, 80, 130};

	private ArenaScenery()
	{
	}

	// ---- Floor ----

	/**
	 * Paves one tile.
	 *
	 * @param tile  the tile's outline on screen, with four corners
	 * @param tint  a colour (ARGB) to wash the stones with, or null
	 */
	static void drawPaving(Graphics2D graphics, Polygon tile, Tile at, FloorTheme theme, Integer tint)
	{
		if (tile.npoints != 4)
		{
			return;
		}
		graphics.setColor(new Color(theme.getMortarArgb(), true));
		graphics.fill(tile);

		int base = tint == null ? theme.getStoneArgb() : mix(theme.getStoneArgb(), tint, LANE_TINT);
		int[] xs = new int[4];
		int[] ys = new int[4];
		for (int i = 0; i < STONES.length; i++)
		{
			float[] stone = STONES[i];
			// The offset course's end stones are halves that join the matching half on the next
			// tile, so they run right up to the tile's edge and share that neighbour's shade.
			boolean joinsWest = i == WEST_HALF_STONE;
			boolean joinsEast = i == EAST_HALF_STONE;
			float u0 = joinsWest ? 0f : stone[0] + MORTAR;
			float v0 = stone[1] + MORTAR;
			float u1 = joinsEast ? 1f : stone[2] - MORTAR;
			float v1 = stone[3] - MORTAR;
			float variation = joinsWest || joinsEast
				? noise(at.getX() + (joinsEast ? 1 : 0), at.getY(), SHARED_STONE)
				: noise(at.getX(), at.getY(), i);
			corner(tile, u0, v0, xs, ys, 0);
			corner(tile, u1, v0, xs, ys, 1);
			corner(tile, u1, v1, xs, ys, 2);
			corner(tile, u0, v1, xs, ys, 3);
			graphics.setColor(new Color(shade(base, 0.84f + 0.3f * variation)));
			graphics.fillPolygon(xs, ys, 4);
		}
	}

	/** Maps a point inside a tile, given as fractions across and up it, onto the tile's screen outline. */
	private static void corner(Polygon tile, float u, float v, int[] xs, int[] ys, int index)
	{
		float a = (1 - u) * (1 - v);
		float b = u * (1 - v);
		float c = u * v;
		float d = (1 - u) * v;
		xs[index] = Math.round(a * tile.xpoints[0] + b * tile.xpoints[1] + c * tile.xpoints[2] + d * tile.xpoints[3]);
		ys[index] = Math.round(a * tile.ypoints[0] + b * tile.ypoints[1] + c * tile.ypoints[2] + d * tile.ypoints[3]);
	}

	// ---- Walls ----

	/** A wall block's footprint and top on screen, ready to draw. */
	static final class WallBlock
	{
		private final Polygon base;
		private final Polygon top;

		private WallBlock(Polygon base, Polygon top)
		{
			this.base = base;
			this.top = top;
		}

		/** Roughly how far down the screen the block sits; blocks higher up are further away. */
		int depth()
		{
			return base.getBounds().y + base.getBounds().height;
		}
	}

	/**
	 * @param x the tile centre's scene position east
	 * @param y the tile centre's scene position north
	 * @return null if the block is not on screen
	 */
	static WallBlock wallBlock(Projector projector, int x, int y, FloorTheme theme)
	{
		Polygon base = tileOutline(projector, x, y, 0);
		Polygon top = tileOutline(projector, x, y, theme.getWallHeight());
		return base == null || top == null ? null : new WallBlock(base, top);
	}

	private static Polygon tileOutline(Projector projector, int x, int y, int height)
	{
		int[][] corners = {{-1, -1}, {1, -1}, {1, 1}, {-1, 1}};
		Polygon outline = new Polygon();
		for (int[] corner : corners)
		{
			java.awt.Point point = projector.project(x + corner[0] * HALF_TILE, y + corner[1] * HALF_TILE, height);
			if (point == null)
			{
				return null;
			}
			outline.addPoint(point.x, point.y);
		}
		return outline;
	}

	/** Draws a wall block as a solid box: its silhouette in shadow, then its top face. */
	static void drawWall(Graphics2D graphics, WallBlock block, FloorTheme theme)
	{
		Polygon silhouette = hull(block.base, block.top);
		graphics.setColor(new Color(shade(theme.getWallArgb(), 0.55f)));
		graphics.fill(silhouette);
		graphics.setColor(new Color(theme.getWallArgb(), true));
		graphics.fill(block.top);
		graphics.setColor(new Color(shade(theme.getWallArgb(), 0.35f)));
		graphics.setStroke(new BasicStroke(1f));
		graphics.draw(block.top);
	}

	// ---- Shields ----

	/**
	 * Draws an upright shield standing on a tile, facing along the north-south axis.
	 *
	 * @param x           the tile centre's scene position east
	 * @param y           the tile centre's scene position north
	 * @param cameraSouth whether the camera is south of the shield, so its south face is the visible one
	 * @param argb        the shield's colour when lit
	 * @param icon     an emblem for its face, or null
	 * @param lit      whether the shield is working
	 * @param flashing whether it has just blocked something
	 */
	static void drawShield(Graphics2D graphics, Projector projector, int x, int y, boolean cameraSouth, int argb,
		BufferedImage icon, boolean lit, boolean flashing)
	{
		// Put whichever face the camera can see on top.
		int towardsCamera = cameraSouth ? -1 : 1;
		Polygon front = shieldFace(projector, x, y, towardsCamera * SHIELD_HALF_DEPTH, 1f);
		Polygon back = shieldFace(projector, x, y, -towardsCamera * SHIELD_HALF_DEPTH, 1f);
		Polygon panel = shieldFace(projector, x, y, towardsCamera * SHIELD_HALF_DEPTH, PANEL_SCALE);
		if (front == null || back == null || panel == null)
		{
			return;
		}

		int rgb = argb & 0xFFFFFF;
		int face;
		int inner;
		if (flashing)
		{
			face = 0xFFFFFF;
			inner = mix(0xFFFFFF, rgb, 0.25f);
		}
		else if (lit)
		{
			face = mix(rgb, 0xFFFFFF, 0.12f);
			inner = mix(rgb, 0xFFFFFF, 0.45f);
		}
		else
		{
			face = mix(UNLIT_METAL, rgb, 0.22f);
			inner = shade(face, 0.72f);
		}

		if (lit || flashing)
		{
			int glow = flashing ? 0xFFFFFF : mix(rgb, 0xFFFFFF, 0.35f);
			for (int i = 0; i < GLOW_WIDTHS.length; i++)
			{
				float width = flashing ? GLOW_WIDTHS[i] * 1.6f : GLOW_WIDTHS[i];
				graphics.setStroke(new BasicStroke(width, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
				graphics.setColor(new Color((GLOW_ALPHAS[i] << 24) | glow, true));
				graphics.draw(front);
			}
		}

		// The rim: everything between the two faces.
		graphics.setColor(SHIELD_RIM);
		graphics.fill(hull(front, back));
		graphics.setColor(new Color(face));
		graphics.fill(front);
		graphics.setColor(new Color(inner));
		graphics.fill(panel);

		graphics.setStroke(OUTLINE);
		graphics.setColor(SHIELD_EDGE);
		graphics.draw(panel);
		graphics.draw(front);

		if (icon != null)
		{
			java.awt.Rectangle bounds = panel.getBounds();
			int size = Math.round(Math.min(bounds.width, bounds.height) * 0.78f);
			if (size >= 6)
			{
				Composite composite = graphics.getComposite();
				Object interpolation = graphics.getRenderingHint(RenderingHints.KEY_INTERPOLATION);
				graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
				graphics.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, lit || flashing ? 1f : 0.5f));
				graphics.drawImage(icon, bounds.x + (bounds.width - size) / 2, bounds.y + (bounds.height - size) / 2 - size / 10,
					size, size, null);
				graphics.setComposite(composite);
				if (interpolation != null)
				{
					graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, interpolation);
				}
			}
		}
	}

	/**
	 * One face of the shield on screen.
	 *
	 * @param offset how far north (positive) or south of the tile's centre the face stands
	 * @param scale  1 for the full outline, less for an inset panel
	 * @return null if any of it is off screen
	 */
	private static Polygon shieldFace(Projector projector, int x, int y, int offset, float scale)
	{
		Polygon face = new Polygon();
		for (float[] point : SHIELD)
		{
			float across = point[0] * scale;
			float up = PANEL_CENTRE_UP + (point[1] - PANEL_CENTRE_UP) * scale;
			java.awt.Point canvas = projector.project(
				x + Math.round(across * SHIELD_HALF_WIDTH),
				y + offset,
				SHIELD_LIFT + Math.round(up * SHIELD_HEIGHT));
			if (canvas == null)
			{
				return null;
			}
			face.addPoint(canvas.x, canvas.y);
		}
		return face;
	}

	// ---- Helpers ----

	/** The convex outline around two polygons, i.e. the silhouette of the solid between them. */
	static Polygon hull(Polygon first, Polygon second)
	{
		List<int[]> points = new ArrayList<>();
		for (int i = 0; i < first.npoints; i++)
		{
			points.add(new int[]{first.xpoints[i], first.ypoints[i]});
		}
		for (int i = 0; i < second.npoints; i++)
		{
			points.add(new int[]{second.xpoints[i], second.ypoints[i]});
		}
		points.sort((a, b) -> a[0] != b[0] ? Integer.compare(a[0], b[0]) : Integer.compare(a[1], b[1]));

		int[][] chain = new int[points.size() * 2][];
		int size = 0;
		for (int[] point : points)
		{
			while (size >= 2 && cross(chain[size - 2], chain[size - 1], point) <= 0)
			{
				size--;
			}
			chain[size++] = point;
		}
		int lower = size + 1;
		for (int i = points.size() - 2; i >= 0; i--)
		{
			int[] point = points.get(i);
			while (size >= lower && cross(chain[size - 2], chain[size - 1], point) <= 0)
			{
				size--;
			}
			chain[size++] = point;
		}
		Polygon hull = new Polygon();
		for (int[] point : Arrays.copyOf(chain, Math.max(0, size - 1)))
		{
			hull.addPoint(point[0], point[1]);
		}
		return hull;
	}

	private static long cross(int[] origin, int[] a, int[] b)
	{
		return (long) (a[0] - origin[0]) * (b[1] - origin[1]) - (long) (a[1] - origin[1]) * (b[0] - origin[0]);
	}

	/** Blends two RGB colours; {@code amount} is how much of the second to use, from 0 to 1. */
	static int mix(int first, int second, float amount)
	{
		int r = Math.round(((first >> 16) & 0xFF) * (1 - amount) + ((second >> 16) & 0xFF) * amount);
		int g = Math.round(((first >> 8) & 0xFF) * (1 - amount) + ((second >> 8) & 0xFF) * amount);
		int b = Math.round((first & 0xFF) * (1 - amount) + (second & 0xFF) * amount);
		return (r << 16) | (g << 8) | b;
	}

	/** Darkens (below 1) or lightens (above 1) an RGB colour. */
	static int shade(int rgb, float factor)
	{
		int r = Math.min(255, Math.round(((rgb >> 16) & 0xFF) * factor));
		int g = Math.min(255, Math.round(((rgb >> 8) & 0xFF) * factor));
		int b = Math.min(255, Math.round((rgb & 0xFF) * factor));
		return (r << 16) | (g << 8) | b;
	}

	/** A repeatable pseudo-random value from 0 to 1 for a stone, so the floor does not shimmer between frames. */
	private static float noise(int x, int y, int index)
	{
		int hash = x * 73856093 ^ y * 19349663 ^ index * 83492791;
		hash ^= hash >>> 13;
		hash *= 0x5bd1e995;
		hash ^= hash >>> 15;
		return (hash & 0xFFFF) / 65535f;
	}
}
