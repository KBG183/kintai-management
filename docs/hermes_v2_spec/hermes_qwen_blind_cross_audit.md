# Hermes v2 독립 제3자 기술감사 보고서 (Blind Cross Audit Report)

> **문서 식별 번호**: `HERMES-BLIND-CROSS-AUDIT-20261112`  
> **감사관 역할**: Hermes 시스템 독립 제3자 기술감사관 (Independent Third-Party Technical Auditor)  
> **감사 기준**: Zero-Trust 원칙, 물리적 소스 코드/스크립트/스키마 직접 실행 및 재현 증거 기반 판정  
> **감사 대상**:
> 1. Hermes v1 운영 스크립트 (`audit_with_hermes.ps1`, `state_manager.ps1`)
> 2. 설정 파일 (`audit_config.json`, `audit_config.schema.json`)
> 3. 엔터프라이즈 헌법 및 프롬프트 (`constitution_4layer_specification.md`, `audit_prompt_spec.md`)
> 4. 상태 관리 원장 (`state_machine_ledger.json`)
> 5. Hermes v2 설계 문서 01~07 전수 (`docs/hermes_v2_spec/01_*` ~ `07_*`)
> 6. 실제 로컬 감사 결과 원본 (`docs/audit_runs/20261112_055925_dbe89f06/report.md` 등)

---

## 1. 감사 환경 및 모델 식별 정보

| 항목 | 물리적 실측치 및 구성 정보 | 비고 |
|---|---|---|
| **감사 환경 OS** | Windows 10 Pro (x64, 10.0.26200.9711) | PowerShell 5.1 데스크톱 환경 |
| **원격 감사 노드** | 자택 데스크톱 (NVIDIA GeForce RTX 5070 Ti 16GB, Tailscale P2P) | IP: `100.111.193.94:11434` |
| **운영 모델 식별자** | `qwen27b:latest` (Ollama v0.40.16) | Digest: `23c3820b0d93...`, Quant: `IQ3_M` |
| **실측 추론 속도** | `47.8 ~ 51.7 tokens/s` (입력 17,183토큰, 출력 1,597토큰, 소요시간 47.03초) | `report.md` 실측 메트릭 기반 |
| **컨텍스트 한도** | `num_ctx: 24576`, 사전 토큰 경보 한도: `22118` (90%) | `audit_with_hermes.ps1` 237행 |

---

## 2. 실제 조사한 물리적 파일 목록

1. `C:\Users\klad3\.gemini\scripts\audit_with_hermes.ps1` (v1.1.1, 433행)
2. `c:\dev\kintai\scripts\state_manager.ps1` (v3.0, 593행)
3. `c:\dev\kintai\scripts\audit_config.json` 및 `audit_config.schema.json`
4. `c:\dev\kintai\docs\constitution_4layer_specification.md` (v2.1)
5. `c:\dev\kintai\docs\audit_prompt_spec.md` (v1.0)
6. `c:\dev\kintai\docs\hermes_v2_spec\01_hermes_v1_survey_report.md`
7. `c:\dev\kintai\docs\hermes_v2_spec\02_judgment_constitution_v2_draft.md`
8. `c:\dev\kintai\docs\hermes_v2_spec\03_result_schema_v2_draft.json`
9. `c:\dev\kintai\docs\hermes_v2_spec\03_result_schema_v2_spec.md`
10. `c:\dev\kintai\docs\hermes_v2_spec\04_v1_v2_compatibility_migration.md`
11. `c:\dev\kintai\docs\hermes_v2_spec\05_golden_test_specifications.md`
12. `c:\dev\kintai\docs\hermes_v2_spec\06_deterministic_verifier_design.md`
13. `c:\dev\kintai\docs\hermes_v2_spec\07_unresolved_and_pending_verification.md`
14. `c:\dev\kintai\docs\audit_runs\20261112_055925_dbe89f06\report.md` (실제 Day 9 결과)

---

## 3. 독립적으로 발견한 결함 목록 (Confirmed Defects)

### DEF-01: JSON Schema 내 Enum 표기 불일치 (`NA` vs `N/A`)
- **실제 파일 및 행**: `c:\dev\kintai\docs\hermes_v2_spec\03_result_schema_v2_draft.json` 131행 vs 194행
- **증거**:
  - 131행 (`counts_by_verdict.required`): `["PASS", "FAIL", "WARN", "NA", "NOT_VERIFIABLE"]`
  - 194행 (`findings[].verdict.enum`): `["PASS", "FAIL", "WARN", "N/A", "NOT_VERIFIABLE"]`
- **재현 및 파급 효과**:
  - PowerShell 스크립트를 통해 스키마 객체 직접 대조 결과:
    `Is 'NA' in summary keys? True` / `Is 'N/A' in summary keys? False`
    `Is 'NA' in findings[].verdict? False` / `Is 'N/A' in findings[].verdict? True`
  - 모델이 finding 항목에 `verdict: "N/A"`를 출력하고 카운트 객체에 `{"N/A": 1}`을 기록하면, 스키마 유효성 검사기에서 필수 키 `"NA"` 누락으로 인해 스키마 검증이 무조건 거부(Reject)됨.
