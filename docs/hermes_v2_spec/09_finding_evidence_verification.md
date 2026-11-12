# Hermes v2 발견 결함의 물리적 증거 확정 및 설계 정정 준비 보고서

> **문서 번호**: `EVIDENCE-HERMES-V2-20261112-002`  
> **검증 일시**: 2026-11-12  
> **검증 대상**: `docs/hermes_v2_spec/08_independent_design_audit.md`의 발견 사항 및 실행 장애 원인  
> **검증 원칙**: 기존 소스 코드, 스크립트, 감사 원본 무수정, 물리적 실행 증거 기반 판정 확정

---

## 1. 개요 및 검증 체계
본 문서는 `08_independent_design_audit.md`에서 식별된 발견 항목(C-01 ~ C-03, R1 ~ R8)과 이전 턴에서 발생한 파워셸 종료 코드 1(Exit Code 1) 장애 3건의 실체적 진실을 규명하고, 각 항목의 최종 상태를 `CONFIRMED_DEFECT`, `DESIGN_IMPROVEMENT`, `FALSE_POSITIVE`, `NOT_VERIFIABLE` 4단계로 확정한 보고서이다.

---

## 2. 직전 종료 코드 1 장애 3건의 기술적 원인 전수 규명

### 2.1. 인라인 파워셸 스크립트 장애 2건 (`inspect JSON schema enums`, `parse JSON schema properly`)
- **실행 명령**: `powershell -ExecutionPolicy Bypass -Command "[Console]... $json = Get-Content ..."`
- **실행 위치**: `c:\dev\kintai`
- **표준 오류**:
  ```text
  = : The term '=' is not recognized as the name of a cmdlet, function, script file, or operable program.
  At line:2 char:2
  +  = Get-Content ...
  ```
- **종료 코드**: `1`
- **실패 원인 (Root Cause)**:
  - **도구 호출 계층의 환경 변수 치환 오류 (Tool Harness Parsing Artifact)**.
  - 파워셸 명령어 인라인 스트링 내부의 변수 선언(`$json =`, `$content =`)에서 변수명이 호출 셸/하네스 단계에서 빈 문자열로 확장되어 파워셸 엔진에 전달될 때 `= Get-Content ...` 형태의 문법 오류(Syntax Error)로 전달됨.
- **JSON 파일 자체의 유효성**:
  - `docs/hermes_v2_spec/03_result_schema_v2_draft.json` 자체는 완벽한 정규 JSON 문법(`Valid JSON`)을 만족함 (`[System.IO.File]::ReadAllText` 및 `ConvertFrom-Json` 검증 성공).
- **최종 판정**: **`TOOL_HARNESS_SYNTAX_ISSUE` (JSON 파일 결함 아님, 스크립트 파일 분리 호출 시 정상 작동 확인)**

### 2.2. 검증기 프로토타입 실행 장애 1건 (`test verifier prototype with draft schema`)
- **실행 명령**: `powershell -ExecutionPolicy Bypass -File "c:\dev\kintai\docs\hermes_v2_spec\verify_audit_result_prototype.ps1" ...`
- **실행 위치**: `c:\dev\kintai`
- **표준 오류**:
  ```text
  The argument 'c:\dev\kintai\docs\hermes_v2_spec\verify_audit_result_prototype.ps1' to the -File parameter does not exist.
  ```
- **종료 코드**: `1`
- **실패 원인 (Root Cause)**:
  - **물리적 파일 부재 (File Not Found)**.
  - 해당 스크립트는 `06_deterministic_verifier_design.md` 문서 내부에 마크다운 코드 블록으로만 설계되어 있었으며, 실제 물리적 디스크 상에 `.ps1` 파일로 생성되지 않은 상태에서 호출을 시도하여 발생함.
- **최종 판정**: **`NON_EXISTENT_FILE_INVOCATION` (설계 문서와 런타임 파일 간의 차이 확인)**

---

## 3. C-01, C-02, C-03 발견 결함 물리적 증거 확정

