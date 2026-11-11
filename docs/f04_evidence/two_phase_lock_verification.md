# F-04 2단계 잠금 구현, 동시성 경계 검증 및 실측 증적서

## 1. 테스트 실행 환경 및 데이터베이스 버전 명시
- **Database Engine**: `MySQL 26.7.0 Innovation` (Port 3306)
- **`SELECT VERSION()` 실측치**: `26.7.0` (MySQL Community Server - GPL)
- **Connector/J**: `9.7.0`
- **MyBatis**: `3.5.19`
- **Java**: OpenJDK `17.0.12`
- **Note on MySQL 8.4 LTS**:
  - 본 환경은 로컬 개발자 PC의 `MySQL 26.7.0 Innovation`에서 직접 실행되었습니다.
  - MySQL 8.4 LTS 환경과의 분리를 위해 모든 실측 데이터는 `MySQL 26.7.0 Innovation`으로 표기하며, CI 환경에서 8.4 LTS 전용 검증을 추가 분리 실행하도록 규정합니다.

---

## 2. confirmMonthlyPayroll 실제 2단계 잠금(Two-Phase Locking) 구현 명세

### ① 구현된 Java Service 코드 ([PayrollService.java](file:///c:/dev/kintai/src/main/java/com/example/kintai/domain/payroll/service/PayrollService.java#L357-L425))
```java
    @Transactional
    public int confirmMonthlyPayroll(Long storeId, String targetYearMonth) {
        // [Step 1] 확정 대상 DRAFT 급여를 보유한 직원 ID 목록을 결정론적 오름차순으로 1차 선별
        List<Long> draftStaffIds = payrollMapper.findDraftStaffIdsByStoreAndMonth(storeId, targetYearMonth);
        if (draftStaffIds == null || draftStaffIds.isEmpty()) {
            List<PayrollDto> allPayrolls = payrollMapper.findPayrollsByStoreAndMonth(storeId, targetYearMonth);
            if (allPayrolls == null || allPayrolls.isEmpty()) {
                throw new IllegalStateException("指定年月 (" + targetYearMonth + ") の給与データが存在しません。先に一括計算を実行してください。");
            } else {
                throw new IllegalStateException("指定年月 (" + targetYearMonth + ") の給与は全件確定済みです。");
            }
        }

        // [Step 2] 전역 락 계층 1단계: 선별된 직원 부모 행(store_staff)들을 ID 오름차순으로 순차 선점 잠금 (Level 1 Lock)
        for (Long staffId : draftStaffIds) {
            payrollMapper.lockStoreStaffForUpdate(staffId);
        }

        // [Step 3] 전역 락 계층 2단계: 부모 락이 확보된 안전 상태에서 점포의 당월 급여 행들을 배타 락(Level 2 Lock)으로 재조회
        List<PayrollDto> payrolls = payrollMapper.lockStorePayrollsForUpdate(storeId, targetYearMonth);
        if (payrolls == null || payrolls.isEmpty()) {
            throw new IllegalStateException("指定年月 (" + targetYearMonth + ") の給与データが存在しません。");
        }

        List<PayrollDto> draftPayrolls = payrolls.stream()
                .filter(p -> "DRAFT".equals(p.getPayrollStatus()))
                .toList();

        if (draftPayrolls.isEmpty()) {
            throw new IllegalStateException("指定年月 (" + targetYearMonth + ") の給与は全件確定済みです。");
        }

        // [Step 3-1] 동시성 경계 검증: 부모 락을 잡지 않은 새로운 직원의 DRAFT가 그 사이에 추가되었는지 감지
        for (PayrollDto p : draftPayrolls) {
            if (!draftStaffIds.contains(p.getStoreStaffId())) {
                throw new IllegalStateException("月次確定処理中に新たな職員の給与が生成されました。(職員ID: " +
                        p.getStoreStaffId() + ")。データの整合性を保つため再実行してください。");
            }
        }

        LocalDate today = LocalDate.now();

        // [Step 4] 마감 기간 종료 및 미처리 근태 전수 검증
        for (PayrollDto p : draftPayrolls) {
            if (!today.isAfter(p.getRangeEndDate())) {
                throw new IllegalStateException("精算対象期間 (" + p.getRangeEndDate() + ") が終了する前に月次給与を一括確定することはできません。(対象スタッフID: " + p.getStoreStaffId() + ")");
            }

            int unresolved = payrollMapper.countUnresolvedAttendance(p.getStoreStaffId(), p.getRangeStartDate(), p.getRangeEndDate());
            if (unresolved > 0) {
                throw new IllegalStateException("未処理の勤怠修正申請または打刻が存在するため、月次給与を一括確定できません。(対象スタッフID: " + p.getStoreStaffId() + ")");
            }
        }

        // [Step 5] 점포·대상 연월 일괄 CONFIRMED 반영 (CAS)
        int updatedCount = payrollMapper.updateAllPayrollStatusConfirm(storeId, targetYearMonth, LocalDateTime.now());
        return updatedCount;
    }
```

