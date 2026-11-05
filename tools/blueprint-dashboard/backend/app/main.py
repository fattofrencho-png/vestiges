from fastapi import FastAPI
from fastapi.middleware.cors import CORSMiddleware
from app.config import CORS_ORIGINS
from app.api.routes_ingest import router as ingest_router
from app.api.routes_preview import router as preview_router
from app.api.routes_variations import router as variations_router
from app.api.routes_library import router as library_router
from app.api.routes_refine import router as refine_router

app = FastAPI(
    title="Minecraft Blueprint Studio & Processing Engine",
    description="Drag-and-drop 3D Minecraft voxelizer, finished product variation engine, and schematic library",
    version="1.0.0"
)

app.add_middleware(
    CORSMiddleware,
    allow_origins=CORS_ORIGINS,
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)

app.include_router(ingest_router)
app.include_router(preview_router)
app.include_router(refine_router)
app.include_router(variations_router)
app.include_router(library_router)

@app.get("/")
async def root():
    return {
        "service": "Blueprint Studio Processing Engine",
        "status": "online",
        "supported_schematic_formats": ["schem", "litematic", "nbt", "schematic"],
        "supported_image_types": ["png", "jpg", "jpeg", "webp", "bmp"]
    }

if __name__ == "__main__":
    import uvicorn
    uvicorn.run("app.main:app", host="127.0.0.1", port=8000, reload=True)
