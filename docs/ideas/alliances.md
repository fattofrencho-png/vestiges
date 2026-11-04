# Alliances: Reign (the Settled) and Unmake (the Unbound) (design only)

Status: **talk held 2026-11-04, after the MVP. Spec approved by the user 2026-11-04 (with slice 1 as the first build).** Nothing is built. Origin: the Dragons chat (`docs/ideas/dragons.md`, "The Vestige hall and the ending"); brief in the shared notes folder (`alliances-brief.md`). This chat never edits `docs/design-log.md` or `docs/lore/`; the main chat merges.

## Already decided before this chat (user, 2026-11-04, Dragons chat)
- Two alliances, joined as soon as the Vestige hall is built, at the title lectern in the Entrance. **Sovereign** = Reign (the Settled). **Cyclebreaker** = Unmake (the Unbound). Titles are given on joining.
- Switching costs emeralds by default (server setting; free also allowed).
- Benefits are mild, equal in value (about +10 to 15%), never world-wide. No control of time or weather. No effect on Echo dragons or Echo mode.
- The alliances cannot coexist: one cannot claim areas inside an area claimed by the other.
- Separate Alliance dimension per world: one massive generated continent, different biomes, two bays, each alliance starts with a safe area.
- Shared base building; alliance owns what is built, not players; the other side takes it if it takes the land. PvP events and arenas only there; the rest of the world is normal.
- Getting there: waystone from home to the hall; the hall's portal changes colour with your alliance and sends you to your own side (display effect on the vanilla frame, no new blocks).
- For all: server switch, vanilla when off, no new block types, anti-exploit, emeralds only currency, data-driven, offline players never lose a base.

## Decisions (user, 2026-11-04, this chat)

### 1. Benefits (each about +10 to 15%, local, never world-wide)
- **Reign, the Settled:** cheaper villager trades; cheaper worker wages.
- **Unmake, the Unbound:** more dungeon XP; better dungeon loot (small chance bump).
- Numbers are tuned at balancing; both sides stay equal in value. Perks apply for the member everywhere (they are personal), not only in the dimension.

### 2. Joining and switching
- Join at the title lectern; the title is given. Switching costs emeralds by default (server setting). **What you built stays with the old alliance** (it owns it); you lose access; there is a **cooldown** before switching again (server setting).

### 3. The map model: core, influence, wild
- **Core:** starts as the base. It grows with the base up to **10% of the continent per alliance**, permanently safe, never lost, even while members are offline. Members may build their own bases inside it, but the better use is improving the alliance base. Both cores together are 20% of the map.
- **Influence (the other 80%):** each base emits influence that grows with the base and takes more and more of the terrain, also beyond the core. When both are big enough the influences **meet in the middle**: an **influence war**.
- **Influence land is protected but raidable in events:** outside the core the other side cannot break or fight there normally; they can attack it only during scheduled events.
- **The wild** (land no influence holds, and where influences meet) is an almost no-rules PvP zone. Its terrain is **renewed one piece at a time, in rotation** (the whole unclaimable terrain, never owned land or cores). Players building there lose it at renewal.
- **Border:** no preference given. Working default (confirm at build): the border is where the two influences are equal and moves in small steps per real day, with a warning to the losing side. Buildings in land that flips go to the winner (as decided).
- **What pulls players out:** rich resources and loot and ruins (both come back after each renewal), influence points from winning fights or holding ground, and dungeon portals in the wild (hard content stays opt-in).

### 4. The base: score, levels, ranks
- **Score only:** control is counted by base score, never by who is online.
- **Score comes from:** blocks placed (less credit for repeating one block, as renown does), key buildings, emeralds donated to the alliance treasury, and villagers living in the alliance city.
- **Levels reuse the 7 settlement levels** (Camp, Settlement, Hamlet, Village, Town, City, Capital). Core size and influence reach grow per level; the top level is the **Alliance Castle**. A level is reached by score thresholds and, for the higher ones, a required key building.
- **Heart:** each bay already has the base's heart (a vanilla bell, display only). No Bell feat needed; the base starts at Camp.
- **Ranks:** Leader, Officer, Member. Draft rights (to confirm): Leader sets treasury spending, ranks and invites; Officers build key buildings, invite and call off members; Members build and donate.

