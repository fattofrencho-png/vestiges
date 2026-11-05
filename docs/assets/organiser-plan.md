# Asset Studio organiser: plan (DRAFT v1, 2026-11-05, for the author's OK)

Goal: the funnel in already exists (imports, packs, online). This plan builds the funnel **through**: every asset is checked
automatically, the machine proposes tags, and the author reviews fast (accept / change / reject). Organised assets then feed
two consumers: the **video funnel** (Minecraft Inspiration) and **Vestiges world generation** (better trees and houses
replacing the basic ones). Spec for folders, dials and numbering: `E:\AI Work\Videos\Minecraft Inspiration\Dev\TAXONOMY.md`.

## Decisions (author, 2026-11-05)

- Build order accepted as written in section 6; one combined test after the Review screen (step 3).
- Optional style/realm helper allowed later: the **offline picture model** (layer 2). The cloud vision AI (layer 3) is not approved.
- **Reject** gives both options in the Review screen: **Archive** (default: hidden, kept, restorable, with a reason) or **Delete now**
  (removes the asset from the library at once; asks to confirm, since the library is outside git).

## Progress

- Step 0 done (2026-11-05): the 381 wrong "ruin" kinds were repaired from the analyser's reading of the builds (old kind kept in `category_was`; backup in `~/.vestiges-assets/backups/categories-*`). Cause of the original bulk rewrite is unknown.
- Step 1 done: `tools/assets/facets.py` (facet records, human beats machine, remembered rejections, review states).
- Step 2 done: `tools/assets/analyse.py` (layer 0 rules and measurements, layer 1 fingerprints) and `triage.py` (runner, category repair, summary). The import path runs the analyser on every new build. Index rows carry `f_type`, `f_species`, `review`, `conf`, `undecided`, numeric size and the three fingerprints.
- First run on the real library (376 builds, 10 seconds): 123 trees with species, 85 houses, 82 street and plaza pieces, 32 workshops, 29 farms, 11 other (9 tree packages, 1 jigsaw piece); 202 sure, 35 unsure; 5 groups of exact duplicates that ignore turning and mirroring, 8 shape families.
- Tests: `tools/assets/test_triage.py` (15) and the existing 157 all pass.
- Step 3 built (2026-11-05): the **Review** screen (button in the header). Backend `tools/assets/review.py` (queues, decisions, undo, look-alikes, batch accept, rights) and `studio.py` endpoints `/api/review/queue|asset|decide|batch`; screen `web/review.js`.
  - Queues: Not reviewed yet, Sure first, Unsure first, Look-alikes, Needs work, Accepted, Archived, Everything; filters for type, species, pack and search. The queue and filters are remembered.
  - Keys: A or Enter accept all, 1-9 change that row, X archive (then 1-6 for the reason, D delete now), N needs work, S or right arrow later, left arrow previous, U or Ctrl+Z undo (steps back through your last 30 decisions per asset), E workbench (with a Back to Review button), ? shows the keys.
  - Each row shows the value, how sure the machine is (dots), who said so, why in plain words, and tick or cross to agree or reject just that row. A rejected row is not offered again.
  - Look-alikes under the picture: same build turned or mirrored (with a keep suggestion), same shape in other materials, same materials.
  - Batch accept in the Sure first queue: preview with a sample, then confirm. Rights are shown strictly: an import with nothing noted reads "Rights unknown (to confirm)", not "own".
  - Tested: 21 organiser tests and the existing 157 pass; the screen was driven in the browser on a copy of the library (accept, undo, archive, change a row, look-alikes, batch dialog, workbench and back). Your real library is untouched: all 376 are still "not reviewed".
