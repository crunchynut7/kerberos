package com.bossarena.engine;

import lombok.Builder;
import lombok.Value;

/**
 * Fixed combat numbers for the player. There is no gear, prayer drain or special attack energy;
 * an encounter simply picks the stats it wants the player to have.
 */
@Value
@Builder(toBuilder = true)
public class CombatStats
{
	@Builder.Default
	int maxHitpoints = 99;
	@Builder.Default
	AttackStyle attackStyle = AttackStyle.MELEE;
	/** Tiles. Melee is always 1. */
	@Builder.Default
	int attackRange = 1;
	/** Ticks between attacks. */
	@Builder.Default
	int attackSpeed = 4;
	@Builder.Default
	int maxHit = 40;
	/** Chance from 0 to 1 that an attack is not a zero. */
	@Builder.Default
	double accuracy = 0.8;
	/** Animation played on attack, or -1 for none. */
	@Builder.Default
	int attackAnimation = -1;
}
