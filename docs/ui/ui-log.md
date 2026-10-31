# UI log

Newest decisions go at the top of their section, with the date.

## Decisions

- **2026-10-31:** UI work happens in its own chat, in the `RPG Mod UI` folder on the `ui` branch. This chat commits to `ui` only and never pushes. `ui` goes into main only when the author says so.
- **2026-10-31:** Fix clear problems first. A new overall look is decided only after that.

## Problems found

From the screenshot tour of 2026-10-31, at normal size (GUI scale 4 at 1080p, 480 x 270), a small window (427 x 240) and GUI scale 2 (960 x 540), in dark and light. `[ ]` is open and `[x]` is fixed on the `ui` branch.

**Tabs**
- [ ] Selected tabs look switched off (hub, Settings, trees, Codex, map layers).
- [ ] "Character" and "Settlements" are cut off in the hub tabs. Every tab has the same width, whatever its label.

**Text and controls that collide or get lost**
- [ ] Waystone screen: Close runs off the screen at normal size, and Share and Close at the small size.
- [ ] Waystone screen: a long name pushes the price off its button.
- [ ] Codex: the search box is missing at normal size and below, because the tabs leave it no room.
- [ ] Codex card: text runs past the card and off the screen ("Uncommon · Discovered", "+4% damage, +1% drop chance").
- [ ] Trees: the "Search nodes..." hint runs out of its box and under Center. The search box is missing at the small size.
- [ ] Ascendancy: the cards run off the right edge, their text is cut off ("Splinter Bol"), and the fifth card covers the footer.
- [ ] Hub Feats: the intro line runs under "Open the feats tree".
- [ ] Hub Settlements: "Renown is counted..." runs off the screen at the small size.
- [ ] Full map: the hint line runs under the side panel at the small size.
- [ ] Trees: the footer hint is cut off at the small size ("V: utility po").

**Messages**
- [ ] The map and waystone screens keep showing the last server message forever, however old it is.

**Colours**
- [ ] Light theme doesn't reach the trees, Codex, feats tree, waystone screen, naming screen or features screen.
- [ ] Light theme: the hub map tab has no header or footer background, and the "?" marks are hard to read.
- [ ] Light theme: the green message on the full map's side panel is hard to read.
- [ ] Dark theme: grey text over a bright world is hard to read (hub, trees, Codex, naming screen). This needs a choice, so it gets a before and after for the author.

**Wording**
- [ ] Players see milestone codes ("arrive in M6", "Comes with dungeons (M8)", "Villagers can sell it (M6)"), and "24 loaded from data" in the hub's Feats tab.

**Noted, not fixed yet**
- Map labels overlap when markers sit close together ("Gold Block" and "Waystone 1").
- The feats tree can be dragged away from everything, with no limit.
- Long lists have no scrollbar (Codex grid, hub Feats tab).

## Needs a playtest

Things screenshots can't confirm, for the next combined test guide:

- (nothing yet)

## For the main chat

- (nothing yet)
