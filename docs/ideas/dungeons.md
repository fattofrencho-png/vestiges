# Dungeons: ideas and small-dungeon spec (MVP)

Idea chat for the Dungeons area. Design only, no code. Source of truth for the big design stays `docs/design-log.md` ("## Dungeons", Part 1 to 4). This file holds the **MVP slice** and later ideas.
Status: **APPROVED by the author (2026-11-03), ready for the main chat to build.** Sections 1 and 3 are the spec; section 6 is the suggested build order.

## 1. Decided with the author (2026-11-03)

- **MVP themes: Overgrown and Trial.**
- **MVP parts: core run + key + rewards only.** Modifiers, Gate tree, Overlord, tiers beyond what balance covers: after the MVP.
- **Rooms: I generate them by code; the author also feeds me assets** (structures, builds) that I fit in.
- **Run space: one dungeon dimension, one plot per run** (as in the design log).

- **Combat rooms lock:** doors close on entry and open when the room's mobs are dead (Lieutenant and Boss rooms stay shut until the boss dies).
- **Portal frame:** the First Key feat hands over the key plus the exact blocks (chiseled tuff + copper); the player builds the frame by hand. No new items.
- **Trial theme is available from the start in the MVP**; the "find a trial chamber" unlock waits until after it.
- **Asset hand-off:** structure files from vanilla structure blocks (.nbt) and Litematica schematics (I convert them).

- **Room size:** regular rooms **32 × 32** floor with **6 to 10 mobs**; Lieutenant room 40 × 40; Boss room **48 × 48**; start and reward rooms 32 × 32. Corridors between rooms are 5 wide, 8 to 16 long.
- **Markers in templates:** gold block = mob spawn point, chest = cache, lodestone = boss spot, iron door = door slot. All vanilla, replaced on paste.
- **Bosses in the MVP: plain strong mobs.** Named bosses with a name card and big health (Mossbound, Rotwood Witch, Gale, Vault Keeper), no signature moves or rolled powers yet; those come after the MVP.

## 2. Already fixed by the design log (reused as is)

- Portal: Nether-portal-sized frame, chiseled tuff + copper, unique and fixed. Key is single-use, theme and tier are on the key and shown in its tooltip. Everyone within 8 blocks enters together.
- Run: start room → 6 rooms → Lieutenant → 3 rooms → Boss → reward room (13 rooms in all), about 10 min, no timer. Walls unbreakable; players may place and break their own blocks.
- Mob level = 12 + 3 per tier (tier 1 = 15). +50% mob health per extra player. Loot is per player.
- Lives per player: 3 (tiers 1-9), 2 (10-24), 1 (25+). Death = back to the start room with items, minus a life. Out of lives = sent home with items; the run fails when everyone is out. Leaving early = step into the start portal (out for good, key used).
- Rewards: a theme block cache in every room · Lieutenant 1 gear roll · reward room: dungeon loot table gear and books, sometimes worker rank items, and the next key.
- Next key after clearing tier N: N-1 15% · N 55% · N+1 25% · N+2 5% (never below 1); theme kept 60%, changed 40%. Modifiers are off in the MVP, so new keys have none.
- Bosses (full design, MVP uses plain strong mobs): Lieutenant ×6 with 1 rolled power, Boss ×10 with 2 powers. Overgrown: **Mossbound** (heals on moss/grass), **The Rotwood Witch** (potions, calls cave spiders). Trial: **Gale** (wind charges), **The Vault Keeper** (ominous spawner waves + giant bogged).
- Theme blocks/mobs: Overgrown = mossy stone bricks, moss, vines, azalea, dripleaf; zombies, spiders, cave spiders, bogged, witches; hidden mobs, berry bushes, dripleaf over pits. Trial = copper types, tuff types; breezes, bogged, trial spawners.
- Caches: Overgrown = moss, azalea, spore blossom, glow berries, dripleaf, mud, bamboo, cocoa. Trial = copper types, tuff types, breeze rods, heavy core (very rare).
- First key: the **First Key feat** (level 15 + 5 elites) already exists in `data/vestiges/vestiges/feats/first_key.json`; it hands out the tier 1 key + portal frame once dungeons exist. Elites and Warlords sometimes drop low keys (tiers 1-5) so nobody is stuck.
- Theme unlock rule in the log: Overgrown from the start; **Trial after finding a trial chamber**.

