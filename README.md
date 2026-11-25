# Boss Arena

A RuneLite plugin that runs a custom boss fight entirely on the client. It draws an arena over the
game scene around your character, gives you a stand-in character to move and fight with, and
simulates the fight tick by tick. Nothing is sent to the game server: your real character does not
move, pray or attack.

This is for **original** bosses only. Recreating real bosses, raids or wave minigames is against
Jagex's third-party client guidelines (see `AGENTS.md`).

## Running it

```bash
./gradlew run
```

Log in, then press `Ctrl+Shift+B` (or type `::arena`) to start the fight where you stand.

| Action | How |
| --- | --- |
| Move | Click an arena tile |
| Attack | Click a tile the boss stands on |
| Protection prayers | Click the three buttons in the panel, or bind keys in the plugin settings |
| Restart | `Ctrl+Shift+R` or `::arena restart` |
| Stop | `Ctrl+Shift+B` or `::arena` |

While the mouse is over the arena, world clicks are consumed and go to the arena character.

## Layout

- `com.bossarena.engine` is the fight simulation. It has no RuneLite dependencies, so it is covered
  by plain unit tests (`./gradlew test`).
- `com.bossarena` draws the engine's state in the client and feeds it input.
- `com.bossarena.encounters` holds the fights. Pick one with the "Encounter" setting.
  - `KerberosEncounter` is a memory fight: the boss shows a sequence of cues, pauses, then performs
    the attacks in order. Head attacks travel down a lane and are stopped by that lane's shield
    only while the matching protection prayer is up; slams cover the left or right four columns.
    Kerberos is drawn by hand in `KerberosArt`: a head rears to cue its attack and a paw scrapes to
    cue a slam on that side.
    Its timings and damage are constants at the top of the class.
  - `SparringGolemEncounter` is a simple placeholder for trying the engine out.

## What the engine supports

- Arenas drawn as text, with walls, marker tiles and per-tile colours
- Tick-based movement with walking, running and pathfinding around walls and bosses
- Player attacks (melee, ranged or magic) with fixed stats chosen by the encounter
- Boss attacks with a style, damage range, travel delay and projectile
- Protection prayers, checked either when the attack is launched or when it lands, so switching
  prayers on the right tick matters. There is no prayer drain.
- Tile hazards that warn, then damage whoever is standing on them
- Hitsplats, health bars, death and victory
- Props such as shields that light up and flash, overhead text, and projectiles between tiles
- Scheduled tasks, boss movement and teleporting, and multiple bosses, for scripting mechanics

Left out on purpose: gear, prayer points, special attack energy and line of sight.

## Adding a boss

Implement `Encounter`: return an `Arena`, the player's `CombatStats`, and spawn bosses in `start`
with a `BossBehaviour` that runs each tick. Then add it to `EncounterChoice` so it appears in the
"Encounter" setting.
