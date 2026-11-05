import io
from PIL import Image
from app.engine.image_voxelizer import voxelize_image
from app.engine.segmentation import (
    detect_and_remove_ground,
    keep_largest_component,
    isolate_subject_by_preset,
    find_connected_components
)
from app.engine.voxel_grid import VoxelGrid

def test_segmentation():
    print("=== Testing Segmentation & Subject Isolation ===")

    # 1. Create a voxel grid simulating a tree (trunk + leaves) on top of grass/dirt ground plane + 1 stray background rock
    grid = VoxelGrid()

    # Ground plane (dirt & grass at Y=0 and Y=1, size 16x16)
    for x in range(16):
        for z in range(16):
            grid.set_block(x, 0, z, "minecraft:dirt")
            grid.set_block(x, 1, z, "minecraft:grass_block")

    # Tree trunk (wood at center X=8, Z=8 from Y=2 to Y=7)
    for y in range(2, 8):
        grid.set_block(8, y, 8, "minecraft:oak_log")

    # Canopy (leaves from Y=6 to Y=9)
    for x in range(6, 11):
        for z in range(6, 11):
            for y in range(6, 9):
                grid.set_block(x, y, z, "minecraft:oak_leaves")

    # Stray background rock (disconnected at corner X=1, Y=3, Z=1)
    grid.set_block(1, 3, 1, "minecraft:cobblestone")
    grid.set_block(2, 3, 1, "minecraft:cobblestone")

    initial_count = len(grid.voxels)
    print(f"Initial raw scene: {initial_count} blocks (includes ground plane and stray rock)")

    # 2. Test Ground Removal
    no_ground, removed_ground = detect_and_remove_ground(grid, max_ground_layers=2)
    print(f"Ground removal: stripped {removed_ground} blocks. Remaining: {len(no_ground.voxels)}")
    assert removed_ground >= 256, "Ground layer should be removed"

    # 3. Test Discarding Disconnected Background Rock
    isolated_tree, removed_noise = keep_largest_component(no_ground)
    print(f"Connected component isolation: discarded {removed_noise} stray rock blocks. Remaining: {len(isolated_tree.voxels)}")
    # Verify stray rock at (1, Y, 1) is gone
    assert isolated_tree.get_block(1, 3, 1) == "minecraft:air", "Stray rock must be removed"
    assert isolated_tree.get_block(8, 0, 8) != "minecraft:air" or isolated_tree.get_block(8, 2, 8) != "minecraft:air", "Tree must remain"

    # 4. Test Preset: Isolate as Tree
    tree_result, explanation = isolate_subject_by_preset(grid, preset="tree")
    print(f"Preset isolation ('tree'): {explanation}")
    assert len(tree_result.voxels) > 0, "Tree should be isolated"
    # Ensure no grass/dirt remained in the isolated tree
    has_grass = any("grass" in b for b in tree_result.voxels.values())
    assert not has_grass, "Grass ground should not be in the isolated tree"

    print("ALL SEGMENTATION TESTS PASSED SUCCESSFULLY!")

if __name__ == "__main__":
    test_segmentation()
