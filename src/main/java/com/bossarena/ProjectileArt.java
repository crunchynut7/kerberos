package com.bossarena;

import com.bossarena.engine.AttackStyle;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Polygon;
import java.awt.geom.AffineTransform;
import java.awt.geom.Arc2D;
import java.awt.geom.Ellipse2D;

/**
 * Draws projectiles: a spinning bladed disc for melee, a glowing orb for magic and a bolt for
 * ranged. Each is drawn at a point on screen, sized to match how far away it is.
 */
final class ProjectileArt
{
	private static final int TRAIL_LENGTH = 5;

	private ProjectileArt()
	{
	}

	/**
	 * @param style      which design to draw; null draws a plain orb
	 * @param argb       the projectile's colour
	 * @param x          where it is on screen
	 * @param y          where it is on screen
	 * @param headingX   the direction it is travelling on screen, any length
	 * @param headingY   the direction it is travelling on screen, any length
	 * @param scale      screen pixels per scene unit at the projectile
	 * @param time       the tick, plus the fraction of the way to the next one; drives the animation
	 */
	static void draw(Graphics2D graphics, AttackStyle style, int argb, double x, double y,
		double headingX, double headingY, double scale, double time)
	{
		int rgb = argb & 0xFFFFFF;
		double length = Math.hypot(headingX, headingY);
		double dirX = length < 0.001 ? 0 : headingX / length;
		double dirY = length < 0.001 ? 1 : headingY / length;
		scale = Math.max(0.12, scale);

		if (style == AttackStyle.MELEE)
		{
			trail(graphics, rgb, x, y, dirX, dirY, 42 * scale, 34 * scale);
			disc(graphics, rgb, x, y, 52 * scale, time);
		}
		else if (style == AttackStyle.RANGED)
		{
			bolt(graphics, rgb, x, y, dirX, dirY, scale);
		}
		else
		{
			trail(graphics, rgb, x, y, dirX, dirY, 34 * scale, 32 * scale);
			orb(graphics, rgb, x, y, (style == AttackStyle.MAGIC ? 42 : 26) * scale, time, style == AttackStyle.MAGIC);
		}
	}

	/** Fading copies left behind the projectile. */
	private static void trail(Graphics2D graphics, int rgb, double x, double y, double dirX, double dirY,
		double radius, double spacing)
	{
		for (int i = TRAIL_LENGTH; i >= 1; i--)
		{
			double r = radius * (1 - i / (TRAIL_LENGTH + 1.5));
			int alpha = 110 - i * 18;
			graphics.setColor(new Color((alpha << 24) | rgb, true));
			graphics.fill(new Ellipse2D.Double(x - dirX * spacing * i - r, y - dirY * spacing * i - r, r * 2, r * 2));
		}
	}

	/** A disc of energy with three hooked blades, spinning. */
	private static void disc(Graphics2D graphics, int rgb, double x, double y, double radius, double time)
	{
		int bright = ArenaScenery.mix(rgb, 0xFFFFFF, 0.55f);
		graphics.setColor(new Color((70 << 24) | rgb, true));
		graphics.fill(new Ellipse2D.Double(x - radius * 1.5, y - radius * 1.5, radius * 3, radius * 3));

		AffineTransform saved = graphics.getTransform();
		graphics.rotate(time * Math.PI * 2 * 1.7, x, y);
		for (int blade = 0; blade < 3; blade++)
		{
			graphics.rotate(Math.PI * 2 / 3, x, y);
			Polygon hook = new Polygon();
			hook.addPoint((int) Math.round(x + radius * 0.55), (int) Math.round(y - radius * 0.2));
			hook.addPoint((int) Math.round(x + radius * 1.35), (int) Math.round(y - radius * 0.55));
			hook.addPoint((int) Math.round(x + radius * 0.95), (int) Math.round(y + radius * 0.35));
			graphics.setColor(new Color(bright));
			graphics.fill(hook);
			graphics.setColor(new Color(ArenaScenery.shade(rgb, 0.45f)));
			graphics.setStroke(new BasicStroke(1.5f));
			graphics.draw(hook);
		}
		graphics.setTransform(saved);

		graphics.setColor(new Color(ArenaScenery.shade(rgb, 0.6f)));
		graphics.fill(new Ellipse2D.Double(x - radius, y - radius, radius * 2, radius * 2));
		graphics.setColor(new Color(rgb));
		graphics.fill(new Ellipse2D.Double(x - radius * 0.8, y - radius * 0.8, radius * 1.6, radius * 1.6));
		graphics.setStroke(new BasicStroke((float) Math.max(1.5, radius * 0.14)));
		graphics.setColor(new Color(bright));
		graphics.draw(new Ellipse2D.Double(x - radius, y - radius, radius * 2, radius * 2));

		// A streak across the face, turning with the blades, so the spin reads at a glance.
		saved = graphics.getTransform();
		graphics.rotate(time * Math.PI * 2 * 1.7, x, y);
		graphics.setColor(new Color(255, 255, 255, 200));
		graphics.draw(new Arc2D.Double(x - radius * 0.5, y - radius * 0.5, radius, radius, 20, 110, Arc2D.OPEN));
		graphics.draw(new Arc2D.Double(x - radius * 0.5, y - radius * 0.5, radius, radius, 200, 110, Arc2D.OPEN));
		graphics.setTransform(saved);
	}

