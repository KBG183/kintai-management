# R-02 신규 DRAFT 경합에 대한 업무 도메인 정책 명세서 (Business Policy on Late DRAFT)

## 1. 업무적 배경 및 결정

- **업무 정책 채택 승인:** 사용자께서 2026-11-14 본 문서의 스냅샷 고정 격리 및 신규 DRAFT 감지 시 전체 롤백 정책을 채택하기로 결정했습니다. 이 기록은 업무 정책 결정의 근거이며, 구현 검증 결과와는 별도로 관리합니다.

## 2. 업무적 배경
- **시나리오**: 점주가 '2026년 10월분 월간 급여 일괄 확정(Batch Confirm)' 버튼을 누르는 순간(T1), 시스템은 해당 월에 존재하는 DRAFT 급여 대상 직원들(예: staff1, staff2)을 선별하고 잠금을 획득합니다.
- **경합 발생**: 확정 처리 도중 다른 세션이나 직원이 뒤늦게 10월분 신규 출퇴근 기록을 생성하거나 DRAFT 급여(staff3)를 삽입했습니다(T2).
- **결정된 정책**: 뒤늦게 생성된 staff3의 DRAFT가 처리 중 재조회에 나타나면 월간 확정 전체를 롤백하고, 점주가 재확인 후 다시 실행합니다.

---

## 3. 채택된 일본 실무 근태·급여 비즈니스 정책 (Business Policy)

### [채택 정책: 스냅샷 고정 격리 및 경계 위반 시 트랜잭션 안전 롤백 2중 정책]

1. **원칙 1 (스냅샷 격리 원칙 - Deterministic Batch Scope)**:
   - 월간 일괄 확정은 **"확정 작업 시작 시점에 선별되어 부모 락(`store_staff`)이 정상적으로 확보된 대상 집합"**만을 원자적으로 CONFIRMED 전환합니다.
   - 확정 작업 착수 이후 뒤늦게 들어온 신규 직원의 DRAFT는 이번 확정 대상에 포함되지 않고 **`DRAFT` 상태 그대로 안전하게 남아있어야 합니다.** (조용히 자동 확정되는 오염 원천 방지)

2. **원칙 2 (부모 락 미확보 신규 DRAFT 감지 시 트랜잭션 롤백 - Fail-Fast Safety Guard)**:
   - 만약 Step 1(직원 선별)과 Step 2(부모 락 획득) 사이에 새로운 직원의 DRAFT가 생성되어 급여 재조회 Step 3에 걸려들었을 경우:
   - 해당 직원은 부모 락(`store_staff`)을 선점하지 않은 상태이므로, 다른 개별 계산 트랜잭션과 데드락이나 데이터 불일치를 일으킬 위험이 있습니다.
   - 따라서 현 시스템은 `!draftStaffIds.contains(p.getStoreStaffId())`를 감지하여 **즉시 `IllegalStateException`을 던지고 월간 확정 전체를 안전하게 롤백(Aborting for Consistency)**하도록 설계되었습니다.
   - 점주는 "새로운 급여가 생성되었으므로 재확인 후 다시 확정하라"는 안내를 받게 됩니다.

3. **결론 및 증거 연계**:
   - [`PayrollMonthlyConfirmPostQueryRaceConditionIntegrationTest.java`](file:///c:/dev/kintai/src/test/java/com/example/kintai/domain/payroll/service/PayrollMonthlyConfirmPostQueryRaceConditionIntegrationTest.java)의 `testLateDraftCreation_IsolationRisk` 테스트는 위 정책에 따라:
     - 확정 작업 이후 생성된 신규 급여가 `CONFIRMED`로 잘못 오염되지 않고 `DRAFT` 상태로 완벽히 격리 보존됨을 실측 증명했습니다.


## 5. 승인 및 검증 상태

- 업무 정책 채택: 사용자 승인 완료 (2026-11-14).
- 기술 검증: 격리 DB에서 PayrollMonthlyConfirmPostQueryRaceConditionIntegrationTest 실행 완료. 최신 전체 회귀 JUnit에서 이 클래스 2건 PASS, 실패·오류 0이며, 실행 증거는 `docs/audit_runs/isolated_all_20261114_094427_kintai_full_test_d4124a317ed0/`입니다.
- 승인 결정은 정책 문서와 RTM에 반영했습니다. 운영 DB는 사용하지 않았습니다.
