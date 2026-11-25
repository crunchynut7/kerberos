package com.bossarena;

import com.bossarena.engine.ActiveHazard;
import com.bossarena.engine.Arena;
import com.bossarena.engine.ArenaEngine;
import com.bossarena.engine.Entity;
import com.bossarena.engine.Hitsplat;
import com.bossarena.engine.PlayerEntity;
import com.bossarena.engine.Projectile;
import com.bossarena.engine.Tile;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.Polygon;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.inject.Inject;
import net.runelite.api.Client;
import net.runelite.api.Perspective;
import net.runelite.api.Point;
import net.runelite.api.coords.LocalPoint;
import net.runelite.client.ui.FontManager;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;

/**
 * Draws everything that sits on the scene: tiles, hazards, projectiles, health bars and hitsplats.
 * It also works out which arena tile the mouse is over, since the tile outlines are already here.
 */
class ArenaOverlay extends Overlay
{
	private static final Color GRID = new Color(255, 255, 255, 45);
	private static final Color WALL = new Color(0, 0, 0, 110);
	private static final Color HOVER = new Color(255, 255, 255, 200);
	private static final Color DESTINATION = new Color(255, 235, 59, 220);
	private static final Color HEALTH_GREEN = new Color(0, 200, 0);
	private static final Color HEALTH_RED = new Color(200, 0, 0);
	private static final Color SPLAT_DAMAGE = new Color(185, 20, 20);
	private static final Color SPLAT_BLOCK = new Color(50, 90, 200);
	private static final Color SPLAT_HEAL = new Color(150, 60, 200);
	private static final BasicStroke THIN = new BasicStroke(1);
	private static final BasicStroke THICK = new BasicStroke(2);
	private static final int HEALTH_BAR_HEIGHT = 5;
	private static final int[][] SPLAT_OFFSETS = {{0, 0}, {0, -20}, {-16, -10}, {16, -10}};

	private final Client client;
	private final BossArenaPlugin plugin;
	private final BossArenaConfig config;

	@Inject
	ArenaOverlay(Client client, BossArenaPlugin plugin, BossArenaConfig config)
	{
		this.client = client;
		this.plugin = plugin;
		this.config = config;
		setPosition(OverlayPosition.DYNAMIC);
		setLayer(OverlayLayer.ABOVE_SCENE);
	}

	@Override
	public Dimension render(Graphics2D graphics)
	{
		ArenaSession session = plugin.getSession();
		if (session == null)
		{
			plugin.setHoveredTile(null);
			return null;
		}
		graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
		double progress = session.progress();

		renderTiles(graphics, session);
		for (ActorVisual visual : session.getVisuals().values())
		{
			renderActor(graphics, session, visual, progress);
		}
		for (Projectile projectile : session.getEngine().getProjectiles())
		{
			renderProjectile(graphics, session, projectile, progress);
		}
		return null;
	}

	private void renderTiles(Graphics2D graphics, ArenaSession session)
	{
		ArenaEngine engine = session.getEngine();
		Arena arena = engine.getArena();
		ArenaView view = session.getView();
		Map<Tile, Integer> paint = arena.getPaint();
		Map<Tile, Integer> hazardColours = hazardColours(engine);
		Tile destination = engine.getPlayer().getDestination();
		Point mouse = client.getMouseCanvasPosition();

		Tile hovered = null;
		Polygon hoveredPolygon = null;
		Polygon destinationPolygon = null;

		for (int x = 0; x < arena.getWidth(); x++)
		{
			for (int y = 0; y < arena.getHeight(); y++)
			{
				Tile tile = new Tile(x, y);
				LocalPoint local = view.toLocal(client, tile, 1);
				Polygon polygon = local == null ? null : Perspective.getCanvasTilePoly(client, local);
				if (polygon == null)
				{
					continue;
				}
				if (arena.isBlocked(tile))
				{
					graphics.setColor(WALL);
					graphics.fill(polygon);
					continue;
				}
				Integer fill = hazardColours.get(tile);
				if (fill == null)
				{
					fill = paint.get(tile);
				}
				if (fill != null)
				{
					graphics.setColor(new Color(fill, true));
					graphics.fill(polygon);
				}
				if (config.showGrid())
				{
					graphics.setColor(GRID);
					graphics.setStroke(THIN);
					graphics.draw(polygon);
				}
				if (mouse != null && polygon.contains(mouse.getX(), mouse.getY()))
				{
					hovered = tile;
					hoveredPolygon = polygon;
				}
				if (tile.equals(destination))
				{
					destinationPolygon = polygon;
				}
			}
		}

		graphics.setStroke(THICK);
		if (destinationPolygon != null)
		{
			graphics.setColor(DESTINATION);
			graphics.draw(destinationPolygon);
		}
		if (hoveredPolygon != null)
		{
			graphics.setColor(HOVER);
			graphics.draw(hoveredPolygon);
		}
		plugin.setHoveredTile(hovered);
	}

	private static Map<Tile, Integer> hazardColours(ArenaEngine engine)
	{
		Map<Tile, Integer> colours = new HashMap<>();
		for (ActiveHazard active : engine.getHazards())
		{
			int colour = active.isActive(engine.getTick())
				? active.getHazard().getActiveArgb()
				: active.getHazard().getWarningArgb();
			for (Tile tile : active.getHazard().getTiles())
			{
				colours.put(tile, colour);
			}
		}
		return colours;
	}

