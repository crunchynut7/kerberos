package com.bossarena;

import com.bossarena.ArenaScenery.Projector;
import com.bossarena.encounters.KerberosEncounter;
import com.bossarena.encounters.KerberosEncounter.Attack;
import com.bossarena.engine.Pose;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Polygon;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Draws Kerberos: a three-headed hound built from flat shapes standing at different depths, so he
 * holds together as the camera moves. Each head is marked with the colour of the style it uses,
 * and the heads and front paws act out the encounter's poses.
 *
 * <p>Shapes are described in the hound's own space, in scene units from the middle of his
 * footprint: across (east is positive), up, and forward (towards the player, south).
 */
final class KerberosArt
{
	/** How tall the figure stands with a head reared, for placing the health bar above it. */
	static final int HEIGHT = 640;

	private static final int FUR = 0x2B2227;
	private static final int FUR_LIGHT = 0x40343A;
	private static final int FUR_DARK = 0x1C1619;
	private static final int CLAW = 0xD9D2C2;
	private static final int TOOTH = 0xF3EEE0;
	private static final int MOUTH = 0x3A0D10;
	private static final int SLAM_COLOUR = 0xFB8C00;
	private static final Color OUTLINE = new Color(12, 9, 10);
	private static final BasicStroke LINE = new BasicStroke(2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND);

	/** The heads from west to east, matching the lanes. */
	private static final Attack[] HEADS = {Attack.MELEE, Attack.MAGIC, Attack.RANGED};
	private static final float[] HEAD_ACROSS = {-158, 0, 158};
	private static final float[] HEAD_UP = {325, 405, 325};
	private static final float[] HEAD_FORWARD = {150, 185, 150};
	private static final float PAW_ACROSS = 125;
	private static final float PAW_FORWARD = 150;

	private final Graphics2D graphics;
	private final Projector projector;
	private final int originX;
	private final int originY;
	/** Shapes waiting to be drawn, each with how far forward it stands. */
	private final List<Layer> layers = new ArrayList<>();

	private KerberosArt(Graphics2D graphics, Projector projector, int originX, int originY)
	{
		this.graphics = graphics;
		this.projector = projector;
		this.originX = originX;
		this.originY = originY;
	}

	/**
	 * @param x           the middle of his footprint, scene units east
	 * @param y           the middle of his footprint, scene units north
	 * @param cameraSouth whether the camera is on the player's side of him
	 * @param heads       the pose his heads are in, or null
	 * @param paws        the pose his front paws are in, or null
	 * @param time        the tick, plus the fraction of the way to the next one
	 */
	static void draw(Graphics2D graphics, Projector projector, int x, int y, boolean cameraSouth,
		Pose heads, Pose paws, double time)
	{
		KerberosArt art = new KerberosArt(graphics, projector, x, y);
		art.build(heads, paws, time);
		// Paint the shapes furthest from the camera first.
		art.layers.sort(Comparator.comparingDouble(layer -> cameraSouth ? layer.forward : -layer.forward));
		for (Layer layer : art.layers)
		{
			layer.paint.run();
		}
	}

	private void build(Pose heads, Pose paws, double time)
	{
		float breath = (float) Math.sin(time * 1.6) * 5;

		// Tail, haunches and hind legs.
		layer(-170, () -> shape(FUR_DARK, -170,
			-20, 250, -70, 330, -40, 420, 10, 455, 0, 400, -25, 340, 20, 250));
		layer(-120, () ->
		{
			ellipse(FUR_DARK, -120, -105, 190, 80, 105);
			ellipse(FUR_DARK, -120, 105, 190, 80, 105);
			leg(FUR_DARK, -135, -120, 0, 0);
			leg(FUR_DARK, 135, -120, 0, 0);
		});

		// Body, with a ridge of fur along the back.
		layer(0, () ->
		{
			shape(FUR_DARK, 0,
				-150, 320, -120, 380, -90, 335, -55, 400, -25, 345, 0, 410, 25, 345, 55, 400, 90, 335, 120, 380, 150, 320);
			ellipse(FUR, 0, 0, 225 + breath, 195, 125);
		});

		// Front legs. The paw on the slammed side acts out scrapes and slams.
		for (int side = -1; side <= 1; side += 2)
		{
			final int s = side;
			PawMotion motion = pawMotion(paws, side, time);
			layer(PAW_FORWARD + motion.forward, () ->
			{
				leg(FUR, s * PAW_ACROSS, PAW_FORWARD + motion.forward, motion.lift, motion.strength);
			});
			if (motion.shock > 0)
			{
				layer(-1000, () -> shockwave(s, motion.shock));
			}
			if (motion.scratch > 0)
			{
				layer(PAW_FORWARD + 40, () -> scratches(s, motion.scratch));
			}
		}

		// Chest and mane.
		layer(95, () -> ellipse(FUR_LIGHT, 95, 0, 235 + breath, 165, 100));

		for (int i = 0; i < HEADS.length; i++)
		{
			head(i, headMotion(heads, HEADS[i], time, i));
		}
	}

