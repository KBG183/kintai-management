### [Hermes Audit Execution Metrics]
| Target File | Model | Duration | Input Code Tokens | Output Tokens | Speed |
|---|---|---|---|---|---|
| C:\dev\kintai\scripts\prepare_video_demo_state.sql (10 lines) | qwen27b:latest | 9.94s | 664 tokens | 429 tokens | 44.9 tokens/s |

| Audit Item | Verdict (PASS / WARN / FAIL) | Detailed Finding & Recommendation (English) |
|---|---|---|
| 1. It is limited to resetting prior demo workflow outcomes | **PASS** | The script executes only `UPDATE` statements targeting `shift_schedule` (status change) and `attendance_mod_request` (status/reason change). It does not perform broad deletions or unrelated data modifications, strictly limiting the scope to resetting workflow states. |
| 2. It does not delete baseline accounts, shifts, or attendance | **PASS** | The script contains no `DELETE` or `DROP` commands. It only modifies existing rows via `UPDATE`, thereby preserving the integrity of baseline accounts, shift definitions, and attendance records. |
| 3. It removes pending approval state from the recording start | **PASS** | The second `UPDATE` statement explicitly targets `attendance_mod_request` where `approval_status = 'PENDING'` and sets it to `'REJECTED'`. This ensures no pending approvals remain at the start of the recording. |
| 4. It does not create the live shift request or correction shown in the video | **PASS** | The script contains no `INSERT` statements. It only modifies existing data. Therefore, it does not pre-create the specific shift request or correction records that are presumably generated during the live video demonstration. |
| 5. It is safe only for an already isolated demo database and clearly communicates that scope | **PASS** | The header comment explicitly states: `-- Apply only to the isolated video demo database before recording.` This clearly communicates the intended scope and safety constraint to the operator. |
| 6. Its resulting state is consistent with video_demo_script_KO.md | **NOT_VERIFIABLE** | The file `video_demo_script_KO.md` was not provided in the input context. Without access to the specific requirements of the video script, it is impossible to verify if the resulting database state matches the narrative or technical expectations of that document. |

OVERALL: AUDIT_FAILED