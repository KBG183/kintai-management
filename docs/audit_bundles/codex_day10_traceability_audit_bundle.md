# Codex Day 10 Traceability Audit Bundle

## Audit scope

This is an independent pre-audit input for Day 10. The auditor must distinguish `IMPLEMENTED`, `VERIFIED`, `BLOCKED`, and `NOT_STARTED`. No completion claim may be made without executable evidence.

## Governing files

- Project rules: `C:\dev\kintai\AGENTS.md`
- SRS: `C:\dev\kintai\kintai_requirements_specification_revised.md`
- Traceability process: `C:\dev\kintai\docs\requirements_tracking_process.md`
- RTM: `C:\dev\kintai\docs\rtm_ledger.md`
- Day 10 working traceability: `C:\dev\kintai\docs\working\day10_traceability_working.md`

## Day 10 scope and current claims

1. Attendance punch concurrency: simultaneous `CLOCK_IN` and `CLOCK_OUT` for one staff member must produce exactly one success and one business rejection.
2. Payroll confirmation concurrency: existing F-04 evidence covers the payroll path, but it is a separate domain and must not be treated as evidence for attendance punch concurrency.
3. Authorization and CSRF: existing security tests are related evidence only; they do not automatically verify the new attendance concurrency tests.
4. Full regression: the local MySQL run was recorded in `docs/logs/day10_local_mysql_test_run.log` and reports `BUILD SUCCESSFUL in 30s`, but the log does not identify the exact selected test class. Treat this as partial evidence until the exact JUnit XML is attached.

## RTM entries relevant to this audit

| ID | Requirement | Current status | Evidence / limitation |
|---|---|---|---|
| R-01 | `confirmMonthlyPayroll` transaction and connection scope | VERIFIED | Payroll-specific evidence; not attendance evidence |
| R-02 | Monthly confirmation race policy | VERIFIED (limited) | Payroll-specific concurrency evidence |
| R-03 | Payroll mapper lock/update correctness | VERIFIED | Payroll-specific SQL evidence |
| R-04 | Deadlock five-stage timeline | VERIFIED (limited) | `docs/f04_evidence/`; payroll path |
| R-05 | MySQL 8.4 LTS separation from MySQL 26.7 | BLOCKED | Local environment is MySQL 26.7; no 8.4 container available |
| R-06 | Test isolation and repeated execution | VERIFIED (limited) | Payroll race test repetition; attendance test still needs exact evidence |
| R-07 | Consolidated audit items | VERIFIED | Must be reopened if Day 10 attendance evidence is added |

## New test under review

File: `src/test/java/com/example/kintai/domain/attendance/service/AttendanceServiceConcurrencyIntegrationTest.java`

Tests declared:

- `concurrentClockIn_ExactlyOneSucceeds`
- `concurrentClockOut_ExactlyOneSucceeds`

Mechanism: two executor tasks, `CountDownLatch` start gate, `Future.get` timeout, success/rejection counters, unexpected-error capture, and database cleanup. The test uses fixed store/staff IDs (`1`, `2`) and a local `DataSource`.

## Required audit checks

- Verify every Day 10 claim against the SRS and RTM; report missing bidirectional IDs.
- Check whether the fixed IDs and `deleteActiveAttendances` can collide with other tests or a real local database.
- Check executor shutdown in assertion-failure paths and whether `Future.get`/latch results are asserted deterministically.
- Check transaction boundaries, connection visibility, lock ordering, and whether a rejected request can be caused by a pre-existing fixture rather than the race.
- Check the `CLOCK_OUT` fixture, cleanup, date/time dependence, and status assertion.
- Check whether the previous `BUILD SUCCESSFUL` log proves this exact test class ran. If not, mark execution `UNVERIFIED`.
- Do not approve Day 10 as complete until exact test results, JUnit XML, and an updated RTM entry exist.

## Evidence policy

The current bundle is a working audit input. It is not a completion certificate. The auditor must list concrete findings, severity, and the exact file or test evidence needed to close each finding.
