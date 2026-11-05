#!/usr/bin/env python3
"""build_worldgen.py - the Vestiges world type: the terrain chain that reads the globe map (Java: dev.vestiges.world.GlobeMap).

Writes, under src/main/resources/data/:
    vestiges/worldgen/world_preset/trenec.json            the world type (Nether and End stay vanilla)
    vestiges/worldgen/noise_settings/trenec.json          vanilla's Large Biomes settings, pointing at our files
    vestiges/worldgen/density_function/trenec/*.json      vanilla's terrain chain, with climate read from the map
    vestiges/worldgen/noise/*_medium.json                 biome noise between normal and Large Biomes
    minecraft/tags/worldgen/world_preset/normal.json      adds the world type to the World Type button

Vanilla's terrain files point at each other by name, so changing `continents` means copying the chain that uses it. Copies go under our
own names so vanilla worlds never change. Vanilla's files are read from the game jar, so they can't drift from the pinned version.

Run after changing anything here:   python tools/build_worldgen.py
"""

import copy
import json
import shutil
import zipfile
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
DATA = ROOT / "src" / "main" / "resources" / "data"
WORLDGEN = DATA / "vestiges" / "worldgen"
MC_JAR = next((Path.home() / ".gradle" / "caches" / "fabric-loom" / "minecraftMaven" / "net" / "minecraft").glob("minecraft-common-deobf/26.3/*.jar"))

BIOME_NOISES = {"continents": "continentalness", "erosion": "erosion", "temperature": "temperature", "vegetation": "vegetation"}  # file: noise
CHAIN = ("offset", "factor", "jaggedness", "depth", "sloped_cheese", "final_density", "preliminary_surface_level", "chunk_surface_level")
LARGE = "minecraft:overworld_large_biomes/"
CAVES = "minecraft:overworld/caves/"
RIDGES = "minecraft:overworld/ridges"
CAVE_FILES = ("entrances", "noodle", "pillars", "spaghetti_2d", "spaghetti_2d_thickness_modulator", "spaghetti_roughness_function")
CAVE_NOISE = ("cave", "spaghetti", "pillar", "noodle")
CAVE_SCALE = 0.7        # below 1 stretches a noise, so caves get bigger and further apart
ENTRANCE_SCALE = 0.85   # entrances reach the surface, so they change less
TOP_FADE = {240: 304, 256: 320}  # Amplified's ceiling, so peaks can pass vanilla's 256
OURS = "vestiges:trenec/"


def globe(channel):
    return {"type": "vestiges:globe", "channel": channel}


def weighted(noise, name):
    """noise x <name>_weight + <name>_base: the map sets the value, and the noise adds as much local variety as the map allows."""
    return {"type": "minecraft:add", "left": {"type": "minecraft:mul", "left": noise, "right": globe(name + "_weight")}, "right": globe(name + "_base")}


def scale_caves(value, scale):
    """Stretches every cave noise found inside a terrain file."""
    if isinstance(value, dict):
        if value.get("type") == "minecraft:noise" and any(word in value.get("noise", "") for word in CAVE_NOISE):
            value["xz_scale"] *= scale
            value["y_scale"] *= scale
        for child in value.values():
            scale_caves(child, scale)
    elif isinstance(value, list):
        for child in value:
            scale_caves(child, scale)
    return value


def raise_ceiling(value):
    """Moves the fade at the top of the world to Amplified's heights."""
    if isinstance(value, dict):
        if value.get("type") == "minecraft:gradient" and value.get("axis") == "y" and value.get("from_coordinate") in TOP_FADE:
            value["from_coordinate"] = TOP_FADE[value["from_coordinate"]]
            value["to_coordinate"] = TOP_FADE[value["to_coordinate"]]
        for child in value.values():
            raise_ceiling(child)
    elif isinstance(value, list):
        for child in value:
            raise_ceiling(child)
    return value


def vanilla(jar, path):
    return json.loads(jar.read(f"data/minecraft/{path}.json"))


def write(path, value):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(value, indent=2) + "\n", encoding="utf-8", newline="\n")


def climate(jar, file, change):
    """A vanilla climate file with our noise, changed by `change`, inside the cache if the vanilla file has one."""
    function = copy.deepcopy(vanilla(jar, f"worldgen/density_function/overworld_large_biomes/{file}"))
    cached = function["type"] == "minecraft:cache"
    noise = function["input"] if cached else function
    noise["noise"] = f"vestiges:{BIOME_NOISES[file]}_medium"
    changed = change(noise)
    return {"type": "minecraft:cache", "input": changed} if cached else changed


