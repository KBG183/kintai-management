param(
    [string]$TargetFile,
    [string]$CustomPrompt = "",
    [string]$SystemPromptOverride = "",
    [string]$OutputFile = "hermes_audit_report.md",
    [string]$Model = "qwen27b:latest",
    [string]$HostUrl = "http://<TAILSCALE_OLLAMA_HOST>:11434",
    [int]$NumCtx = 24576,
    [int]$Seed = 42,
    [double]$Temperature = 0.1,
    [bool]$Think = $false,
    [string]$SectionFilter = "",
    [string]$LineRange = "",
    [string]$InputManifestPath = "",
    [switch]$Chunk
)

# Set UTF-8 encoding
[Console]::OutputEncoding = [System.Text.Encoding]::UTF8
$OutputEncoding = [System.Text.Encoding]::UTF8

if (-not [string]::IsNullOrWhiteSpace($InputManifestPath)) {
    $validatorPath = Join-Path $PSScriptRoot 'validate_qwen_audit_input.ps1'
    if (-not (Test-Path -LiteralPath $validatorPath -PathType Leaf)) {
        throw "QWEN_INPUT_VALIDATOR_MISSING: $validatorPath"
    }

    & $validatorPath -BundlePath $TargetFile -ManifestPath $InputManifestPath -EmitJson
    if ($LASTEXITCODE -ne 0) {
        throw "QWEN_INPUT_BLOCKED: audit input manifest is incomplete. ExitCode=$LASTEXITCODE"
    }
}

if (Test-Path -LiteralPath $OutputFile -PathType Leaf) {
    Write-Error "HERMES_FAILED: output file already exists; choose a new path to preserve prior evidence: $OutputFile"
    exit 2
}

$defaultSystemPrompt = @"
You are an expert Spring Boot architecture & Japanese Labor Law code auditor.
[CRITICAL LANGUAGE RULE]
You MUST respond 100% in ENGLISH ONLY.
Under NO circumstances should you output any Chinese characters (Hanzi) or Chinese sentences.
Any Chinese word is considered a fatal failure.

[Audit Checklist]
1. Japanese Labor Standards Act Art 37:
   - Night work (22:00~05:00): +25% extra pay.
   - Daily overtime (>8h/day): +25% extra pay.
   - Night + Overtime concurrent overlap: +50% combined extra pay.
   - Break time between 22:00~05:00 must be deducted specifically from night work hours.
2. Payroll Math & Rounding:
   - Check RoundingMode.HALF_UP rounding logic (1-yen discrepancy prevention).
