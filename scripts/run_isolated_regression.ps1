[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)]
    [ValidateSet('Day10', 'Day11', 'All')]
    [string]$Scope,
    [Parameter(Mandatory = $true)]
    [string]$DatabaseName,
    [string]$TestClass
)

$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path -Parent $PSScriptRoot
$mysqlExe = 'C:\Program Files\MySQL\MySQL Server 26.7\bin\mysql.exe'
$settingsFile = Join-Path $projectRoot 'src\main\resources\application.yaml'
$runId = Get-Date -Format 'yyyyMMdd_HHmmss'
$outputDir = Join-Path $projectRoot ("docs\audit_runs\isolated_{0}_{1}_{2}" -f $Scope.ToLowerInvariant(), $runId, $DatabaseName)
$consoleLog = Join-Path $outputDir 'gradle_console.log'

if (-not $TestClass) {
    $TestClass = if ($Scope -eq 'Day11') {
        'com.example.kintai.domain.payroll.service.AnnualPayrollScenarioIntegrationTest'
    } else {
        'com.example.kintai.domain.attendance.service.AttendanceServiceConcurrencyIntegrationTest'
    }
}

if (-not (Test-Path -LiteralPath $mysqlExe -PathType Leaf)) { throw 'MySQL client not found.' }
if (-not (Test-Path -LiteralPath $settingsFile -PathType Leaf)) { throw 'application.yaml not found.' }

if ($Scope -eq 'Day10' -and $DatabaseName -notmatch '^kintai_day10_test_[0-9a-f]{12}$') {
    throw "Day10 database name is outside the generated allowlist: $DatabaseName"
}
if ($Scope -eq 'Day11' -and $DatabaseName -notmatch '^kintai_day11_test_[0-9a-f]{12}$') {
    throw "Day11 database name is outside the generated allowlist: $DatabaseName"
}
if ($Scope -eq 'All' -and $DatabaseName -notmatch '^kintai_full_test_[0-9a-f]{12}$') {
    throw "Full-suite database name is outside the generated allowlist: $DatabaseName"
}

function Get-DatabasePassword {
    if ($env:KINTAI_DB_PASSWORD) { return $env:KINTAI_DB_PASSWORD }
    $passwordLine = Get-Content -LiteralPath $settingsFile -Encoding UTF8 |
        Where-Object { $_ -match '^\s*password:' } | Select-Object -First 1
    if (-not $passwordLine) { throw 'Datasource password setting not found. Set KINTAI_DB_PASSWORD.' }
    return (($passwordLine -split ':', 2)[1] -split '#', 2)[0].Trim()
}

New-Item -ItemType Directory -Path $outputDir -Force | Out-Null
$resultDir = Join-Path $projectRoot 'build\test-results\test'
if (Test-Path -LiteralPath $resultDir -PathType Container) {
    $preexistingResults = @(Get-ChildItem -LiteralPath $resultDir -Filter 'TEST-*.xml' -File -ErrorAction SilentlyContinue)
    if ($preexistingResults.Count -gt 0) {
        $preservedResultDir = Join-Path $outputDir 'preexisting_test_results'
        New-Item -ItemType Directory -Path $preservedResultDir -Force | Out-Null
        foreach ($result in $preexistingResults) {
            Copy-Item -LiteralPath $result.FullName -Destination (Join-Path $preservedResultDir $result.Name) -Force
            Remove-Item -LiteralPath $result.FullName -Force
        }
        Write-Output "PRESERVED_PREEXISTING_JUNIT_COUNT=$($preexistingResults.Count)"
    }
}
$oldSpringUrl = $env:SPRING_DATASOURCE_URL
$oldMysqlPwd = $env:MYSQL_PWD
$env:MYSQL_PWD = Get-DatabasePassword
$env:SPRING_DATASOURCE_URL = "jdbc:mysql://127.0.0.1:3306/$DatabaseName"

