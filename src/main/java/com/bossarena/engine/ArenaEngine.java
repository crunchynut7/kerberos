package com.bossarena.engine;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Random;
import lombok.Getter;
import lombok.Setter;

/**
 * Runs one attempt at an {@link Encounter}. The engine knows nothing about the game client: call
 * {@link #tick()} once per game tick and read the state back out to draw it.
 *
 * <p>Not thread safe. Call everything from one thread.
 *
 * <p>Each tick runs in this order: queued player input, scheduled tasks, player movement,
 * player attack, encounter and boss logic, tile hazards, then hits that are due to land.
 */
public class ArenaEngine
{
	public enum State
	{
		RUNNING,
		WON,
		LOST
	}

	/** How many ticks a hitsplat stays above an entity. */
	public static final int HITSPLAT_TICKS = 2;

	private static final int RANGED_ARGB = 0xFF4CAF50;
	private static final int MAGIC_ARGB = 0xFF42A5F5;
	private static final int TYPELESS_ARGB = 0xFFE0E0E0;

	@Getter
	private final Encounter encounter;
	@Getter
	private final Arena arena;
	@Getter
	private final PlayerEntity player;
	@Getter
	private final Random random;
	private final List<Boss> bosses = new ArrayList<>();
	private final List<ActiveHazard> hazards = new ArrayList<>();
	private final List<Projectile> projectiles = new ArrayList<>();
	private final List<PendingHit> pendingHits = new ArrayList<>();
	private final List<ScheduledTask> scheduled = new ArrayList<>();
	private final List<Prayer> prayerClicks = new ArrayList<>();
	@Setter
	private EngineListener listener = new EngineListener()
	{
	};

	@Getter
	private int tick;
	@Getter
	private State state = State.RUNNING;

	private Tile queuedWalk;
	private Boss queuedTarget;

	public ArenaEngine(Encounter encounter, long seed)
	{
		this.encounter = encounter;
		this.random = new Random(seed);
		this.arena = encounter.createArena();
		this.player = new PlayerEntity(encounter.playerStats(), encounter.playerStart(arena));
		encounter.start(this);
	}

	// ---- Player input. Takes effect at the start of the next tick. ----

	public void walkTo(Tile tile)
	{
		queuedWalk = tile;
		queuedTarget = null;
	}

	public void attack(Boss boss)
	{
		queuedTarget = boss;
		queuedWalk = null;
	}

	/** Clicks a prayer: turns it on, or off if it is already on. */
	public void clickPrayer(Prayer prayer)
	{
		prayerClicks.add(prayer);
	}

	public void setRunning(boolean running)
	{
		player.setRunning(running);
	}

	/** The prayer the player will have once queued clicks apply; use this to draw the prayer buttons. */
	public Prayer getDisplayedPrayer()
	{
		Prayer prayer = player.getPrayer();
		for (Prayer click : prayerClicks)
		{
			prayer = prayer == click ? Prayer.NONE : click;
		}
		return prayer;
	}

	// ---- Encounter scripting ----

	public Boss spawnBoss(String name, int size, int maxHitpoints, Tile tile)
	{
		Boss boss = new Boss(name, size, maxHitpoints, tile);
		boss.setFacing(player);
		bosses.add(boss);
		return boss;
	}

	/** Launches an attack at the player that rolls between 0 and {@code maxHit}. */
	public void attackPlayer(Boss source, AttackStyle style, int maxHit, int delayTicks, PrayerCheck check)
	{
		attackPlayer(source, style, 0, maxHit, delayTicks, check);
	}

	/**
	 * Launches an attack at the player. The damage is rolled now and lands after {@code delayTicks};
	 * a matching protection prayer reduces it to zero.
	 */
	public void attackPlayer(Boss source, AttackStyle style, int minDamage, int maxDamage, int delayTicks, PrayerCheck check)
	{
		int damage = minDamage + random.nextInt(maxDamage - minDamage + 1);
		int landTick = tick + Math.max(0, delayTicks);
		boolean protectedAtLaunch = player.getPrayer().blocks(style);
		pendingHits.add(new PendingHit(player, style, damage, landTick, check, protectedAtLaunch));
		if (style != AttackStyle.MELEE && landTick > tick)
		{
			projectiles.add(new Projectile(source, player, style, tick, landTick, projectileColour(style)));
		}
	}

	/** Damages the player immediately, ignoring prayer. */
	public void damagePlayer(int damage)
	{
		applyDamage(player, damage);
	}

	public void heal(Entity entity, int amount)
	{
		if (!entity.isDead() && amount > 0)
		{
			entity.changeHitpoints(amount);
			entity.addHitsplat(new Hitsplat(Hitsplat.Kind.HEAL, amount, tick));
		}
	}

	public ActiveHazard addHazard(TileHazard hazard)
	{
		ActiveHazard active = new ActiveHazard(hazard, tick);
		hazards.add(active);
		return active;
	}

	/** Runs a task a number of ticks from now, before the player moves on that tick. */
	public void schedule(int ticksFromNow, Runnable task)
	{
		scheduled.add(new ScheduledTask(tick + Math.max(1, ticksFromNow), task));
	}

