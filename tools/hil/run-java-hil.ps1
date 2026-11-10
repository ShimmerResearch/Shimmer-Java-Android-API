<#
.SYNOPSIS
Runs the Java driver's hardware-in-the-loop (HIL) scenarios on every Shimmer in a rig file, over
the PC's paired Bluetooth serial ports, and prints a pass/fail table. Exits 1 if anything did not pass.

.DESCRIPTION
One-time setup is in tools/hil/README.md: pair each unit with this PC, list it in
tools/hil/rig-java.json (copy rig-java.example.json), and check out the repository in a folder named
Shimmer-Java-Android-API (ShimmerDriverPC's settings.gradle finds its siblings by that name).

.EXAMPLE
.\tools\hil\run-java-hil.ps1
Every scenario on every unit.

.EXAMPLE
.\tools\hil\run-java-hil.ps1 -Unit s3r-86f8 -Scenario inStream

.EXAMPLE
.\tools\hil\run-java-hil.ps1 -Report
Before "Release Java API": the full run on this commit, recorded on GitHub as the commit status
"java-hil". The release refuses to publish a commit without a passing one.
#>
param(
    [string]$Rig = (Join-Path $PSScriptRoot 'rig-java.json'),
    [ValidateSet('streamRest', 'midStreamSet', 'reconnectLoop', 'inStream', 'queueStress', 'rollover', 'idleStart', 'samplingRates')]
    [string[]]$Scenario = @('streamRest', 'midStreamSet', 'reconnectLoop', 'inStream', 'queueStress', 'rollover', 'idleStart', 'samplingRates'),
    [string[]]$Unit,
    [double]$Rate = 256,
    [int]$Seconds = 30,
    # Link CRC to ask for; by default the driver's own default, as an app gets it.
    [ValidateSet('off', '1', '2')]
    [string]$Crc,
    [double]$MaxLossPercent = 1.0,
    [switch]$SkipBuild,
    [switch]$Report,
    [int]$ScenarioTimeoutSeconds = 900,
    # Pause between runs, so the unit has dropped the last link before the next connect.
    [int]$SettleSeconds = 10
)

$ErrorActionPreference = 'Continue'
$repo = (Resolve-Path (Join-Path $PSScriptRoot '..\..')).Path
$gradleRoot = Join-Path $repo 'ShimmerDriverPC'
$testClass = 'com.shimmerresearch.hil.JavaHilScenariosTest'

function Fail([string]$message) {
    Write-Host "ERROR: $message" -ForegroundColor Red
    exit 2
}

# --- Rig -----------------------------------------------------------------------------------------
if (-not (Test-Path $Rig)) { Fail "No rig file at $Rig. Copy rig-java.example.json to rig-java.json and list your units (README)." }
$rigData = Get-Content $Rig -Raw | ConvertFrom-Json
$units = @($rigData.units)
if ($Unit) { $units = @($units | Where-Object { $Unit -contains $_.name }) }
if ($units.Count -eq 0) { Fail "No units to test (rig: $Rig, -Unit: $Unit)." }

# Each unit's outgoing Bluetooth serial port, from its MAC, unless the rig names one.
$ports = @{}
Get-CimInstance Win32_PnPEntity -Filter "PNPClass='Ports'" -ErrorAction SilentlyContinue |
    Where-Object { $_.DeviceID -match 'BTHENUM' } | ForEach-Object {
        $m = [regex]::Match($_.DeviceID, '&([0-9A-F]{12})_')
        $p = [regex]::Match($_.Name, 'COM\d+')
        if ($m.Success -and $p.Success -and $m.Groups[1].Value -ne '000000000000') { $ports[$m.Groups[1].Value] = $p.Value }
    }
foreach ($u in $units) {
    if (-not $u.type) { Fail "Unit $($u.name): give its type (Shimmer3 or Shimmer3R)." }
    if (-not $u.comPort) {
        $key = ($u.mac -replace '[:-]', '').ToUpper()
        if (-not $ports.ContainsKey($key)) { Fail "Unit $($u.name) ($($u.mac)) is not paired with this PC: no Bluetooth serial port has its MAC. Pair it (README) or give comPort." }
        $u | Add-Member -NotePropertyName comPort -NotePropertyValue $ports[$key] -Force
    }
}

# The release gate trusts this status, so only report a run that tested exactly this commit, with
# the gate's own settings: every scenario, both device types, defaults, a fresh build, clean tree.
$commit = $null
$githubRepo = $null
if ($Report) {
    if ($PSBoundParameters.ContainsKey('Scenario') -or $Unit) { Fail '-Report needs every scenario on every unit in the rig; drop -Scenario and -Unit.' }
    $shaping = @('Rate', 'Seconds', 'Crc', 'MaxLossPercent', 'SkipBuild') | Where-Object { $PSBoundParameters.ContainsKey($_) }
    if ($shaping) { Fail "-Report runs with the default settings and a fresh build; drop $((@($shaping) | ForEach-Object { "-$_" }) -join ', ')." }
    $types = @($units | ForEach-Object { $_.type })
    if (-not ($types -contains 'Shimmer3') -or -not ($types -contains 'Shimmer3R')) {
        Fail "-Report needs at least one Shimmer3 and one Shimmer3R in the rig (types found: $($types -join ', '))."
    }
    $dirty = (git -C $repo status --porcelain) -join "`n"
    if ($dirty) { Fail "-Report needs a clean working tree, so the tested code is exactly the commit:`n$dirty" }
    $commit = ((git -C $repo rev-parse HEAD) -join '').Trim()
    $remote = ((git -C $repo remote get-url origin) -join '').Trim()
    $githubRepo = ([regex]::Match($remote, 'github\.com[:/](.+?)(\.git)?$')).Groups[1].Value
    if (-not $githubRepo) { Fail "Cannot tell the GitHub repository from origin ($remote)." }
    if (-not (Get-Command gh -ErrorAction SilentlyContinue)) { Fail '-Report posts the result with the GitHub CLI (gh); install it and log in.' }
    gh api "repos/$githubRepo/commits/$commit" --silent 2>$null
    if ($LASTEXITCODE -ne 0) { Fail "Commit $commit is not on GitHub ($githubRepo); push it first." }
}

# --- Build ---------------------------------------------------------------------------------------
# ShimmerDriverPC's settings.gradle finds ShimmerDriver at ../../Shimmer-Java-Android-API/ShimmerDriver.
if ((Split-Path $repo -Leaf) -ne 'Shimmer-Java-Android-API') {
    Fail ("ShimmerDriverPC only builds from a folder named Shimmer-Java-Android-API (its settings.gradle finds its siblings that way), " +
        "and this checkout is $repo. Make a junction and run from it:`n" +
        "  New-Item -ItemType Junction -Path '$(Join-Path (Split-Path $repo -Parent) 'Shimmer-Java-Android-API')' -Target '$repo'")
}
function JavaMajor([string]$javaHome) {
    $release = Join-Path $javaHome 'release'
    if (-not (Test-Path $release)) { return 0 }
    $m = [regex]::Match((Get-Content $release -Raw), 'JAVA_VERSION="(?:1\.)?(\d+)')
    if ($m.Success) { return [int]$m.Groups[1].Value } else { return 0 }
}
if (-not $env:JAVA_HOME -or (JavaMajor $env:JAVA_HOME) -lt 11) {
    $jdk = @(Get-ChildItem 'C:\Program Files\Amazon Corretto', 'C:\Program Files\Eclipse Adoptium', 'C:\Program Files\Java' -Directory -ErrorAction SilentlyContinue |
        Where-Object { (JavaMajor $_.FullName) -ge 11 } | Sort-Object { JavaMajor $_.FullName }) | Select-Object -First 1
    if (-not $jdk) { Fail 'No JDK 11 or newer found; set JAVA_HOME.' }
    $env:JAVA_HOME = $jdk.FullName
}

$outDir = Join-Path $PSScriptRoot ('out\' + (Get-Date -Format 'yyyyMMdd-HHmmss'))
$reportDir = Join-Path $outDir 'reports'
New-Item -ItemType Directory -Force $reportDir | Out-Null
$sha = ((git -C $repo rev-parse --short HEAD) -join '').Trim()
Write-Host "Java HIL: driver source $sha, JDK $(JavaMajor $env:JAVA_HOME) ($env:JAVA_HOME)"
Write-Host "Results: $outDir"

# --no-watch-fs: Gradle's file watcher can miss edits made through the real path of a junction,
# and would then run stale test classes.
$gradle = Join-Path $gradleRoot 'gradlew.bat'
if (-not $SkipBuild) {
    Write-Host 'Building...'
    Push-Location $gradleRoot
    & $gradle ':testClasses' --no-watch-fs --console=plain *> (Join-Path $outDir 'build.log')
    $buildExit = $LASTEXITCODE
    Pop-Location
    if ($buildExit -ne 0) { Fail "Build failed; see $(Join-Path $outDir 'build.log')" }
}

# --- Run -----------------------------------------------------------------------------------------
function Invoke-Scenario([string[]]$gradleArgs, [int]$timeoutSeconds, [string]$logFile) {
    $p = Start-Process -FilePath $gradle -ArgumentList $gradleArgs -WorkingDirectory $gradleRoot -NoNewWindow -PassThru `
        -RedirectStandardOutput $logFile -RedirectStandardError "$logFile.err"
    if (-not $p.WaitForExit($timeoutSeconds * 1000)) {
        # A test JVM stuck in a deadlock outlives the Gradle client; stopping the daemons ends it.
        & $gradle --stop *> $null
        $p.Kill()
        return $true
    }
    return $false
}

$results = @()
$runs = 0
foreach ($u in $units) {
    foreach ($s in $Scenario) {
        if ($runs++ -gt 0) { Start-Sleep -Seconds $SettleSeconds }
        $label = "$($u.name) $s"
        Write-Host "Running $label on $($u.comPort)..." -NoNewline
        $gradleArgs = @(':hilTest', '--no-watch-fs', '--console=plain', '--tests', "$testClass.$s",
            "-Phil.port=$($u.comPort)", "-Phil.mac=$($u.mac)", "-Phil.unit=$($u.name)", "-Phil.type=$($u.type)",
            "-Phil.rate=$Rate", "-Phil.seconds=$Seconds", "-Phil.maxLossPercent=$MaxLossPercent", "-Phil.outDir=$reportDir")
        if ($Crc) { $gradleArgs += "-Phil.crc=$Crc" }
        $safe = "$($u.name)_$s" -replace '[^\w.-]', '_'
        $log = Join-Path $outDir "$safe.console.txt"
        $timedOut = Invoke-Scenario $gradleArgs $ScenarioTimeoutSeconds $log

        $verdict = 'ERROR'
        $detail = ''
        $xml = Join-Path $gradleRoot "build\test-results\hilTest\TEST-$testClass.xml"
        if ($timedOut) {
            $verdict = 'TIMEOUT'
            $detail = "did not finish within $ScenarioTimeoutSeconds s; stopped (see $safe.console.txt)"
        }
        elseif (Test-Path $xml) {
            Copy-Item $xml (Join-Path $outDir "$safe.junit.xml")
            $case = ([xml](Get-Content $xml -Raw -Encoding UTF8)).testsuite.testcase | Where-Object { $_.name -eq $s } | Select-Object -First 1
            if (-not $case) { $detail = 'the scenario did not run (see the console)' }
            elseif ($case.failure) { $verdict = 'FAIL'; $detail = (($case.failure.message -split "`n")[0]) -replace '^java\.lang\.AssertionError: ', '' }
            elseif ($case.skipped) { $verdict = 'SKIP'; $detail = 'skipped' }
            else { $verdict = 'PASS' }
            Remove-Item $xml
        }
        else {
            $detail = ((Get-Content $log, "$log.err" -ErrorAction SilentlyContinue | Select-String 'error|FAILURE|wrong' | Select-Object -First 2) -join ' ').Trim()
        }
        Write-Host " $verdict" -ForegroundColor $(if ($verdict -eq 'PASS') { 'Green' } elseif ($verdict -eq 'SKIP') { 'Yellow' } else { 'Red' })
        $results += [pscustomobject]@{ Unit = $u.name; Port = $u.comPort; Scenario = $s; Result = $verdict; Detail = $detail }
    }
}

