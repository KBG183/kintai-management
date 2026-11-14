# Compact frozen project handoff and audit runner review v6

Independent review of current handoff, fail-closed audit runner, status claims and execution evidence. PASS requires coherent evidence. Only configured remote: http://100.111.193.94:11434 / qwen27b:latest.

### Qwen configuration

[lines 1-]
{
}
[lines 22-]
    "think": false,
    "model_digest": "23c3820b0d9326b73e97191b9fb7e16b53f6e24bb3449d2a17037c5f2b6179a1",
    "model_tag": "qwen27b:latest",
    "endpoint": "http://100.111.193.94:11434",
    "network_type": "Tailscale Overlay Network",
    "enabled": false,
  "fallback_auditor": {
  },
    "verdict_authority": "AUDIT_PASSED"
    },
      "seed": 42
      "num_ctx": 24576,
      "temperature": 0.1,
    "options": {
    "think": false,
    "model_digest": "23c3820b0d9326b73e97191b9fb7e16b53f6e24bb3449d2a17037c5f2b6179a1",
    "model_tag": "qwen27b:latest",
    "endpoint": "http://100.111.193.94:11434",
    "network_type": "Tailscale Overlay Network",
  "primary_auditor": {
  "$schema": "./audit_config.schema.json",
{
}

### Current handoff facts, scope and open work

[lines 1-24]
# Antigravity project-wide handoff (2026-11-14)

### 목적과 기준 상태

Codex가 2026-11-14 기준 저장소·증적·잔여 작업을 대조한 인수인계입니다. Antigravity에서 아래 남은 작업을 순서대로 수행해 주세요. 저장소에는 사용자 및 이전 작업자의 미커밋 변경과 감사 증적이 많습니다. 기존 변경을 보존하고 reset/revert/clean을 하지 마세요.

- 최신 격리 전체 회귀: 92 PASS, 실패 0, 오류 0, 스킵 0, Gradle exit 0. DB `kintai_full_test_d4124a317ed0`; `docs/audit_runs/isolated_all_20261114_094427_kintai_full_test_d4124a317ed0/`.
- Day 11 연간·경계 대상 실행: 6/6 PASS, 격리 DB `kintai_day11_test_01f4cb1439e6`; `docs/audit_runs/isolated_day11_20261114_094349_kintai_day11_test_01f4cb1439e6/`.
- R-02 정책 채택 및 기술 검증 완료. R-11/R-12/R-14는 명시된 로컬 MySQL 26.7 범위에서 VERIFIED.
- 이전 독립 감사: Qwen 27B 소스 Run `20261114_004900_46049b14` 12/12 PASS; 고정 문서 Run `20261114_005527_76dc1c9f` 10/10 PASS. 두 결과는 당시 입력 bundle에만 유효합니다. 프로젝트 전체 문서 bundle SHA `cd7cd20d12ba7771fb8ceb01dcd95181bfe39c924b8a595aa5e6fb91fdf649b8`는 첫 연결 실패 뒤 잘못된 형식의 보고서가 exit 0으로 생성됐고, 독립 재감사 Run `20261114_014515_cd7cd20d`는 WARN을 반환했습니다. 이 결과들을 PASS로 합산하지 않습니다.

### 고정 범위와 실행 제한

1. R-05(MySQL 8.4 Testcontainers) 및 Day 12 작업은 사용자 지시에 따라 예정일까지 제외합니다. 지금 실행하지 마세요.
2. 운영 DB는 사용 금지. 대상/전체 테스트와 시연마다 새 allowlisted 격리 DB를 사용하고 DB명을 증거로 확인하세요. 기존 격리 DB도 삭제하지 마세요.
3. 원격 Qwen 감사는 `http://100.111.193.94:11434`, `qwen27b:latest`만 사용하세요. 저장소 실행 진입점은 `scripts/codex_hermes_bridge.ps1`이며 HTTP 전송을 기본값으로 사용하고 `scripts/audit_config.json`에서 주소와 모델을 읽습니다. 문서 감사에는 `-SystemPromptOverride`로 작업에 맞는 지시를 지정하세요. 로컬 7B 대체, 다른 IP, 모델 pull/download/install 금지. 연결 실패 시 원본 주소와 오류를 보존하고 미검증으로 남기세요.
4. 외부 배포, 원격 Git 반영, 메시지 전송은 하지 마세요. 해당 작업은 사용자 지시가 별도로 필요합니다.
5. 기존 실패 로그와 증거는 덮어쓰거나 삭제하지 말고 새 실행 디렉터리에 보존하세요.

### 잔여 백로그 구분

- 즉시 넘길 프로젝트 코드 작업은 B-05와 B-06입니다. B-04는 VERIFIED 완료입니다.
- B-01(독립 감사 정확도 벤치마크)과 B-07(감사 메트릭 마크다운 자동화)은 범용 파이프라인 개선 백로그로서 마일스톤 검토 대기입니다. 현재 기능 검증을 막는 결함으로 취급하지 말고, 사용자 일정에 맞춰 별도 우선순위를 정하세요.
- B-02, B-03, B-08은 백로그에서 완료 상태입니다. B-09에는 원격 오류뿐 아니라 빈 응답·종료 청크 누락·잘못된 verdict 표·잘못된 대상 경로의 fail-closed 확인이 포함됩니다. 최신 코드와 실행 증거는 백로그 및 `docs/audit_runs/project_wide_handoff_20261114/`를 확인하세요. R-05와 Day 12는 제외 상태이므로 현재 구현 대상으로 넣지 마세요.
[lines 48-74]

- 기존 bundle `current_docs_audit_bundle_final.md`의 최초 지정 주소 호출은 `GetRequestStream: Unable to connect to the remote server`로 실패했습니다. 후속 실행은 exit 0과 보고서 생성으로 기록됐지만 본문 검증 결과 유효한 감사표가 아니었고, 같은 bundle SHA의 Qwen 재감사 Run `20261114_014515_cd7cd20d`는 구체적 지적 3건을 반환했습니다. 이전 10/10 PASS는 다른 고정 bundle에만 해당합니다. 기존 원본을 덮어쓰지 마세요.
- 새 실행은 `scripts/codex_hermes_bridge.ps1 -Transport Http`를 사용하고, 문서 감사용 system prompt와 판정 표 형식을 지정하세요. 프로세스 exit 0만으로 감사 PASS를 선언하지 마세요. 스크립트가 종료 청크, 본문, verdict 열 및 완료된 보고서를 확인하고 독립 감사가 PASS를 반환해야 합니다. B-05/B-06 변경분은 별도 번들로 감사하세요.
- 최신 수정 후 인수인계 bundle은 `docs/audit_runs/project_wide_handoff_20261114/current_docs_audit_bundle_v5.md`, manifest는 `current_docs_audit_manifest_v5.json`입니다. 매니페스트의 SHA-256과 실제 bundle을 대조한 뒤 원격 감사를 실행하세요. 이전 입력·실패·잘못된 보고서·WARN 결과는 같은 폴더의 별도 실행 디렉터리에 보존합니다.
- 최종 변경 파일과 관련 테스트/증거를 동결해 Qwen 번들을 만들고 실제 원격 감사를 수행하세요. 감사 미실행·연결 실패는 PASS로 기록하지 마세요.
- 지적사항이 있으면 구현과 분리된 검수 결과를 보존하고, 필요한 수정 후 관련 테스트와 동일 범위 감사를 재실행하세요.
- 최종 회귀 실행 결과, Qwen 결과, UI 검수가 요청된 경우 실제 브라우저 흐름, 한·일 문서 수치·상태·경로 및 작업 트리를 대조한 뒤 완료를 보고하세요.

### 문서 우선순위와 역사 자료

- 현재 작업 기준은 루트 `AGENTS.md`, `scripts/audit_config.json`, 최신 RTM/백로그/증적과 이 인수인계입니다. `docs/workflow/AGENTS_legacy_20261113.md`, Day 8 학습·과거 브리지 보고서, 날짜가 지난 인수인계는 스냅샷으로만 취급하세요. 그 안의 모델명·주소·완료 상태를 현재 지시로 재사용하지 마세요.

### 다음 담당자가 우선 읽을 파일

`AGENTS.md`; `docs/workflow/operating_policy_v2.md`; `docs/workflow/evidence_status_policy.md`; `docs/workflow/isolated_test_operation.md`; `docs/rtm_ledger.md`; `docs/workflow/pipeline_improvement_backlog.md`; `docs/working/day11_task.md`; `docs/final-portfolio/README_KO.md`; `docs/final-portfolio/README.md`; `docs/study_guides/audit_prompt_spec.md`; `docs/workflow/AGENTS_review.md`.

### Project response validation and request handling

[lines 1-18]
param(
    [string]$TargetFile,
    [string]$CustomPrompt = "",
    [string]$SystemPromptOverride = "",
    [string]$OutputFile = "hermes_audit_report.md",
    [string]$Model = "qwen27b:latest",
    [string]$HostUrl = "http://100.111.193.94:11434",
    [int]$NumCtx = 24576,
    [int]$Seed = 42,
    [double]$Temperature = 0.1,
    [string]$SectionFilter = "",
    [string]$LineRange = "",
    [string]$InputManifestPath = "",
    [switch]$Chunk
)

# Set UTF-8 encoding
[Console]::OutputEncoding = [System.Text.Encoding]::UTF8
[lines 66-120]
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
        [Parameter(Mandatory = $true)][string]$Content,
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
[lines 132-180]
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

[lines 200-235]
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

### Bridge route, run safety, hashes and invocation

[lines 1-16]
param(
    [Parameter(Mandatory = $true)]
    [string]$TargetFile,
    [string]$CustomPrompt = "",
    [string]$SystemPromptOverride = "",
    [string]$Model = "",
    [string]$HostUrl = "",
    [string]$OutputRoot = "",
    [string]$RunId = "",
    [ValidateSet("Auto", "Http", "OllamaCli")]
    [string]$Transport = "Http",
    [string]$OllamaPath = "",
    [switch]$Preview
)

$ErrorActionPreference = "Stop"
[lines 45-120]
if (-not $HostUrl) { $HostUrl = [string]$config.primary_auditor.endpoint }
$numCtx = [int]$config.primary_auditor.options.num_ctx
$seed = [int]$config.primary_auditor.options.seed
$temperature = [double]$config.primary_auditor.options.temperature
if (-not $OutputRoot) { $OutputRoot = Join-Path $projectRoot "docs\audit_runs" }
if (-not $OllamaPath) {
    $ollamaCommand = Get-Command ollama -ErrorAction SilentlyContinue
    if ($ollamaCommand) { $OllamaPath = $ollamaCommand.Source }
}

$targetFullPath = (Resolve-Path -LiteralPath $TargetFile).Path
$targetHash = (Get-FileHash -LiteralPath $targetFullPath -Algorithm SHA256).Hash.ToLowerInvariant()
$runnerHash = (Get-FileHash -LiteralPath $auditScript -Algorithm SHA256).Hash.ToLowerInvariant()
$configHash = (Get-FileHash -LiteralPath $configPath -Algorithm SHA256).Hash.ToLowerInvariant()
$customPromptHash = if ($CustomPrompt) {
    Get-TextSha256 -Text $CustomPrompt
} else { 'NOT_SUPPLIED' }
$systemPromptHash = if ($SystemPromptOverride) {
    Get-TextSha256 -Text $SystemPromptOverride
} else { 'DEFAULT_IN_RUNNER' }
$targetSha8 = $targetHash.Substring(0, 8)
$timestamp = Get-Date -Format "yyyyMMdd_HHmmss"
if (-not $RunId) { $RunId = "codex_${timestamp}_${targetSha8}" }
$useCli = ($Transport -eq "OllamaCli")
$resolvedTransport = if ($useCli) { "OllamaCli" } else { "Http" }

$runDir = Join-Path $OutputRoot $RunId
if ($RunId -notmatch '^[A-Za-z0-9._-]+$') { throw 'INVALID_RUN_ID: use only letters, digits, dot, underscore, or hyphen.' }
$resolvedRunDir = [System.IO.Path]::GetFullPath($runDir)
$resolvedOutputRoot = [System.IO.Path]::GetFullPath($OutputRoot).TrimEnd([char[]]@('\','/'))
$outputRootPrefix = $resolvedOutputRoot + [System.IO.Path]::DirectorySeparatorChar
if (-not $resolvedRunDir.StartsWith($outputRootPrefix, [System.StringComparison]::OrdinalIgnoreCase)) {
    throw "INVALID_RUN_ID: run output must remain under OutputRoot."
}
if (Test-Path -LiteralPath $resolvedRunDir) {
    throw "RUN_ID_EXISTS: refusing to overwrite existing audit evidence: $resolvedRunDir"
}
$lockRoot = Join-Path $projectRoot "docs\codex_queue"
$lockDir = Join-Path $lockRoot ".hermes.lock"

New-Item -ItemType Directory -Path $OutputRoot -Force | Out-Null
New-Item -ItemType Directory -Path $lockRoot -Force | Out-Null

try {
    New-Item -ItemType Directory -Path $lockDir -ErrorAction Stop | Out-Null
} catch {
    throw "Another Hermes run appears to be active. Existing lock: $lockDir"
}

try {
    New-Item -ItemType Directory -Path $runDir -Force | Out-Null
    $manifest = [ordered]@{
        run_id = $RunId
        operator = "Codex"
        target_file = $targetFullPath
        target_sha256 = $targetHash
        model = $Model
        host_url = $HostUrl
        options = @{ num_ctx = $numCtx; seed = $seed; temperature = $temperature }
        started_at_local = (Get-Date).ToString("o")
        status = "STARTED"
        source_script = $auditScript
        source_script_sha256 = $runnerHash
        config_file = $configPath
        config_sha256 = $configHash
        custom_prompt_sha256 = $customPromptHash
        system_prompt_override_sha256 = $systemPromptHash
        transport_requested = $Transport
        ollama_path = $OllamaPath
        output_dir = $runDir
        transport_used = $resolvedTransport
    }
    $manifest | ConvertTo-Json -Depth 5 | Set-Content -LiteralPath (Join-Path $runDir "run_manifest.json") -Encoding UTF8

    if ($Preview) {
        $manifest.status = "PREVIEW_ONLY"
[lines 130-170]
    if ($useCli) {
        $manifest.transport_used = "OllamaCli"
        $installedModels = @(& $OllamaPath list 2>&1)
        if ($LASTEXITCODE -ne 0) { throw "Cannot verify locally installed models; refusing to invoke Ollama CLI." }
        $escapedModel = [regex]::Escape($Model)
        if (-not ($installedModels | Where-Object { $_ -match "^\s*$escapedModel(?:\s|$)" })) {
            throw "LOCAL_MODEL_NOT_INSTALLED: refusing to pull or download '$Model'."
        }
        $content = [System.IO.File]::ReadAllText($targetFullPath, [System.Text.Encoding]::UTF8)
        $auditPrompt = if ($SystemPromptOverride) { $SystemPromptOverride } else { @"
You are an expert Spring Boot architecture and Japanese Labor Law auditor.
Respond 100% in English and use a Markdown table.
Audit Japanese Labor Standards Act Article 37 calculations, payroll rounding, transaction and locking behavior, IDOR protection, exception handling, and boundary validation.
Do not trust prior conclusions. Use only the supplied target content. State PASS, WARN, FAIL, or NOT_VERIFIABLE and cite concrete evidence.
"@ }
        if ($CustomPrompt) { $auditPrompt += "`n$CustomPrompt`n" }
        $auditPrompt += "`n[Target: $targetFullPath]`n$content"
        $cliOutput = @($auditPrompt | & $OllamaPath run $Model 2>&1)
        if ($LASTEXITCODE -and $LASTEXITCODE -ne 0) {
            throw "Ollama CLI exited with code $LASTEXITCODE"
        }
        $cliText = ($cliOutput -join [Environment]::NewLine).Trim()
        if (-not $cliText) { throw "Ollama CLI returned an empty response" }
        [System.IO.File]::WriteAllText($reportPath, $cliText, [System.Text.Encoding]::UTF8)
    } else {
        $manifest.transport_used = "Http"
        $invokeArgs = @{
            TargetFile = $targetFullPath
            OutputFile = $reportPath
            Model = $Model
            HostUrl = $HostUrl
            NumCtx = $numCtx
            Seed = $seed
            Temperature = $temperature
        }
        if ($CustomPrompt) { $invokeArgs.CustomPrompt = $CustomPrompt }
        if ($SystemPromptOverride) { $invokeArgs.SystemPromptOverride = $SystemPromptOverride }
        $global:LASTEXITCODE = 0
        & $auditScript @invokeArgs
        if ($LASTEXITCODE -and $LASTEXITCODE -ne 0) {
            throw "Hermes audit script exited with code $LASTEXITCODE"

### RTM status rows

| **R-02** | 최종 재조회 이후 신규 DRAFT 경쟁 조건 재현 및 격리 검증과 업무 정책 채택 | T1/T2 경쟁의 기술적 격리 실측, 경계 시 롤백 정책 문서화 및 사용자 채택 결정 기록 | 신규 DRAFT의 자동 확정 방지 및 fail-fast 전체 롤백 실측, 업무 정책 승인 상태 추적 | JUnit XML, DB 상태 증거, `docs/f04_evidence/r02_business_policy.md` | `PayrollService.java`, `PayrollMapper.xml`, `PayrollMonthlyConfirmPostQueryRaceConditionIntegrationTest.java` | **VERIFIED (기술 검증 및 정책 채택)** | 사용자께서 2026-11-14 스냅샷 고정 및 신규 DRAFT 경계 감지 시 전체 롤백 정책을 채택했습니다. 최신 전체 회귀에서 R-02 통합 테스트 2/2 PASS, 실패·오류 0. 정책 채택·제외 범위 정합성 Qwen Run `20261114_010419_130b40f4` 8/8 PASS. 격리 DB `kintai_full_test_d4124a317ed0`; 운영 DB 미사용. |
| **R-05** | MySQL 8.4 LTS 최종 검증 및 26.7 분리 | 로컬 `MySQL 26.7.0 Innovation` 결과와 `MySQL 8.4 LTS` 결과를 혼합하지 않고 각각 분리 기록 | `SELECT VERSION()` 실측치 명시 및 8.4 LTS 전용 검증 분리 | DB 버전 출력, EXPLAIN 결과 분리 파일 | `docs/f04_evidence/` | **DEFERRED (2027-01)** | MySQL 8.4/Testcontainers는 사용자 지시에 따라 현재 작업 범위에서 제외. Docker/Podman 부재로 미실행 상태를 보존하고 예정된 재검토일에 다시 판단 |
| **R-08** | 출퇴근 CLOCK_IN 동시 요청 중복 생성 차단 및 비관적 락 정합성 실측 | 동일 직원 동시 CLOCK_IN에서 성공 1건·업무 거부 1건 및 중복 미생성을 검증한다. | 2개 스레드 결과, DB 상태, 격리 DB 증거와 반복 로그 | `AttendanceService.java`; `AttendanceMapper.xml`; `AttendanceServiceConcurrencyIntegrationTest.java`; `docs/audit_runs/day10_isolated_regression_20261114_093404/` | **VERIFIED (로컬 MySQL 26.7)** | 최종 소스에서 신규 격리 DB 3개 각각 2건 테스트, 총 6건 PASS, 실패·오류 0. 요약과 JUnit SHA-256은 run 폴더 `summary.json`. 최신 전체 회귀(2026-11-14)는 92 PASS. 운영 DB 미사용. |
| **R-09** | 출퇴근 CLOCK_OUT 동시 요청 완료 상태 보존 및 중복 퇴근 거부 실측 | 동일 직원 진행 근태의 동시 CLOCK_OUT에서 성공 1건·거부 1건, 최종 COMPLETED를 검증한다. | 2개 스레드 결과, DB 최종 상태, 격리 DB 증거와 반복 로그 | `AttendanceService.java`; `AttendanceMapper.xml`; `AttendanceServiceConcurrencyIntegrationTest.java`; `docs/audit_runs/day10_isolated_regression_20261114_093404/` | **VERIFIED (로컬 MySQL 26.7)** | 최종 소스에서 신규 격리 DB 3개 각각 CLOCK_OUT 테스트 포함 2건 PASS, 총 6건 PASS, 실패·오류 0. 최신 전체 회귀(2026-11-14)는 92 PASS. 운영 DB 미사용. |
| **R-10** | Day 10 출퇴근 동시성 독립 Qwen 감사 및 감사관 지적 해소 | 최신 근태 타각·인증·입력 경계 구현과 반복 검증을 독립 감사한다. | 현행 소스 번들, 실행 결과, A-05/A-06 및 재검수 결과 보존 | `docs/audit_runs/prior_residuals_20261114/qwen_day10_final/hermes_final/`; bundle SHA-256 `dd991d3ca2ab013943b9f6e7f5b676524b4e77df34d67fe77e881586f03aa7c8` | **VERIFIED (로컬 MySQL 26.7 범위)** | 원격 `http://100.111.193.94:11434`, `qwen27b:latest`, Run `20261114_003657_dd991d3c`. 10 findings 중 PASS 9, N/A 1(급여 계산은 Day10 근태 타각 감사 범위 밖), WARN/FAIL 0. 기존 A-05/A-06을 세션 ID 강제 바인딩/소유권 검사, 실제 필터 미인증 요청 테스트, 입력 거부 및 Mapper 무호출로 해소. report SHA-256 `31e8e18609ec458e8e589888646a05502f1f37c269bc4820b25df6bdb62bf430`. |
| **R-11** | Day 11 12개월 급여 계산 시나리오 및 계약 유효기간 | 월별 근무 분포, 계약 이력, API/Service 경로와 급여를 대조하고 독립 연간 합계를 검증한다. | 12개월×3근무일, 계약 임금 변경, 독립 연간 oracle, 타각 HTTP/API 경로, JUnit 및 격리 DB 로그 | `AnnualPayrollScenarioIntegrationTest.java`; `AttendancePunchApiIntegrationTest.java`; `docs/audit_runs/isolated_day11_20261114_093228_kintai_day11_test_3a25e5513c4f/`; `docs/audit_runs/isolated_all_20261114_094427_kintai_full_test_d4124a317ed0/`; remote Qwen Run `20261114_004900_46049b14` | **VERIFIED (로컬 MySQL 26.7)** | 12개월 각각 3일의 근태 및 독립 연간 oracle 대조 PASS, 계약 경계 6/30·7/1 PASS. 실제 Spring Security 필터→Controller→Service→DB 타각 API spoofed ID 차단·저장 검증 PASS(전용 통합 테스트 및 R-10 재감사 증적 참조). 최신 전체 회귀 92 PASS. Qwen R-11/R-12 소스 감사 12/12 PASS, WARN/FAIL 0. |
| **R-12** | Day 11 경계·결함 시나리오 | 야간·다회 휴게·계약 경계·동일일 분할 근무 8시간 누적·월마감·반올림을 독립 기대값으로 검증한다. | 각 경계의 통합/단위 테스트, 저장된 실행 및 JUnit | `AnnualPayrollScenarioIntegrationTest.java`; `LaborCalculationEngineTest.java`; `SplitShiftOvertimeDefectTest.java`; `PayrollCalculatorStatutoryRoundingTest.java`; `docs/audit_runs/isolated_day11_20261114_094349_kintai_day11_test_01f4cb1439e6/`; `docs/audit_runs/isolated_all_20261114_094427_kintai_full_test_d4124a317ed0/` | **VERIFIED (로컬 MySQL 26.7)** | 4h+5h 분할근무 후 CLOCK_OUT service에서 60분 초과 산출 및 월 급여 반영 PASS. 20:00~05:00 야간 420분 중 연장 60분 중첩의 25%+25%=50% 프리미엄 및 급여 합계 PASS. 1/31 포함·2/1 제외, 계약·자정·다회휴게·야간휴게 차감·HALF_UP 경계 PASS. Qwen Run `20261114_004900_46049b14` 12 findings PASS, WARN/FAIL 0. 전체 회귀 92 PASS. |
| **R-13** | Day 11 격리 실행 및 회귀 증거 무결성 | 새 격리 DB에서 대상 테스트와 전체 회귀를 실행하고 로그·JUnit·요약·해시를 보존한다. | 대상 테스트 및 전체 회귀 종료 코드 0, 격리 DB 및 매니페스트 대조 | `docs/audit_runs/isolated_day11_20261114_094349_kintai_day11_test_01f4cb1439e6/`; `docs/audit_runs/isolated_all_20261114_094427_kintai_full_test_d4124a317ed0/` | **VERIFIED (로컬 MySQL 26.7)** | 최신 대상 연간·경계 테스트 6 PASS, 전체 회귀 92 PASS, 실패·오류·스킵 0, Gradle exit 0. 이전 81건 결과는 당시 스냅샷으로 보존. MySQL 8.4 Testcontainers 결과로 일반화하지 않음. |
| **R-14** | Day 11 독립 검수 및 추적 문서 정합성 | Qwen 입력·결과를 보존하고 지적사항 및 한계를 한·일 증적·RTM·인수인계에 반영한다. | Run ID·입력 해시·결과·지적별 대조와 일치 문서 | `docs/audit_runs/preflight_1e3b8fba040b4197aed07afbde9334f2/`; final document audit `docs/audit_runs/preflight_9c8fda322194453ba642d56325a31d10/`; 한·일 portfolio evidence; `docs/handoff/20261114_residuals_handoff.md` | **VERIFIED (2026-11-14 source/document scope)** | Qwen 27B 원격 Run `20261114_004900_46049b14`, 입력 bundle SHA-256 `46049b14578b32462a6f105966643d658919d5ed658faaf885796e2e1cd2c4b8`, report SHA-256 `b1ae1c9d7844d92b6ab4b47bf5e5c66dbb0f1f3f916549688e3c83ea56798f10`. 12/12 PASS, WARN/FAIL 0. 기존 결함 F-03 중첩 프리미엄 경고에 통합 단언 추가 후 새 격리 DB 대상 6/6 PASS 및 전체 92/92 PASS. 한·일 증적과 RTM/인수인계 수치 대조 완료. 최종 고정 문서 독립 감사 Run `20261114_005527_76dc1c9f`: 10/10 PASS, WARN/FAIL 0. 앞선 2개 문서 경고의 수정 확인. bundle SHA-256 `76dc1c9f5b3750c9b38d0777bd302893d4ea4dbe3783a0b8ef107edf37312d93`, report SHA-256 `dee296cda885e4bdc8208c9145ab61757d6a5323ecd6f7c9b21016b2cfc6b124`. |
| **R-15** | Day 13 격리 데모 데이터 생성 및 연간 분포 | 2026년 synthetic schedule/attendance fixture를 신규 격리 demo DB에 적재하고 DB 식별자·기간·행 수·UTF-8 샘플명을 대조한다. | `SELECT DATABASE()` 및 기간/건수/문자 인코딩 확인, 화면에서 해당 데이터 조회 | `scripts/prepare_local_demo_db.ps1`; `scripts/local_demo_attendance.sql`; `docs/audit_runs/day13_calendar_20261114/` | **VERIFIED (로컬 MySQL 26.7)** | 최종 DB `kintai_demo_9f3f8487bc42`: confirmed shift 313건과 attendance 313건, 2026-01-01~2026-12-31, owner-name UTF-8 HEX 확인. 초기 DB의 손상된 이름은 보존하고 새 DB로 대체. 운영 DB 사용 안 함. |
| **R-16** | Day 13 캘린더 API DTO와 브라우저 표시·상세 정합성 | CalendarEventDto 응답을 그대로 표시하고 상세/확정 모달에서 이벤트 기간·휴게 시간을 사용한다. | 독립 API 응답과 브라우저 월간 이벤트·상세 모달 실측, 전체 회귀 | `calendar.html`; `CalendarEventDto.java`; `docs/audit_runs/isolated_all_20261114_074456_kintai_full_test_3df696be0820/` | **VERIFIED (로컬 브라우저·MySQL 26.7)** | API 필드명과 화면 매핑 불일치를 수정. November 2026 confirmed events 및 11/02 상세 모달(09:00~18:00, 60분) 화면 확인. |
| **R-17** | Day 13 급여 데모 및 Excel 다운로드 확인 | 격리 데이터에서 급여 계산과 UI 표시를 확인하고 Excel 산출을 검증한다. | 화면 급여 값·이름·상태, API/XLSX 산출, 실제 브라우저 다운로드 이벤트 및 저장 파일 검사, 회귀 | `docs/audit_runs/day13_calendar_20261114/ui_review_record.md`; `payroll_api_list.json`; `payroll_2026-01_browser_download.xlsx`; `excel_browser_download.json`; `payroll_excel_validation.json` | **VERIFIED (로컬 MySQL 26.7)** | 격리 DB 급여 UI에서 두 DRAFT 행과 급여액을 확인하고 Excel 출력을 실행했다. 브라우저 다운로드 이벤트가 완료되어 5,092-byte 파일을 확보, OOXML·월·¥141,700·¥164,332를 검증했다. 파일 SHA-256 `ba127f65f193f8289c2b4e445efc09a06e1590e2230ed0b1ad603d839cfdcd98`. Chrome 과거 차단은 이력으로 보존한다.
| **R-18** | Day 13 독립 Qwen 감사 및 증적 추적 | 갱신된 번들·실행 결과·UI 증거를 포함해 Hermes Qwen 27B 독립 감사를 수행하고 지적을 반영한다. | 유효 입력 manifest, 독립 결과 원문/해시, 지적과 조치·재검수 일치 | `docs/audit_runs/day13_calendar_20261114/qwen27b_day13_final/report.md`; `qwen27b_day13_final/audit_run.json`; bundle SHA-256 `d69a9b63c9a6a79043a38fb9c8942ab9c3fd0f9936087ffe5865d778f6817379` | **VERIFIED (로컬 MySQL 26.7 범위)** | 원격 `http://100.111.193.94:11434`, `qwen27b:latest` IQ3_M 27.3B, digest `23c3820b0d9326b73e97191b9fb7e16b53f6e24bb3449d2a17037c5f2b6179a1`; Run ID `20261113_234922_d69a9b63`; report SHA-256 `0dc324150a75bb8a1d3035f7ee770a03ec2bffd8241a3a041ffd9ee2066fbf32`. 최신 번들 15개 감사 finding 모두 PASS, WARN/FAIL 0. R-17 브라우저 다운로드 이벤트와 저장 XLSX 내용 검증을 포함한다. MySQL 8.4는 2027-01 DEFERRED.
| **R-19** | Day 13 최종 포트폴리오 패키지 | 기술 증적, 아키텍처 그림, 데모 안내 및 한·일 요약을 실제 결과에 맞게 작성한다. | 문서·링크·상태·수치를 RTM/원본 증거와 대조하고 로컬 문서 검사 및 독립 감사를 보존한다. | `docs/final-portfolio/`; `docs/audit_runs/day13_calendar_20261114/final_portfolio_review/hermes_final_snapshot/` | **VERIFIED** | 상대 Markdown 링크 검사 PASS. 원격 Qwen 27B Run `20261114_000458_4af20cd9`, 15 finding PASS, WARN/FAIL 0. 번들 SHA-256 `4af20cd936e1a70c91ca8d6b905f0d6f948d108b1c09fd226958a2a6c2b3b543`; report SHA-256 `4876963e92a4cb197596d02fb5d172a50db4a13b7b382930c312e433337d1d28`. 최초 감사 WARN는 Mapper/Service 잠금 증거를 추가해 해소하고 재검수 PASS. |

### Backlog B status rows

| **B-01** | Day 8 F-01, F-02 | Qwen 27B가 일본 노동기준법 제37조 할증률(심야 25%, 연장 25%)을 50%, 75%로 환각 오판함 | **공통 시스템** | 정확성 | 2회 (반복) | `docs/temp_audit_day8_bundle.java`<br>`docs/audit_ledger.csv` | 추후 개선 (프롬프트/도메인 지식 팩 주입) | 법률/수치 감사 시 조문 출처 없는 환각 주장을 0건으로 억제하고 정답 케이스 5건 100% 통과 | 대기 (마일스톤 시 검토) |
| **B-02** | Day 8 F-04 | `confirmPayroll`과 `calculatePayrollForStaff` 간 락 획득 순서 역전으로 인한 MySQL 1213 데드락 | **프로젝트 결함** | 정확성, 안정성 | 1회 (재현) | `docs/f04_evidence/show_engine_innodb_status_deadlock.txt` | **즉시 수정 완료** | `confirmPayroll` 전역 락 순서 일치 및 동시 실행 시 1213 데드락 0건 실측 | **완료 (PASS-VERIFIED)** |
| **B-03** | Day 8 F-04 증적 | 디버깅 중 transient Thread ID(1114)가 공식 보존 로그(1112/1113)와 혼선될 뻔함 | **공통 시스템** | 재현성, 신뢰성 | 1회 | `docs/f04_evidence/deadlock_execution_ledger.md` | **즉시 수정 완료** | 공식 실행 번호, SHA-256, Connection ID를 원본과 1:1 매핑 분리 기록 | **완료 (해소됨)** |
| **B-04** | Day 8 F-04 배치 | `confirmMonthlyPayroll` 일괄 확정 시 다중 행 물리적 락 순서 미보장 (`ORDER BY`의 락 순서 불일치) | **프로젝트 결함** | 안정성 | 1회 (설계) | `docs/f04_evidence/lock_architecture_analysis.md`, `docs/rtm_ledger.md`, 최신 전체 회귀 증적 | 대상 DRAFT 직원만 동적 추출 후 ID 오름차순 2단계 선점 잠금 구현 및 다건 동시성 테스트 통과 | **완료 (VERIFIED)** |
| **B-05** | Day 8 F-07 | YearMonth 파라미터 유효성 검증 부재 및 HTTP 400 예외 매핑 미비 | **프로젝트 결함** | 안정성 | 1회 | `PayrollApiController.java` | 추후 개선 | 올바르지 않은 YYYY-MM 입력 시 400 Bad Request 반환 컨트롤러 단위 테스트 3건 통과 | 대기 |
| **B-06** | Day 8 F-10 | 급여 명세 상세 응답 DTO에서 민감정보(계좌번호, 마이넘버) 노출 위험 방어 | **프로젝트 결함** | 보안성 | 1회 | `PayrollDetailResponseDto.java` | 추후 개선 | 응답 JSON 직렬화 패킷 검증 테스트에서 민감 필드 누락/미포함 확인 | 대기 |
| **B-07** | 감사 파이프라인 | 로컬 감사 스크립트 실행 메트릭의 수동 마크다운 표 변환 공수 발생 | **개선 아이디어** | 시간, 편의성 | 다수 | `audit_with_hermes.ps1` | 추후 개선 | 스크립트 출력 결과에 한글 마크다운 표 블록을 자동 생성 옵션으로 내장 | 대기 (마일스톤 시 검토) |
| **B-08** | 파이프라인 헌법 준수 | 개발자 셀프 판정 오류(로컬 27B 감사 생략 후 외부 제출 시도) 발생 | **공통 시스템** | 신뢰성, 절차 무결성 | 1회 | 대화 로그 및 `qwen_final_audit_report.md` | **즉시 해결 완료** | 사용자의 지적 즉시 Qwen 27B 독립 감사 번들 투입 ➔ 공식 PASS 획득 후 외부에 보고하는 정석 순서 복원 및 `daily_audit_temp.log`에 실측치 누적 | **완료 (운영 규칙 확립)** |
| **B-09** | Project-wide handoff audit (2026-11-14) | API 오류의 false exit-0 외에 빈/미완료/잘린 응답·프롬프트와 충돌하는 감사 역할·잘못된 verdict 보고서가 성공처럼 기록될 수 있고, bridge Auto가 로컬 Ollama를 선택할 수 있음 | **공통 시스템** | 신뢰성·절차 무결성 | 1회 | `docs/audit_runs/project_wide_handoff_20261114/`; `scripts/audit_with_hermes.ps1`; `scripts/codex_hermes_bridge.ps1` | 지정 원격 HTTP 및 설정 inference 옵션, 작업별 system prompt, 입력 토큰 예산, 종료 청크·본문·verdict 표 검증, 잘못된 입력/기존 출력/중복 run ID 거부, 해시 포함 실행 증거 | 빈·잘림·표/프롬프트 오류 테스트, 기존 증거 보존, HTTP/Auto 원격 preview, PowerShell parser와 wrapper/preflight mock, 정확한 원격 Qwen 독립 재검수 | **수정 중 (최종 독립 재검수 대기)** |
### B-09 현재 실행 결과 (2026-11-14)

### Local runner test summary

[lines 1-]
{
}
[lines 38-]
      "file": "scripts/test_qwen_wrapper_mock.ps1",
    {
    },
      "errors": []
      "result": "PASS",
      "file": "scripts/test_codex_hermes_bridge_routing.ps1",
    {
    },
      "errors": []
      "result": "PASS",
      "file": "scripts/test_audit_response_validation.ps1",
    {
    },
      "errors": []
      "result": "PASS",
      "file": "scripts/audit_hermes_preflight.ps1",
    {
    },
      "errors": []
      "result": "PASS",
      "file": "scripts/run_codex_hermes_host.ps1",
    {
    },
      "errors": []
      "result": "PASS",
      "file": "scripts/codex_hermes_bridge.ps1",
    {
    },
      "errors": []
      "result": "PASS",
      "file": "scripts/audit_with_hermes.ps1",
    {
  "parser": [
  "model": "qwen27b:latest",
  "endpoint": "http://100.111.193.94:11434",
  "checked_at": "2026-11-14T11:06:46.3772130+09:00",
  "run_id": "runner_validation_20261114_111800",
{
}

### Application execution evidence

Latest isolated full regression: 92 PASS, failures/errors/skips 0; database kintai_full_test_d4124a317ed0. Day 11 target suite: 6/6 PASS; isolated database kintai_day11_test_01f4cb1439e6. R-05 and Day 12 excluded. B-05/B-06 remain open.

### Prior audit history

Prior Qwen review run 20261114_014515_cd7cd20d returned WARN for chronology/status accuracy, malformed output handling, and overbroad fail-closed claim. The updated runner added response validation and local tests. A subsequent attempt against v4 failed HTTP 400 before generation (prompt/eval token counts 0); it is not an audit verdict. The 10/10 result applied only to its own older frozen bundle.

### Current post-v7 correction and verification (authoritative)
Source: scripts/audit_with_hermes.ps1; SHA-256: b055c1e01285e56684abd13b23644082b2208c8d50fdfae7a119d739d216987b
[lines 1-18]
param(
    [string]$TargetFile,
    [string]$CustomPrompt = "",
    [string]$SystemPromptOverride = "",
    [string]$OutputFile = "hermes_audit_report.md",
    [string]$Model = "qwen27b:latest",
    [string]$HostUrl = "http://100.111.193.94:11434",
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
[lines 97-120]

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
[lines 150-168]
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

Source: scripts/codex_hermes_bridge.ps1; SHA-256: 227d4a2ab719c363c3307e29e18b2d64ca97b46a11b4200091970e65ca0d0372
[lines 44-53]
if (-not $Model) { $Model = [string]$config.primary_auditor.model_tag }
if (-not $HostUrl) { $HostUrl = [string]$config.primary_auditor.endpoint }
$numCtx = [int]$config.primary_auditor.options.num_ctx
$seed = [int]$config.primary_auditor.options.seed
$temperature = [double]$config.primary_auditor.options.temperature
$think = [bool]$config.primary_auditor.think
if (-not $OutputRoot) { $OutputRoot = Join-Path $projectRoot "docs\audit_runs" }
if (-not $OllamaPath) {
    $ollamaCommand = Get-Command ollama -ErrorAction SilentlyContinue
    if ($ollamaCommand) { $OllamaPath = $ollamaCommand.Source }
[lines 95-118]
try {
    New-Item -ItemType Directory -Path $runDir -Force | Out-Null
    $manifest = [ordered]@{
        run_id = $RunId
        operator = "Codex"
        target_file = $targetFullPath
        target_sha256 = $targetHash
        model = $Model
        host_url = $HostUrl
        options = @{ num_ctx = $numCtx; seed = $seed; temperature = $temperature; think = $think }
        started_at_local = (Get-Date).ToString("o")
        status = "STARTED"
        source_script = $auditScript
        source_script_sha256 = $runnerHash
        config_file = $configPath
        config_sha256 = $configHash
        custom_prompt_sha256 = $customPromptHash
        system_prompt_override_sha256 = $systemPromptHash
        transport_requested = $Transport
        ollama_path = $OllamaPath
        output_dir = $runDir
        transport_used = $resolvedTransport
    }
    $manifest | ConvertTo-Json -Depth 5 | Set-Content -LiteralPath (Join-Path $runDir "run_manifest.json") -Encoding UTF8
[lines 150-169]
            throw "Ollama CLI exited with code $LASTEXITCODE"
        }
        $cliText = ($cliOutput -join [Environment]::NewLine).Trim()
        if (-not $cliText) { throw "Ollama CLI returned an empty response" }
        [System.IO.File]::WriteAllText($reportPath, $cliText, [System.Text.Encoding]::UTF8)
    } else {
        $manifest.transport_used = "Http"
        $invokeArgs = @{
            TargetFile = $targetFullPath
            OutputFile = $reportPath
            Model = $Model
            HostUrl = $HostUrl
            NumCtx = $numCtx
            Seed = $seed
            Temperature = $temperature
            Think = $think
        }
        if ($CustomPrompt) { $invokeArgs.CustomPrompt = $CustomPrompt }
        if ($SystemPromptOverride) { $invokeArgs.SystemPromptOverride = $SystemPromptOverride }
        $global:LASTEXITCODE = 0
Source: docs/handoff/20261114_antigravity_project_wide_handoff.md; SHA-256: 2c9b036c7c712fa3c7aac9e70e10b83ed151acab56d1d67ff25730b1d3184e72
[lines 48-]

### 4. 독립 Qwen 27B 감사 및 최종 확인

- 범위를 넘어 법률 완전 준수를 주장하지 마세요. SRS가 제외한 주 40시간, 법정휴일, 월 60시간 초과·세무 영역을 PASS로 표시하지 마세요.
- Day 13의 83건은 과거 체크포인트입니다. 최신 92건 결과를 최신값으로 쓰고 과거 원본은 수정하지 마세요. MySQL 버전, DB명, 실행 결과, 감사 run ID는 원본 증거와 대조하세요.
- B-05/B-06의 구현·테스트·상태를 RTM 또는 적절한 프로젝트 추적 문서, 한국어/일본어 누적 증적, 작업표와 인수인계서에서 동일하게 반영하세요.
- B-04는 ID 오름차순 2단계 잠금과 동시성 테스트가 끝났으므로 완료로 기록하세요. 이 handoff 작성 시 `docs/workflow/pipeline_improvement_backlog.md`에서 완료로 정정했으며 RTM/92건 회귀 증거를 연결했습니다.

### 3. 증적과 범위 동기화

- 노출이 확인될 경우에만 근본 원인과 최소 수정을 수행하고, 데이터베이스 실데이터/운영 DB는 사용하지 마세요.
- 민감 필드가 JSON 응답에 포함되지 않는 serializer/controller 회귀 테스트를 추가하세요. 테스트는 DTO 선언만 보지 말고 실제 JSON 직렬화 결과를 확인해야 합니다.
- `PayrollDetailResponseDto.java`와 직렬화 경로를 확인하세요. 현재 조사에서는 필요한 필드만 가진 명시적 DTO로 확인되어 계좌번호/마이넘버 노출의 직접 증거는 없습니다.

### 2. B-06: 급여 상세 응답의 데이터 최소화 증거

- 요청 바인딩 수준을 포함하는 controller/API 통합 테스트로 입증하세요. 코드 변경 시 관련 테스트, 격리 타깃 실행, 전체 격리 회귀를 실행합니다.
- 잘못된 달(예: `2026-13`, `2026-1`, `not-a-month`)이 계산/확정으로 진행되지 않고 일관된 400 응답을 반환하는지 테스트하세요. 기존 API 계약과 오류 형식은 보존하세요.
- `PayrollApiController.java`의 급여 `/list`, `/calculate`, `/confirm-month` 입력 검증을 확인하세요. 현재 원장은 이 라우트들의 엄격한 `YYYY-MM` 검사와 HTTP 400 컨트롤러 테스트가 남았다고 기록합니다. `/excel`의 현재 검증과 응답 동작을 먼저 대조하세요.

### 1. B-05: YearMonth 입력 경계

## 실행 순서

- B-02, B-03, B-08은 백로그에서 완료 상태입니다. B-09에는 원격 오류뿐 아니라 빈 응답·종료 청크 누락·잘못된 verdict 표·잘못된 대상 경로의 fail-closed 확인이 포함됩니다. 최신 코드와 실행 증거는 백로그 및 `docs/audit_runs/project_wide_handoff_20261114/`를 확인하세요. R-05와 Day 12는 제외 상태이므로 현재 구현 대상으로 넣지 마세요.
- B-01(독립 감사 정확도 벤치마크)과 B-07(감사 메트릭 마크다운 자동화)은 범용 파이프라인 개선 백로그로서 마일스톤 검토 대기입니다. 현재 기능 검증을 막는 결함으로 취급하지 말고, 사용자 일정에 맞춰 별도 우선순위를 정하세요.
- 즉시 넘길 프로젝트 코드 작업은 B-05와 B-06입니다. B-04는 VERIFIED 완료입니다.

## 잔여 백로그 구분

5. 기존 실패 로그와 증거는 덮어쓰거나 삭제하지 말고 새 실행 디렉터리에 보존하세요.
4. 외부 배포, 원격 Git 반영, 메시지 전송은 하지 마세요. 해당 작업은 사용자 지시가 별도로 필요합니다.
3. 원격 Qwen 감사는 `http://100.111.193.94:11434`, `qwen27b:latest`만 사용하세요. 저장소 실행 진입점은 `scripts/codex_hermes_bridge.ps1`이며 HTTP 전송을 기본값으로 사용하고 `scripts/audit_config.json`에서 주소와 모델을 읽습니다. 문서 감사에는 `-SystemPromptOverride`로 작업에 맞는 지시를 지정하세요. 로컬 7B 대체, 다른 IP, 모델 pull/download/install 금지. 연결 실패 시 원본 주소와 오류를 보존하고 미검증으로 남기세요.
2. 운영 DB는 사용 금지. 대상/전체 테스트와 시연마다 새 allowlisted 격리 DB를 사용하고 DB명을 증거로 확인하세요. 기존 격리 DB도 삭제하지 마세요.
1. R-05(MySQL 8.4 Testcontainers) 및 Day 12 작업은 사용자 지시에 따라 예정일까지 제외합니다. 지금 실행하지 마세요.

## 고정 범위와 실행 제한

- 이전 독립 감사: Qwen 27B 소스 Run `20261114_004900_46049b14` 12/12 PASS; 고정 문서 Run `20261114_005527_76dc1c9f` 10/10 PASS. 두 결과는 당시 입력 bundle에만 유효합니다. 프로젝트 전체 문서 bundle SHA `cd7cd20d12ba7771fb8ceb01dcd95181bfe39c924b8a595aa5e6fb91fdf649b8`는 첫 연결 실패 뒤 잘못된 형식의 보고서가 exit 0으로 생성됐고, 독립 재감사 Run `20261114_014515_cd7cd20d`는 WARN을 반환했습니다. 이 결과들을 PASS로 합산하지 않습니다.
- R-02 정책 채택 및 기술 검증 완료. R-11/R-12/R-14는 명시된 로컬 MySQL 26.7 범위에서 VERIFIED.
- Day 11 연간·경계 대상 실행: 6/6 PASS, 격리 DB `kintai_day11_test_01f4cb1439e6`; `docs/audit_runs/isolated_day11_20261114_094349_kintai_day11_test_01f4cb1439e6/`.
- 최신 격리 전체 회귀: 92 PASS, 실패 0, 오류 0, 스킵 0, Gradle exit 0. DB `kintai_full_test_d4124a317ed0`; `docs/audit_runs/isolated_all_20261114_094427_kintai_full_test_d4124a317ed0/`.

Codex가 2026-11-14 기준 저장소·증적·잔여 작업을 대조한 인수인계입니다. Antigravity에서 아래 남은 작업을 순서대로 수행해 주세요. 저장소에는 사용자 및 이전 작업자의 미커밋 변경과 감사 증적이 많습니다. 기존 변경을 보존하고 reset/revert/clean을 하지 마세요.

## 목적과 기준 상태

# Antigravity project-wide handoff (2026-11-14)
`AGENTS.md`; `docs/workflow/operating_policy_v2.md`; `docs/workflow/evidence_status_policy.md`; `docs/workflow/isolated_test_operation.md`; `docs/rtm_ledger.md`; `docs/workflow/pipeline_improvement_backlog.md`; `docs/working/day11_task.md`; `docs/final-portfolio/README_KO.md`; `docs/final-portfolio/README.md`; `docs/study_guides/audit_prompt_spec.md`; `docs/workflow/AGENTS_review.md`.
[lines 58-]
Qwen Run `codex_20261114_111907_v7`에서 지정 원격의 응답이 종료 청크와 함께 도착했으나 `message.content`가 비어 있어 검증기가 PowerShell 매개변수 바인딩 오류로 종료했습니다. 설정의 `think:false`가 `/api/chat` 요청 본문에 전달되지 않은 것이 원인이었습니다. 이를 전달하도록 수정하고 빈 문자열 응답을 fail-closed 검증기로 넘기도록 보정했습니다. 새 로컬 검증은 `docs/audit_runs/project_wide_handoff_20261114/runner_validation_20261114_112300/`이며, 새 독립 감사의 최종 판정은 번들 SHA와 일치하는 실행 증거로 확인해야 합니다.

## Codex 감사 실행기 보정 기록

- 최종 회귀 실행 결과, Qwen 결과, UI 검수가 요청된 경우 실제 브라우저 흐름, 한·일 문서 수치·상태·경로 및 작업 트리를 대조한 뒤 완료를 보고하세요.
- 지적사항이 있으면 구현과 분리된 검수 결과를 보존하고, 필요한 수정 후 관련 테스트와 동일 범위 감사를 재실행하세요.
- 최종 변경 파일과 관련 테스트/증거를 동결해 Qwen 번들을 만들고 실제 원격 감사를 수행하세요. 감사 미실행·연결 실패는 PASS로 기록하지 마세요.
- 최신 수정 후 인수인계 bundle은 `docs/audit_runs/project_wide_handoff_20261114/current_docs_audit_bundle_v8.md`, manifest는 `current_docs_audit_manifest_v8.json`입니다. 매니페스트의 SHA-256과 실제 bundle을 대조한 뒤 원격 감사를 실행하세요. 이전 입력·실패·잘못된 보고서·WARN 결과는 같은 폴더의 별도 실행 디렉터리에 보존합니다.
- 새 실행은 `scripts/codex_hermes_bridge.ps1 -Transport Http`를 사용하고, 문서 감사용 system prompt와 판정 표 형식을 지정하세요. 프로세스 exit 0만으로 감사 PASS를 선언하지 마세요. 스크립트가 종료 청크, 본문, verdict 열 및 완료된 보고서를 확인하고 독립 감사가 PASS를 반환해야 합니다. B-05/B-06 변경분은 별도 번들로 감사하세요.
- 기존 bundle `current_docs_audit_bundle_final.md`의 최초 지정 주소 호출은 `GetRequestStream: Unable to connect to the remote server`로 실패했습니다. 후속 실행은 exit 0과 보고서 생성으로 기록됐지만 본문 검증 결과 유효한 감사표가 아니었고, 같은 bundle SHA의 Qwen 재감사 Run `20261114_014515_cd7cd20d`는 구체적 지적 3건을 반환했습니다. 이전 10/10 PASS는 다른 고정 bundle에만 해당합니다. 기존 원본을 덮어쓰지 마세요.

### 4. 독립 Qwen 27B 감사 및 최종 확인

- 범위를 넘어 법률 완전 준수를 주장하지 마세요. SRS가 제외한 주 40시간, 법정휴일, 월 60시간 초과·세무 영역을 PASS로 표시하지 마세요.
- Day 13의 83건은 과거 체크포인트입니다. 최신 92건 결과를 최신값으로 쓰고 과거 원본은 수정하지 마세요. MySQL 버전, DB명, 실행 결과, 감사 run ID는 원본 증거와 대조하세요.
- B-05/B-06의 구현·테스트·상태를 RTM 또는 적절한 프로젝트 추적 문서, 한국어/일본어 누적 증적, 작업표와 인수인계서에서 동일하게 반영하세요.
- B-04는 ID 오름차순 2단계 잠금과 동시성 테스트가 끝났으므로 완료로 기록하세요. 이 handoff 작성 시 `docs/workflow/pipeline_improvement_backlog.md`에서 완료로 정정했으며 RTM/92건 회귀 증거를 연결했습니다.

### 3. 증적과 범위 동기화

- 노출이 확인될 경우에만 근본 원인과 최소 수정을 수행하고, 데이터베이스 실데이터/운영 DB는 사용하지 마세요.
- 민감 필드가 JSON 응답에 포함되지 않는 serializer/controller 회귀 테스트를 추가하세요. 테스트는 DTO 선언만 보지 말고 실제 JSON 직렬화 결과를 확인해야 합니다.
- `PayrollDetailResponseDto.java`와 직렬화 경로를 확인하세요. 현재 조사에서는 필요한 필드만 가진 명시적 DTO로 확인되어 계좌번호/마이넘버 노출의 직접 증거는 없습니다.

### 2. B-06: 급여 상세 응답의 데이터 최소화 증거

- 요청 바인딩 수준을 포함하는 controller/API 통합 테스트로 입증하세요. 코드 변경 시 관련 테스트, 격리 타깃 실행, 전체 격리 회귀를 실행합니다.
- 잘못된 달(예: `2026-13`, `2026-1`, `not-a-month`)이 계산/확정으로 진행되지 않고 일관된 400 응답을 반환하는지 테스트하세요. 기존 API 계약과 오류 형식은 보존하세요.
- `PayrollApiController.java`의 급여 `/list`, `/calculate`, `/confirm-month` 입력 검증을 확인하세요. 현재 원장은 이 라우트들의 엄격한 `YYYY-MM` 검사와 HTTP 400 컨트롤러 테스트가 남았다고 기록합니다. `/excel`의 현재 검증과 응답 동작을 먼저 대조하세요.

### 1. B-05: YearMonth 입력 경계

## 실행 순서

- B-02, B-03, B-08은 백로그에서 완료 상태입니다. B-09에는 원격 오류뿐 아니라 빈 응답·종료 청크 누락·잘못된 verdict 표·잘못된 대상 경로의 fail-closed 확인이 포함됩니다. 최신 코드와 실행 증거는 백로그 및 `docs/audit_runs/project_wide_handoff_20261114/`를 확인하세요. R-05와 Day 12는 제외 상태이므로 현재 구현 대상으로 넣지 마세요.
- B-01(독립 감사 정확도 벤치마크)과 B-07(감사 메트릭 마크다운 자동화)은 범용 파이프라인 개선 백로그로서 마일스톤 검토 대기입니다. 현재 기능 검증을 막는 결함으로 취급하지 말고, 사용자 일정에 맞춰 별도 우선순위를 정하세요.
- 즉시 넘길 프로젝트 코드 작업은 B-05와 B-06입니다. B-04는 VERIFIED 완료입니다.

## 잔여 백로그 구분

5. 기존 실패 로그와 증거는 덮어쓰거나 삭제하지 말고 새 실행 디렉터리에 보존하세요.
4. 외부 배포, 원격 Git 반영, 메시지 전송은 하지 마세요. 해당 작업은 사용자 지시가 별도로 필요합니다.
3. 원격 Qwen 감사는 `http://100.111.193.94:11434`, `qwen27b:latest`만 사용하세요. 저장소 실행 진입점은 `scripts/codex_hermes_bridge.ps1`이며 HTTP 전송을 기본값으로 사용하고 `scripts/audit_config.json`에서 주소와 모델을 읽습니다. 문서 감사에는 `-SystemPromptOverride`로 작업에 맞는 지시를 지정하세요. 로컬 7B 대체, 다른 IP, 모델 pull/download/install 금지. 연결 실패 시 원본 주소와 오류를 보존하고 미검증으로 남기세요.
2. 운영 DB는 사용 금지. 대상/전체 테스트와 시연마다 새 allowlisted 격리 DB를 사용하고 DB명을 증거로 확인하세요. 기존 격리 DB도 삭제하지 마세요.
1. R-05(MySQL 8.4 Testcontainers) 및 Day 12 작업은 사용자 지시에 따라 예정일까지 제외합니다. 지금 실행하지 마세요.

## 고정 범위와 실행 제한

- 이전 독립 감사: Qwen 27B 소스 Run `20261114_004900_46049b14` 12/12 PASS; 고정 문서 Run `20261114_005527_76dc1c9f` 10/10 PASS. 두 결과는 당시 입력 bundle에만 유효합니다. 프로젝트 전체 문서 bundle SHA `cd7cd20d12ba7771fb8ceb01dcd95181bfe39c924b8a595aa5e6fb91fdf649b8`는 첫 연결 실패 뒤 잘못된 형식의 보고서가 exit 0으로 생성됐고, 독립 재감사 Run `20261114_014515_cd7cd20d`는 WARN을 반환했습니다. 이 결과들을 PASS로 합산하지 않습니다.
- R-02 정책 채택 및 기술 검증 완료. R-11/R-12/R-14는 명시된 로컬 MySQL 26.7 범위에서 VERIFIED.
- Day 11 연간·경계 대상 실행: 6/6 PASS, 격리 DB `kintai_day11_test_01f4cb1439e6`; `docs/audit_runs/isolated_day11_20261114_094349_kintai_day11_test_01f4cb1439e6/`.
- 최신 격리 전체 회귀: 92 PASS, 실패 0, 오류 0, 스킵 0, Gradle exit 0. DB `kintai_full_test_d4124a317ed0`; `docs/audit_runs/isolated_all_20261114_094427_kintai_full_test_d4124a317ed0/`.

Codex가 2026-11-14 기준 저장소·증적·잔여 작업을 대조한 인수인계입니다. Antigravity에서 아래 남은 작업을 순서대로 수행해 주세요. 저장소에는 사용자 및 이전 작업자의 미커밋 변경과 감사 증적이 많습니다. 기존 변경을 보존하고 reset/revert/clean을 하지 마세요.

## 목적과 기준 상태

# Antigravity project-wide handoff (2026-11-14)
`AGENTS.md`; `docs/workflow/operating_policy_v2.md`; `docs/workflow/evidence_status_policy.md`; `docs/workflow/isolated_test_operation.md`; `docs/rtm_ledger.md`; `docs/workflow/pipeline_improvement_backlog.md`; `docs/working/day11_task.md`; `docs/final-portfolio/README_KO.md`; `docs/final-portfolio/README.md`; `docs/study_guides/audit_prompt_spec.md`; `docs/workflow/AGENTS_review.md`.
Source: docs/workflow/pipeline_improvement_backlog.md; SHA-256: c958f0109e1ad57c01d91393474d74aba37145d798c055143f57ae930877f0ae
[lines 23-24]
| **B-09** | Project-wide handoff audit (2026-11-14) | API 오류의 false exit-0 외에 빈/미완료/잘린 응답·프롬프트와 충돌하는 감사 역할·잘못된 verdict 보고서가 성공처럼 기록될 수 있고, bridge Auto가 로컬 Ollama를 선택할 수 있음 | **공통 시스템** | 신뢰성·절차 무결성 | 1회 | `docs/audit_runs/project_wide_handoff_20261114/`; `scripts/audit_with_hermes.ps1`; `scripts/codex_hermes_bridge.ps1` | 지정 원격 HTTP 및 설정 inference 옵션, 작업별 system prompt, 입력 토큰 예산, 종료 청크·본문·verdict 표 검증, 잘못된 입력/기존 출력/중복 run ID 거부, 해시 포함 실행 증거 | 빈·잘림·표/프롬프트 오류 테스트, 기존 증거 보존, HTTP/Auto 원격 preview, PowerShell parser와 wrapper/preflight mock, 정확한 원격 Qwen 독립 재검수 | **수정 중 (최종 독립 재검수 대기)** |

[lines 48-51]
### B-09 현재 실행 결과 (2026-11-14)

이전 실패 경로 수정은 원격 연결 오류에서 exit 1을 반환했지만, Qwen 후속 문서 감사는 exit 0으로 잘못된 보고서를 만들었습니다. 독립 재감사 Run `20261114_014515_cd7cd20d`도 fail-closed 범위와 실행 상태 표기에 구체적 지적을 남겼습니다. 이를 반영해 프로젝트 스크립트에 작업별 system prompt, 입력 컨텍스트 예산과 종료 청크·본문·verdict 표 검증, 잘못된 대상·기존 출력 거부를 추가했습니다. HTTP/Auto 원격 기본값, 설정 해시 기록, 중복 run ID 거부를 확인했습니다. Qwen Run `codex_20261114_111907_v7`에서 `think:false` 미전달로 응답 본문이 비어 있던 결함을 추가로 발견해 설정값을 API 요청에 전달하고 빈 문자열 검증을 fail-closed 처리했습니다. `runner_validation_20261114_112300`에서 parser, 응답 계약, HTTP/Auto preview, preflight 입력 검사, wrapper mock이 PASS했습니다. 최신 독립 Qwen 재검수 전에는 B-09를 완료로 표시하지 않습니다.
The v7 remote response had a terminal done chunk and 1430 eval tokens but no message.content because think:false from audit_config.json was omitted in the API body. The response validator then failed with a PowerShell mandatory-parameter binding message instead of the intended fail-closed empty-response message. Current code forwards configured think:false and permits an empty string into the explicit validator. Current local run runner_validation_20261114_112300: seven scripts parse PASS; response_validation, bridge routing (Http and Auto), input preflight, and wrapper mock all PASS.
Earlier v6 request failed at the socket permission boundary before contacting the server. v7 reached the configured Qwen server and completed generation but did not produce audit content; neither run is an audit PASS. Run folders remain preserved.
