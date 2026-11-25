package com.bossarena.encounters;

import com.bossarena.engine.Arena;
import com.bossarena.engine.ArenaEngine;
import com.bossarena.engine.AttackStyle;
import com.bossarena.engine.Boss;
import com.bossarena.engine.CombatStats;
import com.bossarena.engine.Encounter;
import com.bossarena.engine.EntityVisual;
import com.bossarena.engine.Prayer;
import com.bossarena.engine.Prop;
import com.bossarena.engine.Tile;
import com.bossarena.engine.TileHazard;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import lombok.Getter;
import net.runelite.api.gameval.NpcID;

/**
 * Kerberos: a memory fight. The boss shows a sequence of cues, pauses, then performs the attacks
 * in the same order. The player has to replay the sequence from memory.
 *
 * <p>Three lanes run from the boss to the player, one per combat style, each ending in a shield.
 * A shield only works while the matching protection prayer is up: a head attack that reaches a
 * working shield is stopped, otherwise it carries on and hits the player. Slams cover the left or
 * right four columns of the seven-wide room, so the player has to be standing on the other side.
 */
public class KerberosEncounter implements Encounter
{
	public enum Attack
	{
		MELEE("Melee", AttackStyle.MELEE, Prayer.PROTECT_FROM_MELEE, 1, 0xFFE53935),
		MAGIC("Magic", AttackStyle.MAGIC, Prayer.PROTECT_FROM_MAGIC, 3, 0xFF1E88E5),
		RANGED("Ranged", AttackStyle.RANGED, Prayer.PROTECT_FROM_MISSILES, 5, 0xFF43A047),
		SLAM_LEFT("Slam left", null, null, -1, 0xFFFB8C00),
		SLAM_RIGHT("Slam right", null, null, -1, 0xFFFB8C00);

		private final String cue;
		/** Null for slams. */
		@Getter
		private final AttackStyle style;
		@Getter
		private final Prayer prayer;
		/** The lane's column, or -1 for slams. */
		private final int lane;
		private final int argb;

		Attack(String cue, AttackStyle style, Prayer prayer, int lane, int argb)
		{
			this.cue = cue;
			this.style = style;
			this.prayer = prayer;
			this.lane = lane;
			this.argb = argb;
		}

		public boolean isSlam()
		{
			return style == null;
		}

		/** True if a slam lands on the given column. Both slams cover the middle column. */
		public boolean hitsColumn(int x)
		{
			return this == SLAM_LEFT ? x <= MIDDLE : this == SLAM_RIGHT && x >= MIDDLE;
		}
	}

	// ---- Tuning. All times are in ticks. ----

	/** How long each cue takes; it is shown for the first tick and the rest is a gap. */
	static final int CUE_TICKS = 2;
	/** The pause after the last cue before the first attack is launched. */
	static final int MEMORY_TICKS = 4;
	/** How long a head attack takes to travel down its lane to the shield. */
	static final int TRAVEL_TICKS = 2;
	/** The gap between one attack landing and the next. */
	static final int ATTACK_INTERVAL = 2;
	/** The breather after a sequence before the next one starts. */
	static final int REST_TICKS = 4;
	static final int OPENING_TICKS = 3;
	static final int MIN_LENGTH = 2;
	static final int MAX_LENGTH = 8;
	static final int HEAD_DAMAGE = 40;
	static final int SLAM_DAMAGE = 40;

	// ---- Layout ----

	private static final int WIDTH = 7;
	private static final int MIDDLE = WIDTH / 2;
	/** The open row in front of the boss, where lanes start and scrapes are shown. */
	private static final int APRON_ROW = 9;
	private static final int SHIELD_ROW = 4;
	private static final int LANE_END_ROW = 3;
	private static final int LANE_TINT = 0x28000000;

	private final Map<Attack, Prop> shields = new EnumMap<>(Attack.class);
	/** Things due to happen on a given tick, run from the boss's turn so they see the player's new position. */
	private final TreeMap<Integer, List<Runnable>> timeline = new TreeMap<>();
	private final Map<Integer, Attack> landing = new TreeMap<>();
	private List<Attack> sequence = Collections.emptyList();
	@Getter
	private int round;

	@Override
	public String name()
	{
		return "Kerberos";
	}

	@Override
	public Arena createArena()
	{
		Arena arena = Arena.parse(
			".......",
			".......",
			"..B....",
			".......",
			"#.#.#.#",
			"#.#.#.#",
			"#.#.#.#",
			"#.#.#.#",
			"#.#.#.#",
			"#.#.#.#",
			".......",
			"...P...",
			".......");
		for (Attack attack : Attack.values())
		{
			if (attack.isSlam())
			{
				continue;
			}
			for (int y = LANE_END_ROW; y <= APRON_ROW; y++)
			{
				arena.paint(new Tile(attack.lane, y), LANE_TINT | (attack.argb & 0xFFFFFF));
			}
			// The shield stands in the lane, so nothing can be walked through it.
			arena.setBlocked(new Tile(attack.lane, SHIELD_ROW), true);
		}
		return arena;
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
			.attackStyle(AttackStyle.RANGED)
			.attackRange(20)
			.attackSpeed(4)
			.maxHit(30)
			.accuracy(0.9)
			.build();
	}

