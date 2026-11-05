from pathlib import Path

BASE_DIR = Path(__file__).resolve().parent.parent
STORAGE_DIR = BASE_DIR / "storage"
LIBRARY_DIR = STORAGE_DIR / "library"
UPLOADS_DIR = STORAGE_DIR / "uploads"
THUMBNAILS_DIR = STORAGE_DIR / "thumbnails"

for folder in [STORAGE_DIR, LIBRARY_DIR, UPLOADS_DIR, THUMBNAILS_DIR]:
    folder.mkdir(parents=True, exist_ok=True)

DB_PATH = STORAGE_DIR / "library.db"

CORS_ORIGINS = [
    "http://localhost:5173",
    "http://127.0.0.1:5173",
    "http://localhost:3000",
    "http://127.0.0.1:3000",
    "*"
]