	private void renderActor(Graphics2D graphics, ArenaSession session, ActorVisual visual, double progress)
	{
		Entity entity = visual.getEntity();
		ArenaView view = session.getView();
		if (entity.isDead())
		{
			return;
		}

		if (config.showTrueTiles())
		{
			LocalPoint trueTile = view.toLocal(client, entity.getTile(), entity.getSize());
			Polygon polygon = trueTile == null ? null : Perspective.getCanvasTileAreaPoly(client, trueTile, entity.getSize());
			if (polygon != null)
			{
				graphics.setColor(new Color(entity.getVisual().getMarkerArgb(), true));
				graphics.setStroke(THICK);
				graphics.draw(polygon);
			}
		}

		double[] at = ArenaView.interpolate(entity, progress);
		LocalPoint local = view.toLocal(client, at[0], at[1], entity.getSize());
		if (local == null)
		{
			return;
		}

		Point head = Perspective.localToCanvas(client, local, view.plane(), visual.getHeight() + 25);
		if (head != null)
		{
			int width = 30 * entity.getSize();
			int left = head.getX() - width / 2;
			int filled = (int) Math.ceil((double) width * entity.getHitpoints() / entity.getMaxHitpoints());
			graphics.setColor(HEALTH_RED);
			graphics.fillRect(left, head.getY(), width, HEALTH_BAR_HEIGHT);
			graphics.setColor(HEALTH_GREEN);
			graphics.fillRect(left, head.getY(), filled, HEALTH_BAR_HEIGHT);

			if (entity instanceof PlayerEntity)
			{
				BufferedImage icon = plugin.headIcon(((PlayerEntity) entity).getPrayer());
				if (icon != null)
				{
					graphics.drawImage(icon, head.getX() - icon.getWidth() / 2, head.getY() - icon.getHeight() - 3, null);
				}
			}
		}

		Point middle = Perspective.localToCanvas(client, local, view.plane(), visual.getHeight() / 2);
		if (middle != null)
		{
			List<Hitsplat> hitsplats = entity.getHitsplats();
			for (int i = 0; i < hitsplats.size(); i++)
			{
				int[] offset = SPLAT_OFFSETS[i % SPLAT_OFFSETS.length];
				renderHitsplat(graphics, hitsplats.get(i), middle.getX() + offset[0], middle.getY() + offset[1]);
			}
		}
	}

	private void renderHitsplat(Graphics2D graphics, Hitsplat hitsplat, int x, int y)
	{
		Color colour;
		switch (hitsplat.getKind())
		{
			case DAMAGE:
				colour = SPLAT_DAMAGE;
				break;
			case HEAL:
				colour = SPLAT_HEAL;
				break;
			default:
				colour = SPLAT_BLOCK;
				break;
		}
		graphics.setColor(colour);
		graphics.fillOval(x - 12, y - 10, 24, 20);
		graphics.setColor(Color.BLACK);
		graphics.setStroke(THIN);
		graphics.drawOval(x - 12, y - 10, 24, 20);

		String text = Integer.toString(hitsplat.getAmount());
		graphics.setFont(FontManager.getRunescapeSmallFont());
		FontMetrics metrics = graphics.getFontMetrics();
		int textX = x - metrics.stringWidth(text) / 2;
		int textY = y + metrics.getAscent() / 2 - 1;
		graphics.setColor(Color.BLACK);
		graphics.drawString(text, textX + 1, textY + 1);
		graphics.setColor(Color.WHITE);
		graphics.drawString(text, textX, textY);
	}

	private void renderProjectile(Graphics2D graphics, ArenaSession session, Projectile projectile, double progress)
	{
		int tick = session.getEngine().getTick();
		double duration = projectile.getLandTick() - projectile.getLaunchTick();
		double flown = duration <= 0 ? 1 : (tick - projectile.getLaunchTick() + progress) / duration;
		if (flown < 0 || flown >= 1)
		{
			return;
		}
		double[] from = ArenaView.centre(projectile.getSource(), progress);
		double[] to = ArenaView.centre(projectile.getTarget(), progress);
		LocalPoint local = session.getView().toLocal(client,
			from[0] + (to[0] - from[0]) * flown,
			from[1] + (to[1] - from[1]) * flown,
			1);
		if (local == null)
		{
			return;
		}
		double fromHeight = heightOf(session, projectile.getSource()) * 0.6;
		double toHeight = heightOf(session, projectile.getTarget()) * 0.5;
		int height = (int) (fromHeight + (toHeight - fromHeight) * flown);
		Point point = Perspective.localToCanvas(client, local, session.getView().plane(), height);
		if (point == null)
		{
			return;
		}
		graphics.setColor(new Color(projectile.getArgb(), true));
		graphics.fillOval(point.getX() - 6, point.getY() - 6, 12, 12);
		graphics.setColor(Color.BLACK);
		graphics.setStroke(THIN);
		graphics.drawOval(point.getX() - 6, point.getY() - 6, 12, 12);
	}

	private static int heightOf(ArenaSession session, Entity entity)
	{
		ActorVisual visual = session.getVisuals().get(entity);
		return visual == null ? 180 : visual.getHeight();
	}
}
