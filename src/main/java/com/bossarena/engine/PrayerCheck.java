package com.bossarena.engine;

/** When an attack looks at the player's protection prayer. */
public enum PrayerCheck
{
	/** The prayer must be up on the tick the attack is launched. */
	ON_LAUNCH,
	/** The prayer must be up on the tick the hitsplat appears. */
	ON_HIT
}
