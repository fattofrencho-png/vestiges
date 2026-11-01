# Lore room

This folder is where the Vestiges lore is discussed and decided, in its own Claude chat. The main chat works on the mod's code and reads this folder when it needs lore.

## Rules for the lore chat

- **Lore only.** Talk about story, people, places, faiths, names and written text. Game rules and balance belong in the main chat.
- **Edit only `docs/lore/`.** Never change code, data files, `docs/design-log.md` or `CLAUDE.md`. Never commit or push.
- **Never change the source projects.** The author's own bible is `E:\Program Files\Antigravity\Life AI Projects\Hobby\Minecraft DataPacks\RPG_Lore_Mod\bible\lore_source.md`. Read it and quote it, but never paraphrase it or edit it.
- **I draft, the author approves.** New lore only counts once the author says so. Keep drafts apart from decisions.
- **Writing style for talking to the author:** simple words, short messages, one topic at a time, numbered questions.

## Files

| File | What it holds |
|---|---|
| `lore-log.md` | Every lore decision. Newest entries go in the "Decisions since Batch 5" section, with the date. |
| `fragments.md` | The 112 approved shrine and dungeon texts. |
| `codex-flavour.md` | The 414 approved Codex entries. `tools/build_codex.py` reads this file, so a changed line reaches the game only when the main chat rebuilds. |
| `mob-origins.md` | The three levels of mob lore (Fluff, Flavour, Lore), the group decisions and the open points. |
| `wiki/` | The offline wiki. Edit `wiki/content/` and run `python wiki/build_wiki.py`; the format is in `wiki/content/README.md`. Never edit its two `.html` files by hand. |

Changing an approved text is allowed when the author asks. Note the change in `lore-log.md` so the main chat can pick it up.

## Writing to the main chat

Add a line to "For the main chat" in `lore-log.md` when a decision needs work in the mod, such as a new name, a changed text, or an idea that needs a game feature. Lore that needs a new game feature is a question for the main chat, not a promise to the player.
