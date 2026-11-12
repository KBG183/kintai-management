# Hermes v2 개정안 2차 독립 정밀 감사 보고서 (Independent Design Audit)

> **감사 문서 번호**: `AUDIT-HERMES-V2-20261112-001`  
> **감사 일자**: 2026-11-12  
> **감사관 역할**: 제3자 독립 기술 감사관 (Independent Technical Auditor)  
> **감사 대상**: `docs/hermes_v2_spec/` 내 산출물 8종 전수  
> **감사 원칙**: 기존 Kintai 소스 코드 및 운영 스크립트 무수정, 원본 로그 불변 보존, 사실(Fact) 및 증거 기반 판정

---

## 1. 검토한 파일 및 실측 버전

| 파일명 | 실측 크기 / 라인 수 | 역할 및 개요 | 감사 판정 상태 |
|---|---|---|---|
| `01_hermes_v1_survey_report.md` | 3,923 B / 73 라인 | v1 파이프라인 구성요소 및 실행 흐름 조사서 | `PARTIALLY_VERIFIED` |
| `02_judgment_constitution_v2_draft.md` | 4,204 B / 73 라인 | 판정 헌법 v2 개정 초안 | `VERIFIED` |
| `03_result_schema_v2_draft.json` | 5,660 B / 245 라인 | HermesAuditResultV2 JSON Schema (Draft-07) | `FAILED` (스키마 결함 발견) |
| `03_result_schema_v2_spec.md` | 2,752 B / 65 라인 | 결과 스키마 기술 규격서 및 데이터 사전 | `PARTIALLY_VERIFIED` |
| `04_v1_v2_compatibility_migration.md` | 2,698 B / 49 라인 | v1-v2 호환성 및 마이그레이션 분석서 | `PARTIALLY_VERIFIED` |
| `05_golden_test_specifications.md` | 4,682 B / 85 라인 | Day 1~9 오판 기반 10대 골든 테스트 명세서 | `NOT_VERIFIABLE` (런타임 픽스처 부재) |
| `06_deterministic_verifier_design.md` | 4,142 B / 109 라인 | 결정론적 결과 검증기 구현 설계서 | `PARTIALLY_VERIFIED` |
| `07_unresolved_and_pending_verification.md` | 2,130 B / 23 라인 | 미해결 사항 및 추가 검증 목록 | `VERIFIED` |

---

## 2. 문서 간 정합성 교차 대조 결과 (Cross-Document Consistency)

산출물 8종을 교차 대조한 결과, 다음과 같은 치명적 규격 불일치 및 설계 충돌이 식별되었다:

### 2.1. [결함 C-01] `verdict_summary`의 `counts_by_verdict`와 `findings[].verdict` Enum 불일치 (치명적)
- **위치**: 
  - `03_result_schema_v2_draft.json` 131행 vs 194행
  - `02_judgment_constitution_v2_draft.md` 제2조 제2항
- **불일치 내용**:
  - `findings[].verdict`의 유효 Enum은 `["PASS", "FAIL", "WARN", "N/A", "NOT_VERIFIABLE"]`로 슬래시(`/`)가 포함된 **`"N/A"`**로 정의됨.
  - 반면, `verdict_summary.counts_by_verdict`의 필수 속성 키는 슬래시가 빠진 **`"NA"`** (`["PASS", "FAIL", "WARN", "NA", "NOT_VERIFIABLE"]`)로 선언됨.
- **영향**:
  - `findings` 배열에 `verdict: "N/A"` 항목이 집계될 경우, JSON 키 규격이 일치하지 않아 결정론적 검증기 및 JSON 스키마 검증기에서 100% 검증 실패(Reject) 발생.

### 2.2. [결함 C-02] `verdict_summary.overall_verdict`에 `N/A` 상태 누락
- **위치**:
  - `03_result_schema_v2_draft.json` 123행
  - `03_result_schema_v2_spec.md` 3.3절
- **불일치 내용**:
  - 개별 finding의 verdict는 `N/A`가 허용되나, `overall_verdict`의 Enum은 `["PASS", "FAIL", "WARN", "NOT_VERIFIABLE"]` 4가지만 허용함.
  - 만약 감사 대상 번들이 단순 정적 마크다운이거나 비즈니스 로직이 없어 전 항목이 `N/A`인 경우, `overall_verdict`를 무엇으로 채워야 하는지 모호하며 스키마 유효성 위반 위험 존재.

