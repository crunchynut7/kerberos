package com.bossarena.encounters;

import com.bossarena.engine.Arena;
import com.bossarena.engine.ArenaEngine;
import com.bossarena.engine.AttackStyle;
import com.bossarena.engine.Boss;
import com.bossarena.engine.CombatStats;
import com.bossarena.engine.Encounter;
import com.bossarena.engine.EntityVisual;
import com.bossarena.engine.PrayerCheck;
import com.bossarena.engine.Tile;
import com.bossarena.engine.TileHazard;
import net.runelite.api.gameval.NpcID;

/**
 * A placeholder fight that exercises every engine feature: walls, a boss that alternates ranged and
 * magic attacks to pray against, and a slam on the player's tile to walk out of.
 * Replace it with a real encounter once the arena and attacks are designed.
 */
public class SparringGolemEncounter implements Encounter
{
	private static final int ATTACK_INTERVAL = 4;
	private static final int ATTACKS_PER_SLAM = 4;

	private int attacks;

	@Override
	public String name()
	{
		return "Sparring Golem";
	}

	@Override
	public Arena createArena()
	{
		return Arena.parse(
			"#############",
			"#...........#",
			"#...........#",
			"#....B......#",
			"#...........#",
			"#..#.....#..#",
			"#...........#",
			"#...........#",
			"#.....P.....#",
			"#...........#",
			"#############");
	}

	@Override
	public Tile playerStart(Arena arena)
	{
		return arena.marker('P');
	}

	@Override
	public CombatStats playerStats()
	{
		return CombatStats.builder()
			.maxHitpoints(99)
			.attackStyle(AttackStyle.MELEE)
			.attackSpeed(4)
			.maxHit(45)
			.accuracy(0.85)
			.build();
	}

	@Override
	public void start(ArenaEngine engine)
	{
		attacks = 0;
		Boss golem = engine.spawnBoss(name(), 2, 400, engine.getArena().marker('B'));
		golem.setVisual(EntityVisual.builder()
			.npcId(NpcID.FIREGIANT)
			.markerArgb(0xFFFF7043)
			.build());
		golem.setBehaviour(this::golemTick);
	}

	private void golemTick(ArenaEngine engine, Boss golem)
	{
		if (engine.getTick() % ATTACK_INTERVAL != 0)
		{
			return;
		}
		attacks++;
		if (attacks % ATTACKS_PER_SLAM == 0)
		{
			slam(engine);
			return;
		}
		AttackStyle style = engine.getRandom().nextBoolean() ? AttackStyle.RANGED : AttackStyle.MAGIC;
		int distance = golem.distanceFrom(engine.getPlayer().getTile());
		engine.attackPlayer(golem, style, 30, style.hitDelay(distance) + 1, PrayerCheck.ON_HIT);
	}

	/** Marks the 3x3 around the player, which lands two ticks later. */
	private void slam(ArenaEngine engine)
	{
		Tile centre = engine.getPlayer().getTile();
		TileHazard.TileHazardBuilder hazard = TileHazard.builder()
			.warningTicks(2)
			.activeTicks(1)
			.minDamage(25)
			.maxDamage(40);
		for (int dx = -1; dx <= 1; dx++)
		{
			for (int dy = -1; dy <= 1; dy++)
			{
				Tile tile = centre.plus(dx, dy);
				if (!engine.getArena().isBlocked(tile))
				{
					hazard.tile(tile);
				}
			}
		}
		engine.addHazard(hazard.build());
	}
}
