# Final Full Audit Bundle: bundle_5_final_docs
Generated: 2026-11-14T12:48:13+09:00

## FILE: docs\final-portfolio\portfolio_summary_KO.md
```
# [기술 성과 보고서] 일본형 근태·시프트·급여 관리 시스템(`kintai`) 구축 및 3-Tier 품질 검증 결과

- **보고일**: 2026-11-14 KST (`main` 브랜치 · 로컬 MySQL 26.7.0 격리 검증 기준)
- **상세 증적 원본 (1,196줄)**: [`portfolio_audit_evidence_KO.md`](../portfolio_audit_evidence_KO.md)
- **기술 심화 자료**: [아키텍처](architecture.md) · [로컬 시연 가이드](demo_guide.md) · [RTM 원장](../rtm_ledger.md) · [아카이브 스냅샷(v2.0/v2.1)](../legacy_audit_reports/technical_evidence_v2.1.md)

---

## 1. 보고 개요 (Executive Summary)

본 프로젝트는 일본 아르바이트·시프트 근무 환경의 **출퇴근 타각 -> 시프트 대조 -> 근태 수정 결재 -> 노동기준법 할증 정산 -> 급여대장 Excel 출력** 전 과정을 구현한 Spring Boot 4 / MySQL 26.7.0 기반 엔터프라이즈 웹 시스템입니다.

1인 개발 환경에서 코드 리뷰어 부재와 단일 AI의 자기 확증 편향으로 인해 동시성 데드락·법령 계산 오류·보안 취약점이 유출되는 것을 막기 위해, **상용 AI(구현·리팩토링) · 독립 감사 AI(원격 RTX 5070 Ti 기반 Qwen 27B) · 결정적 자동화 테스트(JUnit 5 / MockMvc)**의 3-Tier 품질 게이트를 구축해 운용했습니다.
그 결과, 본 문서에 정리한 주요 지적사항(데드락·IDOR·할증 계산·입력 경계 등)을 수정해 해당 범위의 통합 테스트와 재감사를 통과했으며, **신규 격리 DB 기준 전체 회귀 테스트 94/94 PASS(실패 0, 오류 0, 스킵 0)**를 달성했습니다.

---

## 2. 사용 시스템 및 검증 인프라

| 구분 | 채택 기술 및 환경 | 도입 목적 |
|---|---|---|
| **애플리케이션** | Java 17 LTS, Spring Boot 4.0.8, Spring Security 7.0.x, MyBatis 3.5.x | 세션 기반 인증/인가 및 트랜잭션 제어 |
| **데이터베이스** | MySQL 26.7.0 (InnoDB `REPEATABLE READ`, 정본 DDL `schema.sql` rev.3) | 행 단위 비관적 락(`FOR UPDATE`) 기반 데이터 정합성 보장 |
| **UI / 대용량 출력** | Thymeleaf, Vanilla JS(FullCalendar, CSRF 헤더), Apache POI `SXSSFWorkbook` | 시프트 캘린더 시각화 및 스트리밍 기반 월차 급여대장 출력(메모리 사용 억제) |
| **로컬 감사 인프라** | 자택 PC `RTX 5070 Ti 16GB` + Tailscale VPN + Ollama `qwen27b:latest` (`IQ3_M`, `think: false`) | 상용 AI와 모델을 분리한 독립 정적·보안·로직 교차 감사 |

### 2-1. 실제 시스템 동작 흐름 (End-to-End Architecture)

```
[직원 / 점장 브라우저 (Thymeleaf + FullCalendar + CSRF)]
   │  HTML / JSON 요청 (세션 쿠키 기반 인증)
   ▼
[Spring Security & Controller]
   │  요청 본문의 ID를 신뢰하지 않고 세션 Principal에서 memberId / storeStaffId 강제 도출
   │  YYYY-MM 경계 검증 등 입력 방어 후 Service 위임
   ▼
[Service (@Transactional) & MyBatis Mapper]
   │  부모 행(store_staff) FOR UPDATE 또는 직원 ID 오름차순 2단계 잠금(2PL)으로 재현한 동시성·데드락 경쟁 방어
   │  분 단위 타임라인 슬라이싱 + BigDecimal HALF_UP으로 제37조 할증(심야 25%·연장 25%·중첩 50%) 산정
   ▼
[MySQL 26.7.0 InnoDB (schema.sql rev.3)] ──▶ [Apache POI SXSSFWorkbook]
   │  shifts / attendance / attendance_break      월차 급여대장(.xlsx) 스트리밍 다운로드
   │  payroll / payroll_detail 영구 저장             (FullCalendar 시프트·근태 일정 시각화)
```

1. **인증·인가 및 입력 경계**: 보호 대상 경로(직원 셀프서비스·타각 API 등)에서 세션 `Principal` 기반 `memberId`·`storeStaffId`·소유권 검사를 적용하고, 상태 변경 요청에 CSRF 보호 및 `YYYY-MM` 등 입력 경계 검증을 적용했습니다.
2. **타각·결재·동시성 제어**: 출퇴근 타각 및 근태 수정 승인 시 `store_staff` 부모 행 비관적 락(`SELECT ... FOR UPDATE`)으로 동시 2스레드 중복 타각을 방어하고, 월간 급여 일괄 확정 시에는 대상 `DRAFT` 직원 ID 오름차순 2단계 잠금(2PL)과 신규 `DRAFT` 유입 시 fail-fast 롤백으로 재현한 교착·경합 시나리오(`Trx 4266/4267`)를 검증했습니다.
3. **급여 계산 및 출력**: 확정된 근태와 휴게 구간을 분 단위로 분할해 일본 노동기준법 제37조 할증을 계산(`DRAFT` 생성)한 뒤, 일괄 확정(`CONFIRMED`) 후 `SXSSFWorkbook` 스트리밍 방식으로 힙 메모리 사용을 억제해 급여대장 Excel(`2026-01` 데모 기준 5,092 bytes)을 생성합니다.
4. **로컬 AI 교차 감사 루프**: 구현 변경 시 `audit_with_hermes.ps1`이 소스·DDL·테스트 번들의 `SHA-256` 해시를 고정해 Tailscale VPN 너머 자택 `RTX 5070 Ti`(`Qwen 27B`)로 감사를 실행하고, 지적 사항 수정 후 신규 격리 DB에서 JUnit 5 회귀 테스트(`94/94 PASS`)로 최종 동작을 증명합니다.

### 2-2. 실제 개발·검증 작업 방식 (AI 하이브리드 2-트랙 파이프라인)

본 프로젝트는 개발자가 단일 AI에게 코드를 맡기고 바로 넘어가는 방식이 아니라, 작업 성격에 따라 **신규 기능 구현 트랙(A)**과 **리팩토링·잔여 결함 교차 검수 트랙(B)**의 2가지 하이브리드 파이프라인으로 개발·검증했습니다.

#### 트랙 A. 신규 기능 구현 파이프라인 (상용 AI -> 원격 Qwen 27B -> 격리 회귀)

```
[1. 개발자 (Decision Owner)] ── Day별 SRS·설계 범위 지시 및 수용 판단
          │
          ▼
[2. 상용 AI (구현 담당)] ── 기능 구현 + JUnit 5 테스트 작성 + E2E 번들 생성
          │  (Controller + Service + Mapper + XML + schema.sql + Test 일괄 번들링)
          ▼
[3. 원격 로컬 감사 (audit_with_hermes.ps1 ──▶ Tailscale ──▶ 자택 RTX 5070 Ti Qwen 27B)]
          │  SHA-256 해시 고정 · seed=42 · temperature=0.1 · think=false · fail-closed 검증
          ├─▶ FAIL / WARN 적발 시: [2]로 복귀해 코드 수정 + 재감사 (구두 설명만으로 통과 금지)
          │
          ▼ PASS
[4. 결정적 격리 회귀 게이트 (run_isolated_regression.ps1)]
          │  매 실행 신규 격리 DB 생성 (SELECT DATABASE() 검증) + 94/94 JUnit 5 실행 증명
          ▼
[5. 증적 고정] ── docs/audit_runs/<run-id>/ 원본 로그·해시 저장 및 한/일 증적서·RTM 동시 갱신
```

#### 트랙 B. 리팩토링 · 잔여 결함 교차 검수 파이프라인 (역할 분리 + 상호 리뷰 + 회귀 방어)

```
[1. 개발자 (역할 지정 & 범위 승인)] ── 동일 파일 동시 수정 금지 규칙 하에 작업 단계 분리
          │
          ▼
