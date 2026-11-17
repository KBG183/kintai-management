[CmdletBinding()]
param(
    [switch]$Apply
)

$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path -Parent $PSScriptRoot
$mysqlExe = 'C:\Program Files\MySQL\MySQL Server 26.7\bin\mysql.exe'
$settingsFile = Join-Path $projectRoot 'src\main\resources\application.yaml'
if (-not (Test-Path -LiteralPath $mysqlExe -PathType Leaf)) { throw 'MySQL client not found.' }

function Get-DatabasePassword {
    if ($env:KINTAI_DB_PASSWORD) { return $env:KINTAI_DB_PASSWORD }
    $passwordLine = Get-Content -LiteralPath $settingsFile -Encoding UTF8 |
        Where-Object { $_ -match '^\s*password:' } | Select-Object -First 1
    if (-not $passwordLine) { throw 'Datasource password setting not found. Set KINTAI_DB_PASSWORD.' }
    return (($passwordLine -split ':', 2)[1] -split '#', 2)[0].Trim()
}

$oldMysqlPwd = $env:MYSQL_PWD
$env:MYSQL_PWD = Get-DatabasePassword
try {
    $names = @(& $mysqlExe --host=127.0.0.1 --port=3306 --user=root --batch --skip-column-names `
        --execute="SELECT SCHEMA_NAME FROM information_schema.SCHEMATA WHERE SCHEMA_NAME REGEXP '^kintai_(day10_test|full_test)_[0-9a-f]{12}$' ORDER BY SCHEMA_NAME;")
    if ($LASTEXITCODE -ne 0) { throw "Database inventory failed: exit $LASTEXITCODE" }

    if ($names.Count -eq 0) {
        Write-Output 'NO_GENERATED_ISOLATED_DATABASES'
        return
    }
    Write-Output 'CANDIDATES:'
    $names | ForEach-Object { Write-Output $_ }
    if (-not $Apply) {
        Write-Output 'DRY_RUN_ONLY: no databases were deleted. Re-run with -Apply after reviewing the allowlist.'
        return
    }

    foreach ($name in $names) {
        if ($name -notmatch '^kintai_(day10_test|full_test)_[0-9a-f]{12}$') {
            throw "Refusing to delete a name outside the generated allowlist: $name"
        }
        & $mysqlExe --host=127.0.0.1 --port=3306 --user=root --execute="DROP DATABASE ``$name``;"
        if ($LASTEXITCODE -ne 0) { throw "Failed to drop isolated database: $name" }
        Write-Output "DELETED=$name"
    }
}
finally {
    if ($null -eq $oldMysqlPwd) { Remove-Item Env:MYSQL_PWD -ErrorAction SilentlyContinue }
    else { $env:MYSQL_PWD = $oldMysqlPwd }
}