### 3.1. [C-01] `NA` vs `N/A` Enum 불일치
- **원래 주장**: `verdict_summary.counts_by_verdict`의 키(`"NA"`)와 `findings[].verdict`의 Enum(`"N/A"`)이 불일치함.
- **물리적 증거**:
  - `03_result_schema_v2_draft.json` 131행: `"required": ["PASS", "FAIL", "WARN", "NA", "NOT_VERIFIABLE"]`
  - `03_result_schema_v2_draft.json` 194행: `"enum": ["PASS", "FAIL", "WARN", "N/A", "NOT_VERIFIABLE"]`
  - 스크립트 실측 대조 결과:
    - `Is 'NA' in summary keys?`: **`True`** / `Is 'N/A' in summary keys?`: **`False`**
    - `Is 'NA' in findings[].verdict?`: **`False`** / `Is 'N/A' in findings[].verdict?`: **`True`**
- **결함 성격 분석**:
  - JSON Schema Draft-07 규격 상 객체의 속성 이름으로 슬래시(`/`)가 포함된 `"N/A"`는 유효한 문자열 키임.
  - 모델이 `verdict: "N/A"`로 결함을 출력하고 카운트 객체에 `{"N/A": 1}`을 기록하면, 스키마 검증기에서 필수 속성 `"NA"`가 누락된 것으로 판정하여 `SchemaValidationError`를 발생시킴.
- **최종 판정**: **`CONFIRMED_DEFECT`** (구조적 스키마 결함 확정)

### 3.2. [C-02] `verdict_summary.overall_verdict`의 `N/A` 누락
- **원래 주장**: 전체 결과에 `N/A`가 누락되어 비즈니스 로직이 없는 번들 평가 시 스키마 위반 발생 위험.
- **물리적 증거**:
  - `03_result_schema_v2_draft.json` 123행: `"enum": ["PASS", "FAIL", "WARN", "NOT_VERIFIABLE"]`
- **정책 분석**:
  - 소스 코드가 순수 DTO이거나 정적 마크다운이어서 모든 감사 항목이 `N/A`로 판정된 경우, 시스템은 이를 실패로 볼 것인가 통과로 볼 것인가?
  - 엔터프라이즈 감사 관점에서 "모든 항목이 N/A인 경우"는 차단 결함이 없으므로 `PASS`로 갈음할 수 있으나, 독립적인 `N/A`를 부여하지 못하면 감사관이 억지로 `NOT_VERIFIABLE`이나 `PASS`를 선택해야 하는 환각 유발.
- **최종 판정**: **`DESIGN_IMPROVEMENT`** (스키마 문법 오류는 아니나, 감사 도메인 표현력 보완 필요)

