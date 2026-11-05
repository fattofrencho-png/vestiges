# Runs every automatic check in one go and writes one report: build\run\all-report.md.
#   powershell -File tools\run_all.ps1                     unit tests, then every real-game suite
#   powershell -File tools\run_all.ps1 -Only core,dungeons  only those batches (names below)
#   powershell -File tools\run_all.ps1 -SkipUnit           skip the unit tests
#   powershell -File tools\run_all.ps1 -NoRetry            do not rerun a failed batch once
# Each batch is one game run under run_suite.ps1's 5-minute limit (muted, the game is stopped by the script). A batch that fails is rerun once: if the
# rerun passes, it is reported as "flaky" (a timing problem in a check, not a lasting failure) and still counts as passed. The whole run takes about 15 minutes.
# Exit code: 0 when everything passed, 1 otherwise.
param(
	[string[]]$Only = @(),
	[switch]$SkipUnit,
	[switch]$NoRetry
)

$root = Split-Path -Parent $PSScriptRoot
Set-Location $root
$runDir = Join-Path $root "build\run\clientGameTest"
$outDir = Join-Path $root "build\run\all"
New-Item -ItemType Directory -Force $outDir | Out-Null

# One batch = one game run (and one world). Atlas checks need a world of their own each.
$batches = [ordered]@{
	"core"       = @("sweep", "quests", "persistence", "settlements", "codex")
	"gameplay"   = @("inventory", "map", "levels", "mobs", "loot")
	"structures" = @("structures", "structures2")
	"villagers"  = @("villagers", "grave")
	"dungeons"   = @("dungeons")
	"themes"     = @("dungeons2")
	"dragons"    = @("dragons")
	"dragons-lost" = @("dragons2")
	"dragons-moves" = @("dragons3")
	"atlas-vanilla"  = @("atlas-vanilla")
	"atlas-vestiges" = @("atlas-vestiges")
}
if ($Only.Count -gt 0) {
	$picked = [ordered]@{}
	foreach ($n in $Only) {
		if (-not $batches.Contains($n)) { Write-Host "No batch called '$n'. Batches: $($batches.Keys -join ', ')"; exit 2 }
		$picked[$n] = $batches[$n]
	}
	$batches = $picked
}

$started = Get-Date
$rows = New-Object System.Collections.ArrayList
$failures = New-Object System.Collections.ArrayList

function Read-Report($suite) {
	$f = Join-Path $runDir "report-$suite.md"
	if (-not (Test-Path $f)) { return $null }
	$text = Get-Content $f -Encoding UTF8
	$sum = $text | Where-Object { $_ -match "\*\*(\d+) passed, (\d+) failed" } | Select-Object -Last 1
	if (-not $sum) { return $null }
	$null = $sum -match "\*\*(\d+) passed, (\d+) failed"
	return [pscustomobject]@{ Passed = [int]$Matches[1]; Failed = [int]$Matches[2]; Fails = @($text | Where-Object { $_ -match "^- \*\*FAIL\*\*" }) }
}

# --- unit tests
if (-not $SkipUnit) {
	Write-Host "== unit tests"
	$jdk = Get-ChildItem "$env:USERPROFILE\.jdks" -Directory -Filter "jdk-25*" | Select-Object -First 1
	if (-not $jdk) { Write-Host "No JDK 25 in $env:USERPROFILE\.jdks"; exit 1 }
	$env:JAVA_HOME = $jdk.FullName
	$t0 = Get-Date
	& "$root\gradlew.bat" test --offline -q 2>&1 | Out-Null
	$tests = 0; $bad = 0
	Get-ChildItem "$root\build\test-results\test\*.xml" -ErrorAction SilentlyContinue | ForEach-Object {
		$x = [xml](Get-Content $_.FullName -Encoding UTF8)
		$tests += [int]$x.testsuite.tests
		$bad += [int]$x.testsuite.failures + [int]$x.testsuite.errors
		foreach ($tc in $x.testsuite.testcase) { if ($tc.failure -or $tc.error) { [void]$failures.Add("unit: $($_.BaseName) / $($tc.name)") } }
	}
	$secs = [int]((Get-Date) - $t0).TotalSeconds
	$status = if ($bad -eq 0 -and $tests -gt 0) { "pass" } else { "FAIL" }
	[void]$rows.Add([pscustomobject]@{ Name = "unit tests"; Passed = $tests - $bad; Failed = $bad; Status = $status; Secs = $secs })
	Write-Host "   $($tests - $bad) of $tests passed ($status)"
}

