package com.bossarena.engine;

import lombok.Getter;
import lombok.Setter;

/**
 * A piece of scenery standing on a tile, such as a shield. Encounters change its state as the
 * fight goes on and the renderer draws whatever state it is in.
 */
@Getter
@Setter
public class Prop
{
	private final Tile tile;
	/** The prayer whose icon is shown on the prop, or null for none. */
	private final Prayer icon;
	/** Colour (ARGB) when lit. Unlit props are drawn dimmed. */
	private final int argb;
	private boolean lit;
	/** The prop flashes brightly until this tick, e.g. when it blocks something. */
	private int flashUntilTick;

	public Prop(Tile tile, Prayer icon, int argb)
	{
		this.tile = tile;
		this.icon = icon;
		this.argb = argb;
	}
}
