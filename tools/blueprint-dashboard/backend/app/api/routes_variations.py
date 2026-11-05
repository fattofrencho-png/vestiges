from fastapi import APIRouter, HTTPException
from typing import Dict
from app.api.routes_ingest import SESSION_CACHE
from app.engine.variations import generate_finished_products
from app.models import FinishedProductsResponse, VariationOption

router = APIRouter(prefix="/api/variations", tags=["Variations"])

# Cache for generated proposals per session
PROPOSALS_CACHE: Dict[str, Dict[str, VariationOption]] = {}

@router.get("/{session_id}", response_model=FinishedProductsResponse)
async def get_variations(session_id: str):
    if session_id not in SESSION_CACHE:
        raise HTTPException(status_code=404, detail="Session not found")

    base_grid = SESSION_CACHE[session_id]
    proposals = generate_finished_products(base_grid)

    # Store in proposal cache for quick retrieval or selection
    PROPOSALS_CACHE[session_id] = {p.id: p for p in proposals}

    return FinishedProductsResponse(
        session_id=session_id,
        base_voxel=base_grid.to_pydantic(),
        proposals=proposals
    )

@router.post("/{session_id}/apply/{proposal_id}")
async def apply_proposal(session_id: str, proposal_id: str):
    """Replaces the active session grid with the selected proposal design."""
    if session_id not in SESSION_CACHE:
        raise HTTPException(status_code=404, detail="Session not found")
    if session_id not in PROPOSALS_CACHE or proposal_id not in PROPOSALS_CACHE[session_id]:
        raise HTTPException(status_code=404, detail="Proposal not found")

    chosen_proposal = PROPOSALS_CACHE[session_id][proposal_id]
    from app.engine.voxel_grid import VoxelGrid
    new_grid = VoxelGrid.from_pydantic(chosen_proposal.voxel_preview)
    SESSION_CACHE[session_id] = new_grid

    return {
        "status": "applied",
        "proposal_id": proposal_id,
        "preview": new_grid.to_pydantic()
    }
