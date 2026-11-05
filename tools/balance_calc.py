#!/usr/bin/env python3
"""balance_calc.py - read-only balance maths for docs/balance-sheet.md. It changes no game number and starts no game.

    python tools/balance_calc.py                 all calculation tables, as markdown
    python tools/balance_calc.py --registry      the parameter table (value, source file:line), as markdown
    python tools/balance_calc.py --check         checks that every number mirrored below still sits in its source file (exit 1 on drift)

The formulas are copies of the Java code, written next to the file they come from (Java Math.round is floor(x + 0.5)). Loot numbers come
from tools/build_loot.py itself (imported, so the generated tables and this sheet cannot disagree). Dragon numbers are read from the dragons
branch with `git show` (it is not merged yet); when that branch is missing the dragon rows are skipped.

Anything marked ASSUMPTION is a pace or a vanilla value that is not in the repository; the play test must confirm it.
"""

import glob
import json
import math
import re
import statistics
import subprocess
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
sys.path.insert(0, str(ROOT / "tools"))
import build_loot as BL  # noqa: E402  (the loot generator is the source of truth for chest and drop numbers)

DRAGON_REF = "claude/dragons-dbafd7"
J = "src/main/java/dev/vestiges/"
RES = "src/main/resources/data/vestiges/"


def jround(x):
    """Java Math.round for the values used here."""
    return int(math.floor(x + 0.5))


# --------------------------------------------------------------------------------------------------------------------------
# The game's own formulas (mirrors; --check proves the constants still sit in the Java files)
# --------------------------------------------------------------------------------------------------------------------------
MAX_LEVEL = 100
SHARE, BONUS, CAP = 0.75, {"surface": 0, "caves": 3, "deepslate": 6}, {"surface": 20, "caves": 40, "deepslate": 50}  # ServerConfig.Numbers
OVER_CAP = {0: 0, 1: 3, 2: 6, 3: 10}  # MobLevels.overCap
STRENGTH = {"normal": 1, "champion": 2, "elite": 3, "warlord": 5, "lieutenant": 6, "boss": 10}  # MobData.strength
DAMAGE_STRENGTH_CAP = 5  # MobData.damageStrength


def kill_xp(m):  # LevelCurve.killXp
    return 10 + 4 * max(0, m)


def need(level):  # LevelCurve.need
    if level >= MAX_LEVEL:
        return 40_000
    lv = max(1, level)
    return jround(kill_xp(lv) * (8 + 0.9 * lv))


def gap(p, m):  # LevelCurve.gapMultiplier
    below = p - m
    if below <= 5:
        return 1.0 + min(0.5, max(0, m - p) * 0.05)
    return max(0.1, 1.0 - (below - 5) * 0.1)


def mob_level(avg_player, layer):  # MobLevels.compute
    return max(1, min(CAP[layer], jround(avg_player * SHARE) + BONUS[layer]))


def mob_hp_mult(m, strength=1):  # MobService.healthMultiplier (size 1.0)
    return (1 + 0.15 * m) * strength


def mob_dmg_mult(m, strength=1):  # MobService.damageMultiplier (size 1.0, no Brute)
    return (1 + 0.06 * m) * min(strength, DAMAGE_STRENGTH_CAP)


def player_hp(level):  # 20 + LevelCurve.bonusHealth (levels only; tree, gear and trims come on top)
    return 20 + max(0, min(level, MAX_LEVEL) - 1)


def player_dmg_mult(level):  # LevelCurve.damageMultiplier (levels only)
    return 1 + max(0, min(level, MAX_LEVEL) - 1) * 2.0 / 100


def armor_blocked(armor, toughness=0.0):  # ArmorFormula.blocked
    e = max(0.0, armor + toughness / 2.0)
    return min(0.85, e / (e + 20.0))


def dungeon_level(tier):  # DungeonRules.mobLevel
    return 12 + 3 * max(1, tier)


def dungeon_lives(tier):  # DungeonRules.startingLives
    return 1 if tier >= 25 else 2 if tier >= 10 else 3


def waystone_cost(distance, per=250):  # WaystoneRules.cost
    return max(1, int(distance / per))


def trader_price(base, tier):  # TradeRules.traderBase
    return jround(base * (1.0 + 0.5 * (max(1, tier) - 1)))


def tier_for(level):  # TradeRules.tierFor
    t = 1
    for i, f in enumerate((1, 10, 25, 50, 75)):
        if level >= f:
            t = i + 1
    return t


# --- ASSUMPTIONS (not in the repository; the combined play test must confirm each) -----------------------------------
VANILLA = {  # vanilla values for a Normal-difficulty reference mob and the best sword a player may use at full power
    "zombie_hp": 20, "zombie_attack": 3, "witch_hp": 26, "enderman_hp": 40,
    "sword": {1: ("stone sword", 5), 10: ("stone sword (copper: to confirm)", 5), 25: ("iron sword", 6), 50: ("netherite sword", 8),
              100: ("netherite sword", 8)},
}
PACE = {  # kills and chests per hour of play
    "open_kills_low": 120, "open_kills_high": 240, "chests_per_hour": 6,
    "dungeon_runs_per_hour": 4,  # the design log says a run takes about 10 minutes (6 per hour at most)
    "monsters_per_fight_room": 8,  # DungeonRules: 6 to 10, so the mean is 8
    "fight_rooms": 9,  # 6 before the Lieutenant, 3 before the Boss (design log, Dungeons part 1)
}
DESIGN_LOG_HP = {1: 20, 20: 60, 50: 120, 100: 250}  # design-log "Balancing": the player's HP including tree and gear
# --------------------------------------------------------------------------------------------------------------------------


def md_table(headers, rows):
    out = ["| " + " | ".join(headers) + " |", "|" + "|".join("---" for _ in headers) + "|"]
    for r in rows:
        out.append("| " + " | ".join(str(c) for c in r) + " |")
    return "\n".join(out)


def f(x, n=1):
    return f"{x:,.{n}f}"