	/**
	 * Walks a boss one tile towards a tile, if it can.
	 *
	 * @return true if the boss moved
	 */
	public boolean stepBossToward(Boss boss, Tile aim)
	{
		Tile at = boss.getTile();
		int dx = Integer.signum(aim.getX() - at.getX());
		int dy = Integer.signum(aim.getY() - at.getY());
		int[][] candidates = {{dx, dy}, {dx, 0}, {0, dy}};
		for (int[] c : candidates)
		{
			if (c[0] == 0 && c[1] == 0)
			{
				continue;
			}
			Tile next = at.plus(c[0], c[1]);
			if (footprintIsOpen(next, boss.getSize()))
			{
				boss.step(next);
				return true;
			}
		}
		return false;
	}

	public void teleportBoss(Boss boss, Tile tile)
	{
		boss.teleport(tile);
	}

	public void message(String message)
	{
		listener.onMessage(message);
	}

	// ---- State for rendering ----

	public List<Boss> getBosses()
	{
		return Collections.unmodifiableList(bosses);
	}

	public List<ActiveHazard> getHazards()
	{
		return Collections.unmodifiableList(hazards);
	}

	public List<Projectile> getProjectiles()
	{
		return Collections.unmodifiableList(projectiles);
	}

	/** The living boss standing on a tile, or null. */
	public Boss bossAt(Tile tile)
	{
		for (Boss boss : bosses)
		{
			if (!boss.isDead() && boss.occupies(tile))
			{
				return boss;
			}
		}
		return null;
	}

	/** True if the player can stand on the tile. */
	public boolean isWalkable(Tile tile)
	{
		return !arena.isBlocked(tile) && bossAt(tile) == null;
	}

	public boolean inAttackRange(Tile from, Boss boss)
	{
		CombatStats stats = player.getStats();
		if (stats.getAttackStyle() == AttackStyle.MELEE)
		{
			return boss.isCardinallyAdjacent(from);
		}
		int distance = boss.distanceFrom(from);
		return distance >= 1 && distance <= stats.getAttackRange();
	}

	// ---- The tick ----

	public void tick()
	{
		tick++;
		player.beginTick();
		player.expireHitsplats(tick - HITSPLAT_TICKS + 1);
		for (Boss boss : bosses)
		{
			boss.beginTick();
			boss.expireHitsplats(tick - HITSPLAT_TICKS + 1);
		}
		projectiles.removeIf(p -> p.getLandTick() <= tick);

		if (state != State.RUNNING)
		{
			return;
		}

		applyInput();
		runScheduled();
		movePlayer();
		playerAttack();

		encounter.tick(this);
		for (Boss boss : new ArrayList<>(bosses))
		{
			if (!boss.isDead() && boss.getBehaviour() != null)
			{
				boss.getBehaviour().tick(this, boss);
			}
		}

		tickHazards();
		landHits();
		updateState();
	}

	private void applyInput()
	{
		for (Prayer click : prayerClicks)
		{
			player.setPrayer(player.getPrayer() == click ? Prayer.NONE : click);
		}
		prayerClicks.clear();

		if (queuedWalk != null)
		{
			Tile aim = queuedWalk;
			player.setTarget(null);
			player.setFacing(null);
			setPath(Pathfinder.find(player.getTile(), this::isWalkable, aim::equals, aim));
			queuedWalk = null;
		}
		else if (queuedTarget != null)
		{
			if (!queuedTarget.isDead())
			{
				player.setTarget(queuedTarget);
				player.setFacing(queuedTarget);
				player.getPath().clear();
				player.setDestination(null);
			}
			queuedTarget = null;
		}
	}

	private void setPath(List<Tile> path)
	{
		player.getPath().clear();
		player.getPath().addAll(path);
		player.setDestination(path.isEmpty() ? null : path.get(path.size() - 1));
	}

	private void runScheduled()
	{
		List<ScheduledTask> due = new ArrayList<>();
		for (Iterator<ScheduledTask> it = scheduled.iterator(); it.hasNext(); )
		{
			ScheduledTask task = it.next();
			if (task.tick <= tick)
			{
				due.add(task);
				it.remove();
			}
		}
		for (ScheduledTask task : due)
		{
			task.task.run();
		}
	}

	private void movePlayer()
	{
		Boss target = player.getTarget();
		if (target != null && target.isDead())
		{
			player.setTarget(null);
			player.getPath().clear();
			player.setDestination(null);
			target = null;
		}
		if (target != null)
		{
			if (inAttackRange(player.getTile(), target))
			{
				player.getPath().clear();
				player.setDestination(null);
			}
			else
			{
				// The boss may have moved, so chase it afresh each tick.
				final Boss chase = target;
				setPath(Pathfinder.find(player.getTile(), this::isWalkable, t -> inAttackRange(t, chase), nearestTileOf(chase)));
			}
		}

		int moves = player.isRunning() ? 2 : 1;
		for (int i = 0; i < moves && !player.getPath().isEmpty(); i++)
		{
			Tile next = player.getPath().peek();
			if (!isWalkable(next))
			{
				player.getPath().clear();
				break;
			}
			player.step(player.getPath().poll());
			if (target != null && inAttackRange(player.getTile(), target))
			{
				player.getPath().clear();
			}
		}
		if (player.getPath().isEmpty())
		{
			player.setDestination(null);
		}
	}

