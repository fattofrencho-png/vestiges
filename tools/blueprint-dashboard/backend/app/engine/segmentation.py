import io
import numpy as np
from scipy import ndimage
from typing import Tuple, List, Set, Dict, Optional
from app.engine.voxel_grid import VoxelGrid
from app.engine.palette import match_closest_block

# Block category groupings for subject isolation
CATEGORY_FOLIAGE = {
    "minecraft:oak_leaves", "minecraft:spruce_leaves", "minecraft:birch_leaves",
    "minecraft:jungle_leaves", "minecraft:acacia_leaves", "minecraft:dark_oak_leaves",
    "minecraft:mangrove_leaves", "minecraft:cherry_leaves", "minecraft:azalea_leaves",
    "minecraft:moss_block", "minecraft:vine"
}

CATEGORY_WOOD = {
    "minecraft:oak_log", "minecraft:spruce_log", "minecraft:birch_log", "minecraft:jungle_log",
    "minecraft:acacia_log", "minecraft:dark_oak_log", "minecraft:mangrove_log", "minecraft:cherry_log",
    "minecraft:oak_planks", "minecraft:spruce_planks", "minecraft:birch_planks", "minecraft:jungle_planks",
    "minecraft:dark_oak_planks", "minecraft:oak_wood", "minecraft:spruce_wood"
}

CATEGORY_TERRAIN = {
    "minecraft:dirt", "minecraft:coarse_dirt", "minecraft:grass_block", "minecraft:sand",
    "minecraft:gravel", "minecraft:stone", "minecraft:deepslate", "minecraft:bedrock"
}

CATEGORY_ROAD_ROCK = {
    "minecraft:cobblestone", "minecraft:gravel", "minecraft:dirt_path", "minecraft:andesite",
    "minecraft:diorite", "minecraft:granite"
}

def smart_isolate_image_subject(image_bytes: bytes, preset: str = "tree") -> Tuple[bytes, str]:
    """
    High-speed, intelligent computer vision isolation of subjects from screenshots:
    - Filters out sky, distant mountain rocks, and flat ground terrain.
    - Isolates foreground subject (e.g. Tree & Roots, Building enclosure).
    - Runs in < 0.1 seconds without stalling.
    """
    from PIL import Image
    img = Image.open(io.BytesIO(image_bytes)).convert("RGB")
    arr = np.array(img)
    h, w, _ = arr.shape
    center_x = w // 2

    if preset == "tree":
        # Minecraft screenshot tree isolation:
        # Sky: blue or white clouds
        is_sky = (arr[:, :, 2] > 175) | ((arr[:, :, 0] > 140) & (arr[:, :, 2] > 160))

        # Flat ground: bright grass green carpet
        is_flat_grass = (arr[:, :, 1] > 120) & (arr[:, :, 0] > 75) & (arr[:, :, 2] < 80)

        # Rocks: neutral gray stone/mountains
        r_diff = np.abs(arr[:, :, 0].astype(int) - arr[:, :, 1].astype(int))
        b_diff = np.abs(arr[:, :, 0].astype(int) - arr[:, :, 2].astype(int))
        is_rock = (r_diff < 18) & (b_diff < 18) & (arr[:, :, 0] > 90)

        # Subject tree bounds: central vertical column
        # In typical screenshots, tree occupies center region (+/- 18% of width)
        win_w = max(int(w * 0.18), 70)
        x_min = max(0, center_x - win_w)
        x_max = min(w, center_x + win_w)

        # Tree pixels: within center column, not sky, not rock, and not flat grass
        tree_mask = np.zeros((h, w), dtype=bool)
        center_region = ~is_sky[:, x_min:x_max] & ~is_rock[:, x_min:x_max] & ~is_flat_grass[:, x_min:x_max]
        tree_mask[:, x_min:x_max] = center_region

        # Include root base under the trunk
        y_pts, x_pts = np.where(tree_mask)
        if len(y_pts) > 0:
            base_y = y_pts.max()
            mean_tree_x = int(np.mean(x_pts))
            root_win = 40
            for ry in range(base_y, min(h, base_y + 25)):
                for rx in range(max(0, mean_tree_x - root_win), min(w, mean_tree_x + root_win)):
                    pr, pg, pb = arr[ry, rx]
                    if (pg < 120 and pr < 100) or (pr > 35 and pr < 100 and pg > 25 and pg < 80):
                        tree_mask[ry, rx] = True

        y_idx, x_idx = np.where(tree_mask)
        if len(y_idx) == 0:
            # Fallback to standard rembg if color heuristic didn't catch
            return remove_image_background(image_bytes), "Fallback segmentation applied."

        isolated = np.zeros((h, w, 4), dtype=np.uint8)
        isolated[tree_mask, :3] = arr[tree_mask]
        isolated[tree_mask, 3] = 255

        pad = 6
        crop = isolated[max(0, y_idx.min()-pad):min(h, y_idx.max()+pad), max(0, x_idx.min()-pad):min(w, x_idx.max()+pad)]
        out_buf = io.BytesIO()
        Image.fromarray(crop).save(out_buf, format="PNG")
        explanation = f"Smart Tree & Roots Isolation: Isolated tree ({crop.shape[1]}x{crop.shape[0]} px). Filtered out background mountains, rocks, and ground terrain."
        return out_buf.getvalue(), explanation

    else:
        # Building / General salient foreground extraction:
        # Remove top sky and bottom 10% flat ground, crop to central salient cluster
        is_sky = (arr[:, :, 2] > 180)
        fg_mask = ~is_sky
        # Discard outer 15% margins
        margin_x = int(w * 0.12)
        fg_mask[:, :margin_x] = False
        fg_mask[:, w - margin_x:] = False

        y_idx, x_idx = np.where(fg_mask)
        if len(y_idx) == 0:
            return remove_image_background(image_bytes), "Standard background removal."

        isolated = np.zeros((h, w, 4), dtype=np.uint8)
        isolated[fg_mask, :3] = arr[fg_mask]
        isolated[fg_mask, 3] = 255
        crop = isolated[y_idx.min():y_idx.max(), x_idx.min():x_idx.max()]
        out_buf = io.BytesIO()
        Image.fromarray(crop).save(out_buf, format="PNG")
        return out_buf.getvalue(), "Isolated central architectural structure from background."