- **판정**: **`CONFIRMED_DEFECT`**

### DEF-02: 손상된 v2 JSON에 대한 Silent Fallback 보안 결함
- **실제 파일 및 행**: `c:\dev\kintai\docs\hermes_v2_spec\04_v1_v2_compatibility_migration.md` 38~47행
- **증거**:
  - v2 JSON 파일 검증 실패 시 v1 마크다운 파서로 넘어가는 흐름이 허용되어 있음.
  - 모델이 스키마를 위반하거나 손상된 JSON을 생성했을 때, 이를 레거시 v1 마크다운 정규식 매칭으로 폴백시키면, 모델의 JSON 생성 결함이나 변조된 데이터가 은폐된 채 v1 정규식 검사를 통과하여 `AUDIT_PASSED`로 부정 승인될 수 있음.
- **판정**: **`CONFIRMED_DEFECT`**

### DEF-03: 상대경로 기본값으로 인한 실행 위치 의존적 경로 고아 발생
- **실제 파일 및 행**: `C:\Users\klad3\.gemini\scripts\audit_with_hermes.ps1` 47행
- **증거**:
  - `$OutputDir = Join-Path "docs/audit_runs" "${dirPrefix}${timestampStr}_${targetSha8}"`로 상대경로 바인딩됨.
  - 사용자가 `C:\Users\klad3`에서 실행할 경우 `C:\Users\klad3\docs\audit_runs\`에 저장되고, 프로젝트 루트(`c:\dev\kintai`)에서 실행할 경우 `c:\dev\kintai\docs\audit_runs\`에 저장됨.
  - 실제로 디스크 조사 결과 `C:\Users\klad3\docs\audit_runs\20261112_054459_PROMPT`가 고아 디렉터리로 잔류하고 있음.
- **판정**: **`CONFIRMED_DEFECT`**

### DEF-04: JSON Schema Strictness 결핍 (`additionalProperties: false` 누락)
- **실제 파일 및 행**: `c:\dev\kintai\docs\hermes_v2_spec\03_result_schema_v2_draft.json` 최상위 및 155행
- **증거**:
  - 최상위 객체 및 `findings[]` 객체에 `additionalProperties` 정책이 미정의됨 (`null`).
  - LLM이 임의의 환각 필드(예: `"hallucinated_comment": "..."`)를 추가하더라도 스키마가 차단하지 못함.
- **판정**: **`CONFIRMED_DEFECT`**

---

## 4. 설계상 개선 권고 사항 (Design Improvements)

### IMP-01: `overall_verdict`의 `N/A` 상태 수용
- **파일 및 행**: `03_result_schema_v2_draft.json` 123행
- **내용**: `overall_verdict`의 Enum이 `["PASS", "FAIL", "WARN", "NOT_VERIFIABLE"]`로 제한되어 있음. 단순 정적 문서나 DDL 등 비즈니스 로직 검증 대상이 없어 모든 항목이 `N/A`일 때, 전체 결과를 무엇으로 둘지 모호함. `overall_verdict`에도 `"N/A"`를 명시적으로 허용하거나, "전원 N/A 시 PASS 간주" 정책을 헌법에 명시해야 함.
- **판정**: **`DESIGN_IMPROVEMENT`**

### IMP-02: FAIL 판정 요건의 3분류 증거 대등 인정
- **파일 및 행**: `02_judgment_constitution_v2_draft.md` 제3조 제6항
- **내용**: FAIL의 필수 요건으로 "단위 테스트 코드, 반례, 에러 스택"을 강제하고 있음. 소스 코드 상에서 명백한 하드코딩 패스워드, 미암호화 토큰 노출, SQL Injection 등 정적 코드만으로 증명 가능한 결함이 단위 테스트 부재를 이유로 `WARN`으로 격하되는 역효과를 방지하기 위해, **정적 코드 분석 증명(Static Code Proof)**을 대등한 증거로 공식 인정해야 함.
- **판정**: **`DESIGN_IMPROVEMENT`**

### IMP-03: Finding 추적 상태의 상태 머신 권한 분리
- **파일 및 행**: `03_result_schema_v2_draft.json` 197행
- **내용**: `tracking_status`에 `FIXED`, `VERIFIED_FIXED`가 정의되어 있으나, LLM에게 이 상태를 결정할 권한을 주면 셀프 승인 환각이 발생함. LLM은 결함 식별(`OPEN`, `REOPENED`)만 수행하고, `FIXED` 및 `VERIFIED_FIXED`는 외부 JUnit 테스트 결과와 `state_manager.ps1`에 의해서만 부여되도록 통제해야 함.
- **판정**: **`DESIGN_IMPROVEMENT`**

---

## 5. 기존 v2 설계에서 정상 작동이 확인된 강점

1. **Severity와 Verdict의 완전 분리 (제2조)**:
   - 심각도(`P0~INFO`)와 검증 결과(`PASS/FAIL/WARN/N/A/NOT_VERIFIABLE`)를 직교하도록 설계하여, 모델이 사소한 제안에 P0를 남발하거나 심각한 보안 이슈를 단순 스타일로 취급하는 문제를 원천 방어함.
2. **`NOT_VERIFIABLE`의 공식화 (제1조, 제4조)**:
   - Day 9 실제 감사 로그(`report.md` F-01, F-09)에서 확인된 바와 같이, 피호출 클래스가 번들에서 누락되었을 때 모델이 막연한 추측으로 P0 FAIL을 때리던 고질적 오판을 차단하는 핵심 장치로 유효함.
3. **독립 결정론적 검증기 아키텍처 (`06_deterministic_verifier_design.md`)**:
   - LLM 출력을 그대로 믿지 않고, 외부 파워셸 규칙 엔진이 Schema 적합성, 고유 ID 중복, 통계 정합성을 100% 룰 기반으로 검증하는 게이트키퍼 철학은 엔터프라이즈급 안정성을 보장함.

---

## 6. 놓치기 쉬운 핵심 회귀 위험 (Regression Risks)

### REG-01: 토큰 예측 글자수 / 4.0 완화 제안의 Silent Truncation 위험
- **위험 분석**:
  - `07_unresolved_and_pending_verification.md` ISSUE-02에서 자바 코드 글자수/토큰 비율을 `/4.0`으로 완화하자고 제안함.
  - 그러나 Qwen 토크나이저(BPE)는 자바의 CamelCase 변수명, 중괄호, 공백에 대해 토큰을 매우 촘촘하게 쪼갬(실제 1.8~2.5글자당 1토큰).
  - 글자수 / 4.0으로 변경하면 실제 토큰 수가 24,576을 초과함에도 불구하고 스크립트의 사전 검사(90% 한도)를 통과하여, Ollama 백엔드에서 **코드 뒷부분이 조용히 잘려나간 채 감사되는 치명적 침묵 절삭(Silent Truncation)**이 발생함.
- **판정**: **`CONFIRMED_DEFECT`** (완화 제안 즉각 기각, 현행 `/3.0` 절대 유지)

### REG-02: 검증기 에러와 비즈니스 코드 결함의 상태 혼선
- `verify_audit_result.ps1`이 JSON 파싱 실패(Exit 10)나 통계 불일치(Exit 13)로 종료되었을 때, 이를 소스 코드 결함(`AUDIT_FAILED`)으로 처리하여 개발자가 엉뚱한 자바 코드를 고치는 혼선이 발생할 수 있음. 파이프라인 에러(`AUDIT_INCOMPLETE`)로 명확히 분리해야 함.

---

## 7. 미검증 항목 목록 (Not Verifiable)

| 항목 ID | 내용 | 현재 상태 | 미검증 사유 |
|---|---|:---:|---|
| **UNVER-01** | **10대 골든 테스트 명세의 실제 런타임 구동** | `NOT_VERIFIABLE` | 명세서 문서(`05_golden_test_specifications.md`)만 존재하며, 실제 입력 번들 픽스처 및 기대 JSON 단언문 파일이 물리적으로 부재함. |
| **UNVER-02** | **Qwen 27B의 순수 JSON 출력 준수율** | `NOT_VERIFIABLE` | 프롬프트에 JSON 출력을 지시했을 때 모델이 백틱(````json````)이나 서두/결말 자연어를 붙이지 않고 순수 JSON만 출력하는지 실측 통계가 없음. |
| **UNVER-03** | **NumCtx: 32768 상향 시 VRAM 및 속도 영향** | `NOT_VERIFIABLE` | 자택 RTX 5070 Ti 16GB VRAM에서 32K 컨텍스트 활성화 시 KV 캐시 증가로 인한 OOM 발생 여부가 실측되지 않음. |

---

## 8. 최종 감사 판정 및 결론

### [최종 판정]: **`REJECT_PENDING_FIXES` (수정 완료 전 운영 적용 불허)**

- **감사 결론**:
  Antigravity가 제시한 Hermes v2 개정안은 문제의 본질(Severity 분리, NOT_VERIFIABLE 도입, 결정론적 검증기)을 정확하게 짚은 우수한 아키텍처이나, **JSON Schema 내 Enum 불일치(DEF-01)**, **손상된 JSON의 Silent Fallback(DEF-02)**, **실측 없는 토큰 예측 완화에 따른 Silent Truncation 위험(REG-01)** 등 실서비스에서 런타임 장애를 일으킬 치명적 결함들이 물리적으로 확인되었습니다.

- **승인 전 필수 선결 과제**:
  1. `03_result_schema_v2_draft.json`의 `"NA"` 키를 `"N/A"`로 통일하고 `additionalProperties: false` 선언.
  2. `04_v1_v2_compatibility_migration.md`에서 손상된 JSON 발생 시 즉시 파이프라인을 중단하는 Hard Stop 정책으로 수정.
  3. 토큰 예측 공식을 현행(글자수 / 3.0, 24576)으로 엄격히 동결.
  4. 10대 골든 테스트의 실제 물리적 테스트 픽스처 파일 생성 및 통과 증적 확보.

상기 과제가 완결되기 전까지 **Hermes v2의 운영 스크립트 교체 및 프로덕션 배포를 전면 금지**합니다.
