from pydantic import BaseModel, Field
from typing import List, Dict, Optional, Any

class VoxelBlock(BaseModel):
    x: int
    y: int
    z: int
    block_id: str
    color_hex: Optional[str] = "#888888"

class VoxelBounds(BaseModel):
    width: int
    height: int
    length: int

class VoxelStats(BaseModel):
    total_blocks: int
    solid_blocks: int
    palette_counts: Dict[str, int]
    dimensions: VoxelBounds

class VoxelGridData(BaseModel):
    dimensions: VoxelBounds
    blocks: List[VoxelBlock]
    stats: VoxelStats

class IngestResponse(BaseModel):
    session_id: str
    source_type: str  # "image", "schematic", "url"
    filename: Optional[str] = None
    preview: VoxelGridData
    detected_style: Optional[str] = "voxel_3d"
    workflow_mode: Optional[str] = "analysis"
    target_preset: Optional[str] = "auto"
    ai_guess_explanation: Optional[str] = "Smart segmentation applied to isolate target subject."

class VariationOption(BaseModel):
    id: str
    title: str
    description: str
    category: str  # "theme", "structure", "decor"
    thumbnail_color: str
    voxel_preview: VoxelGridData

class FinishedProductsResponse(BaseModel):
    session_id: str
    base_voxel: VoxelGridData
    proposals: List[VariationOption]

class SaveToLibraryRequest(BaseModel):
    session_id: str
    title: str
    author: Optional[str] = "Architect"
    description: Optional[str] = ""
    tags: List[str] = []
    formats: List[str] = ["schem", "litematic", "nbt", "schematic"]
    voxel_data: Optional[VoxelGridData] = None

class LibraryItemSummary(BaseModel):
    id: str
    title: str
    author: str
    description: str
    tags: List[str]
    dimensions: VoxelBounds
    total_blocks: int
    created_at: str
    formats_available: List[str]

class UrlIngestRequest(BaseModel):
    url: str
    mode: Optional[str] = "auto"  # "heightmap", "pixel_art", "relief", "floorplan"
    workflow_mode: Optional[str] = "analysis"  # "analysis", "creation"
    target_preset: Optional[str] = "auto"  # "tree", "building", "decorations", "landscape", "auto"