# --------------------------------------------------------------------------------------------------------------------------
# 1. Combat
# --------------------------------------------------------------------------------------------------------------------------
def combat():
    out = ["### 1a. Mob stat multipliers by mob level (HP x (1 + 0.15 L), damage x (1 + 0.06 L), before rank and size; the player gets +1 HP and +2% damage per level)", ""]
    rows = []
    for L in (1, 10, 15, 20, 25, 40, 50, 75, 100):
        rows.append((L, f"x{mob_hp_mult(L):.2f}", f"x{mob_dmg_mult(L):.2f}", f(VANILLA['zombie_hp'] * mob_hp_mult(L), 0),
                     f(VANILLA['zombie_attack'] * mob_dmg_mult(L), 1), f"x{player_dmg_mult(L):.2f}", player_hp(L)))
    out.append(md_table(["Level", "Mob HP", "Mob damage", "Zombie HP", "Zombie hit", "Player damage (levels only)", "Player HP (levels only)"], rows))
    out += ["", "### 1b. A mob of the player's own level (reference mob: zombie, 20 HP, 3 damage, size 1.0)", ""]
    rows = []
    for L in (1, 10, 25, 50, 100):
        hp = VANILLA["zombie_hp"] * mob_hp_mult(L)
        wname, wdmg = VANILLA["sword"][L]
        hit = wdmg * player_dmg_mult(L)
        hits = math.ceil(hp / hit)
        target = hp / 6
        rows.append((L, f(hp, 0), f"{wname} {wdmg}", f(hit, 1), hits, f(target, 1), "x" + f(target / hit, 2)))
    out.append(md_table(["Level", "Mob HP", "Best full-power sword (vanilla value)", "Hit: sword x level bonus", "Hits to kill",
                         "Hit needed for the design's 6 hits", "Extra needed from gear, enchants, tree"], rows))
    out += ["", "Ranks at the same level (HP x strength; hits at the same hit as above):", ""]
    rows = []
    for L in (1, 10, 25, 50, 100):
        hp = VANILLA["zombie_hp"] * mob_hp_mult(L)
        hit = VANILLA["sword"][L][1] * player_dmg_mult(L)
        cells = [L]
        for rank in ("champion", "elite", "warlord", "lieutenant", "boss"):
            cells.append(math.ceil(hp * STRENGTH[rank] / hit))
        rows.append(cells)
    out.append(md_table(["Level", "Champion x2", "Elite x3", "Warlord x5", "Lieutenant x6", "Boss x10"], rows))

    out += ["", "### 1c. Share of the player's health lost to one hit of a same-level zombie", "",
            "Player HP here is **levels only** (20 + level - 1, `LevelCurve.bonusHealth`); tree and gear come on top. Armor blocks armor/(armor+20), at most 85%.", ""]
    rows = []
    for L in (1, 10, 25, 50, 100):
        dmg = VANILLA["zombie_attack"] * mob_dmg_mult(L)
        hp = player_hp(L)
        cells = [L, f(dmg, 1), hp]
        for armor in (0, 15, 20, 60):
            cells.append(f(100 * dmg * (1 - armor_blocked(armor)) / hp, 1) + "%")
        rows.append(cells)
    out.append(md_table(["Level", "Zombie hit (unarmored)", "Player HP (levels only)", "armor 0", "armor 15 (iron)", "armor 20 (diamond)", "armor 60 (heavy)"], rows))

    out += ["", "Same, with the design log's own player HP (20 / 60 / 120 / 250 at levels 1 / 20 / 50 / 100; the code gives 20 / 39 / 69 / 119 from levels, so the rest must come from tree and gear):", ""]
    rows = []
    for L, hp in DESIGN_LOG_HP.items():
        dmg = VANILLA["zombie_attack"] * mob_dmg_mult(L)
        lvl_hp = player_hp(L)
        cells = [L, hp, lvl_hp, "+" + str(hp - lvl_hp)]
        for armor in (0, 20):
            cells.append(f(100 * dmg * (1 - armor_blocked(armor)) / hp, 1) + "%")
        rows.append(cells)
    out.append(md_table(["Level", "Design-log HP", "HP from levels", "Needed from tree and gear", "armor 0", "armor 20"], rows))
    out += ["", "### 1d. What the player really meets in the open world (mob level from the layer formula, not the player's level)", ""]
    rows = []
    for L in (1, 10, 25, 50, 100):
        for layer in ("surface", "deepslate"):
            m = mob_level(L, layer)
            hp = VANILLA["zombie_hp"] * mob_hp_mult(m)
            hit = VANILLA["sword"][L][1] * player_dmg_mult(L)
            dmg = VANILLA["zombie_attack"] * mob_dmg_mult(m)
            rows.append((L, layer, m, f(hp, 0), math.ceil(hp / hit), f(100 * dmg * (1 - armor_blocked(20)) / player_hp(L), 1) + "%"))
    out.append(md_table(["Player level", "Layer", "Mob level", "Zombie HP", "Hits to kill (reference sword)", "HP lost per hit at armor 20 (levels-only HP)"], rows))
    out += ["", f"Tree ceiling for max health: {tree_hp_note()}"]
    return "\n".join(out)


def tree_hp_note():
    d = json.load(open(ROOT / RES / "vestiges/trees/fight.json", encoding="utf-8"))
    flat, pct, fn, pn = 0.0, 0.0, 0, 0

    def walk(effs):
        nonlocal flat, pct, fn, pn
        for e in effs or []:
            if e.get("stat") == "max_health":
                flat += e["value"]
                fn += 1
            elif e.get("stat") == "max_health_pct":
                pct += e["value"]
                pn += 1

    for n in d["nodes"]:
        walk(n.get("effects"))
        for c in n.get("choices") or []:
            walk(c.get("effects"))
    return (f"the Fight tree has {fn} flat max-health effects (sum +{flat:g} HP) and {pn} max-health-percent effects (sum +{pct:g}%), "
            f"counting both options of a choice node, so the real ceiling is lower (to confirm: points are 1 per level).")


# --------------------------------------------------------------------------------------------------------------------------
# 2. Levels of mobs
# --------------------------------------------------------------------------------------------------------------------------
def levels():
    out = ["### 2a. Open-world mob level against the player's level (no rank; the XP column is the gap rule on the best layer)", ""]
    rows = []
    for P in (1, 10, 15, 20, 25, 30, 40, 50, 60, 70, 80, 100):
        s, c, d = (mob_level(P, k) for k in ("surface", "caves", "deepslate"))
        rows.append((P, s, c, d, f"{d - P:+d}", f"{100 * gap(P, d):.0f}%", f"{100 * gap(P, s):.0f}%"))
    out.append(md_table(["Player", "Surface", "Caves", "Deepslate", "Deepslate minus player", "Kill XP kept, deepslate", "Kill XP kept, surface"], rows))
    out += ["", "Elites may go over the layer cap (`MobLevels.overCap`: Champion +3, Elite +6, Warlord +10), so the highest open-world level is "
            f"{CAP['deepslate'] + 10} (a Warlord in deepslate). Deepslate mobs are at or above the player's level up to player level "
            f"{max(p for p in range(1, 101) if mob_level(p, 'deepslate') >= p)}, and below it from there on.", ""]
    out.append("### 2b. Dungeon tier -> mob level (`12 + 3 x tier`, no cap), lives, and mob level minus player level (kill XP kept) for five player levels")
    out.append("")
    rows = []
    players = (15, 25, 50, 75, 100)
    for T in (1, 2, 3, 5, 10, 15, 20, 25, 30, 35):
        m = dungeon_level(T)
        cells = [T, m, dungeon_lives(T)]
        for P in players:
            cells.append(f"{m - P:+d} ({100 * gap(P, m):.0f}%)")
        rows.append(cells)
    out.append(md_table(["Tier", "Mob level", "Lives"] + [f"vs level {P}" for P in players], rows))
    out += ["", f"A level-100 player is matched by tier {math.ceil((100 - 12) / 3)} (level {dungeon_level(math.ceil((100 - 12) / 3))}), "
            "and tiers 25 and up have **one life**. Tier 1 (level 15) equals the level of the First Key feat (level 15). "
            "Party size multiplies mob HP by 1 + 0.5 x (players - 1), with no cap (2 players x1.5, 4 players x2.5)."]
    return "\n".join(out)


