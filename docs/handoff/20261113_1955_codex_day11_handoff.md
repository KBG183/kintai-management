# Day 11 handoff: annual payroll E2E

Date: 2026-11-13. Project: `C:\dev\kintai`.

## Goal and scope
Complete Day 11 by validating 12 monthly payroll periods with representative shifts, current SRS rules only, and no Docker/cloud deployment. Cloud/container deployment remains deferred to Jan-Feb 2027 per the user's decision. Excluded requirements such as 36-agreement limits, monthly 60-hour premiums, weekly 40-hour limits, holidays, tax/social-insurance calculations remain out of this MVP test.

## Current state

- Implemented a 12-month integration test in `src/test/java/com/example/kintai/domain/payroll/service/AnnualPayrollScenarioIntegrationTest.java`.
- Fixed the detailed-labor hourly-wage mapper to use date-effective contract history in `src/main/resources/mapper/attendance/AttendanceMapper.xml`; inclusive start and exclusive end.
- Added a Day 11 isolated schema preparer and extended the local ignored runner to accept `-Scope Day11`: `scripts/prepare_day11_isolated_db.ps1`, `scripts/run_isolated_regression.ps1`.
- XML parsing and `git diff --check` passed. No Java compile, integration test, JUnit XML, or Day 11 DB was obtained.
- MySQL connection to `127.0.0.1:3306` was denied with Windows error 10013. Wrapper could not access its Gradle distribution lock file. Direct Gradle 9.8.1 then failed to connect to its local daemon before compilation. Do not retry by pointing tests at `kintai_db`.
- Execution record: `docs/audit_runs/day11_20261113_codex/execution_blockers.md`.
- Qwen requests v1 and v2 returned no usable verdict table and are not treated as final review evidence. The current focused request is `codex_day11_annual_payroll_audit_v3_20261113`; verify its response SHA and input bundle against the latest source before using it. The first two reviews prompted corrections to the duplicate field and annual oracle; a schema cross-check also found and fixed the overlong invite code. Bundle: `docs/audit_bundles/day11_annual_payroll_qwen_audit.md`.

## Scenario/oracle
2026-01 to 2026-12, one representative completed shift per month. Quarterly sequence: ordinary daytime 480 net minutes; 540 net minutes with 60 overtime; 20:00-04:00 with 360 night minutes; 480 net minutes with two 30-minute breaks. Hourly wage is 1300 through June and 1400 from July. Daily transit 1200 yen, monthly cap 1000 yen. Expected base = wage*net/60; night allowance = wage*night/240; overtime allowance = wage*overtime/240; gross = components plus 1000 capped transit. The test checks monthly payroll fields and independently accumulates the annual total.

## Next actions and completion criteria

1. Inspect Qwen's response when the worker completes; verify its manifest/hash and resolve any material findings.
2. On the user PC, run:
   - `Set-Location C:\dev\kintai`
   - `powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\prepare_day11_isolated_db.ps1`
   - Copy its `ISOLATED_DATABASE=kintai_day11_test_<12 hex>` value.
   - `powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\run_isolated_regression.ps1 -Scope Day11 -DatabaseName <copied-name>`
3. Review test compilation and JUnit XML. If clean, run the existing isolated full regression on the designated `kintai_full_test_<12 hex>` database, not the production/local operational DB.
4. Capture `SELECT DATABASE()` and `SELECT VERSION()` in the same environment, exit summary, console log, JUnit XML, and SHA-256 manifest.
5. Reconcile monthly/annual outputs against independent expected values. Update RTM and Korean/Japanese evidence. Keep any unavailable MySQL 8.4 Testcontainers dimension marked BLOCKED.
6. Final status is VERIFIED only when actual exit code, logs, JUnit, DB identity/version, RTM mapping, Qwen review, and hashes agree. Otherwise use BLOCKED/PARTIALLY_VERIFIED with the exact remaining gap.

## Guardrails
Preserve pre-existing uncommitted changes. Do not commit or push. Do not remove the active queue lock. Do not publish secrets or read/send `application.yaml` contents. Do not mark test source or model review as execution proof.
## Latest continuation note

- The test now also asserts the contract boundary directly: 2026-06-30 resolves to 1300 yen and 2026-07-01 resolves to 1400 yen. It asserts the contract's 1000-yen transport limit is what the monthly payroll applies.
- These two assertions were added after Qwen v3 input bundle creation. Qwen v3 also returned no concise findings table/overall verdict, so no Qwen review is accepted for the final source.
- This session's Codex executor denied local MySQL/loopback and Gradle daemon/cache access. The logs establish the sandbox restrictions, but do not establish whether the executor host is local or cloud. The failed attempts are not fixed by approving a prompt in this chat. Continue in a Codex execution profile that allows the project scripts to reach `127.0.0.1:3306` and the local Gradle cache/daemon, or use the already available Antigravity host runner. If a managed policy controls this Codex profile, its workspace owner must permit those accesses; user config cannot override a managed requirement.
- For a local Codex client not restricted by a managed policy, `%USERPROFILE%\.codex\config.toml` can set `sandbox_mode = "workspace-write"`, `approval_policy = "on-request"`, and `[sandbox_workspace_write]` with `writable_roots = ["C:\\dev\\kintai"]` plus `network_access = true`. A local/private-network policy may still need administrator allowance.
