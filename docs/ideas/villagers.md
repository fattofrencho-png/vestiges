# Villagers idea chat: spec draft

*Design only. Written by the Villagers idea chat; the main chat merges this into `docs/design-log.md`. Started 2026-11-03.*
*Status: APPROVED by the user on 2026-11-03 (level bands and all other drafts accepted). Ready for the main chat to build. The takeover mechanic is parked.*

## What is already designed (from the design log)
- Library (met + recruited), Trust 0-100, requests, Recruit at 100, Post + 4 modes, Contract to carry, release.
- Our own villager screen (tabs Overview, Trades, About, Orders, Work). Vanilla trade screen and trades are removed.
- Trade system: Wealth 1-5, daily purse, stock 12/3/1, discounts, Banker worker. Quest givers (temporary quests from vanilla villagers) move to the new screen, progress kept.
- Villager backgrounds (lore, approved): name (first + byname), birthplace, family trade, turning point, what they want now, traits, rare lore hooks 1 in 50. Overview shows 2 lines, About the full story.

## Decisions (user, 2026-11-03)

### 1. MVP scope for "villagers simple"
- **In the MVP:** names + background, the **library**, the **new villager screen with trades**, and **quest givers moved to it**.
- **After the MVP (right after, before workers):** Trust, requests, Recruit, settlement slots and beds, Post and modes, Contract, the Orders and Work tabs.
- **Manager screen** (Shift + right-click: gear slots, freeze, invulnerable, invisible, mute): not in the MVP.
- **Name tag commands** (Frozen / Unfrozen / Immortal / Mortal): parked; decide when the manager screen is built.

### 2. Names and backgrounds
- A villager's **name and background are created when it spawns** (not when you first meet it). So the look-at HUD and the library can show them from the start.
- Stored on the villager and never change (history grows later, once recruiting exists).
- **Villagers that already exist in a world** (made before the mod, or loaded without a record) get theirs the first time they are loaded. (needed so old worlds work)
- The text comes from the approved lore templates (`docs/lore/`): see "Villager backgrounds" in `lore-log.md`. Name tags from vanilla still win: a villager the player renamed keeps that name as its display name.

### 3. Library (Villagers tab in H), MVP
- Every villager you **talk to (right-click)** is recorded as **met**. No tracking in the MVP, because tracking is for recruited villagers.
- Each entry shows: render, name, job, birthplace, village, where and when met. "Go there" is not in the MVP.
- Recording a first villager still counts for the Founder feat ("meet 1 villager").

### 4. The new villager screen (opens on normal right-click)
- Tabs in the MVP: **Overview · Trades · About · Quests** (Quests only appears when this villager has a quest for you). Orders and Work arrive with recruiting and workers.
- Overview: render, name, job, 2-line story, quick trades on the right.
- About: full story, traits, birthplace.
- **Quests tab (user pick):** shows the quest this villager offers (title, short text, reward, Accept). Replaces the old Shift + right-click. Existing quest progress is kept. Talk steps at the quest giver still count when you open the screen.
- Library entries open the same screen **view-only**.
- The vanilla trade screen and vanilla trades are removed.

### 5. Trades in the MVP (user, 2026-11-03)
- **Villagers only sell, for emeralds, and what they sell depends on YOUR character level.** (user's words: "villager only sell stuff for emerald, according to your level")
- **No Wealth ladder, no daily purse, no buy offers, no Banker, no Trust discounts in the MVP.** They stay in the design for after the MVP ("level now, Wealth after").
- What a villager sells = its **job list**, offers unlocked by your level in bands. Locked offers show greyed with the level they need. Offers are per player, so multiplayer works.
- Level bands, reusing the five price tiers already designed (price bands 1-3 / 4-8 / 9-20 / 21-55 / 56-150 emeralds):

  | Your level | Tier | Price band (emeralds) |
  |---|---|---|
  | 1-9 | 1 | 1-3 |
  | 10-24 | 2 | 4-8 |
  | 25-49 | 3 | 9-20 |
  | 50-74 | 4 | 21-55 |
  | 75-100 | 5 | 56-150 |

  A villager sells its job list at your tier and below. 3-6 sells per villager.
- Stock per offer: 12 normal / 3 enchanted / 1 luxury, refills every morning. Click = trade once, shift-click = as many as stock and items allow. Paid from inventory emeralds, not the Bank.
- Familiar Codex blocks appear in the fitting job's trades. Each trade gives a little character XP. Emerald trims (-2% each) still apply.
- **Risk to watch:** the earlier approved note says the player must still earn enough emeralds to run everything. With no buys and no Banker, emeralds come only from mob drops, loot and quests. Check this in the combined test and re-add buys or the Banker if emeralds run short.

### 6. Wandering Trader
- Opens the **same screen with its own rules** (user pick): rotating stock, no job list. (stock rotates every few days, prices at your tier)

### 7. Taken-over and destroyed villages
- First answer: when a village is taken over, **only recruited villagers are safe** (moved to the nearest Bell or home); wild villagers can be lost.
- **The whole mechanic is parked (user: "Park, discuss later").** What it is, what causes it, whether it can touch your settlement, how it is won back: all open. In the MVP no village is taken over; raids stay as in the design (vanilla Bad Omen, scaled up).
- Draft starting point for that later talk: destroyed = villagers killed, beds and Bell broken, marker gone from the map. Taken over = occupied by raiders or mobs after a lost raid or siege; villagers hide; the village returns to normal when retaken.

## Open points
- Check with the lore chat that the generated names and backgrounds can be produced at spawn (see `lore-log.md`, "For the main chat").

## Build notes for the main chat (not code, just pointers)
- New data per villager: id (UUID), name, background fields, met-by (per player) list. Per-player: met set and quest progress.
- Reuse: Hearthbook's `PriceEngine` and `TradeCatalogue` for prices, `QuestService.onVillager` for quest offers, `HubNet` for the library snapshot, `UiTheme` colours for the screen.
- Everything behind a switch: Villagers > Trade system (already in the switch groups). Switch off = vanilla villagers and vanilla trades.
