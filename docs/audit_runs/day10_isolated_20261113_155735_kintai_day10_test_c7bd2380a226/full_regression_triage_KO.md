# 전체 Gradle 회귀 실행 분류 기록

## 실행 결과

- 실행 DB: `kintai_day10_test_c7bd2380a226`
- 실행 범위: `.\gradlew.bat test --rerun-tasks --no-daemon --console plain`
- 총 테스트: 79건
- 성공: 65건
- 실패: 14건
- 종료 코드: 1
- JUnit XML: 33개
- 이 실행은 Day 10 전용 최소 fixture DB에서 수행되었다.

## 실패 분류

### 1. Fixture 부족 또는 선행 데이터 불일치

다음 급여 테스트는 `store_staff_id`·`store_id` 조합 또는 근태 데이터 등 별도 fixture를 요구한다. 현재 Day 10 최소 fixture에는 owner 1명, staff 1명, store 1개만 포함되어 있어 FK 위반이 발생했다.

- `PayrollMapperUpsertIntegrationTest`
- `PayrollServiceConcurrentExecutionIntegrationTest`
- `PayrollServiceTransactionRollbackIntegrationTest`
- `PayrollLockOrderDeadlockReproductionIntegrationTest`

대표 오류: `fk_att_staff_store`, `fk_payroll_staff_store` 외래키 위반

### 2. 락 테스트 사전조건 또는 환경 차이

- `PayrollPessimisticLockConcurrencyIntegrationTest`: 3회 모두 concurrency guard 기대값 불일치
- `PayrollServiceConfirmConcurrencyDetailedIntegrationTest`: 대상 날짜·급여 선행조건 불일치
- `PerformanceSchemaDataLocksIntegrationTest`: `performance_schema.data_locks` 활성·조회 기대값 불충족

이 항목들은 별도 급여 fixture와 MySQL 런타임 설정을 갖춘 전용 테스트 준비가 필요하다.

### 3. CSRF 테스트 흐름 실패

- `CsrfCookieHeaderLifecycleIntegrationTest`: XSRF-TOKEN 쿠키 미발급 및 후속 NullPointerException
- `CsrfSecurityIntegrationTest`: 로그인 흐름이 `/login?error=true`로 이동

현재 실행은 최소 fixture DB와 기본 프로파일에서 수행되었으므로, 인증 계정·시드 데이터·보안 테스트 전용 설정을 별도로 확인해야 한다. 이 결과만으로 CSRF 기능 결함으로 확정하지 않는다.

## Day 10 판정과의 관계

- Day 10 전용 `AttendanceServiceConcurrencyIntegrationTest`: 신규 rev.3 격리 DB 3회, 총 6건 성공
- 전체 회귀: `BLOCKED_BY_TEST_FIXTURE_AND_ENVIRONMENT` 상태
- 전체 회귀 실패 14건을 Day 10 동시성 결함으로 합산하지 않는다.
- 원본 콘솔 로그와 전체 JUnit XML은 이 디렉터리에 보존한다.