	/** The tile of a boss's footprint closest to the player, used as the aim when it cannot be reached. */
	private Tile nearestTileOf(Boss boss)
	{
		Tile at = player.getTile();
		int x = Math.max(boss.getTile().getX(), Math.min(at.getX(), boss.getTile().getX() + boss.getSize() - 1));
		int y = Math.max(boss.getTile().getY(), Math.min(at.getY(), boss.getTile().getY() + boss.getSize() - 1));
		return new Tile(x, y);
	}

	private void playerAttack()
	{
		Boss target = player.getTarget();
		if (target == null || target.isDead() || tick < player.getNextAttackTick()
			|| !inAttackRange(player.getTile(), target))
		{
			return;
		}
		CombatStats stats = player.getStats();
		int damage = random.nextDouble() < stats.getAccuracy() ? random.nextInt(stats.getMaxHit() + 1) : 0;
		int landTick = tick + stats.getAttackStyle().hitDelay(target.distanceFrom(player.getTile()));
		pendingHits.add(new PendingHit(target, stats.getAttackStyle(), damage, landTick, PrayerCheck.ON_HIT, false));
		if (stats.getAttackStyle() != AttackStyle.MELEE)
		{
			projectiles.add(new Projectile(player, target, stats.getAttackStyle(), tick, landTick,
				projectileColour(stats.getAttackStyle())));
		}
		if (stats.getAttackAnimation() != -1)
		{
			player.playAnimation(stats.getAttackAnimation());
		}
		player.setNextAttackTick(tick + stats.getAttackSpeed());
	}

	private void tickHazards()
	{
		for (Iterator<ActiveHazard> it = hazards.iterator(); it.hasNext(); )
		{
			ActiveHazard active = it.next();
			if (tick >= active.expiresTick())
			{
				it.remove();
				continue;
			}
			TileHazard hazard = active.getHazard();
			if (active.isActive(tick) && hazard.getTiles().contains(player.getTile()))
			{
				int spread = hazard.getMaxDamage() - hazard.getMinDamage();
				applyDamage(player, hazard.getMinDamage() + (spread > 0 ? random.nextInt(spread + 1) : 0));
			}
		}
	}

	private void landHits()
	{
		for (Iterator<PendingHit> it = pendingHits.iterator(); it.hasNext(); )
		{
			PendingHit hit = it.next();
			if (hit.landTick > tick)
			{
				continue;
			}
			it.remove();
			int damage = hit.damage;
			if (hit.target == player)
			{
				boolean prayed = hit.check == PrayerCheck.ON_LAUNCH
					? hit.protectedAtLaunch
					: player.getPrayer().blocks(hit.style);
				if (prayed)
				{
					damage = 0;
				}
			}
			applyDamage(hit.target, damage);
		}
	}

	private void applyDamage(Entity target, int damage)
	{
		if (target.isDead())
		{
			return;
		}
		damage = Math.max(0, Math.min(damage, target.getHitpoints()));
		target.changeHitpoints(-damage);
		target.addHitsplat(new Hitsplat(damage > 0 ? Hitsplat.Kind.DAMAGE : Hitsplat.Kind.BLOCK, damage, tick));
	}

	private void updateState()
	{
		State next = state;
		if (player.isDead())
		{
			next = State.LOST;
		}
		else if (!bosses.isEmpty() && bosses.stream().allMatch(Entity::isDead))
		{
			next = State.WON;
		}
		if (next != state)
		{
			state = next;
			pendingHits.clear();
			hazards.clear();
			projectiles.clear();
			listener.onStateChanged(state);
		}
	}

	private boolean footprintIsOpen(Tile southWest, int size)
	{
		for (int x = 0; x < size; x++)
		{
			for (int y = 0; y < size; y++)
			{
				if (arena.isBlocked(southWest.plus(x, y)))
				{
					return false;
				}
			}
		}
		return true;
	}

	private static int projectileColour(AttackStyle style)
	{
		switch (style)
		{
			case RANGED:
				return RANGED_ARGB;
			case MAGIC:
				return MAGIC_ARGB;
			default:
				return TYPELESS_ARGB;
		}
	}

	private static final class PendingHit
	{
		private final Entity target;
		private final AttackStyle style;
		private final int damage;
		private final int landTick;
		private final PrayerCheck check;
		private final boolean protectedAtLaunch;

		private PendingHit(Entity target, AttackStyle style, int damage, int landTick, PrayerCheck check, boolean protectedAtLaunch)
		{
			this.target = target;
			this.style = style;
			this.damage = damage;
			this.landTick = landTick;
			this.check = check;
			this.protectedAtLaunch = protectedAtLaunch;
		}
	}

	private static final class ScheduledTask
	{
		private final int tick;
		private final Runnable task;

		private ScheduledTask(int tick, Runnable task)
		{
			this.tick = tick;
			this.task = task;
		}
	}
}