- Not built yet in step 3: 3D view inside the Review screen (E opens the workbench), saved queues beyond the last view and filter, per-rule acceptance rates, re-analysing after workbench edits.
- Step 4 built (2026-11-05): the **Dashboard** (opens with the app; also the Dashboard button). Backend `tools/assets/dashboard.py` and endpoints `/api/dashboard`, `/api/targets`, `/api/triage/analyse`; screen `web/dashboard.js`.
  - **Focus next**: a fixed, explainable ladder, best first: analyse new builds; accept the sure builds in one go (twins excluded); review a folder that has a goal, a gap and builds waiting; pick keepers among look-alikes; answer the weakest guesses; name goals with nothing waiting; note the source of builds with unknown rights. The top 4 show, the rest sit behind "More".
  - **Inbox** counts open the matching queue. **Feeds** shows builds ready for the videos and for world generation (accepted, rights noted; for the world also vanilla) and how many are held back and why. **Health** shows no source noted, not vanilla, look-alike groups, analysis errors, not analysed, and the last backup.
  - **Catalog progress** is counted only against goals. Agreed goal: 8 per tree species (72). Every other folder shows a count until you press "set goal" (stored in `~/.vestiges-assets/targets.json`). It counts accepted builds, not how good they are: ranking into series comes in step 7.
  - Batch accept now leaves out builds that have an exact twin (turned or mirrored copy); their keeper is chosen in the Look-alikes queue.
  - Tests: 26 organiser tests and the existing 157 pass; the dashboard was driven in the browser on a copy of the library (focus buttons open the right filtered queue; a Sure first batch of 202 was accepted and the numbers moved). Your real library is untouched.