# --------------------------------------------------------------------------------------------------------------------------
# 3. XP
# --------------------------------------------------------------------------------------------------------------------------
def xp_total(level):
    return sum(need(l) for l in range(1, min(level, MAX_LEVEL)))


def run_xp(p, tier):
    """XP of one dungeon run at player level p: 9 fight rooms x 8 monsters + Lieutenant (x6) + Boss (x10), all at the tier's level."""
    m = dungeon_level(tier)
    base = kill_xp(m) * gap(p, m)
    mobs = PACE["fight_rooms"] * PACE["monsters_per_fight_room"]
    return base * (mobs + STRENGTH["lieutenant"] + STRENGTH["boss"])


def simulate_key_climb(players=2000, seed=1):
    """Runs a player needs to reach each level when the only XP is dungeon runs on the key they hold (DungeonRules.nextTier odds)."""
    import random
    rng = random.Random(seed)
    targets = (25, 50, 75, 100)
    runs_to = {t: [] for t in targets}
    for _ in range(players):
        level, xp_in, tier, runs = 15, 0.0, 1, 0
        pending = list(targets)
        while pending and runs < 5000:
            runs += 1
            xp_in += run_xp(level, tier)
            while level < MAX_LEVEL and xp_in >= need(level):
                xp_in -= need(level)
                level += 1
            for t in list(pending):
                if level >= t:
                    runs_to[t].append(runs)
                    pending.remove(t)
            r = rng.random()
            step = -1 if r < 0.15 else 0 if r < 0.70 else 1 if r < 0.95 else 2
            tier = max(1, tier + step)
        for t in pending:
            runs_to[t].append(5000)
    out = {}
    for t, v in runs_to.items():
        v.sort()
        out[t] = (v[len(v) // 2], v[int(len(v) * 0.9)])
    return out


def xp():
    out = ["### 3a. The XP curve (`LevelCurve`)", ""]
    rows = []
    for L in (1, 10, 25, 50, 75, 99):
        rows.append((L, kill_xp(L), need(L), f(8 + 0.9 * L, 1), f"{xp_total(L + 1):,}"))
    out.append(md_table(["Level", "Kill XP at this mob level", "XP to next level", "Same-level kills per level (= 8 + 0.9 L)", "Total XP from level 1 to next"], rows))
    out += ["", f"Level 1 to 100: {xp_total(100):,} XP. After 100 each Paragon level costs {need(100):,} XP.", ""]

    out.append("### 3b. Kills needed to reach a level, by where the player fights (full simulation of the code's rules, no ranks, one level at a time)")
    out.append("")

    def kills_to(target, strategy):
        total = 0.0
        for p in range(1, target):
            m = strategy(p)
            per = max(1, jround(kill_xp(m) * gap(p, m)))
            total += need(p) / per
        return total

    strategies = {
        "Surface": lambda p: mob_level(p, "surface"),
        "Caves": lambda p: mob_level(p, "caves"),
        "Deepslate": lambda p: mob_level(p, "deepslate"),
    }
    rows = []
    for target in (10, 25, 50, 60, 75, 100):
        cells = [target]
        for name, st in strategies.items():
            cells.append(f"{kills_to(target, st):,.0f}")
        rows.append(cells)
    out.append(md_table(["Reach level", "Kills, always on the surface", "Kills, always in caves", "Kills, always in deepslate"], rows))

    def runs_to(target):
        runs, total = 0, 0.0
        for p in range(15, target):
            tier = max(1, math.ceil((p - 12) / 3))
            total += need(p) / run_xp(p, tier)
        return total

    out += ["", "Per dungeon run at a matched tier (9 fight rooms x 8 monsters + Lieutenant x6 + Boss x10 of kill XP; the reward room and quests are extra):", ""]
    rows = []
    for p in (15, 25, 40, 50, 75, 99):
        tier = max(1, math.ceil((p - 12) / 3))
        x = run_xp(p, tier)
        rows.append((p, tier, dungeon_level(tier), f"{x:,.0f}", f(x / need(p), 2)))
    out.append(md_table(["Player level", "Matched tier", "Mob level", "XP per run", "Levels gained per run"], rows))
    r15 = runs_to(100)
    out += ["", f"Level 15 to 100 by matched-tier runs only: about {r15:,.0f} runs (about {r15 * 10 / 60:,.0f} hours at the design log's 10 minutes per run, "
            f"{r15 / PACE['dungeon_runs_per_hour']:,.0f} hours at the {PACE['dungeon_runs_per_hour']} runs per hour assumed below).", ""]

    out += ["Keys decide the tier, and the player cannot pick it. After a clear the next key is N-1 15% / N 55% / N+1 25% / N+2 5% (never below 1), "
            "so the tier drifts up by " + f"{-0.15 + 0.25 + 0.10:.2f} per run (+{0.55 * 0 + 0.25 + 0.10:.2f} at tier 1, where the step down is clamped). "
            "Simulation (2,000 players, seed 1): the player starts at level 15 with a tier 1 key, always runs the key they hold and clears it; no modifiers, no open-world XP, no quests.", ""]
    sim = simulate_key_climb()
    rows = [(tgt, f"{sim[tgt][0]:,.0f}", f"{sim[tgt][1]:,.0f}", f"{sim[tgt][0] * 10 / 60:,.0f} h") for tgt in (25, 50, 75, 100)]
    out.append(md_table(["Reach level", "Runs, median", "Runs, 90th percentile", "Hours at 10 minutes a run (median)"], rows))
    out.append("")

    out.append("Open-world wall: the best layer is deepslate (cap 50). Kill XP per kill and kills per level there:")
    out.append("")
    rows = []
    for p in (40, 50, 55, 60, 64, 70, 80, 100):
        m = mob_level(p, "deepslate")
        per = jround(kill_xp(m) * gap(p, m))
        rows.append((p, m, f"{100 * gap(p, m):.0f}%", per, f"{need(p):,}", f"{need(p) / per:,.0f}"))
    out.append(md_table(["Player", "Deepslate mob level", "Gap rule", "XP per kill", "XP to next level", "Kills per level"], rows))
    return "\n".join(out)


# --------------------------------------------------------------------------------------------------------------------------
# 4. Economy
# --------------------------------------------------------------------------------------------------------------------------
def emeralds_base_drop(m, bestiary=0.0):
    """Expected emeralds from the plain drop roll of one kill by a player (monster_drops: 16.7% of 1-2 below loot level 25, of 1-3 from 25)."""
    return (BL.EMERALD_CHANCE + bestiary) * (1.5 if m < 25 else 2.0)


def emeralds_rank_drop(layer):
    """Expected emeralds per kill from elite ranks by spawn rate (MobService.rollRank, build_loot.ELITES means 3 / 6 / 15)."""
    ch = 0.03 * sum(BL.ELITES[1][:2]) / 2
    if layer == "surface":
        return ch
    if layer == "caves":
        return ch + 0.012 * sum(BL.ELITES[2][:2]) / 2
    return ch + 0.010 * sum(BL.ELITES[2][:2]) / 2 + 0.002 * sum(BL.ELITES[3][:2]) / 2


def per_kill(m, layer, bestiary=0.0):
    return emeralds_base_drop(m, bestiary) + emeralds_rank_drop(layer)


def chest_emeralds(loot_level):
    """Expected emeralds in one chest's loot-band roll (emerald blocks count 9); the structure's own pools come on top (to confirm)."""
    band = next(b for b, (lo, hi) in BL.BANDS.items() if loot_level <= hi) if loot_level <= 100 else BL.NB
    t = BL.fix_schema(BL.band_table(band))
    return BL.expected(t, "minecraft:emerald") + 9 * BL.expected(t, "minecraft:emerald_block")


def run_emeralds(tier):
    """Emeralds in one dungeon run: the reward room (4-8) plus the plain drop roll of every monster (ranks are not rolled in dungeons)."""
    m = dungeon_level(tier)
    mobs = PACE["fight_rooms"] * PACE["monsters_per_fight_room"] + 2
    reward = sum(BL.REWARD_EMERALDS) / 2
    return reward + mobs * emeralds_base_drop(m), reward, mobs * emeralds_base_drop(m)


BANDS_PLAY = [("1-9", 5), ("10-24", 17), ("25-49", 37), ("50-74", 62), ("75-100", 87)]


def villager_stats():
    by = {}
    for fpath in sorted(glob.glob(str(ROOT / RES / "vestiges/villager_trades/*.json"))):
        d = json.load(open(fpath, encoding="utf-8"))
        if d["job"] == "wandering_trader":
            continue
        for o in d["offers"]:
            by.setdefault(o["tier"], []).append(o["price"])
    return by


def economy():
    out = ["### 4a. What one unit of each source pays (expected emeralds)", ""]
    rows = []
    for name, p in BANDS_PLAY:
        m_open = mob_level(p, "caves")
        layer = "caves"
        ll = m_open
        tier = max(1, math.ceil((p - 12) / 3))
        tot, rew, drops = run_emeralds(tier)
        rows.append((name, p, m_open, f(per_kill(m_open, layer), 2), f(chest_emeralds(ll), 2),
                     (f"{tot:.0f} (tier {tier}: reward {rew:.0f} + drops {drops:.0f})" if p >= 15 else "needs level 15")))
    out.append(md_table(["Level band", "Example level", "Caves mob level", "Emeralds per kill", "Emeralds per chest roll", "Emeralds per dungeon run at the matched tier"], rows))
    out += ["", "Per kill = plain drop roll (16.7% of 1-2, or 1-3 from mob level 25) + elite ranks by spawn rate; the example uses the caves layer. "
            "Dungeon monsters roll no ranks, so a Lieutenant or Boss pays the same plain roll as a zombie.", ""]

    out.append("### 4b. Income per hour of play (ASSUMED paces, listed under the table)")
    out.append("")
    rows = []
    for name, p in BANDS_PLAY:
        m = mob_level(p, "caves")
        k_lo, k_hi, ch = PACE["open_kills_low"], PACE["open_kills_high"], PACE["chests_per_hour"]
        pk, pc = per_kill(m, "caves"), chest_emeralds(m)
        lo = k_lo * pk + ch * pc
        hi = k_hi * pk + ch * pc
        if p >= 15:
            tier = max(1, math.ceil((p - 12) / 3))
            dun = PACE["dungeon_runs_per_hour"] * run_emeralds(tier)[0]
            dtxt = f"{dun:,.0f}"
        else:
            dun = None
            dtxt = "-"
        rows.append((name, f"{lo:,.0f} - {hi:,.0f}", dtxt))
    out.append(md_table(["Level band", "Open world per hour (low - high kills)", "Dungeon hour (matched tier)"], rows))
    out += ["", "Assumptions (not in the repository): " + "; ".join(f"{k} = {v}" for k, v in PACE.items()) + ".", ""]

    out.append("### 4c. The prices (villagers sell only; `data/.../villager_trades`, Wandering Trader excluded)")
    out.append("")
    by = villager_stats()
    rows = []
    for t in range(1, 6):
        p = by[t]
        rows.append((t, ["1-9", "10-24", "25-49", "50-74", "75-100"][t - 1], len(p), min(p), statistics.median(p), max(p), sum(p)))
    out.append(md_table(["Price tier", "Player levels", "Offers (13 jobs)", "Cheapest", "Median", "Dearest", "Sum of one of each"], rows))

    out += ["", "Hours of income needed (open world low / high, dungeon hour) to buy one median offer, and one of every offer, of the tier the band unlocks:", ""]
    rows = []
    for (name, p), t in zip(BANDS_PLAY, range(1, 6)):
        m = mob_level(p, "caves")
        pk, pc = per_kill(m, "caves"), chest_emeralds(m)
        lo = PACE["open_kills_low"] * pk + PACE["chests_per_hour"] * pc
        hi = PACE["open_kills_high"] * pk + PACE["chests_per_hour"] * pc
        med, tot = statistics.median(by[t]), sum(by[t])
        if p >= 15:
            tier = max(1, math.ceil((p - 12) / 3))
            dun = PACE["dungeon_runs_per_hour"] * run_emeralds(tier)[0]
            dtxt = f"{med / dun:.2f} / {tot / dun:.1f}"
        else:
            dtxt = "-"
        rows.append((name, f"{med:g}", f"{med / hi:.2f} - {med / lo:.2f} h", f"{tot} -> {tot / hi:.1f} - {tot / lo:.1f} h", dtxt))
    out.append(md_table(["Band", "Median price", "One median offer (open world)", "One of every offer (open world)", "Dungeon hour: median h / all h"], rows))

    out += ["", "### 4d. Waystone trips (`max(1, int(distance / 250))`, no cap) against the world's own sizes (`world_plan`)", ""]
    legs = [("Within a village area", 500), ("Across a town", 1_000), ("Short expedition", 5_000), ("Long trip", 10_000),
            ("Trenec radius (28,000)", 28_000), ("Trenec centre to Thornner centre", int(math.hypot(4000, 57000))),
            ("Trenec centre to Swetren centre", int(math.hypot(75000, 4000))), ("Across the world (Swetren to Restena)", int(math.hypot(137000, 28000)))]
    rows = []
    for label, d in legs:
        c = waystone_cost(d)
        rows.append((label, f"{d:,}", c, f"{c / (PACE['dungeon_runs_per_hour'] * run_emeralds(10)[0]):.2f} h of tier-10 dungeon income"))
    out.append(md_table(["Trip", "Blocks", "Emeralds", "Compared with"], rows))

    out += ["", "### 4e. Chest loot bands (`build_loot.py`: the band roll of one chest; gear and books are one pool, the structure's own pools come on top)", ""]
    rows = []
    for b, (lo, hi) in BL.BANDS.items():
        t = BL.fix_schema(BL.band_table(b))
        eq = BL.mean_rolls(t["pools"][0]["rolls"])
        em = BL.expected(t, "minecraft:emerald") + 9 * BL.expected(t, "minecraft:emerald_block")
        di = BL.expected(t, "minecraft:diamond") + 9 * BL.expected(t, "minecraft:diamond_block")
        row = next(o for m, o in BL.RARITY_ROWS if (lo + hi) / 2 <= m)
        rows.append((b, f"{lo}-{hi}", f"{eq * (1 - BL.BOOK_SHARE):.2f}", f"{eq * BL.BOOK_SHARE:.2f}", f"{em:.2f}", f"{di:.2f}",
                     f"{eq * sum(row[3:]) / 100:.2f}", f"{BL.expected(t, 'minecraft:netherite_scrap'):.2f}"))
    out.append(md_table(["Band", "Loot levels", "Gear rolls", "Book rolls", "Emeralds", "Diamonds", "Rare or better gear/books", "Netherite scrap"], rows))

    out += ["", "### 4f. Other emerald sources and sinks in the code", ""]
    rows = [
        ("Source", "Quests (current files; the two test quests left out)", quest_text(), "QuestRules.emeralds, quests/*.json"),
        ("Source", "Dragons 1-6 (dragons branch)", "32 x N each: 32 / 64 / 96 / 128 / 160 / 192 = 672 in total", ""),
        ("Source", "Emerald ore sites (world plan)", "16 veins of up to 6 ore per chunk inside a 128-block-radius site (about 200 chunks), 3 or more sites per world: up to ~19,000 ore per site", "mining time is the only limit; to confirm in the play test"),
        ("Sink", "Enchanting table", "ceil(shown cost / 5) emeralds per enchant (at most 6 for a cost-30 enchant)", "EnchantScale.tableEmeralds"),
        ("Sink", "Anvil", "the vanilla level cost, in emeralds (up to 39; 'Too Expensive' from 40)", "EnchantScale.anvilEmeralds"),
        ("Sink", "Passive tree refund", "1 + level/10 per node (level 100: 11)", "TreeRules.refundCost"),
        ("Sink", "Villagers, waystones", "see 2.4c and 2.4d", ""),
    ]
    out.append(md_table(["Kind", "What", "Numbers", "Source / note"], rows))
    return "\n".join(out)


def quest_text():
    t = quests_summary()
    side_n, side_first = t["side"]
    base = [json.load(open(x, encoding="utf-8")) for x in glob.glob(str(ROOT / RES / "vestiges/quests/side_*.json"))]
    comeback = sum(max(1, min(3, d["reward"]["emeralds"] // 4)) for d in base) * 4  # comebacks 2 to 5 (QuestRules.MAX_COMEBACK = 5)
    return (f"{t['guide'][0]} guide quests pay {t['guide'][1]} in all, once; {side_n} side quests pay {side_first} the first time and "
            f"{comeback} more over 4 comebacks (one a week each). Lifetime {t['guide'][1] + side_first + comeback}")


def quests_summary():
    rows, tot = [], {"guide": [0, 0], "side": [0, 0]}
    for fpath in sorted(glob.glob(str(ROOT / RES / "vestiges/quests/*.json"))):
        d = json.load(open(fpath, encoding="utf-8"))
        k = d.get("kind")
        r = d.get("reward", {})
        if k in tot:
            tot[k][0] += 1
            tot[k][1] += r.get("emeralds", 0)
    return tot


# --------------------------------------------------------------------------------------------------------------------------
# 5. Dragons and bosses (dragons branch, read with git show)
# --------------------------------------------------------------------------------------------------------------------------
def git_show(path):
    try:
        return subprocess.run(["git", "show", f"{DRAGON_REF}:{path}"], cwd=ROOT, capture_output=True, text=True, check=True).stdout
    except Exception:
        return None


def dragons():
    out = ["### 5a. Dragons 1-6 (`DragonRules` and `dragons/*.json` on the dragons branch; vanilla dragon = 200 HP)", ""]
    rows = []
    names = ["tulva", "belaz", "morst", "dhars", "ilvig", "durhos"]
    defs = []
    for n in names:
        raw = git_show(f"{RES}vestiges/dragons/{n}.json")
        if raw is None:
            return "Dragon rows skipped: the branch " + DRAGON_REF + " was not found."
        defs.append(json.loads(raw))
    total_em = 0
    for d in defs:
        N = d["number"]
        hp1 = 200 * d["health"]
        hp4 = hp1 * 2.5
        gate = d["gate"]
        lvl = d["level"]
        php = player_hp(max(lvl, 1))
        design_hit = 20 * mob_hp_mult(lvl) / 6
        total_em += d["rewards"]["emeralds"]
        rows.append((N, d["name"], gate or "none", f(hp1, 0), f(hp4, 0), f"x{d['damage']:g}", f(0.08 * hp1, 0), math.ceil(hp1 / design_hit),
                     php, f"{need(lvl):,}", d["rewards"]["emeralds"]))
    out.append(md_table(["N", "Name", "Gate (summoner level)", "HP alone", "HP with 4 players", "Damage", "8% hit cap (alone)",
                         "Hits at the design's 'level L hit = zombie HP / 6'", "Player HP at the dragon's level (levels only)", "XP reward (1 level at its level)", "Emeralds"], rows))
    out += ["", f"Emeralds from dragons 1-6: {total_em}. XP and emeralds follow the data files; dragon 6 pays emeralds although the design notes say 'XP and emeralds only for dragons 1-5' (to confirm)."]

    out += ["", "### 5b. Dungeon bosses at the level of the same gate (Boss x10 HP; vanilla base HP are ASSUMPTIONS: witch 26, enderman 40, zombie 20 for the Lieutenant x6)", ""]
    rows = []
    for T in (1, 3, 4, 5, 7, 10):
        m = dungeon_level(T)
        rows.append((T, m, f(VANILLA["witch_hp"] * mob_hp_mult(m, 10), 0), f(VANILLA["enderman_hp"] * mob_hp_mult(m, 10), 0),
                     f(VANILLA["zombie_hp"] * mob_hp_mult(m, 6), 0)))
    out.append(md_table(["Tier", "Mob level", "Overgrown Boss (witch)", "End Boss (enderman)", "Lieutenant (zombie x6)"], rows))
    return "\n".join(out)


# --------------------------------------------------------------------------------------------------------------------------
# Registry: every draft number, its value, and where it lives. The regex must still match the file (--check).
# (area, parameter, value, status, source file, regex)
# --------------------------------------------------------------------------------------------------------------------------
LOG = "docs/design-log.md"
R = [
    # ---- mob levels and strength
    ("Mob levels", "Mob level share of nearby players' average level", "75%", "draft", J + "config/ServerConfig.java", r"mobLevelShare = 0\.75"),
    ("Mob levels", "Caves layer bonus", "+3", "draft", J + "config/ServerConfig.java", r"cavesLevelBonus = 3"),
    ("Mob levels", "Deepslate layer bonus (Nether and End count as deepslate)", "+6", "draft", J + "config/ServerConfig.java", r"deepslateLevelBonus = 6"),
    ("Mob levels", "Surface cap", "20", "draft", J + "config/ServerConfig.java", r"surfaceLevelCap = 20"),
    ("Mob levels", "Caves cap", "40", "draft", J + "config/ServerConfig.java", r"cavesLevelCap = 40"),
    ("Mob levels", "Deepslate cap", "50", "draft", J + "config/ServerConfig.java", r"deepslateLevelCap = 50"),
    ("Mob levels", "Caves = more than 3 blocks under the surface; deepslate = below y 0", "3 / 0", "draft", J + "level/XpSources.java", r"pos\.getY\(\) < surface - 3"),
    ("Mob levels", "Over-cap for Warlord (Champion +3, Elite +6)", "+10", "draft", J + "level/MobLevels.java", r"case 3 -> 10;"),
    ("Mob levels", "Players within this range set the level", "64 blocks", "draft", J + "mobs/MobService.java", r"d <= 64 \* 64"),
    ("Mob levels", "Mob size roll (normal curve, clamp 80-125%)", "sd 0.065", "draft", J + "mobs/MobService.java", r"nextGaussian\(\) \* 0\.065"),
    ("Mob stats", "Mob HP per level", "+15% of base per level", "draft", J + "mobs/MobService.java", r"1 \+ 0\.15 \* d\.level\(\)"),
    ("Mob stats", "Mob damage per level", "+6% of base per level", "draft", J + "mobs/MobService.java", r"1 \+ 0\.06 \* d\.level\(\)"),
    ("Mob stats", "Brute power damage", "x1.4", "draft", J + "mobs/MobService.java", r"d\.has\(\"brute\"\) \? 1\.4"),
    ("Mob stats", "Champion strength (HP, XP)", "x2", "draft", J + "mobs/MobData.java", r"case CHAMPION -> 2;"),
    ("Mob stats", "Elite strength", "x3", "draft", J + "mobs/MobData.java", r"case ELITE -> 3;"),
    ("Mob stats", "Warlord strength", "x5", "draft", J + "mobs/MobData.java", r"case WARLORD -> 5;"),
    ("Mob stats", "Lieutenant strength", "x6", "draft", J + "mobs/MobData.java", r"case LIEUTENANT -> 6;"),
    ("Mob stats", "Boss strength (Overlord x20 is in the log, not in the code)", "x10", "draft", J + "mobs/MobData.java", r"case BOSS -> 10;"),
    ("Mob stats", "Damage strength never above", "5", "draft, code only", J + "mobs/MobData.java", r"Math\.min\(strength\(\), 5\)"),
    ("Mob stats", "Warlord spawn rate (deepslate)", "0.2%", "draft", J + "mobs/MobService.java", r"r < 0\.002"),
    ("Mob stats", "Elite spawn rate (caves and deeper; 1.2% in caves, 1.0% in deepslate)", "1.0-1.2%", "draft", J + "mobs/MobService.java", r"r < 0\.012"),
    ("Mob stats", "Champion spawn rate (everywhere)", "3%", "draft", J + "mobs/MobService.java", r"championFrom \+ 0\.03"),
    # ---- player and XP
    ("Player and XP", "Kill XP", "10 + 4 x mob level", "draft", J + "level/LevelCurve.java", r"10 \+ 4 \* Math\.max\(0, mobLevel\)"),
    ("Player and XP", "XP from level L to L+1", "kill XP(L) x (8 + 0.9 L)", "draft", J + "level/LevelCurve.java", r"8 \+ 0\.9 \* l"),
    ("Player and XP", "Gap rule: full XP down to 5 levels below you", "5", "draft", J + "level/LevelCurve.java", r"below <= 5"),
    ("Player and XP", "Gap rule: +5% per level above you, at most +50%", "5% / 50%", "draft", J + "level/LevelCurve.java", r"Math\.min\(0\.5, above \* 0\.05\)"),
    ("Player and XP", "Gap rule: -10% per extra level below, floor 10%", "10% / 10%", "draft", J + "level/LevelCurve.java", r"Math\.max\(0\.1, 1\.0 - \(below - 5\) \* 0\.1\)"),
    ("Player and XP", "Max health per level", "+1", "draft", J + "level/LevelCurve.java", r"Math\.min\(level, MAX_LEVEL\) - 1\) \+ paragonBonus\(paragon, 0\.5\)"),
    ("Player and XP", "Damage per level", "+2%", "draft", J + "level/LevelCurve.java", r"\* 2\.0 \+ paragonBonus"),
    ("Player and XP", "Level cap, Paragon XP per level, Paragon soft cap", "100 / 40,000 / 50", "draft", J + "level/LevelCurve.java", r"PARAGON_XP = 40_000"),
    ("Player and XP", "Death loses (of the level's need)", "5% at level 1 to 15% at 100", "draft", J + "config/ServerConfig.java", r"deathXpLossMaxPercent = 15"),
    ("Player and XP", "XP multiplier for the whole server", "1.0", "default", J + "config/ServerConfig.java", r"xpMultiplier = 1\.0"),
    ("Player and XP", "First visit XP: biome / structure type", "100 / 150", "draft", J + "level/XpSources.java", r"BIOME_XP = 100"),
    ("Player and XP", "First visit XP: Nether / End", "500 / 1000", "draft", J + "level/XpSources.java", r"NETHER_XP = 500"),
    ("Player and XP", "Ore XP (coal 2 ... diamond 25, emerald 30, ancient debris 60)", "2-60", "draft", J + "level/XpSources.java", r"ore\(\"emerald_ore\", 30\)"),
    ("Gear and armor", "Armor blocks armor / (armor + 20), at most", "85%", "draft", J + "mobs/ArmorFormula.java", r"MAX_BLOCKED = 0\.85f"),
    ("Gear and armor", "Gear ladder: level each gear tier is meant for (1 wood/stone/leather, 5 chain, 10 copper, 15 gold, 20 iron, 30 diamond, 40 netherite)", "1-40", "draft", J + "gear/GearLadder.java", r"The level a piece of gear is meant for"),
    ("Gear and armor", "Gear above your level: weapon damage", "x0.5 (log says: keep full stats, slow the player)", "draft, differs from the log", J + "tree/TreeCombat.java", r"a weapon above your level deals half damage"),
    ("Gear and armor", "Gear above your level: armor and toughness", "x0.5", "draft, differs from the log", J + "gear/GearService.java", r"armorLoss \+= sums\[0\] / 2"),
    ("Gear and armor", "Rarity odds per item by loot level (first row: levels 1-10)", "60/35/4.9/0.1/0/0/0 %", "draft", "tools/build_loot.py", r"\(10, \[60, 35, 4\.9, 0\.1, 0, 0, 0\]\)"),
    ("Gear and armor", "Enchant table emeralds", "ceil(shown cost / 5)", "draft", J + "gear/EnchantScale.java", r"Math\.ceil\(shownCost / 5\.0\)"),
    ("Gear and armor", "Anvil use costs", "vanilla level cost, in emeralds", "draft", J + "gear/EnchantScale.java", r"levelCost \* Math\.max\(0"),
    ("Gear and armor", "Tree refund / choice switch", "1 + level/10 emeralds", "draft", J + "tree/TreeRules.java", r"1 \+ Math\.max\(0, level\) / 10"),
    # ---- dungeons
    ("Dungeons", "Mob level in a dungeon", "12 + 3 x tier", "draft", J + "dungeon/DungeonRules.java", r"12 \+ 3 \* Math\.max\(1, tier\)"),
    ("Dungeons", "Lives per player", "3 (tiers 1-9), 2 (10-24), 1 (25+)", "draft", J + "dungeon/DungeonRules.java", r"tier >= 25 \? 1 : tier >= 10 \? 2 : 3"),
    ("Dungeons", "Mob HP per extra player (no cap)", "+50%", "draft", J + "dungeon/DungeonRules.java", r"1\.0 \+ 0\.5 \* Math\.max\(0, players - 1\)"),
    ("Dungeons", "Monsters per fight room", "6 to 10", "draft", J + "dungeon/DungeonRules.java", r"MIN_MONSTERS = 6"),
    ("Dungeons", "Run shape: start, 6 rooms, Lieutenant, 3 rooms, Boss, reward (about 10 minutes)", "13 rooms", "draft", LOG, r"start room → 6 rooms → Lieutenant → 3 rooms → Boss → reward room"),
    ("Dungeons", "Next key after tier N: N-1 / N / N+1 / N+2", "15 / 55 / 25 / 5 %", "draft", J + "dungeon/DungeonRules.java", r"roll < 0\.15 \? -1 : roll < 0\.70 \? 0 : roll < 0\.95 \? 1 : 2"),
    ("Dungeons", "Next key keeps the theme", "60%", "draft", J + "dungeon/DungeonRules.java", r"KEEP_THEME = 0\.60"),
    ("Dungeons", "Loot level inside a dungeon = mob level (no cap)", "12 + 3 x tier", "draft", J + "dungeon/DungeonService.java", r"DungeonRules\.mobLevel\(run\.tier\(\)\)"),
    ("Dungeons", "Reward room gear rolls (rarity at least Uncommon)", "2", "draft", "tools/build_loot.py", r"REWARD_GEAR_ROLLS = 2"),
    ("Dungeons", "Reward room book chance", "35%", "draft", "tools/build_loot.py", r"REWARD_BOOK_CHANCE = 0\.35"),
    ("Dungeons", "Reward room emeralds", "4 to 8 (not scaled by tier)", "draft", "tools/build_loot.py", r"REWARD_EMERALDS = \(4, 8\)"),
    ("Dungeons", "Elytra: first End Boss clear at this tier or higher (the log says 3, docs/ideas/dragons.md says 4)", "3", "draft, docs disagree", RES + "vestiges/dungeon_themes/end.json", r"\"elytra_tier\": 3"),
    ("Dungeons", "Low key drops: Elite 2%, Warlord 8%, tiers 1-3", "2% / 8%", "draft, log says tiers 1-5", "tools/build_loot.py", r"KEY_DROPS = \{2: \(0\.02, 1, 3\), 3: \(0\.08, 1, 3\)\}"),
    # ---- loot
    ("Loot", "Mob emerald drop chance (player kills)", "16.7% (1 kill in 6)", "approved 2026-10-11", "tools/build_loot.py", r"EMERALD_CHANCE = 0\.167"),
    ("Loot", "Mob emerald count: below loot level 25 / from 25", "1-2 / 1-3", "draft", "tools/build_loot.py", r"count_fn\(1, 3\)"),
    ("Loot", "Mob ingot chance", "20% (1 kill in 5)", "approved 2026-10-11", "tools/build_loot.py", r"INGOT_CHANCE = 0\.20"),
    ("Loot", "Champion extra: emeralds / gear chance", "2-4 / 10%", "draft", "tools/build_loot.py", r"1: \(2, 4, 0\.10, 0\.0, None\)"),
    ("Loot", "Elite extra: emeralds / gear / book", "4-8 / 25% / 10%", "draft", "tools/build_loot.py", r"2: \(4, 8, 0\.25, 0\.10, None\)"),
    ("Loot", "Warlord extra: emeralds / gear / Relic book", "10-20 / 100% / 5%", "draft", "tools/build_loot.py", r"3: \(10, 20, 1\.0, 0\.05, \"relic\"\)"),
    ("Loot", "Chest loot bands (loot level 1-4, 5-9, 10-14, 15-19, 20-29, 30-39, 40-49, 50-64, 65-79, 80-89, 90-100)", "11 bands", "draft", "tools/build_loot.py", r"1: \(1, 4\), 2: \(5, 9\)"),
    ("Loot", "Chest emerald (weight 70 from band 1) and emerald block (weight 12 from band 6)", "see 4a", "draft", "tools/build_loot.py", r"Item\(\"emerald\", 1, 70"),
    ("Loot", "Chest: share of gear rolls that are books", "25%", "draft", "tools/build_loot.py", r"BOOK_SHARE = 0\.25"),
    ("Loot", "Bestiary adds to emerald and ingot chance", "+1% (50 kills) / +2% (250)", "draft", J + "codex/CodexRules.java", r"stage >= 4 \? 0\.02 : stage == 3 \? 0\.01 : 0"),
    # ---- quests and villagers and waystones
    ("Quests", "Side quest XP share (first time), emeralds", "0.4 of a level, 6 emeralds", "draft", RES + "vestiges/quests/side_armorer.json", r"\"emeralds\": 6"),
    ("Quests", "Side quest comeback: XP share / emeralds / goal growth", "10% / 1 to 3 / +50%", "draft", J + "quest/QuestRules.java", r"COMEBACK_SHARE = 0\.10"),
    ("Quests", "Side quest comeback emeralds", "max(1, min(3, base / 4))", "draft", J + "quest/QuestRules.java", r"Math\.max\(1, Math\.min\(3, base / 4\)\)"),
    ("Quests", "Side quest comebacks and wait", "5 / 7 days", "draft", J + "quest/QuestRules.java", r"MAX_COMEBACK = 5"),
    ("Quests", "Guide quest rewards: XP 25-300, emeralds 0-8", "25 quests, 39 emeralds in all", "draft", RES + "vestiges/quests/choose_your_path.json", r"\"emeralds\": 8"),
    ("Villagers", "Level bands = price tiers 1-5", "1, 10, 25, 50, 75", "draft", J + "villager/TradeRules.java", r"TIER_FROM = \{1, 10, 25, 50, 75\}"),
    ("Villagers", "Price bands per tier (emeralds)", "1-3 / 4-8 / 9-20 / 21-55 / 56-150", "draft", "docs/ideas/villagers.md", r"price bands 1-3 / 4-8 / 9-20 / 21-55 / 56-150"),
    ("Villagers", "Stock per offer per day: normal / enchanted-grade / luxury", "12 / 3 / 1", "draft", J + "villager/TradeRules.java", r"case \"e\" -> 3;"),
    ("Villagers", "Wandering Trader price rise per tier", "+50% of base", "draft", J + "villager/TradeRules.java", r"1\.0 \+ 0\.5 \* \(Math\.max\(1, tier\) - 1\)"),
    ("Villagers", "Emerald trim discount, per trim (cap 50%)", "2%", "draft", J + "gear/GearService.java", r"Map\.entry\(\"emerald\", new TrimStat\(\"trade_discount\", 2"),
    ("Waystones", "Trip cost", "1 emerald per 250 blocks, minimum 1, no cap", "draft", J + "config/ServerConfig.java", r"waystoneBlocksPerEmerald = 250"),
    ("Waystones", "Cost formula (cap decision still open)", "max(1, int(distance / 250))", "draft", J + "waystone/WaystoneRules.java", r"Math\.max\(1, \(int\) \(distance / per\)\)"),
    ("World", "Emerald ore only at sites: veins per chunk / size / height", "16 / 6 / -32 to 120", "draft", J + "world/EmeraldSites.java", r"VEINS_PER_CHUNK = 16"),
    ("World", "Emerald sites: radius, most per land, least per world", "128 / 2 / 3", "draft", RES + "vestiges/world_plan/trenec.json", r"\"emerald_radius\": 128"),
    # ---- dragons (dragons branch)
    ("Dragons", "Vanilla dragon health", "200", "draft", "git:" + J + "dragon/DragonRules.java", r"VANILLA_HEALTH = 200\.0"),
    ("Dragons", "Dragon health per dragon after the first", "+40%", "draft", "git:" + J + "dragon/DragonRules.java", r"1\.0 \+ 0\.4 \* \(Math\.max\(1, n\) - 1\)"),
    ("Dragons", "Dragon damage per dragon after the first", "+20%", "draft", "git:" + J + "dragon/DragonRules.java", r"1\.0 \+ 0\.2 \* \(Math\.max\(1, n\) - 1\)"),
    ("Dragons", "Dragon health per extra player (up to 4 players)", "+50%", "draft", "git:" + J + "dragon/DragonRules.java", r"1\.0 \+ 0\.5 \* \(Math\.min"),
    ("Dragons", "Largest hit as share of maximum health", "8%", "approved", "git:" + J + "dragon/DragonRules.java", r"HIT_CAP = 0\.08"),
    ("Dragons", "Summoner level gate (dragon 1: none)", "4 x N", "approved", "git:" + J + "dragon/DragonRules.java", r"LEVELS_PER_DRAGON = 4"),
    ("Dragons", "XP reward: one level's worth at the dragon's level", "need(4 x N)", "draft", "git:" + J + "dragon/DragonRules.java", r"LevelCurve\.need\(Math\.max\(1, level\)\) \* levels"),
    ("Dragons", "Emeralds per dragon (dragon 6 included)", "32 x N", "draft", "git:" + RES + "vestiges/dragons/durhos.json", r"\"emeralds\": 192"),
]


def read_source(path):
    if path.startswith("git:"):
        raw = git_show(path[4:])
        return raw if raw is not None else None
    p = ROOT / path
    return p.read_text(encoding="utf-8") if p.exists() else None


def locate(path, regex):
    text = read_source(path)
    if text is None:
        return None
    pat = re.compile(regex)
    for i, line in enumerate(text.splitlines(), 1):
        if pat.search(line):
            return i
    return -1


def registry():
    rows = []
    for area, name, value, status, path, regex in R:
        line = locate(path, regex)
        shown = path[4:] if path.startswith("git:") else path
        short = shown.replace(J, "dev/vestiges/")
        where = "branch only" if path.startswith("git:") else ""
        if line is None:
            ref = f"`{short}` (file not found)"
        elif line < 0:
            ref = f"`{short}` (line not found: re-check)"
        else:
            ref = f"`{short}:{line}`" + (f" ({DRAGON_REF})" if where else "")
        rows.append((area, name, value, status, ref))
    return md_table(["Area", "Parameter", "Value", "Status", "Source"], rows)


def check():
    bad = 0
    for area, name, value, status, path, regex in R:
        line = locate(path, regex)
        if line is None and path.startswith("git:"):
            print(f"SKIP  {name}: dragons branch not available")
            continue
        if line is None or line < 0:
            bad += 1
            print(f"DRIFT {area}: {name} -> {path} no longer matches /{regex}/")
    print(f"{len(R)} mirrored numbers checked, {bad} drifted.")
    return 1 if bad else 0


def main():
    if hasattr(sys.stdout, "reconfigure"):
        sys.stdout.reconfigure(encoding="utf-8")
    if "--check" in sys.argv:
        sys.exit(check())
    if "--registry" in sys.argv:
        print(registry())
        return
    if "--quests" in sys.argv:
        print(quests_summary())
        return
    for title, fn in (("1. Combat", combat), ("2. Mob levels", levels), ("3. XP", xp), ("4. Economy", economy), ("5. Dragons and bosses", dragons)):
        print(f"## {title}\n")
        print(fn())
        print()


if __name__ == "__main__":
    main()
