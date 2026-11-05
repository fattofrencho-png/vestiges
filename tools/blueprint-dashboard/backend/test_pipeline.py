import io
from PIL import Image
from app.engine.image_voxelizer import voxelize_image
from app.engine.schematic_reader import read_schematic
from app.engine.schematic_writer import (
    export_sponge_schem,
    export_litematica,
    export_vanilla_nbt,
    export_legacy_schematic
)
from app.engine.variations import generate_finished_products
from app.engine.library import save_schematic_to_library, list_library_items, get_schematic_file_path

def test_full_pipeline():
    print("=== 1. Testing Image Voxelizer ===")
    # Generate test image (16x16 with color gradient and dark center)
    img = Image.new("RGBA", (16, 16), (60, 120, 40, 255))
    for x in range(4, 12):
        for y in range(4, 12):
            img.putpixel((x, y), (130, 130, 130, 255))
    buf = io.BytesIO()
    img.save(buf, format="PNG")
    raw_png = buf.getvalue()

    grid, mode = voxelize_image(raw_png, mode="heightmap")
    print(f"Voxelized image ({mode}): Dimensions={grid.width}x{grid.height}x{grid.length}, Total blocks={len(grid.voxels)}")
    assert len(grid.voxels) > 0, "Grid should contain voxels"

    print("=== 2. Testing Variations Engine ===")
    proposals = generate_finished_products(grid)
    print(f"Generated {len(proposals)} proposals:")
    for p in proposals:
        print(f" - [{p.category}] {p.title} ({p.voxel_preview.stats.solid_blocks} blocks)")
    assert len(proposals) >= 4, "Should have at least 4 finished product proposals"

    print("=== 3. Testing Schematic Multi-Format Exporters ===")
    schem_bytes = export_sponge_schem(grid)
    print(f" - Sponge .schem bytes: {len(schem_bytes)}")
    assert len(schem_bytes) > 0

    litematic_bytes = export_litematica(grid, name="TestCastle")
    print(f" - Litematica .litematic bytes: {len(litematic_bytes)}")
    assert len(litematic_bytes) > 0

    nbt_bytes = export_vanilla_nbt(grid)
    print(f" - Vanilla .nbt bytes: {len(nbt_bytes)}")
    assert len(nbt_bytes) > 0

    legacy_bytes = export_legacy_schematic(grid)
    print(f" - Legacy .schematic bytes: {len(legacy_bytes)}")
    assert len(legacy_bytes) > 0

    print("=== 4. Testing Schematic Deserializers (Round-trip) ===")
    schem_grid, schem_fmt = read_schematic(schem_bytes, "test.schem")
    print(f" - Re-read Sponge .schem: {schem_fmt}, {len(schem_grid.voxels)} voxels")
    assert len(schem_grid.voxels) > 0

    nbt_grid, nbt_fmt = read_schematic(nbt_bytes, "test.nbt")
    print(f" - Re-read Vanilla .nbt: {nbt_fmt}, {len(nbt_grid.voxels)} voxels")
    assert len(nbt_grid.voxels) > 0

    lite_grid, lite_fmt = read_schematic(litematic_bytes, "test.litematic")
    print(f" - Re-read Litematica: {lite_fmt}, {len(lite_grid.voxels)} voxels")
    assert len(lite_grid.voxels) > 0

    print("=== 5. Testing Library Persistence & Vault ===")
    chosen_proposal = proposals[0]
    from app.engine.voxel_grid import VoxelGrid
    proposal_grid = VoxelGrid.from_pydantic(chosen_proposal.voxel_preview)

    item_id = save_schematic_to_library(
        grid=proposal_grid,
        title="Highland Timber Manor",
        author="Architect",
        description="Nordic style manor generated from raw heightmap with timber gable",
        tags=["nordic", "manor", "timber"],
        formats=["schem", "litematic", "nbt", "schematic"]
    )
    print(f"Saved to library with ID: {item_id}")

    items = list_library_items()
    print(f"Total items in library: {len(items)}")
    matched = [i for i in items if i.id == item_id]
    assert len(matched) == 1, "Item must exist in library"
    print(f"Verified library entry: {matched[0].title}, formats: {matched[0].formats_available}")

    for fmt in ["schem", "litematic", "nbt", "schematic"]:
        fpath = get_schematic_file_path(item_id, fmt)
        assert fpath is not None and fpath.exists(), f"File for {fmt} must exist on disk"
        print(f" - Format {fmt} on disk at: {fpath} ({fpath.stat().st_size} bytes)")

    print("\nALL PIPELINE TESTS PASSED SUCCESSFULLY!")

if __name__ == "__main__":
    test_full_pipeline()
