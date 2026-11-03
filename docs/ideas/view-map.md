# View and map extras (batch B) — idea chat notes

Design only. No code. Source: `docs/design-log.md` row B and the "## Map" section. Decisions here are drafts until the author says OK.

## Already decided elsewhere (do not redo)
- **Freecam:** free flying through walls; server can turn it off; works like x-ray in survival, so the welcome page says so; off in every preset (design log, "Comfort" group).
- **Peek:** only containers you could open; unopened loot chests stay hidden (design log).
- **Map (built):** minimap, world map, layers, waypoints, death markers, sharing, "Go there" on the vanilla Locator Bar (design log "Map", playtested).

## Scope of this chat (batch B)
1. Chat heads, and heads on the Locator Bar
2. Better F3 · spawn sphere · mod spheres · light levels
3. Cave detection
4. Map extras: mobs, 3D and isometric views, moving sun, drawing areas
5. Peek (already decided, needs a shape) · Freecam (already decided, needs a shape)
6. Riding and boat view
7. Better sign writing
8. From the Small Packs line: dynamic lights, falling leaves, ambient sound

## Open questions
- Round 2 (sent 2026-11-03): cave rule fix, map mobs, 3D vs isometric, info tools shape.

## Decisions
Round 1 (author, 2026-11-03):
- **Priority first:** map extras, info tools (F3, spheres, light levels), freecam + peek.
- **Left for "later" (not in first version of B):** dynamic lights, falling leaves + ambient sound, riding and boat view, better sign writing. Cave detection and chat/Locator Bar heads stay in.
- **Heads (chat + Locator Bar):** a personal setting ("player's choice"), applies to all players.
- **Cave detection problem:** standing under a tree counts as "cave", and so does being inside a house. The rule must tell a real cave from a roof (tree canopy, house).

Round 2 (author, 2026-11-03):
- **Cave rule:** both rules together — a spot is "cave" only with natural overhead (stone/dirt/deepslate, not player-placed; leaves, planks, glass never count) AND it is well below the surface height.
- **Map mobs:** hostile, passive and players/pets, all with a toggle on the map. Whether mobs are shown only when seen (anti-exploit) is not yet answered.
- **3D / isometric:** world map only (button switches flat / isometric); minimap stays flat.
- **Info tools (spawn sphere, mod spheres, light levels):** in-world overlays, each with its own toggle key; the server can limit them.

Round 3 (author, 2026-11-03):
- **Fairness:** map mobs and spawn sphere are free for all; the server can switch them off (no feat gate).
- **Moving sun = a time-of-day control on the world map:** the player freely sets the time of day on the map and the relief shadows change with it. Not tied to the real in-game clock.
- **Drawing areas = free-shape settlement claims (not free-hand art):**
  - When claiming, choose the usual automatic circle, or draw the area on the map around the placed marker.
  - The drawn area is capped by the maximum zone size allowed at that level.
  - When the zone levels up (renown), it grows bigger keeping the same outline.
  - Author is unsure it is doable. Feasibility note: yes — store the outline as a polygon around the marker, check its area against the cap on the server, and grow it by scaling/expanding the outline to the new radius. Pacified/ward checks then test "inside polygon" instead of "inside circle". Cost: every marker rule (Settlements, Ward Lantern, Banner, Gold Block fixed radii) assumes a circle, so this reaches into the Settlements milestone. **Needs the main chat's OK** (and probably the idea-city chat): the map side is only the drawing tool.
- **Peek:** a toggleable feature with several settings, on by default.

Round 4 (author, 2026-11-03):
- **Mod spheres:** settlement + marker radii (Settlement, Ward Lantern, Banner, Gold Block) and waystone reach / worker areas, drawn in the world with a toggle key.
- **Better F3:** dropped. Vanilla F3 stays as is.
- **Freecam:** detached camera, the body stays put; toggled by a key; server can disable; off in every preset (see design log).
- **Map time of day:** a slider on the world map (drag to change the shadow direction, button to reset).

## Ordered feature list (APPROVED by the author, 2026-11-03)
1. **Heads** in chat and on the Locator Bar; personal setting.
2. **Cave detection fix:** a spot is "cave" only with natural overhead AND well below the surface.
3. **Map extras:** mob dots (hostile / passive / players and pets, each toggled) -> isometric button on the world map -> time-of-day slider (shadows follow it).
4. **In-world overlays**, each with a toggle key and server limit: light levels -> spawn sphere -> mod spheres (settlement/marker radii, waystone reach, worker areas).
5. **Peek:** toggleable, several settings, on by default; only containers you could open.
6. **Freecam:** detached camera, body stays put, server can disable, off in every preset.
7. **Drawing areas (free-shape claims):** the map side is only the drawing tool; it waits for the Settlements work to accept non-circle zones (see hand-over note). Last in B.
8. **Later, not in B v1:** dynamic lights, falling leaves, ambient sound, riding and boat view, better sign writing.
Dropped: better F3.

## Parked outside scope
- Free-shape settlement zones change the Settlements rules (radius checks become "inside outline"): hand-over to the main chat / City chat.