### 5. Events (PvP arenas)
- **Start:** by a server schedule; an event starts when each side has enough members who joined.
- **Rewards:** influence for the winning side; emeralds and XP by performance (capped per event, no farming); titles and feats (cosmetic).
- **Fair fights:** keep inventory in arenas; team balance (the larger side is capped to the smaller); opt-in only to join, and the wild carries a warning; **offline players are never touched**.

### 6. Singleplayer: modes, chosen in settings
No rival exists, so the dimension has modes (some may come to multiplayer later):
1. **Free build:** build on your side; no rival, no takeover. **First slice.**
2. **Creative sandbox:** a creative-mode world to build and make schematics easily and bring them to the overworld.
3. **Off.**
4. **AI rival (the goal, user likes it a lot):** a simulated alliance with a simulated army. **Army design is its own later talk.**

## How it fits the rest (my proposal; confirm)
- **Settlements and renown:** separate systems that share level names. Home settlements keep their own renown; alliance score does not read them. The Reign perks (trades, wages) act on settlements back home. The Bell, Banner and Ward Lantern rules stay as they are in the main world.
- **Dimension joins the "no markers" list:** the design log bans markers in structures, gate dimensions and the End. Add the Alliance dimension; inside it, claims come only from the alliance base. The "cannot claim inside the other's area" rule then applies in the dimension only (the main world is untouched, so no flipping of home settlements).
- **Economy:** emeralds only: switching fee, treasury donations, event payouts, upgrade costs.
- **Feats:** a new chain, **Alliance** (join, first donation, base levels, event wins). Cosmetic and toast only; no power. The main chat decides whether it is a sixth chain or part of the Settler chain.
- **Dragons / End:** no link to Echo mode. Lore check for the main and lore chats: the story says the Unmake or Reign choice comes with the truth at dragon 24; alliances are joined early and the 24 scene is the full lore reveal (already noted in `dragons.md`).
- **Server switches:** `alliances` (master), plus `alliance_wild_pvp`, `alliance_events`, `alliance_switch_cost`, `alliance_switch_cooldown`, `singleplayer_mode`. Off = hall and titles only, no dimension, no perks.

## Build slices (small first, for the main chat)
1. **Slice 1, alliances without the dimension.** Alliance saved per player; join and switch at the title lectern (emeralds + cooldown); the two titles; the four perks as small multipliers; server switch. Needs the Vestige hall's lectern (or a stand-in command `/vestiges alliance join|switch`). Unit-testable: rules and numbers are pure Java.
2. **Slice 2, free-build dimension.** Generated continent with two bays (own seed per world), the hall portal with the colour effect, each side's safe core at fixed size, singleplayer mode "free build" / "off". No score yet.
3. **Slice 3, base score and levels.** Score from blocks, key buildings, donations; the 7 levels; ranks; core growth to 10%.
4. **Slice 4, influence and the wild.** Influence from level, border, warning, protection, rotating terrain renewal, resources and loot back.
5. **Slice 5, events and arenas.** Schedule, join, balance, rewards, influence for the winner.
6. **Later:** creative sandbox mode, AI rival and its army, dungeon portals in the wild.

## Open (smaller; settle at build or balancing)
- Exact numbers: perks, switching fee and cooldown, score weights, thresholds per level, influence radius, border step, renewal rotation.
- Whether very large continents (size of the map) need a performance cap on generation and renewal.
- Dependencies: the Alliance Castle and city templates need Asset Studio's large-structure step; the creative sandbox's schematic export could feed M10 Blueprints.
