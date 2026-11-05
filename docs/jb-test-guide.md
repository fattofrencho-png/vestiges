# Vestiges: your test guide (about 30 minutes)

Written 2026-11-04. Everything a machine can check has already passed: **1,139 automatic checks, 0 failures** (unit tests, 12 suites, shulker checks added today), the screenshots were read, the camera shots look right. This page is only what needs you: feel, look, controls.

Start: `dev deploy`, then open the `modtesting` instance (or `dev runClient`), make a new **Creative** world, then paste the kit.

## Kit (paste in a row)

```
/gamemode survival
/vestiges level set @p 25
/give @p minecraft:red_shulker_box 1
/give @p minecraft:crafting_table 1
/give @p minecraft:diamond 20
/give @p minecraft:emerald 64
/give @p minecraft:oak_log 64
/give @p minecraft:diamond_pickaxe 1
/give @p minecraft:diamond_sword 1
/effect give @p minecraft:speed 1000000 2 true
/time set day
```

## 1. Inventory, shulker box and Bank (8 min)

Press **E**.
1. A 3x3 crafting grid is next to the table slot. Craft something with logs (planks, sticks, a chest).
2. Take the crafting table out of its slot: the extra 5 grid slots disappear and their items come back to you. Put it back.
3. The Bank is at the bottom of the right panel, next to the recipe book button. Put emeralds in, take them out.
4. **Right-click the red shulker box** in your inventory: it opens. Put the diamonds in. Does the screen show the box's red tint? Rename it (type a name): is the name shown on the item afterwards?
5. While the box is open, try to move the box itself with the mouse: it must not move.
6. Close it, open it again: the diamonds are still inside. Place two boxes in one slot: a stack of 2 must not open.
7. Mouse wheel, Ctrl + wheel in a rack, drag and drop: does it feel right?

## 2. Camera (6 min)

```
/vcam here 20
```

Walk into the square it marks. The view should swing to the isometric dungeon view smoothly. Check:
- **W A S D** move you in the screen's directions (W goes up the screen, a diagonal in the world).
- You can still fight and look around the way you expect.
- Walking out of the square swings back to the normal first-person view, no jump.
- `/vcam toggle` forces it everywhere, `/vcam clear` removes the square.

## 3. Combat and mobs (6 min)

```
/summon minecraft:zombie ~ ~ ~3
/vestiges mob rank elite 25
/vestiges mob info
```

Fight it. Are the bars, glow and damage numbers readable? Is the fight fun, not unfair? Repeat with `/summon minecraft:skeleton` and `/vestiges mob rank warlord 25`.

## 4. Loot (3 min)

```
/setblock ~ ~ ~2 minecraft:chest
/vestiges loot chest 25
```

Look at the chest, run the second command, open it: gear and books with rarity names and colours. Do the tooltips read well?

## 5. Map and performance (4 min)

Press **M** and open the minimap. Fly (`/gamemode creative`, double-tap space) fast over new land for a minute: does the map keep up, and is the frame rate steady?

## 6. Sound and small things (3 min)

Open and close a chest, craft, use a totem (`/give @p minecraft:totem_of_undying`, then put it in the totem slot and `/damage @p 100`): do the sounds and effects play?

## Skip

Second-player checks, the Mending rework and balance numbers are not in this pass (they go into the combined MVP test).

## Send back

Anything that felt off, as one line each, for example "shulker tint missing" or "W goes the wrong way". If everything felt fine, say "guide passed" and I tick A2 plain.