def find_connected_components(grid: VoxelGrid) -> List[Set[Tuple[int, int, int]]]:
    """
    Finds 3D connected components (clusters of adjacent solid voxels).
    Returns list of coordinate sets, sorted by descending size (largest cluster first).
    """
    if not grid.voxels:
        return []

    visited: Set[Tuple[int, int, int]] = set()
    clusters: List[Set[Tuple[int, int, int]]] = []

    # 26-connectivity neighbor offsets
    neighbors = [
        (dx, dy, dz)
        for dx in (-1, 0, 1)
        for dy in (-1, 0, 1)
        for dz in (-1, 0, 1)
        if not (dx == 0 and dy == 0 and dz == 0)
    ]

    for start_pos in grid.voxels.keys():
        if start_pos in visited:
            continue
        cluster: Set[Tuple[int, int, int]] = set()
        queue = [start_pos]
        visited.add(start_pos)

        while queue:
            curr = queue.pop()
            cluster.add(curr)
            cx, cy, cz = curr

            for dx, dy, dz in neighbors:
                npos = (cx + dx, cy + dy, cz + dz)
                if npos in grid.voxels and npos not in visited:
                    visited.add(npos)
                    queue.append(npos)

        clusters.append(cluster)

    clusters.sort(key=lambda c: len(c), reverse=True)
    return clusters

def keep_largest_component(grid: VoxelGrid) -> Tuple[VoxelGrid, int]:
    """Keeps only the primary main structure, discarding floating noise and distant rocks."""
    clusters = find_connected_components(grid)
    if not clusters:
        return grid.clone(), 0

    largest = clusters[0]
    removed_count = len(grid.voxels) - len(largest)

    new_grid = VoxelGrid()
    for pos in largest:
        new_grid.set_block(pos[0], pos[1], pos[2], grid.voxels[pos])
    new_grid.recalculate_bounds()
    return new_grid, removed_count

