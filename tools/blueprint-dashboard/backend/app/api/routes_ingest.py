import uuid
import requests
from fastapi import APIRouter, UploadFile, File, Form, HTTPException
from typing import Optional, Dict
from app.engine.voxel_grid import VoxelGrid
from app.engine.image_voxelizer import voxelize_image
from app.engine.schematic_reader import read_schematic
from app.models import IngestResponse, UrlIngestRequest

router = APIRouter(prefix="/api/ingest", tags=["Ingest"])

# In-memory session cache for active working grids
SESSION_CACHE: Dict[str, VoxelGrid] = {}

from app.engine.segmentation import isolate_subject_by_preset

@router.post("/file", response_model=IngestResponse)
async def ingest_file(
    file: UploadFile = File(...),
    mode: Optional[str] = Form("auto"),
    workflow_mode: Optional[str] = Form("analysis"),
    target_preset: Optional[str] = Form("auto")
):
    try:
        content = await file.read()
        filename = file.filename or "uploaded_file"
        lower_name = filename.lower()

        grid: VoxelGrid
        source_type: str

        if any(lower_name.endswith(ext) for ext in [".png", ".jpg", ".jpeg", ".webp", ".bmp"]):
            grid, detected_mode = voxelize_image(
                content,
                mode=mode or "auto",
                preset=target_preset or "auto",
                isolate_subject=(workflow_mode == "analysis" and target_preset != "landscape")
            )
            source_type = f"image_{detected_mode}"
        elif any(lower_name.endswith(ext) for ext in [".schem", ".litematic", ".nbt", ".schematic"]):
            grid, schem_fmt = read_schematic(content, filename)
            source_type = schem_fmt
        else:
            # Fallback
            try:
                grid, schem_fmt = read_schematic(content, filename)
                source_type = schem_fmt
            except Exception:
                grid, detected_mode = voxelize_image(content, mode="auto")
                source_type = f"image_{detected_mode}"

        explanation = f"Ingested raw {source_type}."
        # If in analysis mode, apply smart isolation
        if workflow_mode == "analysis" and target_preset:
            grid, explanation = isolate_subject_by_preset(grid, target_preset)

        session_id = str(uuid.uuid4())
        SESSION_CACHE[session_id] = grid

        return IngestResponse(
            session_id=session_id,
            source_type=source_type,
            filename=filename,
            preview=grid.to_pydantic(),
            detected_style="voxel_3d",
            workflow_mode=workflow_mode,
            target_preset=target_preset,
            ai_guess_explanation=explanation
        )
    except Exception as e:
        raise HTTPException(status_code=400, detail=f"Failed to process file: {str(e)}")

@router.post("/url", response_model=IngestResponse)
async def ingest_url(payload: UrlIngestRequest):
    try:
        headers = {"User-Agent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64)"}
        resp = requests.get(payload.url, headers=headers, timeout=12)
        if resp.status_code != 200:
            raise HTTPException(status_code=400, detail=f"HTTP fetch failed with code {resp.status_code}")

        content = resp.content
        url_lower = payload.url.lower()

        grid: VoxelGrid
        source_type: str

        if any(ext in url_lower for ext in [".schem", ".litematic", ".nbt", ".schematic"]):
            grid, schem_fmt = read_schematic(content, payload.url)
            source_type = schem_fmt
        else:
            # Assume image
            grid, detected_mode = voxelize_image(content, mode=payload.mode or "auto")
            source_type = f"image_{detected_mode}"

        session_id = str(uuid.uuid4())
        SESSION_CACHE[session_id] = grid

        return IngestResponse(
            session_id=session_id,
            source_type=source_type,
            filename=payload.url.split("/")[-1] or "web_resource",
            preview=grid.to_pydantic(),
            detected_style="voxel_3d"
        )
    except Exception as e:
        raise HTTPException(status_code=400, detail=f"Failed to ingest URL: {str(e)}")
