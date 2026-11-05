# Vestiges: dungeons test guide (about 20 minutes)

Written 2026-11-05 for the dungeon MVP (branch `claude/dungeons-559493`). The automatic checks have already run: the `dungeons` and `dungeons2` suites walk every room of an Overgrown, a Trial and an End run, and the unit tests cover the rules. This guide is only what a machine can't judge: **feel, difficulty, looks, and a second player**.

Start: `dev deploy`, open the `modtesting` instance (or `dev runClient`), make a new **Creative** world with the Vestiges world type, then paste the kit.

## Kit (paste in a row)

```
/gamemode survival
/vestiges level set @p 25
/vestiges give key overgrown 1
/vestiges give frame
/effect give @p minecraft:speed 1000000 1 true
/give @p minecraft:diamond_sword 1
/give @p minecraft:diamond_chestplate 1
/give @p minecraft:golden_apple 8
/time set day
```

`/vestiges give frame` gives the smallest portal frame: **6 chiseled tuff and 4 copper blocks**. Real play gets them (and the first key) from the **First Key** feat: level 15 and 5 elites.

## 1. The portal (3 min)

1. Build the frame like a Nether portal, standing up: two columns of **3 chiseled tuff** (the sides) and two rows of **2 copper blocks** (top and bottom). Corners are not needed. Bigger frames work too (up to 21 x 21 inside).
2. Hold the key and right-click the frame. You (and anyone within 8 blocks) arrive in the **Overgrown** dungeon. Does the message tell you what to do?
3. Try the frame with a wrong block in it: nothing must happen, and the key stays in your hand.

## 2. Fighting rooms (6 min)

1. The start room is empty. Behind you on the west wall is a small frame: stepping into it leaves for good.
2. Walk east to the first room. The moment you step in: **the doors close** and **6 to 10 monsters** appear. Fight them.
3. Kill the last one: the doors open and a **cache chest** appears in the back corner. Open it: a stack of theme blocks. (A second player gets their own copy.)
4. Tier 1 should feel like level 15 monsters. With level 25 gear: **too easy, fair, or too hard?** Tell me.
5. Walk through all 13 rooms: 6 fighting rooms, the **Lieutenant Mossbound** (big title when you enter), 3 more, the **Boss The Rotwood Witch**, then the reward room. Do the room shapes (open, pillars, pit, split level) feel different? Is any shape annoying?
6. Try to break a wall or the floor: you can't. Place a block and break it: you can.

## 3. Rewards and the next key (3 min)

1. The Lieutenant's chest has a gear piece. The reward room has a chest with 2 gear pieces, emeralds, sometimes a book, and **the next key**. Look at its name: tier and theme can change (mostly the same).
2. Step into the frame on the **east wall** of the reward room to go home. Build the portal again and use the new key.

## 4. Lives (2 min)

1. In a run, let a monster kill you. You should lose one life, appear back in the **start room with your things**, and see how many lives are left. No gravestone.
2. Die three times: you are sent home with your things.

## 5. Trial and End (4 min)

1. `/vestiges give key trial 1`, use it: copper and tuff rooms, breezes and bogged, **Gale** and **The Vault Keeper**.
2. The End is closed until dragon 1. For now: `/vestiges feat grant @p dragonslayer`, then `/vestiges give key end 3`. Rooms have **void gaps with 3-wide bridges**. Fall in: you lose a life and land in the start room. **The Shellguard** is three shulkers, **The Void Stalker** an enderman. The first Boss clear at tier 3 gives you **elytra** (once).
3. Switch test: `/vestiges config system dungeons false`: a frame with a key does nothing. Switch it back with `true`.

## Questions to answer

- Is a run about 10 minutes? Too long, too short?
- Are the bosses just "big bags of health"? (They are meant to be, until the moves come.)
- Boss damage is capped at the Warlord's x5 (a draft). Does the Boss hurt too much or too little at tier 1?
- Do you want the cache chests somewhere else?

## Not tested by machines

A **second player** (party of 2 with +50% monster health, a second copy of each chest), real `.nbt` rooms from you (the loader exists and passed a hand-made test file; the convention is in `docs/ideas/dungeons.md`, section 7), restarting the game in the middle of a run, and how it feels at tier 4 and up.
