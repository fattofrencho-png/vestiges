# Dragons and the End: idea file

Design only. No code. Written by the Dragons and End idea chat; the main chat reads it and builds only what the user approved.

**Status (2026-11-04, later): the Vestige hall and ending spec is APPROVED (section "The Vestige hall and the ending"), for after the MVP. The alliance dimension is designed separately in the Alliances chat (`docs/ideas/alliances.md`). Open for others: the lore chat names the dragon-24 title; the main chat updates `cyclebreaker.json` and the design log wording.**

**Status (2026-11-04): the End dungeon theme and elytra spec is also APPROVED (section "End dungeons and elytra"). It puts the End theme in the MVP, drops the dragon 3 elytra, and needs the dungeons chat's spec widened by one theme.**

**Status (2026-11-03): the first-dragons spec (dragons 1 to 6) is APPROVED by the user and ready for the main chat.** All numbers in it stay drafts for the balance pass. Dragons 7 to 24, the End dungeons, the Vestige hall and the ending are not designed yet (next talks, after the MVP).
Never edits `docs/design-log.md` or `docs/lore/`. Lore used here comes from `docs/lore/lore-log.md` (read 2026-11-03).

## Already designed (source: design-log "The End", "Events" 24 dragons list, Batch 5 lore)

- **Arena only.** No outer islands, no End Cities. Elytra and shulker shells come from End-themed dungeon runs (End theme unlocks after dragon 1).
- **24 dragons**, then endless **Echo dragons** (3 random signature moves, +4 levels each, XP and a loot roll, no more pages).
- **Summoning:** 4 end crystals, summoner about level 4×N (dragon 24 ≈ level 96). Count is per world; everyone in the arena gets the rewards.
- **Every kill:** ender chest page each (start with 1, +1 per dragon, 25 total), lore chapter, XP, 1 guaranteed dragon-loot item. Act bosses (6, 12, 18, 24) also give a Relic★.
- **Arena repairs itself** after each fight. One new signature move per dragon, plus 1–2 earlier moves.
- **Feat extras:** dragon 1 title Dragonslayer · 6 extra Waystone · 12 Respec Tome · 18 3rd Gold Block · 24 title + ending. Feat files `dragon_6/12/18`, `dragonslayer`, `cyclebreaker` already exist.
- **Falling into the void:** you reappear at world top with Slow Falling.
- **The Vestige** (lore hall far out in the End, via the end gateway): Entrance + waystone first, 4 wings × 6 alcoves, one alcove rebuilds per kill, sealed centre for the ending. Not MVP unless the user says so.
- **Ending:** choice at the centre, Unmake (Cyclebreaker) or Reign (Sovereign). Waits until after the MVP.
- **Lore names (approved):** 1 Tulva · 2 Belaz · 3 Morst · 4 Dhars · 5 Ilvig · 6 Durhos · 7 Mortre … 24 Iginor. Working titles and signature moves are in the design log (1 The First, 2 The Ember, 3 The Gale, 4 The Crystal Keeper, 5 The Swarm Mother, 6 The Shade …). Dragon 1 = the vanilla dragon.

## Gaps and conflicts spotted

- **Elytra gap in the MVP:** elytra only comes from End dungeon runs, but the MVP dungeons have 1–2 themes. Needs a decision (see questions).
- Kill reward "lore chapter" needs the Codex/lectern path; the Vestige hall is the planned home but is big.
- Level gate is "about 4×N" with no rule for parties or for what happens below it.

## Decisions (user)

- **MVP dragons (user, 2026-11-03): 1 to 6** (all of Act I; dragon 6 is the first act boss with a Relic★).
- **Level gate (user, 2026-11-03):** below the gate the crystals refuse, with a message that names the level needed. Party rule still to choose.
- **Dragon loot (user, 2026-11-03):** a Level VI book roll, and emeralds plus XP only for dragons 1–5. *My reading, to confirm:* dragons 1–5 give emeralds + XP + a Level VI book roll; dragon 6 adds the guaranteed Relic★.
- **MVP extras (user, 2026-11-03):** the lore chapter as a Codex entry per dragon (the Vestige island waits). Elytra as a one-off reward for dragon 1 or 6, because End dungeons are not in the MVP (which dragon: to choose).

