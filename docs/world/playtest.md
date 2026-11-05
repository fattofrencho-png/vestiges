# World playtest guide

Tests the Vestiges Vanilla world: one sitting, about 30 minutes. Everything here has only been checked by reading terrain values and in short runs, so your eyes are the real test.

## Setup

1. From the `RPG Mod World` folder, run `dev deploy`. This replaces the jar in the modtesting instance.
2. Start the instance and go to Create World.
3. Vestiges tab, World list: turn on **Continent of Trenec**. Then check the World tab: World Type should say **Vestiges Vanilla**. Turn the switch off and on once, and also try picking the type on the button: the two should always agree.
4. Use a new world. Worlds made before the emerald step won't load correctly.
5. Create the world. The time from clicking Create until you are in the world is your load-time number.

## Kit

Paste these in a row:

```
/gamemode creative
/time set noon
/gamerule advance_time false
/weather clear
/effect give @s minecraft:night_vision infinite 0 true
/effect give @s minecraft:speed infinite 3 true
/give @s minecraft:elytra
/give @s minecraft:firework_rocket 64
/give @s minecraft:diamond_pickaxe
```

Set render distance as high as your computer is comfortable with. Vanilla draws about 500 blocks at most.

## 1. Spawn

You should be on gentle ground, not a mountain.

```
/vestiges world where
```

It should say Trenec.

## 2. The big picture

```
/vestiges world preview
```

The chat gives the file path of a map. Open it and check:

- Coasts have no long straight lines
- Rivers are bright blue lines that reach the sea, with no breaks
- A pale ring around spawn: is it still there, and does it bother you?
- Continents have good shapes, with sea between all of them

## 3. Beaches

```
/tp @s 0 120 -18000
```

Fly north until you reach the coast. Walk or fly along it, looking for straight lines.

## 4. Dilecta

```
/locate structure vestiges:dilecta
```

Click the result to teleport. It finds the nearest of up to 9 villages.

- Does it read as one big town?
- Where villages touch, is it a mess?
- Does it sit well near the coast?
- Is the game slow around it? Check F3 for frame rate and server time.

## 5. Rivers

From the map, pick a river near you. Use `/tp @s <x> 150 <z>`, then follow it down to the sea.

- Is there water all the way?
- Are they too thin, too wide, too many or too few?
- Find one inside a mountain range: is the gorge good or ugly?

## 6. Mountains

From the map, find a white or grey range. Use `/tp @s <x> 310 <z>`.

- Are peaks good and tall?
- Are any cut flat on top, around height 300?

## 7. Caves

```
/tp @s 0 ~ 0
/gamemode spectator
/tp @s ~ 20 ~
```

Fly around underground, then go back to creative.

- Are the caves bigger? Are they too open?
- On the surface, are there too many holes?

## 8. Emerald sites

```
/vestiges world sites
```

The chat lists about where each site is. Teleport to one, then dig down from height 120.

- Is the ore rich enough to feel like a prize?
- Dig in a mountain well away from a site: you should find no emerald ore.

## 9. Saved plan

1. Leave and reopen the world.
2. The land should be the same.
3. Check that `<world>/vestiges/world_plan.json` exists in the save folder.

## 10. Past the globe

```
/tp @s 140000 150 0
```

Normal Minecraft land should start past the ocean ring. The edge should fade smoothly, with no wall.

## 11. Plain vanilla is unchanged

1. Create another world with World Type **Default**.
2. `/locate structure vestiges:dilecta` should find nothing.
3. `/locate biome minecraft:windswept_hills`, teleport there and dig: emerald ore should be normal.

## Tell me

For each step: fine, or what looked wrong.
