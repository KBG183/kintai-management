# 2026-11-14 Residual Verification Handoff

- Work date: 2026-11-14 KST
- Scope: close feasible pre-Day13 RTM residuals in order; preserve operational DB and deferred scope.
- Working tree: pre-existing changes were preserved; no commit or push was made.

## Final evidence-backed results

- R-08/R-09: three newly generated isolated MySQL 26.7 DBs; 6 concurrency tests PASS, 0 failures/errors. Evidence: `docs/audit_runs/day10_isolated_regression_20261114_093404/`.
- R-10: updated attendance security/input/concurrency source independently audited by remote Qwen 27B. Run `20261114_003657_dd991d3c`; 9 PASS, 1 N/A (payroll law is outside the attendance-punch audit slice), 0 WARN/FAIL. Report: `docs/audit_runs/prior_residuals_20261114/qwen_day10_final/hermes_final/report.md`.
- R-11/R-12: expanded 12-month and boundary coverage; added the night + daily overtime overlap assertion after Qwen finding F-03. At 1,300 yen/hour, 540 net minutes, 420 night minutes and 60 overtime minutes, the test asserts 2,275 yen night premium, 325 yen overtime premium, 22,100 yen base, 1,000 yen transport cap, and 25,700 yen gross. Night-break deduction is separately covered by `LaborCalculationEngineTest`.
- Targeted annual integration: fresh isolated DB `kintai_day11_test_01f4cb1439e6`; 6 tests PASS, 0 failures/errors/skips. Evidence: `docs/audit_runs/isolated_day11_20261114_094349_kintai_day11_test_01f4cb1439e6/`.
- Full regression after the last source change: fresh isolated DB `kintai_full_test_d4124a317ed0`; 92 tests PASS, 0 failures/errors/skips, Gradle exit 0. Evidence: `docs/audit_runs/isolated_all_20261114_094427_kintai_full_test_d4124a317ed0/`.
- Final remote independent review used only `http://100.111.193.94:11434`, model `qwen27b:latest` (27.3B, IQ3_M). Run `20261114_004900_46049b14`: 12/12 PASS, WARN/FAIL 0. Bundle SHA-256 `46049b14578b32462a6f105966643d658919d5ed658faaf885796e2e1cd2c4b8`; report SHA-256 `b1ae1c9d7844d92b6ab4b47bf5e5c66dbb0f1f3f916549688e3c83ea56798f10`. Evidence folder: `docs/audit_runs/preflight_1e3b8fba040b4197aed07afbde9334f2/hermes/`.
- R-14: Korean/Japanese evidence, RTM and this handoff have been reconciled to the above evidence. The first independent document audit Run `20261114_005138_3485208b` found two WARNs (stale RTM R-14 state and an ambiguous historic Day 13 83-test heading). Both were corrected. Final frozen-document audit Run `20261114_005527_76dc1c9f` returned 10/10 PASS, WARN/FAIL 0. Bundle SHA-256 `76dc1c9f5b3750c9b38d0777bd302893d4ea4dbe3783a0b8ef107edf37312d93`; report SHA-256 `dee296cda885e4bdc8208c9145ab61757d6a5323ecd6f7c9b21016b2cfc6b124`.

## Excluded items and accepted policy

- R-02: the user accepted the documented snapshot/fail-fast policy on 2026-11-14. The RTM and policy record now show it as accepted; the implementation was unchanged. The isolated full regression includes 2/2 passing tests for `PayrollMonthlyConfirmPostQueryRaceConditionIntegrationTest`.
- R-05: excluded from current work by the user. The Docker/Podman absence probe remains preserved at `docs/audit_runs/prior_residuals_20261114/mysql84_runtime_probe.log`; no install/download was performed. Revisit at its scheduled review.
- Day 12 is excluded until its scheduled work date, per the user. No cloud deployment was performed.
- No operating DB was used. Test runs used newly created, allowlisted isolated databases. No DBs were deleted.

## Tracking

- RTM: `docs/rtm_ledger.md`
- Residual checklist: `docs/audit_runs/prior_residuals_20261114/completion_checklist.json`
- Korean evidence: `docs/portfolio_audit_evidence_KO.md`
- Japanese evidence: `docs/portfolio_audit_evidence.md`
