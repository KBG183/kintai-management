# F-08 Transaction Rollback Evidence Dossier

## 1. Test Characteristics
- **File**: `src/test/java/com/example/kintai/domain/payroll/service/PayrollServiceTransactionRollbackIntegrationTest.java`
- **Class-level `@Transactional`**: **Explicitly Removed (Absent)** to guarantee actual commit/rollback verification without test-framework interceptors.
- **JUnit XML Report**: `docs/f08_evidence/junit_result.xml` (0 failures, duration: ~1.1s).

## 2. Pre-committed Initial State
- **Payroll ID**: Dynamic (Target year-month: 2026-10)
- **Initial Status**: `DRAFT`
- **Initial WorkDaysCount**: `1`
- **Initial TotalWorkMinutes**: `480`
- **Initial BasePay**: `10400`
- **Initial GrossPay**: `10900`
- **Initial Daily Detail Count**: `1`

## 3. Actual DB Mutation During Inner Call
- Added 1 new attendance record (`testAttendanceId2`, 480 mins).
- `confirmPayroll` internally calls `calculatePayrollForStaff(storeId, staffId, ...)`.
- Inside `calculatePayrollForStaff`:
  - `upsertPayroll` was executed with updated values: `workDaysCount = 2`, `grossPay = 53000`.
  - `deleteDailyDetailsByPayrollId` followed by 2 new details inserted.

## 4. Exception Trigger & Rollback Verification
- `confirmDto.expectedGrossPay` was intentionally set to `999999` (mismatched with recalculated `53000`).
- `IllegalStateException` ("確認時と金額が一致しません") was thrown.
- Outer `@Transactional` triggered rollback across all database operations.

## 5. Post-Exception Direct DB Verification
- Polled via independent `findById(testPayrollId)` and `findDailyDetailsByPayrollId(testPayrollId)`:
  - `GrossPay`: **`10900`** (Reverted from 53000 to 10900)
  - `WorkDaysCount`: **`1`** (Reverted from 2 to 1)
  - `Daily Detail Count`: **`1`** (Reverted from 2 to 1)
  - `PayrollStatus`: **`DRAFT`**
  - `ConfirmedAt`: **`null`**
