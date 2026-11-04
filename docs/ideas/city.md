# City layer (idea chat, started 2026-11-03)

Status: **APPROVED as a design draft by the user (2026-11-04), design only.** Balance pass v2 is the number base (all numbers still drafts for the balancing milestone). Still to do in the main chat: move it into `docs/design-log.md`, including the Architect line of 4 grades replacing "Architect = Tier 4, L70+". Comes after the MVP, together with M7 Workers and M10 Blueprints.
Source of truth for the existing systems stays `docs/design-log.md` (Workers, Settlements, Blueprints). This file only adds the city layer on top.

## Goal
A planning layer for building a settlement: place a building as a ghost preview that shows what it costs,
and grow through unlock chains. No new blocks; only markers and display entities.

## The camera is NOT part of the city layer (user, 2026-11-03, after round 4)
The isometric view is **just a camera mode** and has nothing to do with the city itself. It is a general feature, designed in its own topic (the map/views chat; design log Batch B lists "3D and isometric views" and "freecam"). The city layer only *uses* it when it is available and works without it (ghost placement works from the normal camera too).
What was decided about the camera below stays as **input for that topic**, not as a city rule: in-world, pan/zoom/rotate in 90° steps, only you see it, any damage exits it, cut-away roof.

## Decided (user, 2026-11-03, round 1)
- **View (input for the camera topic, not a city rule):** an **in-world isometric camera** (freecam-style), showing the real world and ghosts in 3D. Not a flat picture.
- **Ghost preview shows and charges:**
  - **Emeralds** up front (fee when you confirm).
  - **Materials** taken from nearby chests (like the Architect today); missing blocks are listed.
  - **Workers needed** once the building is finished (roles and number).
  - **Renown / level gate** (shown in red if not met).
- **What a "building" is:** a **blueprint with a type tag** (farm, granary, house...). A tagged build finished inside your own area counts.
- **Unlock chains** (e.g. "build 10 different farms to unlock a granary"):
  - shown as a **tree in the City view** (locked entries greyed out with a progress count),
  - **also listed in the Feats tab**,
  - they **unlock blueprints**, and can also **unlock worker roles or markers** (bigger radius).
  - Never a new block or item.

## Decided (user, 2026-11-03, round 2)
- **Who builds:** the **Architect only**: confirming a city ghost hands it to the Architect. ⚠️ Conflicts with the design log (Architect is Tier 4, L70+, one per settlement, and Blueprints say you fill ghosts in yourself). Needs a ruling, see open question A.
- **Counting for "build 10 different farms"** (all four chosen): a building counts once per **different blueprint** *and* at a **different place**; it must **stay standing**; and it must be **in use** (its workers are present).
- **Litematica / files:** **import .litematic**, **export .litematic**, **vanilla .nbt too**; imported files obey the **size limit that grows with feats**. Blocks only; entities and items ignored.
- **Material-list HUD** counts **chests nearby** (settlement radius) and is **live while building**. Inventory and per-layer counting were not chosen.

## Decided (user, 2026-11-03, round 3)
- **Architect becomes a line of grades, open at every level, each faster** (replaces "Architect = Tier 4, L70+" for city building; the design log must be updated by the main chat if approved). Draft table, numbers **to confirm**:

| Grade | Level | Speed (blocks per day, Novice to Master) | Biggest ghost | Notes |
|---|---|---|---|---|
| 1 Builder | L10 | about 100 to 250 | small (about 15 blocks wide) | cheap, cottages and farms |
| 2 Master Builder | L30 | about 300 to 600 | medium (about 31) | |
| 3 Architect | L50 | about 750 to 1,200 | large (about 63) | the current Architect speed |
| 4 Chief Architect | L70 | about 1,500 to 2,500 | any size allowed by feats | unique per settlement, can queue several ghosts |

  Rank-ups (Novice to Master) keep the existing rule: work XP plus a milestone item. The Architect's blocks still count as yours for renown.
- **"In use":** **every counted building needs a villager** (living or working there). ⚠️ Walls, roads and decoration have no villager, so they can't count for chains. Proposal: chains only use types that can hold a villager (houses, farms, workshops, granary...), and walls and decoration count only for renown Size. To confirm.
- **Camera:** pan, zoom, rotate in 90 degree steps; **only you see it** (it is a client view, no entity left for others). No area limit or combat lock was chosen; ghosts still only exist inside your own areas. Risk to settle: viewing from far above while a mob attacks your standing body (see open C).
- **Build layers:** **the Architect's progress shows as the current layer**, and the **cut-away roof** hides everything above a chosen height in the isometric view. Layer lock and single-layer ghost view were not chosen.