### 3.3. [C-03] 감사 런 원본 경로(User Home vs Project Root) 조사 오류
- **원래 주장**: `20261112_054459_PROMPT`가 프로젝트가 아닌 사용자 홈 디렉터리에 존재함.
- **물리적 증거**:
  - `C:\Users\klad3\.gemini\scripts\audit_with_hermes.ps1` 47행:
    ```powershell
    if (-not $OutputDir) {
        $OutputDir = Join-Path "docs/audit_runs" "${dirPrefix}${timestampStr}_${targetSha8}"
    }
    ```
  - **원인 규명**: `audit_with_hermes.ps1`의 상대경로 기본값이 현재 콘솔의 작업 디렉터리(`$PWD`)를 기준으로 `docs/audit_runs`를 생성함.
  - 사용자가 `C:\Users\klad3`에서 명령을 실행하면 `C:\Users\klad3\docs\audit_runs\`에 저장되고, `c:\dev\kintai`에서 실행하면 `c:\dev\kintai\docs\audit_runs\`에 저장됨.
- **최종 판정**: **`CONFIRMED_DEFECT`** (실행 경로 의존적 경로 고아 발생 결함 확인)

---

## 4. 8대 핵심 위험(R1 ~ R8) 재검토 및 최종 판정

| 위험 ID | 핵심 쟁점 및 원래 감사 의견 | 재검토 증거 및 분석 결과 | 최종 판정 |
|---|---|---|:---:|
| **R1** | **시스템 조사 범위 과장**<br>(일부 파일 확인을 전수 조사로 표현) | 물리적 파일 4종 구조는 확인되었으나 원장 리플레이 및 22개 로그 전수 검증 미수행 확인 | **`CONFIRMED_DEFECT`**<br>(보고서 상태 정정 필요) |
| **R2** | **토큰 예측 공식(/3.0 ➔ /4.0) 완화 및 NumCtx 32768 상향** | Qwen BPE 토크나이저 상 자바 코드는 토큰 소모가 큼. 실측 데이터 없는 완화는 Silent Truncation 유발 위험 | **`CONFIRMED_DEFECT`**<br>(완화 제안 철회 및 현행 동결) |
| **R3** | **v1-v2 Silent Fallback 위험**<br>(JSON 실패 시 MD 파서로 자동 대체) | 손상된 JSON이나 필수 필드 누락 시 v1 정규식으로 우회 승인될 수 있는 보안 구멍 확인 | **`CONFIRMED_DEFECT`**<br>(손상 시 Hard Stop 정책 필수) |
| **R4** | **FAIL 판정 시 단위 테스트 필수화의 과도한 제한** | 명백한 정적 결함(SQLi 등)에 대해 테스트 부재로 WARN 격하되는 위험 확인. 정적 증명 대등 인정 필요 | **`DESIGN_IMPROVEMENT`**<br>(헌법 제3조 증거 요건 보완) |
| **R5** | **10대 골든 테스트 명세의 실현 가능성** | 명세서 텍스트만 존재하고 실행 가능한 물리적 픽스처(코드/단언문) 부재 확인 | **`NOT_VERIFIABLE`**<br>(테스트 픽스처 구축 전까지) |
| **R6** | **Finding 추적 상태의 상태 전이 주체 모호성** | LLM에게 `VERIFIED_FIXED` 권한 부여 시 셀프 승인 위험. 외부 검증기 통제 필수 | **`DESIGN_IMPROVEMENT`**<br>(상태 전이 권한 분리 명문화) |
| **R7** | **JSON Schema additionalProperties 정책 부재** | 스키마에 `additionalProperties: false` 미선언으로 임의 환각 속성 차단 불가 확인 | **`CONFIRMED_DEFECT`**<br>(스키마 strict 정책 누락) |
| **R8** | **모델 메타데이터 재현성 확보** | `model_name` 외 `model_digest`(Ollama 이미지 SHA-256) 강제 구조의 정당성 확인 | **`DESIGN_IMPROVEMENT`**<br>(API 연동 검증 필요) |

---

## 5. 설계 정정(Design Correction) 준비 목록 (운영 미적용, 문서화용)

향후 v2 설계 정정 시 반영할 세부 수정 사양:

1. **`03_result_schema_v2_draft.json` 스키마 정정 사양**:
   - `verdict_summary.counts_by_verdict.required`의 `"NA"`를 **`"N/A"`**로 변경.
   - `verdict_summary.counts_by_verdict.properties` 내 `"NA"` 키를 **`"N/A"`**로 변경.
   - `overall_verdict.enum`에 **`"N/A"`** 추가 (`["PASS", "FAIL", "WARN", "N/A", "NOT_VERIFIABLE"]`).
   - 최상위 객체 및 finding 객체에 **`"additionalProperties": false`** 명시.
   - `code_snippet_or_proof`에 **`"minLength": 1`** 선언.
2. **`04_v1_v2_compatibility_migration.md` 폴백 정책 정정 사양**:
   - "JSON 파싱 실패 시 MD 파서로 대체" 로직 삭제.
   - **`[Hard Gate Rule]`**: 디렉터리에 `report.json`이 존재하는데 파싱이나 검증에 실패한 경우, 즉시 `AUDIT_FAILED (Exit Code 10~13)`를 반환하고 v1 파서 호출을 전면 차단.
   - 오직 `report.json` 파일이 전혀 존재하지 않는 과거 레거시 런에 한해서만 v1 파서 허용.
3. **`02_judgment_constitution_v2_draft.md` FAIL 요건 정정 사양**:
   - 제3조 제6항을 수정하여 **"실패 단위 테스트, 컴파일 에러 스택, 수학적 반례, 또는 명백한 정적 코드 결함 증명(Static Code Proof)"** 중 하나 이상을 제시하도록 3분류 체계 확립.
4. **`07_unresolved_and_pending_verification.md` 토큰 정책 정정 사양**:
   - ISSUE-02의 글자수/4.0 완화 제안 및 NumCtx 32768 상향 권고를 **"보수적 현행 유지(글자수 / 3.0, NumCtx: 24576)"**로 정정.
