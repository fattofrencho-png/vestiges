# Mobs and pets: batch D ideas (design only, no code)

Side chat "idea-mobs". Source: `docs/design-log.md` (Later batches, row D, and Small Packs "Mobs"). Decisions here are agreed with the author one topic at a time; nothing is built until the main chat schedules it.

## Already agreed elsewhere (do not redo)
- Monsters get level, bell-curve size tied to stats, babies inherit size (M3a, built). Animals, villagers, golems, pets and vanilla bosses do **not** scale today (`MobService.scales`).
- No new block types: statues and miniblocks use vanilla blocks with a marker plus display entities.
- Mob bucket: overworld only, passive mobs only, no bosses, elites or villagers; the mob keeps its data.

## Decisions

### Mob bucket (user, 2026-11-03)
- **Earned**, not free from day one. Unlocked by a **Bestiary milestone** for passive mobs in the Codex; the recipe is craftable after that. Exact milestone number: to confirm with the Codex numbers.

### Codex cards (user, 2026-11-03)
- One card per mob, block or item you discover. Extra copies can drop when you re-find things. Cards fill a collection page in the Codex.
- **Look:** a found card is inspected like a vanilla map (opens a view of the card).
- **Two ways to use a card:**
  1. Right-click it in the inventory: a confirmation box ("are you sure?") adds it to your own collection and uses it up.
  2. Keep it as an item, marked with the name of the finder, and give it to another player.
- **Gifting:** the receiver adds it to their collection the same way. The card keeps the mention of the original owner.
- "Trading" means giving to other players, not selling for other goods. The saved question "cards for anything or only other cards" is answered: neither; cards are gifts that fill collections.
- **Gifted cards fill the collection page only** (no Codex XP, no tree points), so alts can't farm rewards. Own discoveries pay as designed.
- Open: duplicate drop rates and anti-farm limits. Can a marked card be re-gifted?

### Riding (user, 2026-11-03)
- **Ride more mobs:** cows, sheep, goats and llama-like animals take a saddle. (Pandas, bears, turtles and pets not chosen for now.)
- **Ride and stay:** a saddled mob with no rider stands still and does not despawn.

### Heads, statues, miniblocks (user, 2026-11-03)
- **Mob heads:** a rare drop from any mob, one head per mob kind, placed like vanilla heads. **Decor only**: no stealth or mob-ignore effect.
- **Statues:** display entities of any mob you hold a **card** for, built with stone; no new block. (Players and villagers as statues not chosen.)
- **Miniblocks:** small display copies of any block (display entity at about 1/4 size); no new block, decor only.

### Animal size, skins, more mobs (user, 2026-11-03)
- **Animal size:** bell curve, rarer mobs trend larger, babies inherit. Sizes change **look, drops, speed and health**. Risks to watch: farms (cap the drop bonus), ridden mobs (speed cap), vanilla breeding.
- **Skin and model variety:** nothing for the MVP; everything later.
- **More mobs:** reskins and variants of vanilla mobs, a few new passive animals, and dungeon/dragon-themed monsters (handed to the Dungeons and Dragons chats). All after the MVP.

## Batch D ordered list (APPROVED by the user, 2026-11-03)
All of batch D is after the MVP. Order = cheapest and most reused first, dependencies after.
1. **Animal size variety:** reuse the M3a size code for passive mobs; look, drops, speed, health, with caps.
2. **Ride more mobs + ride and stay:** saddles on cows, sheep, goats and llama-like animals; empty saddled mobs stand still and don't despawn.
3. **Mob bucket:** unlocked by a passive-mob Bestiary milestone; overworld, passive only; keeps mob data.
4. **Codex cards:** card per discovered entry, extra copies on re-finds, inspect like a map, right-click to add (confirm box) or keep marked with your name; gifting keeps the original owner; collection page in the Codex; gifted cards give no XP.
5. **Mob heads:** rare drop, decor only.
6. **Miniblocks:** small display-entity copies of blocks.
7. **Statues:** from a card plus stone; needs step 4.
8. **Later, art-heavy:** reskins and variants, new passive animals, skin and model variety; dungeon/dragon monsters go to those chats.

Parked outside scope: none yet.