- **Party gate (user, 2026-11-03):** the summoner only. The dragon still scales to the arena (see spec).
- **Elytra (user, 2026-11-03):** a one-off reward from dragon 3, only while End dungeons don't exist. Remove it once the End dungeon theme gives elytra.
- **Book pool (user, 2026-11-03):** the normal Level VI roll, same pool as dungeons.
- **Difficulty (user, 2026-11-03):** much tougher than vanilla; health and damage scale strongly with the dragon's number.

- **Confirmed on the draft (user, 2026-11-03):** failed fight = the 4 crystals are consumed · dragon 1 has no gate · 8% max-health hit cap. **Spec not approved yet:** the user wants to talk through more parts first. **The six fights and their moves are approved (user, 2026-11-03).** Next: scaling and numbers, rewards, summoning.

---

## DRAFT: First-dragons spec (dragons 1 to 6), for the user's approval

All numbers are drafts, set at the balance pass. Everything is data-driven and sits behind the existing "Dragons" switch (off = the vanilla dragon, nothing else changes).

### Sketch: one dragon cycle

```
 Arrive in the End ──► Dragon 1 is already there (no summon, no gate)
        │
   KILL N ─► rewards ─► arena repairs (pillars regrow, ~60 s) ─► portal + egg as vanilla
        │
 Place 4 end crystals on the portal rim ──► gate check (summoner level ≥ 4×N)
        │            │
        │            └─ too low: crystals refuse, message "Needs level 8" (nothing consumed)
        ▼
  Dragon N+1 spawns (boss bar: lore name + title + level) ──► fight ──► KILL N+1 …
```

### Why this shape
- Dragon 1 is the dragon that is already in the End, so players are never blocked at the door. The gate starts at dragon 2 (level 8), which keeps the "summoner ≈ 4×N" rule from the design log.
- Reusing vanilla's 4-crystal respawn means no new item and no new block (vanilla-style rule). We only intercept it to run the check and pick dragon N+1.
- The count is per world, so everyone in the world shares the same progress, as agreed.

### Rules
- **Which dragon is next:** world kill count + 1, up to 6 in the MVP. After 6, summoning gives a plain vanilla-style respawn until the main chat adds 7+ (so nothing breaks).
- **Gate:** level needed = 4×N (dragon 2 = 8 … dragon 6 = 24). Checked on the summoner when the 4th crystal is placed. Failing it consumes nothing.
- **Scaling to the arena** (drafts): health = vanilla 200 × (1 + 0.4×(N−1)) × (1 + 0.5 per extra player within 100 blocks of the portal, max 4 players). So dragon 6 is about 600 HP alone and about 1500 HP with 4 players (user: +40% per dragon, +50% per extra player, 2026-11-03). Damage ×(1 + 0.2×(N−1)), so dragon 6 hits twice as hard as vanilla (user).
- **Boss bar:** lore name and title, for example "Belaz, the Ember · Lv 8". No Kesmar mention (the name appears only after dragon 24).
- **Who gets rewards:** every player who damaged the dragon or its crystals and is within 150 blocks of the portal when it dies. Logging out and back in during the fight still counts.
- **Anti-exploit:** one hit can't take more than 8% of the dragon's maximum health (stops bed and TNT bursts in the End); the dragon can't be damaged while players are unreachable in the void; the count only goes up on a real kill, never by command in survival; an arena with no player in it for 2 minutes resets the fight (health restored, crystals not refunded).
- **If everyone dies:** the dragon heals fully, stays a short while, then despawns. The count does not change. *The 4 crystals are consumed (to confirm).*
- **Arena repair:** after a kill the 10 pillars and their crystals come back over about 60 seconds, as the design log says.
- **Falling into the void:** unchanged (world top, Slow Falling).

### The six fights
Each dragon keeps vanilla's perching and breath, adds one new signature move, and keeps 1–2 earlier ones (from the design log).

