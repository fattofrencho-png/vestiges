from fastapi import APIRouter, HTTPException, Query
from fastapi.responses import FileResponse
from typing import Optional, List
from app.api.routes_ingest import SESSION_CACHE
from app.engine.voxel_grid import VoxelGrid
from app.engine.library import (
    save_schematic_to_library,
    list_library_items,
    get_library_item_voxel,
    get_schematic_file_path
)
from app.models import SaveToLibraryRequest, LibraryItemSummary

router = APIRouter(prefix="/api/library", tags=["Library"])

@router.get("", response_model=List[LibraryItemSummary])
async def get_library(
    tag: Optional[str] = Query(None),
    search: Optional[str] = Query(None)
):
    return list_library_items(tag_filter=tag, search=search)

@router.post("/save")
async def save_to_library(payload: SaveToLibraryRequest):
    grid: VoxelGrid
    if payload.voxel_data:
        grid = VoxelGrid.from_pydantic(payload.voxel_data)
    elif payload.session_id in SESSION_CACHE:
        grid = SESSION_CACHE[payload.session_id]
    else:
        raise HTTPException(status_code=400, detail="No active session or voxel data provided")

    item_id = save_schematic_to_library(
        grid=grid,
        title=payload.title,
        author=payload.author or "Architect",
        description=payload.description or "",
        tags=payload.tags,
        formats=payload.formats
    )

    return {
        "status": "success",
        "item_id": item_id,
        "title": payload.title,
        "message": f"Saved {payload.title} to schematic library"
    }

@router.get("/{item_id}")
async def get_item(item_id: str):
    voxel_json = get_library_item_voxel(item_id)
    if not voxel_json:
        raise HTTPException(status_code=404, detail="Library item not found")
    return voxel_json

@router.get("/{item_id}/export/{format_type}")
async def export_format(item_id: str, format_type: str):
    file_path = get_schematic_file_path(item_id, format_type)
    if not file_path or not file_path.exists():
        raise HTTPException(status_code=404, detail=f"Export format {format_type} not found for item {item_id}")

    filename = f"{item_id}.{format_type}"
    return FileResponse(
        path=str(file_path),
        filename=filename,
        media_type="application/octet-stream"
    )
