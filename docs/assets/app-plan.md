# Asset Studio app: plan (v2, 2026-11-04)

Goal: Asset Studio becomes a desktop app, for the author's own use, that creates, finds online and manages every kind of Vestiges asset.

## Decisions (author, 2026-11-04)

- **Shell:** own window (pywebview) around the current screen, packed into one `Asset Studio.exe` (PyInstaller) with a desktop icon. No rewrite.
- **Asset types:** structures and trees (exists), textures and models, datapack content, sounds / icons / UI images.
- **Manage first:** tags, favorites and collections; send to mod in one click; extract from any archive; library backup.
- **Online search (new):** the app goes online, searches for designs and assets, and imports what the author picks. See below.

## What `mc-asset-forge` (the author's earlier prototype) adds

Checked 2026-11-04 (`Downloads/mc-asset-forge.zip`, 2,600 lines, v0.1). Most of it Asset Studio already does better (more file formats, 3D preview, 1,500-item library). We take only what is missing:

| Take | Why |
|---|---|
| Modrinth source (search, download, pull structures out of datapacks) | The online search. Checked live: the API works with no key, but results are mixed (many are game-mechanics packs, not builds) and most builds are "All Rights Reserved" |
| Licence on every asset, `can_modify` flag, licence badge | Online assets come with terms; the library only has a credit today |
| Download safety: own folder per download, size cap, zip-slip guard, polite delay, User-Agent | Downloads are untrusted |
| Spec + seed generator with presets (oak, spruce, willow, cherry, petrified) | Makes trees offline, free and repeatable; the Claude API credit is empty. Compare with our `tree_gen.py` before copying |
| Later ideas: vision tagging, critic loop, sketch input | Parked, after the milestones below |

Not taken: its SQLite library, `.schem` reader/writer, PNG renderer, CLI. Asset Studio's own versions are broader. Its Modrinth path and Claude path were never run by its author.

## Online sources

- **Modrinth** (first): official API, no key, licence in the result. Datapacks and mods carry structures.
- **GitHub** (second): API search for repos with `.nbt` / `.schem` / `.litematic`; licence in the result.
- **CurseForge** (later): official API, needs a free key from the author.
- **Planet Minecraft, minecraft-schematics.com:** no API and their terms limit automation, so the app only opens the page in the browser. The author downloads, then drops the file into the app.
- **AI web search** (optional, Claude/Gemini): suggests links and ideas; the app never downloads on its own from them.

Rules: a download happens only when the author clicks Import on a result. Each goes in its own folder, with a size cap. Files are parsed, never run. The licence is shown before import and stored. Results marked "All Rights Reserved" show a warning (the author has permission for personal use, see the asset log, but it stays visible).

## Milestones

- [x] A1 Window and exe (2026-11-04): `app.py` opens the screen in its own window, `build_app.bat` makes `Asset Studio.exe` (49 MB, in `Assets/`, ignored by git, keep it there), exe has its own icon. Smoke-tested: window opens, server answers. A desktop shortcut is still to do (to confirm with the author). The old `Asset Studio.bat` still works.
- [x] A2 Library backup (2026-11-04): `backup.py`. A dated zip in `~/.vestiges-assets/backups/` at app start when the library changed (at least 20 h after the last), the newest 10 kept; Settings has "Back up now" and a Restore button per backup, and a restore first makes a "before restore" backup. 5 new tests, 92 pass. The real library (133 assets) was backed up by the rebuilt exe. Restore button not yet clicked in the real UI.
- [x] A3 Online search, Modrinth (2026-11-04): `online.py` + a "Find online" screen. Search datapacks or mods, "only likely builds" filter (worldgen and decoration), result cards with licence and a warning for all-rights-reserved / non-commercial / no-derivatives, Open on Modrinth, Import. Import downloads the newest .zip/.jar (Modrinth's host only, checksum-checked, 100 MB cap), then opens the normal Import screen with creator, licence and link filled in. 7 new tests with a fake network, 99 pass. Live: search and version lookup work; a real download through the screen is not yet tried. Limits: a search with the filter on can return few results (2 for "oak tree"), and a blank mod search lists popular mods, not builds. The User-Agent has no contact yet (to confirm).
- [x] A4 Licence on every asset (2026-11-04): `licences.py` sorts each asset into Your own / Open / Permission noted / Limited / Unknown, with `can_modify` (yes, no, or to confirm). The Library has a Licence column and filter (including "Needs checking"), the asset screen shows it and has a new Link field (Find online now fills Link, no longer Permission), adding a limited or unknown build to the mod asks for a yes, and `credits.md` lists licence, source and status. 4 new tests, 103 pass. All 133 current assets count as your own (none were imported with a creator or licence). Imports from the game jar also count as "own" for now; to confirm whether they should be marked as Mojang's.
- [x] A5 Tags, favorites, collections (2026-11-04): `organise.py`. In the Library: a star on every row, a Favorites filter, collection and tag filters (with counts), and for the ticked assets (or all shown) buttons to add or remove a tag, add to or remove from a collection, and make or remove a favorite; "Manage collections" renames or deletes a collection (the assets stay). The asset screen has Collections and Favorite fields. Collections are named groups; one exists while it has assets. 3 new tests, 106 pass. Not yet clicked through in the real window.
- [ ] A6 Any archive: drop a zip / jar / datapack, browse everything inside by type, pick what to keep
- [ ] A7 Preset trees without AI: spec + seed generator with the Forge presets, sliders, "variations"
- [ ] A8 Online search, more sources: GitHub, then CurseForge (with key), plus "open on Planet Minecraft" links
- [ ] A9 New asset types: library rows get a kind (structure, texture, model, data, sound, image) and a viewer for each; Modrinth resource packs become a source for textures
- [ ] A10 Send to mod: choose assets, copy into the Vestiges `src/main/resources` and `data` folders with the right paths, show what changed
- [ ] A11 Datapack content editors (loot, recipes, quests, codex), tied to the generators in `tools/`

## Parked (author, 2026-11-04)

- **Refine the online search later:** better matching than the worldgen/decoration filter (few results for "oak tree"), a way to rank builds above mechanics packs, blank-search results, previews before importing. Revisit after A4 to A6, once a few real downloads show what works.

## Notes

- Each milestone fits a 1 to 2 hour session. A9 and A11 are the big ones and may each be split.
- Order: A3 right after A1 and A2, since online search is the new headline feature.
- The library stays outside git; A2 protects it.
- Modrinth asks for a User-Agent with a contact; use `vestiges-asset-studio/<version> (<author's contact>)`. The contact is to confirm with the author.