| # | Lore name and title | New move | Earlier moves | Notes |
|---|---|---|---|---|
| 1 | Tulva, the First | none (vanilla) | none | Level 4, no gate. Gives the Dragonslayer title. |
| 2 | Belaz, the Ember | **dive-bomb ring of fire**: on each perch it drops fire in a ring around itself (user pick); stay out of the ring | 1 | Level 8 gate. |
| 3 | Morst, the Gale | **sideways shove** from its wings, with a warning sound first (user pick) | 2 | Level 12 gate. Also gives the one-off elytra. |
| 4 | Dhars, the Crystal Keeper | **only the last 3 destroyed crystals regrow, on a timer, a limited number of times** (user pick; confirmed 2026-11-03: after 60 s with a spark warning, at most 2 times per fight) | 3 | Level 16 gate. |
| 5 | Ilvig, the Swarm Mother | phantoms spawn while it perches (user, 2026-11-03: 2 every 30 s, max 5 alive) | 2, 3 | Level 20 gate. |
| 6 | Durhos, the Shade (act boss) | **flickers in and out of sight in flight, but always keeps a faint shadow** (user pick); Darkness while it perches | 3, 5 | Level 24 gate. First act boss. |

### Rewards (every qualifying player, once per dragon)
- **Ender chest page:** +1 page (page 2 for dragon 1 … page 7 for dragon 6).
- **XP and emeralds:** dragons 1–5 give XP and emeralds. Scales with the gate level (user, 2026-11-03): XP about one level's worth at the gate level, emeralds about 32 × N; exact numbers at the balance pass.
- **One Level VI enchanted book roll** from the normal pool (dragons 1–5). *Confirm: the user said "Level VI book roll" and "emeralds and XP only for dragons 1–5"; I read it as both together.*
- **Dragon 6 (act boss):** the same, plus a guaranteed Relic★ and the extra Waystone feat reward.
- **Extras:** dragon 1 title Dragonslayer · dragon 3 one-off elytra *(superseded 2026-11-04: the End dungeon theme is now in the MVP and gives the elytra, so this reward is dropped; see "End dungeons" below)* · dragon 6 feat `dragon_6`. Feat files for 6, 12, 18 already exist; they need a kill counter hooked in.
- **Lore chapter:** the dragon's chapter unlocks as a Codex entry (text from `docs/lore/`, the lore chat owns it; chapters 1–6 are Tulva, Belaz, Morst, Dhars, Ilvig, Durhos). The Vestige island waits until after the MVP.

### Build notes for the main chat
- New data type `dragons` (`data/vestiges/vestiges/dragons/*.json`): number, lore name, title, gate level, health and damage multipliers, move ids, reward ids. Dragons 7+ later are just more files.
- A `DragonService` stores the per-world kill count in saved world data and hooks vanilla's `EnderDragonFight` respawn; verify the 26.3 API before building. Moves are small server-side behaviours, unit-testable apart from the game.
- The `dragons` switch already exists in `VestigesSystem`; off must leave vanilla behaviour.
- Untested at design time: all numbers, the 8% hit cap, and how the invisible dragon looks in 26.3.

## Act II, dragons 7 to 12 (The Breaking): APPROVED by the user (2026-11-04), after the MVP

Same rules as the first-dragons spec (gate 4×N, summoner only, 4 crystals, health ×(1 + 0.4×(N−1)) and +50% per extra player, damage ×(1 + 0.2×(N−1)), 8% hit cap, rewards to everyone who hit it). Numbers are drafts for the balance pass. After MVP.