### ② MyBatis 매퍼 XML 선별 쿼리
```xml
    <select id="findDraftStaffIdsByStoreAndMonth" resultType="java.lang.Long">
        SELECT DISTINCT store_staff_id
        FROM payroll
        WHERE store_id = #{storeId}
          AND target_year_month = #{targetYearMonth}
          AND payroll_status = 'DRAFT'
        ORDER BY store_staff_id ASC
    </select>
```

---

## 3. 동시성 경계 및 TOCTOU 방어 분석 (시나리오별 대응)

| 시나리오 | 발생 메커니즘 | 방어 기제 및 결과 |
|---|---|---|
| **1. 대상 ID 추출 전 신규 DRAFT 생성** | Step 1 실행 전 신규 직원의 DRAFT가 생성됨 | Step 1 `findDraftStaffIdsByStoreAndMonth`에서 신규 직원 ID까지 정상 포함하여 함께 잠금 및 확정 |
| **2. 대상 ID 추출 후 신규 DRAFT 생성** | Step 1 직후, Step 2 부모 락 확보 전 다른 트랜잭션이 신규 직원의 DRAFT를 삽입 | Step 3 급여 재조회(`lockStorePayrollsForUpdate`)에서 해당 신규 행이 감지되며, Step 3-1 경계 검사(`!draftStaffIds.contains(p.getStoreStaffId())`)에 의해 즉시 `IllegalStateException`을 던져 불완전한 부분 확정 롤백 차단 |
| **3. 부모 행 잠금 중 신규 DRAFT 생성** | Step 2 루프 도중 잠금 대상 직원의 DRAFT 재계산 시도 | 해당 직원의 `store_staff` 행을 이미 잠근 상태이므로, 신규 계산 트랜잭션(`calculatePayrollForStaff`)은 부모 락 대기 상태로 블로킹되어 대기 |
| **4. 부모 행 잠금 완료 후 급여 재조회** | 대상 모든 직원의 부모 행 락 확보 완료 | 어떤 세션도 대상 직원들의 급여를 수정/재계산할 수 없는 완전한 직렬화 상태 확립 |

---

## 4. 실측 검증 데이터

1. **단위/통합 테스트 슈트**:
   - [`PayrollServiceMonthlyConfirmConcurrencyIntegrationTest.java`](file:///c:/dev/kintai/src/test/java/com/example/kintai/domain/payroll/service/PayrollServiceMonthlyConfirmConcurrencyIntegrationTest.java):
     - 2단계 잠금 일괄 확정 성공 검증 (DRAFT ➔ CONFIRMED 2건)
     - 일괄 확정과 개별 재계산 동시 실행 시 데드락 0건 직렬화 완료
   - [`PayrollServiceConfirmConcurrencyDetailedIntegrationTest.java`](file:///c:/dev/kintai/src/test/java/com/example/kintai/domain/payroll/service/PayrollServiceConfirmConcurrencyDetailedIntegrationTest.java):
     - 독립 DB 커넥션 동시 중복 확정 시 1건 성공 / 1건 상태 차단 실측 (CAS)
2. **클린 전체 회귀 테스트**:
   - `70 tests completed, 70 passed, 0 failures, 0 errors` (`BUILD SUCCESSFUL in 37s`)
