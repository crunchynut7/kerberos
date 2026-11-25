package com.bossarena.engine;

import java.util.List;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import org.junit.Test;

public class ArenaEngineTest
{
	/** A 7x7 open room with a passive 2x2 boss in the north. Stats are set so every hit is exactly maxHit or 0. */
	private static class TestEncounter implements Encounter
	{
		private CombatStats stats = CombatStats.builder().maxHitpoints(50).accuracy(1).maxHit(0).build();
		private int bossHitpoints = 100;
		private Boss boss;

		@Override
		public String name()
		{
			return "Test";
		}

		@Override
		public Arena createArena()
		{
			return Arena.parse(
				".......",
				"..B....",
				".......",
				".......",
				".......",
				".......",
				"P......");
		}

		@Override
		public Tile playerStart(Arena arena)
		{
			return arena.marker('P');
		}

		@Override
		public CombatStats playerStats()
		{
			return stats;
		}

		@Override
		public void start(ArenaEngine engine)
		{
			boss = engine.spawnBoss("Dummy", 2, bossHitpoints, engine.getArena().marker('B'));
		}
	}

	private static void ticks(ArenaEngine engine, int count)
	{
		for (int i = 0; i < count; i++)
		{
			engine.tick();
		}
	}

	@Test
	public void arenaDrawingPutsFirstRowNorth()
	{
		Arena arena = Arena.parse(
			"#.B",
			"...",
			"P.#");
		assertEquals(new Tile(0, 0), arena.marker('P'));
		assertEquals(new Tile(2, 2), arena.marker('B'));
		assertTrue(arena.isBlocked(new Tile(0, 2)));
		assertTrue(arena.isBlocked(new Tile(2, 0)));
		assertFalse(arena.isBlocked(new Tile(1, 1)));
		assertTrue("outside the arena counts as blocked", arena.isBlocked(new Tile(3, 0)));
	}

	@Test
	public void pathGoesAroundWallsWithoutCuttingCorners()
	{
		Arena arena = Arena.parse(
			"...",
			".#.",
			"...");
		List<Tile> path = Pathfinder.find(new Tile(0, 0), t -> !arena.isBlocked(t), new Tile(2, 2)::equals, new Tile(2, 2));
		assertEquals(4, path.size());
		assertEquals(new Tile(2, 2), path.get(path.size() - 1));
		assertFalse(path.contains(new Tile(1, 1)));

		// (0,1) -> (1,2) would clip the wall's corner, so it has to take two steps.
		List<Tile> corner = Pathfinder.find(new Tile(0, 1), t -> !arena.isBlocked(t), new Tile(1, 2)::equals, new Tile(1, 2));
		assertEquals(2, corner.size());
	}

	@Test
	public void unreachableTargetWalksToClosestTile()
	{
		Arena arena = Arena.parse(
			"..#.",
			"..#.",
			"..#.");
		List<Tile> path = Pathfinder.find(new Tile(0, 0), t -> !arena.isBlocked(t), new Tile(3, 0)::equals, new Tile(3, 0));
		assertEquals(new Tile(1, 0), path.get(path.size() - 1));
	}

	@Test
	public void inputAppliesOnTheNextTickAndRunningCoversTwoTiles()
	{
		ArenaEngine engine = new ArenaEngine(new TestEncounter(), 1);
		engine.walkTo(new Tile(5, 0));
		assertEquals(new Tile(0, 0), engine.getPlayer().getTile());

		engine.tick();
		assertEquals(new Tile(2, 0), engine.getPlayer().getTile());
		assertEquals(2, engine.getPlayer().getSteps().size());
		assertEquals(new Tile(0, 0), engine.getPlayer().getPreviousTile());

		engine.setRunning(false);
		engine.tick();
		assertEquals(new Tile(3, 0), engine.getPlayer().getTile());

		ticks(engine, 5);
		assertEquals(new Tile(5, 0), engine.getPlayer().getTile());
		assertNull(engine.getPlayer().getDestination());
		assertTrue(engine.getPlayer().getSteps().isEmpty());
	}

	@Test
	public void playerCannotWalkThroughABoss()
	{
		TestEncounter encounter = new TestEncounter();
		ArenaEngine engine = new ArenaEngine(encounter, 1);
		engine.walkTo(new Tile(2, 5));
		ticks(engine, 10);
		assertFalse(encounter.boss.occupies(engine.getPlayer().getTile()));
		assertEquals(1, encounter.boss.distanceFrom(engine.getPlayer().getTile()));
	}

	@Test
	public void prayerCheckedOnHitRewardsALateSwitch()
	{
		TestEncounter encounter = new TestEncounter();
		ArenaEngine engine = new ArenaEngine(encounter, 1);
		engine.tick();
		engine.attackPlayer(encounter.boss, AttackStyle.MAGIC, 20, 20, 2, PrayerCheck.ON_HIT);

		engine.tick();
		assertEquals(50, engine.getPlayer().getHitpoints());
		engine.clickPrayer(Prayer.PROTECT_FROM_MAGIC);
		assertEquals(Prayer.PROTECT_FROM_MAGIC, engine.getDisplayedPrayer());

		engine.tick();
		assertEquals(50, engine.getPlayer().getHitpoints());
		assertEquals(Hitsplat.Kind.BLOCK, engine.getPlayer().getHitsplats().get(0).getKind());
	}

