# Day 11 작업표: 연간 가상 근태 시나리오 급여 E2E 검증

> 이 작업표는 기존 Day 11 로드맵을 현재 기준 문서와 대조해 작성했다. 기존 계획의 핵심은 연간 가상 근태 시나리오에 대한 E2E 검증이다. 다만 과거 로드맵의 36협정 한도·월 60시간 초과 할증 검증은 현재 SRS가 MVP 비지원으로 명시하므로 이번 범위에서 구현하거나 지원한다고 주장하지 않는다.

## 1. 작업 식별

- Day: 11
- 작업명: 연간 가상 근태 시나리오 기반 급여 산정 E2E 정합성 검증
- 작업일: 실제 착수일 기록
- 작업 주체: Antigravity
- 독립 감사: Hermes 경유 Qwen 27B
- Codex 검수: 사용자 요청 시
- 기존 로드맵 위치: `docs/bridge_reports/claude_pipeline_system_review.md` Day 11, `docs/study_guides/constitution_4layer_specification.md` 제7조 Day 11

## 2. 기준 문서

- 운영 지침: `AGENTS.md`
- 현행 SRS: `kintai_requirements_specification_revised.md`
- RTM: `docs/rtm_ledger.md`
- 기존 Day 11 로드맵: 위 두 문서의 Day 11 항목
- 상태·증적 정책: `docs/workflow/evidence_status_policy.md`
- 테스트 실행 규격: `docs/workflow/isolated_test_operation.md`
- Day 10 인수인계: `docs/handoff/20261113_1650_codex_stability_improvements_handoff.md`
- Day 작업 양식: `docs/workflow/day_task_template.md`

## 3. 기존 계획과 현행 범위 대조

### 기존 Day 11 계획

- 1년 치 가상 근태 시나리오를 사용한 노동법 제37조·36협정 한도 E2E 정합성 검증
- 월 60시간 초과 할증 및 36협정 상한 알림 등 경계 사례를 테스트

### 현행 SRS와의 충돌 및 처리

- 현행 SRS 0.3 및 제5장·제6장은 MVP에서 일별 8시간 초과 시간외와 22:00부터 05:00까지의 야간 할증을 구분해 다룬다. 겹치는 구간은 기본급 100%에 두 할증분을 더하는 모델이다.
- 현행 SRS는 주 40시간 초과, 법정휴일, 월 60시간 초과 시간외, 36협정 한도 및 관련 법률상 예외를 MVP의 완전 지원 범위로 선언하지 않는다.
- 따라서 Day 11의 연간 데이터 검증 목표는 유지하되, 검증 규칙은 **현재 SRS가 실제 지원한다고 선언한 계산 범위에 한정**한다. 월 60시간·36협정·미구현 법정 규칙은 새 기능으로 확장하지 않는다. 별도 Phase 2 요구사항과 근거가 승인되기 전에는 해당 결과를 PASS로 보고하지 않는다.

## 4. 시작 기준과 사전 확인

- Day 10 전체 회귀는 기존 기록상 79 PASS, 실패 0, 오류 0, 스킵 0으로 확인되어 있다. 해당 결과는 Day 11 실행 결과로 재사용하지 않는다.
- 실행 전 현행 `src/test`와 `docs/audit_runs`의 테스트·증거를 조사해 중복 검증을 피한다.
- 입력 시나리오의 기대 결과는 구현 코드에서 역산하지 않는다. 독립 계산표 또는 수기 검산으로 사전 고정하고, 사용한 SRS 규칙과 계산 예를 함께 보존한다.
- 모든 테스트 데이터는 새 격리 DB 및 고유 식별자를 사용한다. 운영 DB `kintai_db`는 건드리지 않는다.
- MySQL 8.4 Testcontainers는 로컬 MySQL 회귀와 별도의 환경 호환성 검증이다. Day 11 프로젝트 완성의 현재 차단 요인에서 제외하고 **2027년 1월 재검토 예정(DEFERRED)** 으로 관리한다. 실제 실행 전까지 검증 상태는 미완료이며, 로컬 MySQL 결과를 MySQL 8.4 결과로 간주하지 않는다.

## 4.1 이력서 제출 일정에 따른 범위 조정

