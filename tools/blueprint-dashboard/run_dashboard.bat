@echo off
echo Starting Minecraft Blueprint Studio...

start "Blueprint Studio Backend" cmd /k "cd /d %~dp0backend && python -m uvicorn app.main:app --host 127.0.0.1 --port 8000 --reload"
start "Blueprint Studio Frontend" cmd /k "cd /d %~dp0frontend && npm run dev"

echo Backend running on http://127.0.0.1:8000
echo Frontend running on http://localhost:5173
echo Press any key to exit this launcher window (servers will remain active in their windows).
pause >nul