## Decided (user, 2026-11-03, round 4)
- **Grade table approved as drafted** (4 grades, numbers stay drafts for balancing).
- **Builders per settlement:** **as many as slots allow**; several of the same grade can build different ghosts at once. Each uses a worker slot (so a Capital can run many).
- **Walls, roads, decoration:** count for **renown only** (Size, Variety); chains use only building types that can hold a villager.
- **Safety in the view (decided):** **any damage exits the view at once.** No lock when hostiles are near.
- **Unlock chains need their own full discussion** (user): what they could be and how they tie into the rest (renown, feats, workers, trade, quests). The table below is only a placeholder to show the format, **not agreed**.

## Placeholder (NOT agreed, superseded by the sample catalogue and balance pass below): first unlock chains
Each chain is JSON data (like feats), unlocks a **blueprint** (and sometimes a worker role or marker radius), and appears in the City tree and the Feats tab.
Counting rule for all: different blueprint **and** different place, still standing, a villager present.

| Unlocks | Needs | Also opens |
|---|---|---|
| Cottage (starter) | none | nothing |
| Farm plot | 3 different cottages | nothing |
| Granary | 10 different farms | Granary worker role |
| Mill | Granary + 5 farms | nothing |
| Market stall row | 5 different houses + 3 workshops | Trade wealth tier bonus (to confirm) |
| Barracks | Granary + Smithy | Guard role (to confirm) |
| Town Hall | 25 different buildings of 5 types | bigger Banner radius (r48 to r64) |

## Global overview for the chains topic (user, 2026-11-03: look at the whole picture first)
Before any chain is agreed we need the building set, the Town Hall, balance, cost and playing style. Draft map of the space (nothing agreed):

**Building families** (each a blueprint type tag that can hold a villager):
- Homes: cottage, house, manor (beds, People and Homes renown)
- Food: farm, orchard, pen, fishery, kitchen, granary
- Materials: lumber camp, quarry or mine, masonry, smithy, glassworks
- Services: market, inn, library or chapel, infirmary, bounty board hall
- Civic: Town Hall, watch tower or barracks, gate
- Landmarks: one-off showpieces (statue, fountain, big tower), renown only

**Town Hall, options:** (a) a civic building you grow around, the settlement's "core" tied to renown level, (b) a late unlock at Town or City renown that holds the ledger, bounties and caravans, (c) no special building, just a type tag like the others. It must work with no new blocks: a blueprint plus the existing Bell and a vanilla block (Lectern or similar) as its marker.

**Things to weigh:**
- Balance: emerald fees must not outpace what settlements and workers can earn (emeralds are the only currency, so no passive faucet that breaks trading); chain targets must scale with the renown thresholds (Settlement 80 to Capital 5,000); the grade speeds in the Architect table.
- Cost: fee plus materials plus workers; keep early buildings cheap so a solo player at level 10 can start.
- Playing style: builder, adventurer, trader, solo, multiplayer. The city layer must be optional help, never a wall in front of the base game ("the character is the core").
- Fit: renown scoring, worker gates, trade wealth tiers, bounties, raids, Feats, quests. Chains should reuse these, not add a new currency or a parallel progression.
- Anti-exploit: counting rules above (different blueprint, different place, standing, villager present).

## Decided (user, 2026-11-03, round 5: the big picture)
- **Role:** **optional help.** The base game and the character never need the city layer.
- **Chain shape:** explore **tiers inside a family** (cottage, house, manor), **branches across families** (granary needs farms, mill needs granary) and **districts** (group goals). Not "free choice".
- **Town Hall:** a **core civic building**, unlocked at Town or City renown. It holds the ledger, bounties and caravans, and lifts the settlement's slot cap. Marker: blueprint plus existing Bell and a vanilla block, no new block.
- **Buildings can give:** renown and slots, worker roles, trade bonuses, and a **small emerald income**.
- ⚠️ **Tension to settle:** "optional" vs "worker roles unlocked by chains" (a chain would then gate workers), and vs "small emerald income" (emeralds are the only currency). See open F and G.

