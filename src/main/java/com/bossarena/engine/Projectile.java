package com.bossarena.engine;

import lombok.Value;

/** A purely visual projectile. Damage is handled separately by the hit it accompanies. */
@Value
public class Projectile
{
	Entity source;
	Entity target;
	AttackStyle style;
	int launchTick;
	int landTick;
	int argb;
}
