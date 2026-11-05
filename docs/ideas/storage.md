# Storage: chests, chest rooms and copper golems (parked idea)

**Status:** parked 2026-11-05. Not in the milestones and not counted in progress. JB has not decided whether to build it; it lives here because it touches the Bank, Workers and the Architect.

## What vanilla copper golems do (checked on the [wiki](https://minecraft.wiki/w/Copper_Golem) for Java 1.21.9, and confirmed in the 26.3 game files, see below)

- Take from copper chests only; deliver to wooden chests and trapped chests. Barrels, ender chests, shulker boxes and every other container are ignored.
- Carry up to 16 of the first item type they find. Deposit into a chest that is empty or already holds that item, in the frontmost free slot.
- Search a 65x17x65 area (32 blocks sideways, 1 up and 2 down for chests). Spend 3 seconds looking inside each chest; remember up to 9; after 10 misses they wander 7 seconds and forget.
- Cannot see chest contents without opening them and remember nothing between trips. Items are matched by type only (no potion, enchant or name).
- The wiki lists **no way to filter or configure** them (no redstone, frame or button). Unwaxed golems rust through four stages (about 420 to 460 game minutes each) and become statues.

## Gaps (what makes chest rooms painful)

1. Sorting is one way: copper chest to other chests. There is no "find me this item" and no way to say what a chest is for.
2. The first item into an empty chest decides its category, so rooms drift and mix.
3. No overflow or "unsorted" rule, no priority, no similar-items grouping.
4. Barrels, shulker boxes and modded containers are unreliable or ignored.
5. Golems are slow, stop when the chunk unloads, and need upkeep (wax or lose them).
6. Nothing shows what a chest holds without opening it.

## Existing solutions (adapt ideas, do not rebuild blindly)

