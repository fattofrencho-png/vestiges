# Quest content: drafts and decisions

Quest side chat (started 2026-11-03). Scope: review and improve the draft quest list, templates (guide, story, side), reputation effects and cosmetics rewards.
Rules: small batches; quest JSON texts are edited only after the user approves a batch; `docs/design-log.md` and `docs/lore/` are never edited here.
Source of the rules: design-log "Optional quests". Quest files: `src/main/resources/data/vestiges/vestiges/quests/`.

## State of the draft list (read 2026-11-03)

40 files: 22 guide, 13 side, 1 story (a test), 1 note (a test), plus the real chains below.

**Guide chains (22):** `first_steps` (7: A Little Stronger, Your First Point, The Other Wheel, Read the Colours, Paid in Green, Fit to Wear, Choose Your Path) ·
`pack_and_path` (6: A Place for Tools, Two Quick Slots, The Belt, Safe Keeping, Mark the Spot, Go There) ·
`field_notes` (7: Something New, Filling the Pages, Getting to Know It, Know Your Enemy, Easy Harvest, A Proper Meal, New Life) ·
`putting_down_roots` (5: Founder, Ring the Bell, Worth a Name, The Long Way Round, Keep Them Out).
Guide rewards: XP 25-300, 0-8 emeralds, 5 titles ("the Seasoned", "the Well-Packed", "the Curious", "the Homesteader"), a few items. No intro text, one line per step.

**Side quests (13, one per job):** armorer, butcher, cartographer, cleric, farmer, fisherman, fletcher, leatherworker, librarian, mason, shepherd, toolsmith, weaponsmith.
All identical in shape: 2-3 steps, fixed amounts, XP share 0.4 + 6 emeralds, one intro line, no reputation, no cosmetic.

## Things I noticed (side quests)

