import sqlite3
import json
import time
import uuid
from typing import List, Optional, Dict
from pathlib import Path
from app.config import DB_PATH, LIBRARY_DIR
from app.engine.voxel_grid import VoxelGrid
from app.engine.schematic_writer import serialize_schematic
from app.models import LibraryItemSummary, VoxelBounds

def init_db():
    conn = sqlite3.connect(DB_PATH)
    cursor = conn.cursor()
    cursor.execute("""
        CREATE TABLE IF NOT EXISTS library_items (
            id TEXT PRIMARY KEY,
            title TEXT NOT NULL,
            author TEXT NOT NULL,
            description TEXT,
            tags TEXT,
            dim_w INTEGER,
            dim_h INTEGER,
            dim_l INTEGER,
            total_blocks INTEGER,
            created_at TEXT,
            voxel_json TEXT
        )
    """)
    conn.commit()
    conn.close()

# Initialize DB on import
init_db()

def save_schematic_to_library(
    grid: VoxelGrid,
    title: str,
    author: str = "Architect",
    description: str = "",
    tags: List[str] = None,
    formats: List[str] = None
) -> str:
    grid.recalculate_bounds()
    item_id = str(uuid.uuid4())[:8]
    item_dir = LIBRARY_DIR / item_id
    item_dir.mkdir(parents=True, exist_ok=True)

    if tags is None:
        tags = ["custom"]
    if formats is None:
        formats = ["schem", "litematic", "nbt", "schematic"]

    # Serialize and save all requested schematic formats to disk
    for fmt in formats:
        file_bytes = serialize_schematic(grid, fmt, name=title)
        ext = "schem" if fmt == "schem" else ("litematic" if fmt == "litematic" else ("nbt" if fmt == "nbt" else "schematic"))
        file_path = item_dir / f"model.{ext}"
        file_path.write_bytes(file_bytes)

    # Save to SQLite
    created_at = time.strftime("%Y-%m-%d %H:%M:%S")
    voxel_pydantic = grid.to_pydantic()
    voxel_json = voxel_pydantic.model_dump_json()

    conn = sqlite3.connect(DB_PATH)
    cursor = conn.cursor()
    cursor.execute("""
        INSERT INTO library_items (id, title, author, description, tags, dim_w, dim_h, dim_l, total_blocks, created_at, voxel_json)
        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
    """, (
        item_id,
        title,
        author,
        description,
        json.dumps(tags),
        grid.width,
        grid.height,
        grid.length,
        len(grid.voxels),
        created_at,
        voxel_json
    ))
    conn.commit()
    conn.close()

    return item_id

def list_library_items(tag_filter: Optional[str] = None, search: Optional[str] = None) -> List[LibraryItemSummary]:
    conn = sqlite3.connect(DB_PATH)
    cursor = conn.cursor()
    cursor.execute("SELECT id, title, author, description, tags, dim_w, dim_h, dim_l, total_blocks, created_at FROM library_items ORDER BY created_at DESC")
    rows = cursor.fetchall()
    conn.close()

    items: List[LibraryItemSummary] = []
    for row in rows:
        item_id, title, author, desc, tags_str, w, h, l, total_blocks, created_at = row
        tags = json.loads(tags_str) if tags_str else []

        if tag_filter and tag_filter.lower() not in [t.lower() for t in tags]:
            continue
        if search and search.lower() not in title.lower() and search.lower() not in desc.lower():
            continue

        # Check available files in directory
        item_dir = LIBRARY_DIR / item_id
        formats = []
        if item_dir.exists():
            for f in item_dir.glob("model.*"):
                formats.append(f.suffix.lstrip("."))

        items.append(LibraryItemSummary(
            id=item_id,
            title=title,
            author=author,
            description=desc or "",
            tags=tags,
            dimensions=VoxelBounds(width=w, height=h, length=l),
            total_blocks=total_blocks,
            created_at=created_at,
            formats_available=formats
        ))

    return items

def get_library_item_voxel(item_id: str) -> Optional[dict]:
    conn = sqlite3.connect(DB_PATH)
    cursor = conn.cursor()
    cursor.execute("SELECT voxel_json FROM library_items WHERE id = ?", (item_id,))
    row = cursor.fetchone()
    conn.close()
    if row and row[0]:
        return json.loads(row[0])
    return None

def get_schematic_file_path(item_id: str, format_type: str) -> Optional[Path]:
    item_dir = LIBRARY_DIR / item_id
    ext = format_type.lstrip(".").lower()
    candidate = item_dir / f"model.{ext}"
    if candidate.exists():
        return candidate
    return None
