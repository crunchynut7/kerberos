package com.bossarena;

import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.Keybind;

@ConfigGroup(BossArenaConfig.GROUP)
public interface BossArenaConfig extends Config
{
	String GROUP = "boss-arena";

	@ConfigItem(
		keyName = "encounter",
		name = "Encounter",
		description = "Which fight to start",
		position = -1
	)
	default EncounterChoice encounter()
	{
		return EncounterChoice.KERBEROS;
	}

	@ConfigItem(
		keyName = "toggleKey",
		name = "Start / stop arena",
		description = "Starts the fight around your character, or stops it if one is running",
		position = 0
	)
	default Keybind toggleKey()
	{
		return new Keybind(KeyEvent.VK_B, InputEvent.CTRL_DOWN_MASK | InputEvent.SHIFT_DOWN_MASK);
	}

	@ConfigItem(
		keyName = "restartKey",
		name = "Restart fight",
		description = "Restarts the fight from the beginning",
		position = 1
	)
	default Keybind restartKey()
	{
		return new Keybind(KeyEvent.VK_R, InputEvent.CTRL_DOWN_MASK | InputEvent.SHIFT_DOWN_MASK);
	}

	@ConfigItem(
		keyName = "meleePrayerKey",
		name = "Protect from Melee",
		description = "Toggles the arena's Protect from Melee. The on-screen buttons also work.",
		position = 2
	)
	default Keybind meleePrayerKey()
	{
		return Keybind.NOT_SET;
	}

	@ConfigItem(
		keyName = "missilesPrayerKey",
		name = "Protect from Missiles",
		description = "Toggles the arena's Protect from Missiles. The on-screen buttons also work.",
		position = 3
	)
	default Keybind missilesPrayerKey()
	{
		return Keybind.NOT_SET;
	}

	@ConfigItem(
		keyName = "magicPrayerKey",
		name = "Protect from Magic",
		description = "Toggles the arena's Protect from Magic. The on-screen buttons also work.",
		position = 4
	)
	default Keybind magicPrayerKey()
	{
		return Keybind.NOT_SET;
	}

	@ConfigItem(
		keyName = "runKey",
		name = "Toggle run",
		description = "Switches the arena character between running and walking",
		position = 5
	)
	default Keybind runKey()
	{
		return Keybind.NOT_SET;
	}

	@ConfigItem(
		keyName = "showGrid",
		name = "Show tile grid",
		description = "Outlines every arena tile",
		position = 6
	)
	default boolean showGrid()
	{
		return true;
	}

	@ConfigItem(
		keyName = "showTrueTiles",
		name = "Show true tiles",
		description = "Outlines the tile each character really stands on",
		position = 7
	)
	default boolean showTrueTiles()
	{
		return true;
	}
}
