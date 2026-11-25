package com.bossarena;

import com.bossarena.encounters.KerberosEncounter;
import com.bossarena.engine.ArenaEngine;
import com.bossarena.engine.Boss;
import com.bossarena.engine.Encounter;
import com.bossarena.engine.EngineListener;
import com.bossarena.engine.Entity;
import com.bossarena.engine.EntityVisual;
import com.bossarena.engine.Prayer;
import com.bossarena.engine.Tile;
import com.google.inject.Provides;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import javax.inject.Inject;
import javax.swing.SwingUtilities;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.ChatMessageType;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.MenuAction;
import net.runelite.api.Model;
import net.runelite.api.ModelData;
import net.runelite.api.NPCComposition;
import net.runelite.api.Player;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.events.ClientTick;
import net.runelite.api.events.CommandExecuted;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.MenuOptionClicked;
import net.runelite.api.gameval.SpriteID;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.config.Keybind;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.game.SpriteManager;
import net.runelite.client.input.KeyManager;
import net.runelite.client.input.MouseAdapter;
import net.runelite.client.input.MouseManager;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.ui.overlay.OverlayManager;
import net.runelite.client.util.HotkeyListener;

@Slf4j
@PluginDescriptor(
	name = "Boss Arena",
	description = "Fight custom bosses in a client-side arena drawn over the game scene",
	tags = {"arena", "boss", "custom", "minigame"}
)
public class BossArenaPlugin extends Plugin
{
	private static final String COMMAND = "arena";

	@Inject
	private Client client;
	@Inject
	private ClientThread clientThread;
	@Inject
	private BossArenaConfig config;
	@Inject
	private OverlayManager overlayManager;
	@Inject
	private KeyManager keyManager;
	@Inject
	private MouseManager mouseManager;
	@Inject
	private SpriteManager spriteManager;
	@Inject
	private ArenaOverlay arenaOverlay;
	@Inject
	private ArenaHudOverlay hudOverlay;

	/** The running fight, or null. Only touched on the client thread. */
	@Getter(AccessLevel.PACKAGE)
	private ArenaSession session;
	/** The arena tile under the mouse, worked out by the overlay each frame. */
	@Setter(AccessLevel.PACKAGE)
	private Tile hoveredTile;

	private final Map<Prayer, BufferedImage> prayerIcons = new EnumMap<>(Prayer.class);
	private final Map<Prayer, BufferedImage> headIcons = new EnumMap<>(Prayer.class);
	private final List<HotkeyListener> hotkeys = new ArrayList<>();

	private final MouseAdapter mouseListener = new MouseAdapter()
	{
		@Override
		public MouseEvent mousePressed(MouseEvent event)
		{
			if (!SwingUtilities.isLeftMouseButton(event))
			{
				return event;
			}
			Prayer prayer = hudOverlay.prayerAt(event.getPoint());
			if (prayer != null)
			{
				event.consume();
				clientThread.invoke(() -> clickPrayer(prayer));
			}
			return event;
		}
	};

	@Provides
	BossArenaConfig provideConfig(ConfigManager configManager)
	{
		return configManager.getConfig(BossArenaConfig.class);
	}

	@Override
	protected void startUp()
	{
		overlayManager.add(arenaOverlay);
		overlayManager.add(hudOverlay);
		mouseManager.registerMouseListener(mouseListener);

		addHotkey(config::toggleKey, this::toggleArena);
		addHotkey(config::restartKey, this::restartArena);
		addHotkey(config::meleePrayerKey, () -> clickPrayer(Prayer.PROTECT_FROM_MELEE));
		addHotkey(config::missilesPrayerKey, () -> clickPrayer(Prayer.PROTECT_FROM_MISSILES));
		addHotkey(config::magicPrayerKey, () -> clickPrayer(Prayer.PROTECT_FROM_MAGIC));
		addHotkey(config::runKey, this::toggleRun);
	}

	@Override
	protected void shutDown()
	{
		overlayManager.remove(arenaOverlay);
		overlayManager.remove(hudOverlay);
		mouseManager.unregisterMouseListener(mouseListener);
		hotkeys.forEach(keyManager::unregisterKeyListener);
		hotkeys.clear();
		clientThread.invoke(this::stopArena);
	}

