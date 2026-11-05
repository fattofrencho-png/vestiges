# World log

The record of world generation decisions. Written by the world chat, read by the main chat. Rules: see `README.md`.

## For the main chat

Things that need work outside this room. The main chat removes a line once it is done.

- **Tour clean-up filter:** `*RPG Mod*` also matches this folder. Leave out `*RPG Mod World*` too.
- **Later, once `world` is merged:** entry titles, map labels and the Places page can read places from `WorldAtlas`. Vanilla worlds have no globe, so each needs a fallback. Region reputation needs a rethink: the 4 regions are now continents.
- **Waystone prices:** crossing Trenec costs about 248 emeralds and crossing the globe 960. A cap is a decision for the main chat and the author.

- **Merging `world`:** it branched from main at 07729de, so expect small conflicts in `VestigesSystem`, `VestigesCommands`, `Vestiges`, `FeatureNet`, `WelcomeTab`, `ScreenshotTour` and the mixin list. Merge only when the author says so.
- **Emerald sites:** players need a way to discover them (map or Codex). Lore names first.

## For the lore chat

- The climates are world generation proposals: Thornner cold to temperate, Restena cold to temperate, Swetren temperate to a little hot, Ruthenos warm to hot, Trenec temperate to warm. Swetren, Ruthenos and Thornner are still gaps in the lore.
- Dilecta stays a name with a place on Trenec's north coast (20k-38k north of spawn). Nothing is built.

## Decisions

Newest first, with the date.

- ✅ **MVP built, waiting for playtest (2026-11-01).** The world type is now called Vestiges Vanilla on the button (still `vestiges:trenec` in the files). Welcome page switch added, in sync with the World Type button. Guide: `docs/world/playtest.md`. New command: `/vestiges world sites`.
- ✅ **Emerald sites (author, 2026-11-01).** In Vestiges worlds emerald ore exists only at rich sites: 0 to 2 per continent from the seed, at least 3 in a world (numbers in the plan file). Mob drops and trades stay. Sites can be claimed with normal land protection. **Clans after the MVP:** clans owning mines, mine events, regrowing ore; a multiplayer system that should get its own chat.
- ✅ **Dilecta as joined villages (author, 2026-11-01).** One village stops at 257 blocks, so the capital is a main village plus up to 8 districts around it, leaving out any that would stand in the sea or a river.
- ✅ **Dilecta village (2026-11-01).** One vanilla plains village at the north-coast site, as big as the game allows. Real world, seed vestiges: 1086 pieces, 257 x 257 blocks. Mountains fade out around it. It only places when a Vestiges world is running. **Untested:** a plain vanilla world in the real game showing none, how it looks, and how it sits on the coast.
- ✅ **Mountains, caves, rivers (2026-11-01).** Peaks reach about 300 (Amplified ceiling plus extra height along ranges). Caves about 1.4 times bigger, entrances 1.2. Rivers are traced on the map from high ground to the sea, and vanilla ridges noise is steered onto them. Seed 1 only: 89% of river spots on Trenec are river or sea, no stray rivers, all biomes still found. **Untested in the real game:** flat peak tops, surface holes, water filling the rivers.
- ✅ **World plan file (2026-11-01).** Continents, climate, villages and resource sites live in `data/vestiges/vestiges/world_plan/trenec.json`. A new world keeps its own copy in `<world>/vestiges/world_plan.json`, so updates cannot shift its land. Beach fix: fine noise on the coast. Dilecta is listed but not built yet, and resources are empty. **Untested in the real game:** the saved copy, and whether the beach lines are gone.
- ✅ **First real world (2026-11-01).** Vanilla Biome RPG World works in the real game: picked on the World Type button, ready in about 7 seconds. The tour is `VESTIGES_TOUR=world dev runClientGameTest`. **To fix:** broad ranges can cross spawn (seed "vestiges" has frozen peaks at the centre), so new players can start on a mountain. Not done yet: a load-time comparison with vanilla.
- ✅ **Two world settings (author, 2026-11-01).** **Vanilla Biome RPG World** is the one being built. **Custom Biome RPG World** comes after it, on the same globe, with vanilla blocks only: vanilla biomes stay with custom trees and plants, and lore biomes are added in chosen places. Claude makes the trees and structures, and the author reviews them. First sample: a vast ancient forest. Other mods can inspire us, but real files or builds are copied only if the licence allows it or that mod's author agrees, noted in `credits.md`.
- ✅ **All vanilla biomes fit (author asked, 2026-11-01).** Checked on real terrain values with the game's biome picker: 50 of 50 seeds have every overworld biome and pass the map rules. Not yet checked in a real world. To make it work, the cold north is dry (snowy plains, ice spikes and gravelly hills need dry cold) and the cold border sits at z -60k at the centre. Rivers are still vanilla. Mods wait until later: Geophilic first, Biomes O' Plenty last, never bundled.
- ✅ **The globe (author, 2026-11-01).** 240k x 180k blocks with spawn in the middle. Trenec is central and biggest, with Thornner north, Restena east, Ruthenos south and Swetren west (latest lore: these are continents, not regions). Continents are at least about 8,000 blocks apart, a hard rule. Every continent has at least 2 temperature ranges. Outlines and mountain directions come from the seed. Past the edge, vanilla carries on.
- ✅ **The world type's first decisions (author, 2026-10-31):**
  - **Optional:** vanilla terrain stays the default, and the player picks the Vestiges world type.
  - **Oscat:** stays "the first coastal village you find". World generation only makes sure coastal villages exist.
- ✅ **How it is built (2026-11-01):** `GlobeMap` draws the planned map once per seed, in about 1 second. Vanilla's own terrain chain reads it through the `vestiges:globe` function, in our own copy of the chain, so vanilla worlds never change.
