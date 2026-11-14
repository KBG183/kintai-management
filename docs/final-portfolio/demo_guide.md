# Local demo guide

## Safety and preparation

Use only a newly created isolated demo database, never the operating database. Follow `AGENTS.md` and `scripts/prepare_local_demo_db.ps1`; confirm the selected schema with `SELECT DATABASE()`. The verified DB was `kintai_demo_9f3f8487bc42`. Supply the database password through `KINTAI_DB_PASSWORD` and do not record it in commands or evidence.

## Walkthrough

1. Create the isolated demo database with the repository script and verify its name.
2. Start the app with datasource settings explicitly targeting that database. The recorded review used `http://127.0.0.1:8082`.
3. Sign in with the seeded demo owner, open the 2026 calendar, and inspect confirmed events. Open 2026-11-02; expected time is 09:00–18:00 with a 60-minute break.
4. Open payroll, select `2026-01`, and display the DRAFT rows. Expected gross totals: 佐藤 花子 ¥141,700 and 鈴木 一郎 ¥164,332.
5. Click Excel output. The recorded browser download was 5,092 bytes. Verify the month and both totals using `excel_browser_download.json` and `payroll_excel_validation.json`.

## Expected data and limits

The isolated demo contains 313 confirmed shifts and 313 attendance rows over 2026. The Day 13 checkpoint recorded 83 passes, zero failures/errors/skips. The later full regression after residual fixes recorded 92 passes, zero failures/errors/skips, on isolated DB `kintai_full_test_d4124a317ed0`; see `docs/audit_runs/isolated_all_20261114_094427_kintai_full_test_d4124a317ed0/`. Day 13-specific UI/demo records remain in `docs/audit_runs/day13_calendar_20261114/`.

The demo password is intentionally omitted. Use the locally seeded demo credential and discard or rotate it after the demo. This is a local demonstration, not production deployment guidance.
