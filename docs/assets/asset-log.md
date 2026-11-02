# Asset log

The record of asset decisions. Written by the assets chat, read by the main and world chats. Rules: see `README.md`.

## For the world chat

- **Placing trees:** nothing places Asset Studio's trees in worlds yet. Trees wider than about 32 blocks can't be normal tree features. They need placing as structures or through a small Java feature.
- **Vines:** placing a tree with `/place template` dropped 23 of 258 vines in the one tree tested. It only affects the look. The likely cause is that the game places from the bottom up, before a hanging vine's upper part exists. A placer that adds vines last would keep them all.

## For the main chat

- Add this room to `CLAUDE.md` when `assets` is merged.

## Not yet tested

- A real, complete Claude request: the first one reached the server and stopped because the API credit was empty.
- Gemini: its code has never run through a full asset.
- Walk mode: needs a real mouse.
- A real `.schem` file from another tool: none exists on this PC, so that reader has only seen files this tool wrote. Real `.litematic` files, game jar structures and a 26.3 world are tested.
- Our exported `.schem` in WorldEdit or Litematica. It is only checked by reading the file back.
- For the batched playtest: `/place template` with an imported build. Check its rotated stairs and doors, that air clears the inside of a house without cutting the land around it, and with a tree that has permanent leaves, walls and barriers, that the leaves stay and the walls join.

## Lost data

- On 2026-11-01 a test run moved the author's two earlier AI assets (`20261101_094014`, `20261101_094442`) into a temporary library, then deleted it. They weren't in git. The cause is fixed: a run with its own library no longer moves the old folder.

## Decisions

Newest first, with the date.

- ✅ **Library (author, 2026-11-01).** Asset Studio is our schematic library, for our own builds too. Credit fields are optional, and any number of files, folders and archives can be loaded and reviewed one by one. Saving proposes a detailed name, and each saved version is a variant. Assets live in `~/.vestiges-assets/library/`, outside git. Tested with all 1,511 structures in the game jar: 93 seconds, 1,293 added, 218 exact duplicates skipped, 4.8 MB, search about half a second.
- ✅ **Import first (author, 2026-11-01).** The author judged the AI-made trees bad, and has permission to copy builds by others. Asset Studio now imports real builds and adapts them with one-click edits, needing no AI or key. Credit goes in `credits.md`. AI stays optional. Later ideas: AI edits on imported builds, and learning proportions from the library.
- ✅ **Air when placing (2026-11-01).** A build's `.nbt` adds air only in spaces it encloses, so a house clears its inside without cutting a box out of the land around it. This replaces keeping the source's air, as first planned.
- ✅ **Asset Studio workflow (author, 2026-11-01).** Request, AI questions (required or optional), confirmed brief, build with automatic checks and AI self-review (3 tries, then ask the author), 3D preview, agree or change, save, export. Claude or Gemini, by API key, with Claude first. Trees and plants first; houses and large structures later.
- ✅ **Size taste (author, 2026-11-01).** On the first ancient oaks (38 to 59 blocks wide): "way too big compare to regular minecraft trees, and have way too many leaves and trunk". The AI now stays near vanilla sizes and keeps trees airy unless asked otherwise. The four test oaks were dropped.
- ✅ **Permanent leaves (2026-11-01).** Leaves with no real log in reach are saved as permanent, so trunks of walls work. The preview warns about them, and about barriers.
