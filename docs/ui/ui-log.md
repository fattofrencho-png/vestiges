# UI log

Newest decisions go at the top of their section, with the date.

## Decisions

- **2026-11-01:** Inventory 3×3 crafting look approved by the author (https://claude.ai/artifact/DcxU7egj3vvZT822k1HcUC). Item positions in the window frame: grid x 98, 115, 132 and y 7, 24, 41; result 154, 24; table, ender chest and totem at x 98, 115, 132, y 62; Sort stays at 157, 66; Bank at 206, 142 beside the recipe book; no "Crafting" label with a table. Without a table: vanilla 2×2 and arrow.
- **2026-10-31:** First round of fixes goes into main now. The UI chat then stops until the main chat has shipped more first versions of screens. Next jobs when it restarts: the author picks (new overall look, quest screens, HUD and map labels).
- **2026-10-31:** Dark theme dims the world behind full screens, so text stays readable. The author chose this from before and after screenshots (https://claude.ai/artifact/Csb3JMRJP4QTRnySmWcd8m).
- **2026-10-31:** UI work happens in its own chat, in the `RPG Mod UI` folder on the `ui` branch. This chat commits to `ui` only and never pushes. `ui` goes into main only when the author says so.
- **2026-10-31:** Fix clear problems first. A new overall look is decided only after that.

## Shared parts

- `client/UiTheme`: every colour for panels and text, with a dark and a light value. `paintBackground` is the first thing a full screen draws. `noMilestone` removes codes like "(M8)" from text players read.
- `client/ui/TabButton`: tabs and picked rows. The selected one is lit with a gold label and clicking it does nothing. `widthFor` gives the width that fits a label.
- `client/ui/Scrollbar`: a thin bar that shows there's more to scroll to.

## Problems found

From the screenshot tour of 2026-10-31, at normal size (GUI scale 4 at 1080p, 480 x 270), a small window (427 x 240) and GUI scale 2 (960 x 540), in dark and light. `[ ]` is open and `[x]` is fixed on the `ui` branch.

**Tabs**
- [x] Selected tabs look switched off (hub, Settings, trees, Codex, map layers).
- [x] "Character" and "Settlements" are cut off in the hub tabs.

**Text and controls that collide or get lost**
- [x] Waystone screen: Close runs off the screen at normal size, and Share and Close at the small size.
- [x] Waystone screen: a long name pushes the price off its button.
- [x] Codex: the search box is missing at normal size and below.
- [x] Codex card: text runs past the card and off the screen.
- [x] Trees: the "Search nodes..." hint runs out of its box and under Center. The search box is missing at the small size.
- [x] Ascendancy: the cards run off the right edge, their text is cut off, and the fifth card covers the footer. At the small size a few long names are still shortened; hovering the card shows them in full.
- [x] Hub Feats: the intro line runs under "Open the feats tree".
- [x] Hub Settlements: "Renown is counted..." runs off the screen at the small size.
- [x] Full map: the hint line runs under the side panel at the small size.
- [x] Full map: the buttons under the waypoint list could run off a short window.
- [x] Trees: the footer hint is cut off at the small size.

**Messages**
- [x] The map and waystone screens keep showing the last server message forever. It now goes after 10 seconds.

**Colours**
- [x] Light theme doesn't reach the trees, Codex, feats tree, waystone screen, naming screen or features screen.
- [x] Light theme: the hub map tab has no header or footer background, and the "?" marks are hard to read.
- [x] Light theme: the green message on the full map's side panel is hard to read.
- [x] Dark theme: grey text over a bright world is hard to read.

**Wording**
- [x] Players see milestone codes ("arrive in M6", "Comes with dungeons (M8)", "Villagers can sell it (M6)"), and "24 loaded from data" in the hub's Feats tab.

**Other**
- [x] The feats tree can be dragged away from everything. It now stops once its far edge is in view.
- [x] Long lists have no scrollbar (Codex grid, hub Feats and Settings tabs). The bar only shows the position; the mouse wheel does the scrolling.

**Not fixed yet**
- Map labels overlap when markers sit close together ("Gold Block" and "Waystone 1"). This needs a choice about which label gives way.
- A long quest in the Quests tab gets cut off above its buttons, with no scrolling yet.
- The quest book pages are as the main chat built them; only the Quests tab has been polished.
- The inventory panels still use their own colours. The main chat is working on those screens in A2, so they wait.

## Needs a playtest

Things screenshots can't confirm, for the next combined test guide:

- Tabs: click each one in the hub, Settings, trees, Codex and full map. Clicking the selected tab does nothing. Tab and Enter work from the keyboard.
- Full map: pick a waypoint, then Go there, Delete and Share still work.
- Waystone screen: page arrows, Close, Rename and Share work, in a small window too.
- Ascendancy: hovering a card shows its full names, and the Become buttons work once the checklist is done.
- Feats tree: dragging stops at the edges in every direction.
- Scrolling: the Codex and hub Feats scrollbars follow the mouse wheel.
- Map and waystone screens: a server message goes after 10 seconds.
- Light theme: the tree popup's buttons and the welcome page inside Create World.
- Quests tab: the Active, Available, Done and Standing tabs and the quest rows, with a long quest that needs more room than fits (it stops above the buttons and has no scrolling yet).

## For the main chat

- New screens: use `UiTheme` colours, `paintBackground` first, and `TabButton` for tabs.
