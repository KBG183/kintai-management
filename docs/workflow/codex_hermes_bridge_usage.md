# Codex Hermes Bridge

`scripts\codex_hermes_bridge.ps1` allows Codex and Antigravity to use the same Hermes/Qwen configuration without sharing a conversation or overwriting audit results.

## Rules

- Only one Hermes run may execute at a time.
- Every run receives a unique directory under `docs\audit_runs\`.
- The existing `scripts\audit_config.json` is the default source for the model and endpoint.
- The existing `scripts\audit_with_hermes.ps1` remains the execution engine.
- The bridge does not edit application code, create commits, or push changes.
- A stale lock must be investigated manually; do not delete it blindly.

## Preview

```powershell
.\scripts\codex_hermes_bridge.ps1 `
  -TargetFile .\docs\codex_hermes_connection_test_input.md `
  -Preview
```

## Run

```powershell
.\scripts\codex_hermes_bridge.ps1 `
  -TargetFile .\docs\audit_bundles\day9_bundle.md `
  -CustomPrompt "Perform an independent blind audit. Do not rely on prior conclusions. Report evidence and uncertainty." 
```

Results are stored in a new `docs\audit_runs\codex_<timestamp>_<sha8>\` directory containing `run_manifest.json` and `hermes_report.md`.

## Windows host launcher

When running on the actual Windows host, use the host launcher. This launcher is intentionally fixed to the configured Tailscale HTTP endpoint. It does not inspect the local model list and does not try to download or run the local `qwen2.5:7b` fallback.

```powershell
.\scripts\run_codex_hermes_host.ps1 `
  -TargetFile .\docs\codex_hermes_connection_test_input.md
```

The endpoint and model are read from `scripts\audit_config.json`:

- Endpoint: `primary_auditor.endpoint`
- Model: `primary_auditor.model_tag`

The first connection test must be run in a normal Windows PowerShell session on the PC that has Tailscale access. Once the run finishes, Codex can read the generated `docs\audit_runs\codex_*\hermes_report.md` and `run_manifest.json` files.

---

## File Queue Relay Bridge (Codex Sandbox Decoupled Mode)

When running inside the ChatGPT Codex sandbox where direct network access to Tailscale (`100.111.193.94:11434`) is blocked by sandbox permissions, Codex creates a request JSON file in `docs\codex_queue\pending\`.

### 1. Request Structure (`docs\codex_queue\pending\<request-id>.json`)

```json
{
  "request_id": "codex_yyyyMMdd_HHmmss_xxxx",
  "model": "qwen27b:latest",
  "endpoint": "http://100.111.193.94:11434",
  "prompt": "Reply with exactly REMOTE_OK",
  "created_by": "Codex",
  "status": "PENDING"
}
```

Or using an external prompt file:
```json
{
  "request_id": "codex_yyyyMMdd_HHmmss_xxxx",
  "model": "qwen27b:latest",
  "endpoint": "http://100.111.193.94:11434",
  "prompt_file": "docs/audit_bundles/day9_bundle.md",
  "created_by": "Codex",
  "status": "PENDING"
}
```

### 2. Windows Host Queue Worker (`scripts\process_codex_queue.ps1`)

Run on the host machine with Tailscale connectivity:

```powershell
# One-shot mode: process all pending items and exit
.\scripts\process_codex_queue.ps1

# Continuous watch mode
.\scripts\process_codex_queue.ps1 -Watch -PollIntervalSec 3
```

- Strictly forbids fallback to local Ollama (`qwen2.5:7b`).
- Protected by exclusive lock `docs\codex_queue\.qwen_remote.lock`.
- Results are saved to:
  - Success: `docs\codex_queue\completed\<request-id>\`
    - `request.json`
    - `raw_response.json`
    - `result.md`
    - `response_sha256.txt`
    - `manifest.json`
  - Failure: `docs\codex_queue\failed\<request-id>\`
    - `request.json`
    - `error.txt`
    - `manifest.json`

---

## Permanent Codex Rule: Queue Relay Only

When Codex is running in the sandbox, it must never call `audit_with_hermes.ps1` directly and must never call the Tailscale Ollama endpoint directly. Both actions are expected to fail because the sandbox cannot access the private Tailscale network.

The permanent execution path is:

1. Codex writes a validated request to `docs\codex_queue\pending\`.
2. The Windows host queue worker processes the request through the configured remote Qwen endpoint.
3. Codex reads only `completed\<request-id>\` or `failed\<request-id>\` after processing.
4. `manifest.json`, `result.md`, `error.txt`, and SHA-256 evidence are used for the final judgment.

Direct Hermes invocation from Codex is `PROHIBITED`, even if a future direct network attempt appears available. This rule prevents repeated sandbox network failures and preserves the shared Codex–Antigravity operating contract.

## Codex-only Windows Worker

Antigravity is not required for this relay. Start the Windows transport worker directly:

```powershell
cd C:\dev\kintai
.\scripts\start_codex_qwen_worker.ps1
```

Check whether the worker is actually running:

```powershell
.\scripts\check_codex_qwen_worker.ps1
```

The worker writes `worker_status.json`, `worker.log`, and `worker.error.log` under `docs\codex_queue\`. A request is not considered processed merely because it exists under `pending`; it must move to `completed` or `failed` and have a manifest.