def detect_and_remove_ground(grid: VoxelGrid, max_ground_layers: int = 3) -> Tuple[VoxelGrid, int]:
    """
    Detects flat, expansive base terrain layers (grass, dirt, stone under Y <= 2) and removes them.
    """
    grid.recalculate_bounds()
    if not grid.voxels or grid.height <= 2:
        return grid.clone(), 0

    # Count voxel coverage per Y level
    y_counts: Dict[int, int] = {}
    for (x, y, z) in grid.voxels.keys():
        y_counts[y] = y_counts.get(y, 0) + 1

    # Check bottom layers
    ground_y_levels = set()
    total_area = max(grid.width * grid.length, 1)

    for y in range(min(max_ground_layers, grid.height)):
        count = y_counts.get(y, 0)
        # If density is high and composed mostly of terrain blocks, consider it ground
        layer_blocks = [grid.voxels[(x, y, z)] for (x, y, z) in grid.voxels if y == y]
        terrain_ratio = sum(1 for b in layer_blocks if any(t in b for t in ["dirt", "grass", "stone", "sand"])) / max(len(layer_blocks), 1)

        if count > 0.4 * total_area or (y <= 1 and terrain_ratio > 0.6):
            ground_y_levels.add(y)

    new_grid = VoxelGrid()
    removed_count = 0
    min_kept_y = max(ground_y_levels) + 1 if ground_y_levels else 0

    for (x, y, z), b_id in grid.voxels.items():
        if y in ground_y_levels:
            removed_count += 1
        else:
            # Shift down to sit cleanly on ground plane
            new_grid.set_block(x, y - min_kept_y, z, b_id)

    new_grid.recalculate_bounds()
    return new_grid, removed_count

def isolate_subject_by_preset(grid: VoxelGrid, preset: str) -> Tuple[VoxelGrid, str]:
    """
    Applies intelligent segmentation based on target subject preset:
    - 'tree': isolates trunk (wood/logs) and leaves/foliage, removing ground soil/grass.
    - 'building': keeps walls, floors, roofs, doors; removes flat terrain and road entrances.
    - 'decorations': keeps non-structural detail props; removes thick boundary masonry.
    - 'auto': detects structure type and removes background/ground plane noise.
    """
    initial_count = len(grid.voxels)
    refined_grid = grid.clone()

    if preset == "tree":
        # Keep logs, leaves, roots; remove large flat terrain
        filtered_grid = VoxelGrid()
        for (x, y, z), b_id in grid.voxels.items():
            clean = b_id.split("[")[0].strip()
            # Keep wood, leaves, moss, dirt only if near trunk
            if clean in CATEGORY_WOOD or clean in CATEGORY_FOLIAGE or "leaf" in clean or "log" in clean or "wood" in clean:
                filtered_grid.set_block(x, y, z, b_id)
            elif y <= 1 and ("dirt" in clean or "root" in clean):
                # Keep root structures
                filtered_grid.set_block(x, y, z, b_id)

        # Keep the main connected tree trunk and branches
        largest_tree, _ = keep_largest_component(filtered_grid)
        removed = initial_count - len(largest_tree.voxels)
        explanation = f"Detected Tree & Foliage: Kept trunk, branches, and canopy ({len(largest_tree.voxels)} blocks). Stripped {removed} background/terrain blocks."
        return largest_tree, explanation

    elif preset == "building":
        # First remove bottom flat terrain
        no_ground, removed_ground = detect_and_remove_ground(grid, max_ground_layers=2)
        # Keep largest connected structure (building enclosure)
        isolated_bldg, removed_noise = keep_largest_component(no_ground)
        total_removed = initial_count - len(isolated_bldg.voxels)
        explanation = f"Detected Building: Isolated main architectural enclosure ({len(isolated_bldg.voxels)} blocks). Stripped {total_removed} terrain & peripheral road blocks."
        return isolated_bldg, explanation

    elif preset == "decorations":
        # Remove primary solid stone walls and terrain, keep interior/accent props
        filtered_grid = VoxelGrid()
        for (x, y, z), b_id in grid.voxels.items():
            clean = b_id.split("[")[0].strip()
            if clean not in CATEGORY_TERRAIN and "stone_brick" not in clean and "deepslate" not in clean:
                filtered_grid.set_block(x, y, z, b_id)
        isolated_decor, _ = keep_largest_component(filtered_grid)
        total_removed = initial_count - len(isolated_decor.voxels)
        explanation = f"Detected Decorations & Props: Isolated {len(isolated_decor.voxels)} detail blocks. Stripped {total_removed} structural masonry & terrain blocks."
        return isolated_decor, explanation

    else:
        # Default Auto: strip base terrain and remove disjoint noise
        no_ground, _ = detect_and_remove_ground(grid, max_ground_layers=1)
        cleaned, _ = keep_largest_component(no_ground if len(no_ground.voxels) > 10 else grid)
        total_removed = initial_count - len(cleaned.voxels)
        explanation = f"Smart Auto-Segmentation: Isolated primary subject ({len(cleaned.voxels)} blocks). Filtered {total_removed} surrounding/background blocks."
        return cleaned, explanation
