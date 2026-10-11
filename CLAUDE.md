# CLAUDE.md

Guidance for Claude Code in this repository.

## What this is

**Vestiges** (mod id `vestiges`, package `dev.vestiges`): a Fabric mod for Minecraft **26.3** (pinned) on Java 25, Fabric API only,
server-authoritative for single player and multiplayer. It merges the author's earlier projects into one endless RPG on top of vanilla
building: it ports **RPG Craft** (datapack) to Java and rebuilds **Hearthbook** (`../../Minecraft DataPacks/RPG_Lore_Mod`) as the new
villager system. Never modify those source projects; copy what is needed.

Every design decision was agreed with the author one topic at a time and lives in **`docs/design-log.md`** (the source of truth).
Approved lore: `docs/lore/` (fragments and Codex flavour text) and the author's own bible in Hearthbook (`bible/lore_source.md`, never
paraphrase it). Design rules that every change must respect:

- **The character is the core.** The base game stays "simple but still interesting"; hard content is opt-in through portal dungeons.
- **Vanilla-style:** boost vanilla features first; new things only if they feel Mojang-made. No classes, no new blocks, no new enchants.
- **Anti-exploit:** anything slightly unvanilla is earned (feats, levels, discoveries) and must not be exploitable.
- **Emeralds are the only currency.** All content is data-driven (JSON datapacks) and every system can be switched off by the server.

## Build order (each milestone ends with a playtest and the author's OK before the next starts)

M0 Foundation (this) · Map + waystones · M1 Character · M2 Inventory · M3 Mobs & loot · M4 Codex & farming · M5 Feats & settlements ·
M6 Villagers · M7 Workers · M8 Dungeons · M9 World & End · M10 Blueprints.

## Commands

```bat
dev build          rem gradlew build with the JDK 25 found in %USERPROFILE%\.jdks (Temurin 25.0.4)
dev test           rem JUnit tests
dev deploy         rem build + replace the jar in the modtesting instance's mods folder (VESTIGES_MODS overrides)
dev runClient      rem dev client; H opens the hub
dev runServer      rem needs eula.txt accepted by the author; never accept it on their behalf
```

Loot tables are generated: edit `tools/build_loot.py` and run `python tools/build_loot.py` (never hand-edit `data/vestiges/loot_table/`
or `data/minecraft/loot_table/chests/`).
Codex entries are generated too: `python tools/build_codex.py` reads `docs/lore/codex-flavour.md` (never hand-edit `data/vestiges/vestiges/codex/`).

The machine's default Java is 24 and **cannot** build this (`release = 25`); always go through `dev.bat` or set `JAVA_HOME` to the JDK 25.
From Git Bash: `JAVA_HOME=/c/Users/carpe/.jdks/jdk-25.0.4.1+1 ./gradlew build`.

## Working preferences

- Simple words, short messages, one topic at a time; numbered questions; drafts before code. Plan first for anything non-trivial.
- **Commit and push only when asked.** Remote: private repo https://github.com/fattofrencho-png/vestiges (CI builds every push to main). `gh` is not installed.

## Architecture (M0)

- `Vestiges` (main entrypoint), `VestigesCommands` (`/vestiges info | data | config list | config system <id> <bool> | config reload`,
  operator only; later systems add their tools here).
- `config/`: `ServerConfig` (systems on/off + agreed numbers, `config/vestiges/server.json`), `ClientConfig` (HUD toggles,
  `config/vestiges/client.json`), `VestigesSystem` (every switchable system). Pure Java, unit-tested.
- `data/`: `DataType<T>` = one datapack content type (`data/<ns>/vestiges/<type>/*.json`, decoded by a codec, reloaded by `/reload`);
  `VestigesData` lists them. M0 ships `feats` (the 24 approved feats as display text; M5 makes them real).
- `net/VestigesPayloads` + `server/HubNet`: the client asks (`HubRequest`), the server answers with a complete JSON snapshot (`HubData`),
  Hearthbook's pattern. Systems add sections to `HubNet.snapshot`.
- `ModAttachments.PROFILE` (`PlayerProfile`): persistent, copied on death; grows with level, tree, Codex, feats.
- Client: `client/hub/HubScreen` (H; tabs Character, Codex, Villagers, Settlements, Feats, Map, Settings), `client/hud/LookAtHud` (own
  look-at HUD written from scratch, no Jade code) with `LookAtProvider`s registered in `LookAtProviders`; each line can be switched off.
- Pitfalls seen in 26.3: mixin `@Unique` field initialisers did not run for `Player` (create such fields lazily in a getter); no
  `ItemStack` in static initialisers of classes loaded at client start (components are bound later); unit tests can't build item stacks.
- 26.3 API notes: GUI drawing goes through `GuiGraphicsExtractor` (`extractRenderState`), HUD through Fabric `HudElementRegistry`,
  data reloads through Fabric `ResourceLoader.get(PackType.SERVER_DATA)` + vanilla `SimpleJsonResourceReloadListener`.