- 2026-11-17 이력서 제출 전 완료 목표는 현행 MVP, Day 11 검증, 테스트 증거 및 포트폴리오 설명 준비다.
- 기존 Day 12의 Docker Compose·Redis·웹 배포는 이번 완료 조건에서 제외하고 **DEFERRED**로 관리한다. 작업을 취소하거나 완료한 것으로 기록하지 않는다.
- Docker·AWS·클라우드 배포는 사용자가 계획한 2027년 1~2월 AWS 학습 시점에 별도 작업으로 재개한다. MySQL 8.4 Testcontainers는 **2027년 1월** 별도 재검토 항목으로 등록하되, Docker 설치·환경 준비와 실제 검증 완료를 미리 가정하지 않는다.
- 이력서·면접 자료에는 현재 실제 상태를 “로컬 MVP 구현 및 테스트 검증, 클라우드 배포는 후속 계획”으로 구분해 적는다. 배포 완료 또는 공개 URL이 있는 것처럼 표현하지 않는다.
- 가까운 시일 내 면접이 잡히는 경우를 대비해, 현재 환경에서 재현 가능한 로컬 실행 방법과 데모 데이터·시연 순서를 정리한다. 실제 화면 구동을 확인하지 못한 항목은 준비 완료로 주장하지 않는다.

## 5. 요구사항과 완료 기준

> 아래 상태·완료 기준은 초기 Day 11 시점 기록입니다. 최신 상태는 §13 및 `docs/rtm_ledger.md`를 기준으로 확인하십시오.

| Requirement ID | 요구사항 | 완료 기준 | 상태 |
|---|---|---|---|
| R-11 | 12개월 합성 근태·급여 입력으로 현행 SRS 범위의 월별 및 연간 산정 결과를 검증한다. | 12개월 서비스 통합 시나리오와 독립 연간 산식 대조는 실행 증거로 확인했다. 타각 API 경로와 통상적인 여러 근무일을 포함하는 월별 운영 형태는 이 테스트에서 검증하지 않았다. | PARTIALLY_VERIFIED |
| R-12 | 현행 SRS 범위의 경계·결함 사례를 검증한다. | 자정 통과, 다회 휴게, 계약 유효기간 경계는 확인했다. 분할 근무의 일 8시간 누적 경계, 월 마감 경계, 반올림 경계는 이 번들에서 입증되지 않았다. | PARTIALLY_VERIFIED |
| R-13 | 격리 회귀 실행 결과와 무결성 증거를 확보한다. | 격리 DB `kintai_full_test_cf0fbbd96042`에서 전체 81건, 실패·오류·스킵 0건, 종료 코드 0 및 로그·JUnit·요약·SHA-256 증거를 확인했다. 적용 범위는 로컬 MySQL 26.7이며 MySQL 8.4는 별도 미검증이다. | VERIFIED (로컬 MySQL) |
| R-14 | Day 11 결과와 감사·추적 문서를 일치시킨다. | Qwen 리뷰 및 한·일 증적 대조를 완료했다. 다만 최종 소스의 계약 경계·통근비 상한 단언 일부가 리뷰 번들 이후 추가되어, 최종 소스 전체에 대한 독립 리뷰는 부분 검증으로 남긴다. | PARTIALLY_VERIFIED |

상태 판정은 `docs/workflow/evidence_status_policy.md`를 따른다. 계획은 `NOT_STARTED`, 파일 작성만 확인된 상태는 `IMPLEMENTED`, 독립 감사만 수행한 상태는 `REVIEWED`다. 실제 일치 환경의 테스트 로그와 결과물이 갖춰져야 `VERIFIED`로 승격할 수 있다.

## 6. 테스트 설계 지침

### 연간 시나리오

- 단일 점포·직원 기준의 12개 월별 정산 기간을 생성한다. 필요한 경우 여러 직원은 데이터 격리와 결과 대조가 명확할 때만 추가한다.
- 각 기간의 출퇴근·휴게 기록과 계약 유효기간을 명시하고, 월별 기대 분·금액과 누적 합계를 독립적으로 산출한다.
- 월중 시급 변경, 다른 마감일, 야간과 일별 시간외 중첩, 다회 휴게 등 SRS가 지원하는 규칙을 조합하되, 모든 기능을 억지로 넣기보다 실제 구현과 요구사항에 맞춘다.
- 기대 결과표에는 원시 근태, 제외 휴게, 실근무, 야간 분, 시간외 분, 적용 시급, 반올림 전후 금액을 포함한다.

### 범위 밖 사례

- 주 40시간 초과, 법정휴일 할증, 월 60시간 초과 추가 할증, 36협정 한도 판정·알림, 세금·사회보험·통근수당 과세 판정은 이번 합격 기준에 포함하지 않는다.
- 과거 Day 11 로드맵의 위 항목은 역사적 계획으로 남기되, 현재 구현 완료 또는 현행 MVP 검증 항목으로 옮기지 않는다.
- Docker Compose·Redis·웹 배포·AWS 배포는 이번 제출 전 완료 조건에서 제외하고 후속 계획으로 남긴다.

