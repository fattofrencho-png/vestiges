# Naming: how we call things in Vestiges

Agreed with JB, 2026-11-04. Use these words in chats, docs, UI text and new code. Where the code still uses an old word, it is listed under "To rename".

## Layers (each one includes the one before it)

```
Vanilla Minecraft
 └ Foundation          new inventory + map, always on, cannot be switched off
    └ RPG preset       levels, trees, gear, mobs, loot, Codex, feats, settlements, villagers, dungeons, dragons
       └ Comfort preset   RPG + sorting, bigger stacks, shulkers, peek, shapes
          └ Full preset      Comfort + workers, world events, Nether pockets, blueprints
```

Custom is not a layer: it is your own pick of features. Freecam, sleep percentage and no mob griefing are off in every preset.

## Words

| Word | Means | Do not say |
|---|---|---|
| Vestiges | The whole mod | "the mod" for a layer |
| Feature | One switchable part (Elites, Map, Dragons). Code: `VestigesSystem` | system, module |
| Group | A heading that sorts features: Character, Loot and enchants, Mobs, World, Comfort. Code: `Category` | category |
| Foundation | Inventory and map. Always on | Core |
| Preset | A ready-made set of features: RPG, Comfort, Full (and Custom) | level |
| RPG | The first preset, the game itself | Core, mod level |
| Full | Every feature on | feature level |
| Vanilla mode | Every optional feature off: vanilla plus Foundation. Must play like vanilla | vanilla version |

"Vanilla" alone means Minecraft without Vestiges, or a world in Vanilla mode. "Vanilla-style" is the design rule (boost vanilla first, nothing that feels un-Mojang) and is always written with "-style".

## Quests: two labels

**Kind** (what the quest is):
- Guide: teaches one feature. Each guide quest has one home feature.
- Odd Job: a villager request (today's "side" quests, 13 jobs).
- Story: the campaign chain (after the MVP).
- Note: found in a loot chest.

**Needs** (what must be on for it to appear):
- Vanilla quest: uses only plain Minecraft actions (bring 8 wool). Works in Vanilla mode.
- RPG quest: uses RPG features (levels, elites, Codex, feats).
- Feature quest: belongs to one feature (Dungeons, Dragons, Villagers) and is hidden when that feature is off.

A quest is written "Guide, Feature quest (Dungeons)": kind first, then needs. To confirm with JB: whether the Needs label becomes a field in the quest JSON or stays a way of talking.

## Rooms (chats and worktrees)

Main room, Assets room, World room, UI room (plus Lore and Quests side chats). "World" stays a feature group; "RPG world" stays the merged map. Say "the World room" for the worktree and chat.

## Renames

Done 2026-11-04 (display labels only, uncompiled and untested): `Preset.CORE` label "Core" -> "RPG"; `Category.CORE` label "Always on" -> "Foundation". Enum names stay `CORE` so saved files and tests keep working.

Still to do: `CLAUDE.md` and `design-log.md` wording ("Core preset", "Core RPG features") when next edited; `docs/ideas/quests.md` ("side" -> Odd Job); decide whether Needs becomes a field in quest JSON.
