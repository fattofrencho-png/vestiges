# World room

World generation has its own chat, in the `RPG Mod World` folder on the `world` branch. It builds the optional Vestiges world type: Trenec, the ocean around it, the far lands and climate by direction.

## Rules

- This chat commits to `world` only when the author asks, and never pushes. `world` goes into main only when the author says so, and the main chat handles all pushes.
- Decisions and the list for the main chat live in `world-log.md`.
- Place names come from `docs/lore/`. This room never invents lore. New names and lore questions go to the lore chat through the author.
- Terrain files are generated: edit `tools/build_worldgen.py` and run `python tools/build_worldgen.py`. Never hand-edit `data/vestiges/worldgen/` or `data/vestiges/vestiges/world/`.
- When stopping a game, only stop Java processes from this folder (`*RPG Mod World*`). Never run `gradlew --stop`.

## Checking the terrain

Terrain can be checked without starting the game. These read real terrain values and generate no chunks:

```bash
# Maps of seeds 1-3 and a report for each seed, written to build/world
VESTIGES_WORLD=trenec VESTIGES_WORLD_SEEDS=50 ./gradlew cleanTest test --tests 'dev.vestiges.WorldgenTest'
```

In a game, as an operator: `/vestiges world where`, `/vestiges world preview [seed]`, `/vestiges world check [seeds]`.
