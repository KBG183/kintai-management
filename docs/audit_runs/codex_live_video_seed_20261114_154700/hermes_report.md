### [Hermes Audit Execution Metrics]
| Target File | Model | Duration | Input Code Tokens | Output Tokens | Speed |
|---|---|---|---|---|---|
| C:\dev\kintai\scripts\local_video_demo_seed.sql (50 lines) | qwen27b:latest | 15.05s | 2545 tokens | 639 tokens | 46.9 tokens/s |

| Audit Item | Verdict (PASS / WARN / FAIL) | Detailed Finding & Recommendation (English) |
|---|---|---|
| 1. Baseline data without pre-completing workflow | **PASS** | The seed inserts `shift_schedule` and `attendance` records for dates prior to the demo action (Nov 18). It does not insert any `shift_request` or payroll calculation results, leaving the workflow open for live demonstration. The attendance records are marked `COMPLETED`, which serves as valid baseline history without blocking new requests. |
| 2. No collision for new shift request on 2026-11-18 | **PASS** | The `shift_schedule` inserts cover Nov 2, 3, 5, 7, 10, 12, 14, 17, 19, 21, 24, 26, and 28. There is no entry for `2026-11-18` for `store_staff_id` 3 (or any other staff), ensuring a clean slate for creating a new shift request on that specific date without unique constraint violations or logical conflicts. |
| 3. Consistency of staff IDs and names | **PASS** | The SQL comments explicitly document that `data.sql` creates IDs 3 (Takahashi Misaki), 4 (Ito Ren), 5 (Yamamoto Aoi), and 6 (Kobayashi Koichi). The `INSERT` statements correctly reference `store_staff_id` 3, 4, 5, and 6. The demo focuses on staff 3, which aligns with the comment "recording follows staff3". |
| 4. Attendance correction against existing baseline | **PASS** | The seed creates an `attendance` record for `store_staff_id` 3 on `2026-11-02` with `attendance_status` = 'COMPLETED'. This provides a valid, existing baseline row against which a live "attendance correction" workflow can be demonstrated (e.g., modifying break times or clock-in/out). |
| 5. Scoped to isolated demo database | **PASS** | The header comment states: "Loaded only after prepare_local_demo_db.ps1 creates a fresh database." The data uses hardcoded `store_id` 1 and specific staff IDs, typical of a local demo environment. There are no references to production-specific UUIDs or external API calls, confirming it is scoped for local isolation. |
| 6. Sufficient data for readable calendar/payroll | **PASS** | The seed provides 13 shift entries for staff 3 across November 2026, creating a visually populated calendar. It includes varied scenarios: normal days, night work (staff 4, 6), and overtime (staff 5). This variety is sufficient to demonstrate payroll calculations (night premiums, overtime premiums) and owner calendar views without being overcrowded. |

OVERALL: AUDIT_PASSED