try {
    $selected = & $mysqlExe --host=127.0.0.1 --port=3306 --user=root --database=$DatabaseName --batch --skip-column-names `
        --execute='SELECT DATABASE();'
    if ($LASTEXITCODE -ne 0) { throw "Target database preflight failed: exit $LASTEXITCODE" }
    if (($selected | Out-String).Trim() -ne $DatabaseName) {
        throw "Target database mismatch. Expected '$DatabaseName', got '$selected'."
    }

    $metadata = [ordered]@{
        database = $DatabaseName
        scope = $Scope
        test_class = if ($Scope -ne 'All') { $TestClass } else { $null }
        run_id = $runId
        output_dir = $outputDir
        jdbc_url_host = '127.0.0.1:3306'
    }
    $metadata | ConvertTo-Json | Set-Content -LiteralPath (Join-Path $outputDir 'run_metadata.json') -Encoding UTF8
    Write-Output "TARGET_DATABASE=$DatabaseName"
    Write-Output "OUTPUT_DIR=$outputDir"

    if ($Scope -eq 'Day10' -or $Scope -eq 'Day11') {
        $gradleArgs = "test --tests $TestClass --rerun-tasks --no-daemon --console plain"
    }
    else {
        $gradleArgs = 'test --rerun-tasks --no-daemon --console plain'
    }

    Push-Location $projectRoot
    try {
        $gradleCommand = ".\gradlew.bat $gradleArgs > `"$consoleLog`" 2>&1"
        & cmd.exe /d /c $gradleCommand
        $gradleExit = $LASTEXITCODE
    }
    finally {
        Pop-Location
    }

    $junitSources = @(Get-ChildItem -LiteralPath $resultDir -Filter 'TEST-*.xml' -File -ErrorAction SilentlyContinue)
    foreach ($source in $junitSources) {
        Copy-Item -LiteralPath $source.FullName -Destination (Join-Path $outputDir $source.Name) -Force
    }

    $totals = [ordered]@{ tests = 0; failures = 0; errors = 0; skipped = 0 }
    $parseErrors = [System.Collections.Generic.List[string]]::new()
    foreach ($junitFile in @(Get-ChildItem -LiteralPath $outputDir -Filter 'TEST-*.xml' -File -ErrorAction SilentlyContinue)) {
        try {
            $xml = [xml][IO.File]::ReadAllText($junitFile.FullName, [Text.Encoding]::UTF8)
            $suiteNodes = @($xml.SelectNodes('/testsuite | /testsuites/testsuite'))
            if ($suiteNodes.Count -eq 0) { throw 'No JUnit testsuite element found.' }
            foreach ($suite in $suiteNodes) {
                foreach ($key in @('tests', 'failures', 'errors', 'skipped')) {
                    $value = $suite.GetAttribute($key)
                    if ($value -and $value -notmatch '^\d+$') { throw "Invalid '$key' attribute '$value'." }
                    if ($value) { $totals[$key] += [int]$value }
                }
            }
        }
        catch {
            $parseErrors.Add("$($junitFile.Name): $($_.Exception.Message)")
        }
    }

    $summary = [ordered]@{
        gradle_exit_code = $gradleExit
        junit_xml_count = $junitSources.Count
        tests = $totals.tests
        failures = $totals.failures
        errors = $totals.errors
        skipped = $totals.skipped
        junit_parse_errors = @($parseErrors)
        result = if ($gradleExit -ne 0 -or $parseErrors.Count -gt 0) { 'FAILED' } elseif ($junitSources.Count -eq 0 -or $totals.tests -eq 0) { 'NO_JUNIT_XML' } elseif ($totals.failures -gt 0 -or $totals.errors -gt 0) { 'FAILED' } else { 'PASS' }
    }
    $summary | ConvertTo-Json -Depth 4 | Set-Content -LiteralPath (Join-Path $outputDir 'exit_summary.json') -Encoding UTF8

    $hashTargets = @()
    if (Test-Path -LiteralPath $consoleLog -PathType Leaf) { $hashTargets += Get-Item -LiteralPath $consoleLog }
    $hashTargets += @(Get-ChildItem -LiteralPath $outputDir -Filter 'TEST-*.xml' -File -ErrorAction SilentlyContinue)
    $manifest = foreach ($target in $hashTargets) {
        [pscustomobject]@{
            path = $target.Name
            bytes = $target.Length
            sha256 = (Get-FileHash -LiteralPath $target.FullName -Algorithm SHA256).Hash.ToLowerInvariant()
        }
    }
    [ordered]@{
        schema_version = '1.0'
        run_id = $runId
        generated_at_utc = [DateTime]::UtcNow.ToString('o')
        algorithm = 'SHA-256'
        files = @($manifest)
    } | ConvertTo-Json -Depth 5 | Set-Content -LiteralPath (Join-Path $outputDir 'sha256_manifest.json') -Encoding UTF8

    Write-Output "GRADLE_EXIT_CODE=$gradleExit"
    Write-Output "JUNIT_XML_COUNT=$($junitSources.Count)"
    Write-Output "TESTS=$($totals.tests)"
    Write-Output "FAILURES=$($totals.failures)"
    Write-Output "ERRORS=$($totals.errors)"
    Write-Output "SKIPPED=$($totals.skipped)"
    if ($gradleExit -ne 0 -or $parseErrors.Count -gt 0 -or $junitSources.Count -eq 0 -or $totals.tests -eq 0 -or $totals.failures -gt 0 -or $totals.errors -gt 0) {
        throw "Isolated regression did not pass. See run artifacts: $outputDir"
    }
    Write-Output 'ISOLATED_REGRESSION_SUCCESS'
}
finally {
    if ($null -eq $oldSpringUrl) { Remove-Item Env:SPRING_DATASOURCE_URL -ErrorAction SilentlyContinue }
    else { $env:SPRING_DATASOURCE_URL = $oldSpringUrl }
    if ($null -eq $oldMysqlPwd) { Remove-Item Env:MYSQL_PWD -ErrorAction SilentlyContinue }
    else { $env:MYSQL_PWD = $oldMysqlPwd }
}
