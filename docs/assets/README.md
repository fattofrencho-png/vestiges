# Assets room

Assets have their own chat, in the `Minecraft Asset Manager` folder (under `E:/Claudia/Projects`) on the `assets` branch. Asset Studio is our schematic library: it imports our own builds and builds by others, reviews them one by one, adapts them with one-click edits, names them, and exports them. It can also make trees and plants with an AI (Claude or Gemini), which is optional.

## Rules

- This chat commits to `assets` only when the author asks, and never pushes. `assets` goes into main only when the author says so, and the main chat handles all pushes.
- Decisions and the lists for other chats live in `asset-log.md`.
- Vanilla blocks only. Names here are working names; real names come from `docs/lore/` through the author.
- Nothing from the Minecraft jar is copied into the repo or published. The tool reads it on this computer while running. Rendered views do go to the chosen AI company, unless turned off in Settings.
- Never hand-edit `data/vestiges/structure/` or `docs/assets/credits.md`. **Add to mod** writes them, from saved versions only.
- Credit is optional, and `credits.md` lists entries with a creator. Builds by others should have one: filter the Library by **No credit** to find the gaps. Check permission before adding anything to the mod.
- Downloads can hold any blocks. **Add to mod** takes vanilla blocks only.
- Chest contents, sign text, command block data and entities are not copied.
- When stopping a game, only stop Java processes from this folder (`*Minecraft Asset Manager*`). Never run `gradlew --stop`.

## Using it

Double-click **`Asset Studio.bat`**.

- **Import builds:** many `.schem`, `.litematic` or structure `.nbt` files, whole folders, mod `.jar` and datapack `.zip` files, or a box from a saved world. Review them one by one or in bulk from the Library. No key is needed.
- **Names:** `kind_subject_materials_detail_WxHxD_source_varNN`, proposed on save and editable. See `naming.py`. Each saved version of an asset is one variant.
- **Make with AI:** needs an API key in Settings. Request, Questions, Building, Preview, Saved, Export.

- Keys and settings: `~/.vestiges-assets/`, outside the repo.
- The library: `~/.vestiges-assets/library/`, outside git. Only assets added to the mod enter the repo. **Back this folder up**: git doesn't hold it, and source files aren't kept.
- `tools/assets/exports.json` says where each tree's ground and trunk are inside its file. Roots go below the ground, so `/place template` needs the position lowered by `ground_y`.

## Organiser (first round of sorting)

Every build is checked when it is imported: `analyse.py` reads the build itself (blocks, size, rooms, doors, materials) and proposes its type, species, realm, palette, scale, complexity and rarity, each with a reason and how sure it is. Proposals are not decisions: yours always win (`facets.py`). Plan and progress: `organiser-plan.md`.

The app opens on the **Dashboard**: what to do next, the inbox, progress against your goals, what is ready for the videos and the mod, and health. From the Dashboard you can note a pack's rights, export a catalog for the videos (`~/.vestiges-assets/exports/video-catalog.json`) and send vanilla, rights-cleared builds to the mod. Open **Review** in the header to check what the analyser found. One build at a time: **A** accepts everything shown, **1-9** change a row, **X** archives (or deletes), **U** undoes, **E** opens the workbench, **?** lists the keys. Queues: Not reviewed yet, Sure first (with a batch accept), Unsure first, Look-alikes, Needs work, Accepted, Archived.

```bat
python tools/assets/triage.py summary          rem counts by type, review state, sure and unsure
python tools/assets/triage.py analyse          rem analyse builds that have not been analysed (makes a backup first)
python tools/assets/triage.py analyse --all    rem analyse everything again; your decisions are kept
python tools/assets/triage.py numbers --import F rem adopt the video catalog's card_numbers.json
python tools/assets/triage.py categories       rem show kinds that would change; add --apply to fix them
python tools/assets/test_triage.py             rem organiser tests
```

## For developers

```bat
python tools/assets/test_assets.py      rem unit tests; no key, no cost
```

Set `VESTIGES_FAKE_AI=1` before starting `tools/assets/studio.py` to add a free scripted "Test AI". Words in its request change what it does: `failplan`, `alwaysfail`, `askuser`.

The AI never writes code that runs here. It returns a plan using the fixed tools in `plan_tools.py`, and every plan is checked before building. `pipeline.py` holds its instructions, including the author's taste.

## 26.3 world format

World palettes hold plain text such as `minecraft:stone`. When a list also holds compounds, Minecraft wraps text as `{"": "minecraft:stone"}`. Worlds live in `dimensions/minecraft/<dimension>/region`.

## 26.3 structure file format

Palette entries use `id` and `properties`, not the older `Name` and `Properties`. The game raises no error for the old keys: it reads every block as air and still reports success. A unit test compares our keys with a vanilla file.
