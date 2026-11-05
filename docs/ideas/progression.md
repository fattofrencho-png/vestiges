# Progression and comfort ideas (design only, no code)

*Idea chat started 2026-11-03. Source: `docs/design-log.md` (never edited here). Every line below is a draft until the user says keep / skip / later.*

## 1. What the design log already covers (overlap check)

| Idea | Already in the log | My reading |
|---|---|---|
| Vein miner, tree harvester, cluster harvest | Character: utility powers are tree keystones/notables, each switchable, V key toggles all | **Covered. Skip as a separate feature.** |
| Tool trims | Gear: trims give stats (material sets stat, pattern rarity sets strength); built in M1 with set bonuses; Quests: trim patterns never quest-only | **Covered. Skip.** |
| Gear above your level | Gear ladder built: gear above your level works at half armor / damage / mining speed, red tooltip line | **Armor gating covered.** Only "burden" is new. |
| Legendary tooltips | Built: rarity colour, tier line, Relic stars | **Covered.** |
| Durability + shulker box tooltips, inventory/durability HUD, loot pickup notifier | A2 "Small features": shulker and bundle preview tooltip, pickup notice bottom right, durability warning, durability HUD on the right | **Covered (A2).** Not mine to design again. |
| Food values (AppleSkin) | A3 names it: saturation on the hunger bar, restore preview, tooltips that show meal buffs | **Same item as A3.** |
| Character sheet | A3: book-style sheet replaces the Character tab, trees button kept; draft look reviewed 2026-10-30 | **In my scope (content, not look).** |
| Enchanter and Jeweler screen | Enchanter worker (Tier 4: copies a sample book up to IV), Gem Cutter, Runesmith; table rules (level V from arcane blocks); anvil costs emeralds | **Mostly covered by workers and the rescale.** Open: a *player* screen? |
| Better enchanting, brewing, banner screens, archaeology | Batch E, no details | **Needs your vision.** |
| 13-tier progression core | Level milestones, gear ladder (L1/5/10/15/20/30/40), dungeon tiers + keys, 24 dragons at ~4 x N levels | **Probably already exists in pieces.** Open: a visible "ages" ladder? |
| Optional 5-act campaign through advancements | Feats (advancement-style tree), optional Quests (guide / story / side), lore "no forced questline" | **Overlaps Quests + Feats.** Open: skip, or one guide-quest chain? |
| Nether Seal + overworld ecosystem loot | World: Nether pockets in deepslate, Nether matters more (Nether-only blocks, keys from fortresses) | **Partly overlaps.** Pack lines are all that is kept. |
| Equipment burden | nothing | **New, needs your memory of it.** |
| Starter kit | nothing (Blueprints has a starter *catalog*, different) | **New.** Vanilla-style rule suggests a switch, off by default. |
| Advancement plaques | Feats give "a vanilla toast"; discovery toast already built | **Mostly covered.** |
| Better Combat attacks | Combat: vanilla base with light tweaks only | **Conflicts with "vanilla-style".** |
| Better animations, ambient + physical sound, falling leaves, dynamic lights | nothing; welcome page already has a "recommended mods" list | **Client polish; existing mods do it well.** |

## 2. Decisions

