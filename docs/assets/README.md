# Assets room

Assets have their own chat, in the `RPG Mod Assets` folder on the `assets` branch. Asset Studio is our schematic library: it imports our own builds and builds by others, reviews them one by one, adapts them with one-click edits, names them, and exports them. It can also make trees and plants with an AI (Claude or Gemini), which is optional.

## Rules

- This chat commits to `assets` only when the author asks, and never pushes. `assets` goes into main only when the author says so, and the main chat handles all pushes.
- Decisions and the lists for other chats live in `asset-log.md`.
- Vanilla blocks only. Names here are working names; real names come from `docs/lore/` through the author.
- Nothing from the Minecraft jar is copied into the repo or published. The tool reads it on this computer while running. Rendered views do go to the chosen AI company, unless turned off in Settings.
- Never hand-edit `data/vestiges/structure/` or `docs/assets/credits.md`. **Add to mod** writes them, from saved versions only.
- Credit is optional, and `credits.md` lists entries with a creator. Builds by others should have one: filter the Library by **No credit** to find the gaps. Check permission before adding anything to the mod.
- Downloads can hold any blocks. **Add to mod** takes vanilla blocks only.
- Chest contents, sign text, command block data and entities are not copied.
- When stopping a game, only stop Java processes from this folder (`*RPG Mod Assets*`). Never run `gradlew --stop`.

## Using it

Double-click **`Asset Studio.bat`**.

- **Import builds:** many `.schem`, `.litematic` or structure `.nbt` files, whole folders, mod `.jar` and datapack `.zip` files, or a box from a saved world. Review them one by one or in bulk from the Library. No key is needed.
- **Names:** `kind_subject_materials_detail_WxHxD_source_varNN`, proposed on save and editable. See `naming.py`. Each saved version of an asset is one variant.
- **Make with AI:** needs an API key in Settings. Request, Questions, Building, Preview, Saved, Export.

- Keys and settings: `~/.vestiges-assets/`, outside the repo.
- The library: `~/.vestiges-assets/library/`, outside git. Only assets added to the mod enter the repo. **Back this folder up**: git doesn't hold it, and source files aren't kept.
- `tools/assets/exports.json` says where each tree's ground and trunk are inside its file. Roots go below the ground, so `/place template` needs the position lowered by `ground_y`.

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
