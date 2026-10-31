# UI room

This folder is where the Vestiges UI is improved, in its own Claude chat. The main chat builds features and ships simple first versions of their screens; this chat polishes them afterwards.

## Rules for the UI chat

- **UI only.** Screens, HUD, look and feel, wording on screen, and the gametest tour. Game rules, balance, data and lore belong to other chats. Lore text stays as approved.
- **Own copy, own branch.** Work in the `RPG Mod UI` folder on the `ui` branch. Commit to `ui` only and never push. `ui` goes into main only when the author says so, and the main chat handles all pushes.
- **New data.** A small addition to `HubNet` is allowed if it changes no game rule. Anything bigger goes on the list for the main chat.
- **Behaviour stays the same.** Polish adds no feature, and everything still respects its `VestigesSystem` switch.
- **Drafts as renders.** If a fix means choosing how something should look, show the author a render first. Clear fixes need no asking.
- **Testing.** Check work with `dev build`, `dev test` and the screenshot tour. Keep a list of what only a playtest can confirm for the next combined test guide.
- **Writing style for talking to the author:** simple words, short messages, one topic at a time.

## Files

| File | What it holds |
|---|---|
| `ui-log.md` | UI decisions, the list of known problems, what still needs a playtest, and the "For the main chat" list. |

## Writing to the main chat

Add a line to "For the main chat" in `ui-log.md` when UI work needs something from the main chat, and send it a message if it can't wait. Tell the main chat before a change to a screen it is working on goes into main.
