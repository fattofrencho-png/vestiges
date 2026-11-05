# Minecraft Blueprint Studio & Processing Engine

Full-stack application for turning raw 2D images, blueprints, and 3D schematics into interactive 3D Minecraft voxel models, generating architectural finished products, and exporting them across all Minecraft schematic formats.

## Features

- **Ingestion Engine**:
  - Drag & drop PNG / JPG / WebP images (supports Heightmap / 3D Relief, Upright Pixel Art murals, and Floorplan wall extrusion).
  - Drag & drop Minecraft schematics: Sponge `.schem` (1.13+), Litematica `.litematic`, Vanilla Structure `.nbt`, and Legacy `.schematic` (1.12-).
  - Paste any web image or schematic URL for instant fetching.
- **3D Voxel Viewer**:
  - Interactive Three.js WebGL viewport with instanced voxel rendering.
  - Smooth orbit rotation, zoom, and panning.
  - Interactive Layer Slicer (inspect any horizontal Y cross-section).
  - Block statistics and palette breakdown.
- **Finished Product Proposal Engine**:
  - Automatically analyzes the ingested file and proposes finished variations:
    - **Medieval Timber & Gable Manor** (A-frame peaked roof, timber eaves, lanterns)
    - **Gothic Deepslate Citadel** (deepslate bricks, crenellations, soul lanterns)
    - **Nether Citadel** (blackstone, nether bricks, crimson wood)
    - **Desert Sandstone Pavilion** (cut sandstone, terracotta, sun terrace)
    - **Overgrown Relic** (mossy bricks, hanging vines, nature reclamation)
    - **Modernist Quartz Villa** (clean quartz, panorama glass, recessed sea lanterns)
  - 1-click 3D preview switching and variation acceptance.
- **Schematic Vault & Multi-Format Library**:
  - Exports agreed designs to `.schem`, `.litematic`, `.nbt`, and `.schematic`.
  - SQLite database tracking titles, author, descriptions, dimensions, and tags.
  - Instant direct download for all supported formats.
  - Reload saved library schematics back into the 3D studio anytime.

## Quick Start

### 1. Launch All Services (Windows)
Double-click `run_dashboard.bat` or run:
```bat
run_dashboard.bat
```

### 2. Manual Startup
**Backend**:
```bat
cd backend
python -m uvicorn app.main:app --host 127.0.0.1 --port 8000 --reload
```

**Frontend**:
```bat
cd frontend
npm run dev
```

Open your browser at `http://localhost:5173`.
