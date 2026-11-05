from fastapi import APIRouter, HTTPException
from pydantic import BaseModel
from typing import Optional, List, Dict
from app.api.routes_ingest import SESSION_CACHE
from app.engine.voxel_grid import VoxelGrid
from app.engine.segmentation import (
    isolate_subject_by_preset,
    detect_and_remove_ground,
    keep_largest_component,
    find_connected_components
)
from app.models import VoxelGridData

router = APIRouter(prefix="/api/refine", tags=["Refinement"])

# Session history stack for undo support: session_id -> List[VoxelGrid]
SESSION_HISTORY: Dict[str, List[VoxelGrid]] = {}

class RefineRequest(BaseModel):
    action: str  # "isolate_preset", "remove_ground", "keep_largest_cluster", "undo", "filter_blocks"
    preset: Optional[str] = "auto"  # "tree", "building", "decorations", "landscape", "auto"
    filter_category: Optional[str] = None  # "terrain", "foliage", "rock"
    feedback_note: Optional[str] = None

class RefineResponse(BaseModel):
    session_id: str
    action_taken: str
    ai_guess_explanation: str
    did_guess_right_question: str = "Did I guess right? You can confirm or ask me to refine."
    preview: VoxelGridData
    can_undo: bool
    suggested_actions: List[Dict[str, str]]

@router.post("/{session_id}", response_model=RefineResponse)
async def refine_session(session_id: str, payload: RefineRequest):
    if session_id not in SESSION_CACHE:
        raise HTTPException(status_code=404, detail="Session not found")

    current_grid = SESSION_CACHE[session_id]

    # Initialize history
    if session_id not in SESSION_HISTORY:
        SESSION_HISTORY[session_id] = []

    # Handle Undo
    if payload.action == "undo":
        if not SESSION_HISTORY[session_id]:
            raise HTTPException(status_code=400, detail="No previous states to undo")
        prev_grid = SESSION_HISTORY[session_id].pop()
        SESSION_CACHE[session_id] = prev_grid
        return RefineResponse(
            session_id=session_id,
            action_taken="undo",
            ai_guess_explanation="Reverted to previous voxel state.",
            preview=prev_grid.to_pydantic(),
            can_undo=len(SESSION_HISTORY[session_id]) > 0,
            suggested_actions=[
                {"label": "Remove Terrain / Ground", "action": "remove_ground"},
                {"label": "Keep Largest Cluster", "action": "keep_largest_cluster"}
            ]
        )

    # Save current grid to history before applying change
    SESSION_HISTORY[session_id].append(current_grid.clone())
    if len(SESSION_HISTORY[session_id]) > 10:
        SESSION_HISTORY[session_id].pop(0)

    explanation = "Refinement applied."
    new_grid = current_grid.clone()

    if payload.action == "isolate_preset":
        new_grid, explanation = isolate_subject_by_preset(current_grid, payload.preset or "auto")

    elif payload.action == "remove_ground":
        new_grid, removed = detect_and_remove_ground(current_grid, max_ground_layers=2)
        explanation = f"Removed {removed} bottom ground / terrain blocks. Structure now rests directly at Y=0."

    elif payload.action == "keep_largest_cluster":
        new_grid, removed = keep_largest_component(current_grid)
        explanation = f"Isolated the primary contiguous structure. Discarded {removed} disconnected background rocks and noise."

    elif payload.action == "filter_blocks":
        cat = (payload.filter_category or "").lower()
        filtered = VoxelGrid()
        removed = 0
        for (x, y, z), b_id in current_grid.voxels.items():
            clean = b_id.split("[")[0].strip()
            drop = False
            if cat == "terrain" and any(k in clean for k in ["dirt", "grass", "stone", "gravel"]):
                drop = True
            elif cat == "foliage" and any(k in clean for k in ["leaf", "leaves", "vine", "flower"]):
                drop = True
            elif cat == "rock" and any(k in clean for k in ["cobble", "diorite", "andesite", "granite"]):
                drop = True

            if drop:
                removed += 1
            else:
                filtered.set_block(x, y, z, b_id)
        filtered.recalculate_bounds()
        new_grid = filtered
        explanation = f"Filtered {removed} '{cat}' blocks from the model."

    SESSION_CACHE[session_id] = new_grid

    # Suggested actions based on current grid state
    clusters = find_connected_components(new_grid)
    suggested = []
    if len(clusters) > 1:
        suggested.append({"label": f"Discard {len(clusters)-1} Disconnected Fragments", "action": "keep_largest_cluster"})
    suggested.append({"label": "Strip Base Ground Plane", "action": "remove_ground"})
    suggested.append({"label": "Isolate as Tree & Canopy", "action": "isolate_preset", "preset": "tree"})
    suggested.append({"label": "Isolate as Building Enclosure", "action": "isolate_preset", "preset": "building"})

    return RefineResponse(
        session_id=session_id,
        action_taken=payload.action,
        ai_guess_explanation=explanation,
        preview=new_grid.to_pydantic(),
        can_undo=len(SESSION_HISTORY[session_id]) > 0,
        suggested_actions=suggested
    )