	/** Registers a hotkey whose action runs on the client thread. */
	private void addHotkey(Supplier<Keybind> keybind, Runnable action)
	{
		HotkeyListener listener = new HotkeyListener(keybind)
		{
			@Override
			public void hotkeyPressed()
			{
				clientThread.invoke(action);
			}
		};
		hotkeys.add(listener);
		keyManager.registerKeyListener(listener);
	}

	// ---- Starting and stopping. Client thread only. ----

	private void toggleArena()
	{
		if (session != null)
		{
			stopArena();
		}
		else
		{
			startArena();
		}
	}

	private void restartArena()
	{
		stopArena();
		startArena();
	}

	private void startArena()
	{
		Player local = client.getLocalPlayer();
		if (client.getGameState() != GameState.LOGGED_IN || local == null)
		{
			return;
		}
		loadIcons();

		Encounter encounter = config.encounter().create();
		ArenaEngine engine = new ArenaEngine(encounter, System.nanoTime());
		engine.setListener(new EngineListener()
		{
			@Override
			public void onMessage(String message)
			{
				chat(message);
			}

			@Override
			public void onStateChanged(ArenaEngine.State state)
			{
				if (state == ArenaEngine.State.WON)
				{
					chat("You defeated " + encounter.name() + " in " + engine.getTick() + " ticks.");
				}
				else if (state == ArenaEngine.State.LOST)
				{
					chat("You were defeated by " + encounter.name() + ". Restart with " + config.restartKey() + ".");
				}
			}
		});

		// Put the arena's player start tile under the real character.
		Tile start = engine.getPlayer().getTile();
		WorldPoint here = local.getWorldLocation();
		ArenaView view = new ArenaView(new WorldPoint(here.getX() - start.getX(), here.getY() - start.getY(), here.getPlane()));

		session = new ArenaSession(engine, view);
		syncVisuals();
		chat("Started " + encounter.name() + ". Click arena tiles to move and the boss to attack.");
	}

	private void stopArena()
	{
		if (session == null)
		{
			return;
		}
		session.getVisuals().values().forEach(ActorVisual::remove);
		session = null;
		hoveredTile = null;
	}

	/** Creates scene objects for any entity that does not have one yet, such as a boss spawned mid-fight. */
	private void syncVisuals()
	{
		ArenaEngine engine = session.getEngine();
		Map<Entity, ActorVisual> visuals = session.getVisuals();
		if (!visuals.containsKey(engine.getPlayer()))
		{
			visuals.put(engine.getPlayer(), createVisual(engine.getPlayer()));
		}
		for (Boss boss : engine.getBosses())
		{
			if (!visuals.containsKey(boss))
			{
				visuals.put(boss, createVisual(boss));
			}
		}
	}

	private ActorVisual createVisual(Entity entity)
	{
		EntityVisual visual = entity.getVisual();
		if (visual.getArt() != null)
		{
			int height = KerberosEncounter.ART.equals(visual.getArt()) ? KerberosArt.HEIGHT : 180 * entity.getSize();
			return new ActorVisual(client, entity, height);
		}
		if (visual.getNpcId() != -1)
		{
			return new ActorVisual(client, entity, loadNpcModel(visual.getNpcId()),
				visual.getIdleAnimation(), visual.getWalkAnimation(), visual.getRunAnimation());
		}
		// No NPC given: borrow the local player's look and stance.
		Player local = client.getLocalPlayer();
		if (local == null)
		{
			return new ActorVisual(client, entity, null, -1, -1, -1);
		}
		return new ActorVisual(client, entity, local.getModel(),
			local.getIdlePoseAnimation(), local.getWalkAnimation(), local.getRunAnimation());
	}

	private Model loadNpcModel(int npcId)
	{
		NPCComposition composition = client.getNpcDefinition(npcId);
		int[] modelIds = composition == null ? null : composition.getModels();
		if (modelIds == null || modelIds.length == 0)
		{
			log.debug("NPC {} has no models", npcId);
			return null;
		}
		ModelData[] parts = new ModelData[modelIds.length];
		for (int i = 0; i < modelIds.length; i++)
		{
			parts[i] = client.loadModelData(modelIds[i]);
			if (parts[i] == null)
			{
				log.debug("Could not load model {} of NPC {}", modelIds[i], npcId);
				return null;
			}
		}
		ModelData merged = client.mergeModels(parts);
		short[] find = composition.getColorToReplace();
		short[] replace = composition.getColorToReplaceWith();
		if (find != null && replace != null)
		{
			merged = merged.cloneColors();
			for (int i = 0; i < Math.min(find.length, replace.length); i++)
			{
				merged.recolor(find[i], replace[i]);
			}
		}
		return merged.light();
	}

