# Day 10 요구사항·SRS·RTM 추적 작업본

> 상태: 작업 중인 추적표. 최종 증적서와 RTM 본문에 반영하기 전 검토용이다.

> **증적 대조 주의 (2026-11-13):** 아래 기존 완료 표현은 당시 보고이며, 현재 파일에 대한 재현 확인과 구분한다. 감사 실행 기록의 입력 해시 `19b587c7392d924af23aacd09cc6c898c373e7ee1d36e7f4d9e82ab1863a4425`와 현재 번들 해시 `6f0b80bb7e7a842839dde56bf31c1c6addfa67c406f64b1ca15b0960558d5ba6`는 다르다. 기존 증적의 JUnit 해시 `423e6439ffa35447d656956985b5407ffe66ea348f1d5983ce24d372610327b1`과 현재 JUnit 해시 `3d30805e4f07b5e237ecf6daacd600866c5b4c492c31242913b18fe7a7d7c2af`도 다르다. 변경 시점과 원인, 당시 원본 복구 가능 여부는 미확인이다. 현재 XML에는 테스트 2건, 실패 0건이 기록되어 있으나 이것만으로 과거 3회 각각의 실행 로그를 입증할 수 없다. Qwen 원문에는 A-05와 A-06이 WARN으로 남아 있으므로 전체 항목 PASS로 취급하지 않는다.

> **후속 대조:** 현재 번들의 경로 표기를 이전 경로로 되돌린 재구성본 `docs/audit_runs/20261113_030041_19b587c7/reconstructed_input_bundle.md`의 SHA-256이 당시 입력 해시 `19b587c7392d924af23aacd09cc6c898c373e7ee1d36e7f4d9e82ab1863a4425`와 일치했다. 재구성본은 당시 저장된 원본이 아니라 동일 해시로 확인된 복원 결과다. 현재 매퍼와 테스트 소스의 해시는 기존 증적과 일치했다. 당시 JUnit 원본과 3회 각각의 로그는 여전히 미확인이다.

> **추가 실패·재실행 제약:** Antigravity 작업 `task-46.log`에는 동시성 테스트 2건의 AssertionFailedError 실패가, `task-106.log`에는 `com.example.kintai.security.AttendanceApiSecurityTest` 필터에 해당하는 테스트가 없다는 실패가 기록돼 있다. 실제 테스트 클래스는 `com.example.kintai.domain.attendance.controller.AttendanceApiSecurityTest`이다. 각 실패는 이후 실행의 성공 여부와 별도로 보존한다. 동시성 테스트의 `@BeforeEach`는 고정 직원 ID 2의 진행 중 근태를 삭제한다. 현재 격리 DB는 확인되지 않았고 Codex의 MySQL 연결은 소켓 권한 오류로 실패했으므로 기존 DB에서 재실행하지 않았다.

> **격리 환경 준비 주의:** `src/main/resources/db/schema.sql`은 `CREATE DATABASE ... kintai_db` 및 `USE kintai_db`를 포함한다. 테스트용 DB 접속 URL만 바꾸고 이 파일을 그대로 실행하면 대상이 기존 DB로 전환될 수 있으므로 금지한다. 테스트 전용 스키마를 별도로 확인하고 대상 연결과 테스트 데이터가 격리된 경우에만 수행한다.

## 1. 추적 기준

- 최상위 규정: `C:\dev\kintai\AGENTS.md`
- SRS: `C:\dev\kintai\kintai_requirements_specification_revised.md`
- RTM: `C:\dev\kintai\docs\rtm_ledger.md`
- SRS 감사: `C:\dev\kintai\audit_reports\day_by_day\hermes_srs_audit.md`

## 2. Day 10 추적표

| Day 10 범위 | SRS 근거 | RTM 연결 | 기존 검증 상태 | 추가 판단 |
|---|---|---|---|---|
| 동시 출퇴근 타각의 중복 방지 | SRS 1.3, F04 동시성 규칙 | F04 계열 | `AttendanceService`에 `store_staff FOR UPDATE` 구현 확인 | 동시 `CLOCK_IN` 및 `CLOCK_OUT` 실측 테스트 필요 |
| 급여 확정 중복 실행 방지 | SRS F01, F02, F04 | R-01, R-02, R-03, R-04, R-06 | 기존 CountDownLatch·CAS·락 순서 테스트 존재 | 동일 테스트 복제 금지, 기존 증적 재사용·빈틈만 보완 |
| 점포·직원 권한 경계 | SRS 1.3, IDOR 규칙 | 보안 검증 항목 | 기존 `AttendanceApiSecurityTest`에 출퇴근 IDOR 검증 존재 | 급여 API 경계가 빠졌는지 별도 확인 |
| CSRF 요청 차단 | SRS Security/CSRF 규칙 | 보안 검증 항목 | 기존 `CsrfSecurityIntegrationTest`와 Cookie/Header 테스트 존재 | 중복 추가 금지, 회귀 실행으로 재확인 |
| 전체 회귀 및 재현성 | SRS DoD, RTM 증적 규칙 | R-06, R-07 | 로컬 MySQL 전체 테스트 기준선 성공 | 테스트 로그·JUnit XML·해시를 최종 증적으로 보존 |

## 3. 검증 결과 및 실측 판정

- `AttendanceServiceConcurrencyIntegrationTest` 실측 완료:
  - `concurrentClockIn_ExactlyOneSucceeds`: 1건 성공, 1건 거부 (`IllegalStateException`), 중복 행 없음 (PASS)
  - `concurrentClockOut_ExactlyOneSucceeds`: 1건 성공, 1건 거부 (`IllegalStateException`), 최종 `COMPLETED` 보존 (PASS)
- MyBatis `AttendanceMapper.xml`의 `lockStoreStaffForUpdate`에 `resultType="java.lang.Long"`을 명시하여 타입 매핑 예외 해결.
- 테스트 안정성: `--rerun-tasks` 포함 3회 연속 실행 성공 (BUILD SUCCESSFUL, 종료 코드 0).
- Qwen 3.8 27B 독립 감사: `audit_reports/day10_attendance_concurrency_audit.md` 도출 완료 (P0 0건, WARN에 대한 IDOR 보안 증거 확인 완료).
- 환경 제약: Docker 부재로 인한 MySQL 8.4 컨테이너 검증은 BLOCKED(R-05)로 명확히 분리 유지.

## 4. RTM 반영 내역

- RTM 항목 신설: R-08 (동시 CLOCK_IN), R-09 (동시 CLOCK_OUT), R-10 (Day 10 독립 감사)
- 상태: **VERIFIED** (로컬 MySQL 기준 실측 증거 및 독립 감사 완료)

## 5. 완료 조건 달성도

1. 각 신규 테스트가 SRS 요구사항(SRS 1.3, F04)과 RTM 항목(R-08, R-09)에 연결됨: 완료
2. 기존 테스트와 중복되지 않음 (급여 동시성 R-01~R-07과 출퇴근 타각 분리): 완료
3. 정상·실패·동시성 결과가 JUnit XML과 실행 로그로 보존됨: 완료
4. Hermes/Qwen 독립 감사를 통과함 (Run ID: `20261113_030041_19b587c7`): 완료
5. 한국어·일본어 증적서에 동일한 판정과 파일 해시가 반영됨: 병행 갱신 중
