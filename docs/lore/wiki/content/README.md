# Wiki content format

Run `python build_wiki.py` in the folder above. It makes `vestiges-wiki.html` (ships with the mod) and `vestiges-wiki-author.html` (the author's copy).
Never edit those two files by hand. Fragments, mobs and structures are read straight from `../fragments.md`, `../mob-origins.md` and `../codex-flavour.md`.

| Line | Meaning |
|---|---|
| `@page id \| Title \| Group` | Starts a page. Group is History, World, Reference or Game guide. A fourth part `author` keeps the page out of the shipped file. |
| `@entry id \| Title \| lock` | Starts an entry, with an optional lock. It ends at the next `@entry`, `##` or `@page`. |
| `@lock dragon 6` ... `@endlock` | A locked part of a page. Locks can't be nested. Also `found diary-keep-1`, and `dragon 12 or found diary-keep-1`. |
| `@author` ... `@endauthor` | A note only the author's copy shows. |
| `@bible text` | An exact quote from the bible. The build stops if it isn't exact. |
| `@gap text` | "Not written yet". The detail shows only in the author's copy and joins its Gaps page. |
| `@name Name \| Kind \| One line \| target id \| lock` | A row in the Names index. |
| `[[id\|label]]` | A link. The build stops if the id doesn't exist. |

Also: `## heading`, `- list`, `1. list`, `| tables |`, `**bold**`, `*italic*`.

Use neutral ids for locked entries (`ch12`, `leg2`), because ids stay readable in the shipped file.

## Progress

The game writes `progress.js` next to the shipped file, before opening it:

```js
window.VESTIGES_PROGRESS = { dragons: 12, found: ["diary-keep-1", "shrine-primacy-3"], player: "Steve" };
```

`fragment-ids.json` lists every fragment id. With no progress file the wiki shows only what is open to everyone.