### 품질 기준

- 테스트는 독립 오라클과 비교하며, 계산기와 서비스가 같은 잘못된 로직을 공유해 양쪽 결과가 일치하는 것을 정답으로 오인하지 않는다.
- 실패 케이스는 삭제하거나 덮어쓰지 않고 원본 로그와 실패 데이터를 보존한다.
- 전체 12개월 E2E 결과와 개별 단위 테스트 결과를 구분해 보고한다.

## 7. 작업 순서

1. 운영 지침, 현행 SRS, RTM 및 두 기존 Day 11 계획 문서의 실제 내용을 확인한다.
2. 기존 구현·테스트·증거를 조사하고 요구사항 ID와 테스트 케이스 간 추적표를 만든다.
3. 독립 오라클의 연간 입력 데이터와 기대 결과를 사전에 고정한다. SRS 밖의 규칙은 기대값에 포함하지 않는다.
4. 부족한 테스트만 추가한다. 기능 결함이 드러나면 원인·영향·최소 수정안을 기록하고, 정책 또는 범위 확대가 필요하면 먼저 사용자 판단 대상으로 분리한다.
5. 고유 격리 DB를 확인하고 대상 E2E 및 관련 테스트를 실행한다.
6. `scripts/run_isolated_regression.ps1`로 전체 회귀를 실행한다. 자동 집계, Exit Code, 성공 토큰, JUnit XML, 실제 DB명, SHA-256을 서로 대조한다.
7. 기능·급여 변경이 있으면 Hermes 자동 backlog를 통한 Qwen 27B 독립 감사를 수행한다. 모델을 호출하지 못했으면 결과를 만들지 말고 미검증으로 기록한다.
8. 필요 조치와 재감사·재시험을 수행한다. 모든 실패·수정 전후 결과를 보존한다.
9. RTM과 한국어·일본어 증적서를 같은 판정으로 갱신한다.
10. 인수인계서를 작성하고 실제 파일 경로, 명령, 환경, 잔여 위험과 다음 담당자 실행 단계를 남긴다.

## 8. 수정 범위와 금지 항목

### 수정 가능 후보

- 기존 테스트와 대조 후 확인된 누락에 대한 테스트 파일
- 테스트가 실제 결함을 입증한 경우 필요한 최소 기능 파일
- `docs/rtm_ledger.md`, 한·일 증적서, 새 실행 결과 폴더, Day 11 인수인계서

### 지시 없이 하지 않을 항목

- 36협정·월 60시간 초과 추가할증 등 SRS 밖 정책의 기능 구현이나 PASS 주장
- 운영 DB 접속을 테스트 대상으로 사용하거나 운영 데이터 변경
- 기존 로그·실패 증거 삭제 또는 덮어쓰기
- 격리 DB 삭제, Git commit/push, 외부 서비스로의 공유
- 실행하지 않은 Qwen, MySQL 버전 또는 테스트를 실행 완료로 서술

## 9. 실행 증적

| 구분 | 명령 또는 산출물 | 결과 | Exit Code | 증적 경로 |
|---|---|---|---:|---|
| 요구사항 대조 | SRS·RTM·Day 11 테스트 추적표 | 착수 후 기록 |  |  |
| 독립 오라클 | 연간 입력과 기대 결과표 | 착수 후 기록 |  |  |
| 대상 E2E | 실제 실행 명령과 JUnit XML | 착수 후 기록 |  |  |
| 전체 회귀 | `scripts/run_isolated_regression.ps1` | 착수 후 기록 |  |  |
| DB 환경 | `SELECT DATABASE()` 및 `SELECT VERSION()` | 착수 후 기록 |  |  |
| 자동 요약 | `exit_summary.json` | 착수 후 기록 |  |  |
| 무결성 | `sha256_manifest.json` 및 해시 재검산 | 착수 후 기록 |  |  |
| Qwen 독립 감사 | Hermes Run ID, 입력·결과·해시 | 실제 수행 시 기록 |  |  |
| MySQL 8.4 별도 검증 | Testcontainers 실행 결과 | **2027년 1월 재검토 예정(DEFERRED); 실행 전까지 미검증** |  |  |

## 10. 종료 체크리스트와 최종 보고

