# Vestiges (RPG Mod) — Design Log
*Every topic below was agreed with you one at a time. Approving this starts with the tree review page; no code is written until you sign off on the tree.*

## Vision (agreed)
- **The character is the core.** Explore, fight and build like vanilla, then go on runs.
- **"Simple but still interesting" base game.** You go looking for new blocks, then new areas. **Hard content is opt-in, through dungeons of various difficulties.**
- Automate the repetitive parts (farming, mining, crafting), but **earn** the automation through level and discoveries.
- Make survival more interesting, and rare building blocks (coral and the like) less painful to get.
- **One fully integrated mod.** Every system feeds the others; nothing is a free shortcut.
- **Vanilla-style rule:** boosting existing vanilla features comes first. Rare new things are allowed only if they look and feel like Mojang made them. No classes.
- **Anti-exploit rule:** anything slightly unvanilla must be **earned** (feats, levels, discoveries) and **not exploitable**.

## Foundation ✅
- New Fabric 26.3 mod in `Minecraft/RPG Mod/`. It absorbs Hearthbook and ports RPG Craft to Java.
- Server-authoritative, for both single-player and multiplayer.
- Self-contained, with Fabric API as the only dependency.

## Character ✅
- **Levels:** 1–100 with milestones, then automatic small Paragon stats per level with diminishing returns.
- **XP sources:** combat, discovery, building and gathering/crafting.
- **No classes.** One **PoE-style passive tree** (passives only, no manual stats).
  - The tree also covers gathering and crafting, boosting vanilla features only.
- **Tree regions (10):** Melee · Ranged · Defense · Vitality · Mobility · Mining & Excavation · Woodcutting/Farming/Husbandry · Crafting/Enchanting/Brewing · Building · Exploration.
- **Forced choices:** scarce points (about 120 plus a few Codex bonus points, against **~400 nodes**), exclusive keystones, and mastery picks (1 of N per cluster).
- **One center start**, with regions radiating outward.
- **Keystones have no drawbacks.** Exclusivity alone forces the choice.
- **Utility powers** (vein mining, tree felling, cluster harvest, auto-replant) are tree keystones or notables that compete with combat power. **Each one can be switched on or off.** Cluster harvest works like tree felling for anything that grows in clusters (coral, moss, dripstone, amethyst, flowers).
- **Silk Touch and Fortune stay separate enchants on separate tools.** The rack holds one tool per type, so **you choose which pickaxe to carry.** A second one has to sit in your normal inventory.
- **One key switches all utility powers on or off at once.**
- **Keystones come in rival pairs:** taking one locks its rival.
- **Review:** I'll draft the full ~400-node tree as an **interactive page** for you to review. That's the first deliverable once plan mode ends, before any code.
- **Respec:** refund points one at a time for **emeralds**, or do a full reset with a rare item. The tree is round, with the start in the center.
- **Numbers stay moderate:** endgame is about 200–500 HP.
- **Health UI:** 10 vanilla hearts, each worth 10% of max HP, with the exact number shown.
- **Death:** lose XP on a scale from 5% to about 15% by level, never dropping a level. Items go to a gravestone.
- **Level milestones** unlock gear tiers (the RPG Craft equip ladder) and worker tiers.

## Gear ✅
- **Rarity:** keep RPG Craft's 8 tiers (Common → Relic) as a **score** from enchants plus base material, with no random affixes (`RPG Craft/docs/LOOT_DESIGN.md`).
- **No new enchantments.** **Every vanilla enchant, single-level ones included, is rescaled to I–V.**
  - The enchanting table gives up to **IV**.
  - **V** takes effort: a table surrounded by rarer blocks, tree nodes, or combining two IVs at the anvil.
  - **VI (Relic level) is found only** (dragons, dungeons, rare elites).
- **Relic ★:** each enchant raised to VI adds one star.
- **Anvil "Too Expensive!" cap:** stays, but tree nodes raise it and slow the growth of the repair penalty.
- **Mending stays vanilla.** **Crafting can match found gear** with enough tree investment and materials.
- **Armor trims give stats:** the material sets the stat, the pattern's rarity sets its strength.
- **Gear above your level** can be equipped, but with a penalty.

## Inventory ✅
- **Dedicated slots:** Elytra (alongside a chestplate), Arrow/quiver, a Tool belt (compass, clock, map, spyglass… working passively), and Ender Chest (right-click the slot to open it).
- **Hotbar:** 9 slots + **Tool slot** + **Weapon slot**, each backed by a rack holding **one item per type**. Ctrl+scroll cycles the rack. Auto-tool is a settings toggle.
- **Offhand rack:** shield, totem, light source… The totem only works while it's the selected offhand item.
- **No extra inventory rows** (the user rejected them as too unvanilla). **Ender chest pages** are unlocked by dragon kills.

## Block Codex ✅
- **Own full screen**, like Hearthbook's. Sections: Blocks · Mobs (bestiary, kill-milestone bonuses) · Biomes & structures · Items.
- **Blocks: Discovered → Familiar → Mastered.** Thresholds scale with rarity, and only hand- or tool-gathered blocks count.
- **Unlock order (workers come last, because once a worker makes it you never farm it again):** Discovered → cluster harvest works on it · Familiar → villagers can sell it · **Mastered → a worker can produce it.** Undiscovered entries show as "???".
- **Rewards:** XP per entry, plus a bonus tree point at big milestones (around 100, 250 and 500 entries).
- **Multiplayer:** block and biome unlocks are shared by settlement; mob and item entries are personal.

## Workers ✅
- **Five gates** (Level, Discover, Recruit, Build, Sustain), scaled by tier:
  - 1 Basic, gatherers (Farmer, Forester, Herder, **Miner**; L10–20) needs Level, Build and Sustain. *(Full list in Batch 4.)*
  - 2 Skilled, makers (Mason, Smith, Cook, Fisher; L25–35) adds Discover.
  - 3 Specialist (Aquarist, Gardener, Beekeeper, Glassblower/Dyer, **Spelunker**; L40–60) needs all 5.
  - 4 Unique (Gem Cutter, Runesmith, Enchanter, Architect; L70+) is one per world or region.
- **Recruit:** rescue, quests and reputation, settlement attraction, unique specialists.
- **Station:** a single vanilla-style block plus a radius check. **Each producing job needs specific things around its station** (e.g. an Aquarist needs water, coral and light nearby). Inputs and output go in a **vanilla chest next to the station**.
- **Rejected:** "preserving" blocks by waxing them (too unvanilla). No new blocks.
- **Upkeep:** inputs, food and wages. Production converts materials, and workers depend on each other.
- **Cap:** renown sets slots per settlement; character level sets the global maximum.
- **Can die permanently.** 1–3 traits each. No happiness system.
- **Rank-ups** (Novice → Master) need work XP plus a milestone item you bring.
- **Production runs only while loaded**; a Gold Block chunk loader keeps it going.
- **Shortage:** the worker idles, shows an icon, and posts to the ledger.

## Settlements ✅
- **Bell** (earned) founds a settlement. Its radius grows with renown. Each extra Bell is a harder feat (about 3 at most).
- **Earned markers** come pre-named from **Feats** (an advancement-style tab) and can't be renamed. Each has a **fixed radius baked in** (e.g. a 6-block or a 30-block Ward Lantern):
  - Banner = area name and entry title.
  - Ward Lantern = pacified pocket.
  - Gold Block = chunk loader.
- **Pacified** means no natural hostile spawns, at full height (world bottom to top).
- **Safeguards:**
  - An establishing period with no hostiles and no combat.
  - No placement during combat.
  - Mobs already inside are never removed.
  - No markers in structures, gate dimensions or the End.
  - Markers are owner-bound, limited, and keep a minimum distance from each other.
- **Broken markers** drop for owner-only pickup, and the area goes inactive until it's re-established.
- **Renown** comes from what you build and what you do. **Raids** come only from vanilla Bad Omen, scaled up.
  - Building also counts **blocks placed in the area**, so a big build helps even with few beds or little variety. Each block type gives less the more of it you place, so a wall of one block can't max it.
  - **7 renown levels:** Camp, Settlement, Hamlet, Village, Town, City, Capital (draft table on the Batch 1 page).
  - **Heroes and Legends** can appear before Capital at a smaller chance (very rare at Village, rising up to Capital).

## Mobs ✅
- **Scaling:** mob level ≈ **75% of the average level of nearby players + a layer bonus** (caves about +3, deepslate about +6). It is **capped per layer: Surface 20 · Caves 40 · Deepslate 50.** No distance factor, no World Tier.
- **Elites:** glow and a name, plus 1–3 vanilla-mechanic powers. They can exceed the caps and drop better loot.
- **Sizes** follow a bell curve and are tied to stats; babies inherit them. **No Nemesis.**
- **Display:** level shown when you look at a mob, a subtitle when you enter a new layer, and visual cues.
- **Look-at HUD:** our own, written from scratch with no Jade code. It covers blocks, mobs, villagers/workers and containers/crops. It's modular: lines toggle on or off, and every system plugs its own lines in.

## Dungeons ✅
- **Portal dungeons only.** Overworld structures stay vanilla.
- **Portal:** a Nether-portal-shaped frame built from a vanilla block, activated with an **earned key**.
- **Difficulty = key tier.** Higher keys drop from harder runs, and tiers never end.
- **Rewards:** top gear and level VI books, rare block caches, worker milestone items, and feats that unlock markers and Bells.
- **Frame:** chiseled tuff and copper, for a trial-chamber "ancient tech" look.
- **Keys are single-use** and can roll 0–2 modifiers for risk against reward.
- **Themed runs** such as Overgrown, Frozen, Sunken, Deep Dark, **Nether** and **End**. Each theme has its own mobs, blocks and caches, which brings Nether and End content back into play.
- **Death:** a set number of lives per run, depending on tier. The run fails after the last one.
- **First key** comes from a **level feat**. **Runs are short (about 10 minutes)** with **no timer**.
- **Party:** players near the portal enter together. Mobs and loot scale with party size, and each player gets their own loot.
- **Bosses:** mostly empowered, named versions of theme mobs. Rare tiers bring real vanilla bosses (Wither, Warden, Elder Guardian).
- **Walls are unbreakable.** You can place blocks and break the ones you placed.
- **Keys:** clearing tier N always gives a key for N, with a chance at N+1 (better with modifiers).
- **Modifiers:** Darkness, Mining Fatigue, more mobs, more elites, no natural regen, explosive mobs. Each adds loot or caches.
- **Rooms** are handmade, built-in templates assembled at random per theme.

## World ✅
- **Nether pockets in deepslate:** rare crimson, warped, basalt and soul-sand cave pockets, with their blocks and some mobs.
- **The Nether matters more:**
  - Nether-only Codex blocks.
  - Nether-only worker milestone items.
  - Fortresses and bastions can drop keys and grant feats.
  - Nether materials help reach level V enchants (rarer blocks around the table) and high-tier trims.
- **Events:**
  - Night variants (Blood Moon, Harvest Moon).
  - Wandering themed caravans.
  - Ruined shrines (a one-time reward).
  - Traveler in distress (a recruitable rescue).
  - Pillager camp (a raid warning; clearing it earns a feat).
  - Bounty board at the Bell.
  - Rare vanilla variants (bestiary).
  - A roaming named elite.
  - Treasure maps to rare-block caches.
  - A settlement festival at renown milestones.
- **Terrain stays vanilla** and compatible with terrain mods such as Tectonic.

## The End ✅
- **Arena only.** No outer islands or End Cities. Elytra and shulker shells come from End-themed dungeon runs.
- **24 lore dragons**, each distinct and harder with new mechanics, then **endless Echo dragons**.
- **Each kill gives:** an ender chest page, level VI books or Relic★ gear, feats, and a piece of lore from the 15,000-year cycle.
- **Count is per world.** Every participant gets the rewards, including their own ender chest page.
- **Summoning is level-gated:** dragon N needs a summoner of about level 4×N, so dragon 24 needs about level 96.
- **After dragon 24:** a big ending moment (lore reveal, vanilla-style end poem, a title), then **Echo mode** with Echo dragons and endless dungeon tiers. The world is never reset.

## Blueprints ✅
- **Save any build** you stand in (yours, villages, structures) from the Codex UI.
  - Only the **block layout** is saved, never blocks or items.
  - Maximum size grows with feats.
- **Ghost placement** (see-through outlines you fill in) works **only inside your own areas**. The Architect worker can build a ghost from chest materials (confirmed in Batch 4).
- **Sources:** your own saves, a built-in starter catalog, and blueprint pages found in the world.
- **Dungeon rooms are always built-in.** Player builds never become rooms.

## Economy ✅
- **Currency: emeralds only** (no gold currency), using RPG Craft's economy.
- **Worker wages** go in a **decorated pot next to the worker**. 1 emerald = 1 day of work (7 emeralds = 7 days).
- **Worker food** goes in a **copper chest** next to them. Work materials and output use a normal chest.
- **Mastered blocks are bought through villager trades**, paid in emeralds.
- **Default:** keep RPG Craft's trade economy, folded together with the Extended Trades pool. Caravans are covered under World events.
- **Update (Batch 4):** vanilla trading is replaced by **our own trade system** inside our villager screen. Rules are drafted in Batch 4.

## Villagers (Hearthbook reborn) ✅
- **A new system** built from Hearthbook. **The player cannot create villagers.**
- **Villager library:** tracks your villagers, shows where each one is, and gives full info about each.
- **Library = met + recruited.** Any villager you talk to gets recorded. Recruited ones get full details and live tracking.
- **Backgrounds** are generated from the lore. Rare **unique villagers (Heroes, Legends)** come from handwritten lore.
- **Post + Modes** for recruited villagers:
  - A Post is an anchor point (station, bed or stall).
  - Modes: **Stay** (stands at the post and still trades) · **Work** (station by day, bed at night, within a leash) · **Wander** (inside the settlement only) · **Follow**.
- **No AI deaths:** recruited villagers don't suffocate, fall, drown or walk into lava. Mobs and raids can still kill them.
- **Unstuck:** they walk back to their post first. If that fails, they teleport to it, but only while no player can see them.
- **Carry:** an earned **Contract** item, usable on your own villagers only and never in combat. It keeps all their data. A placed villager starts in Stay mode until you assign a post.
- **Release from service** exists: you can un-recruit a villager.
- **The Contract is reusable**, with no cooldown. Once earned (Explorer feat) you keep it, and repeatable feats no longer hand out Contracts. **A carried villager can only be placed inside your claimed areas** (settlements or wards).
- **Villager viewer:** the library shows a small render of each villager.
- **Villager screen (Batch 4):** right-clicking a villager opens our screen (quick trades, presentation, quick actions, management tabs). The vanilla trade screen is removed.

## Map ✅ (new)
- **Our own full map from the start**, JourneyMap-style and written from scratch: a minimap, a world map (hub tab plus key M), waypoints, and layer views (surface, caves, deepslate, Nether).
- It shows settlements and their radii, wards, recruited villagers, portals and waypoints.
- Also: **death markers**, **sharing waypoints** with other players, and a **"Go there" button** that pins the target onto the vanilla **Locator Bar** to guide you.
- **Waystones:**
  - An earned Lodestone (from a feat), placed only in your claimed areas.
  - Travel costs emeralds based on distance. No travel during combat.
  - You always arrive on top of the lodestone.
  - Right-clicking a waystone opens the **waystone terminal**: the list of your waystones, with distance and cost.
  - **Trips only start at a waystone.** Cost: 1 emerald per 250 blocks, minimum 1.
  - **Waystones shared by friends** can be used.
- **Sharing:**
  - A shared waypoint or waystone arrives as a toast plus a line in the tracker, and waits in a **"Shared with you" box on the Map** with **Add** and **Decline** buttons.
  - Settings choose: ask every time, auto-add from friends, or sharing off. You can also block a player.
- **Built early:** right after the M0 foundation.

## Feats ✅
- **Shown as a vanilla advancement tree you can drag around**, with 5 chains:
  - **Settler:** Bells, Banners.
  - **Warden:** Ward Lanterns, in radius sizes 6 → 12 → 30.
  - **Explorer:** blueprint save size, Contracts.
  - **Gatekeeper** (renamed from Delver): first key, Gold Block chunk loaders.
  - **Dragonslayer:** the Respec Tome at dragon 12 (also a very rare drop in dungeon tiers 20+), a title at dragon 24.