# --- real-game batches
foreach ($name in $batches.Keys) {
	$suites = $batches[$name]
	$flaky = $false
	$attempts = if ($NoRetry) { 1 } else { 2 }
	$final = $null
	$t0 = Get-Date
	for ($try = 1; $try -le $attempts; $try++) {
		Write-Host "== $name ($($suites -join ',')) attempt $try"
		& powershell -NoProfile -File "$root\tools\run_suite.ps1" ($suites -join ",") 2>&1 | Out-Null
		$results = @{}
		foreach ($s in $suites) { $results[$s] = Read-Report $s }
		$batchBad = @($suites | Where-Object { $null -eq $results[$_] -or $results[$_].Failed -gt 0 })
		New-Item -ItemType Directory -Force (Join-Path $outDir $name) | Out-Null
		Get-ChildItem "$runDir\report-*.md" -ErrorAction SilentlyContinue | Copy-Item -Destination (Join-Path $outDir $name) -Force
		Get-ChildItem "$runDir\screenshots\*.png" -ErrorAction SilentlyContinue | Copy-Item -Destination (Join-Path $outDir $name) -Force
		$final = $results
		if ($batchBad.Count -eq 0) {
			if ($try -gt 1) { $flaky = $true }
			break
		}
		Write-Host "   not clean: $($batchBad -join ', ')"
	}
	$secs = [int]((Get-Date) - $t0).TotalSeconds
	foreach ($s in $suites) {
		$r = $final[$s]
		if ($null -eq $r) {
			[void]$rows.Add([pscustomobject]@{ Name = $s; Passed = 0; Failed = 0; Status = "NO REPORT"; Secs = $secs })
			[void]$failures.Add("${s}: no report was written (the game did not finish in time or crashed)")
		} else {
			$status = if ($r.Failed -gt 0) { "FAIL" } elseif ($flaky) { "pass (flaky: passed on rerun)" } else { "pass" }
			[void]$rows.Add([pscustomobject]@{ Name = $s; Passed = $r.Passed; Failed = $r.Failed; Status = $status; Secs = $secs })
			foreach ($f in $r.Fails) { [void]$failures.Add("${s}: $f") }
		}
		Write-Host "   ${s}: $(if ($r) { "$($r.Passed) passed, $($r.Failed) failed" } else { 'no report' })"
	}
}

# --- the report
$total = [int]((Get-Date) - $started).TotalSeconds
$allPassed = ($rows | Where-Object { $_.Status -match "FAIL|NO REPORT" }).Count -eq 0
$sumPassed = ($rows | Measure-Object -Property Passed -Sum).Sum
$sumFailed = ($rows | Measure-Object -Property Failed -Sum).Sum
$lines = New-Object System.Collections.ArrayList
[void]$lines.Add("# Vestiges: all automatic checks")
[void]$lines.Add("")
[void]$lines.Add("Run $(Get-Date -Format 'yyyy-MM-dd HH:mm') on commit $(git rev-parse --short HEAD), $([int]($total / 60)) min $($total % 60) s.")
[void]$lines.Add("")
[void]$lines.Add("**$(if ($allPassed) { 'ALL PASSED' } else { 'FAILURES' }): $sumPassed passed, $sumFailed failed.**")
[void]$lines.Add("")
[void]$lines.Add("| Check | Passed | Failed | Status |")
[void]$lines.Add("|---|---:|---:|---|")
foreach ($r in $rows) { [void]$lines.Add("| $($r.Name) | $($r.Passed) | $($r.Failed) | $($r.Status) |") }
if ($failures.Count -gt 0) {
	[void]$lines.Add("")
	[void]$lines.Add("## Failures")
	foreach ($f in $failures) { [void]$lines.Add("- $f") }
}
[void]$lines.Add("")
[void]$lines.Add("Full reports and screenshots per batch: build\run\all\<batch>\")
$reportPath = Join-Path $root "build\run\all-report.md"
Set-Content -Path $reportPath -Value $lines -Encoding UTF8
Write-Host ""
$lines | ForEach-Object { Write-Host $_ }
Write-Host ""
Write-Host "Report: $reportPath"
if ($allPassed) { exit 0 } else { exit 1 }
