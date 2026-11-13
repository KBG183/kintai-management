# Codex → 다음 작업자 인수인계서: Day 10 마감 검증 상태

## 1. 목표

Kintai 프로젝트 Day 10의 근태 동시성 검증을 로컬 MySQL 격리 DB에서 완료하고, 전체 Gradle 회귀 결과를 Day 10 결과와 분리해 기록한다. 이후 프로젝트 전체 Day 작업이 끝나면 기존 한국어·일본어 증적서를 일괄 정리한다.

## 2. 현재 상태

### 완료

- `AttendanceServiceConcurrencyIntegrationTest`의 `CLOCK_IN`·`CLOCK_OUT` 2건을 정식 rev.3 스키마 기반 신규 격리 DB 3개에서 강제 재실행했다.
- 총 6건 성공, 실패 0건, 오류 0건, Gradle 종료 코드 0.
- 운영 DB `kintai_db`는 사용하지 않았다.
- `UP-TO-DATE` 캐시 오판을 막기 위해 `--rerun-tasks`를 적용했다.
- PowerShell의 Gradle stderr 처리 문제를 `cmd.exe` 내부 로그 리다이렉션으로 보정했다.
- 구형 스키마의 `break_id`와 현재 Mapper의 `attendance_break_id` 불일치를 확인했다.
- 실패·보정 이력을 삭제하지 않고 원본 실행 디렉터리에 보존했다.

### 부분 완료 또는 보류

- 전체 Gradle 회귀: 79건 중 65건 성공, 14건 실패, 종료 코드 1.
- 이 전체 회귀는 Day 10 최소 fixture DB에서 실행했으므로, 14건을 모두 기능 결함으로 확정하지 않았다.
- 실패 분류 보고서: `full_regression_triage_KO.md`, `full_regression_triage_JP.md`.
- MySQL 8.4 Testcontainers: Docker 환경 미확보로 `BLOCKED`.
- 이번 회귀 실행에서는 Qwen/Hermes 독립 재감사를 호출하지 않았다.

## 3. 확정된 결정과 근거

- Day 10 전용 동시성 결과와 전체 회귀 결과를 하나의 성공률로 합치지 않는다.
- 현재 Mapper와 일치하는 `src/main/resources/db/schema.sql` rev.3를 격리 테스트 기준으로 사용한다.
- `src/main/resources/schema.sql`과 `db/schema.sql`의 차이는 기능 코드 없이 임의 통합하지 않는다. 두 파일에는 컬럼명 외에도 계약이력 테이블, CHECK 제약조건, 복합 외래키 차이가 있다.
- 기존 `portfolio_audit_evidence_KO.md`와 `portfolio_audit_evidence.md`는 프로젝트 전체 완료 전까지 누적 증적서로 유지한다. 구조 개편은 전체 완료 후 수행한다.
- 실제 실행 증적이 없는 GPT·Claude·기타 상용 AI 사용을 기록하지 않는다.

## 4. 주요 파일과 실제 경로

### 실행 스크립트

- 격리 DB 준비: `C:\dev\kintai\scripts\prepare_day10_isolated_db.ps1`
- Day 10 또는 전체 테스트 실행: `C:\dev\kintai\scripts\run_day10_isolated_test.ps1`
- 3회 Day 10 반복 실행: `C:\dev\kintai\scripts\run_day10_isolated_regression.ps1`

### Day 10 성공 증적

- 요약: `C:\dev\kintai\docs\audit_runs\day10_isolated_regression_20261113_154029\summary.json`
- 한국어 상세: `...\day10_regression_evidence_KO.md`
- 일본어 상세: `...\day10_regression_evidence_JP.md`
- 회차별 JUnit XML·로그: 같은 디렉터리의 `iteration_01`부터 `iteration_03`

### 전체 회귀 진단

- 실행 디렉터리: `C:\dev\kintai\docs\audit_runs\day10_isolated_20261113_155735_kintai_day10_test_c7bd2380a226`
- 실행 로그: `gradle_console.log`
- 전체 JUnit XML: 같은 디렉터리의 `TEST-*.xml`
- 분류 보고서: `full_regression_triage_KO.md`, `full_regression_triage_JP.md`

### 정책·증적

- 최상위 규칙: `C:\dev\kintai\AGENTS.md`
- 한국어 증적: `C:\dev\kintai\docs\portfolio_audit_evidence_KO.md`
- 일본어 증적: `C:\dev\kintai\docs\portfolio_audit_evidence.md`
- RTM: `C:\dev\kintai\docs\rtm_ledger.md`
- 운영 정책: `C:\dev\kintai\docs\workflow\operating_policy_v2.md`

## 5. 전체 회귀 실패 요약

- 급여 테스트: 최소 fixture에 필요한 직원·근태·급여 데이터 부족으로 FK 위반
- 락 테스트: 선행조건 또는 동시성 guard 기대값 불일치
- `performance_schema.data_locks`: 런타임 활성·조회 조건 불충족
- CSRF 테스트: XSRF-TOKEN 쿠키 미발급 및 로그인 흐름 `/login?error=true` 이동

다음 작업자는 별도 전체 fixture를 준비한 후 재실행해야 하며, 운영 DB를 사용해서는 안 된다.

## 6. 다음 작업과 완료 기준

1. Antigravity에서 전체 테스트용 fixture를 별도로 설계·준비한다.
2. 동일한 rev.3 스키마와 `--rerun-tasks`로 전체 테스트를 재실행한다.
3. 실패 14건을 fixture 문제, 환경 문제, 실제 결함으로 최종 분류한다.
4. 필요한 경우에만 기능 코드 수정 여부를 사용자에게 보고하고 승인받는다.
5. Day 10 전체 회귀가 정리되면 기존 한·일 증적서와 RTM에 결과를 누적한다.
6. 프로젝트 전체 Day 작업이 끝난 뒤에만 증적서 구조를 한·일 기준으로 일괄 정리한다.

## 7. 주의할 함정

- `schema.sql`과 `db/schema.sql`을 동일 파일로 간주하지 않는다.
- `UP-TO-DATE`는 테스트 실행 증거가 아니다. 반드시 `--rerun-tasks`를 사용한다.
- 최소 Day 10 fixture로 전체 테스트 성공률을 평가하지 않는다.
- `kintai_db`에 테스트 데이터를 넣지 않는다.
- Docker/Testcontainers가 성공하지 않았는데 MySQL 8.4 호환성을 VERIFIED로 표시하지 않는다.
- Codex의 환경 검수를 Qwen 독립 감사 결과로 표현하지 않는다.

## 8. 사용자 확인 대기 항목

- 전체 fixture 보강 후 재실행 범위
- `schema.sql`과 `db/schema.sql` 최종 통합 여부
- Docker/Testcontainers 검증을 나중에 수행할지 여부

## 9. 인계 후 첫 단계

`AGENTS.md`를 먼저 읽은 뒤 이 인수인계서와 `full_regression_triage_KO.md`를 읽고, 전체 테스트용 fixture가 기존 테스트가 요구하는 ID와 일치하는지 조사한다. 이후 새 격리 DB에서만 재실행한다.