- [x] 기존 Day 11 목표를 현재 SRS와 대조하고 범위 충돌을 문서화
- [x] SRS와 RTM 요구사항의 양방향 추적을 정리하고 현재 상태를 반영
- [ ] 독립 오라클 기대값을 구현 전에 고정했다는 별도 증거 확보
- [x] 12개월 결과와 연간 합계를 실행 결과와 대조 (서비스 통합 시나리오 범위)
- [x] 36협정·월 60시간 등 제외 범위가 구현 완료로 기록되지 않도록 명시
- [x] 격리 DB명·로컬 DB 버전과 실행 로그 확인; 운영 DB를 테스트 대상으로 사용하지 않음
- [x] 대상 테스트 및 전체 회귀의 종료 코드·JUnit 결과 대조
- [x] 자동 요약·로그·XML·SHA-256 증거 보존 및 대조
- [x] Qwen 감사 결과와 검수 범위 제한 기록
- [x] RTM, 한·일 증적서, 인수인계서의 상태·수치·경로 정합성 반영
- [x] Docker·웹 배포와 MySQL 8.4 검증을 후속 일정으로 구분

최종 판정 보고에는 전체 상태(`VERIFIED`, `PARTIALLY_VERIFIED`, `BLOCKED`, `FAILED` 중 근거에 맞는 값), 실행 환경, 월별·누적 결과, 독립 오라클, Qwen 결과, 제외 범위, 잔여 위험, 모든 증적 경로를 포함한다.

## 11. 최초 실행 차단 기록 (2026-11-13, 당시 상태 보존)

> 아래 BLOCKED 기록은 최초 시도의 실행 환경을 보존한다. 이후 분리 DB 실행과 전체 회귀 결과로 현재 판정은 §12에서 갱신한다.
> 이어지는 Qwen v1-v3 미완료 메모와 Codex 실행기 제한 설명도 당시 상태 기록이며, 최종 판정은 §12를 따른다.

- 상태: **구현 준비 완료, 런타임 검증 BLOCKED**. 아직 Day 11 테스트 실행 PASS 또는 VERIFIED로 판정하지 않는다.
- 상세 근무 계산용 시급 조회가 고정 직원 시급을 사용하던 문제를 확인해, `AttendanceMapper.xml`을 계약 이력의 유효기간 조회로 수정했다. 시작일은 포함하고 종료일은 제외한다. 7월 1일 변경 경계를 테스트 자료에 포함했다.
- `AnnualPayrollScenarioIntegrationTest.java`는 2026년 월별 12개 근무자료, 주간/초과/야간/복수휴게 형태, 7월 시급 변경, 월 교통비 한도, 월별 급여 명세 및 독립 연간 합계를 검증하도록 작성했다.
- `prepare_day11_isolated_db.ps1` 및 `run_isolated_regression.ps1 -Scope Day11` 경로를 준비했다. 격리 DB 이름은 `kintai_day11_test_<12자리 16진수>`만 허용한다.
- 실제 시도 결과: MySQL 격리 DB 생성은 `127.0.0.1:3306` 연결 오류 10013으로 시작 전 차단. Gradle Wrapper는 사용자 홈의 배포판 잠금 파일 접근 거부. 설치된 Gradle 직접 실행도 로컬 데몬 통신 제한으로 컴파일 전에 실패했다. 운영 DB 우회는 하지 않았다. JUnit XML 및 실행 DB는 생성되지 않았다.
- 실행 기록: `docs/audit_runs/day11_20261113_codex/execution_blockers.md`. 테스트 번들 Qwen 요청 ID: `codex_day11_annual_payroll_audit_20261113`.
- 다음 완료 기준: 사용자 PC에서 격리 DB 준비와 테스트 컴파일/단위 통합 시나리오 실행, 전체 회귀, 로그/JUnit/DB 버전/해시 보존, Qwen 결과 검토 및 RTM·한/일 증적 일치.
Qwen requests v1-v3 completed, but none produced the required findings table and final verdict. They remain recorded as raw attempts, not accepted review sign-off. The final source also includes explicit June 30 / July 1 contract-boundary checks and a contract transport-limit assertion added after the last submitted Qwen bundle. R-14 remains BLOCKED pending a usable independent review.

This Codex session's executor denied host loopback access. The error does not establish whether the executor host is local or cloud. Run the isolated DB commands in a Codex execution profile permitted to access localhost and the local Gradle cache/daemon, or use the Antigravity host runner. See `docs/handoff/20261113_1955_codex_day11_handoff.md`.

## 12. 최종 실행·검수 판정 (2026-11-13)

### 판정