## 3. Proposed MVP spec *(to confirm, topic by topic)*

### A. Key and portal
- One item `vestiges:dungeon_key` with a theme + tier stored as item data (no new item per theme). Tooltip: "Overgrown Key, Tier 1".
- MVP tiers: the system accepts any tier; the MVP **balance pass covers tiers 1 to 3**; the rest is "same formula, not tested".
- Portal frame: built by the player from vanilla blocks in a fixed pattern (blocks handed out by the First Key feat), so no new blocks or items.

### B. Run space
- A dimension `vestiges:dungeon` (void, no weather, no mob spawning of its own, fixed time).
- Each run gets a **plot** on a grid far apart (for example 1024 blocks), rooms placed along it. When the run ends everyone leaves, the plot is cleared and reused.
- Entering and leaving always put players back at the portal (position saved with the run).

### C. Rooms and assets
- A room is a small JSON + a builder: `id`, `theme`, `role` (start, combat, lieutenant, boss, reward), size on a 16-block grid, door positions, and **markers**: mob spawn points, cache position, boss spot.
- **Two sources, same format:** (1) **code-generated rooms** (arena, pillar hall, pit room, corridor with alcoves; theme palette from the block lists above); (2) **author assets**: vanilla structure files or Litematica schematics (converted), placed through the same marker system. Author assets win over generated rooms of the same role when both exist.
- Markers are vanilla blocks in the template that get replaced on paste (gold = mob spawn, chest = cache, lodestone = boss spot, iron door = door slot).
- 13 rooms per run in a line, joined by short corridors; rooms picked at random from the theme's pool (no repeat twice in a row).
- Room sizes as in section 1. The generated rooms differ by shape (open arena, pillar hall, pit room, split level) and by theme palette; each theme ships at least 4 combat shapes so a run rarely repeats.
- Mob counts scale: base 6 to 10, +50% health per extra player (log rule), mob list from the theme (Overgrown: zombies, spiders, cave spiders, bogged, witches; Trial: breezes, bogged, trial-spawner mobs).

### D. Room flow *(decided, see section 1)*
- Combat rooms: doors close behind you; they open when the room's mobs are dead. Cache chest appears (per player) when cleared.
- Lieutenant and Boss rooms: name card on entry, doors stay shut until the boss dies.

### E. Rewards *(per-player loot)*
- Cache: 1 theme block stack per room, per player. Lieutenant: 1 gear roll. Reward room: loot table gear/book roll, plus the next key.
- Loot tables are generated: new rules go in `tools/build_loot.py`, never hand-edited.

### F. Switches and config
- One `VestigesSystem` entry "Dungeons" (parent of nothing in MVP). Off = portal frame does nothing, keys not dropped.
- Numbers (lives, mob level, odds) live in `ServerConfig` with the log's defaults.

## 4. Later (not MVP)
- Key modifiers (12 general + 9 theme), Gate tree, Overlord tiers, the other 7 themes, more random layouts, open-space areas.
- **Isometric-camera dungeons in the style of Minecraft Dungeons** (log row "M8 Dungeons"; the map chat also has an isometric view idea). Needs its own talk: camera lock, controls, what the player can see through walls.

## 5. Open questions
None. Spec approved by the author on 2026-11-03.

## 6. Build slices for the main chat (once approved)
1. **Skeleton:** `VestigesSystem` entry, key item with theme + tier, dungeon dimension, plot allocator, portal frame detection and entry/exit (no rooms yet; one empty box room).
2. **Rooms:** room format + code-generated rooms + marker handling + structure/schematic loader, run assembly (13 rooms, door locking).
3. **Mobs and bosses:** theme mob lists, level 12 + 3 per tier, party scaling, Lieutenant and Boss as plain strong named mobs.
4. **Rewards and lives:** per-room caches, loot tables in `tools/build_loot.py`, reward room, next-key odds, First Key feat hookup, lives and leave rules.
5. **Test pass:** one combined test guide (kit block, then commands). Not tested yet: everything.
