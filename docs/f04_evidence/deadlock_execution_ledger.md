# F-04 InnoDB Deadlock Run Identification & Evidence Ledger

## 1. Summary of Executions
- **Run-1 (Initial CyclicBarrier Spring Tx Test)**:
  - Timestamp: Prior to 15:20:00 (Timeout / aborted before raw lock inversion caught by JUnit).
- **Run-2 (Raw JDBC Deadlock Reproduction - Official Canonical Evidence)**:
  - Execution Timestamp: 2026-11-11T15:24:45+09:00 (JUnit Finished at 15:24:45.583)
  - SHOW ENGINE INNODB STATUS Captured at: 2026-11-11 15:24:56
  - Evidence File: `docs/f04_evidence/show_engine_innodb_status_deadlock.txt`
  - Evidence File SHA-256: `F79B778F23ABAC1FC60D825B7654D1ACB4370C31DCBC01C11B65ADB1DDCEBE8C`
  - JUnit Result XML: `docs/f04_evidence/deadlock_junit_result.xml`
  - JUnit Result XML SHA-256: `3477BEA9AD334F726C89EB910C5CB217454FD3F4C135F7441728EB4B57305531`
  - Test Source File: `docs/f04_evidence/deadlock_test_source.java`
  - Test Source SHA-256: `4C9875689723699B87FCF0F051A5976883B24BAF6E8A9F7547D4B3CD5D0A14FA`

## 2. Canonical Evidence Detailed Mapping (Run-2)
- **Transaction (1)**:
  - MySQL Thread ID: `1113`
  - OS Thread Handle: `19664`
  - InnoDB Trx ID: `4267`
  - Mapping to Java: `Conn-2` (confirmPayroll simulation)
  - Locks Held: PRIMARY of `payroll` (`payroll_id = 13`, lock_mode X locks rec but not gap)
  - Lock Waiting For: PRIMARY of `store_staff` (`store_staff_id = 1`, lock_mode X locks rec but not gap waiting)
  - Outcome: **SURVIVED & COMMITTED**
- **Transaction (2)**:
  - MySQL Thread ID: `1112`
  - OS Thread Handle: `21012`
  - InnoDB Trx ID: `4266`
  - Mapping to Java: `Conn-1` (calculatePayrollForStaff simulation)
  - Locks Held: PRIMARY of `store_staff` (`store_staff_id = 1`, lock_mode X locks rec but not gap)
  - Lock Waiting For: PRIMARY of `payroll` (`payroll_id = 13`, lock_mode X locks rec but not gap waiting)
  - Engine Resolution: `*** WE ROLL BACK TRANSACTION (2)`
  - Outcome: **VICTIM (Rolled back by MySQL with Error 1213 / SQLState 40001)**

*(Note on previous discrepancy: Thread ID 1114 / Trx ID 4268 mentioned in earlier draft notes belonged to an unrecorded transient check; the official, reproducible, and preserved disk evidence is strictly Thread 1112 / 1113 and Trx 4266 / 4267 as recorded above).*
