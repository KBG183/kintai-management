[CmdletBinding()]
param()

$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path -Parent $PSScriptRoot

# MySQL CLI 実行ファイルの検出（PATH優先、環境変数、標準インストールパスの順でフォールバック）
$mysqlExe = $null
if ($env:MYSQL_EXE -and (Test-Path -LiteralPath $env:MYSQL_EXE -PathType Leaf)) {
    $mysqlExe = $env:MYSQL_EXE
} else {
    $pathCmd = Get-Command 'mysql' -ErrorAction SilentlyContinue
    if ($pathCmd) {
        $mysqlExe = $pathCmd.Source
    } else {
        $candidates = @(
            'C:\Program Files\MySQL\MySQL Server 26.7\bin\mysql.exe',
            'C:\Program Files\MySQL\MySQL Server 8.4\bin\mysql.exe',
            'C:\Program Files\MySQL\MySQL Server 8.0\bin\mysql.exe'
        )
        foreach ($c in $candidates) {
            if (Test-Path -LiteralPath $c -PathType Leaf) {
                $mysqlExe = $c
                break
            }
        }
    }
}
if (-not $mysqlExe) {
    throw "mysql 実行ファイルが見つかりません。PATH に追加するか、環境変数 MYSQL_EXE を設定してください。"
}

$schemaFile = Join-Path $projectRoot 'src\main\resources\db\schema.sql'
$dataFile = Join-Path $projectRoot 'src\main\resources\db\data.sql'
$fixtureFile = Join-Path $PSScriptRoot 'local_demo_attendance.sql'
$videoSeedFile = Join-Path $PSScriptRoot 'local_video_demo_seed.sql'
$settingsFile = Join-Path $projectRoot 'src\main\resources\application.yaml'

foreach ($path in @($mysqlExe, $schemaFile, $dataFile, $fixtureFile, $videoSeedFile, $settingsFile)) {
    if (-not (Test-Path -LiteralPath $path -PathType Leaf)) {
        throw "Required file not found: $path"
    }
}

$schema = [IO.File]::ReadAllText($schemaFile, [Text.Encoding]::UTF8)
$schema = [regex]::Replace($schema, '(?im)^\s*(CREATE\s+DATABASE\s+IF\s+NOT\s+EXISTS.*|USE\s+[^;]+;)\s*\r?\n', '')
if ($schema -match '(?im)^\s*(USE\s+|CREATE\s+DATABASE|DROP\s+DATABASE|ALTER\s+DATABASE)') {
    throw 'Schema contains an unexpected database-level statement; refusing to run.'
}

$passwordLine = Get-Content -LiteralPath $settingsFile -Encoding UTF8 |
    Where-Object { $_ -match '^\s*password:' } | Select-Object -First 1
if ($env:KINTAI_DB_PASSWORD) {
    $password = $env:KINTAI_DB_PASSWORD
} elseif ($passwordLine) {
    $password = (($passwordLine -split ':', 2)[1] -split '#', 2)[0].Trim()
} else {
    throw 'Set KINTAI_DB_PASSWORD; no datasource password setting was found.'
}

$previousMysqlPassword = $env:MYSQL_PWD
$env:MYSQL_PWD = $password
$databaseName = 'kintai_demo_' + [guid]::NewGuid().ToString('N').Substring(0, 12)
$normalizedSchemaFile = Join-Path $env:TEMP ('kintai_demo_schema_' + [guid]::NewGuid().ToString('N') + '.sql')

