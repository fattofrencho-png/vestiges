# Vestiges — Combined Testing Plan (draft, 2026-11-03)

Goal: test everything built so far (Map and waystones, M1 to M5, quests, A1 switches, A2 first parts) with **as little work from JB as possible**.
Claude runs the checks, reads the screenshots and the game log, and fixes what breaks. JB only does what needs a human (feel, sound, controls, a second player).

## The sketch

```
 LAYER 1  Unit tests (JUnit, exist: 14 classes)          ~1 min    Claude, `dev test`
    │     numbers, codecs, rules, generated JSON
    ▼
 LAYER 2  Auto-check world (NEW, built on the existing   ~5 min    Claude, `dev runClientGameTest`
    │     client game test): a real world, a real          per       one suite per run
    │     player, real commands. Each check writes         suite
    │     PASS / FAIL to build/run/.../report.md
    │       ├─ command sweep   every /vestiges command, /reload, switches on/off
    │       ├─ travel          tp to structures, open chests, map fills in
    │       ├─ mobs & loot     summon, rank, damage maths, 1,000-roll tests
    │       ├─ inventory       racks, Bank, shulker, sort, survives death + re-join
    │       ├─ codex & feats   break/kill/visit → stage goes up
    │       └─ settlements     place markers, rules, finish, renown
    ▼
 LAYER 3  Screenshot review (exists: ScreenshotTour)      ~5 min    Claude looks at every PNG
    │     layouts, text cut off, colours, dark theme
    ▼
 LAYER 4  Log scan: latest.log must have no Vestiges     seconds   Claude
    │     errors, no "unknown attachment", no loot-table errors
    ▼
 LAYER 5  JB's guide (only what a machine can't judge)   ~30 min   JB, one page, copy-paste commands
              feel, sound, controls, 2nd player, performance
```

## Why this shape

- **Existing solution first.** `src/gametest/.../ScreenshotTour.java` already creates a world through the real Create World screen, runs server commands, simulates
  clicks and saves screenshots. I extend it with assertions (a new `AutoCheck` class next to it) instead of adding a new tool. JUnit stays for pure maths.
- **One suite per run.** The game stalls when a world closes (known, noted in CLAUDE.md) and a run must stay under 5 minutes. So suites run one at a time in the
  background with a time limit, are stopped once the report file is written, and each suite can be re-run alone.
- **Claude reads the screenshots.** Layout problems are caught without JB opening the game.
- **Failures:** at most 3 fix attempts per problem, then I stop and report what I tried.

## Layer 2 suites: what is checked automatically