- 전체 상태: **PARTIALLY_VERIFIED**.
- 대상 Day 11 통합 테스트 2건과 전체 회귀 81건은 로컬 MySQL 26.7 격리 DB에서 통과했다. 전체 회귀는 실패 0, 오류 0, 스킵 0, Gradle 종료 코드 0이다.
- 전체 결과: `docs/audit_runs/isolated_all_20261113_203209_kintai_full_test_cf0fbbd96042/`.
- Day 11 대상 실행: `docs/audit_runs/isolated_day11_20261113_202210_kintai_day11_test_769aa826e376/`.
- Qwen 27B 리뷰 및 대조: Run ID `20261113_113749_a39493e5`; `docs/audit_runs/day11_local_final_review_20261113/`.
- MySQL 8.4 Testcontainers는 Docker 미사용 결정에 따라 2027년 1월 재검토로 연기했다. 실행 전까지 **미검증**이다.

### 검증 범위와 잔여 항목

- 테스트는 근태·휴게 행을 DB에 직접 입력하고 노동 계산·급여 서비스를 호출한다. 타각 API/컨트롤러/화면 경로를 검증하지 않았으므로 API부터의 E2E로 표현하지 않는다.
- 시나리오는 월 1건씩 12개 근태를 사용한다. 야간·초과·복수 휴게·계약 경계는 다루지만, 동일일 분할 근무 누적 8시간, 월중 다일 통근비 누적 상한, 월 마감 경계 및 반올림 경계는 이 시나리오에서 입증하지 않았다.
- 테스트 클래스는 연결 DB명을 자체 확인하지 않고 `DataSource`에 직접 데이터를 쓴다. 기록된 실행은 격리 DB에서 수행됐으나, IDE 등에서 단독 실행할 경우를 막는 DB 허용 목록 검사는 후속 보완 대상이다.
- Qwen 입력 이후 추가된 계약 경계·통근비 상한 단언은 Qwen 리뷰 입력에 포함되지 않았다. 따라서 최신 소스 전체의 독립 리뷰는 추가 확인이 필요하다.
- 달력 화면 및 현실적인 브라우저 데모 데이터 검수는 Day 11 급여 서비스 통합 테스트 범위 밖이다. 포트폴리오 영상 준비를 위해 Day 13 완료 범위에 추가했으며, 브라우저에서 실제 확인하기 전까지 미검증이다.

## 13. 2026-11-14 최신 상태 (이전 §12 판정을 대체)

§12는 2026-11-13 당시의 역사적 실행 스냅샷이며 최신 판정으로 사용하지 않습니다. 본 절은 실행 증거 기준으로 갱신한 현재 상태입니다.

- 최신 격리 전체 회귀: 92 PASS, 실패 0, 오류 0, 스킵 0, Gradle 종료 코드 0. DB `kintai_full_test_d4124a317ed0`; 증거 `docs/audit_runs/isolated_all_20261114_094427_kintai_full_test_d4124a317ed0/`.
- 최신 연간·경계 대상 실행: 6/6 PASS, 격리 DB `kintai_day11_test_01f4cb1439e6`; 증거 `docs/audit_runs/isolated_day11_20261114_094349_kintai_day11_test_01f4cb1439e6/`.
- R-11/R-12/R-14는 RTM의 제한된 로컬 MySQL 26.7 범위에서 VERIFIED. Qwen 27B 소스 감사 Run `20261114_004900_46049b14` 12/12 PASS, WARN/FAIL 0. 문서 정합성 Run `20261114_005527_76dc1c9f` 10/10 PASS는 당시 고정 입력에만 유효한 역사적 결과입니다. 후속 프로젝트 전체 인수인계 문서 번들 SHA `cd7cd20d12ba7771fb8ceb01dcd95181bfe39c924b8a595aa5e6fb91fdf649b8`의 독립 재감사 Run `20261114_014515_cd7cd20d`는 WARN입니다.
- R-02는 사용자께서 스냅샷 범위와 경계 감지 시 전체 롤백 정책을 채택했습니다. 관련 통합 테스트 2/2는 최신 전체 회귀에 포함되어 PASS입니다.
- R-05(MySQL 8.4)와 Day 12는 사용자 지시에 따라 일정일까지 현재 범위에서 제외합니다. 기존 §12의 미완료 당시 수치는 삭제하지 않고 이력으로 보존합니다.
- 전체 프로젝트에는 아직 B-05(급여 API의 YearMonth 형식 검증/HTTP 400 테스트)와 B-06(안전한 급여 상세 DTO의 민감 필드 미포함 JSON 직렬화 회귀 테스트) 잔여가 있습니다. 구현에서 민감정보 노출이 확인된 것은 아니며, B-06은 방어 검증 테스트가 없는 상태입니다. Antigravity 인수인계 `docs/handoff/20261114_antigravity_project_wide_handoff.md`에서 범위와 순서를 확인하십시오.

이 판정은 전체 제품 요구 완료나 일본 노동법 전체 준수를 뜻하지 않습니다. RTM과 각 증거 파일의 범위를 따릅니다.