## Decided (user, 2026-11-03, round 6)
- **F. Chains are a second route to worker roles.** Roles keep their current gates; a chain unlocks the matching building blueprint and can speed up or add slots, never the only way in.
- **G. Emerald income:** capped per day per building, collected from the **Town Hall ledger** (nothing paid automatically), and only when the building is **in use**.
- **H. Starter catalogue: small, about 12 types.**

## Draft: sample catalogue (12 types; ALL numbers are drafts to balance later)
Every fee is paid in emeralds up front, on top of materials from chests. "Chain" is what must already stand (different blueprints, different places, villager present). Renown levels use the existing thresholds (Settlement 80, Hamlet 250, Village 600, Town 1,200, City 2,500, Capital 5,000).

| # | Building | Family / District | Tier | Min renown | Chain (needs) | Fee | Workers | Gives | Income cap/day |
|---|---|---|---|---|---|---|---|---|---|
| 1 | Cottage | Homes | 1 | Camp | none | 8 | 0 | 2 beds | 0 |
| 2 | Farm | Food / Farm | 1 | Camp | none | 12 | 1 Farmer | Farmer slot | 1 |
| 3 | Lumber camp | Materials / Craft | 1 | Camp | none | 12 | 1 Forester | Forester slot | 1 |
| 4 | House | Homes | 2 | Settlement | 3 cottages | 30 | 0 | 4 beds | 0 |
| 5 | Granary | Food / Farm | 2 | Settlement | 10 farms | 60 | 1 Farmer | Granary role, more Farmer slots | 2 |
| 6 | Mill | Food / Farm | 2 | Hamlet | Granary + 3 farms | 80 | 1 Cook | Cook slot, bread trades | 2 |
| 7 | Smithy | Materials / Craft | 2 | Hamlet | Lumber camp + 2 houses | 100 | 1 Smith | Smith slot | 3 |
| 8 | Watch tower | Civic | 2 | Hamlet | 5 buildings of 3 types | 80 | 0 | +radius, guard post | 0 |
| 9 | Market | Services | 3 | Village | 5 houses + 2 workshops (Lumber, Smithy or Mill) | 150 | 1 Trader | +1 trade wealth tier | 4 |
| 10 | Library | Services | 3 | Village | 3 houses + Market | 200 | 1 Scholar | Codex hints, Enchanter slot | 3 |
| 11 | Manor | Homes | 3 | Village | 3 houses + Market | 250 | 0 | 6 beds, Hero housing | 0 |
| 12 | Town Hall | Civic (core) | 4 | Town | one of each family + 15 buildings in total | 600 | 1 Steward | Ledger, bounties, caravans, slot cap up | collects all |

**Districts (group goals):** Farm district = Farm + Granary + Mill, Craft district = Lumber camp + Smithy, Civic district = Market + Library + Watch tower. Completing a district could give a small renown bonus (to confirm).
**Landmarks** (statues, fountains, walls, roads) are outside the catalogue and count for renown Size and Variety only.

**Catalogue accepted as the base (user, 2026-11-03: "Looks right").** Numbers still drafts.

## Balance pass v2 (PROPOSAL, 2026-11-03, waits for the user's OK; replaces the numbers in the sample catalogue)
Checked against the design log: wage 1 emerald per worker per day; daily purse per villager W1 5, W2 10, W3 20 (so a Village is about 180 per day at the cap, a Town about 600; real trade income is a fraction); villager slots Camp 3, Settlement 6, Hamlet 10, Village 18, Town 30, City 50, Capital 80; every recruited villager uses a slot and a bed.