| # | Lore name and title | Gate | Health alone | Damage | New move (user picks, 2026-11-04) | Earlier moves (my draft) |
|---|---|---|---|---|---|---|
| 7 | Mortre, the Quake | Lv 28 | 680 | ×2.2 | **Ground shakes and flings you up**, no falling: cracks and dust for 1.5 s on the outer ring, then everyone on it is thrown up and landed softly (short Slow Falling), so there is no void death | 2 ring of fire, 3 shove |
| 8 | Graciel, the Frost | Lv 32 | 760 | ×2.4 | **Breath turns the floor to ice so you slide**: frosted ice that melts after about 10 s; a movement hazard, not damage | 3 shove, 7 quake |
| 9 | Nitox, the Venom | Lv 36 | 840 | ×2.6 | **One cloud type at a time, alternating each perch**: green poison (milk cures it) or black wither (regeneration or a golden apple helps). Clouds last about 10 s and drift slowly | 5 phantoms, 2 ring of fire |
| 10 | Dunther, the Stormcaller | Lv 40 | 920 | ×2.8 | **Lightning with a glowing mark 1.5 s before each strike; a lightning rod placed within about 8 blocks of the mark draws the strike** | 3 shove, 8 ice |
| 11 | Megia, the Mirror | Lv 44 | 1000 | ×3.0 | **At half health it splits in two, one real, one fake.** The fake pops in one hit and re-forms after about 20 s; the real one has a faint glint; hitting the fake gives the attacker a short Slowness | 6 flicker, 7 quake |
| 12 ⭐ | Hergun, the Devourer (act boss) | Lv 48 | 1080 | ×3.2 | **Flies to a pillar crystal, eats it (3 s bite) and heals 8% of its maximum health.** Destroy crystals first, or hit it mid-bite to stop it. No crystals left = no healing | 9 clouds, 10 lightning |

- **Rewards (user, 2026-11-04):** as Act I: +1 ender chest page each (pages 8 to 13), XP and emeralds scaled to the gate, a Level VI book roll (dragons 7 to 11), the Codex chapter (Mortre, Graciel, Nitox, Dunther, Megia, Hergun). **Dragon 12 adds a guaranteed Relic★ and the Respec Tome** (the `dragon_12` feat already exists).
- **Build notes:** new moves are small server behaviours like Act I's. Ice and cloud effects must restore the arena and leave no block behind. The lightning-rod redirect is our own rule (vanilla rods do not redirect scripted strikes), so test it.
- **Not decided here:** the exact health of the fake Mirror, the Devourer's behaviour if players keep every crystal alive for too long, and balance numbers.

## End dungeons and elytra (talk started 2026-11-04)

**Already in the design log (reused):** End theme = purpur, end stone bricks, end rods, void edges · endermen, shulkers, endermites, phantoms · caches purpur, end stone, end rods, chorus flowers, shulker shells, elytra (rare) · bosses: Lieutenant The Shellguard (levitation volleys), Boss The Void Stalker (teleports behind you, floor blocks vanish briefly), Overlord The Void Colossus (phase 2 splits into 3) · modifier End Void Pull · unlocks after dragon 1 · lore diary of 8 pages of the Ascended (dragon 13, Scande) found across rooms, a full diary gives XP and its story in the Codex.

