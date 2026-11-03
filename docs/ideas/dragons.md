# Dragons and the End: idea file

Design only. No code. Written by the Dragons and End idea chat; the main chat reads it and builds only what the user approved.

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
- **Extras:** dragon 1 title Dragonslayer · dragon 3 one-off elytra · dragon 6 feat `dragon_6`. Feat files for 6, 12, 18 already exist; they need a kill counter hooked in.
- **Lore chapter:** the dragon's chapter unlocks as a Codex entry (text from `docs/lore/`, the lore chat owns it; chapters 1–6 are Tulva, Belaz, Morst, Dhars, Ilvig, Durhos). The Vestige island waits until after the MVP.

### Build notes for the main chat
- New data type `dragons` (`data/vestiges/vestiges/dragons/*.json`): number, lore name, title, gate level, health and damage multipliers, move ids, reward ids. Dragons 7+ later are just more files.
- A `DragonService` stores the per-world kill count in saved world data and hooks vanilla's `EnderDragonFight` respawn; verify the 26.3 API before building. Moves are small server-side behaviours, unit-testable apart from the game.
- The `dragons` switch already exists in `VestigesSystem`; off must leave vanilla behaviour.
- Untested at design time: all numbers, the 8% hit cap, and how the invisible dragon looks in 26.3.

## Parked (outside my scope)

*(none yet)*
