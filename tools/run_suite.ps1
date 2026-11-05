# Runs automatic suites (src/gametest/.../AutoCheck.java) in the real game under a time limit, stops the game once it has finished
# (the test tool stalls when the world closes), then prints the reports and the Vestiges lines of the game log.
#   powershell -File tools\run_suite.ps1 sweep            one suite
#   powershell -File tools\run_suite.ps1 sweep,map        several suites in one world
# Optional second argument: seconds before giving up (default 280, the 5-minute rule).
param(
	[Parameter(Mandatory = $true)][string]$Suites,
	[int]$Limit = 280
)

$root = Split-Path -Parent $PSScriptRoot
Set-Location $root
$jdk = Get-ChildItem "$env:USERPROFILE\.jdks" -Directory -Filter "jdk-25*" | Select-Object -First 1
if (-not $jdk) { Write-Host "No JDK 25 in $env:USERPROFILE\.jdks"; exit 1 }
$env:JAVA_HOME = $jdk.FullName
$env:VESTIGES_SUITE = $Suites

$run = Join-Path $root "build\run\clientGameTest"
New-Item -ItemType Directory -Force $run | Out-Null
Remove-Item "$run\autocheck-finished.txt", "$run\report-*.md" -ErrorAction SilentlyContinue
$out = Join-Path $root "build\suite-gradle.log"

$p = Start-Process -FilePath "$root\gradlew.bat" -ArgumentList "runClientGameTest", "--no-daemon" -WorkingDirectory $root `
	-RedirectStandardOutput $out -RedirectStandardError "$out.err" -WindowStyle Hidden -PassThru
$start = Get-Date
$finished = $false
while (((Get-Date) - $start).TotalSeconds -lt $Limit) {
	if (Test-Path "$run\autocheck-finished.txt") { $finished = $true; break }
	if ($p.HasExited) { break }
	Start-Sleep -Seconds 3
}
$secs = [int]((Get-Date) - $start).TotalSeconds
# Stop the game and everything Gradle started for it.
& taskkill /T /F /PID $p.Id | Out-Null
# Only processes of THIS folder: other chats run games and builds from their own worktrees at the same time, and a global sweep would kill theirs.
# ($root is also the start of a worktree's path, so the folder must be followed by build, gradle or .gradle, not by .claude.)
$mine = [regex]::Escape($root) + '\\+(build|gradle|\.gradle)\b'
Get-CimInstance Win32_Process -Filter "Name='java.exe'" | Where-Object { $_.CommandLine -match $mine -and $_.CommandLine -match "clientGameTest|GradleDaemon|GradleWrapperMain|gradle-wrapper" } |
	ForEach-Object { Stop-Process -Id $_.ProcessId -Force -ErrorAction SilentlyContinue }

Write-Host "== finished: $finished after $secs s (limit $Limit s) =="
Get-ChildItem "$run\report-*.md" -ErrorAction SilentlyContinue | ForEach-Object {
	Write-Host "== $($_.Name) (full report: $($_.FullName)) =="
	Get-Content $_.FullName -Encoding UTF8 | Where-Object { $_ -match "FAIL|^- note|passed" }
}
$log = Join-Path $run "logs\latest.log"
if (Test-Path $log) {
	Write-Host "== game log: Vestiges problems =="
	Select-String -Path $log -Pattern "ERROR|WARN|Exception|unknown attachment|Failed|Couldn't|Could not" |
		Where-Object { $_.Line -match "vestiges|Vestiges|attachment|loot|Exception" -and $_.Line -notmatch "oshi|authlib|Realms|jna|Win32Exception|still running|FormattedException|^\s+at " } | ForEach-Object { $_.Line } | Select-Object -First 40
}
if (-not $finished) {
	Write-Host "== did not finish; last gradle lines =="
	Get-Content $out -Tail 25 -ErrorAction SilentlyContinue
	Get-Content "$out.err" -Tail 15 -ErrorAction SilentlyContinue
}
