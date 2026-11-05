# Puts an "Asset Studio" shortcut on the desktop and in the Start menu, pointing at Asset Studio.exe in the project folder.
# Run once: powershell -NoProfile -ExecutionPolicy Bypass -File tools\assets\make_shortcuts.ps1
$root = Split-Path (Split-Path $PSScriptRoot)
$exe = Join-Path $root "Asset Studio.exe"
if (-not (Test-Path $exe)) { Write-Host "Asset Studio.exe not found in $root. Run build_app.bat first."; exit 1 }
$shell = New-Object -ComObject WScript.Shell
$places = @([Environment]::GetFolderPath("Desktop"), (Join-Path ([Environment]::GetFolderPath("Programs")) ""))
foreach ($place in $places) {
    $link = $shell.CreateShortcut((Join-Path $place "Asset Studio.lnk"))
    $link.TargetPath = $exe
    $link.WorkingDirectory = $root
    $link.IconLocation = "$exe,0"
    $link.Description = "Vestiges Asset Studio"
    $link.Save()
    Write-Host "Shortcut: $place\Asset Studio.lnk"
}