	// ---- Heads ----

	private static final class HeadMotion
	{
		private float lift;
		private float forward;
		private float mouth;
		private float glow = 0.4f;
		private float aura;
	}

	private static HeadMotion headMotion(Pose pose, Attack head, double time, int index)
	{
		HeadMotion motion = new HeadMotion();
		motion.lift = (float) Math.sin(time * 1.3 + index * 2.1) * 7;
		double phase = pose == null ? -1 : pose.phase(time);
		if (phase < 0)
		{
			return motion;
		}
		// Up quickly, hold, then back down.
		float swell = (float) (Math.min(1, phase / 0.2) * Math.min(1, (1 - phase) / 0.25));
		if (pose.getName().equals(KerberosEncounter.REAR + head.name()))
		{
			motion.lift += 125 * swell;
			motion.forward = -35 * swell;
			motion.mouth = swell;
			motion.glow = 0.4f + 0.6f * swell;
			motion.aura = swell;
		}
		else if (pose.getName().equals(KerberosEncounter.SPIT + head.name()))
		{
			motion.lift -= 55 * swell;
			motion.forward = 95 * swell;
			motion.mouth = swell;
			motion.glow = 1;
		}
		return motion;
	}

	private void head(int index, HeadMotion motion)
	{
		int colour = HEADS[index].getArgb() & 0xFFFFFF;
		float across = HEAD_ACROSS[index];
		float up = HEAD_UP[index] + motion.lift;
		float forward = HEAD_FORWARD[index] + motion.forward;
		float lean = Math.signum(across) * 10;

		// The neck runs from the chest out to the back of the skull.
		layer(forward - 30, () -> shape3(FUR,
			across * 0.45f - 55, 235, 95,
			across * 0.45f + 55, 235, 95,
			across + 42, up - 20, forward - 20,
			across - 42, up - 20, forward - 20));

		layer(forward, () ->
		{
			if (motion.aura > 0)
			{
				for (int ring = 0; ring < 3; ring++)
				{
					float size = 95 + ring * 22;
					int alpha = Math.round(motion.aura * (110 - ring * 30));
					graphics.setColor(new Color((alpha << 24) | colour, true));
					graphics.fill(ellipsePolygon(forward - 5, across, up + 5, size, size));
				}
			}
			// Ears, then the skull over them.
			shape(FUR_DARK, forward, across - 50, up + 38, across - 70 - lean, up + 108, across - 18, up + 58);
			shape(FUR_DARK, forward, across + 50, up + 38, across + 70 - lean, up + 108, across + 18, up + 58);
			shape(FUR, forward,
				across - 52, up + 42, across - 22, up + 62, across + 22, up + 62, across + 52, up + 42,
				across + 62, up, across + 42, up - 40, across - 42, up - 40, across - 62, up);
			// A gem on the brow in the head's colour, so each head can be told apart at rest.
			fill(colour, 255, forward + 2, across, up + 52, across + 13, up + 36, across, up + 20, across - 13, up + 36);
		});

		layer(forward + 30, () ->
		{
			float jaw = 34 * motion.mouth;
			float snout = forward + 30;
			if (motion.mouth > 0.05f)
			{
				// The open mouth, lit from inside in the head's colour.
				shape(MOUTH, snout, across - 24, up - 52, across + 24, up - 52, across + 20, up - 66 - jaw, across - 20, up - 66 - jaw);
				fill(colour, Math.round(200 * motion.mouth), snout,
					across - 15, up - 58, across + 15, up - 58, across + 12, up - 64 - jaw, across - 12, up - 64 - jaw);
			}
			shape(FUR_LIGHT, snout, across - 31, up + 6, across + 31, up + 6, across + 25, up - 56, across - 25, up - 56);
			shape(FUR_DARK, snout, across - 11, up - 44, across + 11, up - 44, across, up - 57);
			// Lower jaw and fangs.
			shape(FUR, snout, across - 22, up - 60 - jaw, across + 22, up - 60 - jaw, across + 16, up - 76 - jaw, across - 16, up - 76 - jaw);
			for (int side = -1; side <= 1; side += 2)
			{
				fill(TOOTH, 255, snout + 1, across + side * 22, up - 56, across + side * 12, up - 56, across + side * 17, up - 72);
			}

			// Eyes, with a soft glow that brightens when the head is active.
			for (int side = -1; side <= 1; side += 2)
			{
				float eye = across + side * 30;
				graphics.setColor(new Color((Math.round(motion.glow * 120) << 24) | colour, true));
				graphics.fill(ellipsePolygon(snout + 1, eye, up + 20, 20, 15));
				fill(ArenaScenery.mix(colour, 0xFFFFFF, 0.35f + 0.5f * (motion.glow - 0.4f)), 255, snout + 2,
					eye - side * 13, up + 14, eye + side * 12, up + 28, eye + side * 14, up + 18, eye - side * 6, up + 10);
			}
		});
	}

