# Batch C: Records and social (design only)

Status: **ordered list approved by the user, 2026-11-03.** Echo details, pet call limits, painting size and season length are still open. Nothing here is built. Source row: `docs/design-log.md` "Later batches", row C (after M7).
Already decided elsewhere and reused here: gravestone (3 Minecraft days, then a skull with the most valuable item), death markers and the Locator Bar "Go there" button, Add/Decline sharing with a block option, "after death: each player picks in Settings from what the server allows", per-player loot in multiplayer.

## Decisions so far (user, 2026-11-03)

### Friends (shared base for the whole batch)
- **One mutual friend list** (both players accept, with a block option). It serves waypoint and waystone sharing, tracking and the album. Replaces the per-feature "friends" wording in the Map section.

### Graveyard page and cemetery
- **Graveyard page** in H: lists your deaths (date, place, cause, level, what was lost, gravestone collected or crumbled).
- **Cemetery as a place:** a real built area (shape to decide).
- **Fallen workers and villagers** are remembered too (ties to M7 workers and M6 villagers).
- **Map keeps past deaths** as grey markers after the gravestone has crumbled or been collected (new: today the marker disappears).

### Tracking
- **Friends on the map**, opt-in: each player chooses who may see them (always, nearby only, off).
- **Pets:** list with location and health, a "Go there" button, and call or teleport home (limits to decide).
- **Locator Bar** integration as well as map dots (user ticked both).

### After-death choices (each player picks in Settings from what the server allows)
- **Gravestone** (default, as designed).
- **Respawn point choice.**
- **Drop items normally** (vanilla).
- **New: a copy of the player (an "echo")** created at death, usable in different ways, such as defending the grave or appearing sometimes. Details to decide, see open questions.

### Grave system, in steps (user, 2026-11-03)
- **MVP (this is all that ships first):** the pick-up gravestone, **switchable on/off by the server**, with a **setting for the timer** before it disappears, **also switchable** (timer off = it stays).
- **Step 2, the Tomb:** a gravestone nobody collects becomes a **tomb with a barrel** holding some of the original items. Server switches: how many items go in the barrel (1 or more), whether the tomb also disappears over time, whether other players can destroy it, and similar.
- **Step 3, the Echo** (a copy of the player, by server switches and each player's choice):
  - Both forms: **guards the grave** and **wanders or haunts** (appears sometimes, can hunt or haunt you, can become a boss or a random mini-boss). The user wants room to grow many mechanics from it; each one is its own talk.
  - **Cosmetic only:** copies your skin and gear look; real items never sit on the echo (no duplication).
  - Other players must beat the guard to loot a grave (the owner passes freely); a small reward (XP or emeralds by level) once per death, capped against farming; fades once the grave or tomb is gone.
- **Cemetery: both**, a memorial plaque block (shows the graveyard page of the area, buildable by players) and an optional settlement building.

### Screenshot album and paintings
- **Album page in H** (thumbnails with place, date, who was in view), a **photo key with a camera frame** (clean shot, no HUD), and **sharing to friends** (Add/Decline, as for waypoints).
- **Paintings:** an album photo can be turned into a **placeable painting** (pixelated to the vanilla look; the server keeps a small copy so everyone sees it; size limit). Builds on the user's Screenshot-to-paintings project. The End murals reuse it later.
- Fairness: a server switch for player paintings and for photo sharing; blocked players can't send; the server stores only the small painting copy, never the full album.

### Rankings page
- **Records and firsts** (level, deaths, dragons, settlements, discoveries, dungeons) on a **whole-server** page, **per dungeon season** so newcomers can compete.
- Each player can **hide from people who aren't their friends** (user).
- Fairness: only server-counted numbers; no number a player can set; operators can reset or exclude a player.

### Armor stand editor
- **Pose editor screen** (sliders and presets), **equipment and size/visibility toggles** (arms, base plate, small, invisible, locked), **save and share poses**, and a **mannequin of yourself** (your skin and gear look, cosmetic only, no real items copied).
- Fairness: locked stands follow the claim rules, so only the owner edits; posing never duplicates items.

## Open questions
- Echo details (boss version, hunting rules, how it scales, opt-out for players who don't want to be haunted).
- Pet call limits (cooldown, emerald cost, distance).
- Painting size limit and how many per player.
- Season length for rankings.

## Ordered feature list (draft, for the user's approval)
Order follows what each item needs. Rule: every feature has a server switch and leaves vanilla behaviour when off.
1. **Grave MVP** (switch plus timer switch). Part of M1/MVP in the main chat; written here only so the later steps have a base.
2. **Friends list** (mutual, block), moving the existing sharing settings onto it. Everything social below needs it.
3. **Graveyard page** in H, and past deaths kept as grey map markers.
4. **After-death choices** in Settings (gravestone, drop normally, respawn point choice, later echo), limited to what the server allows.
5. **Tomb** (grave step 2: barrel with some items, with the server switches above).
6. **Tracking:** friends on the map and Locator Bar (opt-in), pets list with Go there, then call or teleport home.
7. **Armor stand editor** (pose, toggles, save and share, mannequin).
8. **Screenshot album** with photo key and sharing.
9. **Paintings from album photos.**
10. **Rankings page** (needs dungeons from M8 for its dungeon season board; the other boards can start earlier).
11. **Cemetery** (plaque block, then settlement building; needs M6 settlements, and fallen workers from M7).
12. **Echo** (guard first, then wanderer, haunt, boss and mini-boss; each its own talk).

Not done here (outside scope, goes to the main chat): the grave MVP switches, and removing the "marker disappears when it crumbles" rule so past deaths stay on the map.
