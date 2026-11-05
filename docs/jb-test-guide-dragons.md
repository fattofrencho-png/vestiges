# Vestiges: dragons test guide (about 30 minutes)

Written 2026-11-05 for the first dragons (branch `claude/dragons-dbafd7`). The automatic checks have already run: the `dragons`, `dragons2` and `dragons3` suites fight dragon 1, kill it, try the gate, summon dragon 2, lose a fight, and run every move of dragons 2 to 6 in a real world (all numbers in the machine checks come from the data files). This guide holds only what a machine cannot judge: how it **feels**, how it **looks and sounds**, how hard it is, and a second player.

All numbers are drafts for the balance pass. Say what feels too easy, too hard or unfair, and I change the data files, not the code.

Start: `dev deploy`, open the `modtesting` instance (or `dev runClient`), make a new **Creative** world with the Vestiges world type, then paste the kit.

## Kit (paste in a row)

```
/gamemode survival
/vestiges level set @p 30
/give @p minecraft:diamond_sword 1
/give @p minecraft:bow 1
/give @p minecraft:arrow 64
/give @p minecraft:diamond_chestplate 1
/give @p minecraft:golden_apple 16
/give @p minecraft:end_crystal 16
/give @p minecraft:obsidian 16
/effect give @p minecraft:speed 1000000 1 true
/effect give @p minecraft:slow_falling 1000000 0 true
/time set day
```

Getting into the End quickly (the world has a Stronghold; or in creative): `/execute in minecraft:the_end run tp @p 0 80 0`. Handy any time: `/vestiges dragon info` (kill count, next dragon, gate level) and `/vestiges dragon count <0-6>` (jump to a dragon: count 2 means Morst is next).

## 1. Dragon 1, Tulva, the First (5 min)

1. Go to the End. The boss bar must read **Tulva, the First · Lv 4**. It is the vanilla dragon with vanilla health (200). Is the fight still the vanilla one, not harder in a surprising way?
2. Break the pillar crystals with your bow and fight it. **One hit can never take more than 8% of its health**, so even TNT or a bed cannot kill it in one go. Does the cap feel fair or does it make hits feel pointless?
3. Stand far out in the void (bridge 100+ blocks away or fall under the island): your arrows must not hurt it.
4. Kill it. Check the rewards (everyone who hit it and is within 150 blocks): **XP**, **32 emeralds**, **one Level VI enchanted book**, a toast for the **Dragonslayer feat**, a **new Codex entry** (open H, Codex, tab **Lore**: the chapter shows placeholder text for now), and **ender chest page 2** (open the ender chest: the page list on the right). Does the reward moment feel like a reward (sound, message)?
5. The 10 pillars and their crystals come back over about a minute. Does it look good while it happens?

## 2. The gate and summoning (5 min)

1. Put 4 end crystals on the 4 bedrock blocks around the exit portal (the vanilla way). First try at a low level: `/vestiges level set @p 5`. The 4th crystal must **refuse** with a message naming the level needed, and nothing is used up.
2. `/vestiges level set @p 8` and try again: the crystals go down and the vanilla summoning starts. **Belaz, the Ember · Lv 8** arrives (280 health alone).
3. Does the message wording make sense? Is the level number (4 x the dragon's number) clear?

## 3. The moves (10 min; jump with `/vestiges dragon count N`, each jump needs the crystals again, or just fight on)

- **Belaz (dragon 2), fire ring:** when it perches, a ring of fire burns around it for about 7 seconds. Can you see the ring (flames) and stay out? Is the burn fair?
- **Morst (dragon 3), wing shove:** a growl, one second later everyone near it is thrown sideways. Is the warning long enough? Does it throw you into the void too often?
- **Dhars (dragon 4), regrowing crystals:** a minute after you break a crystal, the last 3 you broke grow back (sparks first). At most twice. Do the sparks warn you in time? Is the minute right?
- **Ilvig (dragon 5), the swarm:** 2 phantoms every time it perches and every 30 seconds it sits, 5 alive at most. Too many? Too few?
- **Durhos (dragon 6), the shade:** in flight it flickers out of sight but a faint smoke marks it. **How does the invisible dragon look in 26.3?** (I could not judge this on screen.) When it perches, everyone near gets Darkness. Too dark?

## 4. A lost fight (3 min)

Fight any dragon and let yourself die (`/kill @p`, or let it kill you). If everyone dies, the dragon heals and leaves after about 10 seconds; the kill count does not change and the 4 crystals are gone. Walk away from a fight and stay away for 2 minutes: when you come back its health is full again.

## 5. Ender chest pages (3 min)

Open the ender chest (slot under the crafting grid, or a block). Click page 2 in the list: it opens. Put something in it, close, die, respawn: still there? Scroll the list (mouse wheel over it) to see all 25 pages; locked pages say which dragon opens them. Rename a page.

## 6. A second player (5 min, if you can)

On a LAN or server: both fight the same dragon. The dragon gets +50% health for the second player (dragon 2: 280 becomes 420). Both get the rewards if both hit it. Does the party scaling feel right?

## What to tell me

Too easy or too hard (which dragon, which move)? Anything that looks wrong or confusing? Does the End dungeon theme now open (use `/vestiges give key end 3` after the Dragonslayer feat)? Anything that broke?