	// ---- Paws ----

	private static final class PawMotion
	{
		private float lift;
		private float forward;
		/** How brightly the paw glows, from 0 to 1. */
		private float strength;
		/** How far the shockwave has spread, from 0 (none) to 1. */
		private float shock;
		/** How visible the scratch marks are, from 0 to 1. */
		private float scratch;
	}

	/**
	 * @param side -1 for the west paw, 1 for the east paw
	 */
	private static PawMotion pawMotion(Pose pose, int side, double time)
	{
		PawMotion motion = new PawMotion();
		double phase = pose == null ? -1 : pose.phase(time);
		if (phase < 0)
		{
			return motion;
		}
		Attack mine = side < 0 ? Attack.SLAM_LEFT : Attack.SLAM_RIGHT;
		if (pose.getName().equals(KerberosEncounter.SCRAPE + mine.name()))
		{
			// Two drags of the paw back across the floor.
			double drag = (phase * 2) % 1;
			motion.forward = (float) (70 - 120 * drag);
			motion.lift = (float) (Math.sin(Math.PI * drag) * 14);
			motion.strength = 0.6f;
			motion.scratch = (float) Math.min(1, phase * 3) * (float) Math.min(1, (1 - phase) * 4);
		}
		else if (pose.getName().equals(KerberosEncounter.SLAM + mine.name()))
		{
			// The first half winds up; the paw comes down at the halfway point, as the slam lands.
			if (phase < 0.5)
			{
				double rise = phase / 0.5;
				motion.lift = (float) (190 * (1 - (1 - rise) * (1 - rise)));
				motion.strength = (float) rise;
			}
			else
			{
				double after = (phase - 0.5) / 0.5;
				motion.lift = (float) (190 * Math.max(0, 1 - after / 0.12));
				motion.strength = (float) (1 - after);
				motion.shock = (float) Math.max(0.02, after);
			}
		}
		return motion;
	}

	private void leg(int colour, float across, float forward, float lift, float strength)
	{
		float foot = lift;
		// The leg bends towards the body as the paw lifts.
		shape3(colour,
			across - 36, 215, forward - 30,
			across + 36, 215, forward - 30,
			across + 30, foot + 28, forward,
			across - 30, foot + 28, forward);
		int paw = strength > 0 ? ArenaScenery.mix(colour, SLAM_COLOUR, 0.55f * strength) : colour;
		outline(fillShape(paw, 255, ellipsePolygon(forward + 6, across, foot + 22, 50, 24)));
		for (int claw = -1; claw <= 1; claw++)
		{
			fill(CLAW, 255, forward + 8,
				across + claw * 26 - 8, foot + 14, across + claw * 26 + 8, foot + 14, across + claw * 28, foot - 6);
		}
	}

