# Asset naming convention (AGREED with the author 2026-11-05; built in `cards.py`, `libname.py`, `sizeclass.py`)

One permanent card number, three names for three uses. Names are generated from the asset's record (never typed by hand), except the display name, which the author edits in Review.

## The card number: `#SNNN-SSNN`

Example: `#1001-0101` = Trees, model 001, Nether, variant 01. The original oak is `#1001-0001`.

- **Model `SNNN`**: `S` = the folder (1 Trees, 2 Gardens, 3 Terrain, 4 Water, 5 Houses ... from the catalog's 28 folders, `Videos/Minecraft Inspiration/Dev/TAXONOMY.md`), `NNN` = the model counter inside the folder, 001 to 999. *To confirm: the author said houses are 2000s; the catalog order puts Houses at 5. Folders 10 and up need a two-digit `S` (`10001`), which is also to confirm.*
- **Variant part `SSNN`**: `SS` = the realm code, `NN` = the variant number inside that model and realm, 01 to 99 (author, 2026-11-05).
- **Realm codes `SS`** (to confirm; two digits so 100 codes exist): 00 Overworld (regular), 01 Nether, 02 End, 03 Elven, 04 Dwarven, 05 Wizard / Arcane, 06 Undead / Haunted, 07 Fairy / Mushroom, 08 Dragon / Fire, 09 Frost, 10 Pirate, 11 Tribal. More are added at the end, never renumbered. A change of style, palette or season is a variant (`NN`), described in the name and the recipe; `SS` stays the realm only.
- **When it is given:** when the author accepts an asset in Review, from its Type (folder) and Realm, because those can still change during review. It is permanent after that.
- **Existing numbers:** the 381 assets in the library carry random numbers imported from the video catalog. They need renumbering before anything is exported, and the video side (Minecraft Inspiration) must be told first.
- Shown to people as `#1001-0101`; the same digits in file names (`1001-0101`).

## Catalog: folder numbers (proposed)

The model number is `folder x 1000 + counter`, so every folder owns a thousand: Trees `1001` to `1999`, Houses `5001` to `5999`, Mega builds `28001` to `28999`. Folders follow the catalog order in TAXONOMY.md. The folder number is never reused or reordered; new folders get the next number (29, 30 ...). In file names the model number is padded to five digits (`01001`, `05001`, `28001`) so lists sort; people see `#1001`, `#5001`.

| No. | Folder | Numbers | In the library now |
|---|---|---|---|
| | **NATURE** | | |
| 1 | Trees | 1001 to 1999 | 123 |
| 2 | Gardens and plants | 2001 to 2999 | 1 |
| 3 | Terrain | 3001 to 3999 |  |
| 4 | Water | 4001 to 4999 | 1 |
| | **HOMES** | | |
| 5 | Houses | 5001 to 5999 | 85 |
| 6 | Interiors | 6001 to 6999 |  |
| 7 | Furniture and props | 7001 to 7999 |  |
| | **DEFENCE** | | |
| 8 | Towers | 8001 to 8999 |  |
| 9 | Gates | 9001 to 9999 |  |
| 10 | Walls | 10001 to 10999 |  |
| 11 | Castles and fortresses | 11001 to 11999 |  |
| 12 | Forts and camps | 12001 to 12999 |  |
| | **TOWN** | | |
| 13 | Workshops and shops | 13001 to 13999 | 32 |
| 14 | Civic and public buildings | 14001 to 14999 | 5 |
| 15 | Streets and plazas | 15001 to 15999 | 82 |
| 16 | Farms | 16001 to 16999 | 29 |
| 17 | Industry and tech | 17001 to 17999 |  |
| | **TRANSPORT** | | |
| 18 | Bridges and roads | 18001 to 18999 |  |
| 19 | Ships and vehicles | 19001 to 19999 |  |
| 20 | Harbours and stations | 20001 to 20999 |  |
| | **SACRED AND MAGIC** | | |
| 21 | Temples, shrines and graves | 21001 to 21999 | 7 |
| 22 | Portals and magic sites | 22001 to 22999 |  |
| | **LEARN AND DETAILS** | | |
| 23 | Details | 23001 to 23999 |  |
| 24 | Techniques and how-to | 24001 to 24999 |  |
| 25 | Starter and survival | 25001 to 25999 |  |
| | **SPECIAL** | | |
| 26 | Statues and pixel art | 26001 to 26999 |  |
| 27 | Sports and entertainment | 27001 to 27999 |  |
| 28 | Mega builds and cities | 28001 to 28999 |  |

Library counts are from the analyser's proposed Type on 2026-11-05; 11 assets are 'Other' and 5 old ruins are not analysed, so they have no folder until reviewed.

## Three names

| Use | Example | Rules |
|---|---|---|
| Library (after review) | `trees_oak_1001-0001_young-oak_11x23x11_forest-autumn.nbt` | `type_species_card_name_size_tags`. Groups by what it is, then sorts by card number; size and the tags (realm if not Overworld, style, palette, setting, season) come last. Rarity, complexity and scale are not in it. Regenerated when a row is corrected; the card number is the anchor. |
| Videos | `#1001-0001 · Young oak`, catalog id `1001-0001` | Short; series and rarity are badges, not part of the id. |
| Schematics and world generation | `vestiges:trees/oak_1001_0001` | Lowercase letters, digits and underscores only (Minecraft rule); built from type, species and card number so it never changes; size, biomes and placement live in `worldgen.json`. |

## Built (2026-11-05)

- Numbers are given at accept (`cards.py`, counters in `~/.vestiges-assets/card_registry.json`); a recoloured twin (same shape, other materials) becomes a variant of the accepted model; an exact turned copy gets a model of its own.
- The library name, the video label and the game id are made by `libname.py`; the world export, the video catalog and Send to mod use them (`exporter.py`, `send.py`).
- Size is `reach` (longest side) and `players` (reach / 1.8) in the measures, and a class inside the type (`sizeclass.py`): Compact, Typical, Grand, Giant.
- New rows in Review: Kind (Build, Piece, Package). Pieces and packages have their own queue and stay out of the catalog, the videos and world generation. Farms have subtypes; houses get a first-guess subtype.
- The 381 random numbers from the video catalog are no longer used (`card_numbers.json` and `exporter.assign_numbers` stay only for the old tests). The video side must be told that numbers changed before anything is exported.
- Open: numbering inside a folder is plain counting by acceptance order.
- The code today (`libname.py`, `exporter.py`) still uses the old random numbers and the first naming rules. Nothing is changed until this is agreed.

## Organising issues found in the library (2026-11-05, to decide with the author)

Data from the 376 builds on the analyser's proposals:

1. **Farms mix several things.** Crop fields, animal pens, stables, large farmsteads and even street pieces ("Streets crossroad/corner/turn/split" sit under Farms). Proposal: Farms gets subtypes (crop field, animal pen, barn and stable, mill, silo and granary, orchard, farmstead for a building with fields) and a rule that anything titled "Streets ..." is a street piece.
2. **Packages are not assets.** 10 "package" files (all the trees of one species, "All trees package") sit in Other. They must be split into single builds first, then deleted or archived.
3. **Pieces versus builds.** 82 street tiles (height about 2) are kit pieces, not designs. Proposal: a Kind (build or piece); pieces stay in the library but are not counted in the catalog goals or shown as video cards.
4. **Scale words say nothing.** Medium is 88 of 123 trees and 62 of 85 houses. Needs the relative size formula below.
5. **Houses have no subtypes** (81 of 85 empty). Needs rules (size, storeys, rooms) for Starter hut, Cabin, Cottage, Townhouse, Manor and so on.
6. **Palette twins are separate assets.** Better Villages ships each building in plains, savanna, taiga, snowy and desert (for example the weaponsmith). By the numbering they are one model with palette variants, not five models.
7. **Size formula (proposal).** Two numbers per asset: the absolute size (longest side in blocks, also shown in player heights, a player is 1.8 blocks high), and a class within its own type from the percentile of that type's builds (smallest 25% Compact, middle Typical, top 25% Grand, top 5% Giant). The class moves as the library grows, so it stays out of file names; the exact dimensions are in them.
