from typing import List
from app.engine.voxel_grid import VoxelGrid
from app.engine.palette import THEME_PALETTES
from app.models import VariationOption

def apply_theme(grid: VoxelGrid, theme_name: str) -> VoxelGrid:
    """Replaces blocks according to the theme dictionary."""
    theme_map = THEME_PALETTES.get(theme_name, {})
    new_grid = VoxelGrid(grid.width, grid.height, grid.length)
    
    for (x, y, z), b_name in grid.voxels.items():
        clean_name = b_name.split("[")[0].strip()
        replaced = theme_map.get(clean_name, b_name)
        new_grid.set_block(x, y, z, replaced)
        
    return new_grid

def add_medieval_roof(grid: VoxelGrid) -> VoxelGrid:
    """Adds an A-frame peaked gable roof atop the highest voxels."""
    new_grid = grid.clone()
    new_grid.recalculate_bounds()
    w, h, l = new_grid.width, new_grid.height, new_grid.length

    if w < 3 or l < 3 or h < 2:
        return new_grid

    # Add gabled roof along Z axis
    half_w = (w + 1) // 2
    for step in range(half_w):
        y = h + step
        x_left = step
        x_right = w - 1 - step
        if x_left > x_right:
            break
        for z in range(l):
            new_grid.set_block(x_left, y, z, "minecraft:spruce_planks")
            new_grid.set_block(x_right, y, z, "minecraft:spruce_planks")
            # Inner fill
            if x_left == 0 or x_right == w - 1:
                new_grid.set_block(x_left, y, z, "minecraft:spruce_log")

    # Corner lanterns
    new_grid.set_block(0, h, 0, "minecraft:lantern")
    new_grid.set_block(w - 1, h, 0, "minecraft:lantern")
    new_grid.set_block(0, h, l - 1, "minecraft:lantern")
    new_grid.set_block(w - 1, h, l - 1, "minecraft:lantern")

    new_grid.recalculate_bounds()
    return new_grid

def add_crenellations_and_foundation(grid: VoxelGrid, wall_block: str = "minecraft:deepslate_bricks") -> VoxelGrid:
    """Adds stone foundation and castle battlements/crenellations on the top."""
    new_grid = grid.clone()
    new_grid.recalculate_bounds()
    w, h, l = new_grid.width, new_grid.height, new_grid.length

    # 1. Foundation layer underneath (Y = 0 shifted or cobblestone border)
    for x in range(w):
        for z in range(l):
            if (x, 0, z) in new_grid.voxels:
                new_grid.set_block(x, 0, z, "minecraft:polished_blackstone_bricks")

    # 2. Crenellations at the top perimeter
    top_y = h
    for x in range(w):
        if x % 2 == 0:
            new_grid.set_block(x, top_y, 0, wall_block)
            new_grid.set_block(x, top_y, l - 1, wall_block)
    for z in range(l):
        if z % 2 == 0:
            new_grid.set_block(0, top_y, z, wall_block)
            new_grid.set_block(w - 1, top_y, z, wall_block)

    # Soul lanterns on corners
    new_grid.set_block(0, top_y + 1, 0, "minecraft:lantern")
    new_grid.set_block(w - 1, top_y + 1, l - 1, "minecraft:lantern")

    new_grid.recalculate_bounds()
    return new_grid

def add_overgrowth(grid: VoxelGrid) -> VoxelGrid:
    """Adds moss, hanging leaves, and subtle natural weathering."""
    new_grid = apply_theme(grid, "overgrown_ruins")
    new_grid.recalculate_bounds()
    w, h, l = new_grid.width, new_grid.height, new_grid.length

    # Add trailing leaves down edges
    for y in range(max(1, h - 3), h + 1):
        new_grid.set_block(0, y, 0, "minecraft:oak_leaves")
        if w > 2:
            new_grid.set_block(w - 1, y, 1, "minecraft:oak_leaves")
        if l > 2:
            new_grid.set_block(1, y, l - 1, "minecraft:oak_leaves")

    # Grass & flower accents on ground level
    for x in range(w):
        for z in range(l):
            if (x, 0, z) not in new_grid.voxels and (x % 3 == 0 and z % 3 == 0):
                new_grid.set_block(x, 0, z, "minecraft:moss_block")

    new_grid.recalculate_bounds()
    return new_grid

def generate_finished_products(base_grid: VoxelGrid) -> List[VariationOption]:
    """
    Generates diverse architectural finished product proposals based on the ingested voxel grid.
    """
    proposals: List[VariationOption] = []

    # 1. Medieval Timber & Stone Manor
    nordic_grid = add_medieval_roof(base_grid)
    proposals.append(VariationOption(
        id="proposal_medieval_manor",
        title="Medieval Timber & Gable Manor",
        description="Completed with pitched spruce timber roof, warm lantern lighting, and structured eaves.",
        category="structure",
        thumbnail_color="#a2824e",
        voxel_preview=nordic_grid.to_pydantic()
    ))

    # 2. Gothic Deepslate Fortress
    gothic_base = apply_theme(base_grid, "gothic_deepslate")
    gothic_fortress = add_crenellations_and_foundation(gothic_base, "minecraft:deepslate_bricks")
    proposals.append(VariationOption(
        id="proposal_gothic_deepslate",
        title="Gothic Deepslate Citadel",
        description="Reinforced deepslate brick battlements, defensive parapets, and soul lantern illumination.",
        category="theme",
        thumbnail_color="#46464b",
        voxel_preview=gothic_fortress.to_pydantic()
    ))

    # 3. Nether Crimson Fortress
    nether_base = apply_theme(base_grid, "ruined_nether")
    nether_fort = add_crenellations_and_foundation(nether_base, "minecraft:nether_bricks")
    proposals.append(VariationOption(
        id="proposal_nether_citadel",
        title="Nether Citadel",
        description="Forged with polished blackstone, nether bricks, crimson wood accents, and underworld flair.",
        category="theme",
        thumbnail_color="#653046",
        voxel_preview=nether_fort.to_pydantic()
    ))

    # 4. Desert Sun Palace
    desert_grid = apply_theme(base_grid, "desert_sandstone")
    proposals.append(VariationOption(
        id="proposal_desert_palace",
        title="Desert Sandstone Pavilion",
        description="Warm cut sandstone walls, chiseled hieroglyphic trim, terracotta accents, and sun terrace.",
        category="theme",
        thumbnail_color="#d8cb9b",
        voxel_preview=desert_grid.to_pydantic()
    ))

    # 5. Overgrown Ancient Relic
    overgrown_grid = add_overgrowth(base_grid)
    proposals.append(VariationOption(
        id="proposal_overgrown_ruin",
        title="Overgrown Relic",
        description="Weathered mossy stone masonry draped in wild foliage, vines, and reclaimed flora.",
        category="decor",
        thumbnail_color="#5c8c32",
        voxel_preview=overgrown_grid.to_pydantic()
    ))

    # 6. Modern Quartz & Glass Villa
    modern_grid = apply_theme(base_grid, "modern_quartz")
    proposals.append(VariationOption(
        id="proposal_modern_quartz",
        title="Modernist Quartz Villa",
        description="Pristine white quartz architecture with panoramic glass panels and recessed sea lanterns.",
        category="theme",
        thumbnail_color="#ebe5de",
        voxel_preview=modern_grid.to_pydantic()
    ))

    return proposals
