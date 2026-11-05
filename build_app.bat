@echo off
title Build Asset Studio
cd /d "%~dp0"
where python >nul 2>nul || (echo Python was not found. & goto :end)
python -c "import webview, PyInstaller, PIL, anthropic, google.genai" >nul 2>nul
if errorlevel 1 python -m pip install --user pywebview pyinstaller -r tools\assets\requirements.txt
python tools\assets\make_icon.py || goto :end
python -m PyInstaller --noconfirm --clean --onefile --windowed --name "Asset Studio" ^
  --icon "%~dp0tools\assets\icon.ico" --paths "%~dp0tools\assets" ^
  --add-data "%~dp0tools\assets\web;web" ^
  --distpath "%~dp0." --workpath "%~dp0build\app" --specpath "%~dp0build\app" ^
  "%~dp0tools\assets\app.py"
if errorlevel 1 (echo Build failed. & goto :end)
echo.
echo Done: Asset Studio.exe is in this folder. Keep it here: it finds the project next to itself.
:end
if not defined NOPAUSE pause