**Decisions (user, 2026-11-04):**
- **End theme is in the MVP, with the normal first-person camera.** The isometric (Minecraft Dungeons style) camera is dropped for now ("forget it for now"). The dungeons chat's approved MVP (Overgrown and Trial) therefore grows by one theme; the main chat must tell that chat or fold it into the dungeon build.
- **Parked camera ideas, in case the camera comes back:** WASD moves relative to the screen, the character faces the mouse cursor, click attacks that way; keep the player visible both by building rooms with no ceiling and low near walls, and by fading blocks that block the view. Overlaps with the map chat's isometric view idea.
- **Elytra:** once per player, guaranteed, from the first End Boss clear at tier 4 or higher (tier to confirm, see below). After that elytra stays a rare cache find, so a lost one can be found again. This replaces the one-off dragon 3 elytra.
- **Shulker shells:** come from End dungeon caches, as the log says. With the End theme in the MVP, no dragon stopgap is needed (so shulker boxes and A2's shulker-box feature stay reachable).
- **Void:** falling into the void in an End dungeon costs a life and puts you back in the start room, same as dying.
- **Look:** ruined purpur halls of the Ascended with open void gaps, plus floating islands joined by bridges; the boss room reuses the dragon arena look; the 8-page Ascended diary is found across the rooms (follows whatever diary rule the dungeon MVP uses; diaries are not in its spec yet).

**Final picks (user, 2026-11-04) — End dungeon MVP spec APPROVED, ready for the main chat:**
- **Elytra tier: 3** (inside the tested range 1 to 3). Once per player, guaranteed, on the first End Boss clear at tier 3 or higher; later elytra only as a rare cache find.
- **Bosses are plain strong named mobs, like the other themes:** Lieutenant **The Shellguard** = a pack of 3 strong shulkers; Boss **The Void Stalker** = an enderman with big health. Signature moves (levitation volleys, teleport behind you, floor blocks vanishing) and the Overlord come after the MVP.
- Build it with the dungeon MVP slices (room markers, key with theme and tier, 13-room run); End unlocks after dragon 1.

**Draft details (mine, to confirm at build time):** void gaps cover at most a quarter of a room's floor and bridges are 3 blocks wide, so a miss is a mistake and not a trap. The End modifier Void Pull (levitation near the void) waits with the other modifiers.

## The Vestige hall and the ending (talk started 2026-11-04; after the MVP)

**Already designed (lore log, Batch 5):** a very big island far out and very low in the End, reached through the end gateway (every gateway leads there; a return gateway goes back to the arena); no mob spawns there. First build = the Entrance + a waystone; the rest shows as ghost and that space can't be built in. Layout: Entrance, 4 wings (one per act) × 6 alcoves (one per dragon), a sealed centre. Each kill: alcove N rebuilds over a few seconds with a lectern (chapter as a book, also in the Codex), a mural painting and the dragon's head on a pedestal; act bosses 6, 12, 18 open the next wing. After dragon 24 the centre holds an altar for the choice: Unmake (hall fades grey and ghostly; title Cyclebreaker; world keeps running as a fading echo; Echo dragons = its last memories) or Reign (centre becomes a throne room; title Sovereign; master powers at your Capital's Bell: set time, set weather, change the next dungeon theme; Echo dragons = the makers trying to take the world back). Building is free on and around the island outside the hall's protected space; markers are banned in the End except the Vestige waystone (trips cost 1 emerald). Art: 24 murals (pixel art; the user's Screenshot-to-paintings project can help).

**Decisions (user, 2026-11-04):**
- **How it is made:** "fully generated by our asset creator" = Asset Studio (the Assets chat's tool: Request, AI questions, build with checks, 3D preview, save, export to `.nbt` structure files). Code pastes the exported files. *Dependency to check:* Asset Studio does trees and plants first; houses and large structures are "later", so a hall this size needs that step first. Assets stay vanilla blocks only.
- **Ghost look:** a faint particle outline drawn on the client, plus an invisible no-build zone kept on the server. No new blocks.
- **Who chooses:** each participant chooses for themselves at the altar; their own title and their own Reign powers or grey fade.
- **Not final:** a player can switch between Unmake and Reign later at the altar (the earned title stays; how the switch works is open).

**More decisions (user, 2026-11-04):**
- **Reign and Unmake are to be rethought together** as a pair (the earlier Reign powers at the Bell and Unmake's fading echo are drafts until then).
- **Titles:** you can choose a title as soon as the Hall is unlocked and built. It is chosen at a designated spot in the hall, and you can change your title there at any time.
- **The poem:** a different version of the same poem appears after each kill. Put together in some way, the versions form another text that tells who the author is and why they love Minecraft (a personal message; the user writes it, I design only how it is hidden and revealed).
- **Lore reveal:** at 24 kills (the ending moment is not at the first kill, so the end poem is the combined text, not a one-off).
- **Switching between Unmake and Reign:** costs special points earned in dungeons, or emeralds, or is free; a server setting, **emeralds by default**. *Open: what "special points from dungeons" are (the Gate tree's points, or a new count).*

- **Poem trick:** each of the 24 versions has one highlighted line; the 24 lines in order form the hidden text. A Codex page gathers the lines you have found.
- **Title spot:** a title lectern in the Entrance (available as soon as the hall is built).
- **Switch cost:** emeralds only for now (the "dungeon points" option is parked until we know what they are).

**Reign and Unmake become two alliances (user, 2026-11-04).** This replaces the earlier "master powers at the Bell" draft.
- **No world-wide powers:** neither path can control time or weather, and neither has any effect on the Echoes (Echo dragons and Echo mode are the same for everyone; the lore lines about Echo dragons stay flavour only).
- **Each path has its own benefits** (not yet chosen).
- **They cannot coexist:** a member of one alliance cannot claim areas (Bells, Banners and so on) inside an area claimed by the other.
- **Multiplayer idea:** parts of the world are contested, and control shifts with alliance numbers.
- The earlier parked idea (affiliation, clans, regions fought over) is now part of this.

**The Alliance dimension (user, 2026-11-04) — to be designed in its own chat:**
- **Benefits (chosen):** Reign = **the Settled** (home and villagers), Unmake = **the Unbound** (adventure and travel). Mild, equal in value, no world-wide effect. Details in the Alliances chat.
- **Joining (chosen):** as soon as the hall is built, at the title lectern (not at dragon 24). *Lore note:* the story says the Unmake or Reign choice comes with the truth at dragon 24, so the main chat and the lore chat should check how the two fit (for example the alliance is joined early and the full ending scene stays at 24).
- **A separate dimension, per world:** generated with its own randomness, as one massive continent with different biomes. Two bays, one on each side. Each alliance starts with a small safe area (about 10% each).
- **Base building is shared:** an alliance builds its base together. Once a base reaches a certain level, that part can never be taken over. The bigger the base, the more control the alliance gets over the rest of the continent, up to a maximum share per alliance.
- **The clash:** at some point the two bases hold so much control that they clash, so they must keep investing (improving the base, a massive city or "Alliance Castle").
- **Ownership:** inside controlled territory, members of the alliance can build and do whatever they want, but the **alliance owns it, not any player**. If the other alliance takes the territory, it takes whatever was built there.
- **Events:** players of both sides can connect for PvP events, with various PvP arenas in that dimension. Large-scale PvP happens only there; the rest of the world is normal.
- **Replaces** my earlier question about flipping settlements in the main world (the main world is untouched).

**Decisions on the ending and the gate (user, 2026-11-04):**
- **Dragon 24 triggers:** the lore reveal with the full combined poem text · Echo mode starts (endless Echo dragons and endless dungeon tiers, the same for both alliances) · a final title for everyone · the mob Lore texts unlock in the Codex. The world is never reset.
- **Final title name is open:** "Cyclebreaker" is now the Unmake alliance's title (below), so the title everyone gets at 24 needs a different name (lore chat to propose; the feat file `cyclebreaker.json` and the design log's "24 title Cyclebreaker" need updating by the main chat).
- **Hall centre:** the final lectern with the full poem and the lore, plus a monument to every dragon. It opens at 24. No choice happens there any more.
- **Alliance titles:** **Sovereign** = the Reign alliance, **Cyclebreaker** = the Unmake alliance, given on joining at the title lectern.
- **Getting there:** a waystone takes you from your home to the hall. In the hall, the portal to the Alliance dimension **changes colour with your alliance and takes you to your own side** of that dimension (a different part for each alliance). Built with no new blocks (the colour is a display effect on the vanilla frame).
- **Everything about the Alliance dimension goes to the Alliances chat** (brief in the shared notes folder, `alliances-brief.md`).

**Last hall details (user, 2026-11-04):**
- **Wing doors:** when act boss 6, 12 or 18 dies, that wing's ghost doorway rebuilds as a plain open archway, the same rebuild effect as the alcoves. No keys or items.
- **Protection:** hall blocks are unbreakable for everyone except operators; no mobs spawn; explosions do not harm it. Operators can repair it with `/vestiges` commands.
- **Murals:** 24 pixel-art paintings made with the user's Screenshot-to-paintings project, one per cycle. A placeholder painting stands in until each is ready.
- **Final title at dragon 24:** its name is open. Default: the lore chat proposes it (the user did not pick one). Until then it is called "the 24-kill title" in the data.

## Parked (outside my scope)

- Clans as a wider late-game affiliation beyond the two alliances (user, 2026-11-04). Fits the social chat.

- Isometric camera for dungeons (user parked it 2026-11-04): see above. Also listed in the log as "Isometric-camera dungeons" under M8 Dungeons.
