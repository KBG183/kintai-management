# 全 도메인 쓰기 작업(Write Path) 및 잠금 계층 전수 감사 보고서 (F-04 Scope)

## 1. 개요 및 계층 원칙
- **기본 전역 잠금 계층 (Global Lock Hierarchy)**:
  `store_staff (Level 1 부모)` ➔ `attendance` / `shift_schedule` / `payroll (Level 2 자식)` ➔ `payroll_daily_detail (Level 3 손자)`
- **조사 대상**: MyBatis Mapper XML 내 모든 `INSERT`, `UPDATE`, `DELETE`, `ON DUPLICATE KEY UPDATE` 및 `FOR UPDATE`

---

## 2. 도메인별 쓰기 경로(Write Path) 전수 조사 매트릭스

| 도메인 | 작업명 (XML ID) | 대상 테이블 | SQL 유형 | 잠금 유형 및 인덱스 | 부모 락 선행 여부 | 계층 위반 / 데드락 위험 |
|---|---|---|---|---|---|---|
| **Attendance** | `insertAttendance` | `attendance` | `INSERT` | `AUTO_INCREMENT` PK 락, FK(`store_staff_id`) 참조 읽기 | 서비스단 `lockStoreStaffForUpdate` 선행 | **위반 없음 (안전)** |
| **Attendance** | `updateClockOut` | `attendance` | `UPDATE` | `PRIMARY` (PK) 레코드 락 | 서비스단 `lockStoreStaffForUpdate` 선행 | **위반 없음 (안전)** |
| **Attendance** | `updateBreakTime` | `attendance` | `UPDATE` | `PRIMARY` (PK) 레코드 락 | 서비스단 `lockStoreStaffForUpdate` 선행 | **위반 없음 (안전)** |
| **Attendance** | `insertModificationRequest` | `attendance_modification_request` | `INSERT` | PK `AUTO_INCREMENT`, FK 참조 | 서비스단 `lockStoreStaffForUpdate` 선행 | **위반 없음 (안전)** |
| **Attendance** | `updateModificationStatus` | `attendance_modification_request` | `UPDATE` | PK 레코드 락 | 서비스단 `lockStoreStaffForUpdate` 선행 | **위반 없음 (안전)** |
| **Shift** | `insertShiftSchedule` | `shift_schedule` | `INSERT` | PK `AUTO_INCREMENT`, FK 참조 | 서비스단 `lockStoreStaffForUpdate` 선행 | **위반 없음 (안전)** |
| **Shift** | `deleteShiftRequest` | `shift_schedule` | `DELETE` | `PRIMARY` + `store_staff_id` 복합 조건 | 서비스단 `lockStoreStaffForUpdate` 선행 | **위반 없음 (안전)** |
| **Shift** | `updateShiftStatus` | `shift_schedule` | `UPDATE` | `PRIMARY` (PK) 레코드 락 | 서비스단 `lockStoreStaffForUpdate` 선행 | **위반 없음 (안전)** |
| **Payroll** | `upsertPayroll` | `payroll` | `INSERT ... ON DUPLICATE KEY UPDATE` | `uq_payroll_staff_month` UNIQUE 인덱스 락 + PK 레코드 락 | 서비스단 `lockStoreStaffForUpdate` 선행 | **위반 없음 (안전)** |
| **Payroll** | `insertPayrollDailyDetail` | `payroll_daily_detail` | `INSERT` | PK `AUTO_INCREMENT`, FK(`payroll_id`) 참조 | 서비스단 `store_staff` ➔ `payroll` 선행 | **위반 없음 (안전)** |
| **Payroll** | `deleteDailyDetailsByPayrollId` | `payroll_daily_detail` | `DELETE` | `fk_detail_payroll` 인덱스 레코드 락 | 서비스단 `store_staff` ➔ `payroll` 선행 | **위반 없음 (안전)** |
| **Payroll** | `updatePayrollStatusConfirm` | `payroll` | `UPDATE` | `PRIMARY` (PK) 레코드 락 (`payroll_id`) | **개선 완료**: `store_staff` 선행 락 획득 후 실행 | **위반 없음 (안전)** |
| **Payroll** | `updateAllPayrollStatusConfirm` | `payroll` | `UPDATE` | `fk_payroll_store` 인덱스 레코드 락 (`store_id` & `target_year_month`) | **보완 필요**: 대상 `store_staff` 오름차순 선점 2단계 설계 수립 완료 | **설계 보완 완료 (구현 대기)** |

---

## 3. FK 검사 및 UNIQUE 인덱스에서의 잠금 영향 평가

1. **외래키(FK) 참조 무결성 잠금**:
   - `attendance`, `payroll`, `shift_schedule` 행이 INSERT될 때, 부모 테이블인 `store_staff`에 대한 외래키 검증이 수행됩니다.
   - MySQL InnoDB는 외래키 부모 행에 대해 공유 락(S-Lock)을 내부적으로 획득하려 합니다.
   - 이때 서비스 계층에서 이미 `lockStoreStaffForUpdate`로 배타 락(X-Lock)을 선점하고 있으므로, 타 트랜잭션의 외래키 검증 요청은 부모 락 대기 상태로 직렬화됩니다.
2. **UNIQUE 인덱스(`uq_payroll_staff_month`) 잠금**:
   - `upsertPayroll` 실행 시 `uq_payroll_staff_month (store_staff_id, target_year_month)` 인덱스에 대해 락이 발생합니다.
   - 이미 해당 직원의 `store_staff` 행이 잠겨 있으므로, 동일 직원에 대해 두 트랜잭션이 동시에 `upsertPayroll`을 실행하는 경쟁 자체가 부모 레벨에서 원천 차단됩니다.