### 2.3. [결함 C-03] 감사 런 원본 경로(User Home vs Project Root) 조사 오류
- **위치**: `04_v1_v2_compatibility_migration.md` 27행
- **불일치 내용**:
  - 문서에 `docs/audit_runs/20261112_054459_PROMPT/report.md`를 예시로 들었으나, 실제 물리적 디스크 조사 결과 해당 디렉터리는 프로젝트 루트(`c:\dev\kintai\docs\audit_runs\`)가 아니라 **사용자 홈 디렉터리(`C:\Users\klad3\docs\audit_runs\`)**에 고아(Orphan) 상태로 생성되어 있었음.
  - 작업 실행 경로(`cwd`)에 따라 감사 리포트가 엉뚱한 위치에 저장되는 현행 v1 스크립트의 취약점을 간과하고 프로젝트 내 경로로 잘못 기술함.

---

## 3. 8대 핵심 위험(R1 ~ R8) 심층 감사 결과

### R1. 실제 시스템 조사 범위의 정직성 (조사 vs 미조사 구분)
- **감사 판정**: `PARTIALLY_VERIFIED`
- **검증 팩트**:
  - `audit_with_hermes.ps1`, `state_manager.ps1`, `audit_config.json`, `constitution_4layer_specification.md`는 실제 파일 열람 및 코드 구조 파악이 완료됨.
  - 그러나 `state_machine_ledger.json`의 전체 트랜잭션 재생(Replay) 검증이나, `audit_runs` 내 22개 디렉터리 원본 리포트 전수에 대한 파싱 검증은 실제로 수행되지 않았음.
  - 따라서 단순 "파일 존재 확인 및 샘플 열람"을 "전수 시스템 조사 완료"로 표현한 것은 과장이며, **`PARTIALLY_VERIFIED`로 하향 조정**함.

### R2. 토큰 예측 공식(/3.0 ➔ /4.0) 및 컨텍스트 상향의 기술적 타당성
- **감사 판정**: `FAILED` (미검증 변경 제안 위험)
- **위험 분석**:
  - `07_unresolved_and_pending_verification.md`의 ISSUE-02에서 자바 코드의 글자수/토큰 비율을 `/4.0`으로 완화하거나 `NumCtx: 32768`로 일괄 상향을 권고함.
  - **결함 지적**: Qwen 2.5/3.8(Qwen2/Qwen3 토크나이저)의 BPE 토크나이저는 한글, 공백, 줄바꿈, CamelCase 자바 변수명(`payrollExcelExporter`)에 대해 서구권 영어 텍스트보다 훨씬 많은 토큰(토큰당 2.0~2.5글자)을 소모함.
  - 글자수 / 4.0으로 변경할 경우, 실제 토큰 수가 컨텍스트를 초과함에도 사전 방어선이 뚫려 **Ollama 내부에서 조용히 토큰 절삭(Silent Truncation)이 발생하는 치명적 회귀 위험**이 있음.
  - 또한 자택 PC RTX 5070 Ti의 VRAM(16GB)에서 Qwen 27B IQ3_M 구동 시 `NumCtx 24576`은 안정적이나, `32768`로 상향 시 KV Cache가 약 3.5GB 추가 소모되어 OOM(Out of Memory) 또는 시스템 RAM 스왑으로 인한 급격한 속도 저하(51 tokens/s ➔ 5 tokens/s) 위험이 실측되지 않았음.
- **시정 조치**: 공식 비율 변경을 불허하며, 정밀 BPE 토큰 계산 스크립트 작성 및 VRAM 실측 전까지 현행 `/3.0` 및 `NumCtx: 24576`을 엄격히 동결해야 함.

### R3. v1-v2 폴백 및 데이터 정합성 (Silent Fallback 위험)
- **감사 판정**: `FAILED` (설계 결함)
- **위험 분석**:
  - `04_v1_v2_compatibility_migration.md` 4.절의 순서도에서 `report.json` 유효성 검사 실패 시 `report.md`로 넘어가는 로직은 **치명적인 Silent Fallback(조용한 대체) 취약점**을 가짐.
  - 모델이 손상된 JSON이나 허위 필드를 출력하여 검증기에서 에러가 났을 때, 이를 마크다운 파서로 자동 폴백시키면 모델의 JSON 스키마 위반이 감춰지고 레거시 정규식 통과로 부정 승인될 위험이 존재함.
- **시정 조치**: 
  - `report.json`이 존재하지만 스키마나 검증을 통과하지 못한 경우, **v1 파서로 폴백하지 않고 즉시 파이프라인을 `AUDIT_FAILED`로 중단(Hard Stop)**해야 함.
  - 오직 `report.json`이 전혀 생성되지 않은 순수 v1 레거시 디렉터리에 한해서만 v1 파서를 적용해야 함.
  - 동일 실행 여부를 증명하기 위해 `report.json`의 `run_id`와 `bundle_sha256`이 `report.md` 헤더 및 `state_machine_ledger.json`과 100% 일치해야 함.

### R4. FAIL 판정 7대 필수 성립 요건의 과도한 제한 위험
- **감사 판정**: `PARTIALLY_VERIFIED`
- **위험 분석**:
  - 제3조 제6항에서 "실패하는 단위 테스트 코드, 수학적 반례 입력값, 또는 런타임 에러 스택 제시"를 의무화함.
  - **우려 사항**: 소스 코드 상에서 명백히 SQL Injection 위험(`Statement.executeQuery("SELECT ... " + input)`)이나 암호화되지 않은 비밀번호 평문 저장이 눈에 뻔히 보임에도, 로컬 27B 모델이 재현 단위 테스트 코드를 작성하지 못했다는 이유로 `WARN`으로 격하되는 것은 보안상 중대한 결함 방치(False Negative)로 이어질 수 있음.
- **시정 조치**:
  - 증명 방식을 3가지(① 실행/테스트 증명, ② 정적 코드 증명, ③ 수학적/논리적 반례)로 명확히 병렬 인정하고, "정적 코드 분석상 반론의 여지 없는 명백한 보안 취약점"인 경우 테스트 코드가 없더라도 정적 증명(Code Proof)만으로 FAIL을 인정하도록 요건을 유연화해야 함.

### R5. 골든 테스트 실현 가능성 (문서 vs 실행 픽스처)
- **감사 판정**: `NOT_VERIFIABLE`
- **검증 팩트**:
  - `05_golden_test_specifications.md`에 기술된 10종의 테스트 케이스는 매우 정교하게 기획되었으나, 실제 테스트를 구동할 수 있는 **입력 번들 파일(.md/.java 픽스처)과 검증용 기대 JSON 단언문(Assertion)이 물리적 파일로 존재하지 않음.**
  - 따라서 현재 상태는 "테스트 명세서 기획"일 뿐이며, 런타임 검증 가능 여부는 아직 입증되지 않았음.

### R6. Finding ID 및 라이프사이클 추적의 일관성
- **감사 판정**: `PARTIALLY_VERIFIED`
- **위험 분석**:
  - `findings[].tracking_status`에 `FIXED`, `VERIFIED_FIXED` 등이 추가되었으나, **누가 어떤 증거로 상태를 변경하는가에 대한 주체가 불분명**함.
  - LLM에게 `VERIFIED_FIXED`를 판정하게 하면 또다시 셀프 승인(Self-Approval) 환각이 발생함.
- **시정 조치**:
  - LLM은 오직 코드 상태에 따라 결함을 발견(`OPEN`)하거나 재발견(`REOPENED`)할 수만 있으며, `FIXED`와 `VERIFIED_FIXED`로의 전이는 반드시 **외부 JUnit 테스트 통과 증적과 개발자의 커밋이 결합된 결정론적 상태 관리자(`state_manager.ps1`)에 의해서만 확정**되어야 함.

### R7. JSON Schema 규격 엄밀성 및 Validator 검증
- **감사 판정**: `FAILED`
- **위험 분석**:
  - `03_result_schema_v2_draft.json`에 `additionalProperties: false` 정책이 정의되어 있지 않아, 모델이 임의의 필드(환각 데이터)를 추가하더라도 스키마 검증을 통과하는 취약점이 있음.
  - 필수 필드인 `findings[].evidence.code_snippet_or_proof`에 최소 문자열 길이(`minLength`) 제약이 없어 빈 문자열(`""`)을 넘겨도 통과될 수 있음.

### R8. 모델 및 실행 메타데이터 재현성
- **감사 판정**: `PARTIALLY_VERIFIED`
- **위험 분석**:
  - `model_name: "qwen27b:latest"`는 태그가 갱신되면 과거 모델과 달라질 수 있음.
  - `model_digest`(Ollama 이미지 SHA-256)를 스키마에 필수로 넣은 설계는 매우 타당하나, `audit_with_hermes.ps1`이 실제로 Ollama REST API 호출 시 `/api/show`를 호출하여 다이제스트를 실시간으로 받아오는지 확인되지 않음.

---

## 4. 추가로 발견된 시스템 설계 및 헌법 충돌 위험

### 4.1. 4-Layer 엔터프라이즈 헌법(v2.1)과 판정 헌법 v2 간 우선순위 충돌
- **현상**: `docs/constitution_4layer_specification.md`는 프로젝트 전역의 6단계 상태 머신을 통제하는 상위 헌법(SSOT)임.
- **충돌**: v2 개정 초안 제2조/제3조가 4-Layer 헌법의 Layer 3(전문 감사관 헌법)과 연동 규칙을 명시하지 않아, 두 문서 간 판정 기준 불일치 시 어느 헌법이 우선하는지 모호함.
- **원칙 확립**: 전역 4-Layer 엔터프라이즈 헌법(v2.1)이 상위 규범이며, 판정 헌법 v2는 Layer 3의 상세 시행세칙으로 종속되어야 함.

### 4.2. 검증기 자체 에러와 모델 결함 판정의 혼동
- `verify_audit_result.ps1` 프로토타입 설계에서 JSON 파싱 에러(Exit 10), ID 중복(Exit 11), 통계 불일치(Exit 13)가 발생했을 때, 이를 소스 코드의 결함(`AUDIT_FAILED`)으로 처리할 것인지, 파이프라인 자체 오류(`AUDIT_INCOMPLETE`)로 처리할 것인지 명확한 상태 머신 매핑이 누락됨.

---

## 5. 결함 목록 및 심각도별 조치 우선순위

| 결함 ID | 결함 내용 | 심각도 | 조치 우선순위 | 필수 조치 요구사항 |
|---|---|:---:|:---:|---|
| **DEF-01** | `counts_by_verdict`의 키 `"NA"`와 `findings[].verdict`의 Enum `"N/A"` 불일치 | **P0** | 즉시 수정 | 스키마 양측을 `"N/A"` (또는 일관된 `"NA"`)로 완전 통일 |
| **DEF-02** | 유효하지 않은 v2 JSON 발생 시 v1 마크다운으로의 Silent Fallback 위험 | **P1** | 즉시 수정 | JSON 오류 시 즉각 파이프라인 중단(Hard Stop) 정책 확립 |
| **DEF-03** | 자바 토큰 추정 글자수 / 4.0 및 NumCtx 32768 성급한 완화 제안 | **P1** | 보류/동결 | 실측 데이터 없는 비율 변경 금지, 현행 24576 및 /3.0 유지 |
| **DEF-04** | FAIL 판정 시 테스트 코드 필수화로 인한 정적 코드 명백한 결함 누락 위험 | **P1** | 규정 수정 | 정적 코드 분석 증명(Static Proof)도 대등한 증거로 인정 |
| **DEF-05** | `additionalProperties: false` 누락 및 빈 문자열 검증 미비 | **P2** | 스키마 보완 | 스키마 내 불필요한 속성 침입 방지 및 minLength 선언 |
| **DEF-06** | 골든 테스트 10종의 물리적 픽스처 부재 | **P2** | 후속 구현 | `tests/golden_fixtures/` 디렉터리에 실제 입출력 파일 구축 |

---

## 6. 원본 설계에서 반드시 보존해야 하는 핵심 강점
1. **Severity와 Verdict의 완전 분리**: 모델의 감정적/주관적 P0 남발을 억제하는 가장 핵심적인 설계이므로 절대 후퇴해서는 안 됨.
2. **`NOT_VERIFIABLE` 상태의 공식화**: 번들 외 코드 결핍을 결함으로 오판하던 근본 원인을 해소하는 핵심 장치로 유지해야 함.
3. **독립 결정론적 검증기 게이트키퍼 구조**: LLM 텍스트를 그대로 신뢰하지 않고 외부 스크립트가 100% 규칙 기반으로 검증하는 아키텍처는 보존해야 함.
4. **기존 v1 로그의 불변성 원칙**: 과거 감사 이력을 훼손하지 않는 사이드카 방식은 그대로 유지되어야 함.

---

## 7. 최종 감사 종합 판정 및 결론

### [최종 판정]: **`CONDITIONAL_REJECT` (조건부 보완 요구)**
- **판정 사유**:
  - 본 Hermes v2 개정안은 v1의 고질적인 오판 패턴을 해결하기 위한 우수한 설계 방향성을 담고 있으나, **스키마 내 Enum 불일치(DEF-01)**, **Silent Fallback 회귀 위험(DEF-02)**, **실측되지 않은 토큰/컨텍스트 완화(DEF-03)** 등 런타임 장애를 유발할 수 있는 기술적 결함이 발견되었음.
- **운영 적용 승인 조건**:
  1. `03_result_schema_v2_draft.json`의 `"NA"` vs `"N/A"` 불일치 수정 완료.
  2. Silent Fallback 방지 규칙(손상된 JSON 발생 시 즉시 중단)을 마이그레이션 및 상태 관리자 설계에 반영.
  3. 토큰 추정 공식과 컨텍스트 한도는 현행(글자수 / 3.0, 24576)으로 보수적 유지.
  4. 10대 골든 테스트의 실제 픽스처 파일 구현 및 로컬 27B 모델 단독 런타임 검증 통과 증적 확보.

위 4가지 필수 조치가 완료되기 전까지는 **Hermes v2의 런타임 프로덕션 적용을 전면 보류**하며, 개정안 규격서 상태로 동결 유지할 것을 권고합니다.