function Invoke-MySqlFile([string]$FilePath, [string]$DatabaseName) {
    $mysqlArguments = @(
        '--host=127.0.0.1', '--port=3306', '--user=root',
        '--default-character-set=utf8mb4', "--database=$DatabaseName", '--batch'
    )
    $mysqlProcess = Start-Process -FilePath $mysqlExe -ArgumentList $mysqlArguments `
        -RedirectStandardInput $FilePath -NoNewWindow -Wait -PassThru
    return $mysqlProcess.ExitCode
}

try {
    $createSql = "CREATE DATABASE ``$databaseName`` CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;"
    & $mysqlExe --host=127.0.0.1 --port=3306 --user=root --default-character-set=utf8mb4 --execute=$createSql
    if ($LASTEXITCODE -ne 0) { throw "Demo database creation failed: exit $LASTEXITCODE" }

    [IO.File]::WriteAllText($normalizedSchemaFile, $schema, [Text.UTF8Encoding]::new($false))
    $schemaExitCode = Invoke-MySqlFile -FilePath $normalizedSchemaFile -DatabaseName $databaseName
    if ($schemaExitCode -ne 0) { throw "Schema initialization failed. Retained database: $databaseName (exit $schemaExitCode)" }

    $dataExitCode = Invoke-MySqlFile -FilePath $dataFile -DatabaseName $databaseName
    if ($dataExitCode -ne 0) { throw "Base demo data load failed. Retained database: $databaseName (exit $dataExitCode)" }

    $fixtureExitCode = Invoke-MySqlFile -FilePath $fixtureFile -DatabaseName $databaseName
    if ($fixtureExitCode -ne 0) { throw "Attendance demo data load failed. Retained database: $databaseName (exit $fixtureExitCode)" }

    $videoSeedExitCode = Invoke-MySqlFile -FilePath $videoSeedFile -DatabaseName $databaseName
    if ($videoSeedExitCode -ne 0) { throw "Video demo data load failed. Retained database: $databaseName (exit $videoSeedExitCode)" }

    $selected = & $mysqlExe --host=127.0.0.1 --port=3306 --user=root --database=$databaseName --batch --skip-column-names --execute='SELECT DATABASE();'
    if ($LASTEXITCODE -ne 0 -or ($selected | Out-String).Trim() -ne $databaseName) {
        throw "Demo database selection check failed. Retained database: $databaseName"
    }

    $counts = & $mysqlExe --host=127.0.0.1 --port=3306 --user=root --database=$databaseName --batch --skip-column-names --execute="SELECT COUNT(*), MIN(work_date), MAX(work_date) FROM attendance WHERE store_id=1 AND work_date BETWEEN '2026-01-01' AND '2026-12-31';"
    if ($LASTEXITCODE -ne 0) { throw "Demo data verification failed. Retained database: $databaseName" }
    $shiftCount = & $mysqlExe --host=127.0.0.1 --port=3306 --user=root --database=$databaseName --batch --skip-column-names --execute="SELECT COUNT(*) FROM shift_schedule WHERE store_id=1 AND work_date BETWEEN '2026-01-01' AND '2026-12-31' AND status='CONFIRMED';"
    if ($LASTEXITCODE -ne 0 -or [int]($shiftCount | Out-String).Trim() -le 0) { throw "Demo shift verification failed. Retained database: $databaseName" }
    $ownerNameHex = & $mysqlExe --host=127.0.0.1 --port=3306 --user=root --database=$databaseName --batch --skip-column-names --execute='SELECT HEX(name) FROM member WHERE member_id=1;'
    if ($LASTEXITCODE -ne 0 -or [string]::IsNullOrWhiteSpace(($ownerNameHex | Out-String).Trim()) -or ($ownerNameHex | Out-String) -match '3F') {
        throw "Demo UTF-8 name verification failed. Retained database: $databaseName"
    }

    Write-Output "ISOLATED_DEMO_DATABASE=$databaseName"
    Write-Output "ATTENDANCE_COUNT_AND_RANGE=$($counts.Trim())"
    Write-Output "CONFIRMED_SHIFT_COUNT=$($shiftCount.Trim())"
    Write-Output "OWNER_NAME_HEX=$($ownerNameHex.Trim())"
    Write-Output 'LOGIN_EMAIL=owner@kintai.com'
    Write-Output "APPLICATION_DB_OVERRIDE=SPRING_DATASOURCE_URL=jdbc:mysql://127.0.0.1:3306/${databaseName}?useSSL=false&serverTimezone=Asia/Tokyo&characterEncoding=UTF-8&allowPublicKeyRetrieval=true"
} finally {
    if ($null -eq $previousMysqlPassword) { Remove-Item Env:MYSQL_PWD -ErrorAction SilentlyContinue }
    else { $env:MYSQL_PWD = $previousMysqlPassword }
    if (Test-Path -LiteralPath $normalizedSchemaFile -PathType Leaf) { Remove-Item -LiteralPath $normalizedSchemaFile -Force }
    $password = $null
}