	@Test
	public void wrongOrMissingPrayerTakesFullDamage()
	{
		TestEncounter encounter = new TestEncounter();
		ArenaEngine engine = new ArenaEngine(encounter, 1);
		engine.clickPrayer(Prayer.PROTECT_FROM_MISSILES);
		engine.tick();
		engine.attackPlayer(encounter.boss, AttackStyle.MAGIC, 20, 20, 1, PrayerCheck.ON_HIT);
		engine.tick();
		assertEquals(30, engine.getPlayer().getHitpoints());
		assertEquals(20, engine.getPlayer().getHitsplats().get(0).getAmount());
	}

	@Test
	public void prayerCheckedOnLaunchIgnoresALateSwitch()
	{
		TestEncounter encounter = new TestEncounter();
		ArenaEngine engine = new ArenaEngine(encounter, 1);
		engine.tick();
		engine.attackPlayer(encounter.boss, AttackStyle.RANGED, 20, 20, 2, PrayerCheck.ON_LAUNCH);
		engine.clickPrayer(Prayer.PROTECT_FROM_MISSILES);
		ticks(engine, 2);
		assertEquals(30, engine.getPlayer().getHitpoints());
	}

	@Test
	public void clickingAnActivePrayerTurnsItOff()
	{
		ArenaEngine engine = new ArenaEngine(new TestEncounter(), 1);
		engine.clickPrayer(Prayer.PROTECT_FROM_MELEE);
		engine.tick();
		assertEquals(Prayer.PROTECT_FROM_MELEE, engine.getPlayer().getPrayer());
		engine.clickPrayer(Prayer.PROTECT_FROM_MELEE);
		engine.tick();
		assertEquals(Prayer.NONE, engine.getPlayer().getPrayer());
	}

	@Test
	public void hazardOnlyHurtsOnceActiveAndOnlyOnItsTiles()
	{
		ArenaEngine engine = new ArenaEngine(new TestEncounter(), 1);
		engine.tick();
		engine.addHazard(TileHazard.builder()
			.tile(new Tile(0, 0))
			.warningTicks(2)
			.activeTicks(1)
			.minDamage(10)
			.maxDamage(10)
			.build());

		engine.tick();
		assertEquals("still a warning", 50, engine.getPlayer().getHitpoints());
		engine.tick();
		assertEquals("active this tick", 40, engine.getPlayer().getHitpoints());
		engine.tick();
		assertEquals("expired", 40, engine.getPlayer().getHitpoints());
		assertTrue(engine.getHazards().isEmpty());
	}

	@Test
	public void steppingOffAHazardAvoidsIt()
	{
		ArenaEngine engine = new ArenaEngine(new TestEncounter(), 1);
		engine.tick();
		engine.addHazard(TileHazard.builder().tile(new Tile(0, 0)).warningTicks(2).minDamage(10).maxDamage(10).build());
		engine.walkTo(new Tile(1, 0));
		ticks(engine, 3);
		assertEquals(50, engine.getPlayer().getHitpoints());
	}

	@Test
	public void attackingWalksIntoRangeThenKillsTheBoss()
	{
		TestEncounter encounter = new TestEncounter();
		encounter.stats = CombatStats.builder().accuracy(1).maxHit(1000).attackSpeed(4).build();
		encounter.bossHitpoints = 1;
		ArenaEngine engine = new ArenaEngine(encounter, 1);
		engine.attack(encounter.boss);

		ticks(engine, 2);
		assertTrue(encounter.boss.isCardinallyAdjacent(engine.getPlayer().getTile()));
		assertEquals("the hit has not landed yet", ArenaEngine.State.RUNNING, engine.getState());

		engine.tick();
		assertTrue(encounter.boss.isDead());
		assertEquals(Hitsplat.Kind.DAMAGE, encounter.boss.getHitsplats().get(0).getKind());
		assertEquals(ArenaEngine.State.WON, engine.getState());
	}

	@Test
	public void playerAttacksOncePerAttackSpeed()
	{
		TestEncounter encounter = new TestEncounter();
		encounter.stats = CombatStats.builder().accuracy(1).maxHit(1).attackSpeed(4).build();
		ArenaEngine engine = new ArenaEngine(encounter, 1);
		engine.attack(encounter.boss);
		int splats = 0;
		for (int i = 0; i < 2 + 4 * 5; i++)
		{
			engine.tick();
			for (Hitsplat hitsplat : encounter.boss.getHitsplats())
			{
				if (hitsplat.getTick() == engine.getTick())
				{
					splats++;
				}
			}
		}
		assertEquals(5, splats);
	}

	@Test
	public void lethalDamageEndsTheFight()
	{
		TestEncounter encounter = new TestEncounter();
		ArenaEngine engine = new ArenaEngine(encounter, 1);
		engine.tick();
		engine.attackPlayer(encounter.boss, AttackStyle.TYPELESS, 99, 99, 1, PrayerCheck.ON_HIT);
		engine.tick();
		assertTrue(engine.getPlayer().isDead());
		assertEquals(ArenaEngine.State.LOST, engine.getState());

		engine.walkTo(new Tile(3, 3));
		ticks(engine, 3);
		assertEquals("no moving after death", new Tile(0, 0), engine.getPlayer().getTile());
	}
}