| Suite | Checks (all by Claude) | Not covered here |
|---|---|---|
| **S1 Sweep** | Every `/vestiges` command runs without error (info, data, give, feat, quest, settlement, level, xp, tree, counter, mob, codex, loot, config); `/reload` keeps all data; each of the switches off then on: vanilla behaviour returns when off (vanilla chest tables, unlevelled mobs, normal enchant caps) | |
| **S2 Map and waystones** | Give and place waystones, travel between them, price and cap rules, no-go places; teleport to ~12 spread points and biomes (desert, ocean, mountains, nether, end), check the map region fills with non-empty colours; waypoint list saves | How the minimap *feels* |
| **S3 Levels, trees, gear** | `level set`, `xp add` cross level-ups; tree points spend and reset; gear above your level is halved (armor, damage, speed, shield); rarity rolls; enchants rescaled I–VI with the datapack on and off; anvil IV + IV = V; table cost | |
| **S4 Inventory** | 23 extra slots hold and keep items; shift-click goes to the right rack; Bank takes emeralds (64 / 1); totem slot saves from death; arrows slot feeds bow and crossbow; elytra slot glides; sort and move-all; shulker box in inventory; **everything survives death and leaving + re-entering the world** | Mouse-wheel order, drag feel |
| **S5 Mobs** | Summon every monster type: level, rank, size, powers set once and saved; health and damage match the formulas; armor formula (85% cap); `mob rank` changes; kill XP; layer subtitle on entering caves; babies take size of nearby adults | Look of glow, bars, damage numbers |
| **S6 Loot** | `loot test` at every level band: rarity split matches the odds; `loot chest`; **teleport to real structures** (`/locate`: village, mineshaft, dungeon, stronghold, ancient city, bastion, fortress, trail ruins, desert temple, shipwreck, buried treasure, abandoned camp), open each chest: loot is filled, per-player copy, sneak-break; dispensers, bonus chest and trial vaults stay vanilla; mob drops follow the mob's level | |
| **S7 Codex and farming** | Break blocks (placed blocks don't count), kill, breed, visit biomes by tp, find structures: stage rises and bonus points are paid; `codex set/reset`; cluster harvest only on Discovered blocks | Toast look |
| **S8 Feats and settlements** | Place Bell, Banner, Ward Lantern, Gold Block: every placement rule (no fights, 2,000 apart, no overlap, limits); `settlement finish`; protection (a second fake-player-free check by commands and block events); renown counts; markers return to owner | Real 10-minute timers (run sped up) |
| **S9 Quests and A1/A2** | Quest start, steps, hand-in, kill, reward, standing; welcome page presets; crafting table slot and 3x3 grid; Bank move; shulker items | |
| **S10 Restart and persistence** | Save world, leave, re-enter: profile, Codex, tree, quests, settlements, waystones, racks all still there | |

After each suite: screenshots where a screen is involved, log scan, then one line per check in the report.

## Layer 5: what JB still tests (one page, written after Layers 1 to 4 pass)

Only things a machine cannot judge. Guide will start with a **kit block** (gear, effects, time, items) and then numbered steps, each with commands to paste:

1. **Feel of the inventory:** mouse-wheel order (belt → 1-9 → Tool → Weapon), Ctrl + wheel in a rack, Alt + wheel offhand, drag and drop, the recipe button placement.
2. **Combat feel:** fight 3 elites at your level, check bars, glow, damage numbers are readable, Brute / size variation looks right, powers are fun not unfair.
3. **Sound, particles, animation:** chest sparkles, Relic flicker, elytra wings on another player, totem save.
4. **Map feel:** minimap position and speed, zoom, performance while flying fast over new terrain (FPS).
5. **Second player (or LAN):** protection in a settlement, per-player loot copies, wings visible, both players' map pins.
6. **Real instance:** the `modtesting` ATLauncher instance with the mod jar (`dev deploy`), a quick 15-minute free play to see nothing feels broken next to vanilla.

If a Layer 2 check cannot be automated after 3 attempts, it moves to this list with its commands.

## Progress

- **Day 1 done (2026-11-03):** Layer 1 passed (83 JUnit tests, 0 failures). Harness built: `AutoCheck` + `Checks` in `src/gametest`, run with `tools/run_suite.ps1 <suite>`. **S1 Sweep passed: 413 checks, 0 failures** (42-63 s per run, run twice). Game log has no Vestiges errors.

- **Day 2 done (2026-11-03):** S2 Map and waystones (43 checks), S3 Levels, trees, gear (48) and S4 Inventory (60) all pass; the four suites together (with the sweep) run in about 155 s, 564 checks, 0 failures. Findings to confirm with JB: (1) turning a switch off also turns off what needs it, and turning it back on does not bring those back (by design?); (2) the arrows slot holds 64 at the default stack size, 99 only with the 99 option; (3) damage numbers and the visual look of the map are still for Layer 3 and JB.
- **Day 3 done (2026-11-03):** S5 Mobs (126 checks), S6 Loot (204), S7 Codex and farming (69) and two structure runs (30 + 30: village, desert temple, shipwreck, buried treasure, trail ruins, mineshaft; stronghold, ancient city, abandoned camp, bastion, nether fortress, trial chambers) all pass. Run `mobs,loot,codex,structures,structures2` in about 117 s; the four older suites still pass (564 checks, 150 s). Suite names: `mobs`, `loot`, `codex`, `structures`, `structures2` (the last two open real chests found with `/locate`).
  Findings to confirm with JB: (1) the comment says Champion is 3% anywhere, but on the surface it is 4.2% (the Elite and Warlord share is added to Champion); in deepslate it is 3.0% / Elite 1.0% / Warlord 0.2% as agreed. (2) The Warlord's "gear" drop can be a utility tool (shears, brush, flint and steel, fishing rod), enchanted but not armor or a weapon. (3) Banded loot replaces the vanilla pools of a chest table; it does not add to them (dungeon chest: 6.7 stacks banded vs 7.5 vanilla). (4) Vanilla's trial chambers `intersection_barrel` table logs "Couldn't set damage of loot item compass" (vanilla's own table, not Vestiges). (5) Not tested: a second player's own copy, the Lancer cluster-harvest power (only the Codex gate), whole-entity save and reload of a levelled mob (data codec round trip only), the Nether and End mob levels, the dungeon (monster room) as a located structure (its table is covered by the table sweep and appears in many structures).
- **JB's answers (2026-11-03):** the arrows slot holding 64 by default is fine; ascendancies/keystone rivals, fights and second-player waystone cases are dropped from the plan (not tested). Switch grouping (group and sub-group for toggles that need each other) is a new design item, see the design log once agreed.
- **Harness lessons:** a suite must never leave the survival player falling or standing in the block it places into; each suite starts with empty pockets and an empty Bank; use `T.goTo` (loads the column first, then teleports).

- **Switch groups built (2026-11-03):** parents and children, remembered children, group "Loot and enchants". 88 JUnit tests pass (5 new); all nine auto-check suites pass again (sweep 413, map 43, levels 48, inventory 60, mobs 126, loot 204, codex 69, structures 30 + 30); `groups` run (`VESTIGES_SUITE=groups`) saves screenshots of every group on the welcome page. One run of the second batch died at game start with a Mixin error for `KeyboardInputMixin` (`moveVector` not located); the rerun was clean, so it looks transient (to watch).

- **Day 4 done (2026-11-03):** three new suites, all pass, and together in one world (107 checks, 48 s): `settlements` (S8, 36 checks: Bell, Banner, Ward Lantern and Gold Block placement rules, 2,000-apart Bells, overlaps, setting up with `settlement finish`, renown from player-placed blocks, Gold Block chunk loading, markers returning to the owner, a stranger blocked from placing, breaking, chests and the Bell while gates stay usable, the switch, the fight rule), `quests` (S9, 45 checks: guide quest with a level step, harvest steps with an item reward, kill and elite steps, the offer gate, admin start/complete/reset, standing and its cap, the switch; plus the A2 crafting grid: 2x2 without a table, 3x3 with it, extra slots refuse items without a table) and `persistence` (S10, 26 checks: a player's data is written the way a save does, wiped and read back: level, tree counters, Codex, feats, running quest, racks, table slot, Bank; after a forced save the world folder holds the settlements, waystones, profile, racks and Bank). Run with `toolsun_suite.ps1 settlements,quests,persistence`.
  Harness lessons: a block cannot be placed into the player's own space (put markers beside the pad's centre); `server.runCommand` has no player, so use the service calls (`QuestService.adminReset`) for per-player admin; `save-all` exists only on dedicated servers, use `MinecraftServer.saveEverything(true, true, true)`; do not leave the shell inside `buildun\clientGameTest` (Gradle deletes it before each run).
  Not covered (for JB's guide or later): talking to a real villager and hand-in of "bring" steps, the offer and choice screens, homes (beds) in renown, real 10-minute timers, a second player (membership, shared quests, protection from a real stranger), leaving and re-entering the world for real (the test tool stalls on close; world data is only checked on disk), chunk data (placed blocks, forced chunks), the Bank move and shulker items beyond S4, the screenshot review (Layer 3) for Day 4 screens.

- **Day 4 screenshot review (2026-11-04):** the tour ran in 235 s (109 shots, no Vestiges errors in the log; its usual "finished while a server is still running" stall at the end). Looked at: quest tracker, Quests tab, offer and choice books, Settlements tab (dark, light, small, wide), the welcome page (Always on, World with the switch groups, Comfort), the no-table inventory, the Features screen. All readable, nothing cut off, switch groups indent correctly (Feats > Settlements > Workers, Feats > Waystones, Villagers > Trade system).
  Findings: (1) `full_inventory` (the with-table inventory, A2's 3x3 look) is unusable: the tour's survival player is at 1 heart with a lava overlay and the screen is not open, so that look was not reviewed (fix the tour: heal and clear fire before the shot, or take it before the lava scene); the lava scene also sits behind every later shot. (2) Wide window (1920 px at scale 1): on the Settlements tab, "Name it", the player box and "Add member" sit alone at the top right, far from the text they belong to (for the UI room). (3) The offer page in the tour shows a hard-coded "50 XP" while the Quests tab shows the real "150 XP" for the same quest: a tour sample, not a bug. (4) The design says Workers also shows "needs Villagers"; the welcome page shows only "later" (confirm it is meant for later). Test quests ("Test choice") show up in the Quests tab of a Full RPG world, which a release build should hide (to confirm).

- **Day 5, Claude's part done (2026-11-04):** all 12 suites rerun after the camera work and pass (sweep 413, map 43, levels 48, inventory 69, mobs 126, loot 204, codex 69, structures 30 + 30, settlements 36, quests 45, persistence 26 = 1,139 checks, 0 failures; the end-of-run "finished while a server is still running" error is the known stall). Added 9 shulker checks to S4 (open, locked slot, write-back, rename, shift-click, stack of 2, reopen, removal invalidates, non-box); the plan had listed them but the code had none. The tour's with-table inventory shot is fixed (healed, off the lava, survival) and looks right. Camera shots: the view blends in inside the zone, WASD moves diagonally, and it returns to normal outside. The harness now mutes the game sound. JB's guide: `docs/jb-test-guide.md`.

## Order and time (at 1-2 hours a day; estimates to confirm)

1. **Day 1:** Layer 1 run; build the `AutoCheck` harness + S1 Sweep + log scan. First report.
2. **Day 2:** S2 Map, S3 Levels/gear, S4 Inventory.
3. **Day 3 (done):** S5 Mobs, S6 Loot (structures), S7 Codex.
4. **Day 4 (suites done 2026-11-03):** S8 Settlements, S9 Quests/A2, S10 Persistence all pass; screenshot review (Layer 3) still to do.
5. **Day 5:** write JB's guide from what remains; JB runs it (~30 min); fixes; then the milestones are ticked in PROJECT.md.

## Needs from JB

- **Nothing to start.** Default: no dedicated server (it needs `eula.txt`, which only JB accepts). Everything runs in the in-process test server.
- Optional: accept the EULA yourself if you want a dedicated-server smoke test (join, sync of switches) added later.
- This pauses new features for these days, as the MVP order intends ("test the WIP commits" is the current next step).

## Not covered / risks

- A dedicated-server-only bug or a two-player bug will only show in Layer 5.
- The test client is the real game, so a test can still stall; the 5-minute limit and per-suite reruns handle that.
- Anything I cannot assert by code (taste, balance) is left to JB; I will not call a balance number "tested" because it ran without error.

## Suites added 2026-11-05

Run with `powershell -File tools\run_suite.ps1 <name>` (5-minute limit, muted).

- `atlas-vanilla`: a vanilla world with the mod installed names no places (the Create World screen is used).
- `atlas-vestiges`: the Vestiges world type is in the list, selected on the Create World screen, created; continents arrive named, every label stands on its continent, spawn is on Trenec, Codex places keep their continent, the map screen opens. One world per run.
- `villagers`: names and profiles, the library, right-click opens our screen, trades by level band (locked, price, daily stock, shift-buy, sold out), the Wandering Trader, a quest through the screen, the Trade system switch, every offer is a real item.
- `grave`: a real death makes a gravestone, timer off gives no expiry, Gravestones off drops like vanilla.
- `dungeons`: the key, the portal frame (right size and blocks, switch off, broken), going in, 13 rooms built two ahead, doors that close and open, all Overgrown rooms walked, the Lieutenant and Boss, caches and rewards (own copy, the next key), Elite and Warlord key drops, unbreakable walls, both exit frames, plots cleared before reuse, lives (dying, falling), the First Key feat.
- `dungeons2`: the Trial and End runs room by room, the End's lock (Dragonslayer feat), void gaps, the one-time elytra, and that every block and monster id in the theme data is real. Run the two suites one after the other (each needs about 2 to 3 minutes).

## One command for everything (2026-11-05)

`powershell -File tools\run_all.ps1` runs the unit tests and then every real-game suite, grouped into batches of one game run each (core, gameplay, structures, villagers, dungeons, themes, atlas-vanilla, atlas-vestiges). It writes `build\run\all-report.md` (a table of every suite with passed and failed counts, then the list of failures) and copies each batch's reports and screenshots to `build\run\all\<batch>\`. A batch that fails is rerun once; if the rerun passes it is marked "flaky" (a timing problem in a check). Exit code 0 means all passed. Options: `-Only core,dungeons` (some batches), `-SkipUnit`, `-NoRetry`.

**New suites go into a batch:** when a suite is added to `AutoCheck.java`, add its name to the right batch in `tools\run_all.ps1`, so the one command keeps covering everything.

**What stays human:** the guides `docs/jb-test-guide.md` and `docs/jb-test-guide-dungeons.md` hold only what a machine cannot judge: feel, sound, controls, the look of the screens, a second player, performance and balance.
- `mending` (batch `gear`): the reworked Mending on the real server with a survival player. A Mending tool or armor piece that would break stays at 1 durability, flagged broken, with the enchant gone; a broken pickaxe mines at hand speed, harvests nothing and can't be used; broken armor gives no stats; hitting a broken item again does not break it; repairing ends the state, and a repaired item can take Mending again; gear without Mending breaks as in vanilla; an XP orb is picked up but repairs nothing; with the Gear switch off everything is vanilla. 19 checks. Not covered: the anvil's material and emerald repair, the warning message and sound, the red tooltip line (for the play test).

**Running suites while other chats run games (2026-11-05):** `tools\run_suite.ps1` used to end every run by stopping all game and Gradle processes on the machine, which killed other chats' runs and builds (a run in another worktree ending killed this one two seconds later). It now stops only the processes of its own folder (commit `695fc88`); a worktree needs that script (merge main) for the same safety. If a batch shows "no report" with a run time under the limit, suspect a kill from outside and rerun it alone.