def main():
    shutil.rmtree(WORLDGEN, ignore_errors=True)
    shutil.rmtree(DATA / "vestiges" / "vestiges" / "world", ignore_errors=True)
    functions = WORLDGEN / "density_function" / "trenec"

    def relink(value):
        return json.loads(json.dumps(value).replace(LARGE, OURS).replace(CAVES, OURS + "caves/").replace(RIDGES, OURS + "ridges"))

    with zipfile.ZipFile(MC_JAR) as jar:
        for n in BIOME_NOISES.values():
            noise = vanilla(jar, f"worldgen/noise/{n}")
            noise["base_octave"] -= 1
            write(WORLDGEN / "noise" / f"{n}_medium.json", noise)
        for file in CHAIN:
            chain = relink(vanilla(jar, f"worldgen/density_function/overworld_large_biomes/{file}"))
            if file == "offset":
                # Ranges stand higher. Low ground gets nothing, because the map's mountain value is 0 there.
                # Near rivers a ceiling keeps the ground low enough for water to reach.
                chain["input"]["second"] = {"type": "minecraft:min", "left": {"type": "minecraft:add", "left": chain["input"]["second"], "right": globe("offset_extra")}, "right": globe("offset_limit")}
            write(functions / f"{file}.json", scale_caves(raise_ceiling(chain), CAVE_SCALE))
        # Vanilla makes a river where ridges is near 0, so this one input steers both the valley and the river biome.
        ridges = vanilla(jar, "worldgen/density_function/overworld/ridges")
        ridges["input"] = {"type": "vestiges:globe_ridges", "input": ridges["input"]}
        write(functions / "ridges.json", ridges)
        write(functions / "ridges_folded.json", relink(vanilla(jar, "worldgen/density_function/overworld/ridges_folded")))
        for file in CAVE_FILES:
            scale = ENTRANCE_SCALE if file == "entrances" else CAVE_SCALE
            write(functions / "caves" / f"{file}.json", scale_caves(relink(vanilla(jar, f"worldgen/density_function/overworld/caves/{file}")), scale))
        # The cap keeps open sea open: the noise can't raise land inside the safe distance.
        write(functions / "continents.json", climate(jar, "continents", lambda noise: {"type": "minecraft:min", "left": weighted(noise, "continents"), "right": globe("continents_cap")}))
        write(functions / "temperature.json", climate(jar, "temperature", lambda noise: weighted(noise, "temperature")))
        write(functions / "vegetation.json", climate(jar, "vegetation", lambda noise: weighted(noise, "humidity")))
        write(functions / "erosion.json", climate(jar, "erosion", lambda noise: weighted(noise, "erosion")))
        write(WORLDGEN / "noise_settings" / "trenec.json", relink(vanilla(jar, "worldgen/noise_settings/large_biomes")))
        preset = vanilla(jar, "worldgen/world_preset/large_biomes")
        preset["dimensions"]["minecraft:overworld"]["generator"]["settings"] = "vestiges:trenec"
        write(WORLDGEN / "world_preset" / "trenec.json", preset)

    with zipfile.ZipFile(MC_JAR) as jar:
        # Dilecta: one vanilla village, as big as the game allows (20 pieces deep, within 116 blocks of its centre, since the 128 limit includes terrain blending), in whatever biome the coast has.
        capital = vanilla(jar, "worldgen/structure/village_plains")
        capital["size"] = 20
        capital["max_distance_from_center"] = 116
        capital["biomes"] = "#minecraft:is_overworld"
        write(WORLDGEN / "structure" / "dilecta.json", capital)
    write(WORLDGEN / "structure_set" / "dilecta.json", {"placement": {"type": "vestiges:plan_site", "village": "Dilecta"}, "structures": [{"structure": "vestiges:dilecta", "weight": 1}]})

    write(DATA / "minecraft" / "tags" / "worldgen" / "world_preset" / "normal.json", {"replace": False, "values": ["vestiges:trenec"]})
    print(f"Wrote the world type under {WORLDGEN.relative_to(ROOT)}")


if __name__ == "__main__":
    main()
