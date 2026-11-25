package com.bossarena.encounters;

import com.bossarena.encounters.KerberosEncounter.Attack;
import com.bossarena.engine.ArenaEngine;
import com.bossarena.engine.Tile;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import org.junit.Test;

public class KerberosEncounterTest
{
	private static final int LEFT_SAFE_COLUMN = 2;
	private static final int RIGHT_SAFE_COLUMN = 4;

	/** Answers the attack landing next tick, the way a player with perfect memory would. */
	private static void playPerfectly(ArenaEngine engine, KerberosEncounter encounter)
	{
		Attack next = encounter.landingOn(engine.getTick() + 1);
		if (next == null)
		{
			return;
		}
		if (next.isSlam())
		{
			Tile at = engine.getPlayer().getTile();
			if (next.hitsColumn(at.getX()))
			{
				engine.walkTo(new Tile(next == Attack.SLAM_LEFT ? RIGHT_SAFE_COLUMN : LEFT_SAFE_COLUMN, at.getY()));
			}
		}
		else if (engine.getDisplayedPrayer() != next.getPrayer())
		{
			engine.clickPrayer(next.getPrayer());
		}
	}

	@Test
	public void perfectPlayTakesNoDamage()
	{
		for (long seed = 1; seed <= 25; seed++)
		{
			KerberosEncounter encounter = new KerberosEncounter();
			ArenaEngine engine = new ArenaEngine(encounter, seed);
			for (int i = 0; i < 600; i++)
			{
				playPerfectly(engine, encounter);
				engine.tick();
			}
			assertEquals("seed " + seed, 99, engine.getPlayer().getHitpoints());
			assertEquals("seed " + seed, ArenaEngine.State.RUNNING, engine.getState());
			assertTrue("the fight should be many sequences in", encounter.getRound() > 10);
		}
	}

	@Test
	public void standingStillWithoutPrayerIsFatal()
	{
		KerberosEncounter encounter = new KerberosEncounter();
		ArenaEngine engine = new ArenaEngine(encounter, 7);
		for (int i = 0; i < 300 && engine.getState() == ArenaEngine.State.RUNNING; i++)
		{
			engine.tick();
		}
		assertEquals(ArenaEngine.State.LOST, engine.getState());
	}

	@Test
	public void everyMistakeCostsExactlyOneHit()
	{
		KerberosEncounter encounter = new KerberosEncounter();
		ArenaEngine engine = new ArenaEngine(encounter, 3);
		// Play perfectly but ignore the very first attack.
		boolean skipped = false;
		for (int i = 0; i < 60; i++)
		{
			if (!skipped && encounter.landingOn(engine.getTick() + 1) != null)
			{
				skipped = true;
				Attack ignored = encounter.landingOn(engine.getTick() + 1);
				if (ignored.isSlam() && !ignored.hitsColumn(engine.getPlayer().getTile().getX()))
				{
					// Already safe from this slam by luck; step into it so it counts as a mistake.
					engine.walkTo(new Tile(3, engine.getPlayer().getTile().getY()));
				}
			}
			else
			{
				playPerfectly(engine, encounter);
			}
			engine.tick();
		}
		assertTrue(skipped);
		assertEquals(99 - KerberosEncounter.HEAD_DAMAGE, engine.getPlayer().getHitpoints());
	}

	@Test
	public void slamsSplitTheRoomAndShareTheMiddleColumn()
	{
		for (int x = 0; x < 7; x++)
		{
			assertEquals(x <= 3, Attack.SLAM_LEFT.hitsColumn(x));
			assertEquals(x >= 3, Attack.SLAM_RIGHT.hitsColumn(x));
			assertFalse(Attack.MELEE.hitsColumn(x));
		}
	}

	@Test
	public void sequencesStartShortAndRampToEight()
	{
		assertEquals(2, KerberosEncounter.shortestSequence(1));
		assertEquals(3, KerberosEncounter.longestSequence(1));
		assertEquals(8, KerberosEncounter.longestSequence(6));
		assertEquals(8, KerberosEncounter.longestSequence(50));
		assertEquals(6, KerberosEncounter.shortestSequence(50));

		KerberosEncounter encounter = new KerberosEncounter();
		ArenaEngine engine = new ArenaEngine(encounter, 11);
		int lastRound = 0;
		for (int i = 0; i < 600; i++)
		{
			playPerfectly(engine, encounter);
			engine.tick();
			if (encounter.getRound() != lastRound)
			{
				lastRound = encounter.getRound();
				int length = encounter.getSequence().size();
				assertTrue(length >= KerberosEncounter.shortestSequence(lastRound));
				assertTrue(length <= KerberosEncounter.longestSequence(lastRound));
			}
		}
	}
}