	@Override
	public void start(ArenaEngine engine)
	{
		round = 0;
		timeline.clear();
		landing.clear();
		shields.clear();
		sequence = Collections.emptyList();

		Boss kerberos = engine.spawnBoss(name(), 3, 600, engine.getArena().marker('B'));
		kerberos.setVisual(EntityVisual.builder()
			.npcId(NpcID.HELLHOUND)
			.markerArgb(0xFFB71C1C)
			.build());
		kerberos.setBehaviour(this::bossTick);

		for (Attack attack : Attack.values())
		{
			if (!attack.isSlam())
			{
				shields.put(attack, engine.addProp(new Prop(new Tile(attack.lane, SHIELD_ROW), attack.prayer, attack.argb)));
			}
		}
		at(engine.getTick() + OPENING_TICKS, () -> beginSequence(engine, kerberos));
	}

	@Override
	public String status()
	{
		return round == 0 ? null : "Sequence " + round + ": " + sequence.size() + " attacks";
	}

	/** The attacks of the sequence being shown or performed, in order. */
	public List<Attack> getSequence()
	{
		return Collections.unmodifiableList(sequence);
	}

	/** The attack that lands on the given tick, or null. */
	public Attack landingOn(int tick)
	{
		return landing.get(tick);
	}

	private void bossTick(ArenaEngine engine, Boss kerberos)
	{
		Prayer prayer = engine.getPlayer().getPrayer();
		shields.forEach((attack, shield) -> shield.setLit(attack.prayer == prayer));

		while (!timeline.isEmpty() && timeline.firstKey() <= engine.getTick())
		{
			timeline.pollFirstEntry().getValue().forEach(Runnable::run);
		}
	}

	private void at(int tick, Runnable action)
	{
		timeline.computeIfAbsent(tick, t -> new ArrayList<>()).add(action);
	}

	/** Sequences start short and grow by one attack a round until they reach {@link #MAX_LENGTH}. */
	static int longestSequence(int round)
	{
		return Math.min(MAX_LENGTH, MIN_LENGTH + round);
	}

	static int shortestSequence(int round)
	{
		return Math.max(MIN_LENGTH, longestSequence(round) - 2);
	}

	private void beginSequence(ArenaEngine engine, Boss kerberos)
	{
		round++;
		int shortest = shortestSequence(round);
		int length = shortest + engine.getRandom().nextInt(longestSequence(round) - shortest + 1);
		Attack[] options = Attack.values();
		List<Attack> attacks = new ArrayList<>();
		for (int i = 0; i < length; i++)
		{
			attacks.add(options[engine.getRandom().nextInt(options.length)]);
		}
		sequence = attacks;
		landing.clear();

		int now = engine.getTick();
		int firstLanding = now + length * CUE_TICKS + MEMORY_TICKS + TRAVEL_TICKS;
		for (int i = 0; i < length; i++)
		{
			Attack attack = attacks.get(i);
			int lands = firstLanding + i * ATTACK_INTERVAL;
			landing.put(lands, attack);
			at(now + i * CUE_TICKS, () -> showCue(engine, kerberos, attack));
			at(lands - TRAVEL_TICKS, () -> launch(engine, attack));
			at(lands, () -> land(engine, attack));
		}
		at(firstLanding + (length - 1) * ATTACK_INTERVAL + REST_TICKS, () -> beginSequence(engine, kerberos));
	}

	/** Shows which attack is coming: a head rearing over its lane, or a foot scraping one side. */
	private void showCue(ArenaEngine engine, Boss kerberos, Attack attack)
	{
		kerberos.say(attack.cue, engine.getTick() + 1);
		TileHazard.TileHazardBuilder flash = TileHazard.builder()
			.warningTicks(0)
			.activeTicks(1)
			.minDamage(0)
			.maxDamage(0)
			.activeArgb(0xB0000000 | (attack.argb & 0xFFFFFF));
		if (attack.isSlam())
		{
			for (int x = 0; x < WIDTH; x++)
			{
				if (attack.hitsColumn(x))
				{
					flash.tile(new Tile(x, APRON_ROW));
				}
			}
		}
		else
		{
			for (int y = APRON_ROW; y > APRON_ROW - 3; y--)
			{
				flash.tile(new Tile(attack.lane, y));
			}
		}
		engine.addHazard(flash.build());
	}

	private void launch(ArenaEngine engine, Attack attack)
	{
		if (!attack.isSlam())
		{
			engine.launchProjectile(new Tile(attack.lane, APRON_ROW), new Tile(attack.lane, SHIELD_ROW), TRAVEL_TICKS, attack.argb);
		}
	}

	private void land(ArenaEngine engine, Attack attack)
	{
		if (attack.isSlam())
		{
			TileHazard.TileHazardBuilder slam = TileHazard.builder()
				.warningTicks(0)
				.activeTicks(1)
				.minDamage(SLAM_DAMAGE)
				.maxDamage(SLAM_DAMAGE)
				.activeArgb(0xA0000000 | (attack.argb & 0xFFFFFF));
			for (int x = 0; x < WIDTH; x++)
			{
				for (int y = 0; y <= APRON_ROW; y++)
				{
					Tile tile = new Tile(x, y);
					if (attack.hitsColumn(x) && !engine.getArena().isBlocked(tile))
					{
						slam.tile(tile);
					}
				}
			}
			engine.addHazard(slam.build());
			return;
		}

		Prop shield = shields.get(attack);
		if (engine.getPlayer().getPrayer().blocks(attack.style))
		{
			shield.setFlashUntilTick(engine.getTick() + 1);
			return;
		}
		// The shield was down, so the attack carries on to the player and hits a tick later.
		engine.launchProjectile(shield.getTile(), engine.getPlayer(), 1, attack.argb);
		at(engine.getTick() + 1, () -> engine.damagePlayer(HEAD_DAMAGE));
	}
}