3. Concurrency & Security:
   - Check @Transactional presence and isolation level.
   - Check pessimistic locking (SELECT ... FOR UPDATE) on concurrent updates (e.g. store_staff, shift_schedule).
   - Check IDOR vulnerabilities (ensure user cannot access other staff's records).
4. Exception Handling & Boundary validation (e.g. DateTimeParseException, null / negative hour edge cases).

[Output Format]
Output the audit strictly as a Markdown table in English:
| Audit Item | Verdict (PASS / WARN / FAIL) | Detailed Finding & Recommendation (English) |
|---|---|---|
"@
$systemPrompt = if ([string]::IsNullOrWhiteSpace($SystemPromptOverride)) { $defaultSystemPrompt } else { $SystemPromptOverride }

function Test-HermesAuditReport {
    param([Parameter(Mandatory = $true)][string]$Content)

    $lines = $Content -split "`r?`n"
    $verdictIndex = -1
    $headerIndex = -1
    for ($lineIndex = 0; $lineIndex -lt $lines.Count; $lineIndex++) {
        $line = $lines[$lineIndex].Trim()
        if (-not $line.StartsWith('|')) { continue }
        $cells = @($line.Trim('|').Split('|') | ForEach-Object { $_.Trim() -replace '[*_`]', '' })
        for ($cellIndex = 0; $cellIndex -lt $cells.Count; $cellIndex++) {
            if ($cells[$cellIndex] -match '^Verdict(?:\s*\(.*\))?$') {
                $verdictIndex = $cellIndex
                $headerIndex = $lineIndex
                break
            }
        }
        if ($headerIndex -ge 0) { break }
    }
    if ($headerIndex -lt 0) { return $false }

    for ($lineIndex = $headerIndex + 1; $lineIndex -lt $lines.Count; $lineIndex++) {
        $line = $lines[$lineIndex].Trim()
        if (-not $line.StartsWith('|')) { continue }
        $cells = @($line.Trim('|').Split('|') | ForEach-Object { $_.Trim() -replace '[*_`]', '' })
        if ($verdictIndex -ge $cells.Count) { continue }
        if ($cells[$verdictIndex] -match '^(PASS|WARN|FAIL|N/A)$') { return $true }
    }
    return $false
}

function Assert-HermesAuditResponse {
    param(
        [Parameter(Mandatory = $true)]$TerminalChunk,
        [Parameter(Mandatory = $true)][AllowEmptyString()][string]$Content,
        [string]$ForbiddenText = "",
        [int]$ContextLimit = 24576
    )

    if (-not $TerminalChunk -or -not $TerminalChunk.done) {
        throw 'HERMES_FAILED: response stream ended without a terminal done chunk.'
    }
    if ($TerminalChunk.done_reason -eq 'length') {
        throw 'HERMES_FAILED: response was truncated by the context limit.'
    }
    $reportedTokens = [int]$TerminalChunk.prompt_eval_count + [int]$TerminalChunk.eval_count
    if ($reportedTokens -gt [math]::Floor($ContextLimit * 0.9)) {
        throw "HERMES_FAILED: response token usage $reportedTokens exceeded 90% of context $ContextLimit."
    }
    $normalized = $Content.Trim()
    if ([string]::IsNullOrWhiteSpace($normalized)) {
        throw 'HERMES_FAILED: response contained no audit content.'
    }
    if (-not (Test-HermesAuditReport -Content $normalized)) {
        throw 'HERMES_FAILED: response did not contain a verdict table with at least one recognized verdict.'
    }
    if (-not [string]::IsNullOrWhiteSpace($ForbiddenText)) {
        $echoProbe = $ForbiddenText.Trim()
        if ($echoProbe.Length -gt 256) { $echoProbe = $echoProbe.Substring(0, 256) }
        if ($normalized.IndexOf($echoProbe, [System.StringComparison]::OrdinalIgnoreCase) -ge 0) {
            throw 'HERMES_FAILED: response echoed the audit instructions.'
        }
    }
    return $normalized
}

function Call-HermesApi {
    param(
        [string]$ContentToAudit,
        [string]$HeaderTitle = ""
    )
    
    $userPromptText = if ($CustomPrompt) {
        "$CustomPrompt`n`n[Target: $HeaderTitle]`n$ContentToAudit`n`nFollow the task-specific audit instructions. Do not repeat the prompt. Return the requested structured Markdown report."
    } else {
        "Review the following Java code and output the findings as a Markdown table strictly in English (NO Chinese):`n`n[Target Code: $HeaderTitle]`n$ContentToAudit"
    }

    $estimatedTokens = [math]::Ceiling(($systemPrompt.Length + $userPromptText.Length) / 3.0)
    $contextBudget = [math]::Floor($NumCtx * 0.9)
    if ($estimatedTokens -gt $contextBudget) {
        Write-Error "HERMES_FAILED: estimated input tokens $estimatedTokens exceed 90% context budget $contextBudget; reduce prompt or enable chunking."
        return $null
    }

    $bodyObj = @{
        model = $Model
        messages = @(
            @{ role = "system"; content = $systemPrompt },
            @{ role = "user"; content = $userPromptText }
        )
        options = @{
            temperature = $Temperature
            num_ctx = $NumCtx
            seed = $Seed
            stop = @("<|im_start|>", "<|im_end|>")
        }
        think = $Think
        stream = $true
    }
    $bodyJson = $bodyObj | ConvertTo-Json -Depth 5 -Compress

    try {
        $request = [System.Net.HttpWebRequest]::Create("$HostUrl/api/chat")
        $request.Method = "POST"
        $request.ContentType = "application/json; charset=utf-8"
        $bytes = [System.Text.Encoding]::UTF8.GetBytes($bodyJson)
        $request.ContentLength = $bytes.Length

        $reqStream = $request.GetRequestStream()
        $reqStream.Write($bytes, 0, $bytes.Length)
        $reqStream.Close()

        $response = $request.GetResponse()
        $reader = New-Object System.IO.StreamReader($response.GetResponseStream(), [System.Text.Encoding]::UTF8)

        $tokenCount = 0
        $lastTick = [System.DateTime]::Now
        $sbContent = New-Object System.Text.StringBuilder
        $sbThinking = New-Object System.Text.StringBuilder
        $lastChunk = $null

        while (-not $reader.EndOfStream) {
            $line = $reader.ReadLine()
            if ([string]::IsNullOrWhiteSpace($line)) { continue }
            try { $chunk = $line | ConvertFrom-Json } catch { continue }

            if ($chunk.message.content -or $chunk.message.thinking) {
                $tokenCount++
            }
            if ($chunk.message.content) {
                $sbContent.Append($chunk.message.content) | Out-Null
            }
            if ($chunk.message.thinking) {
                $sbThinking.Append($chunk.message.thinking) | Out-Null
            }

            # 5-second interval heartbeat
            $now = [System.DateTime]::Now
            if (($now - $lastTick).TotalSeconds -ge 5.0) {
                Write-Host "[$($now.ToString('HH:mm:ss'))] Generating... Total Tokens: $tokenCount"
                $lastTick = $now
            }

            if ($chunk.done) {
                $lastChunk = $chunk
                break
            }
        }
        $reader.Close()
        $response.Close()

        # Parse Ollama telemetry metrics
        $totalSec = if ($lastChunk -and $lastChunk.total_duration) { [math]::Round($lastChunk.total_duration / 1e9, 2) } else { 0 }
        $promptTokens = if ($lastChunk -and $lastChunk.prompt_eval_count) { $lastChunk.prompt_eval_count } else { 0 }
        $evalTokens = if ($lastChunk -and $lastChunk.eval_count) { $lastChunk.eval_count } else { $tokenCount }
        $evalDurationSec = if ($lastChunk -and $lastChunk.eval_duration) { $lastChunk.eval_duration / 1e9 } else { 0 }
        $tps = if ($evalDurationSec -gt 0) { [math]::Round($evalTokens / $evalDurationSec, 1) } else { 0 }

        Write-Host "=== [5070 Ti] Done! Tokens: $evalTokens | Time: $($totalSec)s | Speed: $tps tok/s ==="

        $finalOutput = Assert-HermesAuditResponse -TerminalChunk $lastChunk -Content $sbContent.ToString() -ForbiddenText $CustomPrompt -ContextLimit $NumCtx
        $content = $finalOutput

        return [PSCustomObject]@{
            Content      = $content
            TotalSec     = $totalSec
            PromptTokens = $promptTokens
            EvalTokens   = $evalTokens
            SpeedTPS     = $tps
            Model        = $Model
        }
    } catch {
        Write-Error "Ollama Hermes API call failed: $_"
        return $null
    }
}

# Single prompt execution if no file was supplied
if ([string]::IsNullOrWhiteSpace($TargetFile)) {
    Write-Host "[Hermes] Running prompt audit in English..." -ForegroundColor Cyan
    $resObj = Call-HermesApi -ContentToAudit "" -HeaderTitle "Custom Prompt"
    if (-not $resObj) {
        Write-Error 'HERMES_FAILED: no audit response was received.'
        exit 1
    }
    if ($resObj) {
        $metricTable = @"
### ⏱️ Hermes Audit Execution Metrics
| Target | Model | Duration | Prompt Tokens | Eval Tokens | Speed |
|---|---|---|---|---|---|
| Custom Prompt | $($resObj.Model) | $($resObj.TotalSec)s | $($resObj.PromptTokens) | $($resObj.EvalTokens) | $($resObj.SpeedTPS) tokens/s |

"@
        $finalOut = $metricTable + "`n" + $resObj.Content
        [System.IO.File]::WriteAllText($OutputFile, $finalOut, [System.Text.Encoding]::UTF8)
        Write-Host "`n=== [Hermes Audit Report] ===" -ForegroundColor Green
        Write-Output $finalOut
    }
    exit 0
}

if (-not (Test-Path -LiteralPath $TargetFile -PathType Leaf)) {
    Write-Error "HERMES_FAILED: target file does not exist: $TargetFile"
    exit 2
}

$rawText = Get-Content -Path $TargetFile -Encoding UTF8 -Raw

if (-not [string]::IsNullOrWhiteSpace($LineRange)) {
    $lineArr = $rawText -split "`r?`n"
    $parts = $LineRange.Split("-")
    $startL = [int]$parts[0]
    $endL = [int]$parts[1]
    $selectedLines = $lineArr[($startL - 1)..($endL - 1)]
    $rawText = $selectedLines -join "`n"
    Write-Host "[Hermes] Line range selected: $($TargetFile) (Lines $startL ~ $endL, Total $($selectedLines.Count) lines)" -ForegroundColor Cyan
}

$lines = $rawText -split "`r?`n"
$hasMajorHeaders = ($rawText -match "(?m)^##\s+")

if ($Chunk -or ($lines.Count -gt 400 -and $hasMajorHeaders)) {
    Write-Host "[Hermes] Large file detected ($($lines.Count) lines). Splitting by '## ' sections..." -ForegroundColor Cyan
    
    $sections = @()
    $currentTitle = "Introduction"
    $currentBody = [System.Text.StringBuilder]::new()

    foreach ($line in $lines) {
        if ($line -match "^##\s+(.+)$") {
            if ($currentBody.Length -gt 0) {
                $sections += [PSCustomObject]@{ Title = $currentTitle; Content = $currentBody.ToString() }
                $currentBody.Clear() | Out-Null
            }
            $currentTitle = $matches[1].Trim()
        } else {
            [void]$currentBody.AppendLine($line)
        }
    }
    if ($currentBody.Length -gt 0) {
        $sections += [PSCustomObject]@{ Title = $currentTitle; Content = $currentBody.ToString() }
    }

    $targetSections = $sections
    if (-not [string]::IsNullOrWhiteSpace($SectionFilter)) {
        $filter = "*$SectionFilter*"
        $targetSections = @($sections | Where-Object { $_.Title -like $filter })
    }

    Write-Host "[Hermes] Total $($targetSections.Count) section(s) to audit." -ForegroundColor Cyan

    $fullReport = [System.Text.StringBuilder]::new()
    [void]$fullReport.AppendLine("# 🛡️ Hermes Independent Audit Report (Section-by-Section)")
    [void]$fullReport.AppendLine("> **Target**: `$($TargetFile)` ($($lines.Count) lines) | **Model**: `$($Model)` | **Timestamp**: $(Get-Date -Format 'yyyy-MM-dd HH:mm:ss')`n")

    $totalDurationAll = 0
    $totalPromptTokens = 0
    $totalEvalTokens = 0

    $secIdx = 1
    foreach ($sec in $targetSections) {
        if ($sec.Content.Trim().Length -lt 40) { continue }
        Write-Host "[$secIdx/$($targetSections.Count)] Auditing section: $($sec.Title)..." -ForegroundColor Yellow
        $auditObj = Call-HermesApi -ContentToAudit $sec.Content -HeaderTitle $sec.Title

        if (-not $auditObj) {
            Write-Error "HERMES_FAILED: no audit response for section '$($sec.Title)'; refusing to write a partial report."
            exit 1
        }

        $totalDurationAll += $auditObj.TotalSec
        $totalPromptTokens += $auditObj.PromptTokens
        $totalEvalTokens += $auditObj.EvalTokens

        [void]$fullReport.AppendLine("## Section: $($sec.Title) ($($auditObj.TotalSec)s, $($auditObj.PromptTokens) prompt / $($auditObj.EvalTokens) eval tokens)")
        [void]$fullReport.AppendLine($auditObj.Content)
        [void]$fullReport.AppendLine("`n---`n")
        $secIdx++
    }

    if ($secIdx -eq 1) {
        Write-Error 'HERMES_FAILED: no eligible sections were audited; refusing to write an empty report.'
        exit 1
    }

    $summaryMetrics = @"
### [Total Audit Execution Metrics]
| Target File | Model | Total Time | Total Prompt Tokens | Total Eval Tokens |
|---|---|---|---|---|
| $TargetFile ($($lines.Count) lines) | $Model | ${totalDurationAll}s | $totalPromptTokens tokens | $totalEvalTokens tokens |

"@
    $finalText = $summaryMetrics + "`n" + $fullReport.ToString()
    [System.IO.File]::WriteAllText($OutputFile, $finalText, [System.Text.Encoding]::UTF8)
    Write-Host "[Hermes] Audit completed! Full report saved to $OutputFile." -ForegroundColor Green
    Write-Output $summaryMetrics
} else {
    Write-Host "[Hermes] Auditing single file ($($lines.Count) lines)..." -ForegroundColor Cyan
    $resObj = Call-HermesApi -ContentToAudit $rawText -HeaderTitle $TargetFile
    if (-not $resObj) {
        Write-Error 'HERMES_FAILED: no audit response was received; no report was written.'
        exit 1
    }
    if ($resObj) {
        $metricTable = @"
### [Hermes Audit Execution Metrics]
| Target File | Model | Duration | Input Code Tokens | Output Tokens | Speed |
|---|---|---|---|---|---|
| $TargetFile ($($lines.Count) lines) | $($resObj.Model) | $($resObj.TotalSec)s | $($resObj.PromptTokens) tokens | $($resObj.EvalTokens) tokens | $($resObj.SpeedTPS) tokens/s |

"@
        $finalText = $metricTable + "`n" + $resObj.Content
        [System.IO.File]::WriteAllText($OutputFile, $finalText, [System.Text.Encoding]::UTF8)
        Write-Host "[Hermes] Audit completed! Full report saved to $OutputFile." -ForegroundColor Green
        Write-Output $metricTable
    }
}
