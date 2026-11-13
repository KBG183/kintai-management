# Day 11 execution attempt record

Date: 2026-11-13 (local workstation reports +09:00)
Operator: Codex
Scope: compile the new integration test, then run it only against a newly created Day 11 isolated database.

## Changes prepared

- `src/main/resources/mapper/attendance/AttendanceMapper.xml`: labor detail hourly wage lookup now uses contract history effective on the work date, with inclusive `effective_from` and exclusive `effective_to`.
- `src/test/java/com/example/kintai/domain/payroll/service/AnnualPayrollScenarioIntegrationTest.java`: 12 monthly records, four recurring shift shapes, a July wage transition, capped transport, monthly payroll persistence, and independent annual-total assertions.
- Local-only helper scripts `scripts/prepare_day11_isolated_db.ps1` and `scripts/run_isolated_regression.ps1` prepare/run against database names restricted to `kintai_day11_test_<12 hex>`.

## Attempts and results

1. `powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\prepare_day11_isolated_db.ps1`
   - Result: BLOCKED before database creation.
   - MySQL client reported `ERROR 2003 (HY000): Can't connect to MySQL server on '127.0.0.1:3306' (10013)`.
   - The script stopped on the create-database preflight. It did not fall back to `kintai_db` or another database.

2. `.\gradlew.bat compileTestJava --no-daemon --console plain`
   - Result: BLOCKED before Gradle launched.
   - Wrapper could not create/open `C:\Users\klad3\.gradle\wrapper\dists\gradle-9.8.1-bin\...\gradle-9.8.1-bin.zip.lck` (access denied).

3. Direct installed Gradle 9.8.1 invocation with `GRADLE_USER_HOME` redirected to the writable task temp directory and the existing module cache configured read-only:
   - Task: `compileTestJava --no-daemon --console plain -Dorg.gradle.jvmargs=`
   - Result: BLOCKED before compilation; Gradle daemon could not establish its localhost control connection in this environment.

No Day 11 JUnit XML exists. No Day 11 integration test has run. No isolated database was created. These implementation artifacts are not marked VERIFIED.

## Structural checks

- `AttendanceMapper.xml` parsed as XML successfully.
- `git diff --check` returned exit code 0; Git printed only pre-existing line-ending notices in unrelated modified files.
- Qwen queue audit request: `codex_day11_annual_payroll_audit_20261113`; submitted bundle SHA-256 is tracked separately in the queue manifest when returned.

## Next step

Run the Day 11 DB preparation and targeted test on the user PC where local MySQL and Gradle access are available, using only the returned `kintai_day11_test_<12 hex>` database name. Then run the full isolated regression and preserve JUnit XML, console log, database/version preflight, and hashes. Review and resolve Qwen findings before promoting the final status.
## Pre-run source review corrections

The first Qwen response completed with a valid queue manifest and SHA-256, but it did not provide the requested findings table or overall verdict; it is retained as raw reviewer output and is not accepted as a final review. During review of that output, two concrete source defects were identified and corrected before any test execution:

- Removed a duplicate `staffMemberId` field declaration that would prevent Java compilation.
- Corrected the independent annual oracle to reflect the actual 12-entry sequence and contract split: six months at 1300 yen (three ordinary-equivalent, two overtime, one night) and six months at 1400 yen (three ordinary-equivalent, one overtime, two night). The revised expected annual gross is 152,750 yen under the supplied formulas and capped transport.
- Made teardown clean partially seeded fixtures as well, not only after staff insertion.

A concise second Qwen review was queued as `codex_day11_annual_payroll_audit_v2_20261113`. It must be compared to the final source hash before use. Runtime compile and DB execution remain BLOCKED.
## Additional fixture correction and audit status

- Cross-checked the current schema: `store.invite_code` is `VARCHAR(20)`. The first test draft used `d11-` plus a full UUID (40 characters), which would violate the column limit. It now uses `d11-` plus 16 hexadecimal characters (20 total). The QR token remains within its 100-character limit.
- Final pre-run source review now has one `staffMemberId` declaration, the annual formula independently matches the 12-month case counts (expected sum 152,750 yen), and teardown handles partial seeding.
- Qwen requests v1 and v2 completed over the existing queue and their response SHA values matched the worker manifests, but neither response supplied the requested concise findings table/overall verdict. Their raw outputs are retained; they are not accepted as final review evidence.
- A focused v3 request is processing. Its input bundle SHA must be matched to the final bundle before any findings are treated as current. Runtime compile/DB execution remain blocked.
- Scope limitation: this test seeds already-completed attendance rows and checks `LaborCalculationService` output separately before passing persisted minute fields to `PayrollService`. It covers the completed-attendance-to-payroll service path and data consistency, but not the full clock-in/break/clock-out controller lifecycle. Contract cases sample June 15 and July 15, not the exact June 30 / July 1 boundary; 30-minute rounding edges are not represented in these 12 cases.
## Latest review applicability

Qwen v3 completed with valid raw-response SHA-256 integrity, but, like v1 and v2, did not return the requested findings table and verdict. It is not accepted as a final independent review. Its prompt bundle predates the final exact-boundary and applied-transport-limit assertions. The current test source must be reviewed from its current hash.

This Codex session's executor denied local host access and local Gradle cache/daemon access. The observed errors establish execution restrictions, not whether the executor host itself is local or cloud. Use a Codex execution profile whose policy permits these local operations, or the already available Antigravity host runner. If the profile is managed, its workspace owner must permit the required accesses.