1. **Amounts don't scale** with level (design says they should). Level 5 and level 60 get the same "bring 8 iron ingots".
2. **Every reward is identical** (6 emeralds, 0.4 share); no quest differs by effort. Reputation (village, faith) is never used.
3. **Story and step order don't always match:** Farmer ("something walks the rows at night") brings crops first, then kills; Fisherman ("the one that got away") has you catch 10 fish, then bring a pufferfish or salmon, which isn't a story about a lost line.
4. **Weaponsmith** ends by bringing 1 emerald to the weaponsmith, which reads backwards (they pay emeralds, you don't).
5. **Only one line of text per quest** (intro). No thanks line after finishing, no comeback twist text, though comebacks (7 days, up to 5 times) and `eliteFrom` exist.
6. Several quests pay for easy vanilla chores (bring 8 wool). Nothing is tied to the Vestiges mod's own systems (elites, Codex, waystones) except armorer and weaponsmith.

## Batches

| # | What | Status |
|---|---|---|
| 1 | The MVP quests are all teaching quests, one per MVP feature | **APPROVED by the user 2026-11-04: all 54 quests OK.** 26 existing files edited (uncommitted, in the `idea-quests-6c2495` worktree). 16 new drafts wait for their step types |
| 2 | Side quests (13 jobs): scaling, village reputation, thanks lines, cosmetic on 5th comeback | later |

## Decisions (user, 2026-11-03)

- Batch 1 = enough guide quests to cover everything in the MVP and explain it. About 7 quests per batch.
- Side quests get: amounts that scale to level, village reputation, a small cosmetic on the 5th comeback, a thanks line after finishing.
- Batch 1 (user, 2026-11-03, rethink): all MVP quests are teaching quests, one per MVP feature. Scope = Core RPG features; Comfort features (Sort, hotbar sets, shulker boxes, bigger stacks) are not taught. Table slot and 3x3 stay because inventory is always on (my reading, to confirm). Quests in 7 chains, free order. Side quests stay only as the Odd Jobs lesson.
- Secret quests unlocked by reputation: wanted, but **not for the MVP** (parked in the ideas backlog).

## Batch 1 (rethought, user 2026-11-03): the MVP quests are all teaching quests

**Rule:** every quest in the MVP exists to teach one MVP feature. Each feature below has at least one guide quest; a quest names the key or screen, explains it in a short intro, and ends when the player has really used it. Rewards stay XP, a few emeralds and titles. The first draft (7 quests) is replaced by this coverage list.

**Source of the feature list:** `VestigesSystem` (Core preset, plus the built Comfort features), and the MVP scope in the design log.

| Feature (MVP) | Taught by | Status |
|---|---|---|
| Hub (H), Quests tab and tracker | **new: Your Journal** (press H, open Quests, track a quest) | new step `hub_open`, `track_quest` |
| Levels and XP | A Little Stronger | exists |
| Passive trees (Fight, Craft) | Your First Point, The Other Wheel | exists |
| Ascendancy | Choose Your Path | exists |
| Gear rarity | Read the Colours, Fit to Wear | exists |
| Trim stats (4 matching trims) | **new: A Matching Set** | new step `trim_set` |
| Enchant levels and emerald cost | Paid in Green | exists |
| Mob levels | **new: Deeper, Harder** (kill a mob much above your level and read its level tag) | new step `kill_high_level` |
| Elites | **new: Champions** (kill an Elite; tells you about powers and drops) | `kill elite` exists |
| Bestiary | Know Your Enemy | exists |
| Banded loot and per-player chests | **new: Yours Alone** (open a natural chest; your loot is yours, a friend gets their own) | new step `loot_chest` |
| Codex | Something New, Filling the Pages, Getting to Know It | exists |
| Feats | Founder, The Long Way Round | exists; maybe **new: Feat Hunter** (open Feats tab, pick one to follow) |
| Settlements, renown, wards | Ring the Bell, Worth a Name, Keep Them Out | exists |
| Waystones | The Long Way Round | exists |
| Map, waypoints, Go there, minimap | The Belt, Mark the Spot, Go There | exists |
| Racks, quick slots, tool belt, Bank | A Place for Tools, Two Quick Slots, The Belt, Safe Keeping | exists |
| Crafting table slot and 3x3 grid | **new: A Table in Your Pack** | new step `table_slot`, `grid_craft` |
| Sort, Move all, hotbar sets (G) | Comfort, **not taught in the MVP** (user) | dropped, parked |
| Shulker boxes | Comfort, **not taught in the MVP** (user) | dropped, parked |
| Bigger stacks | Comfort, **not taught in the MVP** (user) | dropped |
| Farming help, meal buffs, breeding | Easy Harvest, A Proper Meal, New Life | exists |
| Villagers: names, About page | **new: A Name and a Story** | new step `villager_meet`, `villager_about` |
| Villagers: trust, recruiting | **new: Room at the Hearth** | new step `villager_trust`, `villager_recruit` |
| Trade system | **new: Fair Trade** (make one trade on the new screen) | new step `trade` |
| Villager requests (the side quests themselves) | **new: Odd Jobs** (accept and finish one villager request) | needs M6 request flow |
| Dungeons: key, portal, boss, reward | **new: Through the Gate** | new steps `dungeon_*` |
| Dungeons: the Gate tree | **new: Another Wheel** (take a node on the Gate tree) | new step `gate_points` |
| Dragons: summon, fight, reward | **new: What the Vault Left** (Tulva, the first dragon) | new steps `dragon_*` |

**Count (corrected):** 25 existing guide quests, 13 side quests, 14 new drafts = 52 quests on the review page. Suggested chains (play order): `first_steps` (add Your Journal, Champions, Deeper Harder) · `pack_and_path` (add A Table in Your Pack) · `field_notes` (add Yours Alone) · `putting_down_roots` (add Feat Hunter, A Matching Set) · `good_company` (4: villagers, trust, trade, Odd Jobs) · `below_the_world` (2 dungeon) · `the_first_dragon` (1).

**Order of work:** quests for built features (Your Journal, Champions, Deeper Harder, Yours Alone, Feat Hunter, A Matching Set, A Table in Your Pack) can be written first; villager, dungeon and dragon quests stay drafts until M6, dungeons and dragons exist.

**Side quests (13 jobs):** stay in the MVP only as the "Odd Jobs" lesson; the full rework (scaling, reputation, thanks lines, cosmetic) comes after the MVP.

## Intro text rule (user, 2026-11-03)

Every quest needs an intro (flavour text that explains its purpose). The 13 side quests and the 14 drafts already had one; the 25 existing guide quests had none, so intros were drafted for all 25 (shown on the review page with a "new intro text" tag). They are written to the JSON only after the user approves them.

## Review round 1 (user, 2026-11-03): 44 of 52 OK, 8 changes

**Applied to the quest files in this worktree (JSON, 26 files):** an `intro` on all 25 guide quests, plus the changes below. No Java touched.
- **Fit to Wear:** intro and step now say gear above your level halves armor, damage and mining speed (hit softer, dig slower). The user wrote "makes you slower"; the design log says half armor, damage or mining speed, so I used that. To confirm if there is a real slowdown.
- **Something New:** intro explains the Codex; step says open it with H, then the Codex tab.
- **Worth a Name:** intro and step suggest building up your base.
- **Keep Them Out:** a settlement already stops spawns (Bells and Ward Lanterns are spawn-proof); the lantern pacifies a place away from home (r6 / r12 / r30). Intro and step reworded.
- **Tested Metal (side_armorer):** yes, it copied Toolsmith (bring iron ingots) and Weaponsmith (kill an elite). Now: wear 4 armor pieces, kill 8 monsters in them.
- **Drafts reordered:** Your Journal is the first quest of First Steps; A Table in Your Pack moved into First Steps right after A Little Stronger. Chain order numbers get set when their JSON is written.

**Rule for the main chat (needs code), from "Blank Space" (user):** a quest is completed by going back to the quest giver. When all steps are done, the tracker turns into a "Turn in" button that works once you are near the giver. Bring steps and the cartographer's last "talk" step (sneak + right-click) then go away. Until built, the cartographer quest stays as it is.

## Review round 2 (2026-11-04): checked against the other chats' approved notes

The user asked to review the whole project and the latest chat updates for "Fit to Wear". Read: `docs/ideas/` (progression, villagers, dungeons, dragons, mobs, social, view-map) and the sibling hand-overs. Changes:
- **Fit to Wear (written to the JSON):** the progression chat decided gear above your level keeps full stats but **slows you** (it replaces the built half-power rule; schedule with balancing). Intro and step now say "slows you". If the main chat doesn't change the rule before the MVP, this text is wrong until it does.
- **Villagers (MVP = names, library, new screen with sell-only trades by player level, Quests tab):** trust and recruiting are after the MVP. So *Room at the Hearth* moves to "After the MVP (on hold)"; *A Name and a Story* (library, About tab), *Fair Trade* (sell-only, offers by your level) and *Odd Jobs* (accept in the Quests tab) are rewritten to match.
- **Dungeons (MVP = core run, key, rewards; Overgrown and Trial; Gate tree after the MVP):** *Through the Gate* now follows the First Key feat (level 15 + 5 elites), portal built by hand, 13 rooms, 3 lives, boss, reward room. *Another Wheel* (Gate tree) moves to "After the MVP".
- **Dragons (MVP = dragons 1 to 6; dragon 1 is already in the End, no gate; dragons 2 to 6 summoned with 4 end crystals, gate level 4 x number):** *What the Vault Left* is now "enter the End, defeat the dragon" (the Dragonslayer title comes from its feat). New draft *Call the Second* teaches summoning (level 8, 4 crystals).
- **Social (grave MVP):** new draft *Back for Your Things* teaches the gravestone.
- **Progression:** a welcome message plus a first guide quest replaces the starter kit, so Your Journal is the quest given at first join (main chat). The 5-act campaign is one guide-quest chain, after the MVP: parked below.
- **Mobs, view-map:** nothing in the MVP touches the quests.

New step types for the main chat now also include `grave_recover`, `end_enter`, `quest_accept`.

## Review page

Private artifact https://claude.ai/artifact/PGrTgdEMQCKak9KRNCFj7R lists all 52 quests (generated from the JSON). The user marks each OK / Change / Drop with a note; choices save in the artifact database (`reviews/<quest id>`) and the quest chat reads them with ArtifactData. Rebuild after any JSON change.

## Parked ideas

- Secret quests unlocked by reputation thresholds (backlog, not MVP).
- **5-act campaign as one guide-quest chain** (progression chat, after the MVP): the act list is still to draft with the user.
- Comfort-feature teaching quests (Sort, hotbar sets, shulker boxes, bigger stacks): after the MVP.

## Hand-over for the main chat (2026-11-04)

**Approved (user, 2026-11-04): all 54 quests on the review page.**
1. **Ready to merge (JSON only, 26 files in `src/main/resources/data/vestiges/vestiges/quests/`):** an `intro` on the 25 guide quests, plus text changes to Fit to Wear, Something New, Worth a Name, Keep Them Out and the rewritten side quest Tested Metal (wear 4 armor pieces, kill 8 monsters). All step types already exist; every file parses.
2. **Drafts, write the JSON when the step exists** (texts are in this file's tables and on the review page, `docs/ideas/quests.md` has the wording): Your Journal, A Table in Your Pack, Deeper Harder, Champions, Yours Alone, A Matching Set, Feat Hunter, Back for Your Things, A Name and a Story, Fair Trade, Odd Jobs, Through the Gate, What the Vault Left, Call the Second. On hold until after the MVP: Room at the Hearth (trust, recruiting) and Another Wheel (Gate tree).
3. **New step types needed:** `hub_open`, `track_quest`, `kill_high_level`, `table_slot`, `grid_craft`, `loot_chest`, `trim_set`, `feats_open`, `feat_follow`, `grave_recover`, `villager_meet`, `villager_about`, `trade`, `quest_accept`, `dungeon_enter`, `dungeon_boss`, `dungeon_reward`, `end_enter`, `dragon_summon`, `dragon_kill`.
4. **Rules that need code:** quests complete by going back to the giver (tracker becomes a "Turn in" button near them; the cartographer's `talk` step and bring hand-in then go away) · Your Journal is the first quest, offered at first join with a welcome message (progression chat) · gear above your level slows you (progression chat; Fit to Wear text assumes it).
5. **After the MVP:** side-quest rework (level scaling, village reputation, thanks lines, 5th-comeback cosmetic), secret quests by reputation, the 5-act campaign chain, teaching quests for Comfort features.
