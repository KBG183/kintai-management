### [Hermes Audit Execution Metrics]
| Target File | Model | Duration | Input Code Tokens | Output Tokens | Speed |
|---|---|---|---|---|---|
| C:\dev\kintai\src\main\resources\templates\payroll\list.html (385 lines) | qwen27b:latest | 12.81s | 4572 tokens | 483 tokens | 46.9 tokens/s |

| Audit Item | Verdict (PASS / WARN / FAIL) | Detailed Finding & Recommendation (English) |
|---|---|---|
| 1) clicking Excel first requests a fresh payroll calculation for the selected month | PASS | The `btnExportExcel` click handler explicitly triggers a `fetch` to `/api/payroll/calculate` with the current `storeId` and `currentYearMonth` before proceeding to the download step. |
| 2) download occurs only after a successful recalculation | PASS | The code uses a Promise chain where the download URL (`window.location.href`) is set in the `.then()` block that executes only after the calculation API returns a successful response (`res.ok` and `data.success`). |
| 3) failed recalculation is surfaced to the user and download is not attempted | PASS | If the calculation fails, the `.catch()` block is triggered, which displays an alert (`alert('Excel出力前の再計算に失敗しました: ' + err.message)`) and prevents the execution of the download logic. |
| 4) the selected store and year-month are passed consistently to both calculation and export | PASS | Both the calculation `fetch` body and the export `window.location.href` URL utilize the same `storeId` and `currentYearMonth` variables defined in the script scope, ensuring consistency. |
| 5) the existing confirmed-payroll immutability rule is not bypassed | WARN | The template triggers a recalculation (`/api/payroll/calculate`) before export. If the backend `calculate` endpoint overwrites `CONFIRMED` payroll records without checking their status, this action would bypass immutability. The template itself does not prevent this; it relies entirely on backend logic to respect the `CONFIRMED` status during recalculation. |
| 6) the button state is restored after an error and normal payroll page behavior remains intact | WARN | The button state (`disabled` and `innerHTML`) is restored in the `.catch()` block. However, if the recalculation succeeds but the subsequent `window.location.href` assignment fails (e.g., network error during download), the button remains disabled with the "Recalculating..." text because there is no `.finally()` block or success-state restoration logic. |

OVERALL: AUDIT_FAILED