	/** A glowing ball, with sparks circling it when it is magic. */
	private static void orb(Graphics2D graphics, int rgb, double x, double y, double radius, double time, boolean sparks)
	{
		for (int halo = 3; halo >= 1; halo--)
		{
			double r = radius * (1 + halo * 0.45);
			graphics.setColor(new Color(((30 + (3 - halo) * 25) << 24) | rgb, true));
			graphics.fill(new Ellipse2D.Double(x - r, y - r, r * 2, r * 2));
		}
		graphics.setColor(new Color(rgb));
		graphics.fill(new Ellipse2D.Double(x - radius, y - radius, radius * 2, radius * 2));
		graphics.setColor(new Color(ArenaScenery.mix(rgb, 0xFFFFFF, 0.75f)));
		graphics.fill(new Ellipse2D.Double(x - radius * 0.55, y - radius * 0.6, radius * 1.1, radius * 1.1));
		if (!sparks)
		{
			return;
		}
		graphics.setColor(Color.WHITE);
		for (int spark = 0; spark < 3; spark++)
		{
			double angle = time * Math.PI * 2 * 1.3 + spark * Math.PI * 2 / 3;
			double sx = x + Math.cos(angle) * radius * 1.7;
			double sy = y + Math.sin(angle) * radius * 0.9;
			double size = Math.max(2, radius * 0.22);
			Polygon diamond = new Polygon();
			diamond.addPoint((int) Math.round(sx), (int) Math.round(sy - size * 1.6));
			diamond.addPoint((int) Math.round(sx + size), (int) Math.round(sy));
			diamond.addPoint((int) Math.round(sx), (int) Math.round(sy + size * 1.6));
			diamond.addPoint((int) Math.round(sx - size), (int) Math.round(sy));
			graphics.fill(diamond);
		}
	}

	/** A bolt pointing the way it is flying, with a streak behind it. */
	private static void bolt(Graphics2D graphics, int rgb, double x, double y, double dirX, double dirY, double scale)
	{
		double shaft = 130 * scale;
		double sideX = -dirY;
		double sideY = dirX;
		double tailX = x - dirX * shaft;
		double tailY = y - dirY * shaft;

		for (int streak = 3; streak >= 1; streak--)
		{
			graphics.setStroke(new BasicStroke((float) Math.max(3, 14 * scale * streak), BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
			graphics.setColor(new Color(((28 + (3 - streak) * 22) << 24) | rgb, true));
			graphics.drawLine((int) Math.round(tailX - dirX * shaft * 0.6), (int) Math.round(tailY - dirY * shaft * 0.6),
				(int) Math.round(x), (int) Math.round(y));
		}

		graphics.setStroke(new BasicStroke((float) Math.max(4, 13 * scale), BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
		graphics.setColor(new Color(ArenaScenery.shade(rgb, 0.5f)));
		graphics.drawLine((int) Math.round(tailX), (int) Math.round(tailY), (int) Math.round(x), (int) Math.round(y));
		graphics.setStroke(new BasicStroke((float) Math.max(2, 6 * scale), BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
		graphics.setColor(new Color(ArenaScenery.mix(rgb, 0xFFFFFF, 0.6f)));
		graphics.drawLine((int) Math.round(tailX), (int) Math.round(tailY), (int) Math.round(x), (int) Math.round(y));

		// Fletching.
		double feather = 34 * scale;
		for (int side = -1; side <= 1; side += 2)
		{
			graphics.drawLine((int) Math.round(tailX), (int) Math.round(tailY),
				(int) Math.round(tailX - dirX * feather + sideX * feather * side),
				(int) Math.round(tailY - dirY * feather + sideY * feather * side));
		}

		// Head.
		double head = 46 * scale;
		Polygon tip = new Polygon();
		tip.addPoint((int) Math.round(x + dirX * head), (int) Math.round(y + dirY * head));
		tip.addPoint((int) Math.round(x - dirX * head * 0.4 + sideX * head * 0.6), (int) Math.round(y - dirY * head * 0.4 + sideY * head * 0.6));
		tip.addPoint((int) Math.round(x - dirX * head * 0.4 - sideX * head * 0.6), (int) Math.round(y - dirY * head * 0.4 - sideY * head * 0.6));
		graphics.setColor(new Color(ArenaScenery.mix(rgb, 0xFFFFFF, 0.8f)));
		graphics.fill(tip);
		graphics.setStroke(new BasicStroke(1.5f));
		graphics.setColor(new Color(ArenaScenery.shade(rgb, 0.4f)));
		graphics.draw(tip);
	}
}