# --- Collect -------------------------------------------------------------------------------------
# Every run that reached a verdict wrote a report; the gate's status rests on them.
foreach ($r in $results) {
    if ($r.Result -ne 'PASS' -and $r.Result -ne 'FAIL') { continue }
    $name = ($r.Scenario -creplace '([A-Z])', '_$1').ToLower() + "_$($r.Unit -replace '[^\w.-]', '_').json"
    if (-not (Test-Path (Join-Path $reportDir $name))) {
        $r.Result = 'ERROR'
        $r.Detail = ("no report written ($name). " + $r.Detail).Trim()
    }
}
$summary = [pscustomobject]@{
    driver = $sha; rate = $Rate; seconds = $Seconds; crc = $(if ($Crc) { $Crc } else { 'driver default' })
    settleSeconds = $SettleSeconds; results = $results
}
$summary | ConvertTo-Json -Depth 4 | Set-Content (Join-Path $outDir 'summary.json')

Write-Host ''
$results | Format-Table -AutoSize -Wrap | Out-String -Width 200 | Write-Host
# A skipped scenario did not run, so it is not a pass: the exit code and the gate's status agree.
$failed = @($results | Where-Object { $_.Result -ne 'PASS' })
$passed = @($results | Where-Object { $_.Result -eq 'PASS' })

