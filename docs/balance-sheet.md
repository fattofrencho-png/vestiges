# Balance sheet

Made 2026-11-05 for the balance pass. It reads the design log, the idea notes, the code and the JSON data of `main` (87ceeb1) and the dragons branch `claude/dragons-dbafd7` (d939bca, not merged yet). **Nothing in the game was changed.** Every number here is still a draft.

How it was made:
- The tables in sections 1 and 2 come from `tools/balance_calc.py`. It copies the game's own formulas and reads the loot numbers from `tools/build_loot.py` itself, so the sheet and the generated loot tables cannot disagree.
- `python tools/balance_calc.py --check` proves that every number copied into the script still sits in its source file (88 numbers, 0 drifted today). Run it again after any tuning; if it says DRIFT, this sheet is out of date.
- ASSUMPTION means "not in the repository". The combined play test must confirm it. Nothing was invented as a game number; where the repo has no value, the sheet says "to confirm".
- The game was not started and no test suite was run.

## 0. Read this first

**Can the emerald economy work?** Yes. The numbers show plenty of supply, not a shortage. At the assumed paces a player earns about 50 to 130 emeralds an hour, and the village prices are cheap against that (a median offer costs 2, 6, 12, 30 and 100 emeralds by tier). Even at a quarter of the low pace, a player earns about 17 emeralds an hour at levels 25 to 49, which buys one median offer (12) every hour. The design log says emeralds only come from drops, loot and quests. That is not the whole picture: the code also has the dungeon reward room (4 to 8), the dragons (672 in all) and **emerald ore at the world's emerald sites, up to about 19,000 ore per site**. The real worry is the other way round (too many, flat income, no big sinks) plus the uncapped waystone price. Details: sections 2.4 and 4, risk 5.

**The five biggest risks** (each has a proposal in section 4; none is applied):
1. **The XP wall.** In the open world, kill XP falls to 10% from level 34 on the surface and from level 64 in deepslate. Level 100 would take about 50,900 deepslate kills. Late levels come only from dungeons, and the tier of the next key cannot be chosen (it drifts up by only 0.2 tiers per run).
2. **Dungeon rewards are weaker than the design log says.** The log's dungeon rarity rows are not built (Rare or better is about 4% at tier 1 against 55% in the log, about 31% at tier 26 and up against 95%). Lieutenants and Bosses pay no more emeralds than a zombie.
3. **The player power curve is not in the levels.** A level gives only +1 max health and +2% damage, while mobs gain +15% HP and +6% damage. The log's 20 / 60 / 120 / 250 HP and "6 hits" need the tree and gear to add +21 / +51 / +131 HP and about x1.8 to x2.2 damage. Nothing in the repo shows that they do.
4. **Dragons are softer than dungeon Bosses.** Dragon 6 has 600 HP at its level-24 gate. A same-level dungeon Boss has about 1,200 (witch, assumed base HP). A dragon kill gives less XP than one dungeon run.
5. **Emerald income is flat and the sinks are small**, ore sites bypass the drop economy, and the waystone price has no cap (228 to 559 emeralds to cross a continent or the world).

**Design log against code:** 14 items in section 3 (11 real differences, 3 small or still to measure). The biggest: gear above your level (the log says slows you, the code halves damage and armor), the dungeon rarity rows, key drop tiers, and the player HP table. About 20 other claims were checked and match.

## 1. Every draft number, with its source

Java paths are under `src/main/java/`. "Status" is what the file or the log says; "draft, differs from the log" means the code and the design log disagree (section 3). Dragon rows are read from the dragons branch with `git show`, so their line numbers can move while that branch changes.

