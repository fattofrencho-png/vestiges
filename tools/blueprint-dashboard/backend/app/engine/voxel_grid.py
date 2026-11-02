from typing import Dict, Tuple, List, Optional
from app.models import VoxelBlock, VoxelGridData, VoxelBounds, VoxelStats
from app.engine.palette import get_block_color

class VoxelGrid:
    def __init__(self, width: int = 0, height: int = 0, length: int = 0):
        self.width = width
        self.height = height
        self.length = length
        # Coordinates (x, y, z) -> block_id
        self.voxels: Dict[Tuple[int, int, int], str] = {}

    def set_block(self, x: int, y: int, z: int, block_id: str):
        if block_id == "minecraft:air" or not block_id:
            if (x, y, z) in self.voxels:
                del self.voxels[(x, y, z)]
        else:
            self.voxels[(x, y, z)] = block_id
            self.width = max(self.width, x + 1)
            self.height = max(self.height, y + 1)
            self.length = max(self.length, z + 1)

    def get_block(self, x: int, y: int, z: int) -> str:
        return self.voxels.get((x, y, z), "minecraft:air")

    def recalculate_bounds(self):
        if not self.voxels:
            self.width = self.height = self.length = 0
            return
        max_x = max(pos[0] for pos in self.voxels.keys())
        max_y = max(pos[1] for pos in self.voxels.keys())
        max_z = max(pos[2] for pos in self.voxels.keys())
        self.width = max_x + 1
        self.height = max_y + 1
        self.length = max_z + 1

    def clone(self) -> 'VoxelGrid':
        copy_grid = VoxelGrid(self.width, self.height, self.length)
        copy_grid.voxels = dict(self.voxels)
        return copy_grid

    def to_pydantic(self, max_preview_blocks: int = 65536) -> VoxelGridData:
        self.recalculate_bounds()
        blocks: List[VoxelBlock] = []
        counts: Dict[str, int] = {}

        # Collect blocks
        for (x, y, z), block_id in self.voxels.items():
            counts[block_id] = counts.get(block_id, 0) + 1
            if len(blocks) < max_preview_blocks:
                blocks.append(VoxelBlock(
                    x=x, y=y, z=z,
                    block_id=block_id,
                    color_hex=get_block_color(block_id)
                ))

        total_voxels = len(self.voxels)
        stats = VoxelStats(
            total_blocks=self.width * self.height * self.length,
            solid_blocks=total_voxels,
            palette_counts=counts,
            dimensions=VoxelBounds(width=self.width, height=self.height, length=self.length)
        )

        return VoxelGridData(
            dimensions=VoxelBounds(width=self.width, height=self.height, length=self.length),
            blocks=blocks,
            stats=stats
        )

    @classmethod
    def from_pydantic(cls, data: VoxelGridData) -> 'VoxelGrid':
        grid = cls(data.dimensions.width, data.dimensions.height, data.dimensions.length)
        for b in data.blocks:
            grid.set_block(b.x, b.y, b.z, b.block_id)
        return grid
