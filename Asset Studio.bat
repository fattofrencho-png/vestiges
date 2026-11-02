@echo off
title Asset Studio
cd /d "%~dp0"
where python >nul 2>nul
if errorlevel 1 (
  echo Python was not found. Install it from python.org, then try again.
  pause
  exit /b 1
)
python -c "import anthropic, google.genai" >nul 2>nul
if errorlevel 1 (
  echo First run: installing what Asset Studio needs...
  python -m pip install --user -r tools\assets\requirements.txt
  if errorlevel 1 (
    echo The install failed. Check your internet connection and try again.
    pause
    exit /b 1
  )
)
set "MARK=%USERPROFILE%\.vestiges-assets\shortcut-asked"
if not exist "%MARK%" (
  if not exist "%USERPROFILE%\.vestiges-assets" mkdir "%USERPROFILE%\.vestiges-assets"
  echo asked> "%MARK%"
  choice /c YN /m "Add an Asset Studio shortcut to your desktop"
  if not errorlevel 2 powershell -NoProfile -Command "$s=(New-Object -ComObject WScript.Shell).CreateShortcut([Environment]::GetFolderPath('Desktop')+'\Asset Studio.lnk');$s.TargetPath='%~f0';$s.WorkingDirectory='%~dp0';$s.Save()"
)
python tools\assets\studio.py
if errorlevel 1 pause
