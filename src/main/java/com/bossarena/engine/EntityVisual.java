package com.bossarena.engine;

import lombok.Builder;
import lombok.Value;

/**
 * How the renderer should draw an entity. Plain ids only, so the engine stays independent of the client.
 * An id of -1 means "none".
 */
@Value
@Builder
public class EntityVisual
{
	/** NPC whose model is borrowed for this entity, or -1 to use the local player's model. */
	@Builder.Default
	int npcId = -1;
	@Builder.Default
	int idleAnimation = -1;
	@Builder.Default
	int walkAnimation = -1;
	@Builder.Default
	int runAnimation = -1;
	/** Fallback marker colour (ARGB) drawn under the entity. */
	@Builder.Default
	int markerArgb = 0xFFFFFFFF;
}