### Round 1 (user, 2026-11-03)
- **Client polish (dynamic lights, falling leaves, ambient/physical sound, Better Combat attacks):** none built for now. **Keep Vestiges compatible with the popular mods that do them** (no clashing screens, mixins or sounds), and list them on the welcome page's recommended-mods list. Better animations falls in the same group.
- **Equipment burden:** "forget half power, just makes you slower." My reading, to confirm: gear above your level no longer halves armor, damage and mining speed (that built rule goes away); it slows your movement instead. Open: slower only for armor, or for all gear? Does weight count?
- **Nether Seal:** rethink later. The user wants a **1-to-1 Nether (vanilla travel, nothing sealed)**. Moves to Later; nothing built.
- **Burden confirmed (round 2):** gear above your level works at **full stats but slows you**, by how far above your level it is. This replaces the built half-power rule, so `docs/design-log.md` (Gear, M1 "gear above your level") needs a change when the main chat takes it. The red tooltip line stays, now saying "slows you". Slow amount and cap still to set at balancing.
- **Starter kit (round 2):** no items. A welcome message and, if Quests is on, a first guide quest. Skip the kit.
- **Plaques (round 2):** keep the vanilla toast. Nothing new beyond the existing discovery toast.
### Batch E (round 3, user, 2026-11-03; all after the MVP, looks go to the UI room)
- **Enchanting screen:** show the full cost per offer (level, lapis, emeralds); bookshelf and arcane block counter with the level V chance; preview of every enchant an offer can give. **No pick-from-a-list** (not chosen).
- **Brewing screen:** result preview; recipe memory tied to the Codex; fuel and progress display; **recipe reminders** (user's own addition: a short reminder of recipes you already found, shown in the screen).
- **Banner screen:** a better loom screen on the same loom block (pattern list, live preview, layer order). No own editor.
- **Archaeology:** Codex entries for finds (sherds, trim templates); faster brushing (level or tree node); dig site hints (hint or sound near a suspicious block). All must stay earned and non-exploitable.
### A3 and Enchanter (round 4, user, 2026-11-03)
- **Character sheet pages:** Who you are (name, level, tier name, title, standing) · Stats (with where each number comes from) · Records (kills, distance, blocks, deaths, time, dragons) · Achievements (feats and Codex progress). Tier names come from the 13-tier display below.
- **Food info:** saturation on the hunger bar and a restore preview. **Not chosen: meal buff in food tooltips and the exhaustion bar.** This drops the "tooltips that also show meal buffs" line from A3 in the design log; to confirm with the user whether meal buffs show anywhere else (e.g. the look-at HUD or the sheet's Stats page).
- **Enchanter and Jeweler screen:** later.
- **13-tier core + 5-act campaign:** tiers are shown on the character sheet (names for the stages levels, gear and dungeons already give; no new rules). The campaign is **one guide-quest chain** in the Quests system, not a separate system.

## 3. Keep / skip / later list

**APPROVED by the user, 2026-11-03.** Everything is after the MVP unless noted. Still open: the 13 tier names, the act list, and whether meal buffs show anywhere now that the food tooltip line is dropped.

**KEEP (design agreed)**
1. **Character sheet (A3):** book with 4 pages: Who you are, Stats, Records, Achievements. Trees button kept.
2. **Food info (A3):** saturation overlay on the hunger bar and a restore preview. (Meal-buff tooltips and exhaustion bar dropped.)
3. **Gear above your level slows you** at full stats; replaces the half-power rule. Changes built M1 code and the design log's Gear line.
4. **13 tiers shown on the sheet:** names only, drawn from levels, gear ladder and dungeon tiers. The 13 names and level ranges still need a draft.
5. **5-act campaign** as one chain of guide quests in the Quests system (needs Quests on). Act list still to draft.
6. **Welcome message and a first guide quest** instead of a starter kit.
7. **Enchanting screen:** full cost, bookshelf and arcane block counter, preview of possible enchants.
8. **Brewing screen:** result preview, recipe memory (Codex), fuel and progress display, recipe reminders.
9. **Better loom screen** (banners).
10. **Archaeology:** Codex entries for finds, faster brushing, dig site hints.
11. **Compatibility with popular polish mods** (dynamic lights, falling leaves, sounds, animations, Better Combat): no clashes, listed on the welcome page's recommended-mods list.

**SKIP (covered or not wanted)**
- Vein miner, tree harvester, cluster harvest (tree keystones already).
- Tool trims (trim stats and set bonuses built).
- Starter kit items.
- Advancement plaques (vanilla toast stays).
- Durability/shulker tooltips, inventory/durability HUD, loot pickup notifier, legendary tooltips (A2 / built).
- Better Combat attacks, dynamic lights, falling leaves, ambient and physical sound, better animations as *own features* (other mods do them).
- Pick-an-enchant list in the enchanting screen; exhaustion bar; own banner editor.

**LATER**
- Nether Seal (user wants a 1-to-1 Nether; rethink later).
- Enchanter and Jeweler player screen.

**Notes for the main chat**
- A3 line "food tooltips that also show meal buffs" is dropped (see round 4).
- The burden change touches a built system; schedule it with balancing.
