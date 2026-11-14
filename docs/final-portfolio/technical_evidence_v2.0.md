# Kintai technical evidence v2.0

**Snapshot date:** 2026-11-14 (KST)  
**Status:** Day 13 PASS, limited to the measured local MySQL 26.7 scope.

## 1. Product scope

Kintai is a Japanese shift, attendance, and monthly payroll application. The verified workflow includes confirmed-shift calendar display, attendance and break records, monthly payroll calculation, and payroll-ledger Excel export. The application stack recorded for the review is Spring Boot 4.0.8, Java 17.0.12, MyBatis, Thymeleaf/JavaScript, MySQL, and Apache POI.

## 2. Review workflow

The portfolio's “three-tier AI cooperation” describes three decision roles, not three web-service tiers:

1. **Implementation agent:** Antigravity performs implementation and prepares test evidence under the project instructions.
2. **Independent AI reviewer:** Hermes invokes the remote `qwen27b:latest` model at `http://100.111.193.94:11434` to review a frozen evidence bundle. The audit is advisory and cannot replace execution tests or human acceptance.
3. **Human decision owner:** the user determines scope, accepts or rejects recommendations, and owns final product decisions.

Codex is a user-directed collaborator in this workflow; it is not silently substituted for the implementation or independent review role. No local Qwen fallback or model download was used for the final Day 13 audit.

## 3. Architecture and integrity boundaries

The application uses a browser-rendered Thymeleaf UI and JSON endpoints, service-layer business operations, MyBatis SQL mappers, and a relational MySQL database. Payroll export builds an XLSX workbook through Apache POI. See [architecture diagram](architecture.md).

The current canonical attendance DDL in `src/main/resources/db/schema.sql` defines indexes on `(store_id, work_date)` and `(store_staff_id, work_date)`, plus foreign keys and checks. It does **not** define the historical `uq_att_open_per_staff` generated-column unique constraint described in older cumulative portfolio passages. Do not present that historical statement as current schema fact. Payroll has a unique `(store_staff_id, target_year_month)` constraint; payroll details have an attendance uniqueness constraint. Critical service paths use transactions and mapper row locks as documented in source and tests.

## 4. Day 13 measured results

| Check | Result | Evidence |
|---|---|---|
| Isolated demo database | `kintai_demo_9f3f8487bc42`; 313 confirmed shifts and 313 attendance rows over 2026-01-01 through 2026-12-31; Japanese sample name UTF-8 verified | `docs/audit_runs/day13_calendar_20261114/day13_completion_checklist.json`, `demo_prepare_retry1.log` |
| Calendar UI | November 2026 events shown; 2026-11-02 detail showed 09:00–18:00 and 60-minute break | `docs/audit_runs/day13_calendar_20261114/ui_review_record.md` |
| Payroll UI/API | January 2026 DRAFT totals ¥141,700 and ¥164,332; UI values matched authenticated API | `docs/audit_runs/day13_calendar_20261114/payroll_api_list.json`, `ui_review_record.md` |
| Browser XLSX download | Completed; 5,092 bytes; workbook month and both totals verified; SHA-256 `ba127f65f193f8289c2b4e445efc09a06e1590e2230ed0b1ad603d839cfdcd98` | `docs/audit_runs/day13_calendar_20261114/excel_browser_download.json`, `payroll_excel_validation.json` |
| Full local regression | 83 passed; failures 0, errors 0, skipped 0; Gradle exit 0 | `docs/audit_runs/isolated_all_20261114_081141_kintai_full_test_9f4b67f66b64/exit_summary.json` |
| Independent Qwen audit | 15 findings PASS; WARN/FAIL 0; Run `20261113_234922_d69a9b63`; input SHA-256 `d69a9b63c9a6a79043a38fb9c8942ab9c3fd0f9936087ffe5865d778f6817379`; report SHA-256 `0dc324150a75bb8a1d3035f7ee770a03ec2bffd8241a3a041ffd9ee2066fbf32` | `docs/audit_runs/day13_calendar_20261114/qwen27b_day13_final/report.md`, `audit_run.json` |

Representative calculation and authorization evidence includes `LaborCalculationEngineTest` (13:00 to 01:00, 180 minutes of night/overtime overlap at 150% total pay), `PayrollCalculatorStatutoryRoundingTest` (HALF_UP boundaries including 324.49 to 324 and 324.50 to 325), `StoreAuthorizationServicePayrollOwnershipTest`, and `AttendanceApiSecurityTest`. Relevant implementations are `AttendanceTimeCalculator.java` and `PayrollCalculator.java`; mapper lock statements are in the attendance, payroll, and shift mapper XML files. These are examples of tested paths, not proof that every path is covered.

The browser check used the existing app on port 8082, whose process chain was verified to target the isolated demo database. An earlier boot attempt on occupied port 8081 failed and its logs were preserved. The final evidence and the historical failure are both retained.

## 5. Requirement status and limits

- Day 13 RTM R-15 through R-18: VERIFIED for the local MySQL 26.7 scope.
- RTM R-11, R-12, and R-14: PARTIALLY_VERIFIED. The documented gaps include attendance API path coverage, several annual boundary cases, and a final-source independent review gap from the earlier Day 11 cycle.
- RTM R-05: MySQL 8.4 Testcontainers is DEFERRED to the January 2027 review. No MySQL 8.4 result is claimed here.
- The UI demo and 83-test regression do not establish that every labor boundary, API path, or concurrency condition was verified. Representative overlap and rounding unit cases exist, while documented RTM R-11/R-12/R-14 gaps remain. This evidence does not certify complete compliance with all Japanese labor law or every production deployment condition. It establishes only the listed code paths and recorded tests.

## 6. Reproduction and source records

Use the isolated database and regression procedures described in `AGENTS.md` and the linked run records. Do not point demo or test commands at the operating database. The Day 13 source record is [the completion checklist](../audit_runs/day13_calendar_20261114/day13_completion_checklist.json); requirement state is [the RTM](../rtm_ledger.md); operator handoff is [the Day 13 handoff](../handoff/20261114_0746_codex_day13_handoff.md).