- [Better Copper Golem](https://www.curseforge.com/minecraft/mc-mods/better-copper-golem): chest memory, barrels, shulkers, modded storage, optional "similar items" grouping.
- [Smarter Copper Golems](https://www.curseforge.com/minecraft/mc-mods/smarter-copper-golems): scores chests (+50 same item, +10 same category).
- [Sorting Golem](https://www.curseforge.com/minecraft/mc-mods/sortingolem): wrench-based setup.
- Player-side sorters: ChestSort, Inventory Sorter Pro, SortCraft (they sort by hand, not by golem).

## Hooks already in the Vestiges docs

- Workers goal and Smith role: `docs/design-log.md` lines 678 and 686 (Smith ages copper and makes copper golem statues).
- Lore: Dunther's Coppersmiths made the first copper golems (`docs/design-log.md` line 856, dragon 10).
- Architect and material list already read "nearby chests" (`docs/ideas/city.md` lines 18, 32, 162).
- A2 plain: Bank move and shulker boxes (`PROJECT.md`).

## JB's choices (2026-11-05)

- Pain points: setting up filters (for golems, or with redstone), and golem limits. Rust upkeep and drifting rooms were not picked, but labels fix drift for free.
- Approach: **light rules for golems first**, written in Vestiges, no dependency on other mods. No ties to Workers, Architect, Bank or lore for now. A bigger chest room system stays parked.

## Draft v3: light golem rules (JB answered twice on 2026-11-05; nothing built; every number is a draft)

Fits the Vestiges rules: no new blocks (only vanilla item frames), boosts a vanilla mob, one server switch for the whole feature that returns vanilla behaviour when off, and each part below is its own option.

**Labels (setup)**
1. **Frame label:** an item frame on a chest, trapped chest, copper chest or barrel means "this container is for these items". Both halves of a double chest share one label. (JB)
2. **Menu:** sneak + right-click the frame opens a menu with 9 slots; put in 1 to 9 items. Any listed item matches. (JB)
3. **Real items, one consumed (JB):** like a vanilla frame, putting a stack in a slot takes only 1 item and the rest stays in your hand. Taking an item out of a slot gives it back; breaking the frame drops them all.
4. **Two buttons in the menu, per frame, both optional (JB):**
   - **Click-through** (default on): a plain right-click on the frame opens the container behind it. Off = a plain right-click does what a vanilla frame does. Sneak + right-click always opens the menu.
   - **Invisible** (default off): the frame body is hidden but **all items stay visible** (JB). One item looks like vanilla; two to nine are drawn small in a 2x2 or 3x3 grid on the frame face.
5. **Strict for golems (JB):** a labelled container takes only its listed items from golems, so rooms never drift. By hand you can still put anything in. Unlabelled containers behave as vanilla.
6. **Overflow:** a container whose frame holds a chest item takes anything with no home, so the golem stops wandering.
7. **No redstone (JB):** no lock and no readout. Frames only.

**Limits (each its own option in the server config; vanilla values when off)**
8. **Chest memory:** a golem remembers which container took each item for 5, 10 or 15 minutes (JB's choices) and tries it first, skipping the 3-second look. Default 5. Confirmed by JB: the 5, 10, 15 minute choice is this memory time, not the look time.
9. **Barrels:** accepted as destinations, and as sources if the config says so. Shulker boxes stay out for now.
10. **Bigger loads (JB):** carry up to **64** instead of 16, as a toggleable option. Items that stack to less than 64 are capped at their stack size; items that do not stack stay at 1.
11. **Look time:** a plain config number (vanilla 3 s).

**Order the golem tries (first match wins):** a container labelled for this item, then one already holding it (vanilla rule, now also barrels), then an empty unlabelled one, then the overflow container.

Sketch of a chest room (F = item frame label with 1 to 9 items, C = copper chest you drop loot into):

```
        C  (you drop loot here)
        |
   golem walks the row
        |
 [F:iron] [F:cobble] [F:wood,planks,logs] [F:food x9] [F:chest = overflow]
```

## Checked in the 26.3 game files (2026-11-05)

- Golem AI is `TransportItemsBetweenContainers`, set up in `CopperGolemAi`. Constants read from the jar: look time `TARGET_INTERACTION_TIME` 60 ticks (3 s); `IDLE_COOLDOWN` 140 ticks (7 s); `MAX_VISITED_POSITIONS` 10; `VISITED_POSITIONS_MEMORY_TIME` 6000 ticks (5 min: vanilla already remembers chests that did not work, our memory is the opposite, what worked); `TRANSPORTED_ITEM_MAX_STACK_SIZE` 16; search radius 32 sideways and 8 vertically. Sources are `BlockTags.COPPER_CHESTS`; destinations are `Blocks.CHEST` and `Blocks.TRAPPED_CHEST`. So every wiki number holds in 26.3, and barrels need the destination predicate widened.
- Carry size: `16` is used in `pickupItemFromContainer` (and once more in `getTransportTarget`, to read) so the mixin changes both. Destination choice is `isTargetValidToPick` (labels go here).
- Item frames: `ItemFrame.interact(Player, InteractionHand, Vec3)` is the hook for sneak + right-click and click-through; `HangingEntity.getDirection()` gives the side, so the container is the block behind the frame. The frame's vanilla item (`DATA_ITEM`) stays slot 1, so a vanilla client still sees one item; items 2 to 9 are saved as a Fabric attachment on the frame.
- Rendering: `ItemFrameRenderer` already reads `isInvisible` from its render state; drawing 2 to 9 items needs a client render mixin. Whether vanilla keeps the single item visible on an invisible frame is to check in game.
- The menu with two buttons is a custom Vestiges screen (the vanilla 3x3 screen has no buttons); the UI room can polish it. Editing labels must respect `SettlementService.lockedFor`.
- **26.4** is not released (the wiki says planned for Q4 2026). Its page so far lists no copper golem or barrel changes, only item frame bug fixes (part of the page was unread). The project stays pinned to 26.3; recheck when 26.4 snapshots arrive. An undated Mojang feedback post says a newer snapshot lets golems path only to chests they can reach; 26.3 already has path checks (`hasValidTravellingPath`).

## Open questions (small; defaults in brackets)

- Click-through default on, invisible default off. [yes]
- How the 2 to 9 items look on the frame face. [grid; show JB a picture before building]
- Build order. [after the MVP, like Workers]