[2. 상용 AI 1차 리팩토링 / 잔여 결함 수정]
          │  결함 수정 + handoff(docs/handoff/*.md) 및 실패·보정 이력 원본 보존
          ▼
[3. 교차 검수 (독립 리뷰 + 원격 Qwen 27B)]
          │  교차 리뷰 단계에서 코드·테스트·증적 정합성을 재검토 + Qwen 27B(`codex_hermes_bridge.ps1` / `audit_with_hermes.ps1`) 독립 감사
          ├─▶ 결함·불일치 발견 시: [2] 또는 보완 수정 후 재감사 (`v10`, `B-05/B-06` 등)
          │
          ▼ PASS
[4. 전체 격리 회귀 비파괴 검증 (`94/94 PASS`) + 한·일 증적/RTM 동기화] ──▶ 개발자 최종 수용
```

* **구현과 감사의 물리적 분리**: 상용 AI가 작성한 코드를 같은 세션의 같은 AI가 평가하지 않고, E2E 계층 번들로 묶어 별도 모델 계열인 로컬 `Qwen 27B`에게 독립 감사를 의뢰했습니다.
* **리팩토링 시 역할 분리 및 이력 변조 차단**: 리팩토링과 잔여 백로그(`B-01`부터 `B-10`) 정리 시에는 구현 담당과 교차 리뷰 역할을 분리해 상호 검증하고, 실패·보정 이력을 지우지 않고 `docs/audit_runs/`와 `docs/handoff/`에 보존한 뒤 전체 회귀(`94/94 PASS`)로 기존 기능 비파괴를 증명했습니다.
* **증거 기반 결함 종결 원칙**: 감사관이 지적한 결함(IDOR, 데드락, 입력 경계 등)은 말로 해명해 넘기지 않고, 반드시 **코드 수정 + 통합 테스트 통과 + 재감사 PASS**라는 물리적 증거를 남긴 뒤에만 종결 처리했습니다.

---

## 3. 왜 무료 상용 AI 웹챗이 아닌 로컬 감사 파이프라인인가? (프로젝트 핵심 의의)

단순히 "API 요금 몇 달러를 아끼기 위해" 로컬 LLM을 쓴 것이 아닙니다. 무료 ChatGPT·Gemini 웹 채팅에 코드를 복사해 붙여넣는 방식과 달리, 본 프로젝트가 자체 로컬 감사 파이프라인을 구축한 엔지니어링 의의는 다음 4가지입니다.

1. **복붙 검수가 아닌 재현 가능한 감사 파이프라인 (`Audit-as-Code`)**:
   * 웹 채팅은 대화마다 컨텍스트와 조건이 달라져 감사 근거가 남지 않습니다. 본 프로젝트는 `audit_with_hermes.ps1` 기반 자동화 스크립트로 입력 번들 `SHA-256` 해시, `seed=42`, `temperature=0.1`, 입출력 토큰·소요 시간(`audit_run.json`), `done_reason`·판정표 fail-closed 검증까지 **실행 단위 감사 증적**을 남겼습니다.
2. **독립 감사 실행 환경의 로컬 고정 & Rate Limit 없는 연속 재감사**:
   * 구현·리팩토링에는 상용 AI를 활용했고, 독립 `Qwen 27B` 교차 감사 실행은 외부 웹챗이 아닌 자택 PC(`RTX 5070 Ti 16GB`, Tailscale P2P) 내부의 로컬 Ollama 엔드포인트에서 수행했습니다. 또한 웹 구독의 시간당 횟수 제한 없이 **Day 4 하루에만 8회(82,649 tokens)**, 프로젝트 전체 **확인 실측 271,934 tokens**의 반복 수정-재검수 루프를 돌릴 수 있었습니다.
3. **구현 AI와 모델 계열을 분리한 편향 차단 & 로컬 LLM 튜닝 실증**:
   * 구현 AI가 자기 코드를 스스로 통과시키는 확증 편향을 막기 위해 별도 계열(`Qwen 27B`)을 감사관으로 분리했고, 실제로 IDOR(`FAIL`), 데드락 위험(`F-04`), 할증 중첩 단언 누락(`F-03`)을 적발했습니다. 또한 `7B -> 9B -> 27B` 확장과 `think: false` A/B 실험으로 **동일 파일 기준 출력 토큰 86.9% 절감·감사 시간 6.32배 단축**을 달성했습니다.
4. **AI 판정을 물리적 DB·회귀 테스트로 검증하는 3-Tier 품질 게이트**:
   * 본 프로젝트의 본질은 "AI가 PASS라고 했다"가 아니라, **로컬 AI가 짚은 위험을 실제 MySQL 데드락 재현(`Trx 4266/4267`)과 재현한 경쟁·경계 시나리오의 94/94 격리 통합 테스트로 검증**한 개발·검증 체계 자체에 있습니다.

---

## 4. 핵심 문제 · 로컬 AI 지적 · 조치 · 최종 해결 (5대 사례)

### 사례 1. 월간 급여 일괄 확정 시 DB 데드락 및 정합성 붕괴 위험
* **무엇이 문제였는가**: 여러 직원의 급여를 일괄 확정(`confirmMonthlyPayroll`)하는 도중 개별 급여 재계산(`calculatePayrollForStaff`)이 동시에 실행되면, 트랜잭션 간 락 획득 순서가 역전되어 **MySQL 1213 데드락**이 발생할 위험이 있었습니다. 또한 재조회 이후 신규 `DRAFT` 행이 끼어드는 경합 조건도 존재했습니다.
* **로컬 AI의 지적**: 원격 Qwen 27B 감사(`F-04`)에서 다중 행 일괄 잠금 시 물리적 락 순서 불일치 및 교착 가능성을 지적했습니다.
* **어떻게 고쳤는가**:
  1. 실제 통합 테스트에서 `Trx 4266 / 4267` 간 상호 `waiting` 데드락을 물리적으로 재현해 원인을 확정했습니다.
  2. 대상 `DRAFT` 직원 ID만 동적 추출한 뒤 **직원 ID 오름차순으로 2단계 선점 잠금(2PL, `SELECT ... FOR UPDATE`)**을 걸도록 재설계했습니다(`B-04`).
  3. 재조회 이후 신규 `DRAFT` 유입 감지 시 자동 확정하지 않고 **fail-fast 전체 롤백**하도록 방어했습니다(`R-02`).
* **최종 해결 결과**: 데드락 재현 테스트 및 동시성 통합 테스트(`R-01`부터 `R-04`) 전수 PASS, 94/94 회귀 통과로 재현한 경쟁 시나리오에서의 해결을 검증했습니다.

### 사례 2. 출퇴근 중복 타각 및 API 권한·식별자 위변조(IDOR) 취약점
* **무엇이 문제였는가**: 동일 직원이 `CLOCK_IN` / `CLOCK_OUT`을 동시에 두 번 호출할 때 중복 근태 행이 생성될 위험이 있었고, 초기 셀프서비스 API 및 타각 API 요청 본문(`storeId`, `storeStaffId`)을 조작해 타인 데이터에 접근할 위험이 있었습니다.
* **로컬 AI의 지적**: Day 4 감사에서 셀프서비스 API IDOR을 **FAIL**로 적발했고, Day 10 재감사(`A-05`, `A-06`)에서 타각 요청의 세션 식별자 검증 및 경계 테스트 보완을 요구했습니다.
* **어떻게 고쳤는가**:
  1. 컨트롤러에서 요청 본문의 ID를 신뢰하지 않고 Spring Security 세션 `Principal`에서 `memberId` / `storeStaffId`를 강제 도출하도록 수정했습니다.
  2. `AttendanceMapper.xml`의 `lockStoreStaffForUpdate` 반환형(`resultType="java.lang.Long"`) 누락을 수정하고, 부모 행(`store_staff`) 비관적 락으로 동시 요청 중 1건만 성공·나머지는 거부하도록 구현했습니다.
  3. 테스트 환경의 구형 스키마 및 `kintai_db` 하드코딩을 제거해 `schema.sql` rev.3 단일 정본과 `SELECT DATABASE()` 동적 검증으로 통일했습니다.
* **최종 해결 결과**: 신규 격리 DB 3회 연속 동시성 테스트 **6/6 PASS**(`R-08`, `R-09`), 타각 API 위변조 차단 E2E 테스트 PASS 및 원격 Qwen 27B 재감사(`20261114_003657_dd991d3c`) 9 PASS / 0 WARN으로 종결했습니다.

### 사례 3. 일본 노동기준법 제37조 할증 계산 오류 및 분할근무 누락 위험
* **무엇이 문제였는가**: 단순 시간 합산 방식으로는 **심야(22:00-05:00, 25%)와 일 8시간 초과 연장(25%)이 겹치는 구간의 50% 중첩 할증**이 누락되거나, 하루 두 번 출근하는 분할 근무(4h+5h)의 8시간 초과분이 계산되지 않을 위험이 있었습니다.
* **로컬 AI의 지적**: Day 1 SRS 감사에서 50% 중첩 가산 명세 누락 위험을 지적했고, Day 11 감사(`F-03`)에서 야간+연장 중첩 구간에 대한 통합 테스트 단언 보강을 요구했습니다.
* **어떻게 고쳤는가**:
  1. 분 단위 타임라인 슬라이싱 엔진과 `BigDecimal HALF_UP` 법정 끝수 처리를 구현하고, 동일 일자 분할 근무 누적 8시간 초과 산정을 연동했습니다.
  2. 계약 유효기간 변경 경계(6/30-7/1) 시급 참조 및 야간 420분 중 연장 60분 중첩(기본급 22,100엔 + 심야 2,275엔 + 연장 325엔 + 교통비 1,000엔 = 25,700엔) 단언을 추가했습니다.
* **최종 해결 결과**: 12개월×3근무일 독립 연간 오라클 시나리오 및 경계 테스트 **6/6 PASS**(`R-11`, `R-12`), 원격 Qwen 27B 소스 감사(`20261114_004900_46049b14`) **12/12 PASS**를 달성했습니다.

### 사례 4. 캘린더 UI 불일치, API 입력 경계(`B-05`) 및 민감정보 직렬화(`B-06`) 검증
* **무엇이 문제였는가**: Day 13 화면 검수에서 시프트 캘린더가 비어 보였고 데모 DB의 일본어 성명이 `?`로 손상되었습니다. 또한 급여 `/excel` 외 `/list`, `/calculate`, `/confirm-month` 엔드포인트에 `YYYY-MM` 형식 검증이 없었으며(`B-05`), 상세 응답 JSON의 민감정보 미포함 회귀 테스트가 부재했습니다(`B-06`).
* **로컬 AI의 지적**: Day 13 감사에서 UI 다운로드 실측 보강을 요구했고, Day 8 백로그(`F-07`, `F-10`)로 입력 경계 및 DTO 직렬화 검증이 이관되었습니다.
* **어떻게 고쳤는가**:
  1. `calendar.html`의 `CalendarEventDto` 필드 매핑을 수정하고 UTF-8 stdin으로 데모 DB(313행)를 적재해 실제 브라우저에서 `給与台帳_2026-01.xlsx`(5,092 bytes) 다운로드를 실측했습니다.
  2. `PayrollApiController`에 공용 `validateYearMonth` 정규식을 적용해 4개 급여 API 전 경로에서 비정상 연월(`2026-13`, `2026-1` 등) 입력 시 일관되게 HTTP 400을 반환하도록 통일했습니다.
  3. `PayrollApiValidationAndSerializationIntegrationTest`에서 실제 MockMvc JSON 응답을 파싱해 허용 키 일치 및 계좌번호·마이넘버 등 민감 필드 미포함을 검증했습니다.
* **최종 해결 결과**: `R-15`부터 `R-19` VERIFIED, Day 13 포트폴리오 번들 Qwen 감사(`20261114_000458_4af20cd9`) **15/15 PASS**, 최종 전체 격리 회귀 **94/94 PASS**를 달성했습니다.

### 사례 5. 로컬 AI 감사 파이프라인 병목 및 거짓 정상종료(`B-09`) 해결
* **무엇이 문제였는가**: Day 4에 로컬 감사 8회 실행 시 **82,649 tokens / 1,147.38초(약 19.1분)**가 소요되어 개발 리듬이 무너졌습니다. 또한 후속 감사 자동화 중 `think: false`가 API 요청 본문에 전달되지 않아 빈 응답이 오거나, 토큰 초과 실패가 `exit 0`으로 처리되는 결함(`B-09`)이 발생했습니다.
* **어떻게 고쳤는가**:
  1. 동일 코드(`ShiftService.java`, 469줄)로 Thinking ON/OFF A/B 통제 실험을 수행해 원인이 기본 `thinking` 출력임을 증명하고 `"think": false`를 전역 고정했습니다.
  2. `codex_hermes_bridge.ps1` / `audit_with_hermes.ps1`에 `think` 본문 전달, `[AllowEmptyString()]`, 종료 청크·verdict 표 fail-closed 검증을 추가했습니다.
* **최종 해결 결과**:
  * **성능 개선**: 동일 파일 기준 출력 토큰 **86.9% 절감**(9,803 -> 1,287 tokens), 감사 시간 **6.32배 단축**(206.88초 -> 32.72초).
  * **신뢰성 확보**: 원격 Qwen 27B 최종 재검수(`codex_20261114_112428_v10`) **Overall PASS**(51.41초)로 해소했습니다.

---

## 5. 한계, 제외(`DEFERRED`) 범위 및 최종 결론

* **최종 검증 결론**: 로컬 MySQL 26.7.0 격리 환경 기준 **94/94 PASS**(실패 0, 오류 0, 스킵 0) 및 해당 범위의 원격 Qwen 27B 독립 감사를 통과해 코드·기존 증적을 `main` 브랜치에 반영했습니다. *(참고: 금일 개편한 본 요약 보고서 자체에 대해서는 별도 Qwen 재감사를 실행하지 않았으며, 원본 증적서·실행 요약과의 교차 대조 리뷰만 반영했습니다.)*
* **연기(`DEFERRED`) 범위**: `Day 12`(클라우드 배포) 및 `R-05`(MySQL 8.4 LTS Testcontainers — Docker/Podman 부재)는 차기 프로젝트 일정에 따라 내년 재검토로 분리했습니다.
* **한계 명시**: 주 40시간, 법정휴일, 월 60시간 초과 할증, 세무·사회보험 계산은 현행 MVP 범위 외이며, 상용 출시 시 공인노무사 감수가 필요합니다. 모델 `PASS`는 참고 감사이며 최종 기능 동작은 항상 JUnit 5 결정적 테스트로 보증합니다.
```

## FILE: docs\final-portfolio\portfolio_summary_JA.md
```
# [技術成果報告書] 日本型勤怠・シフト・給与管理システム（`kintai`）構築および 3-Tier 品質検証結果

- **報告日**: 2026-11-14 KST（`main` ブランチ・ローカル MySQL 26.7.0 隔離検証基準）
- **詳細証跡原本（1,117行）**: [`portfolio_audit_evidence.md`](../portfolio_audit_evidence.md)
- **技術詳細資料**: [アーキテクチャ](architecture_ja.md) · [ローカルデモ手順](demo_guide_ja.md) · [RTM 台帳](../rtm_ledger.md) · [アーカイブスナップショット(v2.0/v2.1)](../legacy_audit_reports/technical_evidence_v2.1_ja.md)

---

## 1. 報告概要（Executive Summary）

本プロジェクトは、日本のアルバイト・シフト勤務現場における **出退勤打刻 -> シフト対照 -> 勤怠修正承認 -> 労働基準法割増算定 -> 給与台帳 Excel 出力** の全工程を実装した Spring Boot 4 / MySQL 26.7.0 ベースの Web システムです。

1人開発環境においてコードレビュアー不在および単一 AI の自己確証バイアスにより並行性デッドロック・法令計算誤り・セキュリティ脆弱性が流出することを防ぐため、**商用 AI（実装・リファクタリング）・独立監査 AI（リモート RTX 5070 Ti 搭載 Qwen 27B）・決定的自動テスト（JUnit 5 / MockMvc）** による 3-Tier 品質ゲートを構築・運用しました。
その結果、本報告書で整理した主要な指摘事項（デッドロック・IDOR・割増算定・入力境界等）を是正して該当範囲の統合テストおよび再監査を通過し、**新規隔離 DB 基準で全体回帰テスト 94/94 PASS（失敗 0、エラー 0、スキップ 0）** を達成しました。

---

## 2. 利用システムおよび検証インフラ

| 区分 | 採用技術および環境 | 導入目的 |
|---|---|---|
| **アプリケーション** | Java 17 LTS, Spring Boot 4.0.8, Spring Security 7.0.x, MyBatis 3.5.x | セッションベース認証・認可およびトランザクション制御 |
| **データベース** | MySQL 26.7.0（InnoDB `REPEATABLE READ`、正本 DDL `schema.sql` rev.3） | 行レベル悲観的ロック（`FOR UPDATE`）によるデータ整合性担保 |
| **UI / 大容量出力** | Thymeleaf, Vanilla JS（FullCalendar, CSRF ヘッダ）, Apache POI `SXSSFWorkbook` | シフトカレンダー可視化およびストリーミング型月次給与台帳出力（メモリ使用抑制） |
| **ローカル監査インフラ** | 自宅 PC `RTX 5070 Ti 16GB` + Tailscale VPN + Ollama `qwen27b:latest`（`IQ3_M`, `think: false`） | 商用 AI とモデルを分離した独立静的・セキュリティ・ロジック交差監査 |

### 2-1. 実システムの動作フロー（End-to-End Architecture）

```
[スタッフ / 店長ブラウザー（Thymeleaf + FullCalendar + CSRF）]
   │  HTML / JSON 要求（セッション Cookie ベース認証）
   ▼
[Spring Security & Controller]
   │  要求本文の ID を信頼せずセッション Principal から memberId / storeStaffId を強制導出
   │  YYYY-MM 境界検証等の入力防御後に Service へ委譲
   ▼
[Service（@Transactional）& MyBatis Mapper]
   │  親行（store_staff）FOR UPDATE またはスタッフ ID 昇順 2段階ロック（2PL）で再現した並行競合・デッドロックを防御
   │  分単位タイムライン分割 + BigDecimal HALF_UP で第37条割増（深夜 25%・残業 25%・重畳 50%）を算定
   ▼
[MySQL 26.7.0 InnoDB（schema.sql rev.3）] ──▶ [Apache POI SXSSFWorkbook]
   │  shifts / attendance / attendance_break      月次給与台帳（.xlsx）ストリーミング出力
   │  payroll / payroll_detail 永続化               （FullCalendar シフト・勤怠予定可視化）
```

1. **認証・認可および入力境界**: 保護対象経路（スタッフセルフサービス・打刻 API 等）においてセッション `Principal` ベースの `memberId`・`storeStaffId`・所有権検査を適用し、状態変更要求には CSRF 保護および `YYYY-MM` 等の入力境界検証を適用しました。
2. **打刻・承認・並行性制御**: 出退勤打刻や勤怠修正承認時は `store_staff` 親行への悲観的ロック（`SELECT ... FOR UPDATE`）で同時 2スレッドの重複打刻を防御し、月次給与一括確定時は対象 `DRAFT` スタッフ ID 昇順の 2段階ロック（2PL）と新規 `DRAFT` 混入時の fail-fast ロールバックにより再現した競合シナリオ（`Trx 4266/4267`）を検証しました。
3. **給与計算および出力**: 確定した勤怠と休憩区間を分単位で分割して労働基準法第37条割増を算定（`DRAFT` 生成）し、一括確定（`CONFIRMED`）後に `SXSSFWorkbook` ストリーミング方式でヒープメモリ使用を抑制して給与台帳 Excel（`2026-01` デモ基準 5,092 bytes）を出力します。
4. **ローカル AI 交差監査ループ**: 実装変更ごとに `audit_with_hermes.ps1` がソース・DDL・テストバンドルの `SHA-256` ハッシュを固定して Tailscale VPN 経由で自宅 `RTX 5070 Ti`（`Qwen 27B`）へ監査を実行し、指摘修正後に新規隔離 DB 上の JUnit 5 回帰テスト（`94/94 PASS`）で最終動作を証明します。

### 2-2. 実際の開発・検証ワークフロー（AI ハイブリッド 2トラックパイプライン）

本プロジェクトは開発者が単一 AI にコード生成を任せてそのまま完了とするのではなく、作業性質に応じて **新規機能実装トラック（A）** と **リファクタリング・残余欠陥交差レビュー トラック（B）** の 2つのハイブリッドパイプラインで開発・検証を行いました。

#### トラック A. 新規機能実装パイプライン（商用 AI -> リモート Qwen 27B -> 隔離回帰）

```
[1. 開発者（Decision Owner）] ── Day 別 SRS・設計範囲の指示および受容判断
          │
          ▼
[2. 商用 AI（実装担当）] ── 機能実装 + JUnit 5 テスト作成 + E2E バンドル生成
          │  （Controller + Service + Mapper + XML + schema.sql + Test を一括バンドル）
          ▼
[3. リモートローカル監査（audit_with_hermes.ps1 ──▶ Tailscale ──▶ 自宅 RTX 5070 Ti Qwen 27B）]
          │  SHA-256 ハッシュ固定 · seed=42 · temperature=0.1 · think=false · fail-closed 検証
          ├─▶ FAIL / WARN 指摘時: [2]へ戻りコード修正 + 再監査（口頭説明のみでの通過禁止）
          │
          ▼ PASS
[4. 決定的隔離回帰ゲート（run_isolated_regression.ps1）]
          │  実行ごとに新規隔離 DB を作成（SELECT DATABASE() 検証）+ 94/94 JUnit 5 実行証明
          ▼
[5. 証跡固定] ── docs/audit_runs/<run-id>/ へ原本ログ・ハッシュ保存および日韓証跡書・RTM 同時更新
```

#### トラック B. リファクタリング・残余欠陥交差レビュー パイプライン（役割分離 + 相互レビュー + 回帰防御）

```
[1. 開発者（役割指定＆範囲承認）] ── 同一ファイル同時編集禁止ルール下で作業段階を分離
          │
          ▼
[2. 商用 AI 1次リファクタリング / 残余欠陥修正]
          │  欠陥修正 + handoff（docs/handoff/*.md）および失敗・補正履歴の原本保存
          ▼
[3. 交差レビュー（独立レビュー + リモート Qwen 27B）]
          │  交差レビュー段階でコード・テスト・証跡整合性を再検証 + Qwen 27B（`codex_hermes_bridge.ps1` / `audit_with_hermes.ps1`）独立監査
          ├─▶ 欠陥・不整合発見時: [2]または補完修正後に再監査（`v10`, `B-05/B-06` 等）
          │
          ▼ PASS
[4. 全体隔離回帰非破壊検証（`94/94 PASS`）+ 日韓証跡・RTM 同期] ──▶ 開発者最終受容
```

* **実装と監査の物理的分離**: 商用 AI が書いたコードを同一セッションの同一 AI に自己評価させず、E2E 階層バンドルにまとめて別系統モデルであるローカル `Qwen 27B` へ独立監査を委ねました。
* **リファクタリング時の役割分離および履歴改ざん防止**: リファクタリングと残余バックログ（`B-01`から`B-10`）整理時には実装担当と交差レビュー役割を分離して相互検証し、失敗・補正履歴を消去せず `docs/audit_runs/` と `docs/handoff/` に保存したうえで全体回帰（`94/94 PASS`）により既存機能の非破壊を証明しました。
* **証拠ベースの欠陥クローズ原則**: 監査 AI が指摘した欠陥（IDOR、デッドロック、入力境界等）は言葉での説明だけで済ませず、必ず **コード修正 + 統合テスト通過 + 再監査 PASS** という物理的証拠を残したうえでのみ完了としました。

---

## 3. なぜ無料商用 AI ではなくローカル監査パイプラインなのか（プロジェクトの中核的意義）

単に「API 料金の数ドルを節約するため」にローカル LLM を使ったわけではありません。無料の ChatGPT・Gemini の Web チャットへコードを貼り付ける運用と異なり、本プロジェクトが独自のローカル監査パイプラインを構築したエンジニアリング上の意義は次の 4点です。

1. **コピペ検収ではなく再現可能な監査パイプライン（`Audit-as-Code`）**:
   * Web チャットは会話ごとにコンテキストや条件が揺らぎ監査根拠が残りません。本プロジェクトでは `audit_with_hermes.ps1` ベースの自動化スクリプトにより、入力バンドルの `SHA-256` ハッシュ、`seed=42`、`temperature=0.1`、入出力トークン・所要時間（`audit_run.json`）、`done_reason`・判定表の fail-closed 検証まで **実行単位の監査証跡** を固定しました。
2. **独立監査実行環境のローカル固定および Rate Limit なき反復再監査**:
   * 実装・リファクタリングには商用 AI を活用し、独立 `Qwen 27B` 交差監査の実行自体は外部 Web チャットではなく自宅 PC（`RTX 5070 Ti 16GB`, Tailscale P2P）内部のローカル Ollama エンドポイント上で実施しました。また Web サブスクリプションの時間当たり回数制限に縛られず、**Day 4 のみで 8回（82,649 tokens）**、プロジェクト全体で **確認済み実測 271,934 tokens** の修正・再検証ループを回すことができました。
3. **実装 AI とモデル系統を分離したバイアス遮断＆ローカル LLM チューニング実証**:
   * 実装 AI が自身のコードを自己承認する確証バイアスを防ぐため別系統（`Qwen 27B`）を監査役に分離し、実際に IDOR（`FAIL`）、デッドロック懸念（`F-04`）、割増重畳アサーション不足（`F-03`）を検出しました。さらに `7B -> 9B -> 27B` の段階拡張と `think: false` の A/B 実験により、**同一ファイル基準で出力トークン 86.9% 削減・監査時間 6.32倍高速化** を達成しました。
4. **AI 判定を物理的 DB・回帰テストで検証する 3-Tier 品質ゲート**:
   * 本プロジェクトの本質は「AI が PASS と言った」ことではなく、**ローカル AI が指摘したリスクを実際の MySQL デッドロック再現（`Trx 4266/4267`）と再現した競合・境界シナリオの 94/94 隔離統合テストで検証** した開発・検証アーキテクチャそのものにあります。

---

## 4. 主要課題 · ローカル AI 指摘 · 措置 · 最終解決（5大事例）

### 事例 1. 月次給与一括確定時の DB デッドロックおよび整合性崩壊リスク
* **何が問題だったか**: 複数スタッフの給与を一括確定（`confirmMonthlyPayroll`）する最中に個別給与再計算（`calculatePayrollForStaff`）が同時実行されると、トランザクション間のロック取得順序が逆転し **MySQL 1213 デッドロック** が発生するリスクがありました。また再照会後に新規 `DRAFT` 行が混入する競合条件も存在しました。
* **ローカル AI の指摘**: リモート Qwen 27B 監査（`F-04`）にて、複数行一括ロック時の物理ロック順序不一致およびデッドロック可能性が指摘されました。
* **どのように修正したか**:
  1. 統合テスト上で `Trx 4266 / 4267` 間の相互 `waiting` デッドロックを物理再現し原因を特定しました。
  2. 対象 `DRAFT` スタッフ ID のみを動的抽出したうえで、**スタッフ ID 昇順に 2段階先行ロック（2PL, `SELECT ... FOR UPDATE`）** を取得する構造へ再設計しました（`B-04`）。
  3. 再照会後の新規 `DRAFT` 混入検知時は自動確定せず **fail-fast 全体ロールバック** するよう防御しました（`R-02`）。
* **最終解決結果**: デッドロック再現テストおよび並行性統合テスト（`R-01`から`R-04`）全数 PASS、94/94 回帰通過により再現した競合シナリオでの解決を検証しました。

### 事例 2. 出退勤重複打刻および API 認可・識別子偽装（IDOR）脆弱性
* **何が問題だったか**: 同一スタッフが `CLOCK_IN` / `CLOCK_OUT` を同時に 2回要求した際に重複勤怠行が生成されるリスクがあり、初期セルフサービス API や打刻 API のリクエスト本文（`storeId`, `storeStaffId`）を改ざんして他者データへアクセスされる恐れがありました。
* **ローカル AI の指摘**: Day 4 監査でセルフサービス API の IDOR を **FAIL** として検出し、Day 10 再監査（`A-05`, `A-06`）で打刻要求のセッション識別子検証および境界テストの補強が求められました。
* **どのように修正したか**:
  1. コントローラーがリクエスト本文の ID を信頼せず、Spring Security セッション `Principal` から `memberId` / `storeStaffId` を強制導出するよう修正しました。
  2. `AttendanceMapper.xml` の `lockStoreStaffForUpdate` 戻り値型（`resultType="java.lang.Long"`）欠落を修正し、親行（`store_staff`）の悲観的ロックで同時要求のうち 1件のみ成功・後続は拒否するよう実装しました。
  3. テスト環境の旧スキーマおよび `kintai_db` ハードコーディングを排除し、`schema.sql` rev.3 単一正本と `SELECT DATABASE()` 動的検証へ統一しました。
* **最終解決結果**: 新規隔離 DB 3回連続並行性テスト **6/6 PASS**（`R-08`, `R-09`）、打刻 API 偽装遮断 E2E テスト PASS、リモート Qwen 27B 再監査（`20261114_003657_dd991d3c`）9 PASS / 0 WARN で完了しました。

### 事例 3. 労働基準法第37条割増算定の誤りおよび分割勤務合算漏れリスク
* **何が問題だったか**: 単純な時間合算方式では、**深夜（22:00-05:00, 25%）と 1日 8時間超過残業（25%）が重なる区間の 50% 重畳割増** が欠落したり、同日 2回出勤の分割勤務（4h+5h）の 8時間超過分が算定されないリスクがありました。
* **ローカル AI の指摘**: Day 1 SRS 監査で 50% 重畳割増仕様の欠落リスクを指摘され、Day 11 監査（`F-03`）で深夜+残業重畳区間に対する統合テストアサーションの追加が求められました。
* **どのように修正したか**:
  1. 分単位タイムライン分割エンジンと `BigDecimal HALF_UP` 法定端数処理を実装し、同一日分割勤務の累積 8時間超過算定を連動させました。
  2. 契約有効期間境界（6/30-7/1）の時給参照および深夜 420分中残業 60分重畳（基本給 22,100円 + 深夜 2,275円 + 残業 325円 + 交通費 1,000円 = 25,700円）のアサーションを追加しました。
* **最終解決結果**: 12か月×3勤務日独立年間オラクルシナリオおよび境界テスト **6/6 PASS**（`R-11`, `R-12`）、リモート Qwen 27B ソース監査（`20261114_004900_46049b14`）**12/12 PASS** を達成しました。

### 事例 4. カレンダー UI 不一致、API 入力境界（`B-05`）および機微情報シリアライズ（`B-06`）検証
* **何が問題だったか**: Day 13 画面検証でシフトカレンダーが空表示となり、デモ DB の日本語氏名が `?` に文字化けしていました。また給与 `/excel` 以外の `/list`, `/calculate`, `/confirm-month` に `YYYY-MM` 形式検証がなく（`B-05`）、詳細 JSON 応答の機微情報非含有テストも未整備でした（`B-06`）。
* **ローカル AI の指摘**: Day 13 監査で実ブラウザーダウンロード検証の補強が求められ、Day 8 バックログ（`F-07`, `F-10`）として入力境界および DTO シリアライズ検証が引き継がれました。
* **どのように修正したか**:
  1. `calendar.html` の `CalendarEventDto` 項目マッピングを修正し、UTF-8 stdin でデモ DB（313行）を投入して実ブラウザーから `給与台帳_2026-01.xlsx`（5,092 bytes）のダウンロードを実測しました。
  2. `PayrollApiController` に共通 `validateYearMonth` 正規表現を適用し、4つの給与 API 全経路で不正年月（`2026-13`, `2026-1` 等）に対し一貫した HTTP 400 を返すよう統一しました。
  3. `PayrollApiValidationAndSerializationIntegrationTest` にて実 MockMvc JSON 応答を解析し、許可キー一致および口座番号・マイナンバー等の機微フィールド非含有を検証しました。
* **最終解決結果**: `R-15`から`R-19` VERIFIED、Day 13 ポートフォリオバンドル Qwen 監査（`20261114_000458_4af20cd9`）**15/15 PASS**、最終全体隔離回帰 **94/94 PASS** を達成しました。

### 事例 5. ローカル AI 監査パイプラインの遅延ボトルネックおよび偽正常終了（`B-09`）解消
* **何が問題だったか**: Day 4 にローカル監査 8回実行で **82,649 tokens / 1,147.38秒（約 19.1分）** を要し開発リズムが阻害されました。さらに後続の監査自動化で `think: false` が API 本文に送信されず空応答が返ったり、トークン超過失敗が `exit 0` と誤判定される欠陥（`B-09`）が発生しました。
* **どのように修正したか**:
  1. 同一ファイル（`ShiftService.java`, 469行）で Thinking ON/OFF の A/B 統制実験を実施して原因を特定し、`"think": false` を全域固定しました。
  2. `codex_hermes_bridge.ps1` / `audit_with_hermes.ps1` に `think` 本文送信、`[AllowEmptyString()]`、終了チャンク・verdict 表の fail-closed 検証を追加しました。
* **最終解決結果**:
  * **性能改善**: 同一ファイル基準で出力トークン **86.9% 削減**（9,803 -> 1,287 tokens）、監査時間 **6.32倍 高速化**（206.88秒 -> 32.72秒）。
  * **信頼性確保**: リモート Qwen 27B 最終再検証（`codex_20261114_112428_v10`）で **Overall PASS**（51.41秒）を取得し解消しました。

---

## 5. 限界、除外（`DEFERRED`）範囲および最終結論

* **最終検証結論**: ローカル MySQL 26.7.0 隔離環境基準 **94/94 PASS**（失敗 0、エラー 0、スキップ 0）および該当範囲のリモート Qwen 27B 独立監査を通過し、コード・既存証跡を `main` ブランチへ反映しました。*（注記：本日再編した本要約報告書自体に対する新規 Qwen 再監査は実施しておらず、原本証跡書・実行サマリーとの突合レビューのみを反映しています。）*
* **延期（`DEFERRED`）範囲**: `Day 12`（クラウドデプロイ）および `R-05`（MySQL 8.4 LTS Testcontainers — Docker/Podman 不在）は次期プロジェクト日程に合わせ来年再検討へ分離しました。
* **正直な制約明示**: 週40時間、法定休日、月60時間超過割増、税務・社会保険計算は現行 MVP 対象外であり、商用展開時は社会保険労務士の監修が必要です。モデルの `PASS` は参考監査であり、最終的な機能動作は常に JUnit 5 の決定的テストで保証しています。
```

## FILE: docs\rtm_ledger.md
```
# Requirements Traceability Matrix (RTM) - F-04 Residual Audit

| ID | 요구사항 원문 및 의미 | 수행할 작업 | 완료 조건 (DoD) | 필요 증거 | 관련 코드/위치 | 상태 | 검증 명령 / 비고 |
|:---:|---|---|---|---|---|:---:|---|
| **R-01** | `confirmMonthlyPayroll` 실제 단일 DB 트랜잭션/커넥션 경계 확인 | 선별, 부모락, 재조회, 검증, UPDATE가 단일 Connection 및 활성 트랜잭션 내에서 실행됨을 입증 | `TransactionSynchronizationManager.isActualTransactionActive() == true` 및 단일 `CONNECTION_ID()` 확인 | 테스트 로그, Connection ID 출력 | `PayrollService.java` (`confirmMonthlyPayroll`) | **VERIFIED** | 통합 테스트 실측 완료 (`BUILD SUCCESSFUL in 29s`) |
| **R-02** | 최종 재조회 이후 신규 DRAFT 경쟁 조건 재현 및 격리 검증과 업무 정책 채택 | T1/T2 경쟁의 기술적 격리 실측, 경계 시 롤백 정책 문서화 및 사용자 채택 결정 기록 | 신규 DRAFT의 자동 확정 방지 및 fail-fast 전체 롤백 실측, 업무 정책 승인 상태 추적 | JUnit XML, DB 상태 증거, `docs/f04_evidence/r02_business_policy.md` | `PayrollService.java`, `PayrollMapper.xml`, `PayrollMonthlyConfirmPostQueryRaceConditionIntegrationTest.java` | **VERIFIED (기술 검증 및 정책 채택)** | 사용자께서 2026-11-14 스냅샷 고정 및 신규 DRAFT 경계 감지 시 전체 롤백 정책을 채택했습니다. 최신 전체 회귀에서 R-02 통합 테스트 2/2 PASS, 실패·오류 0. 정책 채택·제외 범위 정합성 Qwen Run `20261114_010419_130b40f4` 8/8 PASS. 격리 DB `kintai_full_test_d4124a317ed0`; 운영 DB 미사용. |
| **R-03** | Mapper SQL 및 상태 필터(DRAFT 전용, 갱신 건수 대조) 전수 조사 | `findDraftStaffIdsByStoreAndMonth`, `lockStoreStaffForUpdate`, `lockStorePayrollsForUpdate`, `updateAllPayrollStatusConfirm` SQL 및 파라미터 수록 | 4개 쿼리의 DRAFT 필터 조건 및 affected rows 일치 검증 확인 | SQL 원문, XML 라인 번호, 파라미터 매핑표 | `PayrollMapper.xml` | **VERIFIED** | 정적 분석 및 DDL 대조 완료 |
| **R-04** | 실제 락 대기 및 5단계 타임라인 추적 | 상호 역순 락 획득으로 인한 InnoDB 데드락 재현 실측 및 실제 락 대기(waiting) 관계 입증 | Trx 4266/4267 간 상호 `waiting` 물리적 증거 확인. (수정된 2단계 잠금의 실시간 5단계 직렬화 타임라인과는 구분 보존) | `show_engine_innodb_status_deadlock.txt` | `docs/f04_evidence/` | **VERIFIED (데드락 입증)** | 데드락 재현 및 상호 대기 교착 관계 입증 완료 |
| **R-05** | MySQL 8.4 LTS 최종 검증 및 26.7 분리 | 로컬 `MySQL 26.7.0 Innovation` 결과와 `MySQL 8.4 LTS` 결과를 혼합하지 않고 각각 분리 기록 | `SELECT VERSION()` 실측치 명시 및 8.4 LTS 전용 검증 분리 | DB 버전 출력, EXPLAIN 결과 분리 파일 | `docs/f04_evidence/` | **DEFERRED (2027-01)** | MySQL 8.4/Testcontainers는 사용자 지시에 따라 현재 작업 범위에서 제외. Docker/Podman 부재로 미실행 상태를 보존하고 예정된 재검토일에 다시 판단 |
| **R-06** | 테스트 픽스처 독립성 및 반복 실행 안정성 검증 | 고정 ID 충돌 제거(동적 추적 ID), 대상 통합 테스트 반복 실행 안정성 검증 | 대상 통합 테스트 3회 연속 실행 성공 (마지막 `--rerun-tasks`). 전체 테스트 스위트의 순서 독립성은 별도 미검증 | `build/test-results/test/` | `PayrollMonthlyConfirmPostQueryRaceConditionIntegrationTest` | **VERIFIED (대상 반복 검증)** | 대상 테스트 3회 연속 실행 통과 확인 완료 |
| **R-07** | 최종 독립 감사 및 11대 포맷 종합 보고 | R-01~R-06 결과를 대조하여 누락 없는지 독립 검수 후 11대 규격 보고서 출력 | 11개 항목을 누락 없이 순서대로 팩트 기술 | 최종 보고서 본문 | 본 세션 응답 | **VERIFIED** | 11대 표준 보고서 출력 완료 |
| **R-08** | 출퇴근 CLOCK_IN 동시 요청 중복 생성 차단 및 비관적 락 정합성 실측 | 동일 직원 동시 CLOCK_IN에서 성공 1건·업무 거부 1건 및 중복 미생성을 검증한다. | 2개 스레드 결과, DB 상태, 격리 DB 증거와 반복 로그 | `AttendanceService.java`; `AttendanceMapper.xml`; `AttendanceServiceConcurrencyIntegrationTest.java`; `docs/audit_runs/day10_isolated_regression_20261114_093404/` | **VERIFIED (로컬 MySQL 26.7)** | 최종 소스에서 신규 격리 DB 3개 각각 2건 테스트, 총 6건 PASS, 실패·오류 0. 요약과 JUnit SHA-256은 run 폴더 `summary.json`. 당시 전체 회귀(B-05/B-06 이전) 92 PASS, 이후 B-05/B-06 포함 최신 전체 회귀(2026-11-14) 94 PASS (`kintai_full_test_8a75679f5b1e`). 운영 DB 미사용. |
| **R-09** | 출퇴근 CLOCK_OUT 동시 요청 완료 상태 보존 및 중복 퇴근 거부 실측 | 동일 직원 진행 근태의 동시 CLOCK_OUT에서 성공 1건·거부 1건, 최종 COMPLETED를 검증한다. | 2개 스레드 결과, DB 최종 상태, 격리 DB 증거와 반복 로그 | `AttendanceService.java`; `AttendanceMapper.xml`; `AttendanceServiceConcurrencyIntegrationTest.java`; `docs/audit_runs/day10_isolated_regression_20261114_093404/` | **VERIFIED (로컬 MySQL 26.7)** | 최종 소스에서 신규 격리 DB 3개 각각 CLOCK_OUT 테스트 포함 2건 PASS, 총 6건 PASS, 실패·오류 0. 당시 전체 회귀(B-05/B-06 이전) 92 PASS, 이후 B-05/B-06 포함 최신 전체 회귀(2026-11-14) 94 PASS (`kintai_full_test_8a75679f5b1e`). 운영 DB 미사용. |
| **R-10** | Day 10 출퇴근 동시성 독립 Qwen 감사 및 감사관 지적 해소 | 최신 근태 타각·인증·입력 경계 구현과 반복 검증을 독립 감사한다. | 현행 소스 번들, 실행 결과, A-05/A-06 및 재검수 결과 보존 | `docs/audit_runs/prior_residuals_20261114/qwen_day10_final/hermes_final/`; bundle SHA-256 `dd991d3ca2ab013943b9f6e7f5b676524b4e77df34d67fe77e881586f03aa7c8` | **VERIFIED (로컬 MySQL 26.7 범위)** | 원격 `http://100.111.193.94:11434`, `qwen27b:latest`, Run `20261114_003657_dd991d3c`. 10 findings 중 PASS 9, N/A 1(급여 계산은 Day10 근태 타각 감사 범위 밖), WARN/FAIL 0. 기존 A-05/A-06을 세션 ID 강제 바인딩/소유권 검사, 실제 필터 미인증 요청 테스트, 입력 거부 및 Mapper 무호출로 해소. report SHA-256 `31e8e18609ec458e8e589888646a05502f1f37c269bc4820b25df6bdb62bf430`. |
| **R-11** | Day 11 12개월 급여 계산 시나리오 및 계약 유효기간 | 월별 근무 분포, 계약 이력, API/Service 경로와 급여를 대조하고 독립 연간 합계를 검증한다. | 12개월×3근무일, 계약 임금 변경, 독립 연간 oracle, 타각 HTTP/API 경로, JUnit 및 격리 DB 로그 | `AnnualPayrollScenarioIntegrationTest.java`; `AttendancePunchApiIntegrationTest.java`; `docs/audit_runs/isolated_day11_20261114_093228_kintai_day11_test_3a25e5513c4f/`; `docs/audit_runs/isolated_all_20261114_094427_kintai_full_test_d4124a317ed0/`; remote Qwen Run `20261114_004900_46049b14` | **VERIFIED (로컬 MySQL 26.7)** | 12개월 각각 3일의 근태 및 독립 연간 oracle 대조 PASS, 계약 경계 6/30·7/1 PASS. 실제 Spring Security 필터→Controller→Service→DB 타각 API spoofed ID 차단·저장 검증 PASS(전용 통합 테스트 및 R-10 재감사 증적 참조). 당시 전체 회귀 92 PASS(이후 B-05/B-06 포함 최신 94 PASS). Qwen R-11/R-12 소스 감사 12/12 PASS, WARN/FAIL 0. |
| **R-12** | Day 11 경계·결함 시나리오 | 야간·다회 휴게·계약 경계·동일일 분할 근무 8시간 누적·월마감·반올림을 독립 기대값으로 검증한다. | 각 경계의 통합/단위 테스트, 저장된 실행 및 JUnit | `AnnualPayrollScenarioIntegrationTest.java`; `LaborCalculationEngineTest.java`; `SplitShiftOvertimeDefectTest.java`; `PayrollCalculatorStatutoryRoundingTest.java`; `docs/audit_runs/isolated_day11_20261114_094349_kintai_day11_test_01f4cb1439e6/`; `docs/audit_runs/isolated_all_20261114_094427_kintai_full_test_d4124a317ed0/` | **VERIFIED (로컬 MySQL 26.7)** | 4h+5h 분할근무 후 CLOCK_OUT service에서 60분 초과 산출 및 월 급여 반영 PASS. 20:00~05:00 야간 420분 중 연장 60분 중첩의 25%+25%=50% 프리미엄 및 급여 합계 PASS. 1/31 포함·2/1 제외, 계약·자정·다회휴게·야간휴게 차감·HALF_UP 경계 PASS. Qwen Run `20261114_004900_46049b14` 12 findings PASS, WARN/FAIL 0. 당시 전체 회귀 92 PASS(이후 B-05/B-06 포함 최신 94 PASS). |
| **R-13** | Day 11 격리 실행 및 회귀 증거 무결성 | 새 격리 DB에서 대상 테스트와 전체 회귀를 실행하고 로그·JUnit·요약·해시를 보존한다. | 대상 테스트 및 전체 회귀 종료 코드 0, 격리 DB 및 매니페스트 대조 | `docs/audit_runs/isolated_day11_20261114_094349_kintai_day11_test_01f4cb1439e6/`; `docs/audit_runs/isolated_all_20261114_094427_kintai_full_test_d4124a317ed0/` | **VERIFIED (로컬 MySQL 26.7)** | 당시 대상 연간·경계 테스트 6 PASS, 전체 회귀 92 PASS(이후 B-05/B-06 포함 최신 94 PASS, `kintai_full_test_8a75679f5b1e`), 실패·오류·스킵 0, Gradle exit 0. 이전 81건 결과는 당시 스냅샷으로 보존. MySQL 8.4 Testcontainers 결과로 일반화하지 않음. |
| **R-14** | Day 11 독립 검수 및 추적 문서 정합성 | Qwen 입력·결과를 보존하고 지적사항 및 한계를 한·일 증적·RTM·인수인계에 반영한다. | Run ID·입력 해시·결과·지적별 대조와 일치 문서 | `docs/audit_runs/preflight_1e3b8fba040b4197aed07afbde9334f2/`; final document audit `docs/audit_runs/preflight_9c8fda322194453ba642d56325a31d10/`; 한·일 portfolio evidence; `docs/handoff/20261114_residuals_handoff.md` | **VERIFIED (2026-11-14 source/document scope)** | Qwen 27B 원격 Run `20261114_004900_46049b14`, 입력 bundle SHA-256 `46049b14578b32462a6f105966643d658919d5ed658faaf885796e2e1cd2c4b8`, report SHA-256 `b1ae1c9d7844d92b6ab4b47bf5e5c66dbb0f1f3f916549688e3c83ea56798f10`. 12/12 PASS, WARN/FAIL 0. 기존 결함 F-03 중첩 프리미엄 경고에 통합 단언 추가 후 새 격리 DB 대상 6/6 PASS 및 전체 92/92 PASS. 한·일 증적과 RTM/인수인계 수치 대조 완료. 최종 고정 문서 독립 감사 Run `20261114_005527_76dc1c9f`: 10/10 PASS, WARN/FAIL 0. 앞선 2개 문서 경고의 수정 확인. bundle SHA-256 `76dc1c9f5b3750c9b38d0777bd302893d4ea4dbe3783a0b8ef107edf37312d93`, report SHA-256 `dee296cda885e4bdc8208c9145ab61757d6a5323ecd6f7c9b21016b2cfc6b124`. |
| **R-15** | Day 13 격리 데모 데이터 생성 및 연간 분포 | 2026년 synthetic schedule/attendance fixture를 신규 격리 demo DB에 적재하고 DB 식별자·기간·행 수·UTF-8 샘플명을 대조한다. | `SELECT DATABASE()` 및 기간/건수/문자 인코딩 확인, 화면에서 해당 데이터 조회 | `scripts/prepare_local_demo_db.ps1`; `scripts/local_demo_attendance.sql`; `docs/audit_runs/day13_calendar_20261114/` | **VERIFIED (로컬 MySQL 26.7)** | 최종 DB `kintai_demo_9f3f8487bc42`: confirmed shift 313건과 attendance 313건, 2026-01-01~2026-12-31, owner-name UTF-8 HEX 확인. 초기 DB의 손상된 이름은 보존하고 새 DB로 대체. 운영 DB 사용 안 함. |
| **R-16** | Day 13 캘린더 API DTO와 브라우저 표시·상세 정합성 | CalendarEventDto 응답을 그대로 표시하고 상세/확정 모달에서 이벤트 기간·휴게 시간을 사용한다. | 독립 API 응답과 브라우저 월간 이벤트·상세 모달 실측, 전체 회귀 | `calendar.html`; `CalendarEventDto.java`; `docs/audit_runs/isolated_all_20261114_074456_kintai_full_test_3df696be0820/` | **VERIFIED (로컬 브라우저·MySQL 26.7)** | API 필드명과 화면 매핑 불일치를 수정. November 2026 confirmed events 및 11/02 상세 모달(09:00~18:00, 60분) 화면 확인. |
| **R-17** | Day 13 급여 데모 및 Excel 다운로드 확인 | 격리 데이터에서 급여 계산과 UI 표시를 확인하고 Excel 산출을 검증한다. | 화면 급여 값·이름·상태, API/XLSX 산출, 실제 브라우저 다운로드 이벤트 및 저장 파일 검사, 회귀 | `docs/audit_runs/day13_calendar_20261114/ui_review_record.md`; `payroll_api_list.json`; `payroll_2026-01_browser_download.xlsx`; `excel_browser_download.json`; `payroll_excel_validation.json` | **VERIFIED (로컬 MySQL 26.7)** | 격리 DB 급여 UI에서 두 DRAFT 행과 급여액을 확인하고 Excel 출력을 실행했다. 브라우저 다운로드 이벤트가 완료되어 5,092-byte 파일을 확보, OOXML·월·¥141,700·¥164,332를 검증했다. 파일 SHA-256 `ba127f65f193f8289c2b4e445efc09a06e1590e2230ed0b1ad603d839cfdcd98`. Chrome 과거 차단은 이력으로 보존한다.
| **R-18** | Day 13 독립 Qwen 감사 및 증적 추적 | 갱신된 번들·실행 결과·UI 증거를 포함해 Hermes Qwen 27B 독립 감사를 수행하고 지적을 반영한다. | 유효 입력 manifest, 독립 결과 원문/해시, 지적과 조치·재검수 일치 | `docs/audit_runs/day13_calendar_20261114/qwen27b_day13_final/report.md`; `qwen27b_day13_final/audit_run.json`; bundle SHA-256 `d69a9b63c9a6a79043a38fb9c8942ab9c3fd0f9936087ffe5865d778f6817379` | **VERIFIED (로컬 MySQL 26.7 범위)** | 원격 `http://100.111.193.94:11434`, `qwen27b:latest` IQ3_M 27.3B, digest `23c3820b0d9326b73e97191b9fb7e16b53f6e24bb3449d2a17037c5f2b6179a1`; Run ID `20261113_234922_d69a9b63`; report SHA-256 `0dc324150a75bb8a1d3035f7ee770a03ec2bffd8241a3a041ffd9ee2066fbf32`. 최신 번들 15개 감사 finding 모두 PASS, WARN/FAIL 0. R-17 브라우저 다운로드 이벤트와 저장 XLSX 내용 검증을 포함한다. MySQL 8.4는 2027-01 DEFERRED.
| **R-19** | Day 13 최종 포트폴리오 패키지 | 기술 증적, 아키텍처 그림, 데모 안내 및 한·일 요약을 실제 결과에 맞게 작성한다. | 문서·링크·상태·수치를 RTM/원본 증거와 대조하고 로컬 문서 검사 및 독립 감사를 보존한다. | `docs/final-portfolio/`; `docs/audit_runs/day13_calendar_20261114/final_portfolio_review/hermes_final_snapshot/` | **VERIFIED** | 상대 Markdown 링크 검사 PASS. 원격 Qwen 27B Run `20261114_000458_4af20cd9`, 15 finding PASS, WARN/FAIL 0. 번들 SHA-256 `4af20cd936e1a70c91ca8d6b905f0d6f948d108b1c09fd226958a2a6c2b3b543`; report SHA-256 `4876963e92a4cb197596d02fb5d172a50db4a13b7b382930c312e433337d1d28`. 최초 감사 WARN는 Mapper/Service 잠금 증거를 추가해 해소하고 재검수 PASS. |

> **R-08부터 R-10 후속 증적 재평가 (2026-11-13):** 위 상태는 당시 기록을 보존한다. 현재 XML은 동시성 2건 실패 0건이나, 과거 3회 각각의 로그와 기존 기록의 JUnit 해시에 일치하는 원본은 미확인이다. 감사 원문 A-05와 A-06은 WARN이므로 R-10의 지적 해소 전체는 `PARTIALLY_VERIFIED`로 재평가한다. A-05는 직원 세션 ID 바인딩과 점주 소유권 검사가 코드에 있으나 해당 보안 테스트의 이번 재실행은 Gradle 환경 제한으로 미실행이다. A-06은 입력 경계 검증이 남았다. `/punch`의 인증 객체 부재는 컨트롤러 자체 차단이 아니라 보안 필터 설정에 의존하므로 실제 경계 검증이 필요하다. 격리 DB 부재와 소켓 권한 제한으로 동시성 재실행은 `BLOCKED`, MySQL 8.4는 별도로 `BLOCKED`를 유지한다. 당시 감사 번들은 `docs/audit_runs/20261113_030041_19b587c7/reconstructed_input_bundle.md`에 해시 일치 재구성본을 보존한다.
> **이후 상태 정정 (2026-11-14):** 아래 2026-11-13 R-08~R-10 BLOCKED/PARTIALLY_VERIFIED 문장은 당시 증거 재평가 스냅샷입니다. 최신 격리 반복 결과와 Qwen 재감사로 R-08/R-09/R-10은 위 RTM 표에서 제한된 로컬 MySQL 26.7 범위로 VERIFIED 처리했습니다. 당시 전체 회귀는 92건 PASS였으며, 이후 B-05/B-06을 포함한 최신 전체 회귀는 94건 PASS(`docs/audit_runs/isolated_all_20261114_114819_kintai_full_test_8a75679f5b1e/`)입니다. 과거 로그는 삭제하거나 최신 상태로 오인하지 마세요.

> **Day 10 개선·반복 검증 추가 기록(2026-11-13):** 기존 RTM 구조를 유지하고 오늘의 실행 사실만 추가한다. 정식 rev.3 스키마 기반 신규 격리 DB 3개에서 `AttendanceServiceConcurrencyIntegrationTest`를 강제 재실행했다. 총 6건 성공, 실패 0건, 오류 0건, Gradle 종료 코드 0이다. 구형 스키마의 `break_id`와 현재 Mapper의 `attendance_break_id` 불일치, JDBC URL 전달 오류, `UP-TO-DATE` 오판 가능성은 실패·보정 이력으로 보존했다. 원본: `docs/audit_runs/day10_isolated_regression_20261113_154029/summary.json`. 이 기록은 MySQL 8.4 Testcontainers 성공이나 이번 Qwen 재감사를 의미하지 않는다.
> **전체 회귀 진단 기록(2026-11-13):** Day 10 최소 fixture DB에서 전체 Gradle 테스트 79건을 실행한 결과 65건 성공, 14건 실패, 종료 코드 1이다. 실패는 급여 fixture 부족, 락·performance_schema 선행조건, CSRF 인증 흐름으로 분류했다. Day 10 전용 동시성 테스트의 3회·총 6건 성공 판정과 전체 회귀 결과를 혼합하지 않는다. 상세: `docs/audit_runs/day10_isolated_20261113_155735_kintai_day10_test_c7bd2380a226/full_regression_triage_KO.md`.

> **전체 Gradle 회귀 재검증 정밀 기록 (2026-11-13):** 신규 rev.3 스키마 및 전체 Fixture(member 3, store 1, staff 2, contract 2)가 적재된 격리 DB kintai_full_test_b769142733d1에서 .\gradlew.bat test --rerun-tasks --no-daemon --console plain을 재실행함. 총 79건 중 **78건 성공(PASS), 실패 1건, 오류 0건, 종료 코드 1**. 기존 14건 실패 중 13건(급여 FK 10건, CSRF 로그인 인증 2건 등)은 Fixture 부재로 인한 환경적 실패였음이 물리적으로 입증되어 전원 PASS로 회복됨. 잔여 1건(PerformanceSchemaDataLocksIntegrationTest)은 테스트 코드 내부의 WHERE OBJECT_SCHEMA = 'kintai_db' 하드코딩으로 인한 격리 DB명 불일치 환경 이슈로 확인됨. 운영 DB kintai_db는 일체 접촉하지 않음. 상세 증적: docs/audit_runs/full_regression_20261113_160744_kintai_full_test_b769142733d1/.
> **PerformanceSchemaDataLocks テスト 보정(2026-11-13):** `OBJECT_SCHEMA = 'kintai_db'` 하드코딩을 제거하고 테스트 연결의 `SELECT DATABASE()` 결과를 파라미터로 사용하도록 테스트 코드만 수정했다. 기능 코드는 변경하지 않았다. 전체 회귀 재실행 전 상태는 `REVALIDATION_PENDING`이다.

> **PerformanceSchemaDataLocks 보정 후 전체 Gradle 회귀 최종 판정 (2026-11-13):** PerformanceSchemaDataLocksIntegrationTest의 동적 DB 스키마 바인딩 보정 후 신규 격리 DB kintai_full_test_a65c3d18271f에서 .\gradlew.bat test --rerun-tasks --no-daemon --console plain을 재실행함. 총 79건 중 **79건 전수 성공(PASS, 100%), 실패 0건, 오류 0건, Gradle 종료 코드 0 (BUILD SUCCESSFUL)** 달성. PerformanceSchemaDataLocksIntegrationTest PASS 확인. 운영 DB kintai_db 미접촉. 전체 회귀 테스트 최종 상태: **VERIFIED**. 상세 증적: docs/audit_runs/full_regression_20261113_161616_kintai_full_test_a65c3d18271f/.

> **Day 11 실행 및 Qwen 독립 검수 갱신 (2026-11-13):** 신규 격리 DB `kintai_full_test_cf0fbbd96042`에서 전체 회귀 81건, 실패·오류·스킵 0건, Gradle 종료 코드 0을 확인했고 Day 11 테스트 클래스 2건도 JUnit PASS다. Qwen `qwen27b:latest` 독립 검수는 Run ID `20261113_113749_a39493e5`로 실행 완료했다. 보고서 F-03의 정수 나눗셈 경고는 실제 `PayrollCalculator`의 `BigDecimal`/`HALF_UP` 구현과 불일치하여 오탐으로 판정한다. F-07은 테스트가 `attendance` 데이터를 직접 INSERT하므로 타각 API부터의 E2E PASS라는 설명이 부정확하다. 급여 상세·연간 합계 통합 시나리오는 통과했지만 실제 타각 경로는 이 테스트에서 검증하지 않았다. 검수 원문과 지적 대조: `docs/audit_runs/day11_local_final_review_20261113/`; 전체 대조표: `review_reconciliation_KO.md`. 상세 인수인계: `docs/handoff/20261113_2045_codex_day11_review_handoff.md`.
> **스키마 정본 통합 및 재검증 완료 기록(2026-11-13):** `src/main/resources/db/schema.sql` rev.3를 유일 정본으로 지정하고 DB 생성·선택 구문을 제거했다. 구형 `src/main/resources/schema.sql`은 `docs/legacy_audit_reports/schema.sql_legacy_20261113.sql`로 이동해 SHA-256을 보존했다. 이후 신규 격리 DB `kintai_full_test_d90b040155e0`에서 `.\gradlew.bat test --rerun-tasks --no-daemon --console plain`을 재실행하여 79건 전수 통과(0 failures, 0 errors, 종료 코드 0)를 실측 검증 완료함에 따라 `SCHEMA_REVALIDATION_PENDING` 상태를 **VERIFIED**로 갱신함. 상세 증적: `docs/audit_runs/full_regression_20261113_162950_kintai_full_test_d90b040155e0/`.
- 2026-11-13 안정화 운영 보완: 공용 격리 테스트 실행기와 허용 목록 기반 DB 정리기를 추가했다. 실행기 정적 반영은 완료했으나 현재 Codex 환경의 MySQL 포트 접근은 BLOCKED이다. 기존 로컬 MySQL 전체 회귀 79/79 PASS는 별도 실측으로 유지한다.
- 2026-11-13 공용 격리 실행기 기존 산출물 사후 확인: `isolated_all_20261113_164156_kintai_full_test_52bbea674f8d`의 보존된 실행 로그에서 Gradle 종료 코드 0을 확인하고, JUnit XML 33개를 사후 집계해 79 PASS, 실패·오류·스킵 0건을 확인했다. 실행기 상세 집계·SHA-256 자동 생성 수정 전의 실행이므로 수정 후 실행기의 E2E 검증 완료로 판정하지 않는다. 기존 로그와 XML의 매니페스트는 사후 생성해 해시 일치를 확인했다. 새 실행기의 종단 간 검증은 로컬 MySQL 접근 가능한 환경에서 대기 중이다. 로그 SHA-256 `7EE458FEDA4EC5915565FB18DF6C3B46C9E7DAB20AFF18D41BD7EE2CB62F1996`.
```

