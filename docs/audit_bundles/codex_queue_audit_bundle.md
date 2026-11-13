# Codex Queue Bridge & Manifest Audit Target (Regex Hardened Revision)

## 1. File: scripts/process_codex_queue.ps1
```powershell
<#
.SYNOPSIS
    Codex -> Antigravity Remote Qwen 27B Queue Processor.
.DESCRIPTION
    Monitors docs/codex_queue/pending/ for audit/generation requests from Codex,
    calls the Tailscale remote Ollama host (http://100.111.193.94:11434),
    and saves results into docs/codex_queue/completed/<request-id>/ or failed/<safe-id>/.
    Strictly forbids fallback to local Ollama (qwen2.5:7b).
    Enforces SSRF endpoint whitelist, path traversal protection, and strict request_id regex validation.
    Guarantees post-save SHA-256 integrity and strips <think> reasoning blocks.
#>

param(
    [switch]$Watch,
    [int]$PollIntervalSec = 3,
    [int]$MaxRequests = 0,
    [int]$TimeoutSec = 180,
    [string]$DefaultModel = "qwen27b:latest",
    [string]$DefaultEndpoint = "http://100.111.193.94:11434"
)

[Console]::OutputEncoding = [System.Text.Encoding]::UTF8
$OutputEncoding = [System.Text.Encoding]::UTF8
$ErrorActionPreference = "Stop"

$scriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$projectRoot = [System.IO.Path]::GetFullPath((Split-Path -Parent $scriptDir))
$queueRoot = Join-Path $projectRoot "docs\codex_queue"
$pendingDir = Join-Path $queueRoot "pending"
$completedDir = Join-Path $queueRoot "completed"
$failedDir = Join-Path $queueRoot "failed"
$lockFile = Join-Path $queueRoot ".qwen_remote.lock"
$hermesLockDir = Join-Path $queueRoot ".hermes.lock"

# Allowed Remote Endpoints Whitelist (SSRF Defense - AUDIT-004)
$AllowedEndpoints = @(
    "http://100.111.193.94:11434",
    "http://100.111.193.94:11434/"
)

# UTF-8 without BOM encoding instance
$utf8NoBom = New-Object System.Text.UTF8Encoding($false)

# Ensure queue directories exist
foreach ($dir in @($pendingDir, $completedDir, $failedDir)) {
    if (-not (Test-Path -LiteralPath $dir)) {
        New-Item -ItemType Directory -Path $dir -Force | Out-Null
    }
}

function Clean-ThinkTags {
    param([string]$Text)
    if ([string]::IsNullOrEmpty($Text)) { return "" }
    $cleaned = $Text -replace '(?s)<think>.*?</think>', ''
    if ($cleaned -match '(?s)^.*?</think>') {
        $cleaned = $cleaned -replace '(?s)^.*?</think>', ''
    }
    return $cleaned.Trim()
}

function Acquire-QueueLock {
    param([string]$LockPath, [string]$HermesLock)
    if (Test-Path -LiteralPath $HermesLock) {
        throw "Hermes lock is active ($HermesLock). Another Hermes audit is running."
    }

    if (Test-Path -LiteralPath $LockPath) {
        try {
            $lockInfo = Get-Content -LiteralPath $LockPath -Raw -Encoding UTF8 | ConvertFrom-Json
            $lockAge = (Get-Date) - [DateTime]$lockInfo.acquired_at
            $processExists = Get-Process -Id $lockInfo.pid -ErrorAction SilentlyContinue
            if ($lockAge.TotalMinutes -ge 10 -or (-not $processExists)) {
                Write-Warning "[Queue] Removing stale lock from PID $($lockInfo.pid) (Age: $([math]::Round($lockAge.TotalMinutes, 1))m)"
                Remove-Item -LiteralPath $LockPath -Force -ErrorAction SilentlyContinue
            }
        } catch {
            Write-Warning "[Queue] Failed to parse existing lock file. Removing corrupt lock."
            Remove-Item -LiteralPath $LockPath -Force -ErrorAction SilentlyContinue
        }
    }

    try {
        $fileStream = [System.IO.File]::Open($LockPath, [System.IO.FileMode]::CreateNew, [System.IO.FileAccess]::ReadWrite, [System.IO.FileShare]::None)
        $pidInfo = [ordered]@{
            pid = $PID
            machine = $env:COMPUTERNAME
            acquired_at = (Get-Date).ToString("o")
        }
        $bytes = $utf8NoBom.GetBytes(($pidInfo | ConvertTo-Json -Compress))
        $fileStream.Write($bytes, 0, $bytes.Length)
        return $fileStream
    } catch {
        throw "Could not acquire queue lock ($LockPath). Another process may be running: $_"
    }
}

function Release-QueueLock {
    param($LockStream, [string]$LockPath)
    if ($LockStream) {
        try {
            $LockStream.Close()
            $LockStream.Dispose()
        } catch {
            Write-Warning "[Queue] Error closing lock stream: $_"
        }
    }
    if (Test-Path -LiteralPath $LockPath) {
        try {
            Remove-Item -LiteralPath $LockPath -Force -ErrorAction Stop
        } catch {
            Write-Warning "[Queue] Error removing lock file ($LockPath): $_"
        }
    }
}

function Validate-Endpoint {
    param([string]$EndpointUrl)
    $cleanUri = $EndpointUrl.Trim().TrimEnd('/')
    $matchFound = $false
    foreach ($allowed in $AllowedEndpoints) {
        if ($cleanUri -eq $allowed.TrimEnd('/')) {
            $matchFound = $true
            break
        }
    }
    if (-not $matchFound) {
        throw "SECURITY_VIOLATION: Endpoint '$EndpointUrl' is not in the allowed Tailscale whitelist."
    }
}

function Validate-RequestId {
    param([string]$Id)
    if ([string]::IsNullOrWhiteSpace($Id)) {
        throw "SECURITY_VIOLATION: request_id cannot be null or empty."
    }
    # Enforce strict regex: alphanumeric, underscore, and hyphen only
    if ($Id -notmatch '^[a-zA-Z0-9_-]+$') {
        throw "SECURITY_VIOLATION: Invalid request_id format '$Id'. Only alphanumeric, underscore, and hyphen characters are allowed."
    }
}

function Validate-PromptFilePath {
    param([string]$FilePath, [string]$RootDirectory)
    $fullPath = if ([System.IO.Path]::IsPathRooted($FilePath)) {
        [System.IO.Path]::GetFullPath($FilePath)
    } else {
        [System.IO.Path]::GetFullPath((Join-Path $RootDirectory $FilePath))
    }

    $normalizedRoot = $RootDirectory.TrimEnd('\') + '\'
    if (-not $fullPath.StartsWith($normalizedRoot, [System.StringComparison]::OrdinalIgnoreCase) -and -not ($fullPath -eq $RootDirectory)) {
        throw "SECURITY_VIOLATION: Path traversal detected. Access denied outside project root: $FilePath"
    }
    return $fullPath
}

function Process-SingleRequest {
    param(
        [Parameter(Mandatory = $true)]
        [System.IO.FileInfo]$RequestFile
    )

    $startedAt = Get-Date
    $startedIso = $startedAt.ToString("o")
    $rawContent = ""
    $sanitizedFileBase = $RequestFile.BaseName -replace '[^a-zA-Z0-9_-]', '_'
    $requestId = $sanitizedFileBase
    $safeDirectoryId = $sanitizedFileBase
    $model = $DefaultModel
    $endpoint = $DefaultEndpoint
    $createdBy = "Codex"
    $sw = [System.Diagnostics.Stopwatch]::StartNew()

    Write-Host "[Queue] Processing: $($RequestFile.Name) at $startedIso"

    try {
        $rawContent = [System.IO.File]::ReadAllText($RequestFile.FullName, [System.Text.Encoding]::UTF8)

        $req = $null
        try {
            $req = $rawContent | ConvertFrom-Json
        } catch {
            throw "JSON_PARSE_ERROR: Failed to parse request JSON content: $($_.Exception.Message)"
        }

        if (-not $req) {
            throw "INVALID_REQUEST: Request JSON parsed to null or empty."
        }

        if ($req.request_id) {
            $candidateId = [string]$req.request_id
            Validate-RequestId -Id $candidateId
            $requestId = $candidateId
            $safeDirectoryId = $candidateId
        }

        if ($req.model) { $model = [string]$req.model }
        if ($req.endpoint) { $endpoint = [string]$req.endpoint }
        if ($req.created_by) { $createdBy = [string]$req.created_by }

        Validate-Endpoint -EndpointUrl $endpoint
        if ($model -eq "qwen2.5:7b") {
            throw "SECURITY_VIOLATION: Local 7B model ($model) is strictly prohibited as remote auditor."
        }

        $promptText = ""
        if ($req.prompt) {
            $promptText = [string]$req.prompt
        } elseif ($req.prompt_file) {
            $safePath = Validate-PromptFilePath -FilePath ([string]$req.prompt_file) -RootDirectory $projectRoot
            if (-not (Test-Path -LiteralPath $safePath)) {
                throw "FILE_NOT_FOUND: Specified prompt_file not found: $safePath"
            }
            $promptText = [System.IO.File]::ReadAllText($safePath, [System.Text.Encoding]::UTF8)
        } else {
            throw "INVALID_REQUEST: Neither 'prompt' nor 'prompt_file' was specified in request."
        }

        $uri = "$($endpoint.TrimEnd('/'))/api/generate"
        $payloadObj = [ordered]@{
            model = $model
            prompt = $promptText
            stream = $false
        }
        if ($req.options) {
            $payloadObj["options"] = $req.options
        } else {
            $payloadObj["options"] = @{ temperature = 0.1 }
        }
        $jsonPayload = $payloadObj | ConvertTo-Json -Depth 10

        Write-Host "[Queue] Calling Remote Ollama: $uri (Model: $model)..."

        $httpResponse = Invoke-WebRequest -Uri $uri -Method Post -Body ($utf8NoBom.GetBytes($jsonPayload)) -ContentType "application/json; charset=utf-8" -TimeoutSec $TimeoutSec -UseBasicParsing
        $sw.Stop()

        $rawResponseJson = if ($httpResponse.Content -is [string]) {
            $httpResponse.Content
        } else {
            [System.Text.Encoding]::UTF8.GetString($httpResponse.Content)
        }

        $parsedResponse = $rawResponseJson | ConvertFrom-Json
        $rawResult = if ($parsedResponse.response) { [string]$parsedResponse.response } else { "" }

        $cleanedResult = Clean-ThinkTags -Text $rawResult

        $targetDir = Join-Path $completedDir $safeDirectoryId
        if (-not (Test-Path -LiteralPath $targetDir)) {
            New-Item -ItemType Directory -Path $targetDir -Force | Out-Null
        }

        $requestPath = Join-Path $targetDir "request.json"
        $rawPath = Join-Path $targetDir "raw_response.json"
        $resultPath = Join-Path $targetDir "result.md"
        $shaPath = Join-Path $targetDir "response_sha256.txt"
        $manifestPath = Join-Path $targetDir "manifest.json"

        [System.IO.File]::WriteAllText($requestPath, $rawContent, $utf8NoBom)
        [System.IO.File]::WriteAllText($rawPath, $rawResponseJson, $utf8NoBom)
        [System.IO.File]::WriteAllText($resultPath, $cleanedResult, $utf8NoBom)

        $actualFileHash = (Get-FileHash -LiteralPath $rawPath -Algorithm SHA256).Hash.ToLowerInvariant()
        [System.IO.File]::WriteAllText($shaPath, $actualFileHash, $utf8NoBom)

        $finishedAt = Get-Date
        $manifest = [ordered]@{
            request_id = $requestId
            status = "SUCCESS"
            endpoint = $endpoint
            model = $model
            created_by = $createdBy
            started_at_local = $startedIso
            finished_at_local = $finishedAt.ToString("o")
            elapsed_seconds = [math]::Round($sw.Elapsed.TotalSeconds, 3)
            prompt_eval_count = $parsedResponse.prompt_eval_count
            eval_count = $parsedResponse.eval_count
            response_sha256 = $actualFileHash
            files = [ordered]@{
                request = "request.json"
                raw_response = "raw_response.json"
                result = "result.md"
                sha256 = "response_sha256.txt"
            }
        }
        $manifestJson = $manifest | ConvertTo-Json -Depth 5
        [System.IO.File]::WriteAllText($manifestPath, $manifestJson, $utf8NoBom)

        Remove-Item -LiteralPath $RequestFile.FullName -Force

        Write-Host "[Queue] SUCCESS: Request $requestId completed in $($manifest.elapsed_seconds)s. Verified SHA: $actualFileHash"
        return $true

    } catch {
        if ($sw) { $sw.Stop() }
        $errMessage = $_.Exception.ToString()
        Write-Warning "[Queue] FAILED: Request $requestId failed: $errMessage"

        $failDir = Join-Path $failedDir $safeDirectoryId
        if (-not (Test-Path -LiteralPath $failDir)) {
            New-Item -ItemType Directory -Path $failDir -Force | Out-Null
        }

        $failRequestPath = Join-Path $failDir "request.json"
        if ([string]::IsNullOrEmpty($rawContent)) {
            try { $rawContent = [System.IO.File]::ReadAllText($RequestFile.FullName, [System.Text.Encoding]::UTF8) } catch { $rawContent = "FILE_READ_FAILED" }
        }
        [System.IO.File]::WriteAllText($failRequestPath, $rawContent, $utf8NoBom)

        $failErrorPath = Join-Path $failDir "error.txt"
        [System.IO.File]::WriteAllText($failErrorPath, $errMessage, $utf8NoBom)

        $finishedAt = Get-Date
        $manifest = [ordered]@{
            request_id = $requestId
            status = "FAILED"
            endpoint = $endpoint
            model = $model
            created_by = $createdBy
            started_at_local = $startedIso
            finished_at_local = $finishedAt.ToString("o")
            elapsed_seconds = if ($sw) { [math]::Round($sw.Elapsed.TotalSeconds, 3) } else { 0 }
            error_message = $_.Exception.Message
            files = [ordered]@{
                request = "request.json"
                error = "error.txt"
            }
        }
        $manifestJson = $manifest | ConvertTo-Json -Depth 5
        [System.IO.File]::WriteAllText((Join-Path $failDir "manifest.json"), $manifestJson, $utf8NoBom)

        if (Test-Path -LiteralPath $RequestFile.FullName) {
            Remove-Item -LiteralPath $RequestFile.FullName -Force
        }

        return $false
    }
}
```

## 2. File: docs/codex_queue/completed/codex_test_remote_ok/manifest.json
```json
{
    "request_id":  "codex_test_remote_ok",
    "status":  "SUCCESS",
    "endpoint":  "http://100.111.193.94:11434",
    "model":  "qwen27b:latest",
    "created_by":  "Codex",
    "started_at_local":  "2026-11-13T10:23:43.9624947+09:00",
    "finished_at_local":  "2026-11-13T10:23:44.9701725+09:00",
    "elapsed_seconds":  0.964,
    "prompt_eval_count":  58,
    "eval_count":  27,
    "response_sha256":  "802201a2a9ab3ef5d98f033e10ced795e968831ed4fd687ddf3d0769ded61994",
    "files":  {
                  "request":  "request.json",
                  "raw_response":  "raw_response.json",
                  "result":  "result.md",
                  "sha256":  "response_sha256.txt"
              }
}
```