- **Big markers are one-time feats.** Small ones (r6 lantern, small banner, Contract) also come from **repeatable** feats, such as every 10 elites or every 5 clears.
- **Each feat gives** its item, a vanilla toast and some XP. The exact requirements are drafted in the conversation and tuned at balancing.

## Food, farming & combat ✅
- **Food:** no new foods. Complex vanilla foods (stews, pies, golden carrots, cake) give short attribute buffs; simple foods just fill hunger.
  - **Meal buffs stay different from potions:** small, long-lasting buffs to stats potions don't cover (mining speed, max HP, hunger, luck), and only one meal buff at a time. Potions stay stronger and keep their unique effects.
- **Farming QoL for everyone:**
  - Right-click harvest with auto-replant (the tree only adds stronger area versions).
  - No trampling.
  - Crop growth shown in the HUD.
  - Animal grow-up and breeding timers.
- **Combat:** vanilla base with **light tweaks**: a crit-chance attribute from the tree, bow draw speed, and shield parry timing.

## Lore ✅
- **I draft, you approve.** No lore gets used until you sign off on it.
- **Where it shows up:** villager backgrounds, dragon chapters, fragments from shrines and dungeon books, and flavour text on Codex entries.
- **Discoverable story:** no forced questline. Fragments come together in the Codex, and the 24 dragons are the spine.

## UI ✅
- **Hearthbook style** for every screen, reusing the approved Hearthbook design.
- **One hub with tabs:** Character (tree) · Codex · Villagers · Settlements · Feats.
- **HUD additions:**
  - Level and XP readout.
  - Damage numbers (toggleable).
  - Bounty, event and worker-alert tracker, top right by default and **movable to any corner**.
  - Mob health bars.
  - The look-at HUD, with a short **"New" tag** the first time you look at something.
  - Health hearts shown as a % of max.
  - **The layer subtitle shows the danger number** ("Caves: mobs up to level 40").
- **Keys:** hub H · utility powers V · Tool/Weapon rack Ctrl + Scroll · offhand rack **Alt + Scroll**.
- **Inventory (layout A, revised):** no left panel.
  - **Middle window:**
    - Armor and the player.
    - A column with the **elytra slot on top** and a **2-slot offhand rack** below it.
    - Crafting, with the **ender chest slot** and the **totem slot** side by side under the crafting grid. The totem works from its own slot.
  - **Ender chest pages can be named.** The ender chest screen has a page list **on the right** (locked pages show which dragon unlocks them), a rename field, and a **Close button**.
  - **Right panel (approved):** tool rack · weapon rack **plus the arrows slot** · **tool belt**.
  - **Bank:** one slot **next to the totem slot** that holds **emeralds only** (no emerald blocks), in unlimited amount, with the total shown in emeralds.
    - It **pays automatically only for waystone trips, repairs and tree refunds**. Everything else, like villager trades, needs emeralds in hand as in vanilla.
  - **Ender chest pages:** start with 1 page, plus 1 per dragon (25 in total).
  - Mockups use real item icons. The axe goes wherever balances the layout best.
- **Hotbar:** on the left, **[tool belt slot (scrollable)] [offhand]**; then the 9 slots; then **[Tool] [Weapon]** on the right.
  - Scrolling left past slot 1 lands on the belt slot. Ctrl + Scroll picks the belt item.
- **Feats screen:** advancement style, approved.

## Loot ✅
- **Chest loot band = the mob formula:** about 75% of nearby players' level plus the layer bonus, capped per layer.
- **Per-player chest loot** (Lootr-style) in multiplayer.
- **Mob drops:**
  - RPG Craft's emerald and leveled-ore pools.
  - Elites can drop rarity gear and books.
  - Bestiary milestones slightly raise that mob's drop chances.
- **Level VI books** come from dungeons, dragons and **rare high-level elites**.
- ⚠️ The user wants all of this **carefully balanced**, so there will be a dedicated balancing pass.

## Config & admin ✅
- **Server:** every system can be switched off, and key numbers are tunable.
- **Content is data-driven (JSON/datapacks):** tree, feats, jobs, dungeon themes and rooms, loot, elite powers, lore.
- **Ops commands:** player tools (level, points, tree, feats), world tools (villagers, settlements, dragon count, dungeon tiers), and debug tools (spawn elites, roll loot, simulate workers, show the mob formula).
- **Client:** an in-game settings tab in the hub (HUD toggles, damage numbers, auto-tool, keybinds).

## Project ✅
- **Name: Vestiges.** Mod id `vestiges`, package `dev.vestiges`.
- **Minecraft 26.3, pinned.** Updates happen only deliberately, between milestones.
- **Local git plus a private GitHub repo** with automatic builds.
- **License:** all rights reserved for now.

## Technical approach (my recommendation)
- **Setup copied from Hearthbook (`RPG_Lore_Mod`):** `build.gradle`, `gradle.properties`, `settings.gradle`, `dev.bat` and `.github/workflows/build.yml`. Same source split: `src/main`, `src/client`, `src/test`.
- **Reused from Hearthbook code:**
  - The data-attachment pattern (`ModAttachments`).
  - Network payloads (`net/Payloads`, `server/ServerNet`).
  - Screen framework (`client/HearthbookScreen`, `PageHost`), which becomes the hub.
  - Engines (`catalogue/PriceEngine`, `EnchantEngine` rescaled to I–VI, `NameGenerator`, `TradeCatalogue`, `RoleIndex`).
  - The command pattern and its JUnit tests.
- **Ported from RPG Craft:** level formulas, the `rpg_band` equip ladder, the rarity score (`docs/LOOT_DESIGN.md`), banded loot data, mob loot pools, and the villager economy.
- **State is stored as Fabric data attachments:**
  - Player: level, XP, tree, Codex, feats.
  - Entity: villager record, elite rank, size, mob level.
  - World: settlements, markers, dragon count, villager library.
- **All content in JSON datapack registries:** tree, feats, jobs, dungeon themes and rooms, elite powers, food buffs, lore, enchant tables.
- **Mixins** for attribute caps, the armor formula, enchant rescaling, the enchanting table, villager AI, extra slots and hotbar, and hearts rendering.
- **Dungeons** run in a dedicated dimension with an instanced region per run.
- **Tests:** JUnit for every formula (mob scaling, armor, enchants, rarity checked against RPG Craft) and Fabric GameTest for in-world behaviour.