if ($Report) {
    $firmware = @{}
    Get-ChildItem $reportDir -Filter '*.json' -ErrorAction SilentlyContinue | ForEach-Object {
        $r = Get-Content $_.FullName -Raw | ConvertFrom-Json
        if ($r.firmware -and $r.unit) { $firmware[$r.unit] = "$($r.hardware) $($r.firmware)" }
    }
    $tested = @($units | ForEach-Object { if ($firmware[$_.name]) { $firmware[$_.name] } else { $_.type } }) | Sort-Object -Unique
    $state = if ($passed.Count -eq $results.Count) { 'success' } else { 'failure' }
    $description = "$($passed.Count)/$($results.Count) passed over Classic: $($tested -join ', ')"
    if ($description.Length -gt 140) { $description = $description.Substring(0, 137) + '...' }
    gh api -X POST "repos/$githubRepo/statuses/$commit" -f state=$state -f context=java-hil -f description=$description --silent
    if ($LASTEXITCODE -ne 0) {
        Write-Host 'Could not post the commit status; the release will not see this run.' -ForegroundColor Red
        exit 2
    }
    Write-Host "Posted 'java-hil' = $state on $githubRepo@$($commit.Substring(0, 8)): $description"
}

if ($failed.Count -gt 0) {
    Write-Host "$($failed.Count) of $($results.Count) did not pass. Reports and console output: $outDir" -ForegroundColor Red
    exit 1
}
Write-Host "All $($results.Count) passed. Reports: $outDir" -ForegroundColor Green
exit 0