	private void loadIcons()
	{
		if (!prayerIcons.isEmpty())
		{
			return;
		}
		putIcon(prayerIcons, Prayer.PROTECT_FROM_MELEE, SpriteID.Prayeron.PROTECT_FROM_MELEE, 0);
		putIcon(prayerIcons, Prayer.PROTECT_FROM_MISSILES, SpriteID.Prayeron.PROTECT_FROM_MISSILES, 0);
		putIcon(prayerIcons, Prayer.PROTECT_FROM_MAGIC, SpriteID.Prayeron.PROTECT_FROM_MAGIC, 0);
		putIcon(headIcons, Prayer.PROTECT_FROM_MELEE, SpriteID.HEADICONS_PRAYER, 0);
		putIcon(headIcons, Prayer.PROTECT_FROM_MISSILES, SpriteID.HEADICONS_PRAYER, 1);
		putIcon(headIcons, Prayer.PROTECT_FROM_MAGIC, SpriteID.HEADICONS_PRAYER, 2);
	}

	private void putIcon(Map<Prayer, BufferedImage> icons, Prayer prayer, int spriteId, int index)
	{
		BufferedImage image = spriteManager.getSprite(spriteId, index);
		if (image != null)
		{
			icons.put(prayer, image);
		}
	}

	/** The prayer book icon for a prayer, or null if it has not loaded. */
	BufferedImage prayerIcon(Prayer prayer)
	{
		return prayerIcons.get(prayer);
	}

	/** The overhead icon for a prayer, or null for no prayer. */
	BufferedImage headIcon(Prayer prayer)
	{
		return headIcons.get(prayer);
	}

	// ---- Input. Client thread only. ----

	private void clickPrayer(Prayer prayer)
	{
		if (session != null)
		{
			session.getEngine().clickPrayer(prayer);
		}
	}

	private void toggleRun()
	{
		if (session != null)
		{
			ArenaEngine engine = session.getEngine();
			engine.setRunning(!engine.getPlayer().isRunning());
		}
	}

	private void chat(String message)
	{
		client.addChatMessage(ChatMessageType.GAMEMESSAGE, "", "[Arena] " + message, null);
	}

	@Subscribe
	public void onClientTick(ClientTick event)
	{
		if (session == null)
		{
			return;
		}
		if (session.advanceClientTick())
		{
			session.getEngine().tick();
			syncVisuals();
		}
		double progress = session.progress();
		for (ActorVisual visual : session.getVisuals().values())
		{
			visual.update(session.getView(), progress);
		}
	}

	/**
	 * While the mouse is over the arena, clicks in the game world drive the arena character instead
	 * of the real one. The click is consumed, so nothing is sent to the server.
	 */
	@Subscribe
	public void onMenuOptionClicked(MenuOptionClicked event)
	{
		Tile tile = hoveredTile;
		if (session == null || tile == null || !isWorldAction(event))
		{
			return;
		}
		event.consume();
		ArenaEngine engine = session.getEngine();
		if (engine.getState() != ArenaEngine.State.RUNNING)
		{
			return;
		}
		Boss boss = engine.bossAt(tile);
		if (boss != null)
		{
			engine.attack(boss);
		}
		else
		{
			engine.walkTo(tile);
		}
	}

	private static boolean isWorldAction(MenuOptionClicked event)
	{
		MenuAction action = event.getMenuAction();
		if (event.getWidget() != null || action == MenuAction.CANCEL || action == MenuAction.RUNELITE)
		{
			return false;
		}
		String name = action.name();
		return !name.startsWith("EXAMINE") && !name.startsWith("CC_OP") && !name.startsWith("WIDGET")
			&& !name.startsWith("RUNELITE");
	}

	@Subscribe
	public void onGameStateChanged(GameStateChanged event)
	{
		if (event.getGameState() != GameState.LOGGED_IN && event.getGameState() != GameState.LOADING)
		{
			stopArena();
		}
	}

	@Subscribe
	public void onCommandExecuted(CommandExecuted event)
	{
		if (!COMMAND.equalsIgnoreCase(event.getCommand()))
		{
			return;
		}
		String argument = event.getArguments().length > 0 ? event.getArguments()[0] : "";
		if ("restart".equalsIgnoreCase(argument))
		{
			restartArena();
		}
		else
		{
			toggleArena();
		}
	}
}
