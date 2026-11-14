### [Hermes Audit Execution Metrics]
| Target File | Model | Duration | Input Code Tokens | Output Tokens | Speed |
|---|---|---|---|---|---|
| C:\dev\kintai\src\main\resources\templates\payroll\list.html (397 lines) | qwen27b:latest | 14.39s | 4719 tokens | 550 tokens | 46.8 tokens/s |

| Audit Item | Verdict (PASS / WARN / FAIL) | Detailed Finding & Recommendation (English) |
|---|---|---|
| 1. Clicking Excel first requests a fresh payroll calculation for the selected month when the loaded payroll rows are still mutable | PASS | The `btnExportExcel` click handler explicitly calls `fetch('/api/payroll/calculate', ...)` before triggering the download. It passes `storeId` and `currentYearMonth` in the request body, ensuring a fresh calculation is attempted for the selected period. |
| 2. Download occurs only after a successful recalculation | PASS | The code uses a Promise chain: `.then(async res => { ... if (!res.ok || !data.success) { throw new Error(...) } ... })`. The download URL (`window.location.href = ...`) is only set in the subsequent `.then()` block, which executes only if the previous step did not throw an error. |
| 3. Failed recalculation is surfaced to the user and download is not attempted | PASS | If the recalculation fails (either HTTP error or `data.success` is false), an error is thrown. This triggers the `.catch(err => { ... })` block, which displays an alert (`alert('Excel output before recalculation failed: ' + err.message)`) and does not execute the download logic. |
| 4. The selected store and year-month are passed consistently to both calculation and export | PASS | The calculation request uses `storeId` and `currentYearMonth`. The export URL is constructed as `/api/payroll/excel?storeId=${storeId}&yearMonth=${currentYearMonth}`. Both use the same JavaScript variables, ensuring consistency. |
| 5. The UI refuses to recalculate or export when the loaded payroll list contains CONFIRMED or PAID rows, preserving the backend immutability rule | PASS | The handler checks `currentPayrollList.some(item => item.payrollStatus === 'CONFIRMED' || item.payrollStatus === 'PAID')`. If true, it alerts the user and returns immediately, preventing the recalculation and export actions. |
| 6. The button state is restored before navigation and after errors, while normal payroll page behavior remains intact | PASS | In the success path, `button.disabled = false` and `button.innerHTML = originalText` are called before `window.location.href` is set. In the error path (`.catch`), the button state is also restored. The rest of the page logic (loading list, modals) is unaffected by this specific button handler. |

OVERALL: AUDIT_PASSED