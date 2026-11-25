package com.bossarena;

import com.bossarena.engine.ArenaEngine;
import com.bossarena.engine.Boss;
import com.bossarena.engine.Prayer;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;
import javax.inject.Inject;
import net.runelite.client.ui.FontManager;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.components.ComponentConstants;

/**
 * The fight's status panel and its three clickable protection prayer buttons.
 */
class ArenaHudOverlay extends Overlay
{
	static final Prayer[] PRAYERS = {
		Prayer.PROTECT_FROM_MAGIC,
		Prayer.PROTECT_FROM_MISSILES,
		Prayer.PROTECT_FROM_MELEE,
	};
	private static final String[] LABELS = {"Mage", "Range", "Melee"};

	private static final int PADDING = 6;
	private static final int BUTTON = 40;
	private static final int WIDTH = Math.max(170, PADDING + PRAYERS.length * (BUTTON + PADDING));
	private static final Color BUTTON_OFF = new Color(0, 0, 0, 120);
	private static final Color BUTTON_ON = new Color(255, 255, 190, 130);

	private final BossArenaPlugin plugin;
	/** Button areas in canvas coordinates, read from the mouse thread. Replaced whole, never mutated. */
	private volatile Rectangle[] buttonBounds = new Rectangle[0];

	@Inject
	ArenaHudOverlay(BossArenaPlugin plugin)
	{
		this.plugin = plugin;
		setPosition(OverlayPosition.ABOVE_CHATBOX_RIGHT);
	}

	/** The prayer whose button is at a canvas point, or null. Safe to call from any thread. */
	Prayer prayerAt(java.awt.Point point)
	{
		Rectangle[] bounds = buttonBounds;
		for (int i = 0; i < bounds.length; i++)
		{
			if (bounds[i].contains(point))
			{
				return PRAYERS[i];
			}
		}
		return null;
	}

	@Override
	public Dimension render(Graphics2D graphics)
	{
		ArenaSession session = plugin.getSession();
		if (session == null)
		{
			buttonBounds = new Rectangle[0];
			return null;
		}
		ArenaEngine engine = session.getEngine();
		List<String> lines = statusLines(engine);

		graphics.setFont(FontManager.getRunescapeSmallFont());
		FontMetrics metrics = graphics.getFontMetrics();
		int lineHeight = metrics.getHeight();
		int height = PADDING + BUTTON + PADDING + lines.size() * lineHeight + PADDING;

		graphics.setColor(ComponentConstants.STANDARD_BACKGROUND_COLOR);
		graphics.fillRect(0, 0, WIDTH, height);

		Prayer active = engine.getDisplayedPrayer();
		Rectangle origin = getBounds();
		Rectangle[] bounds = new Rectangle[PRAYERS.length];
		for (int i = 0; i < PRAYERS.length; i++)
		{
			int x = PADDING + i * (BUTTON + PADDING);
			graphics.setColor(PRAYERS[i] == active ? BUTTON_ON : BUTTON_OFF);
			graphics.fillRect(x, PADDING, BUTTON, BUTTON);
			graphics.setColor(Color.GRAY);
			graphics.drawRect(x, PADDING, BUTTON, BUTTON);

			BufferedImage icon = plugin.prayerIcon(PRAYERS[i]);
			if (icon != null)
			{
				graphics.drawImage(icon, x + (BUTTON - icon.getWidth()) / 2, PADDING + (BUTTON - icon.getHeight()) / 2, null);
			}
			else
			{
				graphics.setColor(Color.WHITE);
				graphics.drawString(LABELS[i], x + (BUTTON - metrics.stringWidth(LABELS[i])) / 2,
					PADDING + (BUTTON + metrics.getAscent()) / 2);
			}
			bounds[i] = new Rectangle(origin.x + x, origin.y + PADDING, BUTTON, BUTTON);
		}
		buttonBounds = bounds;

		int y = PADDING + BUTTON + PADDING + metrics.getAscent();
		graphics.setColor(Color.WHITE);
		for (String line : lines)
		{
			graphics.drawString(line, PADDING, y);
			y += lineHeight;
		}
		return new Dimension(WIDTH, height);
	}

	private static List<String> statusLines(ArenaEngine engine)
	{
		List<String> lines = new ArrayList<>();
		lines.add("You: " + engine.getPlayer().getHitpoints() + " / " + engine.getPlayer().getMaxHitpoints());
		for (Boss boss : engine.getBosses())
		{
			lines.add(boss.getName() + ": " + boss.getHitpoints() + " / " + boss.getMaxHitpoints());
		}
		String status = engine.getEncounter().status();
		if (status != null)
		{
			lines.add(status);
		}
		lines.add("Tick " + engine.getTick() + (engine.getPlayer().isRunning() ? " - run" : " - walk"));
		if (engine.getState() == ArenaEngine.State.WON)
		{
			lines.add("Victory!");
		}
		else if (engine.getState() == ArenaEngine.State.LOST)
		{
			lines.add("You died.");
		}
		return lines;
	}
}