**Rules that fall out of it**
1. **Fee = about 2 to 5 days of realistic trade income at the minimum renown** (Hamlet about 25 per day, Village about 50, Town about 150). A building is never a wall, and a settlement can afford the next one soon after levelling up.
2. **Chain counts are capped by slots.** "10 farms" would eat the whole Hamlet (10 slots), so counts are kept to **about 40% of the slots at that renown**. One villager can be both the resident of a cottage and the worker of a farm; the chain only needs "a recruited villager lives or works there".
3. **Income pays back in 12 to 70 days** (fee divided by income cap). It is about the size of a worker's wage times 1 to 4, so even 20 buildings in a Capital add about 60 per day against purses in the thousands: it can't break the economy.
4. **Tier set by the Architect grade** (biggest ghost): Tier 1 by a Builder (up to 15 wide), Tier 2 by a Master Builder (up to 31), Tier 3 and the Town Hall by an Architect (up to 63). The Chief Architect only adds more than 63 wide and several ghosts queued.
5. **Build time** (Minecraft days at the grade's speed): Tier 1 about 1 to 2 days, Tier 2 about 2 to 3, Tier 3 about 3 to 5, Town Hall about 6 to 10. That's 20 minutes per day, so the wait is minutes to a few hours; the materials are the real cost.
6. **District bonus:** finishing a district gives a **permanent Deed of +25 renown, once per district per settlement** (existing Deeds: bounty 10, raid 30, Hero 50). Granted only when every building in it stands at that moment.
7. **"Different place":** footprints can't overlap and centres must be at least 12 blocks apart.
8. **Corrections found:** Enchanter is Tier 4 (City) and Merchant is Tier 3 (Town), so the Library and Market at Village can't hold them. At Village they use a plain recruited villager; the Merchant and Enchanter housing opens at Town and City.

| # | Building | Tier | Min renown | Chain (needs) | Fee | Blueprint size (blocks) | Income cap/day | Payback (days) |
|---|---|---|---|---|---|---|---|---|
| 1 | Cottage | 1 | Camp | none | 8 | 150 to 300 | 0 | n/a |
| 2 | Farm | 1 | Camp | none | 12 | 150 to 400 | 1 | 12 |
| 3 | Lumber camp | 1 | Camp | none | 12 | 150 to 400 | 1 | 12 |
| 4 | House | 2 | Settlement | 3 cottages | 30 | 500 to 900 | 0 | n/a |
| 5 | Granary | 2 | Hamlet | 4 farms | 60 | 700 to 1,200 | 2 | 30 |
| 6 | Smithy | 2 | Hamlet | Lumber camp + 2 houses | 100 | 600 to 1,000 | 3 | 33 |
| 7 | Watch tower | 2 | Hamlet | 5 buildings of 3 types | 80 | 600 to 1,000 | 0 | n/a |
| 8 | Mill | 2 | Village | Granary + 2 farms | 80 | 700 to 1,200 | 2 | 40 |
| 9 | Market | 3 | Village | 4 houses + 2 workshops (Lumber, Smithy or Mill) | 150 | 1,500 to 3,000 | 4 | 37 |
| 10 | Library | 3 | Village | 3 houses + Market | 200 | 2,500 to 4,500 | 3 | 67 |
| 11 | Manor | 3 | Village | 3 houses + Market | 250 | 3,000 to 5,000 | 0 | n/a |
| 12 | Town Hall | 4 | Town | one of each family + 12 buildings in total | 600 | 6,000 to 12,000 | collects all | n/a |

Changes from the first sample: Granary 10 farms to **4 farms**, Hamlet instead of Settlement (the 10-farm idea can return later as a Town-level Silo); Mill moved to Village; Market 5 houses to 4; Town Hall 15 buildings to 12; income and fees kept; Library no longer "Enchanter slot". All still drafts.

## Next topic
**Unlock chains, full discussion** (what chains exist, how they connect to renown, feats, workers, trade and quests). Approval of this file was put on hold ("wait", 2026-11-03).

## Done when
The user approves this file. Then the main chat updates `docs/design-log.md` (Architect line, City layer) and files M7/M10 additions. Not built before the MVP.

## Fits with what exists (from the design log)
- Ghost placement works only inside your own areas (Blueprints). The Architect places 5 ghost blocks per action from its copper chest, bottom-up; its blocks count as yours for renown.
- Renown already counts Size, Variety, Homes, People and Deeds, so a tagged building adds to those without a new score.
- Only the block layout is saved in blueprints, never blocks or items. Dungeon rooms stay built-in.

## Open questions (next rounds)
1. **Litematica files:** import only, or export too? Which formats (.litematic, vanilla .nbt, WorldEdit .schem)? Size limit and safety (no entities, no items).
2. **Material-list HUD:** where it shows, how it counts "have" (inventory only, or nearby chests), live while building.
3. **Build layers:** show one layer (Y level) of the ghost at a time; keys; link to the isometric camera.
4. **Who builds:** you by hand, the Architect, or both? Does the fee replace materials or add to them?
5. **Camera limits:** range around the Bell, flying free or snapped to the area, multiplayer (one camera per player, nobody else sees it).
6. **Anti-exploit:** one blueprint built 10 times counts once? "10 different farms" means different blueprints, or different places? Demolish and rebuild?
7. **Unlock data:** chains as JSON (like quests/feats) so a server can edit them.
8. **Switch-off:** its own `VestigesSystem` entry; needs Blueprints and Settlements.