	/** A ring spreading across the floor on the slammed side. */
	private void shockwave(int side, float spread)
	{
		float fade = 1 - spread;
		for (int ring = 0; ring < 2; ring++)
		{
			float radius = 90 + (spread * 520) - ring * 60;
			if (radius <= 0)
			{
				continue;
			}
			Polygon circle = new Polygon();
			for (int i = 0; i < 28; i++)
			{
				double angle = Math.PI * 2 * i / 28;
				java.awt.Point point = point(side * PAW_ACROSS + (float) Math.cos(angle) * radius, 4,
					PAW_FORWARD + (float) Math.sin(angle) * radius);
				if (point == null)
				{
					return;
				}
				circle.addPoint(point.x, point.y);
			}
			graphics.setStroke(new BasicStroke(7f - ring * 3));
			graphics.setColor(new Color((Math.round(fade * 220) << 24) | SLAM_COLOUR, true));
			graphics.draw(circle);
		}
	}

	/** Claw marks gouged into the floor in front of a scraping paw. */
	private void scratches(int side, float visible)
	{
		graphics.setStroke(new BasicStroke(3f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
		graphics.setColor(new Color((Math.round(visible * 230) << 24) | SLAM_COLOUR, true));
		for (int claw = -1; claw <= 1; claw++)
		{
			java.awt.Point from = point(side * PAW_ACROSS + claw * 26, 3, PAW_FORWARD + 120);
			java.awt.Point to = point(side * PAW_ACROSS + claw * 26 + side * 10, 3, PAW_FORWARD + 10);
			if (from != null && to != null)
			{
				graphics.drawLine(from.x, from.y, to.x, to.y);
			}
		}
	}

	// ---- Drawing in the hound's own space ----

	private static final class Layer
	{
		private final float forward;
		private final Runnable paint;

		private Layer(float forward, Runnable paint)
		{
			this.forward = forward;
			this.paint = paint;
		}
	}

	private void layer(float forward, Runnable paint)
	{
		layers.add(new Layer(forward, paint));
	}

	private java.awt.Point point(float across, float up, float forward)
	{
		return projector.project(originX + Math.round(across), originY - Math.round(forward), Math.round(up));
	}

	/** A filled, outlined shape standing at one depth. Points are given as across, up pairs. */
	private void shape(int colour, float forward, float... points)
	{
		outline(fill(colour, 255, forward, points));
	}

	/** A filled shape with no outline. Points are given as across, up pairs. */
	private Polygon fill(int colour, int alpha, float forward, float... points)
	{
		Polygon polygon = new Polygon();
		for (int i = 0; i < points.length; i += 2)
		{
			java.awt.Point point = point(points[i], points[i + 1], forward);
			if (point == null)
			{
				return null;
			}
			polygon.addPoint(point.x, point.y);
		}
		return fillShape(colour, alpha, polygon);
	}

	/** A filled, outlined shape whose corners stand at different depths. Points are across, up, forward triples. */
	private void shape3(int colour, float... points)
	{
		Polygon polygon = new Polygon();
		for (int i = 0; i < points.length; i += 3)
		{
			java.awt.Point point = point(points[i], points[i + 1], points[i + 2]);
			if (point == null)
			{
				return;
			}
			polygon.addPoint(point.x, point.y);
		}
		outline(fillShape(colour, 255, polygon));
	}

	private void ellipse(int colour, float forward, float across, float up, float radiusAcross, float radiusUp)
	{
		outline(fillShape(colour, 255, ellipsePolygon(forward, across, up, radiusAcross, radiusUp)));
	}

	private Polygon ellipsePolygon(float forward, float across, float up, float radiusAcross, float radiusUp)
	{
		Polygon polygon = new Polygon();
		for (int i = 0; i < 20; i++)
		{
			double angle = Math.PI * 2 * i / 20;
			java.awt.Point point = point(across + (float) Math.cos(angle) * radiusAcross,
				up + (float) Math.sin(angle) * radiusUp, forward);
			if (point == null)
			{
				return null;
			}
			polygon.addPoint(point.x, point.y);
		}
		return polygon;
	}

	private Polygon fillShape(int colour, int alpha, Polygon polygon)
	{
		if (polygon != null)
		{
			graphics.setColor(new Color((Math.max(0, Math.min(255, alpha)) << 24) | (colour & 0xFFFFFF), true));
			graphics.fill(polygon);
		}
		return polygon;
	}

	private void outline(Polygon polygon)
	{
		if (polygon != null)
		{
			graphics.setStroke(LINE);
			graphics.setColor(OUTLINE);
			graphics.draw(polygon);
		}
	}
}
