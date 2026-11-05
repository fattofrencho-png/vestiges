import io
from PIL import Image
from typing import Tuple, Optional
from app.engine.voxel_grid import VoxelGrid
from app.engine.palette import match_closest_block

def voxelize_image(
    image_bytes: bytes,
    mode: str = "auto",
    preset: str = "auto",
    isolate_subject: bool = True,
    max_dimension: int = 64,
    max_height: int = 32
) -> Tuple[VoxelGrid, str]:
    """
    Converts raw image bytes into a 3D Minecraft voxel grid with smart subject isolation.
    """
    # If isolating subject and image is not already transparent, use rembg
    if isolate_subject:
        from app.engine.segmentation import remove_image_background
        try:
            image_bytes = remove_image_background(image_bytes)
        except Exception:
            pass

    img = Image.open(io.BytesIO(image_bytes))
    img = img.convert("RGBA")

    # Resize while maintaining aspect ratio to fit max_dimension
    w, h = img.size
    scale = min(max_dimension / max(w, 1), max_dimension / max(h, 1), 1.0)
    target_w = max(1, int(w * scale))
    target_h = max(1, int(h * scale))
    img = img.resize((target_w, target_h), Image.Resampling.NEAREST)

    # Mode auto-detection
    if mode == "auto":
        # Check aspect ratio and alpha transparency
        has_transparency = any(img.getpixel((x, y))[3] < 128 for x in range(target_w) for y in range(target_h))
        if has_transparency:
            mode = "pixel_art"
        else:
            mode = "heightmap"

    grid = VoxelGrid()

    if mode == "pixel_art":
        # Render as an upright 3D pixel art mural with framed base
        for x in range(target_w):
            for y_img in range(target_h):
                r, g, b, a = img.getpixel((x, y_img))
                if a < 100:
                    continue
                # Invert Y so top of image is top of 3D voxel
                y_3d = target_h - 1 - y_img
                block_id, _ = match_closest_block((r, g, b))
                # Add 2 blocks thickness in Z for 3D presence
                grid.set_block(x, y_3d + 1, 0, block_id)
                grid.set_block(x, y_3d + 1, 1, block_id)

        # Add a clean base pedestal
        for x in range(target_w):
            grid.set_block(x, 0, 0, "minecraft:stone_bricks")
            grid.set_block(x, 0, 1, "minecraft:stone_bricks")

    elif mode == "floorplan":
        # Blueprint/Floorplan: darker lines become walls, lighter become floors
        wall_height = min(6, max_height)
        for x in range(target_w):
            for z in range(target_h):
                r, g, b, a = img.getpixel((x, z))
                if a < 50:
                    continue
                brightness = (r * 0.299 + g * 0.587 + b * 0.114)
                
                # Base floor for all valid pixels
                grid.set_block(x, 0, z, "minecraft:oak_planks")

                # Dark pixels (< 110) are walls
                if brightness < 110:
                    wall_block, _ = match_closest_block((r, g, b))
                    if wall_block == "minecraft:black_wool" or "wool" in wall_block:
                        wall_block = "minecraft:stone_bricks"
                    for y in range(1, wall_height + 1):
                        grid.set_block(x, y, z, wall_block)

    else:
        # Default: Heightmap / 3D Relief
        for x in range(target_w):
            for z in range(target_h):
                r, g, b, a = img.getpixel((x, z))
                if a < 50:
                    continue
                brightness = (r * 0.299 + g * 0.587 + b * 0.114) / 255.0
                voxel_height = max(1, int(brightness * max_height))
                block_id, _ = match_closest_block((r, g, b))

                # Build solid vertical column up to voxel_height
                for y in range(voxel_height):
                    if y == voxel_height - 1:
                        grid.set_block(x, y, z, block_id)
                    elif y > voxel_height - 3:
                        grid.set_block(x, y, z, "minecraft:dirt" if "grass" in block_id else block_id)
                    else:
                        grid.set_block(x, y, z, "minecraft:stone")

    grid.recalculate_bounds()
    return grid, mode