| Area | Parameter | Value | Status | Source |
|---|---|---|---|---|
| Mob levels | Mob level share of nearby players' average level | 75% | draft | `dev/vestiges/config/ServerConfig.java:24` |
| Mob levels | Caves layer bonus | +3 | draft | `dev/vestiges/config/ServerConfig.java:25` |
| Mob levels | Deepslate layer bonus (Nether and End count as deepslate) | +6 | draft | `dev/vestiges/config/ServerConfig.java:26` |
| Mob levels | Surface cap | 20 | draft | `dev/vestiges/config/ServerConfig.java:27` |
| Mob levels | Caves cap | 40 | draft | `dev/vestiges/config/ServerConfig.java:28` |
| Mob levels | Deepslate cap | 50 | draft | `dev/vestiges/config/ServerConfig.java:29` |
| Mob levels | Caves = more than 3 blocks under the surface; deepslate = below y 0 | 3 / 0 | draft | `dev/vestiges/level/XpSources.java:99` |
| Mob levels | Over-cap for Warlord (Champion +3, Elite +6) | +10 | draft | `dev/vestiges/level/MobLevels.java:38` |
| Mob levels | Players within this range set the level | 64 blocks | draft | `dev/vestiges/mobs/MobService.java:150` |
| Mob levels | Mob size roll (normal curve, clamp 80-125%) | sd 0.065 | draft | `dev/vestiges/mobs/MobService.java:196` |
| Mob stats | Mob HP per level | +15% of base per level | draft | `dev/vestiges/mobs/MobService.java:220` |
| Mob stats | Mob damage per level | +6% of base per level | draft | `dev/vestiges/mobs/MobService.java:229` |
| Mob stats | Brute power damage | x1.4 | draft | `dev/vestiges/mobs/MobService.java:229` |
| Mob stats | Champion strength (HP, XP) | x2 | draft | `dev/vestiges/mobs/MobData.java:51` |
| Mob stats | Elite strength | x3 | draft | `dev/vestiges/mobs/MobData.java:52` |
| Mob stats | Warlord strength | x5 | draft | `dev/vestiges/mobs/MobData.java:53` |
| Mob stats | Lieutenant strength | x6 | draft | `dev/vestiges/mobs/MobData.java:54` |
| Mob stats | Boss strength (Overlord x20 is in the log, not in the code) | x10 | draft | `dev/vestiges/mobs/MobData.java:55` |
| Mob stats | Damage strength never above | 5 | draft, code only | `dev/vestiges/mobs/MobData.java:62` |
| Mob stats | Warlord spawn rate (deepslate) | 0.2% | draft | `dev/vestiges/mobs/MobService.java:168` |
| Mob stats | Elite spawn rate (caves and deeper; 1.2% in caves, 1.0% in deepslate) | 1.0-1.2% | draft | `dev/vestiges/mobs/MobService.java:171` |
| Mob stats | Champion spawn rate (everywhere) | 3% | draft | `dev/vestiges/mobs/MobService.java:176` |
| Player and XP | Kill XP | 10 + 4 x mob level | draft | `dev/vestiges/level/LevelCurve.java:40` |
| Player and XP | XP from level L to L+1 | kill XP(L) x (8 + 0.9 L) | draft | `dev/vestiges/level/LevelCurve.java:27` |
| Player and XP | Gap rule: full XP down to 5 levels below you | 5 | draft | `dev/vestiges/level/LevelCurve.java:46` |
| Player and XP | Gap rule: +5% per level above you, at most +50% | 5% / 50% | draft | `dev/vestiges/level/LevelCurve.java:48` |
| Player and XP | Gap rule: -10% per extra level below, floor 10% | 10% / 10% | draft | `dev/vestiges/level/LevelCurve.java:50` |
| Player and XP | Max health per level | +1 | draft | `dev/vestiges/level/LevelCurve.java:55` |
| Player and XP | Damage per level | +2% | draft | `dev/vestiges/level/LevelCurve.java:60` |
| Player and XP | Level cap, Paragon XP per level, Paragon soft cap | 100 / 40,000 / 50 | draft | `dev/vestiges/level/LevelCurve.java:15` |
| Player and XP | Death loses (of the level's need) | 5% at level 1 to 15% at 100 | draft | `dev/vestiges/config/ServerConfig.java:32` |
| Player and XP | XP multiplier for the whole server | 1.0 | default | `dev/vestiges/config/ServerConfig.java:36` |
| Player and XP | First visit XP: biome / structure type | 100 / 150 | draft | `dev/vestiges/level/XpSources.java:40` |
| Player and XP | First visit XP: Nether / End | 500 / 1000 | draft | `dev/vestiges/level/XpSources.java:42` |
| Player and XP | Ore XP (coal 2 ... diamond 25, emerald 30, ancient debris 60) | 2-60 | draft | `dev/vestiges/level/XpSources.java:39` |
| Gear and armor | Armor blocks armor / (armor + 20), at most | 85% | draft | `dev/vestiges/mobs/ArmorFormula.java:5` |
| Gear and armor | Gear ladder: level each gear tier is meant for (1 wood/stone/leather, 5 chain, 10 copper, 15 gold, 20 iron, 30 diamond, 40 netherite) | 1-40 | draft | `dev/vestiges/gear/GearLadder.java:42` |
| Gear and armor | Gear above your level: weapon damage | x0.5 (log says: keep full stats, slow the player) | draft, differs from the log | `dev/vestiges/tree/TreeCombat.java:155` |
| Gear and armor | Gear above your level: armor and toughness | x0.5 | draft, differs from the log | `dev/vestiges/gear/GearService.java:131` |
| Gear and armor | Rarity odds per item by loot level (first row: levels 1-10) | 60/35/4.9/0.1/0/0/0 % | draft | `tools/build_loot.py:50` |
| Gear and armor | Enchant table emeralds | ceil(shown cost / 5) | draft | `dev/vestiges/gear/EnchantScale.java:113` |
| Gear and armor | Anvil use costs | vanilla level cost, in emeralds | draft | `dev/vestiges/gear/EnchantScale.java:119` |
| Gear and armor | Tree refund / choice switch | 1 + level/10 emeralds | draft | `dev/vestiges/tree/TreeRules.java:28` |
| Dungeons | Mob level in a dungeon | 12 + 3 x tier | draft | `dev/vestiges/dungeon/DungeonRules.java:18` |
| Dungeons | Lives per player | 3 (tiers 1-9), 2 (10-24), 1 (25+) | draft | `dev/vestiges/dungeon/DungeonRules.java:13` |
| Dungeons | Mob HP per extra player (no cap) | +50% | draft | `dev/vestiges/dungeon/DungeonRules.java:23` |
| Dungeons | Monsters per fight room | 6 to 10 | draft | `dev/vestiges/dungeon/DungeonRules.java:49` |
| Dungeons | Run shape: start, 6 rooms, Lieutenant, 3 rooms, Boss, reward (about 10 minutes) | 13 rooms | draft | `docs/design-log.md:746` |
| Dungeons | Next key after tier N: N-1 / N / N+1 / N+2 | 15 / 55 / 25 / 5 % | draft | `dev/vestiges/dungeon/DungeonRules.java:28` |
| Dungeons | Next key keeps the theme | 60% | draft | `dev/vestiges/dungeon/DungeonRules.java:33` |
| Dungeons | Loot level inside a dungeon = mob level (no cap) | 12 + 3 x tier | draft | `dev/vestiges/dungeon/DungeonService.java:405` |
| Dungeons | Reward room gear rolls (rarity at least Uncommon) | 2 | draft | `tools/build_loot.py:332` |
| Dungeons | Reward room book chance | 35% | draft | `tools/build_loot.py:333` |
| Dungeons | Reward room emeralds | 4 to 8 (not scaled by tier) | draft | `tools/build_loot.py:334` |
| Dungeons | Elytra: first End Boss clear at this tier or higher (the log says 3, docs/ideas/dragons.md says 4) | 3 | draft, docs disagree | `src/main/resources/data/vestiges/vestiges/dungeon_themes/end.json:5` |
| Dungeons | Low key drops: Elite 2%, Warlord 8%, tiers 1-3 | 2% / 8% | draft, log says tiers 1-5 | `tools/build_loot.py:336` |
| Loot | Mob emerald drop chance (player kills) | 16.7% (1 kill in 6) | approved 2026-10-11 | `tools/build_loot.py:307` |
| Loot | Mob emerald count: below loot level 25 / from 25 | 1-2 / 1-3 | draft | `tools/build_loot.py:495` |
| Loot | Mob ingot chance | 20% (1 kill in 5) | approved 2026-10-11 | `tools/build_loot.py:308` |
| Loot | Champion extra: emeralds / gear chance | 2-4 / 10% | draft | `tools/build_loot.py:315` |
| Loot | Elite extra: emeralds / gear / book | 4-8 / 25% / 10% | draft | `tools/build_loot.py:316` |
| Loot | Warlord extra: emeralds / gear / Relic book | 10-20 / 100% / 5% | draft | `tools/build_loot.py:317` |
| Loot | Chest loot bands (loot level 1-4, 5-9, 10-14, 15-19, 20-29, 30-39, 40-49, 50-64, 65-79, 80-89, 90-100) | 11 bands | draft | `tools/build_loot.py:43` |
| Loot | Chest emerald (weight 70 from band 1) and emerald block (weight 12 from band 6) | see 4a | draft | `tools/build_loot.py:162` |
| Loot | Chest: share of gear rolls that are books | 25% | draft | `tools/build_loot.py:304` |
| Loot | Bestiary adds to emerald and ingot chance | +1% (50 kills) / +2% (250) | draft | `dev/vestiges/codex/CodexRules.java:182` |
| Quests | Side quest XP share (first time), emeralds | 0.4 of a level, 6 emeralds | draft | `src/main/resources/data/vestiges/vestiges/quests/side_armorer.json:23` |
| Quests | Side quest comeback: XP share / emeralds / goal growth | 10% / 1 to 3 / +50% | draft | `dev/vestiges/quest/QuestRules.java:14` |
| Quests | Side quest comeback emeralds | max(1, min(3, base / 4)) | draft | `dev/vestiges/quest/QuestRules.java:71` |
| Quests | Side quest comebacks and wait | 5 / 7 days | draft | `dev/vestiges/quest/QuestRules.java:11` |
| Quests | Guide quest rewards: XP 25-300, emeralds 0-8 | 25 quests, 39 emeralds in all | draft | `src/main/resources/data/vestiges/vestiges/quests/choose_your_path.json:17` |
| Villagers | Level bands = price tiers 1-5 | 1, 10, 25, 50, 75 | draft | `dev/vestiges/villager/TradeRules.java:12` |
| Villagers | Price bands per tier (emeralds) | 1-3 / 4-8 / 9-20 / 21-55 / 56-150 | draft | `docs/ideas/villagers.md:43` |
| Villagers | Stock per offer per day: normal / enchanted-grade / luxury | 12 / 3 / 1 | draft | `dev/vestiges/villager/TradeRules.java:37` |
| Villagers | Wandering Trader price rise per tier | +50% of base | draft | `dev/vestiges/villager/TradeRules.java:51` |
| Villagers | Emerald trim discount, per trim (cap 50%) | 2% | draft | `dev/vestiges/gear/GearService.java:52` |
| Waystones | Trip cost | 1 emerald per 250 blocks, minimum 1, no cap | draft | `dev/vestiges/config/ServerConfig.java:34` |
| Waystones | Cost formula (cap decision still open) | max(1, int(distance / 250)) | draft | `dev/vestiges/waystone/WaystoneRules.java:17` |
| World | Emerald ore only at sites: veins per chunk / size / height | 16 / 6 / -32 to 120 | draft | `dev/vestiges/world/EmeraldSites.java:24` |
| World | Emerald sites: radius, most per land, least per world | 128 / 2 / 3 | draft | `src/main/resources/data/vestiges/vestiges/world_plan/trenec.json:8` |
| Dragons | Vanilla dragon health | 200 | draft | `dev/vestiges/dragon/DragonRules.java:11` (claude/dragons-dbafd7) |
| Dragons | Dragon health per dragon after the first | +40% | draft | `dev/vestiges/dragon/DragonRules.java:39` (claude/dragons-dbafd7) |
| Dragons | Dragon damage per dragon after the first | +20% | draft | `dev/vestiges/dragon/DragonRules.java:44` (claude/dragons-dbafd7) |
| Dragons | Dragon health per extra player (up to 4 players) | +50% | draft | `dev/vestiges/dragon/DragonRules.java:49` (claude/dragons-dbafd7) |
| Dragons | Largest hit as share of maximum health | 8% | approved | `dev/vestiges/dragon/DragonRules.java:13` (claude/dragons-dbafd7) |
| Dragons | Summoner level gate (dragon 1: none) | 4 x N | approved | `dev/vestiges/dragon/DragonRules.java:32` (claude/dragons-dbafd7) |
| Dragons | XP reward: one level's worth at the dragon's level | need(4 x N) | draft | `dev/vestiges/dragon/DragonRules.java:78` (claude/dragons-dbafd7) |
| Dragons | Emeralds per dragon (dragon 6 included) | 32 x N | draft | `src/main/resources/data/vestiges/vestiges/dragons/durhos.json:16` (claude/dragons-dbafd7) |

## 2. Calculations

All results use the code's own formulas. Where a vanilla value is needed (zombie 20 HP and 3 damage, sword damage, witch and enderman HP) it is marked ASSUMPTION: vanilla value, to confirm for 26.3, Normal difficulty. The player's hit is the best sword the gear ladder allows at full power, with no enchants, no tree, no crits.

### 2.1 Combat

#### 2.1a Mob stat multipliers by mob level (HP x (1 + 0.15 L), damage x (1 + 0.06 L), before rank and size; the player gets +1 HP and +2% damage per level)

| Level | Mob HP | Mob damage | Zombie HP | Zombie hit | Player damage (levels only) | Player HP (levels only) |
|---|---|---|---|---|---|---|
| 1 | x1.15 | x1.06 | 23 | 3.2 | x1.00 | 20 |
| 10 | x2.50 | x1.60 | 50 | 4.8 | x1.18 | 29 |
| 15 | x3.25 | x1.90 | 65 | 5.7 | x1.28 | 34 |
| 20 | x4.00 | x2.20 | 80 | 6.6 | x1.38 | 39 |
| 25 | x4.75 | x2.50 | 95 | 7.5 | x1.48 | 44 |
| 40 | x7.00 | x3.40 | 140 | 10.2 | x1.78 | 59 |
| 50 | x8.50 | x4.00 | 170 | 12.0 | x1.98 | 69 |
| 75 | x12.25 | x5.50 | 245 | 16.5 | x2.48 | 94 |
| 100 | x16.00 | x7.00 | 320 | 21.0 | x2.98 | 119 |

#### 2.1b A mob of the player's own level (reference mob: zombie, 20 HP, 3 damage, size 1.0)

| Level | Mob HP | Best full-power sword (vanilla value) | Hit: sword x level bonus | Hits to kill | Hit needed for the design's 6 hits | Extra needed from gear, enchants, tree |
|---|---|---|---|---|---|---|
| 1 | 23 | stone sword 5 | 5.0 | 5 | 3.8 | x0.77 |
| 10 | 50 | stone sword (copper: to confirm) 5 | 5.9 | 9 | 8.3 | x1.41 |
| 25 | 95 | iron sword 6 | 8.9 | 11 | 15.8 | x1.78 |
| 50 | 170 | netherite sword 8 | 15.8 | 11 | 28.3 | x1.79 |
| 100 | 320 | netherite sword 8 | 23.8 | 14 | 53.3 | x2.24 |

Ranks at the same level (HP x strength; hits at the same hit as above):

| Level | Champion x2 | Elite x3 | Warlord x5 | Lieutenant x6 | Boss x10 |
|---|---|---|---|---|---|
| 1 | 10 | 14 | 23 | 28 | 46 |
| 10 | 17 | 26 | 43 | 51 | 85 |
| 25 | 22 | 33 | 54 | 65 | 107 |
| 50 | 22 | 33 | 54 | 65 | 108 |
| 100 | 27 | 41 | 68 | 81 | 135 |

#### 2.1c Share of the player's health lost to one hit of a same-level zombie

Player HP here is **levels only** (20 + level - 1, `LevelCurve.bonusHealth`); tree and gear come on top. Armor blocks armor/(armor+20), at most 85%.

| Level | Zombie hit (unarmored) | Player HP (levels only) | armor 0 | armor 15 (iron) | armor 20 (diamond) | armor 60 (heavy) |
|---|---|---|---|---|---|---|
| 1 | 3.2 | 20 | 15.9% | 9.1% | 8.0% | 4.0% |
| 10 | 4.8 | 29 | 16.6% | 9.5% | 8.3% | 4.1% |
| 25 | 7.5 | 44 | 17.0% | 9.7% | 8.5% | 4.3% |
| 50 | 12.0 | 69 | 17.4% | 9.9% | 8.7% | 4.3% |
| 100 | 21.0 | 119 | 17.6% | 10.1% | 8.8% | 4.4% |

Same, with the design log's own player HP (20 / 60 / 120 / 250 at levels 1 / 20 / 50 / 100; the code gives 20 / 39 / 69 / 119 from levels, so the rest must come from tree and gear):

| Level | Design-log HP | HP from levels | Needed from tree and gear | armor 0 | armor 20 |
|---|---|---|---|---|---|
| 1 | 20 | 20 | +0 | 15.9% | 8.0% |
| 20 | 60 | 39 | +21 | 11.0% | 5.5% |
| 50 | 120 | 69 | +51 | 10.0% | 5.0% |
| 100 | 250 | 119 | +131 | 8.4% | 4.2% |

#### 2.1d What the player really meets in the open world (mob level from the layer formula, not the player's level)

| Player level | Layer | Mob level | Zombie HP | Hits to kill (reference sword) | HP lost per hit at armor 20 (levels-only HP) |
|---|---|---|---|---|---|
| 1 | surface | 1 | 23 | 5 | 8.0% |
| 1 | deepslate | 7 | 41 | 9 | 10.7% |
| 10 | surface | 8 | 44 | 8 | 7.7% |
| 10 | deepslate | 14 | 62 | 11 | 9.5% |
| 25 | surface | 19 | 77 | 9 | 7.3% |
| 25 | deepslate | 25 | 95 | 11 | 8.5% |
| 50 | surface | 20 | 80 | 6 | 4.8% |
| 50 | deepslate | 44 | 152 | 10 | 7.9% |
| 100 | surface | 20 | 80 | 4 | 2.8% |
| 100 | deepslate | 50 | 170 | 8 | 5.0% |

Tree ceiling for max health: the Fight tree has 14 flat max-health effects (sum +47 HP) and 17 max-health-percent effects (sum +52%), counting both options of a choice node, so the real ceiling is lower (to confirm: points are 1 per level).

### 2.2 Mob levels

#### 2.2a Open-world mob level against the player's level (no rank; the XP column is the gap rule on the best layer)

| Player | Surface | Caves | Deepslate | Deepslate minus player | Kill XP kept, deepslate | Kill XP kept, surface |
|---|---|---|---|---|---|---|
| 1 | 1 | 4 | 7 | +6 | 130% | 100% |
| 10 | 8 | 11 | 14 | +4 | 120% | 100% |
| 15 | 11 | 14 | 17 | +2 | 110% | 100% |
| 20 | 15 | 18 | 21 | +1 | 105% | 100% |
| 25 | 19 | 22 | 25 | +0 | 100% | 90% |
| 30 | 20 | 26 | 29 | -1 | 100% | 50% |
| 40 | 20 | 33 | 36 | -4 | 100% | 10% |
| 50 | 20 | 40 | 44 | -6 | 90% | 10% |
| 60 | 20 | 40 | 50 | -10 | 50% | 10% |
| 70 | 20 | 40 | 50 | -20 | 10% | 10% |
| 80 | 20 | 40 | 50 | -30 | 10% | 10% |
| 100 | 20 | 40 | 50 | -50 | 10% | 10% |

Elites may go over the layer cap (`MobLevels.overCap`: Champion +3, Elite +6, Warlord +10), so the highest open-world level is 60 (a Warlord in deepslate). Deepslate mobs are at or above the player's level up to player level 26, and below it from there on.

#### 2.2b Dungeon tier -> mob level (`12 + 3 x tier`, no cap), lives, and mob level minus player level (kill XP kept) for five player levels

| Tier | Mob level | Lives | vs level 15 | vs level 25 | vs level 50 | vs level 75 | vs level 100 |
|---|---|---|---|---|---|---|---|
| 1 | 15 | 3 | +0 (100%) | -10 (50%) | -35 (10%) | -60 (10%) | -85 (10%) |
| 2 | 18 | 3 | +3 (115%) | -7 (80%) | -32 (10%) | -57 (10%) | -82 (10%) |
| 3 | 21 | 3 | +6 (130%) | -4 (100%) | -29 (10%) | -54 (10%) | -79 (10%) |
| 5 | 27 | 3 | +12 (150%) | +2 (110%) | -23 (10%) | -48 (10%) | -73 (10%) |
| 10 | 42 | 2 | +27 (150%) | +17 (150%) | -8 (70%) | -33 (10%) | -58 (10%) |
| 15 | 57 | 2 | +42 (150%) | +32 (150%) | +7 (135%) | -18 (10%) | -43 (10%) |
| 20 | 72 | 2 | +57 (150%) | +47 (150%) | +22 (150%) | -3 (100%) | -28 (10%) |
| 25 | 87 | 1 | +72 (150%) | +62 (150%) | +37 (150%) | +12 (150%) | -13 (20%) |
| 30 | 102 | 1 | +87 (150%) | +77 (150%) | +52 (150%) | +27 (150%) | +2 (110%) |
| 35 | 117 | 1 | +102 (150%) | +92 (150%) | +67 (150%) | +42 (150%) | +17 (150%) |

A level-100 player is matched by tier 30 (level 102), and tiers 25 and up have **one life**. Tier 1 (level 15) equals the level of the First Key feat (level 15). Party size multiplies mob HP by 1 + 0.5 x (players - 1), with no cap (2 players x1.5, 4 players x2.5).

### 2.3 XP

#### 2.3a The XP curve (`LevelCurve`)

| Level | Kill XP at this mob level | XP to next level | Same-level kills per level (= 8 + 0.9 L) | Total XP from level 1 to next |
|---|---|---|---|---|
| 1 | 14 | 125 | 8.9 | 125 |
| 10 | 50 | 850 | 17.0 | 4,441 |
| 25 | 110 | 3355 | 30.5 | 35,215 |
| 50 | 210 | 11130 | 53.0 | 210,805 |
| 75 | 310 | 23405 | 75.5 | 639,270 |
| 99 | 406 | 39423 | 97.1 | 1,392,930 |

Level 1 to 100: 1,392,930 XP. After 100 each Paragon level costs 40,000 XP.

#### 2.3b Kills needed to reach a level, by where the player fights (full simulation of the code's rules, no ranks, one level at a time)

| Reach level | Kills, always on the surface | Kills, always in caves | Kills, always in deepslate |
|---|---|---|---|
| 10 | 131 | 83 | 56 |
| 25 | 583 | 453 | 352 |
| 50 | 15,914 | 1,973 | 1,492 |
| 60 | 30,399 | 7,777 | 2,384 |
| 75 | 62,157 | 24,590 | 13,907 |
| 100 | 148,498 | 70,300 | 50,911 |

Per dungeon run at a matched tier (9 fight rooms x 8 monsters + Lieutenant x6 + Boss x10 of kill XP; the reward room and quests are extra):

| Player level | Matched tier | Mob level | XP per run | Levels gained per run |
|---|---|---|---|---|
| 15 | 1 | 15 | 6,160 | 4.09 |
| 25 | 5 | 27 | 11,422 | 3.40 |
| 40 | 10 | 42 | 17,230 | 2.30 |
| 50 | 13 | 51 | 19,774 | 1.78 |
| 75 | 21 | 75 | 27,280 | 1.17 |
| 99 | 29 | 99 | 35,728 | 0.91 |

Level 15 to 100 by matched-tier runs only: about 54 runs (about 9 hours at the design log's 10 minutes per run, 13 hours at the 4 runs per hour assumed below).

Keys decide the tier, and the player cannot pick it. After a clear the next key is N-1 15% / N 55% / N+1 25% / N+2 5% (never below 1), so the tier drifts up by 0.20 per run (+0.35 at tier 1, where the step down is clamped). Simulation (2,000 players, seed 1): the player starts at level 15 with a tier 1 key, always runs the key they hold and clears it; no modifiers, no open-world XP, no quests.

| Reach level | Runs, median | Runs, 90th percentile | Hours at 10 minutes a run (median) |
|---|---|---|---|
| 25 | 4 | 5 | 1 h |
| 50 | 36 | 65 | 6 h |
| 75 | 80 | 126 | 13 h |
| 100 | 123 | 183 | 20 h |

Open-world wall: the best layer is deepslate (cap 50). Kill XP per kill and kills per level there:

| Player | Deepslate mob level | Gap rule | XP per kill | XP to next level | Kills per level |
|---|---|---|---|---|---|
| 40 | 36 | 100% | 154 | 7,480 | 49 |
| 50 | 44 | 90% | 167 | 11,130 | 67 |
| 55 | 47 | 70% | 139 | 13,225 | 95 |
| 60 | 50 | 50% | 105 | 15,500 | 148 |
| 64 | 50 | 10% | 21 | 17,450 | 831 |
| 70 | 50 | 10% | 21 | 20,590 | 980 |
| 80 | 50 | 10% | 21 | 26,400 | 1,257 |
| 100 | 50 | 10% | 21 | 40,000 | 1,905 |

### 2.4 Economy

#### 2.4a What one unit of each source pays (expected emeralds)

| Level band | Example level | Caves mob level | Emeralds per kill | Emeralds per chest roll | Emeralds per dungeon run at the matched tier |
|---|---|---|---|---|---|
| 1-9 | 5 | 7 | 0.41 | 0.34 | needs level 15 |
| 10-24 | 17 | 16 | 0.41 | 0.85 | 25 (tier 2: reward 6 + drops 19) |
| 25-49 | 37 | 31 | 0.50 | 1.67 | 31 (tier 9: reward 6 + drops 25) |
| 50-74 | 62 | 40 | 0.50 | 2.19 | 31 (tier 17: reward 6 + drops 25) |
| 75-100 | 87 | 40 | 0.50 | 2.19 | 31 (tier 25: reward 6 + drops 25) |

Per kill = plain drop roll (16.7% of 1-2, or 1-3 from mob level 25) + elite ranks by spawn rate; the example uses the caves layer. Dungeon monsters roll no ranks, so a Lieutenant or Boss pays the same plain roll as a zombie.

#### 2.4b Income per hour of play (ASSUMED paces, listed under the table)

| Level band | Open world per hour (low - high kills) | Dungeon hour (matched tier) |
|---|---|---|
| 1-9 | 52 - 101 | - |
| 10-24 | 55 - 104 | 98 |
| 25-49 | 70 - 129 | 123 |
| 50-74 | 73 - 132 | 123 |
| 75-100 | 73 - 132 | 123 |

Assumptions (not in the repository): open_kills_low = 120; open_kills_high = 240; chests_per_hour = 6; dungeon_runs_per_hour = 4; monsters_per_fight_room = 8; fight_rooms = 9.

#### 2.4c The prices (villagers sell only; `data/.../villager_trades`, Wandering Trader excluded)

| Price tier | Player levels | Offers (13 jobs) | Cheapest | Median | Dearest | Sum of one of each |
|---|---|---|---|---|---|---|
| 1 | 1-9 | 39 | 1 | 2 | 3 | 78 |
| 2 | 10-24 | 39 | 4 | 6 | 8 | 221 |
| 3 | 25-49 | 39 | 9 | 12 | 18 | 480 |
| 4 | 50-74 | 28 | 22 | 30.0 | 50 | 903 |
| 5 | 75-100 | 13 | 60 | 100 | 140 | 1265 |

Hours of income needed (open world low / high, dungeon hour) to buy one median offer, and one of every offer, of the tier the band unlocks:

| Band | Median price | One median offer (open world) | One of every offer (open world) | Dungeon hour: median h / all h |
|---|---|---|---|---|
| 1-9 | 2 | 0.02 - 0.04 h | 78 -> 0.8 - 1.5 h | - |
| 10-24 | 6 | 0.06 - 0.11 h | 221 -> 2.1 - 4.0 h | 0.06 / 2.3 |
| 25-49 | 12 | 0.09 - 0.17 h | 480 -> 3.7 - 6.9 h | 0.10 / 3.9 |
| 50-74 | 30 | 0.23 - 0.41 h | 903 -> 6.8 - 12.4 h | 0.24 / 7.3 |
| 75-100 | 100 | 0.76 - 1.38 h | 1265 -> 9.6 - 17.4 h | 0.81 / 10.3 |

#### 2.4d Waystone trips (`max(1, int(distance / 250))`, no cap) against the world's own sizes (`world_plan`)

| Trip | Blocks | Emeralds | Compared with |
|---|---|---|---|
| Within a village area | 500 | 2 | 0.02 h of tier-10 dungeon income |
| Across a town | 1,000 | 4 | 0.03 h of tier-10 dungeon income |
| Short expedition | 5,000 | 20 | 0.16 h of tier-10 dungeon income |
| Long trip | 10,000 | 40 | 0.33 h of tier-10 dungeon income |
| Trenec radius (28,000) | 28,000 | 112 | 0.91 h of tier-10 dungeon income |
| Trenec centre to Thornner centre | 57,140 | 228 | 1.86 h of tier-10 dungeon income |
| Trenec centre to Swetren centre | 75,106 | 300 | 2.44 h of tier-10 dungeon income |
| Across the world (Swetren to Restena) | 139,832 | 559 | 4.55 h of tier-10 dungeon income |

#### 2.4e Chest loot bands (`build_loot.py`: the band roll of one chest; gear and books are one pool, the structure's own pools come on top)

| Band | Loot levels | Gear rolls | Book rolls | Emeralds | Diamonds | Rare or better gear/books | Netherite scrap |
|---|---|---|---|---|---|---|---|
| 1 | 1-4 | 0.30 | 0.10 | 0.12 | 0.00 | 0.00 | 0.00 |
| 2 | 5-9 | 0.34 | 0.11 | 0.34 | 0.00 | 0.00 | 0.00 |
| 3 | 10-14 | 0.38 | 0.13 | 0.59 | 0.00 | 0.02 | 0.00 |
| 4 | 15-19 | 0.42 | 0.14 | 0.85 | 0.00 | 0.02 | 0.00 |
| 5 | 20-29 | 0.46 | 0.15 | 1.22 | 0.00 | 0.09 | 0.00 |
| 6 | 30-39 | 0.51 | 0.17 | 1.67 | 0.10 | 0.09 | 0.00 |
| 7 | 40-49 | 0.55 | 0.18 | 2.19 | 0.23 | 0.23 | 0.02 |
| 8 | 50-64 | 0.59 | 0.20 | 2.69 | 0.42 | 0.24 | 0.03 |
| 9 | 65-79 | 0.63 | 0.21 | 3.30 | 0.52 | 0.26 | 0.05 |
| 10 | 80-89 | 0.67 | 0.22 | 3.98 | 0.68 | 0.28 | 0.07 |
| 11 | 90-100 | 0.71 | 0.24 | 4.67 | 0.77 | 0.29 | 0.07 |

#### 2.4f Other emerald sources and sinks in the code

| Kind | What | Numbers | Source / note |
|---|---|---|---|
| Source | Quests (current files; the two test quests left out) | 25 guide quests pay 39 in all, once; 13 side quests pay 78 the first time and 52 more over 4 comebacks (one a week each). Lifetime 169 | QuestRules.emeralds, quests/*.json |
| Source | Dragons 1-6 (dragons branch) | 32 x N each: 32 / 64 / 96 / 128 / 160 / 192 = 672 in total |  |
| Source | Emerald ore sites (world plan) | 16 veins of up to 6 ore per chunk inside a 128-block-radius site (about 200 chunks), 3 or more sites per world: up to ~19,000 ore per site | mining time is the only limit; to confirm in the play test |
| Sink | Enchanting table | ceil(shown cost / 5) emeralds per enchant (at most 6 for a cost-30 enchant) | EnchantScale.tableEmeralds |
| Sink | Anvil | the vanilla level cost, in emeralds (up to 39; 'Too Expensive' from 40) | EnchantScale.anvilEmeralds |
| Sink | Passive tree refund | 1 + level/10 per node (level 100: 11) | TreeRules.refundCost |
| Sink | Villagers, waystones | see 2.4c and 2.4d |  |

### 2.5 Dragons and bosses

#### 2.5a Dragons 1-6 (`DragonRules` and `dragons/*.json` on the dragons branch; vanilla dragon = 200 HP)

| N | Name | Gate (summoner level) | HP alone | HP with 4 players | Damage | 8% hit cap (alone) | Hits at the design's 'level L hit = zombie HP / 6' | Player HP at the dragon's level (levels only) | XP reward (1 level at its level) | Emeralds |
|---|---|---|---|---|---|---|---|---|---|---|
| 1 | Tulva | none | 200 | 500 | x1 | 16 | 38 | 23 | 302 | 32 |
| 2 | Belaz | 8 | 280 | 700 | x1.2 | 22 | 39 | 27 | 638 | 64 |
| 3 | Morst | 12 | 360 | 900 | x1.4 | 29 | 39 | 31 | 1,090 | 96 |
| 4 | Dhars | 16 | 440 | 1,100 | x1.6 | 35 | 39 | 35 | 1,658 | 128 |
| 5 | Ilvig | 20 | 520 | 1,300 | x1.8 | 42 | 39 | 39 | 2,340 | 160 |
| 6 | Durhos | 24 | 600 | 1,500 | x2 | 48 | 40 | 43 | 3,138 | 192 |

Emeralds from dragons 1-6: 672. XP and emeralds follow the data files; dragon 6 pays emeralds although the design notes say 'XP and emeralds only for dragons 1-5' (to confirm).

#### 2.5b Dungeon bosses at the level of the same gate (Boss x10 HP; vanilla base HP are ASSUMPTIONS: witch 26, enderman 40, zombie 20 for the Lieutenant x6)

| Tier | Mob level | Overgrown Boss (witch) | End Boss (enderman) | Lieutenant (zombie x6) |
|---|---|---|---|---|
| 1 | 15 | 845 | 1,300 | 390 |
| 3 | 21 | 1,079 | 1,660 | 498 |
| 4 | 24 | 1,196 | 1,840 | 552 |
| 5 | 27 | 1,313 | 2,020 | 606 |
| 7 | 33 | 1,547 | 2,380 | 714 |
| 10 | 42 | 1,898 | 2,920 | 876 |

### 2.6 What the tables say

- **Same-level fights:** with only level and the best allowed vanilla sword, a same-level zombie takes 5 / 9 / 11 / 11 / 14 hits at levels 1 / 10 / 25 / 50 / 100, not 6. The design's "6 hits" needs the rest of the build to add x0.8 / x1.4 / x1.8 / x1.8 / x2.2 damage. The design log's zombie HP checks (80, 170, 320 at levels 20, 50, 100) are exact.
- **Danger per hit stays flat:** a same-level zombie hit takes about 16% to 18% of the levels-only HP, unarmored, at every level (8% to 9% at armor 20). Level alone does not make the character safer.
- **Open world mobs lag the player:** mob level is 75% of the player's level, plus 3 in caves and 6 in deepslate, with caps 20 / 40 / 50. A player is only level-matched in deepslate up to level 26. However high the player is, the best open-world mob is level 50 (level 60 for a Warlord in deepslate).
- **Dungeons are the XP engine:** one matched-tier run is worth 4.1 levels at level 15 and 0.9 levels at level 99. Level 15 to 100 by runs alone is about 54 runs if the tier always matches, and a median 123 runs (about 20 hours) when the key lottery picks the tier.
- **Dragons are mostly a Level VI book and a Relic:** XP 302 to 3,138 and emeralds 32 to 192 per dragon are small beside one dungeon run (about 6,000 to 11,400 XP and 25 to 31 emeralds at levels 15 to 37).

## 3. Design log against code

Line numbers are in the files named. "Log" means `docs/design-log.md` unless another file is named.

| # | Topic | Design log or notes say | Code or data says | Effect |
|---|---|---|---|---|
| 1 | Gear above your level | Changed 2026-11-03: keeps full stats but slows you by how far above your level it is (`design-log.md:242`). Line 931 still describes the old half rule. | Half damage (`tree/TreeCombat.java:156`) and half armor and toughness (`gear/GearService.java:131`). The change was never coded. | A player in gear one tier up is weaker than the log says. |
| 2 | Dungeon rarity rows | Three dungeon rows: tiers 1-10 (Rare 35%, Epic 16.5%), 11-25, 26+ (Epic 40%, Legendary 20%, Relic 5%) (`design-log.md:571-573`). | Only the four loot-level rows (`tools/build_loot.py:49-54`, comment: "Dungeon rows come in M8"). Dungeon gear uses the loot level of the tier, with Uncommon as the lowest roll (`build_loot.py:331`). | Rare or better: about 4% at tier 1, 31% at tier 10 and up, against 55% and 95% in the log (risk 2). |
| 3 | Low key drops | Elites and Warlords sometimes drop keys of tiers 1 to 5 (`design-log.md:755`, `docs/ideas/dungeons.md:34`). | Elite 2% and Warlord 8%, tiers 1 to 3 only (`tools/build_loot.py:335-336`). | The log gives no chance; tiers 4 and 5 can never drop. |
| 4 | Player HP | About 20 / 60 / 120 / 250 HP at levels 1 / 20 / 50 / 100 (`design-log.md:1150`). | Levels give +1 max health each (`level/LevelCurve.java:55`): 20 / 39 / 69 / 119. The Fight tree has 14 flat HP effects (+47 at most, both choice options counted) and 17 percent effects (`data/vestiges/vestiges/trees/fight.json`). | Needs +21 / +51 / +131 from tree and gear (risk 3). |
| 5 | Elite spawn rate | Elite 1%, caves and deeper (`design-log.md:521`). | 1.0% in deepslate but 1.2% in caves, because the Warlord band is not used there (`mobs/MobService.java:171`). | Small. |
| 6 | Boss damage | Lieutenant x6, Boss x10, Overlord x20 "strength" (`design-log.md:528`, `1151`). | HP and XP use the full strength. Damage is capped at x5 (`mobs/MobData.java:62`), marked "a draft for the balance pass" in the code. Overlord does not exist in the code (after the MVP). | A Boss has 10x HP but only 5x damage. Decide whether that is intended. |
| 7 | Where emeralds come from | "Emeralds only come from drops, loot and quests" (`design-log.md:25`, `docs/ideas/villagers.md:56`). | Also the dungeon reward room 4-8 (`tools/build_loot.py:334`), dragons 32 x N (dragons branch), and emerald ore at world sites, "0 to 2 per continent, at least 3 in a world" (`docs/world/world-log.md:26`, `world/EmeraldSites.java`). | The economy check must include ore (section 2.4, risk 5). |
| 8 | Dragon 6 rewards | "Emeralds and XP for dragons 1 to 5, a Relic for dragon 6" (`design-log.md:27`). The notes say dragon 6 gets "the same, plus" the Relic (`docs/ideas/dragons.md:99`). | Dragon 6 pays 192 emeralds and 1 level of XP (dragons branch `dragons/durhos.json`). | To confirm which is meant. |
| 9 | Reward room emeralds | The reward room holds gear, books and the next key (`design-log.md:750`, `docs/ideas/dungeons.md:61-62`). | It also pays 4 to 8 emeralds at every tier (`tools/build_loot.py:334`). | Not in the spec; flat across tiers. |
| 10 | Elytra tier | End Boss clear at tier 3 or higher (`design-log.md:27`). | `docs/ideas/dragons.md:198` says tier 4 or higher "to confirm". The theme file says 3 (`data/vestiges/vestiges/dungeon_themes/end.json`). | Docs disagree with each other. |
| 11 | Waystone price cap | Cost 1 per 250 blocks, minimum 1 (`design-log.md:387`); the cap is an open decision (`design-log.md:13`). | No cap (`waystone/WaystoneRules.java:17`). | 228 to 559 emeralds for continent-wide trips (risk 5). |
| 12 | Villager price bands | 1-3 / 4-8 / 9-20 / 21-55 / 56-150 (`docs/ideas/villagers.md:43`). | Villager offers fit the bands (1-3, 4-8, 9-18, 22-50, 60-140). The Wandering Trader's base prices reach 20 at tier 1 (nautilus shell) and grow +50% per tier. | Small. Tier 5 has only 13 luxury offers (stock 1 a day), tier 4 only 28 (stock 3). |
| 13 | Dungeon rooms and time | 13 rooms, about 10 minutes (`design-log.md:746`). | Not measurable without the game (the run is built from `DungeonRunner`). The income tables assume 4 runs an hour. | To confirm in the play test. |
| 14 | Party scaling | +50% mob HP per extra player in dungeons (`design-log.md:747`). | No cap (`dungeon/DungeonRules.java:23`); the dragons cap at 4 players (dragons branch). | 8 players face x4.5 HP in a dungeon. |

Checked and **consistent** (log = code): mob HP +15% and damage +6% per level (`mobs/MobService.java:220,229`); zombie HP 80 / 170 / 320 at levels 20 / 50 / 100; kill XP 10 + 4 x mob level; XP per level (8.9 same-level kills at level 1, 97.1 at level 99); the gap rule; layer bonuses 3 and 6 and caps 20 / 40 / 50; over-cap +3 / +6 / +10; rank strengths x2 / x3 / x5 / x6 / x10; Champion 3% and Warlord 0.2%; the armor formula and the 85% cap; dungeon mob level 12 + 3 per tier; lives 3 / 2 / 1; next-key odds; waystone 1 per 250 blocks; villager level bands; stock 12 / 3 / 1; dragon health +40% and damage +20% per dragon, the 8% hit cap, the 4 x N gate, 2,040 HP for dragon 24; mob emerald drop chance 16.7% and ingot chance 20%.

## 4. The five biggest balance risks

Each proposal is a **PROPOSAL, not applied**. The author decides.

### Risk 1. The XP wall and the key lottery
- **Numbers:** kill XP is kept at 100% while the mob is up to 5 levels below you, then falls 10% per level, to a floor of 10% (table 2.3). On the surface (cap 20) that is 50% at level 30 and 10% from level 34. In deepslate (cap 50) it is 90% at level 50, 50% at level 60 and 10% from level 64, where one level needs 831 kills (1,905 at level 100). Reaching level 100 by deepslate kills alone is about 50,900 kills.
- **Dungeons carry the late game:** a matched-tier run gives 0.9 to 4.1 levels. But the next key tier cannot be chosen: it drifts up by 0.2 tiers per run (0.35 at tier 1). Median runs to reach levels 25 / 50 / 75 / 100: 4 / 36 / 80 / 123 (90th percentile 183). A level-50 player holding a tier 5 key gets 10% XP. From tier 25 (matched at level 87) there is one life.
- **Why it matters:** if the open world is meant to carry players only to about level 50, this is fine. If players are expected to feel progress to level 100 outside dungeons, they cannot.
- **PROPOSAL A (one number):** raise the deepslate cap from 50 to 80 and the caves cap from 40 to 60 (`config/ServerConfig.java:28-29`). The wall then moves from level 64 to about level 85.
- **PROPOSAL B (keys):** let the portal accept any tier up to the highest tier the player has cleared. This reverses the agreed "difficulty = key tier" rule, so it needs a talk first; the Gate tree after the MVP is the planned fix.

### Risk 2. Dungeon rewards are weaker than designed
- **Numbers:** the log wants Rare or better gear at 55% (tiers 1-10), 80% (11-25) and 95% (26+). The code rolls by the loot level only: Rare or better is about 4% at tier 1 (loot level 15), about 31% from loot level 36 (tier 8) on, with Uncommon as the floor. From tier 26 up a Relic is 0.1% against 5% in the log, and Legendary 0.5% against 20%. A Lieutenant or a Boss pays 0.25 to 0.33 emeralds on average, the same plain roll as a zombie, while a Warlord pays 15. The reward room pays 6 on average at every tier.
- **PROPOSAL A:** build the three dungeon rows of the log into `RARITY_ROWS` and key them by tier (`tools/build_loot.py:49-54`, plus the roll in `loot/LootGear.java`), so tiers 1-10, 11-25 and 26+ use the log's rows.
- **PROPOSAL B:** give the Lieutenant the Elite emerald row (4 to 8) and the Boss the Warlord row (10 to 20) by adding ranks 4 and 5 to `ELITES` in `tools/build_loot.py:314-318`.

### Risk 3. The player power curve is not in the levels
- **Numbers:** a same-level zombie takes 15.9% of a level-1 player's HP per hit and 17.6% of a level-100 player's (levels-only HP, no armor); with armor 20 it is 8.0% and 8.8%. The log's "15 / 7 / 5 / 4%" needs 60 / 120 / 250 HP at levels 20 / 50 / 100. The code gives 39 / 69 / 119, so tree and gear must add +21 / +51 / +131. The whole Fight tree offers at most +47 flat HP and +52% (counting both options of every choice node, so the real ceiling is lower). For damage, a same-level mob needs x1.8 at levels 25 and 50 and x2.2 at level 100 beyond the best allowed vanilla sword to reach 6 hits.
- **PROPOSAL:** +2 max health per level instead of +1 (`level/LevelCurve.java:55`): 58 / 118 / 218 HP at levels 20 / 50 / 100. That matches the log's 60 and 120 without the tree, and gives 5.1% per zombie hit at level 50 with armor 20 (the log says about 5%). It changes the log's line 513 ("+1 max health"), so it needs the author's OK. Judge the damage side only after the play test shows what tree and enchants add.

### Risk 4. Dragons are softer than dungeon Bosses
- **Numbers:** dragons 1 to 6 have 200 / 280 / 360 / 440 / 520 / 600 HP alone (500 to 1,500 with four players). A dungeon Boss is x10 HP on top of the level formula: an Overgrown Boss (witch, assumed 26 base HP) has 845 HP at tier 1 (level 15) and 1,196 at tier 4 (level 24, the level of dragon 6's gate). The 8% hit cap (16 to 48 a hit) is hardly reached. A dragon kill gives 302 to 3,138 XP (one level at the dragon's level), less than one dungeon run (6,160 at level 15, 11,422 at level 25), and 32 to 192 emeralds.
- **PROPOSAL (data and one number):** raise the health step from +40% to +80% per dragon (`DragonRules.healthMultiplier`, and the `health` field of the six dragon files): 200 / 360 / 520 / 680 / 840 / 1,000. Dragon 6 then matches a same-level Boss (about 1,200), and dragon 24 (3,880) matches a level-96 Boss (about 4,000). Damage (+20% per dragon) already follows the mobs (x2.0 against x2.44 at level 24). Set `xp_levels` to 3 so dragon 6 pays about 9,400 XP. The dragons branch belongs to the dragons chat, so hand these to it; do not edit here.

### Risk 5. Emeralds: flat income, small sinks, ore sites, no waystone cap
- **Numbers:** income is flat. A kill in the caves layer pays 0.41 emeralds before mob level 25 and 0.50 from level 25 (plain roll 16.7% of 1-2, then of 1-3, plus elite ranks). A dungeon run pays 25 (tier 2) to 31 (tier 9 and up); a dungeon hour is about 98 at tier 2 and 123 from tier 9 on. Chests add 0.1 to 4.7 emeralds a roll by band. All quests together pay 169 over a whole game. The sinks are small: the enchant table at most 6 per enchant, the anvil at most 39, a tree refund 11 at level 100, village offers at most 140. The only big sink is the waystone: 112 emeralds across Trenec's radius, 228 to Thornner, 300 to Swetren, 559 across the world.
- **Ore sites:** each chunk in a site gets 16 veins of up to 6 ore, in a 128-block radius, for 3 or more sites per world. That is up to about 19,000 ore per site (200 chunks x 16 veins x 6; to confirm, the site shape comes from the world function), one emerald each, before Fortune. How many a player mines per hour is **to confirm** in the play test; if it is above about 100 an hour, ore alone out-earns every other source.
- **PROPOSAL A (waystone cap):** cap the price at 40 emeralds (10,000 blocks, about 20 minutes of dungeon income) so a long trip is a cost and not a wall. This is the open "waystone price cap decision" in the MVP list.
- **PROPOSAL B (ore):** measure first. If the play test shows ore out-earning the rest, cut `VEINS_PER_CHUNK` from 16 to 4 (`world/EmeraldSites.java:24`).
- **PROPOSAL C (sinks):** none until the play test shows emeralds piling up; the Banker and the other sinks come after the MVP.

## 5. Not verified, to confirm

- Every vanilla reference value (zombie 20 HP and 3 damage, sword damage 4 / 5 / 6 / 8, witch 26 and enderman 40 HP, Normal difficulty) and the copper sword's damage (the table uses stone's 5 at level 10).
- The paces: 120 to 240 kills an hour in the open world, 6 chests an hour, 4 dungeon runs an hour, 8 monsters per fight room (the middle of 6 to 10). They are assumptions; the design log only says a run takes about 10 minutes.
- The structure chests' own themed pools (from RPG Craft) may hold emeralds; chest numbers count only the loot-band roll.
- How much damage, HP and speed the tree, enchants and trims add (needed for risk 3), and how `min_rarity: uncommon` treats a Common roll.
- How many emerald ore blocks a player can mine in an hour at a site.
- Dragon damage against the player (the vanilla dragon's attack values are not in the repo).
- The Overlord rank, tier-10 vanilla bosses, key modifiers, the Gate tree, workers, the Banker and caravans are not built, so they are not in these numbers.
- The dragons branch moves; re-run the script after it merges.

## 6. Re-running

```bat
python tools\balance_calc.py             rem all calculation tables
python tools\balance_calc.py --registry  rem the parameter table (section 1)
python tools\balance_calc.py --check     rem proves the copied numbers still match their files
```
