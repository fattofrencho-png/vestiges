from fastapi import APIRouter, HTTPException, Query
from typing import Optional
from app.models import VoxelGridData
from app.api.routes_ingest import SESSION_CACHE

router = APIRouter(prefix="/api/preview", tags=["Preview"])

@router.get("/{session_id}", response_model=VoxelGridData)
async def get_preview(session_id: str):
    if session_id not in SESSION_CACHE:
        raise HTTPException(status_code=404, detail="Session not found or expired")
    grid = SESSION_CACHE[session_id]
    return grid.to_pydantic()

@router.get("/{session_id}/slice")
async def get_slice(
    session_id: str,
    y: int = Query(..., description="Layer Y level to inspect")
):
    if session_id not in SESSION_CACHE:
        raise HTTPException(status_code=404, detail="Session not found")
    grid = SESSION_CACHE[session_id]
    
    slice_blocks = []
    for (bx, by, bz), b_id in grid.voxels.items():
        if by == y:
            slice_blocks.append({"x": bx, "y": by, "z": bz, "block_id": b_id})
            
    return {
        "y": y,
        "width": grid.width,
        "length": grid.length,
        "count": len(slice_blocks),
        "blocks": slice_blocks
    }