- Not built yet in step 4: the coverage matrix by realm and style (only Overworld exists, so rows only for now), and the export buttons (step 5).
- Step 5 built (2026-11-05): the two exports. Backend `tools/assets/exporter.py`; endpoints `/api/export/video`, `/api/export/world`, `/api/exports`, `/api/rights/packs`, `/api/rights`; Dashboard buttons in the Feeds card.
  - **Rights gate both feeds.** A build is ready only when its rights are noted (own, open licence, or permission); a creator name alone does not count. New screen **Rights by pack** (Dashboard, Feeds card): type the creator, licence or permission once for a whole pack. Nothing is filled in for you: on your real library all 376 are still "rights unknown", so 0 are ready until you note them.
  - **Video export**: `~/.vestiges-assets/exports/video-catalog.json` and `.csv`, every accepted build with folder, subfolder, species, realm, style, palette, setting, scale, complexity, rarity, a permanent model number (`#004-00`), series key, size, rights, `video_ready`, credit, source path, `duplicate_of` (the keeper of a turned or mirrored twin), fingerprints and an empty `recipe`. It is not written into the Minecraft Inspiration project: that project reads it from there.
  - **Model numbers** are permanent, kept in `~/.vestiges-assets/card_numbers.json`. The 364 numbers the video catalog already gave were adopted (for example the acacia tree stays #553), and new ones come from the same shuffled pool, so nothing clashes. `python tools/assets/triage.py numbers --import <card_numbers.json>` repeats the adoption.
  - **World export**: `exports/worldgen.json`, accepted + vanilla + rights noted, one of each twin pair, no packages. Each entry has the structure name, `replaces` (trees: the vanilla tree it replaces; houses: "village house"), biomes, weight by rarity, size and the ground and trunk offset. The **Send to the mod** button saves proposed names for builds that have none, writes the list, then opens the existing Send to mod screen, where every file is confirmed before anything reaches the mod.
  - **To confirm with the mod side**: the vanilla tree ids in `replaces`, the biome lists and the weights are suggestions; no house replacement ids yet.
  - **Packages** (files that hold many builds, like the 10 tree packages) are never "sure", are left out of batch accept and out of both exports, and have a "Package" archive reason.
  - The analyser is version 2; the real library was re-analysed (backup in `~/.vestiges-assets/backups/triage-*`): 376 builds, 192 sure, 35 unsure, still all "not reviewed".
  - Tests: 30 organiser tests and the existing 157 pass. Browser-tested on a copy of the library with test rights ("CC0" typed into the COPY only): batch accept, rights by pack, both exports, and the Send to mod screen.
- Next: step 6 only if too many guesses stay unsure after you review (offline picture model); step 7, model and variant numbers, recipes, series and rarity in series, starting with the Oak series.

## What exists today (audit, 2026-11-05)

- Review is the edit workbench (`drawAdapt`). No accept/reject, no per-field decision, no keyboard shortcuts.
- Statuses: review / saved / skipped. Nothing says who set a tag or how sure it is.
- Import already does: exact-duplicate hash, vanilla-block validation, a kind guess from file path words, subject and tags from
  the path and the top 2 materials. It does not know species, size class, style, quality, rarity.
- **Bug: all 381 library entries have category "ruin"** (even acacia trees). Needs a re-guess pass first.
- "Review one by one" on a filtered list already exists, but the queue is lost when you leave and is not resumable.
- Bulk actions default to ALL filtered rows when nothing is ticked (risky).
- Frontend: plain JavaScript, one file (`web/app.js`), full redraw per action. Index is small (236 KB for 381). Fine up to thousands.
- The Minecraft Inspiration side already has scoring code worth moving in: `cardinfo.py` (metrics, difficulty, score),
  `rarity.py` (points with reasons), `catalog.py` (clusters, tiers). Its classifier reads **titles and paths only**, so it
  would fail on an unknown pack. The new analyser must read **the build itself**.

## Design in one picture

```
 IN                     CHECK (automatic)                  REVIEW (you)                    OUT
 packs, jars,           Analyser, 4 layers                 Inbox of proposals              Video export
 schematics,    --->    L0 rules + measurements   --->     accept / change / reject  --->  (catalog.json, cards)
 online, own            L1 fingerprints (dupes,            keyboard first, undo,
                        variant families)                  batches, groups                 World-gen export
                        L2 picture model (optional)        Dashboard: progress +            (mod-ready, replaces
                        L3 vision AI (optional, opt-in)    next best action                 basic oak / house)
```

## 1. Data: proposals are not decisions

Each facet on an asset is a record, not a bare tag:

`facet` (type, subtype, species, realm, style, palette, setting, scale, complexity, rarity) · `value` · `source`
(rule / fingerprint / picture / vision / import / human) · `confidence` · `why` (plain words) · `status`
(proposed / accepted / changed / rejected) · `analyser_version` · `decided_at`.

Rules:
- A human decision always beats a machine one. Re-running the analyser never overwrites a decision.
- Rejected proposals are remembered, so they are not proposed again.
- Type is single-valued (your 28 folders). The dials are separate facets. Parent inheritance: "oak" implies "tree".
- Measured numbers (blocks, size, variety, height, enclosed volume, door/window counts, symmetry) are stored as numbers, not text, so they can be sorted and filtered.
- Asset review state: **Inbox** (analysed, waiting) → **Accepted** (in the catalog) or **Rejected** (archived, never deleted automatically, with a reason) or **Needs work** (open in the workbench).
- Model/variant numbers (`#315-07`) and the recipe live on the asset; lineage comes from fingerprints.

## 2. The analyser (the automatic first round)

Runs on every import and on demand ("Re-analyse these"). Cheap layers first; expensive layers only for what is still unsure.

| Layer | What it does | Cost | Notes |
|---|---|---|---|
| L0 Rules and measurements | Block palette (logs/leaves = tree; glass panes, doors, beds = house; spawners, chests = dungeon), bounding box, height profile, enclosed air (rooms), roof slope, symmetry, floating share, size class, difficulty, newest-block share. Reuses `cardinfo.metrics`, `difficulty`, `score`. | free, instant | Type is strong here; style and realm are weak |
| L1 Fingerprints | Exact hash with all 8 rotations/mirrors folded in (fixes rotated copies), shape-only hash (ignores materials), palette hash. Gives: duplicate, palette variant, same shape family, and variant-vs-new-model (your 25% setting). | free, instant | Own code first; Nucleation (MIT) is an option to test later |
| L2 Picture model (optional) | Zero-shot style/realm labels from the rendered thumbnail with a small local model (SigLIP 2). Proposes style, realm, mood with a confidence. | free, minutes for thousands; needs a download | Unproven on Minecraft renders; we measure agreement with your decisions before trusting it |
| L3 Vision AI (optional, opt-in) | Only for leftovers the layers above are unsure about. Claude (cheapest model) or Gemini sees the render. | roughly $3-7 per 30,000 renders (estimate); sends renders to the AI company, as Settings already warns | Off by default |

Every proposal carries a plain-words reason ("12 logs and 340 leaves, 2 wide trunk, so oak-like tree"). Packs are also grouped:
if one pack is 90% villages, the pack name becomes a hint for its members.

First job, before anything else: **re-guess the 381 wrong "ruin" categories** with L0.

## 3. The review system (most important part)

Separate from the edit workbench. One screen, one asset at a time, keyboard first.

```
+--------------------------------------------------------------------------------------+
| Review: Trees > unreviewed  |  47 left of 123  |  today 31 done   [sure-first v]       |
+----------------------------------+---------------------------------------------------+
|                                  |  Type      Tree > Oak        ●●●●○  sure  [change] |
|        3D / thumbnail            |  Style     Rustic            ●●○○○  guess [change] |
|        (drag to rotate,          |  Realm     Overworld         ●●●●●  [change]      |
|         top/side buttons)        |  Palette   Plains            ●●●○○  [change]      |
|                                  |  Scale     Medium  22x20x18 · 1,018 blocks         |
|  Looks like: #4, #85 (same shape)|  Complexity Standard          Rarity: Rare (7 pts) |
|  [Compare side by side]          |  Why: 12 logs, 340 leaves, 2-wide trunk...         |
|                                  |  Rights: unknown  Vanilla: yes  Mod-ready: yes     |
+----------------------------------+---------------------------------------------------+
|  A accept all   1-6 change field   X reject   S later   U undo   E workbench   ← →     |
+--------------------------------------------------------------------------------------+
```

How it works:
- **A** accepts everything shown and auto-advances. Changing one field turns just that field into "changed", then A accepts the rest. **X** rejects with one key for a reason (duplicate / ugly / wrong / not vanilla). **U** undoes the last decision, any number of times. **← →** go back and forth through decided assets and re-open any decision.
- **Queues** are saved views you can resume: "unreviewed Trees", "unsure first" (the machine's weakest guesses, where your answer teaches most), "sure first" (fast batch accept), "duplicates and look-alikes" (shown as a group with the best one pre-selected as keeper), "new from this pack", "rejected" (to reconsider), "missing rights".
- **Batch accept** for high-confidence groups: preview the count and a sample grid, then confirm. Never an action on "all" without a count and a confirm.
- **Rules with a track record**: the dashboard shows each rule's acceptance rate (for example "oak by logs/leaves: 97% accepted over 60 reviews"). A rule above a threshold you choose can auto-accept its proposals; you see a summary and can undo it.
- **Speed**: the next asset's thumbnail and data are loaded ahead, so there is no wait between decisions. Queue and filters are remembered when you leave.
- **Learning from you**: where you correct the machine often, the dashboard says so, and we improve that rule.

## 4. The welcome dashboard (opens with the app)

```
+--------------------------------------------------------------------------------------+
| Asset Studio                                                        Library 1,432 assets |
+--------------------------------------------------------------------------------------+
| FOCUS NEXT                                                                             |
|  1. Review 31 sure-first Oak trees (about 4 min)  → fills 2 of 4 gaps in Oak series     |
|  2. 12 look-alike groups waiting (pick a keeper)                                        |
|  3. Pack "Medieval Pack.zip": 214 new, analysed, none reviewed                          |
+------------------------------+-------------------------------+-------------------------+
| INBOX                        | CATALOG PROGRESS              | FEEDS                   |
|  to review 218               |  Trees      ███████░░░ 68/72  |  Videos: 51 cards ready |
|  unsure 64   sure 154        |  Houses     ██░░░░░░░░  9/60  |  World: 18 mod-ready    |
|  duplicates 12 groups        |  Towers     ░░░░░░░░░░  0/30  |   (oak 6, birch 4, ...) |
|  rejected 40                 |  [open coverage matrix]       |  [export now]           |
+------------------------------+-------------------------------+-------------------------+
| HEALTH: 14 no credit · 3 not vanilla · 0 analyser errors · last backup 2 days ago       |
+--------------------------------------------------------------------------------------+
```

"Focus next" ranks actions by (size of gap) × (candidates available waiting for review), so it recommends what moves the catalog
most. It shows a daily batch, not the entire backlog. Every number is a button that opens the matching queue.

## 5. The two feeds

- **Video feed**: `catalog.json` is exported from Asset Studio (no longer edited elsewhere). Fields: folder, subfolder, realm,
  style, palette, setting, scale, complexity, model_no, variant_no, recipe, rarity, series, rights, video_ready. The Minecraft
  Inspiration project reads it; it stays read-only for them. Series and coverage matrix are derived from it.
- **World-gen feed**: only accepted, vanilla-only, rights-cleared builds marked **mod-ready**. Adds a **replaces** field
  (for example "plain oak tree", "small plains house"), the ground and trunk offset (`exports.json` already holds this for
  trees), a size class and biome list, so the mod can pick a better build for each spot. Goes through the existing **Send to mod**
  path, which already refuses non-vanilla blocks.

## 6. Build order

| Step | What | Result you can see |
|---|---|---|
| 0 | Fix the 381 wrong categories with L0 rules; add numeric stats to the index | Kind filter works |
| 1 | Facet records with source, confidence, status; Inbox / Accepted / Rejected | Data model, migration of existing library |
| 2 | Analyser L0 + L1 (rules, measurements, fingerprints, reasons), runs on import | Every asset arrives pre-labelled |
| 3 | New Review screen (keyboard, undo, queues, batch accept, groups) | The big UX improvement |
| 4 | Welcome dashboard + coverage matrix | Progress and next best action on open |
| 5 | Video export + world-gen export (trees first) | Both consumers fed |
| 6 | L2 picture model and L3 vision AI, if the first pass leaves too many unsure | Better style/realm guesses |
| 7 | Model/variant numbers, recipes, series and rarity-in-series | Oak series template |

Steps 0-3 are the core. Test at one combined milestone after step 3 (with your 381 real assets), then after step 5.

## Existing solutions (checked)

- **Borrow ideas, not code**: Hydrus (inbox→archive, "denied" tab, parent tags), TagStudio (tag data model), digiKam and PhotoPrism
  (confidence thresholds, label source, remembered rejections), Prodigy and Label Studio (accept/reject keys, predictions kept apart from
  decisions), Photo Mechanic and Lightroom culling (auto-advance, strictness).
- **Possible library**: Nucleation (MIT, Python, reads all our formats, fingerprints, headless render). Test on this PC before depending on it.
- **Avoid**: litemapy (GPL-3.0, matters if the app is ever distributed). We already have our own readers.
- A DAM app (Eagle, Hydrus) cannot understand schematics, so none replaces this; they only inform the review design.

## Risks and unknowns (to confirm)

- Style and realm will be the weakest guesses (published voxel-style classifiers are near 55% on four broad styles). Type, size, rarity and duplicates will be strong. That is why the review keeps the human in charge and why L2/L3 are optional.
- No evidence yet that SigLIP works on Minecraft renders; we measure it against your first 100 decisions.
- Nucleation on Windows without a GPU: unverified.
- "Replaces" mapping for world generation needs the Vestiges mod side agreed (which structures it swaps and where). Not designed yet.
