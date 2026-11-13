# Codex 인수인계: PerformanceSchemaDataLocks 환경 의존성 보정

## 상태

- 수정 완료, 재검증 대기
- 기능 코드 수정: 0건
- 수정 테스트: `src/test/java/com/example/kintai/domain/payroll/service/PerformanceSchemaDataLocksIntegrationTest.java`

## 변경 내용

- 기존 `WHERE OBJECT_SCHEMA = 'kintai_db'` 하드코딩 제거
- 테스트 시작 시 `SELECT DATABASE()`로 현재 연결 DB 확인
- `PreparedStatement` 파라미터로 현재 DB명을 전달
- 운영 DB를 테스트 대상으로 강제하지 않도록 수정

## 검증 전 기준

- 기존 전체 회귀: 79건 중 78건 성공, 1건 실패
- 잔여 실패: `PerformanceSchemaDataLocksIntegrationTest`
- 기존 실패 원인: 격리 DB에서 운영 DB명 하드코딩으로 락 조회 0건
- 수정 후 최종 판정: `REVALIDATION_PENDING`

## 다음 실행

1. `AGENTS.md`와 이 인수인계서를 읽는다.
2. `scripts/prepare_full_test_isolated_db.ps1`로 신규 격리 DB를 생성한다.
3. `scripts/run_full_isolated_regression.ps1`로 `--rerun-tasks` 전체 테스트를 실행한다.
4. `PerformanceSchemaDataLocksIntegrationTest`가 PASS인지 확인한다.
5. 전체 JUnit XML, 콘솔 로그, 종료 코드, 운영 DB 미접촉 여부를 확인한다.
6. 결과를 한·일 증적서와 `docs/rtm_ledger.md`에 반영한다.

## 완료 기준

- 총 테스트 수와 JUnit XML 수 일치
- 실패 0건 또는 잔여 실패 원인 확정
- 운영 DB 미접촉
- 증적서·RTM·인수인계서의 수치와 상태 일치