## Next steps once you approve. ⚠️ Approving does NOT start building.
**Phase A — Sketches and drafts only. No mod code, no project setup.**
- ✅ **Batch 1 (overview and screens) approved** on 2026-10-09: https://claude.ai/artifact/JXHbWz46LRyCtKAgmLcg94
- ✅ **Batch 2 (passive tree) approved** on 2026-10-09. Final state = two wheels (approved) + 5 weapon/tool ascendancies + choice nodes option A + the node fixes below. The wheels page (https://claude.ai/artifact/LeywBtZ4E9Jkuf9bYWaBYW) still shows the older ascendancy draft and node names; **these text decisions supersede it**, and the page gets refreshed before building.
  - **Flagged-node fixes accepted:**
    - **Duplicates:** Delver's fighting node becomes **Aftershock** (mace smash slows for 3 s); Wayfarer's becomes **Pinning Shot** (fully drawn arrows slow for 2 s); the tree's Bolt Thrower becomes **+15% crossbow damage**.
    - **Pathfinder:** −50% hunger from sprinting.
    - **Fletcher:** arrows you craft fly 10% faster.
    - **Prospector:** +5%.
    - **Runic Memory:** combining enchanted books never adds repair penalty.
    - **Grand Alchemist:** potions you drink last 50% longer only.
    - **Green Thumb:** bone meal on crops works 25% better.
    - **Twin Births:** breeding cooldown −50%.
    - **Deep Diver:** +60 s oxygen and you see 30% further underwater.
    - **Whirlwind:** hits at most 5 enemies.
    - **Thick Hide:** gets a HUD-ready icon.
    - All other flagged nodes are kept as they are.
- ✅ Batch 3 (numbers and balancing), by text:
  - ✅ **Power budget**, a hidden balancing tool players never see:
    - **4 node types:** travel node = 1 unit, notable = 3, mastery = 5, keystone ≈ 8. "Small" and "big" notables are merged into one notable type.
    - **Ascendancy (proposed):** crafting node = 5, fighting node = 8.
    - **Exchange rates, 1 unit =**
      - Fight: +3% damage = +3% armor = +3 max health = +2% attack speed = +2% movement speed = +2% crit chance.
      - Craft: +4% mining speed = +4% chopping speed = +3% crop yield = +0.2 reach = +5% placing speed = −4% enchanting cost.
      - Narrow stats get more per unit (e.g. +4% sword-only damage).
    - Both options of a choice node have equal units.
  - ✅ **Player and mob curves:**
    - **Each character level gives +1 max health and +2% damage** with every weapon, automatically (×2 damage at L50, ×3 at L100). The tree, gear, enchants and trims add the rest.
    - This keeps fights at about 6 hits at every level: L20 hit ≈12 vs zombie HP ≈80; L50 ≈28 vs ≈170; L100 ≈53 vs ≈320.
    - **Armor:** damage blocked = armor ÷ (armor + 20), **capped at 85%**. Vanilla full diamond (20) blocks 50%; heavily built (60) blocks 75%.
  - ✅ **Elite ranks:**

    | Rank | Strength | Powers | Where | Spawn rate | Over layer cap |
    |---|---|---|---|---|---|
    | Champion | ×2 | 1 | anywhere | 3% | +3 levels |
    | Elite | ×3 | 2 | caves and deeper | 1% | +6 levels |
    | Warlord | ×5 | 3 | deepslate and dungeons | 0.2% | +10 levels |

    - **13 powers (fine for now):** Swift, Brute, Armored, Burning, Shove, Shadow, Caller, Volatile (no block damage), Venom, Chilling, Mending, Spiked, Withering (deepslate and Nether only).
    - **Loot per rank is OK for now:** Champion 10% gear; Elite 25% gear and 10% book; Warlord guaranteed gear and 5% level VI book. **A full loot table discussion is still to come** (added to Batch 3).
    - **Glow is temporary:** it shows on spawn or when you first spot the elite, then fades. Colour by rank: white, gold, purple.
    - **Name card:** elites and bosses get a bigger, fancier look-at card (rank banner, powers, health) that scales with monster size.
    - **Boss strength:** Lieutenant ×6, Boss ×10, Overlord ×20. Their design comes in Batch 4.
  - ✅ **Enchants I–VI:**
    - **Rule:** IV = today's vanilla max (what the table gives), V = +25% beyond vanilla, VI = +50% (found only). I–III spread the vanilla range below.
    - Fortune, Looting and Luck of the Sea stay small past IV because of the economy.
    - **Special cases:**
      - **Quick Charge:** 1.0 s → 0.55 s (IV), 0.45 s (V), 0.35 s (VI). Never instant.
      - **Lure:** −4 s → −15 s, −17 s, −19 s. Never zero.
      - **Depth Strider:** 25 → 100% of vanilla, then +10% and +20% swim speed.
      - **Multishot:** 2 arrows (I–II), 3 arrows (III–IV), +10% damage (V), 3 arrows that pierce 1 (VI).
      - **Mending:** 25 → 100% of vanilla repair, then 125% and 150%.
      - **Infinity:** 25 → 100% chance to save the arrow; V also saves spectral arrows, VI also tipped arrows.
      - **Silk Touch:** I glass and leaves · II + grass and mycelium · III + ores · IV everything vanilla · V +10% mining speed · VI +20% and half durability use.
      - **Aqua Affinity:** 25 → 100% of vanilla, then +10% and +20% mining underwater.
      - **Flame:** burn 1 s → 5 s, then 7 s and 9 s.
      - **Channeling:** 25 → 100% lightning in storms; V also in rain; VI in any weather.
      - **Curses:** unchanged.
  - ✅ **Rarity (reworked):**
    - **7 tiers, Mythic deleted:** Common · Uncommon · Superior · Rare · Epic · Legendary · Relic.

      | Tier | What it means |
      |---|---|
      | Common | junk or plain basic gear |
      | Uncommon | decent base |
      | Superior | one good enchant |
      | Rare | two good enchants |
      | Epic | full table enchants (IV) |
      | Legendary | V enchants |
      | Relic | any VI enchant |

    - **Points:**
      - Material: wood or leather 0, stone, gold, chainmail or copper 10, iron 20, diamond 30, netherite 40.
      - Enchants per level: major 12 (Sharpness, Power, Protection, Efficiency, Fortune, Looting, Mending, Silk Touch, Infinity), normal 8, minor 5 (Smite, Bane, Knockback, Punch). Curses −20.
      - **Bands:** 0–19 Common · 20–44 Uncommon · 45–79 Superior · 80–129 Rare · 130–229 Epic · 230+ Legendary.
    - **Relic = the item has at least one level VI enchant, whatever its material.** ★ = the number of VI enchants. **A level VI book shows as Relic.**
    - **Checks:** Sharpness IV book = Superior; iron pickaxe Eff IV + Unb IV = Rare; iron pickaxe Eff VI = Relic ★; table-enchanted diamond boots max out at Epic; netherite gear all at V = Legendary.
  - ✅ **Loot tables, rarity odds per item (%):**

    | Source | Common | Uncommon | Superior | Rare | Epic | Legendary | Relic |
    |---|---|---|---|---|---|---|---|
    | Loot level 1–10 | 60 | 35 | 4.9 | 0.1 | – | – | – |
    | Loot level 11–20 (surface cap) | 35 | 44 | 17 | 3.5 | 0.4 | 0.08 | 0.02 |
    | Loot level 21–35 (caves) | 15 | 39 | 32 | 12 | 1.7 | 0.25 | 0.05 |
    | Loot level 36–50 (deepslate) | 5 | 24 | 40 | 25 | 5.4 | 0.5 | 0.1 |
    | Dungeon tiers 1–10 | – | 10 | 35 | 35 | 16.5 | 3 | 0.5 |
    | Dungeon tiers 11–25 | – | – | 20 | 40 | 29 | 9 | 2 |
    | Dungeon tier 26+ and dragons | – | – | 5 | 30 | 40 | 20 | 5 |

    - A **tiny Relic chance from the surface cap up** (about 1 in 5,000 items); **no Relic at loot level 1–10**.
    - Chest contents follow RPG Craft's catalogue, scaled by loot level.
    - Dungeons add block caches and worker items. Warlords keep their 5% VI book.
  - ✅ **Meals** (one buff at a time; a new meal replaces the old one):

    | Food | Buff | Lasts |
    |---|---|---|
    | Mushroom Stew | +5% mining speed | 10 min |
    | Beetroot Soup | +4% chopping speed and +3% crop yield | 10 min |
    | Rabbit Stew | +6 max health | 8 min |
    | Pumpkin Pie | −15% hunger drain | 10 min |
    | Golden Carrot | +10% natural regeneration | 8 min |
    | Cookie | +0.3 block reach | 5 min |
    | Cake (per slice) | +4% movement speed | 6 min |

    - Suspicious Stew, Honey Bottle and Golden Apples keep their vanilla effects. Simple foods just fill hunger.
  - ✅ **Trims:**
    - **The material sets the stat, per piece:**

      | Material | Stat | Material | Stat |
      |---|---|---|---|
      | Iron | +1 armor | Resin | +5% natural regeneration |
      | Netherite | +1 armor toughness | Gold | +4% mining speed |
      | Diamond | +3 max health | Copper | +5% block placing speed |
      | Amethyst | +3% damage | Lapis | −4% enchanting cost |
      | Quartz | +2% attack speed | Emerald | villager trades 2% cheaper |
      | Redstone | +2% movement speed | | |

    - **Patterns are cosmetic only.** Their rarity is about scarcity, not power.
    - **Set bonuses, both possible at once:** 4 of the same material = +25% on that stat; 4 of the same pattern = **+2 max health and +2% movement speed** (the same for every pattern, option A).
  - ✅ **Codex thresholds and costs:**

    | Block kind | Familiar | Mastered |
    |---|---|---|
    | Common | 64 | 256 |
    | Uncommon | 48 | 192 |
    | Rare | 32 | 128 |
    | Very rare | 16 | 64 |
    | Unique | Codex only | – |

    - **Bestiary:** 10 kills = +2% damage against that mob; 50 = +4% and +1% drop chance; 250 = +6% and +2% drops.
    - **Refunding one tree point** = 1 emerald + 1 per 10 levels. Switching a choice node costs the same. Full respec or changing ascendancy uses the rare respec item. All paid from the Bank.
- ✅ **Batch 3 (numbers) approved** on 2026-10-10.
- ✅ **Batch 4 (content lists) approved** on 2026-10-10, by text:
  - ✅ **Feats list** (approved 2026-10-10). ↻ = repeatable. Each feat gives its item, a toast and XP.
    - **Settler:** Founder (level 10, 5 beds in one area, meet 1 villager) → 1st Bell · Town Banner (renown 2) → Banner r16 · Big Banner (renown 5) → Banner r48 · Second Bell (level 50, a settlement at renown 5, 3 workers Journeyman or above) → 2nd Bell · Third Bell (level 80, two settlements at renown 6) → 3rd Bell · ↻ Local Landmark (each renown level gained) → small Banner r16.
    - **Warden:** Camp Breaker (clear a pillager camp) → Ward Lantern r6 · Defender (defend a raid) → r12 · Bulwark (defend a Bad Omen V raid) → r30 · ↻ Elite Hunter (every 10 elites) → r6.
    - **Explorer:** First Waystone (level 15, 10 biomes) → Waystone · Charted (100 Codex entries) → blueprint size M · Surveyor (250) → size L · Pilgrim (restore 10 ruined shrines) → Contract (reusable) · ↻ Trailblazer (every 50 new Codex entries) → Waystone.
    - **Gatekeeper** (renamed from Delver, which is now an ascendancy): First Key (level 15, 5 elites) → tier 1 key + portal frame · Deep Runner (clear tier 10) → Gold Block · Abyss Walker (clear tier 25) → 2nd Gold Block · ↻ Regular (every 5 clears) → small Banner r16.
    - **Dragonslayer:** every dragon → ender chest page, lore chapter, XP. Extras: dragon 1 title "Dragonslayer" · 6 extra Waystone · 12 **Respec Tome** · 18 3rd Gold Block · 24 title "Cyclebreaker" + the ending.
    - **Respec Tome** (full respec + ascendancy change): from dragon 12, and also a **very rare drop in dungeon tiers 20+** (rate set at balancing).
  - 🟡 **Worker jobs** (in progress):
    - ✅ **Agreed:**
      - Tools wear out like the player's (Efficiency = faster, Unbreaking = lasts longer; no Fortune or Looting for workers; no tool = idle + alert).
      - Wages by tier: 1 / 2 / 3 / 5 emeralds a day.
      - **Pay = 50% of speed, food = the other 50%.** Workers eat any food. Paid but unfed = half speed. (Reading to confirm: not paid = stops.) No meal bonus.
      - **One worker = one tool = one output.** More outputs = more variants (e.g. cows: Milker with a bucket, Butcher with a sword, Tanner with an axe).
      - Workers can be **grouped** in one shared area (e.g. one big cow pen with 3 workers).
      - **Miner is a gatherer (Tier 1).** The Tier 3 "Delver" worker is renamed **Spelunker**.
      - Every job description must be **very precise and simple**: what it does, how, and what it needs.
      - **Not paid = stops.**
      - **Each worker has exactly 3 blocks, never shared:** its station, a decorated pot (wages), and a copper chest that holds its food, its tool, one sample of the output (always leave one), and everything it produces. Chest full = it stops and notifies you.
      - **Job = station + tool.** The output is chosen by the sample in the chest. Byproducts are used to replant or are lost.
      - **Rank-up = speed only (B):** Novice 1 action every 10 s → Master every 4 s. The work area stays fixed; for a bigger farm, hire more workers.
      - ✅ **Tier 1 gatherers:**

        | Job | Level | Station + tool | Needs in its area | Output (pick one) |
        |---|---|---|---|---|
        | Farmer | 10 | Composter + hoe | farmland, water, its crop | wheat, carrots, potatoes, beetroot |
        | Picker | 12 | Composter + axe | the plant growing | melon, pumpkin, sugar cane, bamboo, cactus |
        | Forester | 12 | Fletching Table + axe | open sky, room for trees | one wood type (by sapling) |
        | Shearer | 12 | Loom + shears | sheep, grass | wool |
        | Milker | 12 | Loom + bucket | cows or goats | milk (empty buckets supplied) |
        | Breeder | 14 | Loom + breeding food | 2 adults | more animals, up to "breed up to" (20) |
        | Butcher | 15 | Loom + sword | the animal | its meat; kills adults above "keep" (10), never babies |
        | Tanner | 15 | Loom + axe | cows or rabbits | leather or rabbit hide; same kill rule |
        | Miner | 18 | Stonecutter + pickaxe | a stone generator you build | cobblestone, stone or basalt |
        | Fisher | 20 | Barrel + fishing rod | 5×5 water, open sky | cod or salmon; no junk, no treasure |

        No worker produces ores.
      - ✅ **Linking:** sneak + right-click your worker with an empty hand ("Linking Aldric"), right-click his station, pot and copper chest in any order, then sneak + right-click him again. Linking the station sets it as his post (Work mode). Looking at a worker outlines his 3 blocks; the look-at HUD shows days paid, days of food, tool durability and chest space. A block belongs to one worker only; to move one, break it and link the new one.
      - ✅ **Makers = a station with no tool.** They use the vanilla recipe (ingredients + one output sample in the copper chest). One action = one craft or one smelt; fuel like a furnace (1 coal = 8 smelts). Blocks need Mastered; food, ingots and repairs don't.
      - ✅ **Makers need a roof:** a set number of solid blocks above the station (draft: at least 15 of the 25 blocks in the 5×5 area above it, up to 6 blocks higher).
      - ✅ **Tier 2 makers** (OK for now, refine later): Carpenter 25 Fletching Table (wood blocks) · Weaver 25 Loom (carpets, beds) · Mason 26 Stonecutter (stone blocks) · Cook 27 Smoker (one food) · Smelter 28 Blast Furnace (ingots) · Brickmaker 30 Furnace (bricks, mud bricks, smooth stone, cracked bricks, terracotta) · Smith 32 Smithing Table (chains, bars, lanterns, cut copper, grates, bulbs) · Repairer 35 Grindstone (anvil-style repair, 1 material = 25%, no XP cost, "Too Expensive" still applies).
      - ✅ **Tier 3 specialists:** Gardener 40 (Composter + shears) · Honey Keeper 42 (Beehive + glass bottle) · Comb Cutter 42 (Beehive + shears) · Aquarist 45 (Barrel + shears, pool ≥5×5×2 with 1 live coral or sea plant, light) · Spelunker 55 · Dyer 45 (Cauldron: one colour of wool, terracotta, concrete, stained glass, candles) · Glassblower (station to settle).
      - ✅ **Growers copy a "seed":** 1 of the chosen plant alive in the area; each action uses 1 bone meal and harvests 1 copy; the original stays.
      - ✅ **Recruit gate (Tier 3):** retrain a worker who reached Expert in any job (rank resets to Novice), or recruit a rescued specialist (Traveler in distress). **Must be explained to the player in the mod.**
      - ✅ **Spelunker = a real quarry** (user idea): set at a spot and level, it digs out an area up to 3 blocks high, and every block it removes becomes 1 of the chosen output (deepslate, tuff, calcite…). Exact rules drafted next.
      - ✅ **Spelunker quarry rules:** place its station at the spot, inside your claimed area · it digs out the 13×13 area around the station, 3 blocks high (station level + 2 above) · every block it digs becomes 1 of the chosen output (below y=0: deepslate, cobbled deepslate, tuff, calcite; above: stone, granite, diorite, andesite); the dug block is gone · it skips and leaves air, ores (and reports them: "found 4 iron ore"), blocks touching water or lava, containers, spawners, structure blocks, bedrock and its own 3 blocks · places a torch from its chest every 6 blocks (no torches = idle + alert) · "Area cleared" when done: move the station and relink · about 500 blocks per spot.
      - ✅ **Miner** also mines what regrows: amethyst shards (at a budding amethyst) and pointed dripstone (dripstone with water above). **Glow lichen** moves to the Gardener.
      - ✅ **Glassblower (B):** shares the Furnace with the Brickmaker; **the sample picks the job** (brick sample = Brickmaker, Tier 2; glass sample = Glassblower, Tier 3, level 42: glass, panes, tinted glass). General rule: on a shared station with no tool, the sample picks the job.
      - ✅ **Jobs page** in the Villagers tab: one card per job (station + tool, needs, one action, outputs, level and gates), a ✔/✘ checklist of what's missing (Tier 3 shows both recruit ways), a toast + tracker line when a job unlocks, and the look-at HUD says why a worker is idle. **Later: add a picture of what each work area should look like.**
      - ✅ **Tier 4 uniques** (level 70+, 5 emeralds a day, makers under a roof):
        - **Gate:** retrain a Master worker (rank resets to Novice) or recruit a Hero or Legend villager.
        - **One of each job per settlement** (replaces "one per world").
        - **Enchanter** (Lectern, 15 bookshelves around it): copies the sample book (one enchant, up to IV); each copy = 1 book + 1 lapis per level; 1 action per level. Never past IV, no treasure enchants (Mending, Frost Walker, Soul Speed, Swift Sneak, Wind Burst).
        - **Runesmith** (Anvil): 1 enchanted item + 1 book → the item's highest enchant moves onto the book, the item is destroyed. A VI enchant becomes V on the book.
        - **Gem Cutter** (Stonecutter; a rare-block sample picks this job over the Mason): gilded blackstone (1 blackstone + 1 gold ingot), budding amethyst (4 amethyst blocks + 1 diamond), sponge (1 dried kelp block + 4 prismarine shards). All three approved.
        - ✅ **Architect** (decided in the Blueprints topic): Cartography Table, 5 ghost blocks per action, bottom-up; its blocks count as yours for renown.
      - Correction noted: in Work mode workers work by day and sleep at night, so a Novice does about 60 actions per Minecraft day (a full quarry ≈ 3 h at Novice, ≈ 1 h at Master).
      - ✅ **Ranks:** 1 work XP per action. Apprentice 200 XP + 1 Book (≈3 days) · Journeyman 800 + 1 Gold Block (≈11) · Expert 2,000 + 1 Blaze Rod + 1 Diamond (≈24) · Master 4,000 + 1 Echo Shard (≈41 Minecraft days of loaded work, ≈14 h). Ready = green sparkles + HUD "Ready: bring…"; right-click the worker with the item.
      - ✅ **Traits:** rolled at recruit, 1 / 2 / 3 traits at 50 / 35 / 15%, each 1 in 4 bad; never change; retraining keeps them; rescued specialists get at least 1 good, Heroes and Legends 3 good. Good: Diligent +10% speed · Careful tool wears 25% slower · Light Eater half food · Fast Learner +25% work XP · Tough +50% health · Thrifty 5 emeralds = 6 days · Night Owl also works at night at half speed. Bad: Lazy −10% · Clumsy +25% wear · Big Eater double food · Slow Learner −25% XP · Frail −25% health · Greedy 5 emeralds = 4 days. No trait adds output.
      - ✅ **Death:** permanent by default, **toggleable in settings**. Library page stays as "fallen", its 3 blocks unlink.
      - 🎯 **Goal (user):** by the end, a settlement full of workers can farm **any item** in Minecraft (paintings, redstone components, copper golem statues…).
      - ✅ **Death off:** a "dead" worker wakes up at its bed the next morning.
      - ✅ **Item coverage, new jobs:**
        - **Crafter** (Tier 3, L50, vanilla Crafter block, no tool, roof): any crafting-table recipe (sample picks); only items you've crafted yourself at least once.
        - **Brewer** (Tier 3, L50, Brewing Stand, no tool, roof): one potion (normal, splash or lingering) from water bottles, nether wart, ingredients, blaze powder fuel; 3 potions per action.
        - **Slayer** (Tier 3, L55, Grindstone + sword): kills mobs from a spawner in its area, or from a dark room it guards (floor ≥6×6, fully dark, roofed; the one spot in a settlement where mobs still spawn by vanilla rules). Keeps one drop (sample). Mob must be at 250 Bestiary kills. Can get hurt.
        - **Barterer** (Tier 3, L50, Barrel, no tool): gives 1 gold ingot to a piglin in its pen, keeps the chosen result, the rest is lost. Nether only (needs a ward there).
        - **Brusher** (Tier 1, L15, Loom + brush): armadillo scutes. **Egg Collector** (Tier 1, L10, Loom + bundle): eggs.
        - **Smith** also ages copper and makes copper golem statues (1 copper block + 1 carved pumpkin). **Tanner** also gets feathers. **Spelunker** keeps the real ores it digs from Expert rank (no Fortune).
        - 🔒 **Never made by workers:** dragon egg, elytra, nether star, heavy core, heart of the sea, totem of undying, enchanted golden apple, trial keys, music discs, loot-only templates and banner patterns. Villager-sold items stay in trades.
        - **Pottery sherds are farmable** (user: purely decorative, too rare for no reason). Proposed: the Brickmaker copies any sherd you've found (1 clay ball + fuel per copy).
      - ✅ **Sherds:** the Brickmaker copies any pottery sherd you've found (1 clay ball + fuel per copy).
  - 🟡 **Villager interaction** (in progress):
    - ✅ **Life with you:** meet (right-click = "met" in the library) → Trust → request → recruit → orders → work → grow → move → track → let go.
    - ✅ **Trust 0–100:** trade +2 · request +20 · defend its village in a raid +10 · cure from zombie = 100 · hit −10. At 100 a **Recruit** button appears. Also: rescued Travelers join at once; Heroes and Legends after a short lore request.
    - ✅ **Requests:** one small job-themed ask at a time (16 bread for a Farmer, 32 cobblestone for a Mason), shown on its page; +20 Trust and a few emeralds.
    - ✅ **Every recruited villager uses a settlement slot** (not just workers). ⚠️ **To rebalance:** renown radius, size, beds and slots so a **Capital is big enough for every job** (the any-item goal needs many one-output workers).
    - ✅ **Vanilla trade screen is gone. Vanilla trades are gone.** Right-click a villager → **our villager screen**: first tab = quick trades (our own trade system) + a presentation of the villager + quick action buttons; other tabs manage him. It is the same details page the library opens.
    - ✅ **Villager screen layout:** tabs Overview · Trades · About · Orders (yours) · Work (workers). Overview = render, name, job and rank, traits, 2-line story, status line (Trust for strangers) on the left; 4–6 quick trades on the right; context quick actions at the bottom (Give request, Recruit, Mode, Link blocks, Rank up). Release lives in Orders only. Trades tab shows locked offers greyed. About = full story, traits, where met, history. Work = job card, rank progress, pay/food/tool/chest, linked blocks + coordinates, last 7 days of output.
    - ✅ **Trading clicks:** click = trade once, shift-click = as many as stock and items allow; paid from inventory (not the Bank); stock refills every morning.
    - ✅ **From the library: view-only** (except "Go there"). Trading and orders need you next to the villager.
  - 🟡 **Our trade system** (in progress): built from Hearthbook's PriceEngine/TradeCatalogue (1,534 priced items, wealth tiers 1–5 with bands 1–3 / 4–8 / 9–20 / 21–55 / 56–150 emeralds, stock 12 / enchanted 3 / luxury 1, buyback offers) and RPG Craft's villager economy (3–6 rolled offers per job pool, bulk buy offers, cure reroll). Proposed: Wealth as the trade ladder (settlement renown raises it), daily purse per villager, Familiar blocks in trades, discounts and trade XP replacing vanilla.
    - ✅ **Wealth (1–5) is the trade ladder:** what a villager sells = its job list at its Wealth and below. Wild villagers W1–2; villagers in your settlement take its Wealth (Camp/Settlement 1 · Hamlet/Village 2 · Town 3 · City 4 · Capital 5); Heroes and Legends W5 + one unique offer. **Rank is for work only.** Offers: 3–6 sells + 1–3 buys; workers' lists follow their job.
    - ✅ **Daily purse** per villager (draft W1 5 · W2 10 · W3 20 · W4 40 · W5 80) to stop endless emerald farms. ⚠️ **User: the player must still earn enough emeralds to run everything; some combinations should produce more emeralds than normal; add banker types that buy ores for emeralds.**
    - ✅ **Stock** 12 / enchanted 3 / luxury 1 per day, refills each morning (not objected).
    - ✅ **Familiar blocks** appear in the fitting job's trades.
    - ✅ **Discounts:** Trust 50 −5% · Trust 100 −10% · Hero of the Village −20% with that village while it lasts · Emerald trims −2% each. Each trade gives a little character XP. Curing = Trust 100.
    - ✅ **Emerald income** (all approved 2026-10-10):
      - The purse is a ceiling, not a squeeze (Village W2 with 12 villagers ≈ 120/day cap vs ≈ 12/day wages for 8 workers; City W4 with 40 villagers ≈ 1,600/day cap vs ≈ 70/day for 30 workers). Mob emerald drops and loot on top.
      - **Banker** worker (Tier 2, L30, Lectern + emerald sample; a book sample = Enchanter): buys ores, raw metals, ingots, metal blocks and gems from its chest at full price, 1 item per action, drops an emerald each time the value total reaches 1. Daily limit W1 20 · W2 40 · W3 80 · W4 160 · W5 320. One per settlement. Wild Bankers (Hearthbook Bank Clerk / Moneylender / Banker) trade by hand in big wild villages.
      - **Chains pay more:** villagers pay 50% of the price for raw goods, 75% processed, 100% finished. Bankers pay 100% for ores, metals and gems.
      - **Events that pay:** caravans with a "wanted" list (2× price, own purse 200–500) · festival day (all purses ×2) · bounty board at the Bell.
      - **Merchant** worker (Tier 3): sells the goods in its chest to your settlement's villagers automatically, using their purses.
  - ✅ **Villagers, workers and trade: complete for Batch 4.**
  - 🟡 **Settlement numbers** (in progress):
    - ✅ **Renown table (replaces the Batch 1 draft):**

      | Renown | Name | Bell radius | Villager slots | Wealth | Also unlocks | Hero / Legend visits |
      |---|---|---|---|---|---|---|
      | 1 | Camp | 24 | 3 | 1 | bounty board | never |
      | 2 | Settlement | 32 | 6 | 1 | wanderers ask to join | never |
      | 3 | Hamlet | 48 | 10 | 2 | caravans | never |
      | 4 | Village | 64 | 18 | 2 | festivals | very rare |
      | 5 | Town | 80 | 30 | 3 | Tier 3 workers can live here | rare |
      | 6 | City | 112 | 50 | 4 | Tier 4 workers can live here | uncommon |
      | 7 | Capital | 160 | 80 | 5 | best Hero and Legend odds | common |

    - ✅ **Every recruited villager needs its own bed** inside the settlement ("used / slots"; beds can limit you too).
    - ✅ **Total cap:** your level + 20 recruited villagers across all settlements (120 at level 100).
    - Performance: test 80 villagers in one place during the workers milestone.
    - ✅ **Renown points** (sum of 5 scores, breakdown in the Settlements tab):
      - **Size:** 1 per 100 player-placed blocks; each block type counts fully up to 1,000 blocks, then 1 point per 1,000 more.
      - **Variety:** 5 per block type placed (at least 16 of it).
      - **Homes:** 3 per bed.
      - **People:** 5 per villager living there, +5 per worker rank above Novice (Master = 25).
      - **Deeds (permanent):** raid defended 30 per Bad Omen level · bounty 10 · caravan wanted list 20 · 10 trades 1 · Hero living there 50 · Legend 100.
      - **Thresholds:** Camp 0 · Settlement 80 · Hamlet 250 · Village 600 · Town 1,200 · City 2,500 · Capital 5,000.
      - **Live renown:** only player-placed blocks count; Size/Variety/Homes/People count what's there now, Deeds stay forever. A level drops only after 3 days below the line, with a warning.
  - ✅ **Markers** (approved 2026-10-10):
    - **Bells and Ward Lanterns are spawn-proof at full height**, from the world bottom to the world top.
    - **Setting up = no fighting only** (no need to clear mobs or light caves): Bell 10 minutes, Ward Lantern 5 minutes; a fight restarts the timer. Banners work at once.
    - **Bells at least 2,000 blocks apart.** Markers of the same type can't overlap.
    - **Active limits:** 10 Ward Lanterns, 20 Banners (extras wait in your inventory). Bells and Gold Blocks capped at 3; waystones unlimited.
    - **Gold Block** loads its chunk + the 8 around it (48×48 blocks); inside your claimed area only. Waystones also claimed-area only. Bells, Banners and Ward Lanterns anywhere outside structures, dungeons and the End.
    - The Spelunker **keeps its torch rule** (user said no to dropping it).
  - 🟡 **Dungeons** (in progress):
    - ✅ **Part 1, how a run works:**
      - **The theme is encoded on the key** and shown in its tooltip. The portal frame is unique and fixed (chiseled tuff + copper, Nether-portal size). Use the key on it; everyone within 8 blocks enters together.
      - **Run shape (good start):** start room → 6 rooms → Lieutenant → 3 rooms → Boss → reward room, about 10 minutes; handmade rooms picked at random per theme; walls unbreakable, you can break blocks you placed. **Later: more randomness and open-space areas.**
      - **Mob level** = 12 + 3 per tier (tier 1 = 15). **+50% mob health per extra player**; loot per player.
      - **Lives per player:** 3 (tiers 1–9) · 2 (10–24) · 1 (25+). Death = back to the start room with your items, minus a life. Out of lives = sent home with your items; the run fails when everyone is out.
      - Leaving early: step back into the start portal (out for good, key used).
      - **Rewards:** a theme block cache in each room · Lieutenant 1 gear roll · reward room: dungeon loot table gear and books, sometimes worker rank items, Respec Tome at tier 20+ (very rare), and the next key.
      - **Overlord** (×20) replaces the Boss every 10th tier; real vanilla bosses (Wither, Warden, Elder Guardian) appear there.
      - **Next key (user):** it can come out lower, the same, or higher tier, or with a different theme. These odds could be boosted by a passive tree, possibly a **dedicated Dungeon passive tree**.
    - ✅ **Next key odds** (after clearing tier N): N−1 15% · N 55% · N+1 25% · N+2 5%; never below tier 1. Theme kept 60%, different 40%. New key modifiers: 0 50% · 1 35% · 2 15%. Each modifier on the cleared key moves 5% from lower to higher.
    - ✅ **Theme unlocks:** Overgrown, Frozen, Sunken from the start · Deep Dark after finding an ancient city · Nether after entering the Nether · End after dragon 1.
    - ✅ **Never stuck:** Elites and Warlords sometimes drop low keys (tiers 1–5); fortress and bastion chests can hold keys.
    - ✅ **Gate tree** (4th tab next to Fight, Craft, Ascendancy; same refund rule). **User: 60 nodes, only 30 points. The very powerful nodes get a drawback** (e.g. more monsters but you take more damage; more gear but fewer emeralds).
    - ✅ **Gate tree, 60 nodes** (approved 2026-10-10). Points: 1 per new highest tier cleared, up to 30. ⚠️ = drawback.
      - **Start (free) · Gate Sense:** the reward room shows your next key's exact odds.
      - **Keys (15):** 4× +3% higher tier · 4× +3% theme kept · Climber (+10% higher, from lower) · Steady (never lower) · choice node Theme Focus (pick 1 of 6, theme kept 80%) / Wanderer (theme changes more, new themes +10% loot) · ⚠️ Leap (N+2 chance ×2, modifiers roll more often) · ⭐ rival pair Pathfinder (pick next key's theme) / Gambler (up to N+3, down to N−2).
      - **Spoils (15):** 4× +4% cache blocks · 4× +3% extra Boss gear roll · Modifier Hunter (modifiers +50% loot) · Deep Caches (+25% cache blocks) · Lieutenant's Purse (+1 Lieutenant gear roll) · Bookworm (25% extra Boss book) · ⚠️ Hoarder (+1 reward-room gear roll, −50% emeralds from dungeon mobs) · ⚠️ Relic Hunter (+50% Relic chance, −1 life, never below 1) · ⭐ Golden Run (reward room ×2, dungeon mobs +25% health).
      - **Peril (15):** 4× +5% dungeon XP · 4× +3% dungeon elite chance · Elite Hunter (+25% elites, +1 roll each) · Marked (one Champion per room with a guaranteed drop) · Thrill (+10% XP per key modifier) · Overlord's Call (Overlord tiers give one extra key) · ⚠️ Horde (+50% monsters and loot, +20% damage taken) · ⚠️ Glass Cannon (+25% damage, −1 life, never below 1) · ⭐ Nightmare (always 2 modifiers, modifiers give double loot, only 1 life).
      - **Endurance (14):** 3× +3% max health · 4× +5% healing · Last Breath (+1 life tiers 1–9) · Spare Key (25% key back on a failed run) · Second Wind (on losing a life: Regeneration II 10 s + full hunger) · Rally (+5% damage per other party member) · ⚠️ Iron Will (+1 life at every tier, −20% loot) · ⭐ rival pair Lone Wolf (+30% damage and loot, solo only) / Warband (+10% loot per extra player for everyone, mobs +60% health per player instead of +50%).
    - ✅ **Part 2, themes (9)** (approved 2026-10-10):
      - **Overgrown:** mossy stone bricks, moss, vines, azalea, dripleaf · zombies, spiders, cave spiders, bogged, witches · hidden mobs, berry bushes, dripleaf over pits · caches: moss, azalea, spore blossom, glow berries, dripleaf, mud, bamboo, cocoa.
      - **Frozen:** packed/blue ice, snow, spruce · strays, skeletons, polar bears, wolves · powder snow traps, slippery ice by drops · caches: blue ice, packed ice, powder snow, calcite, spruce, goat horns (rare).
      - **Sunken:** prismarine, sea lanterns, half-flooded halls with air pockets · drowned (tridents), guardians, pufferfish · water/air · caches: all prismarine, sea lanterns, wet sponge, coral blocks, sea pickles, nautilus shells; tridents from drowned.
      - **Deep Dark:** deepslate tiles, sculk, candles, soul lanterns · skeletons, silverfish, endermites, shrieker-called elite · Darkness pulses, shriekers · caches: sculk, sensors, catalysts, shriekers, echo shards, reinforced deepslate (very rare).
      - **Nether:** nether bricks, blackstone, basalt, lava falls · wither skeletons, blazes, piglin brutes, magma cubes, ghasts · lava, fire, basalt pits · caches: gilded blackstone, crying obsidian, shroomlight, quartz blocks, soul soil, ancient debris (rare).
      - **End:** purpur, end stone bricks, end rods, void edges · endermen, shulkers, endermites, phantoms · levitation near the void · caches: purpur, end stone, end rods, chorus flowers, shulker shells, elytra (rare).
      - **Buried** (desert tombs): husks, skeletons, sand traps · caches: sandstone types, terracotta, suspicious sand.
      - **Trial** (copper vaults): breezes, bogged, trial spawners · caches: copper types, tuff types, breeze rods, heavy core (very rare) · unlocks after finding a trial chamber.
      - **Illager Keep** (dark fort): vindicators, pillagers, evokers, ravagers · caches: dark oak, cobblestone types, totem (rare) · unlocks after defending a raid.
    - ✅ **Part 3, bosses** (approved 2026-10-10): Lieutenant ×6 (1 rolled power) · Boss ×10 (2) · Overlord ×20 (3, second phase at half health). Each has a fixed signature move built from vanilla mechanics; powers roll from the theme's elite power list; big name card.
      - Overgrown: Mossbound (heals on moss/grass) · The Rotwood Witch (potions, calls cave spiders) · The Spider Mother (phase 2 web storm + swarm).
      - Frozen: Frostfang (calls 3 wolves) · The Pale Archer (slowness volleys, retreats on ice) · The Old Bear (slam freezes; phase 2 calls strays).
      - Sunken: Tidecaller (tridents + Riptide dash) · The Monument Guardian (laser + Mining Fatigue) · Elder Guardian (vanilla).
      - Deep Dark: The Unseen (invisible, shown by sculk sensors) · The Lost Keeper (netherite, Darkness aura) · Warden (vanilla).
      - Nether: Brute Captain (charges, calls piglins) · The Blaze Lord (fire rings, calls blazes) · Wither (vanilla).
      - End: The Shellguard (levitation volleys) · The Void Stalker (teleports behind you, floor blocks vanish briefly) · The Void Colossus (phase 2 splits into 3).
      - Buried: Tomb Guard (raises husks) · The Tomb Lord (sand kick = Blindness) · The Buried King (phase 2 sand pours from the ceiling).
      - Trial: Gale (wind charges) · The Vault Keeper (ominous spawner waves + giant bogged) · The Storm Breeze (phase 2 room fills with wind charges).
      - Illager Keep: Pillager Captain (volleys, buffs pillagers) · The Evoker Prime (fangs, vexes, can drop a totem) · The Siege Beast (ravager + evoker rider; phase 2 the evoker fights on foot).
      - **Overlord bonus:** one extra guaranteed Epic-or-better item; vanilla bosses also drop their vanilla reward (nether star, wet sponges, echo shards).
    - ✅ **Part 4, modifiers** (approved 2026-10-10). Each also moves 5% of next-key odds from lower to higher; "+X% loot" = bigger caches and better extra-gear chance; two modifiers add up.
      - **General:** Darkness (pulse every 20 s, +15%) · Heavy Hands (Mining Fatigue I, +15%) · Swarm (+50% mobs, +20% loot and XP) · Champions (elites ×3, +20%) · No Regen (natural healing off, +25%) · Volatile (mobs explode on death, no block damage, +15%) · Brittle (gear wears ×2, +15%) · Famine (hunger ×2, +10%) · Armored Foes (+50% armor, +15%) · Swift Foes (+25% speed, +15%) · Withering (hits give Wither I 3 s, +20%) · Fragile (−1 life, never below 1, +30%).
      - **Theme (+20% each):** Overgrown Spore Cloud (poison puffs every 30 s) · Frozen Blizzard (freeze unless near light) · Sunken High Tide (water rises) · Deep Dark Restless Sculk (shriekers ×2 as easy) · Nether Hellfire (floor patches catch fire) · End Void Pull (levitation ×2 long) · Buried Sandstorm (Blindness pulses) · Trial Ominous (all spawners ominous) · Illager Keep Raid Horns (pillager wave every 2 rooms).
  - ✅ **Dungeons: complete for Batch 4** (later: more random layouts and open-space areas; room templates designed during the dungeon milestone).
  - ✅ **The 24 dragons** (approved 2026-10-10; working titles, final names in Batch 5, anagrams welcome):
    - Summon with 4 end crystals (summoner ≈ level 4×N). Per-world count; everyone in the arena gets rewards. Every kill: ender chest page each, lore chapter, XP, 1 guaranteed dragon-loot item. **Act bosses (6, 12, 18, 24) also guarantee a Relic★.** The arena repairs itself after each fight. One new signature move per dragon, plus 1–2 earlier moves.
    - **Act I · The Waking:** 1 The First (vanilla) · 2 The Ember (fire patches) · 3 The Gale (wing gusts) · 4 The Crystal Keeper (crystals regrow once after 60 s) · 5 The Swarm Mother (phantoms on perch) · 6 ⭐ The Shade (invisible in flight, Darkness on perch).
    - **Act II · The Breaking:** 7 The Quake (outer ring falls briefly) · 8 The Frost (freezing breath) · 9 The Venom (poison + wither clouds) · 10 The Stormcaller (lightning) · 11 The Mirror (splits in two at half health, one real) · 12 ⭐ The Devourer (eats crystals to heal).
    - **Act III · The Remembering:** 13 The Shepherd (shulkers on pillars) · 14 The Hollow (roar angers all endermen) · 15 The Void Tide (void eats the edge until it lands) · 16 The Undertow (pulls players to the edge) · 17 The Ash Memory (wither skeletons + blazes) · 18 ⭐ The Ancient (sonic boom through blocks + Darkness).
    - **Act IV · The Ending:** 19 The Thousand Wings (2 smaller dragons) · 20 The Unmaker (topples pillars, rage when the last falls) · 21 The Timekeeper (rewinds 10 s of health once per phase unless stunned) · 22 The Twin Flame (two dragons) · 23 The Last Guard (moving caged crystals + minions from every act) · 24 ⭐ The Cycle (3 phases of the best moves, then the ending).
    - **Echo dragons:** endless; each mixes 3 random signature moves and is 4 levels higher than the last; XP + a dragon loot roll, no more pages.
  - ✅ **Events** (approved 2026-10-10; frequencies are drafts):
    - **Blood Moon:** ~1 night in 10 (never in the first 10 days); outside claimed areas +50% hostile mobs at +3 levels; settlements stay spawn-proof; dusk warning; +50% mob loot and XP.
    - **Harvest Moon:** ~1 night in 10; crops and saplings grow ×2, fish bite faster.
    - **Caravan:** from Hamlet, ~every 5 days, 1 day at your Bell; themed; sells one Wealth level above yours; wanted list ×2, purse 200–500.
    - **Ruined shrine:** rare (like ruined portals); small vanilla-block ruin; missing blocks shown as see-through outlines to fill in; lore fragment, XP, small loot; 10 = Pilgrim feat.
    - **Traveler in distress:** ~once per 2–3 days of travel; save the villager, it joins as a specialist (≥1 good trait) and follows you home; no free slot = waits at your nearest Bell for 3 days.
    - **Pillager camp:** from Settlement, ~every 10 days, 200–400 blocks from your Bell, map marker; ignored = bigger pillager patrols (never a raid; raids only from Bad Omen); first clear = Camp Breaker.
    - **Bounty board:** from Camp; 3 bounties, new each morning (kill, bring, visit); emeralds, XP, +10 renown.
    - **Rare sightings:** Bestiary page for rare vanilla mobs (pink sheep, brown mooshroom, blue axolotl, brown panda, charged creeper, skeleton horseman, spider jockey…); XP + Codex progress per first sighting.
    - **Roaming elite:** from level 20, ~weekly, 2 days; named Warlord in a rough map circle; guaranteed gear, key chance, Bestiary entry.
    - **Treasure map:** from chests, caravans, bounties; buried overworld block cache; one time.
    - **Festival:** day after a settlement levels up, from Village; villagers gather at the Bell, fireworks; purses ×2, +5 Trust with everyone there.
  - 🟡 **Codex rare-block classes and paths** (in progress):
    - ✅ **Counts toward Familiar/Mastered:** hand/tool-gathered blocks **plus dungeon-cache and treasure-map blocks**. Bought and worker-made blocks never count.
    - ✅ **Unique blocks:** Discovered by seeing one, never sold; the Gem Cutter can make budding amethyst once Discovered (the one exception to the Mastered rule).
    - ✅ **4 paths:** find (cluster harvest once Discovered) · dungeon caches and treasure maps · buy (Familiar → the fitting job's trades, Wealth by price) · worker (Mastered).
    - ⚠️ **Pass on price-based classes (user asked):** it fails. Hearthbook prices are trade prices, not gathering difficulty (stripped logs 5.97 = "very rare", concrete 4, tall grass 4, every ore 4, dragon egg 3; beds, banners, anvils, beacons, shulker boxes mixed in). Hearthbook prices stay for trading only.
    - ✅ **Fixed rule:** natural blocks classed by where they're found (curated list) · **made blocks** take the class of their rarest natural ingredient and **count when you make them yourself** · functional blocks move to the Items section · technical blocks skipped · 26.3's new natural blocks classed during the build.
    - ✅ **Natural classes:** Common (stone types incl. deepslate, tuff, calcite; dirt types, sand, gravel, clay, sandstone, snow, ice, obsidian; oak/birch/spruce; common flowers and grass, kelp, seagrass, sugar cane, pumpkin, mushrooms, glow lichen; netherrack, basalt, blackstone, soul sand/soil) · Uncommon (biome woods, red sand, badlands terracotta, mud, mangrove roots, podzol, mycelium, moss, azalea leaves, cactus, cocoa, berries, packed ice, powder snow, dripstone, crimson/warped stems, nylium, wart blocks, glowstone, magma, bone block, cobweb, bee nest) · Rare (coral, sea pickles, prismarine family, sea lanterns, amethyst, blue ice, sculk, sensors, shriekers, end stone, purpur, chorus plant, crying obsidian, shroomlight, froglights, pale moss, eyeblossoms, torchflower, pitcher plant, wither rose) · Very rare (sponge, spore blossom, gilded blackstone, sculk catalyst, ancient debris, chorus flower, sniffer egg, heavy core) · Unique (budding amethyst, reinforced deepslate, suspicious sand/gravel, dragon egg, spawners, vaults).
    - ✅ **Ores are buyable and farmable** (approved 2026-10-10):
      - **Classes** (ore blocks you mine yourself; Fortune doesn't change the count): Common coal, copper, iron · Uncommon redstone, lapis, gold, nether quartz, nether gold · Rare diamond, emerald · Very rare ancient debris.
      - **Buy (Familiar):** ore blocks in Toolsmith and Armorer trades, priced like their drop (fixes Hearthbook's flat 4).
      - **Farm (Mastered):** the Spelunker with an ore sample digs as usual; 1 ore block per N blocks dug (coal/copper 4 · iron/redstone/lapis/quartz 8 · gold 16 · diamond/emerald 64 · ancient debris 128); other dug blocks lost; only at depths where that ore spawns. A Master ≈ 2 diamonds or emeralds a day for 3 emeralds of wages, so no money printing.
      - **Smelter** also smelts ore blocks.
  - ✅ **Codex: complete for Batch 4.**
  - ✅ **Blueprints** (approved 2026-10-10):
    - ✅ **Saving:** Codex → Blueprints → Save build → right-click two corners with an empty hand (box outline) → name it. Sizes 16³ at start, 32³ with Charted, 64³ with Surveyor. **Allow bigger sizes later.** Saves block types and facing only (no contents, mobs or villagers).
    - ✅ **Ghost placement:** see-through preview, confirm with right-click, claimed areas only. Helpers: right block turns solid, wrong block red outline, pick-block on ghosts, layer view, materials list with have/missing and progress %. Cancel any time; built blocks stay.
    - ✅ **Move controls** (rebindable): nudge 1 block in 6 directions (arrows + Page Up/Down) · rotate 90° flat (R) · tilt on its side or upside down · mirror left-right or front-back · anchor (corner, centre, bottom centre) · snap to ground · move a placed ghost again (built blocks stay) · lock. Directional blocks turn with the ghost; blocks that can't tilt show yellow.
    - ✅ **Architect is back (B):** Tier 4, one per settlement, Cartography Table, places 5 ghost blocks per action from its copper chest, bottom-up (≈300/day Novice, ≈750/day Master); stops and lists missing blocks; its blocks count as yours for renown.
    - ✅ **Sources:** your saves · starter catalog (a few houses + **one setup per worker job**, also used as the Jobs page work-area picture) · blueprint pages from chests, caravans and bounties.
    - ✅ **Sharing and files:** send to a friend (Add/Decline like waypoints). Files are **Sponge schematic (.schem)** in a `blueprints` folder; **Litematica (.litematic) imports** too. Imports follow the size limits; building still needs real blocks.
    - Never: no instant pasting, no item copying.
- 🟡 **Batch 5 (lore drafts)**, by text. Order: 1 story spine (cycle, makers, dragons, Vestiges, player, ending) → 2 the 24 dragon names + chapter summaries → 3 villager background templates → 4 Heroes and Legends → 5 shrine and dungeon fragments → 6 Codex flavour text style. Source of truth: `RPG_Lore_Mod/bible/lore_source.md` (never paraphrase it; new lore only with the user's approval).
  - ✅ **Story spine (user's answers, 2026-10-10):**
    - **Makers:** a higher race uses our world as a testing ground. Their time runs differently; to them we are an accelerated simulation, and they can modify the world as they want.
    - **Player:** an ordinary person who may or may not slowly learn the truth, depending on their goals (someone who stops at dragon 1 and only does dungeons never learns the full truth).
    - **Dragon 24 ending = a choice:** delete this world, or reign over it and replace the higher race as its master.
    - **Each dragon = the memory of one past cycle**, and its chapter also carries **clues about the higher race**.
    - **Dungeons = the world's archive:** in a way, we relive events from the past.
    - **Not the faiths:** the 3 faiths don't know the truth. **A cult, hunted by the faiths, does.** Nobody listens to or trusts them, but they know things others don't.
    - Act names: The Waking · The Breaking · The Remembering · The Ending.
  - ✅ **Ending choice** (save file never touched): **Unmake** (ending scene, end poem, title "Cyclebreaker"; the world keeps running as a fading echo; Echo dragons = its last memories breaking apart) or **Reign** (ending scene, title "Sovereign"; master powers at your Capital's Bell: set time, set weather, change the next dungeon theme; Echo dragons = the makers trying to take the world back).
  - ✅ **World naming:** the mod reads the world like /locate (biomes anywhere, no chunk generation), groups big areas into named places (mountain ranges, forests, deserts, seas, rivers, landmasses as continents), names seeded by the world seed (same seed = same names), named as you approach. Names show as entry titles, map labels, a **Places** page in the Codex, and in villager backgrounds. Vanilla structures named when found; ruined shrines carry their own name and fragment. Borders are approximations.
  - ✅ **Name style (user):** no direction-based cultures for now. **Every place and person gets a fantasy English name** tied to its job, position or history; play with languages, be creative, but follow set rules. **Players can rename later.**
  - ✅ **Fixed lore places (user):** keep **Restena, Trenec and Oscat** in every world, plus a list of important continents, cities and so on to establish together.
  - ✅ **Naming rules (draft accepted, + French):** meaning first · English roots (and **some French**) · easy to read, ≤3 syllables per part, no real-world names · feature words by type (Peaks/Crags/Horns/Spire, Wood/Weald/Holt, Sound/Deep/Mere, Run/Bourne/Water, Waste/Sands, Fields/Lea/Moor, Delve/Hollow, Isle/Holm) · people = first name + byname from job, position or history · anagram layer for lore names only · no repeats within 2,000 blocks + rude-combo filter · players can rename.
  - ✅ **Geography (user):** **Trenec is the continent you spawn on.** Its regions by direction from spawn: **east = Restena**, **west = Swetren** (anagram of Western), **south = an anagram of "Southern"**, **north = an anagram of "Northern"** (names to pick).
  - ✅ **Oscat = the first coastal village you encounter.**
  - ✅ **Other villages:** each rolls a purpose (why it exists, what it produces) and gets an **anagram name of that purpose**.
  - ✅ **Don't rename vanilla structures** if possible, but put them in the lore: **ancient cities belong to a previous cycle**; **trial chambers are the makers' tests that Heroes through the ages had to complete, just like you.** (Drops "Proving Vaults" and "Echo Halls".)
  - ✅ **Cult name:** more French or Italian (to pick).
  - ✅ **Picks:** south = **Ruthenos**, north = **Thornner** (both anagrams). Continent Trenec = Restena (E) · Swetren (W) · Ruthenos (S) · Thornner (N).
  - ✅ **Cult = Vitera** (anagram of Italian *verità*, truth).
  - ✅ **Kept lore names:** **the Archive** (Gate dimension) · **the Threshold** (End arena) · **the Kesmar** (the makers; anagram of Makers).
  - ✅ **French = light flavour only** (not 1 in 3).
  - ✅ **French flavour:** about 1 name in 10 gets one French word or ending (mont, val, belle, -ville), never a whole phrase; about 1 villager in 10 gets a French first name.
  - ✅ **Villages:** purpose rolled from biome and surroundings; name = anagram of the purpose; second anagram for a repeat; then English qualifiers (Upper/Lower, Old/New, Ford, Point). Purpose shows in the entry title and Places page; its villagers' trades lean toward the product. List: Fishery Shyfire/Shirefy · Salt works Lastre/Stelar · Granary Ragnary/Garnary · Ranch Charn/Narch · Orchard Rochard/Chardor · Sawmill Willsam/Malswil · Masonry Morsany/Raymons · Mine Rimen/Mirne · Market Kermat/Tamker · Weaving Gwenavi/Gaviwen · Smithy Mythis/Thymis · Foundry Fondury/Dourfyn · Shrine Sirhen/Shiren · Tavern Vantre/Ravent · Healers Dymere/Meyred · Garrison Sarrigon/Grisaron.
  - ✅ **The 24 dragons, names and chapters** (approved 2026-10-10). Order goes back in time (dragon 1 = most recent cycle, dragon 24 = the first). Each name hides an anagram; each chapter = who lived, how it ended, one Kesmar clue (clearer each act).
    - **Act I · The Waking:** 1 Tulva (VAULT) Vault-builders, strongholds; libraries in enchanting-table glyphs · 2 Belaz (BLAZE) Kilnfolk; "the fire started in the sky" · 3 Morst (STORM) Windward towers; trial breezes = "the makers' breath" · 4 Dhars (SHARD) Glassmakers; broken things regrew overnight, as if edited · 5 Ilvig (VIGIL) the Sleepless, taken by phantoms; lights faster than the stars · 6 ⭐ Durhos (SHROUD) the Shrouded hid longest; reveal: the purges are chosen.
    - **Act II · The Breaking:** 7 Mortre (TREMOR) Deepdelvers, mineshafts; perfect-circle cracks · 8 Graciel (GLACIER) Frostwardens; "the sun was turned down" · 9 Nitox (TOXIN) Brewers; a sickness that spared animals · 10 Dunther (THUNDER) Coppersmiths, first copper golems; design from a trial chamber · 11 Megia (IMAGE) the Seers; every cycle has the same map (world seed) · 12 ⭐ Hergun (HUNGER) Hollow Kings stole at the crystals; reveal: a cycle ends when it gets too close to the truth.
    - **Act III · The Remembering:** 13 Scande (ASCEND) the Ascended, purpur cities; time runs differently beyond the edge · 14 Dreen (ENDER) the Taken came back as endermen; why they hate being looked at · 15 Nemad (AMEND) Edgewatchers; "the world was being rewritten" · 16 Basylas (ABYSSAL) Tidefolk, monuments; the Heart of the Sea is a hidden recorder · 17 Rethen (NETHER) Fortress-builders; no dimension is outside the test; the Wither was their last king · 18 ⭐ Selince (SILENCE) the Deep Ones built the ancient cities around their own gate; reveal: the Archive holds every Hero tested in the trial chambers, including your name, written before you were born.
    - **Act IV · The Ending:** 19 Kadre (DRAKE) Dragonriders; the dragon is the makers' tool that ends each cycle · 20 Voltre (REVOLT) the Rebels toppled the pillars; the Vitera keep their teachings · 21 Mutine (MINUTE) Clockmakers; our 15,000 years are an afternoon for the Kesmar · 22 Larvi (RIVAL) the Two Crowns; "a test needs a control" · 23 Tenrys (SENTRY) the First Heroes became the Last Guard · 24 ⭐ Iginor (ORIGIN) the Seeding; the dragon was their first creation; then the choice, Unmake or Reign.
  - 🎯 **User idea:** after a dragon kill, the end gateway leads to a dungeon or room where the lore slowly fills in with each dragon: **a final repository players can build around in the End.**
  - ✅ **The Vestige** (lore hall, approved 2026-10-10):
    - **A very big island far out in the End, very low in the End**, reached through the end gateway (every gateway leads there; a return gateway goes back to the arena). **No mob spawns on the island.**
    - **First build** (when the island first appears): **the Entrance** section and **a waystone** are built; the rest of the hall shows as **ghost**, and **that space can't be built in**, so it never blocks the hall later.
    - **Layout:** the Entrance, 4 wings (one per act) × 6 alcoves (one per dragon), a sealed centre.
    - **Each kill:** dragon N's alcove rebuilds itself over a few seconds (like a ghost filling in), with a lectern (the chapter as a book, also in the Codex), a mural painting of that cycle, and the dragon's head on a pedestal. Act bosses (6, 12, 18) open the next wing.
    - **The ending happens at the centre** after dragon 24: an altar for the choice. Unmake = the hall fades grey and ghostly; Reign = the centre becomes a throne room where master powers also work.
    - **Building:** free on and around the island, outside the hall's protected space. Markers stay banned in the End, except **the Vestige waystone, placed by the first build; trips to it always cost 1 emerald.**
    - Art: 24 murals need pixel art (the user's Screenshot-to-paintings project can help).
  - ✅ **Falling into the End void** (user): instead of taking damage and dying, you appear at the overworld's world top, still falling, above your last spawn point, **with Slow Falling until you land**.
  - ✅ **Villager backgrounds** (approved): name (first + byname) · birthplace (60% a real nearby village, 30% a named place in its region, 10% a far land) · family trade · one of ~40 turning points in 5 kinds (hardship, journey, ambition, loss, strange; some give the byname, e.g. Ashborn, Saltlost) · what they want now (drives requests) · one line per trait · **rare lore hooks, 1 villager in 50** (glyph book, gate dreams, Vitera listener) · history grows over time. Overview shows 2 lines, About the full story.
  - ✅ **Cosmology (user, 2026-10-10):**
    - **Overworld** = our reality, managed by the makers (the Kesmar).
    - **Nether** = what the world will become if we let the makers continue.
    - **The Sift** (a new dimension Mojang announced) = what the world could become if we stop them. In the lore it's a **legendary place rumoured to be reachable**: some say another world, some say what the world was, or what it could be. **The cult (Vitera) knows.** Lore to update once Mojang reveals more.
    - **The End** = a glitch that puts you on the same "level" as the makers. They hide their true form and try to kill you with a dragon, **the same dragon they unleashed on the world again and again, starting a new cycle each time.**
    - **Unknown:** what makes the makers launch a new cycle, and why some cycles last longer than others. No exact dates or durations exist outside this cycle (most records are destroyed). "15,000 years" becomes what people believe, not a fact.
    - **Regional beliefs** read the cycle through their faith: gods keeping, showing or proving their power, punishing or saving the people, and so on.
    - Chapter tweaks for consistency: 17 Rethen (the Fortress-builders saw in the Nether what the world becomes if the makers continue) · 13 Scande (the End as the glitch level).
  - ✅ **Faiths (user):** major faith 1 is **linked to the elements**; major faith 2 is **a belief similar to Zoroastrianism**; the small faith is **a mix of Christianity, Islam and Judaism**. Names to pick.
  - ✅ **Faith content kept (names rejected):** elemental faith = four Powers (Flame, Tide, Stone, Gale); the cycle is "the Turning", the elements washing the world clean when people grow proud. Light faith = the Bright Flame against the Lie, eternal temple fires, "good thought, good word, good deed"; the cycle is "the Ordeal". Small faith = the One who promised to save a faithful few, prophets, a book, waiting for a promised one; the cycle is "the Judgment"; quietly persecuted by the two big faiths.
  - ✅ **Regions:** Restena = light faith (state faith backing the council of 3) · Swetren and Ruthenos = elemental faith · Thornner = a mix · the small faith in hidden groups everywhere · the Vitera hunted by all three.
  - ✅ **The Sift and the faiths:** every faith wants to **conquer the Sift**, each sure it is its own promised realm (elemental: the world before the first Turning; light: the realm of the Flame; small faith: the promised land). The Vitera know the truth: what the world could become if the Kesmar are stopped.
  - ✅ **Faith names and ranks** (approved 2026-10-10):
    - **The Primacy** (elemental; followers **Primas**): Seeker → Initiate → Keeper (sworn to one element, e.g. Keeper of the Tide) → Warden (runs a shrine) → Elder (4, one per element) → the Arbiter (speaks for all four).
    - **The Bright Path** (light; followers **Walkers**): Spark → Candle → Lantern (keeps a temple fire) → Beacon (high priest of a city) → the Dawnbringer (head of the faith, in Restena).
    - **The Promise** (the One; followers **the Promised**): Listener → Believer → Reader (may read the Book aloud) → Shepherd (leads a hidden circle) → the Voice (a prophet; nobody knows if one lives today).
    - Regions with names: Restena = Bright Path · Swetren and Ruthenos = Primacy · Thornner = mix · the Promise hidden everywhere · Vitera hunted by all three.
  - ✅ **Heroes and Legends** (approved 2026-10-10). Heroes = famous people of this cycle (from the bible's roles); Legends = touched by past cycles or the truth, each appearing only after a given dragon and unlocking one extra lore fragment. Each appears once per world; 3-step request (arrive and ask → do the deed → return), then joins and takes a slot; 3 good traits, Wealth 5, one unique offer.
    - **Heroes:** Corvin Ironvow (retired Lord Commander of Restena, Walker; defend a raid; a Superior shield or sword weekly) · Aveline Saltmere (merchant princess, Swetren, Prima; fill 3 caravan wanted lists; one far-land luxury weekly) · Brannoc Deepdelve (miners' guild master, Thornner, Keeper of Stone; mine 128 deepslate ores; ore blocks 25% cheaper) · Mother Elsbeth (plague ward, secretly Promised; cure 3 zombie villagers; golden carrots, healing potions) · Tamsin Quickfoot (smuggler queen; steal back her ledger from a pillager outpost; one never-sold item weekly) · Odo Archwright (master mason of Trenec's aqueducts, Walker; restore 3 ruined shrines; rare blueprint pages) · Ysolde Brightwick (temple-fire Lantern, Walker; 3 blaze rods; candles, soul lanterns, Fire Resistance) · Mael Stormwake (Oscat sea captain, Keeper of the Tide; dig up a shipwreck's treasure; treasure maps, nautilus shells) · Garrick Thornwood (northern ranger, Keeper of Gale; kill a roaming elite; rare-block cache maps) · Lisette Vane (copper golem factory engineer who wants them freed; build a copper golem and keep it 3 days; copper golem statues, redstone parts).
    - **Legends:** Iselda Trialborn (IDEALS; after dragon 3; passed every trial chamber, barely ages; open an ominous vault; breeze rods, wind charges) · Coreal (ORACLE; after dragon 6; Vitera Elder, arrives hunted; protect her from a Walker inquisitor patrol; glyph-decoding books, lore fragments) · Fennick Dreamwake (after dragon 12; dreams of the Sift; sleep 3 nights near him; Sift rumours) · Ohce (ECHO reversed; after dragon 18; hermit by an ancient city, Deep One descendant; 8 echo shards; sculk, recovery compasses) · Wyn Embersky (after dragon 19; last of the Dragonriders' line; a bottle of dragon's breath; dragon's breath, phantom membranes) · Trevol the Unbowed (REVOLT; after dragon 20; heir of the Rebels; destroy 3 end crystals in one fight; end crystals).
  - ✅ **Fragment structure** (approved): shrines in 5 kinds (Primacy 10 · Bright Path 10 · Promise 8 · Vitera 6 · Unknown 6, written in enchanting glyphs and readable with Coreal's books); dungeon diaries of 8 pages per theme, each replaying one cycle (Overgrown = Brewers/Nitox · Frozen = Frostwardens/Graciel · Sunken = Tidefolk/Basylas · Deep Dark = Deep Ones/Selince · Nether = Fortress-builders/Rethen · End = Ascended/Scande · Buried = Kilnfolk/Belaz · Trial = First Heroes/Tenrys · Illager Keep = Hollow Kings/Hergun, illagers being their remnants); a full diary gives XP + its story in the Codex. Writing rules: 2–4 sentences, ≤60 words, a real voice, truth never stated outright, one hidden chapter link, anagram rule, no modern words.
  - ✅ **All 112 fragment texts approved** (2026-10-10): `C:/Users/carpe/.claude/plans/vestiges-lore-fragments.md` (40 shrines + 9 diaries × 8 pages). Deep Dark page 6 inserts the player's name; Unknown shrine 4 ("let them explain it with gods") is the right level.
  - ✅ **Codex flavour style** (approved): two lines per entry, line 1 at Discovered (Bestiary 10 kills / first sighting), line 2 at Mastered (Bestiary 250 kills); about 1 entry in 5 hides a lore hint in line 2 · one sentence per line, ≤20 words · a curious traveller's notebook that quotes villagers and faiths and never knows the full truth · tips must be true in game · light humour, no modern words · shaped blocks and colour variants share their base entry · generated places get a line from their name and type.
  - ✅ **All 414 Codex entries approved** (2026-10-10): `vestiges-codex-flavour.md` (88 mobs · 62 biomes · 32 structures · 180 natural blocks · 52 items). Lines for 26.3's newest content stay vague on mechanics until checked in game (Codex milestone).
- ✅ **Batch 5 (lore) approved** on 2026-10-10. **Phase A is complete.** The wheels page is not refreshed: the text log is enough (user).

**Phase B status**
- 🟡 **M0 Foundation** started 2026-10-10 in `RPG Mod/` (user: "go"). Built so far: Gradle/Loom setup copied from Hearthbook (`dev.bat`, JDK 25, MC 26.3, Fabric API 0.161.0+26.3), CI workflow, LICENSE (all rights reserved), `CLAUDE.md`, `docs/` (this log + lore), server/client config with every system switchable, datapack content loading (`DataType`, first type = the 24 feats), networking (request → JSON snapshot), player profile attachment, `/vestiges` admin commands, hub shell (H; Feats and Settings tabs live), own look-at HUD with switchable lines and a short "New" tag. `gradlew build` + JUnit pass. **Playtest in the clean `modtesting` ATLauncher instance passed** (all checks; `/reload` had no confirmation, fixed with a green operator chat line "Vestiges data reloaded: N feats"). **First commit `6bac62d`** (local, user OK). Pushed to the private repo **https://github.com/fattofrencho-png/vestiges** (`main`). ✅ **M0 approved** (2026-10-10).
- 🟡 **Map and waystones** (plan agreed 2026-10-10: waystones now with temporary rules; JourneyMap-style block colours; minimap top-left by default). Built and deployed to modtesting, waiting for playtest:
  - **Map:** client-side scanning of seen chunks into 512×512 region images (`.minecraft/vestiges/maps/<world>/<dimension>/<layer>/r.x.z.png`), colours = average top-face texture × biome tint, relief shading, water depth. Layers: Surface, underground 16-block slices (Caves / Deepslate), Nether slices. Minimap (size 96–192, zoom 1–4, any corner, coords + layer name), world map (M or hub Map tab: drag, zoom, layer and slice buttons, Center on me, right-click = new waypoint), waypoints (add, delete, colours), death markers (last 5), "Go there" = a tracked waypoint pushed to the vanilla Locator Bar (clears on arrival within 6 blocks, dimension change or Clear pin), sharing waypoints (toast + "Shared with you" Add/No; settings: Ask/Off, blocked players).
  - **Waystones:** Waystone item = vanilla lodestone with name, glint and a marker; `/vestiges give waystone [targets] [count]` until the First Waystone feat (M5); placed anywhere for now (claimed areas only from M5); right-click → terminal (list with distance and cost, rename, share with a player); travel = same dimension only, stand within 6 blocks, no travel within 10 s of a fight, 1 emerald per 250 blocks (min 1) from inventory (Bank from M2), creative free; owner-only breaking returns the Waystone item; cyan diamond markers on maps.
  - 16 JUnit tests pass.
  - **Playtest 1 (2026-10-10):** all good except: map drag and right-click (26.3 mouse buttons are left = 1, right = 3, fixed) · Clear pin now always confirms · terminal can't be opened during a fight · map and waystone messages go to the chat box · grey grass fixed (surface uses the MOTION_BLOCKING heightmap so plants don't hide the ground; snow layers stay white; particle-fallback blocks are tinted) · minimap shake fixed (view centre snapped to the pixel grid) · map tiles moved to a versioned folder (`maps/v2/…`, old grey tiles start fresh; waypoints stay in `maps/<world>/waypoints.json`). **Retest passed** (user: "all good"). **Icons (user):** vanilla textures instead of text symbols: death = skeleton face, waystone = lodestone top (compass rose), waypoint = vanilla map banner tinted with its colour, player = vanilla map arrow; same icons on minimap, world map and side list. Built; to be checked in the next playtest together with M1 (user: "let's work on more before I test again").
- 🟡 **M1 Character**, split in M1a Levels · M1b Passive trees · M1c Gear (tested together with the map icons).
  - ✅ **M1a Levels — decisions (2026-10-10):**
    - **XP system reworked for our balance:** vanilla XP orbs no longer give levels (they only feed Mending); the XP bar mirrors the Vestiges level. Kill XP = 10 + 4 × mob level × rank multiplier (Champion ×2, Elite ×3, Warlord ×5, Lieutenant ×6, Boss ×10, Overlord ×20); level gap: full XP up to 5 levels below, −10% per extra level (min 10%), +5% per level above (max +50%). Need per level = kill XP at that level × (8 + 0.9 L): L1 125 · L10 850 · L20 2,340 · L50 11,130 · L99 39,423 (≈1.4 M to 100); Paragon 40,000 each. Ores: coal/copper 2 · iron/redstone/quartz 4 · lapis 6 · gold 8 · diamond 25 · emerald 30 · ancient debris 60 (not with Silk Touch). Discovery: new biome 100 · first of each structure type 150 · first Nether 500 · first End 1,000 · Codex later. Feats, dungeons (200 × tier), dragons (2,000 × N) in their milestones. Crops, breeding and smelting give none.
    - Enchanting and anvils cost no levels (real costs in M1c). Building XP waits for renown (M5).
    - Death: lose 5% (L1) → 15% (L100) of the current level's requirement, never a level.
    - **Gravestone:** items go into the player's own head at the death spot (owner or operator collects by right-click or breaking). After **3 Minecraft days** it turns into a skeleton skull keeping only the **most valuable item** (by emerald price); warning 1 day before; **the map marker disappears** when it crumbles (and when collected). Marker label shows time left.
    - Paragon: 40,000 XP each, +0.5 max health and +0.5% damage each, halved after Paragon 50.
    - Built: LevelCurve/PlayerLevel (attachment, copied on death), LevelService (XP bar mirror, +1 max health per level as attribute modifier, notices), XpSources (kills by mob-level formula and layer, ores, biome/structure/dimension firsts), mixins (no vanilla XP/levels on ServerPlayer, free enchanting, grave hook before inventory drop, player damage × level multiplier in LivingEntity.hurtServer), GraveService, ItemValue (Hearthbook prices), HUD (10 % hearts with number + absorption, "+XP" pop-ups, level-up banner, Paragon label), Character tab, `/vestiges level set` and `/vestiges xp add`. 22 JUnit tests pass. Deployed, waiting for the combined playtest.
  - 🟡 **M1b Passive trees:** plan approved (tree screen with Fight/Craft/Ascendancy tabs drawn like the approved wheels; refunds 1 emerald + 1 per 10 levels from inventory until the Bank; ~70% stat nodes mostly as vanilla attributes; nodes depending on later systems light up then, tooltip says when). Wheels exported: Fight 295 nodes, Craft 215.
    - ✅ **Masteries (pick 1 of 2):** Melee Momentum (+4% per consecutive hit on one target, max +20%, resets after 3 s) / Sunder (hits −10% target armor 5 s) · Defense Fortress (+15% armor standing still) / Riposte (shield block → next hit within 3 s +30%) · Vitality Blood Pact (heal 2% of damage dealt) / Resilience (+20% natural regen, −10% hunger drain) · Mobility Runner's High (after 3 s sprinting +10% speed) / Acrobat (+0.5 step height, +10% jump) · Ranged Deadeye (full-draw bow +20%) / Long Shot (+25% projectile damage beyond 20 blocks) · Mining Vein Reader (ores within 3 blocks sparkle 5 s) / Stonebreaker (+25% mining speed on stone, deepslate, netherrack) · Crafting Thrifty Enchanter (enchanting −20%) / Brewmaster (brewing +30% speed) · Building Long Reach (+1 reach holding a building block) / Quick Builder (+25% placing speed) · Gathering Harvest Moon (+15% crop yield) / Forester (+25% chopping, saplings +25%) · Exploring Wayfinder (waystone trips −25%) / Sea Legs (+30 s oxygen, +15% swim speed).
    - ✅ Notable "Woodsman" renamed **Timberjack** (clash with the ascendancy). "−% enchanting XP cost" nodes become "−% enchanting cost" once M1c sets the cost.
    - ✅ **Ascendancies in full** (travel → crafting node (tool, V toggles) → travel → fighting node (weapon)):
      - **Harvester** (Sword + Hoe): Reaper [+5% sword dmg → Reaper: harvest + replant all ripe crops in 5×5 → +5% sweeping dmg → Scythe Sweep: sweeps hit all enemies within 3 blocks in front, +20% sweep dmg] · Tiller [+5% sword dmg → Tiller: tilling tills the 3×3 → +3% sword crit chance → Grim Harvest: sword kills restore 10% max health].
      - **Woodsman** (Crossbow + Axe): Lumberjack [+5% crossbow dmg → Lumberjack: fell whole tree (≤64 logs) + replant → +5% reload speed → Splinter Bolt: bolts pierce 1 extra enemy, +15% dmg] · Timber Sense [+5% crossbow dmg → Timber Sense: axes chop 30% faster, stripping strips the whole trunk → +5% crossbow dmg → Lockdown: crossbow hits slow 30% for 2 s].
      - **Delver** (Mace + Pickaxe): Vein Sense [+5% mace dmg → Vein Sense: whole connected vein ≤32 → +5% smash dmg → Aftershock: smashes slow nearby enemies 3 s] · Excavator [+5% mace dmg → Excavator: pickaxe 3×3 → +5% armor holding a mace → Stoneheart: no knockback holding a mace, +10% armor].
      - **Wayfarer** (Bow + Shovel): Earthmover [+5% bow dmg → Earthmover: shovel 3×3 of dirt, sand, gravel, snow, clay → +5% draw speed → Pinning Shot: fully drawn arrows slow 2 s] · Trailmaker [+5% bow dmg → Trailmaker: paths 3 wide, +15% speed on paths → +5% arrow speed → Hunter's Mark: shot mob glows and takes +10% dmg from all for 10 s].
      - **Lancer** (Spear + Shears): Cluster Harvest [+5% spear dmg → Cluster Harvest: whole connected cluster ≤32 of coral, moss, dripstone, amethyst, glow lichen, vines, flower patches (Discovered blocks once the Codex exists) → +0.2 reach holding a spear → Impaler: charged spear attacks pierce to the enemy behind] · Shearmaster [+5% spear dmg → Shearmaster: shearing a sheep shears all sheep within 5 blocks, shears cut leaves/wool/cobwebs 50% faster → +3% spear attack speed → Long Reach: +1 attack reach holding a spear].
      - 1st ascension: elite and Codex steps skipped until M3/M4 (checklist says "not in the game yet").
      - 2nd ascension (level 60 +): Harvester 2,000 ripe crops · 500 sword kills · 50 elites | Woodsman 3,000 logs · 500 crossbow kills · 50 elites | Delver 1,000 ores · 500 mace kills · 50 ancient debris | Wayfarer 5,000 shovel blocks · 500 bow kills · 40 biomes | Lancer 500 spear kills · 500 cluster blocks · 64 coral blocks.
    - ✅ Choice-node pairs approved (all good).
    - ✅ **Ascendancy path nodes reworked (approved 2026-10-10, after playtest):** every path node in an ascendancy is different; the 1st fits the branch's tool power, the 2nd its weapon power. Harvester: Reaper +6% crop yield → Reaper → +5% sweeping damage → Scythe Sweep · Tiller +15% seed drops → Tiller → +5% sword damage → Grim Harvest. Woodsman: Lumberjack +10% chopping speed → Lumberjack → +5% reload speed → Splinter Bolt · Timber Sense +15% sapling and apple drops → Timber Sense → +5% crossbow damage → Lockdown. Delver: Vein Sense +3% extra ore drops → Vein Sense → +5% smash damage → Aftershock · Excavator +10% mining speed below y=0 → Excavator → +5% armor holding a mace → Stoneheart. Wayfarer: Earthmover +10% shovel speed → Earthmover → +5% draw speed → Pinning Shot · Trailmaker +10% discovery XP → Trailmaker → +5% bow damage → Hunter's Mark. Lancer: Cluster Harvest +10% underwater mining speed → Cluster Harvest → +5% spear damage → Impaler · Shearmaster −10% tool durability loss → Shearmaster → +3% spear attack speed → Long Reach.
    - **Playtest 1 of M1 (2026-10-10):** levels, XP, hearts, free enchanting, graves, damage bonus, tree screen, search, utility powers (Vein Sense), Thick Hide all OK. Changes asked: bigger "+XP" pop-up (done; may move with the new UI), a discovery toast in the middle of the screen ("New biome discovered +100 XP", done, also for structures and dimensions), the Ascendancy tab drawn as a tree (done), Thick Hide icon position to revisit with the new UI. Later: small icons on the bigger nodes.
    - **Built (2026-10-10), waiting for the combined playtest:** tree data from the approved wheels (Fight 295 nodes, Craft 215, 5 ascendancies) loaded from datapacks and sent to the client; tree screen (hub Character tab → "Open the passive trees", or an unbound key): Fight / Craft / Ascendancy tabs, drag and zoom, pick a start, click to take, pick-one popups for choice nodes, right-click refund and click-to-switch for emeralds (inventory until the Bank), rival keystones lock, tooltips say "Not active yet: comes with …" for effects of later systems. Ascendancy tab: first-ascension checklist (elite and Codex steps skipped until M3/M4), 5 cards, branch nodes in order, second ascension at level 60. Effects live now: vanilla-attribute stats, weapon and projectile damage, crits, damage taken, regen, healing, hunger, XP, mining speeds, drops (crops, seeds, saplings, apples, ores, flint), durability, waystone discount, bow draw, crossbow reload, arrow speed and saving, and the mechanics (Executioner, Sniper, Skirmisher, Deadeye, Last Stand, Thick Hide with a HUD icon, Momentum, Sunder, Riposte, Fortress, Blood Pact, Grim Harvest, Hunter's Mark, Pinning Shot, Lockdown, Splinter Bolt, Aftershock, Stoneheart, Iron Will, Unshakable, Second Wind, Tide Caller, Runner's High, Sea Legs, Ever-Mending, reach nodes, Steady Footing, mount nodes) plus every utility power behind V. Later: enchanting/anvil nodes (M1c), Codex (M4), farming and food (M4), building (M5), and a few combat moves (Scythe Sweep, Whirlwind, Impaler, wider smash, parry, potions, tridents, Vein Reader). Admin: `/vestiges tree reset <targets>`, `/vestiges tree counter <targets> <key> <value>`. 29 JUnit tests pass.
  - 🟡 **M1c Gear — decisions (approved 2026-10-10):**
    - **Rarity look:** the item name takes the tier colour and the tooltip gets a tier line. Colours (from RPG Craft): Common white · Uncommon grey · Superior light blue · Rare dark blue · Epic gold · Legendary red · Relic deep brown with a thin white outline (changed after the playtest; no flicker), plus ★ per VI enchant.
    - **Enchanting table:** your Vestiges level must be at least the number shown (vanilla rule, so the best rolls need level 30); cost = vanilla lapis + emeralds = number shown ÷ 5, rounded up (a level-30 roll costs 6).
    - **Anvil:** emeralds instead of levels, the same number vanilla asks in levels (rename 1). "Too Expensive!" still at 40 (tree nodes raise it). The Bank pays repairs from M2.
    - **Level V at the table:** arcane blocks in the bookshelf ring (crying obsidian, gilded blackstone, shroomlight); each gives the 3rd slot +2.5% chance to come out one level higher (IV → V), up to 8 blocks (20%); Arcane Insight adds +15%.
    - **Gear ladder (RPG Craft):** level 1 wood, stone, leather · 5 chainmail, bow, shield · 10 copper, crossbow · 15 gold · 20 iron, turtle helmet · 30 diamond, trident, mace · 40 netherite, elytra; spears follow their material. Gear above your level works at half its armor, damage or mining speed.
    - **Armor formula** (armor ÷ (armor + 20), max 85%) moves to M3 together with mob levels; toughness counts as half an armor point.
    - **Built (2026-10-10), waiting for playtest:** every enchantment rescaled to I–VI in a built-in datapack "Vestiges enchant levels (I-VI)" (on by default in new worlds, `/datapack disable` turns it off; max level IV so the table, loot, trades and /enchant stop at IV; all special cases from Batch 3); loot tables read Fortune/Looting at the vanilla-equivalent level (IV = vanilla III); Silk Touch tiers; Flame burn times; table: level requirement = your level, lapis + emeralds (shown in the tooltip), 3rd-slot level V from arcane blocks and Arcane Insight, lapis refund node; anvil: emerald price ("Cost: N emeralds"), limit 40 + tree, repair-penalty nodes, Runic Memory, IV + IV = V (VI kept, never made); rarity tooltips (colour, tier line, Relic flicker + stars); gear ladder (half armor/damage/mining speed/shield block above your level, red tooltip line; elytra has no stat to halve); trim stats + set bonuses (copper placing speed and emerald trade discount wait for M5/M6); new switch `gear`. 35 JUnit tests, including one that decodes every rescaled enchantment with the game's codec and one that loads every changed game class with the mixins applied.

- ✅ **M2 Inventory — approved after playtest 2 (2026-10-11, "overall much better, didn't find issues").** Controls agreed: mouse wheel reaches belt / Tool / Weapon; Ctrl + wheel inside the selected rack; Alt + wheel picks the offhand slot; Ctrl + 1–8 = tool rack position; Alt + 1–6 = weapon rack position; optional Tool slot / Weapon slot keys (unbound); Z = spyglass zoom from the belt.
- 🟡 **M2 Inventory — decisions (2026-10-10):**
  - **Layout as approved in Batch 1 (layout A, version 7):** tool rack (8) pickaxe, axe, shovel, hoe, shears, fishing rod, flint and steel, brush · weapon rack (6) sword, mace, spear, trident, bow, crossbow + arrows slot · tool belt (4) compass, clock, map, spyglass · elytra slot over a 2-slot offhand rack · ender chest, totem and Bank slots under the crafting grid · 3 inventory rows + hotbar (no tree row).
  - **Auto-tool:** picks the right tool from the rack when you start breaking a block; a setting you can switch off.
  - **Tool belt effects:** map on the belt = the minimap shows (no map, no minimap; the world map on M stays free) · compass = N/E/S/W around the minimap · clock = time of day in the HUD · spyglass = zoom with a key without holding it.
  - **Bank:** drop emeralds on it to add them; left-click takes 64, right-click takes 1; pays first (then the inventory) for waystones, anvil costs and tree refunds.
  - **Death:** the new slots go into the gravestone; the Bank is kept.
  - **Creative:** the vanilla creative screen stays; the new slots show in the survival inventory.
  - **Playtest 1 of M2 (2026-10-10):** loads and creates worlds (after fixing a crash: mixin field initialisers did not run for the player). Changes asked and done: recipe book no longer covers the inventory and its button sits at the bottom of the rack panel; "+XP" pop-up above the Tool and Weapon slots at the end of the XP bar; outline icons for every empty slot (Vestiges' own, vanilla style); Alt + Scroll now picks which offhand slot is in your hand (white outline) instead of swapping items; both offhand slots take anything (the light-source idea was dropped); the arrows slot stays in the 4th column.
  - **Built (2026-10-10), waiting for playtest:** 23 extra slots on the player (saved with it, part of the inventory menu so vanilla syncs them): tool rack 8, weapon rack 6, arrows, belt 4, second offhand, elytra, totem, ender chest; Bank and page names. Survival inventory widened for the right panel (Tools, Weapons + arrows, Tool belt), frames for elytra / second offhand / ender chest / totem / Bank, recipe book button moved under the result, tooltips on empty slots and the Bank. Hotbar: belt slot left of the offhand, Tool + Weapon slots right of the hotbar with rack dots; mouse wheel order belt → 1–9 → Tool → Weapon; Ctrl + wheel inside a rack, Alt + wheel swaps the offhand rack (controls to be settled once the UI is set). Held item = the rack item (use, drop, wear); F swap blocked while a rack slot is held. Auto-tool (setting, on by default). Shift-click sends items to their rack. Elytra slot glides and wears (wings render, also for other players); totem slot saves you; bows and crossbows draw from the arrows slot. Bank: drop emeralds on it, left-click 64 / right-click 1; pays first for waystones, anvil and tree refunds; kept on death. Ender chest slot: right-click opens your ender chest; ender chest screen shows a page list (page 1 renamable, pages 2–25 show their dragon) and a Close button. Belt: map = minimap shows, compass = N/E/S/W, clock = "Day N · hh:mm" under the minimap, spyglass = zoom key (Z for now). Creative keeps the vanilla screen (extra slots hidden there). Gravestones take the extra slots. 37 JUnit tests, including loading every server and client mixin target.

- ✅ **M3a Mobs — approved after playtest 2 (2026-10-11, "all good").** M3b Loot plan next.
- ✅ **M3 Mobs & loot — approved after playtest (2026-10-11, "all good"): M3a Mobs and M3b Loot both done.** Next: M4 Codex & farming.
- ✅ **M3b Loot — decisions (approved 2026-10-11):**
  - **Loot level** = 75% of the opener's level + layer bonus, capped per layer (Surface 20 · Caves 40 · Deepslate 50; Nether and End = deepslate); each player gets loot for their own level.
  - **Per-player chests:** natural chests, barrels and chest minecarts give each player their own loot on first open; your copy keeps what you leave; breaking a loot chest needs sneaking and drops only the chest; a faint sparkle on loot chests you haven't opened.
  - **Contents:** each structure keeps its theme; vanilla gear and enchanted books removed; one loot-level roll of RPG Craft's 6 pools (gear and books · ores and gems · uncraftable building blocks · decoration · useful items, food, potions · special finds: kit bundles, collectibles); stacks grow with loot level; RPG Craft's 56 chest tables ported as JSON.
  - **Bands:** RPG Craft's 11; outside dungeons loot level stops at 50 (band 8); bands 9–11 for dungeons. Gear materials follow RPG Craft's ladder (netherite from loot level 40).
  - **Gear rarity:** Batch 3 odds table; the rarity is rolled first, then an item is built to match it; books too.
  - **Mob drops** (player kills only): vanilla drops stay; 1 kill in 6 drops 1–2 emeralds (1–3 from mob level 25; was 1 in 10); ingots by mob level 1 kill in 5 (was 15%) (copper and gold nuggets low, iron and gold from 16, small diamond chance from 36); no trim templates from zombies.
  - **Elites:** Champion 10% gear · Elite 25% gear + 10% book · Warlord always gear + 5% level VI book; always emeralds (Champion 2–4, Elite 4–8, Warlord 10–20); elite gear never Common.
  - **Rarity sparkle (user):** Rare-or-better gear and books on the ground show a small sparkle in their rarity colour, the same style as the unopened-chest sparkle (not RPG Craft's beams).
  - Fishing treasure, archaeology, bartering and trial vaults stay vanilla for now. Admin: `/vestiges loot roll <level> [rarity]`, `/vestiges loot chest <level>`. Switches: `loot` (bands and drops) and a new `per_player_loot`.
  - ⚠️ **User: everything must be balanced.** Check the numbers (rarity odds, emerald income, chest value per band) before the playtest.
  - **M3b Loot built (2026-10-11), waiting for playtest:** `tools/build_loot.py` (adapted copy of RPG Craft's generator; RPG Craft untouched) writes the 11 band tables, kit bundles, the mob/elite drop table, `loot_rules` (ladder + odds for Java) and an override for every vanilla chest (themed pools without gear + one band roll while `loot` is on; the untouched vanilla pools while it is off; dispensers, the bonus chest and trial vaults stay vanilla; the new abandoned camp chests are included). Java: loot conditions `loot_level`, `loot_enabled`, `mob_rank` and the `loot_gear` function (rarity rolled from the odds, base from the ladder, enchants planned to land in that rarity; steps down only if nothing can reach it); mob drops roll with each levelled monster's own table; per-player chests, barrels and chest minecarts (loot table moved into an attachment on load, own copy per player, lid and sound driven per copy, sneak to break); small sparkles on unopened loot chests and on Rare+ items on the ground. Admin: `/vestiges loot roll <level> [rarity]`, `/vestiges loot chest <level>`, `/vestiges loot test <level>` (1,000 rolls, prints averages and the rarity split). 48 JUnit tests (every generated table decodes with 26.3's loot codec).
  - **Playtest 1 of M3b (2026-10-11):** odds, rolled items, chests, per-player copies and elites OK. Sparkles made bigger (both) and the item sparkle now sits just above the item. Mob emeralds felt rare on surface and cave kills (tables checked: 10% per kill, 15% ingots, working as designed). **New rates (user): emeralds 1 kill in 6, ingots 1 kill in 5** (≈ 40 emeralds an hour of fighting with elites). **Playtest 2:** loot good, chest sparkle OK; item sparkles not seen → made bigger, twice as frequent, right on top of the item, and dark rarity colours (Rare blue, Relic brown) lightened so they read in low light.
  - **Balance check (generator report, band roll per chest):** gear 0.30 (loot level 1–4) → 0.59 (50) · books 0.10 → 0.20 · emeralds 0.1 → 2.7 · diamonds from loot level 30: 0.10 → 0.42 · totem 0.4–2% · Rare-or-better 0.00 (surface start) → 0.09 (caves) → 0.24 (deepslate). Mob kills ≈ 0.15–0.20 emeralds each plus elites (≈ 0.3 per kill overall, ≈ 30–40 an hour of fighting).
- 🟡 **M3 Mobs & loot — decisions (approved 2026-10-11):**
  - **Split:** M3a Mobs, then M3b Loot, each with its own playtest.
  - **Which mobs scale:** all monsters, including neutral ones (endermen, piglins, spiders); not animals, villagers, golems or pets. Vanilla bosses (Warden, Wither, Elder Guardian) stay vanilla outside dungeons.
  - **Level set once at spawn** from players within 64 blocks (nobody that close: the nearest player), kept after.
  - **Level colour on the look-at card:** grey 6+ below you · green 3–5 below · white within 2 · yellow 3–5 above · red 6+ above.
  - **Sizes:** bell curve, most 90–110%, rarely 80% or 125%; health and damage follow size; babies inherit.
  - **Powers:** Swift +30% speed · Brute +40% damage · Armored +10 armor and knockback resistance · Burning sets you on fire 3 s, immune to fire · Shove knocks you far back · Shadow invisible 3 s every 15 s · Caller 2 helpers once at half health · Volatile explodes on death (no block damage) · Venom Poison I 4 s · Chilling Slowness II 3 s · Mending 2% health per second · Spiked reflects 20% of melee damage · Withering Wither I 4 s (deepslate and Nether only).
  - **Elite names:** powers + mob + rank tag in the rank colour ("Swift Zombie", "Swift Venomous Zombie · Elite", "… · Warlord").
  - **Health bars and damage numbers:** a small bar above a mob for 5 s after you hit it (elites while in view); damage numbers float up from each hit, gold for crits; both on by default, with settings.
  - **M3a Mobs built (2026-10-11), waiting for playtest:** every monster gets level, rank, powers and size once when it first appears (saved with it); health +15% and damage +6% per level × size × rank (Brute ×1.4); the 13 powers; kill XP uses the mob's own level × rank; new armor formula (armor ÷ (armor + 20), max 85%, toughness half, Breach still works) and armor, toughness and health no longer capped; look-at card: name with power adjectives, rank tag and level colour, bigger card for elites (rank banner, rank border, health bar, one line per power) and a size line; health bar over the head 5 s after you hit (elites while in sight, within 32 blocks); floating damage numbers (gold for crits); elites glow in their rank colour for 10 s when first spotted; layer subtitle ("Caves: mobs up to level 40") after 3 s in a new layer; Settings: Mob health bars, Damage numbers, and look-at lines Elite powers and Mob size. Volatile's blast is power 2 and not multiplied; Spiked's reflected damage is not multiplied; babies take the size of the nearest grown mob of their kind. Admin: `/vestiges mob info`, `/vestiges mob rank <normal|champion|elite|warlord> [level]` (nearest monster within 16 blocks). 42 JUnit tests.
  - **Playtest 1 of M3a (2026-10-11):** all 11 checks OK; Spiked, baby sizes and the power words approved. Changes asked and done: no size line and no "Minecraft" line on the look-at card (other mods still show their name); the rank shows once on the card (banner only, the name keeps the power words); health bars show only the bar (no name or level), drawn at half size, at most 4 at a time (the mob under your crosshair, then the ones you hit last, then the nearest elites in sight); Volatile's blast grows with its level (power 2 at level 1, 4 at 50, at most 5; a creeper is 3).

- *(History)* Batch 2 v1 draft: https://claude.ai/artifact/FG1U8FMdynA9ivkYFa6px1
  - 401 nodes, 10 regions with the same pattern: a path out, 4 clusters each ending in a notable, 1 mastery (pick 1 of 3), and 2 rival keystones.
  - Bridges link neighbouring regions, and a crossroads ring circles the center.
  - Points: 1 per level plus 1 every 5 levels (120 at L100), plus 3 Codex bonus points.
  - **User feedback on v1:**
    - Add an **Ascendancy** system: once you pass certain thresholds you choose an ascendancy for extra specialization, on top of the tree.
    - Many more nodes should be **choice nodes**: take the node, then pick one of two options (e.g. gravel drops more flint, or less).
    - Wants **5 tree shape proposals** that allow more specialization.
    - Wants **every node listed, with problems flagged**.
    - **Extra inventory rows are rejected** as too unvanilla, so the inventory stays vanilla size. This replaces the earlier "rows from the tree".
    - **Utility powers:** placement options to choose from (tree, ascendancy, both).
    - **Masteries:** decide after balancing ascendancies.
  - **Decisions after v2:**
    - **Ascendancy is simplified:** 4 big nodes plus 4 small travel nodes, and only **4 points**.
      - There is a **basic** and an **advanced** ascendancy; names to come later.
      - **Fewer ascendancies** than 8; proposals still to come.
      - **Utility powers are linked to ascendancies.**
    - **All luck nodes are removed** ("too powerful").
    - **Choice nodes:** the idea is kept, but settled after the shape and balance.
    - **Paused:** everything except the **tree shape and starting point** (one start or several), which comes first. The user is worried about balance: some nodes are far better than others.
  - **Shape decided (v3):**
    - **Two wheels with separate point pools.**
      - **Fight wheel:** Melee, Ranged, Defense, Vitality, Mobility. 1 point per level.
      - **Craft wheel:** Mining, Gathering, Crafting, Building, Exploring. **1 point every 2 levels** plus the 3 Codex points.
    - **3 possible starts per wheel** (you pick one).
    - **Separate tab for each wheel.**
    - Reason: mobs scale with level, so a shared pool would punish builders. Each wheel has rings (small → notable → keystone), with the same power budget within a ring.
  - **Shape draft (wheels v1):** https://claude.ai/artifact/LeywBtZ4E9Jkuf9bYWaBYW
    - **Fight wheel:** 295 nodes, about 34% fillable.
    - **Craft wheel:** 215 nodes, about 25% fillable.
    - 3 starts sit on an inner ring. Each region has 3 spokes, then clusters with 2 notables each, then a mastery slot, then a long path to a rival keystone pair. Bridges link neighbouring regions.
    - Cost from the nearest start: notable about 10, keystone about 15, farthest keystone about 21. The three starts are nearly equal.
    - **v2 adds draft content** to every node:
      - Each Fight region: 4 clusters × (first notable + big notable). Each Craft region: 2 clusters plus a center line.
      - Luck, inventory rows, Volley and Undying are removed.
      - Utility powers are out of the tree. New keystones replace them: Last Stand, Skirmisher, Featherfall (the softened Windwalker), Ore Scholar, Bountiful, Cartographer.
      - Nodes that need the user's OK are flagged on the page.
    - ✅ **Wheels v2 approved ("this is good")**, kept exactly as drawn:
      - **Start positions:**
        - Fight: Blade & Shield, Heart & Stride, Bow.
        - Craft: Pick & Anvil, Hammer & Hoe, Map.
      - **Region order:**
        - Fight: Melee → Defense → Vitality → Mobility → Ranged.
        - Craft: Mining → Crafting → Building → Gathering → Exploring.
      - **Depth:** about 10 points to a nearby notable, 15 to a nearby keystone, 21 to the farthest.
      - **Sizes:** Fight 295 nodes, Craft 215 nodes.
      - **Starts you didn't pick** work as walk-through nodes.
      - Balancing by ring comes in Batch 3.
    - **Next in Batch 2:** ascendancy proposals (simplified), then choice nodes and masteries, then the flagged checks.
  - **Ascendancy rules (agreed):**
    - ~~Branching basic/advanced~~ **(replaced):** there is **no advanced step**. Pick 1 of **4 ascendancies**, then **ascend twice, getting 2 points each time** (4 points in total).
    - **Each step has 2 branches** (user's revision). Each branch runs: **travel point (stat) → big crafting node → travel point (stat) → bigger fighting node at the end**.
      - **4 points in total** (2 per ascension): either one full branch, or the first half of both branches (two crafting nodes).
      - Utility powers are crafting nodes.
      - **Unlocks (draft):**
        - **1st ascension, same for everyone:** level 20+, kill 100 hostile mobs (incl. 1 elite), mine 64 ores, reach deepslate, 25 Codex entries → "ready to ascend", choose your ascendancy, +2 points.
        - **2nd ascension:** level 60 plus 3 steps tied to your ascendancy, +2 points.
      - **You ascend in an Ascendancy tab** of the passive tree screen (next to the Fight and Craft wheel tabs).
      - **Each ascendancy = one weapon + one tool.** Travel points boost the weapon, crafting nodes use the tool, fighting nodes use the weapon.

        | Ascendancy | Weapon + tool |
        |---|---|
        | Harvester | Sword + Hoe |
        | Woodsman | Crossbow + Axe |
        | Delver | Mace + Pickaxe |
        | Wayfarer | Bow + Shovel |
        | Lancer | Spear + Shears |

      - ✅ **Ascendancy approved:**
        - The 5 weapon + tool ascendancies, with their branch nodes:
          - **Harvester:** Reaper / Tiller.
          - **Woodsman:** Lumberjack / Timber Sense.
          - **Delver:** Vein Sense / Excavator (pickaxe 3×3).
          - **Wayfarer:** Earthmover / Trailmaker.
          - **Lancer:** Cluster Harvest / Shearmaster.
        - The 1st ascension checklist.
        - The 2nd ascension: level 60 plus 3 steps tied to your ascendancy.
        - **You can change ascendancy with the rare full-respec item.**
  - **Choice nodes and masteries (agreed):**
    - **Merged:** a mastery is a bigger choice node. Each region's mastery slot means picking 1 of 2 strong effects.
    - **Choice nodes, option A (approved):** every cluster's first notable, every mastery, **1 small node per cluster** and **1 small node per inner path**. That is about 60 in the Fight wheel and 45 in the Craft wheel, roughly 1 node in 5.
    - Big notables and keystones stay fixed.
    - **Switching a pick costs emeralds** (from the Bank).
    - **Unlock: level plus a feat** for each step (draft: level 30 plus a feat, then level 70 plus a harder feat).
    - **Utility powers live in the ascendancies.**
  - **Ascendancy draft v1** (on the wheels page, section "Ascendancy draft"):

    - *(Superseded by the weapon + tool ascendancies below. Steward and Champion are replaced.)*

    - Wide Harvest is dropped (Reaper covers it; basic right-click harvest stays for everyone).
    - Flagged: Ore Sense, Return Trip, Stubborn, Gem Eye, Hush, Hiring Hand.
- **Format is a mix:** screens and diagrams as static sketch pictures; the tree and data tables as interactive pages (Artifacts). You can comment on all of them.
- I revise each one until you approve it.

1. Save memories: your preferences (simple wording, agree every detail first, short plans, drafts before building) and the project vision and rules.
2. **Draft batches**, reviewed one at a time:
   - **Batch 1, overview & screens:**
     - A map of all systems and how they connect.
     - Sketches of every screen in Hearthbook style: hub tabs (tree, Codex, Villagers, Settlements, Feats, Settings), the inventory with new slots, the hotbar with Tool/Weapon slots, the offhand rack, and the HUD (look-at, level/XP, hearts %, damage numbers, tracker).
   - **Batch 2, the passive tree:** the full ~400-node interactive draft.
   - **Batch 3, numbers:**
     - Enchant table I–VI for every enchant.
     - Rarity score.
     - Mob and balancing charts.
     - Elite and boss ranks with their powers.
     - Armor curve.
     - Meal buffs.
     - Trim stats.
   - **Batch 4, content lists:**
     - Feats with requirements and rewards.
     - Markers and their radii.
     - Worker jobs (gates, station needs, inputs and outputs, traits, ranks).
     - Codex thresholds and rare-block paths.
     - Dungeon themes, room sketches, modifiers and bosses.
     - Events.
     - The 24 dragons.
     - Blueprints flow.
     - Villager library, Post/Modes and Contract flows.
   - **Batch 5, lore drafts:** villager background templates, Heroes and Legends, dragon chapters, shrine and dungeon fragments. Everything is for your approval.

**Phase B — Building starts only after every draft is approved.** Then:
- **M0:** create the `RPG Mod/` project (`git init`, a private GitHub repo `vestiges`, a `CLAUDE.md` with this log), then the foundation work.
- Then M1 → M10, each ending with a playtest and your OK.

## Verification (every milestone)
- `gradlew build` and `gradlew test` pass.
- `runClient` and `runServer` with a client connected work and show no desync.
- I give you a test checklist for the milestone's features. **The next milestone starts only after your OK.**

## Parked ⏸️
- Storehouse and crafting simplification.

## Balancing ✅
- **Fights are tanky (ARPG):** a same-level mob takes about 6 hits.
- **Mob stats:** +15% HP and +6% damage per mob level.
  - Your HP and the % of it lost per hit at levels 1 / 20 / 50 / 100: 20 HP (~15%) / ~60 (~7%) / ~120 (~5%) / ~250 (~4%).
- **Elite ranks:** Champion ×2 · Elite ×3 · Warlord ×5. **Boss ranks:** Lieutenant ×6 · Boss ×10 · Overlord ×20.
- **Dungeon tiers:** each step up adds +3 mob levels. Tier 1 ≈ level 15, with no upper limit.
- **Armor** always helps, but each point helps a little less, and a hit can never be reduced by more than about 85%.
- Exact numbers (XP curve, thresholds) get tuned in each milestone's balancing pass.

## Build order ✅
- **pre:** interactive tree review page.
- **M0 Foundation:** Gradle setup from Hearthbook, data registries, networking, config, the hub shell, the look-at HUD framework, admin commands.
- **M1 Character:** levels, tree, uncapped attributes, % hearts, death and gravestone, Paragon, rarity, gear gating, enchant rescale, trims.
- **M2 Inventory** · **M3 Mobs & loot** · **M4 Codex & farming** · **M5 Feats & settlements** · **M6 Villagers** · **M7 Workers** · **M8 Dungeons** · **M9 World & End** · **M10 Blueprints**.
- Alongside every milestone: lore drafts for your approval and a balancing pass.
- **Each milestone ends with a playable build and a test checklist.** I wait for your OK before starting the next one.

## Reference
- **Existing projects:**
  - RPG Craft: port it.
  - Hearthbook: absorb it.
  - Villager Inventory, Size Scaling, Extended Trades, Ride and Stay, Invincible NPC: replace with real systems.
  - Icebox: reuse its ideas. Modpack: inspiration.
- **Research:** PoE, Diablo 2–4, Titan Quest, Grim Dawn, Last Epoch, Vault Hunters, Mine and Slash, Apotheosis, MineColonies, Valheim, Terraria.
- **To save to memory once plan mode ends:** agree every detail before building, use simple words, keep plans short and readable, follow the vanilla-style and anti-exploit rules, keep the base game simple, and make hard content opt-in.
