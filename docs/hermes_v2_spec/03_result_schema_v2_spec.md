# Hermes 결과 스키마 v2 기술 규격서 (Result Schema v2 Specification - 확정본)

## 1. 개요
본 규격서는 Hermes 로컬 AI 감사 파이프라인의 감사 결과물을 결정론적으로 파싱하고 상태 머신(`state_manager.ps1`)과 무손실로 연동하기 위한 **HermesAuditResultV2 JSON Schema**의 확정된 데이터 사전 및 필드 정책을 정의한다.

`findings[].evidence.proof_type`은 FAIL에서 필수인 판별 규칙이며, 실행 테스트·정적 코드 증명·수학적 반례의 세 증명 방식을 구분한다. 세부 필수 필드는 `03_result_schema_v2_draft.json`과 결정론적 검증기의 proof-type 규칙을 따른다.

---

## 2. 핵심 설계 변경 및 무결성 확정 사항
1. **엄격한 스키마 폐쇄성 (`additionalProperties: false`)**:
   - 최상위 객체 및 하위 중첩 객체(메타데이터, 번들 정보, 통계, findings 배열) 전반에 걸쳐 임의의 속성 추가를 100% 차단하여 LLM의 환각 속성 누출을 원천 봉쇄함.
2. **`counts_by_verdict`와 `findings[].verdict`의 완전한 일치 (`"N/A"`)**:
   - v2 초안의 `"NA"` vs `"N/A"` 불일치(DEF-01)를 해결하여, 양측 모두 슬래시가 포함된 표준 규격인 **`"N/A"`**로 완전 일원화함.
3. **`overall_verdict`의 `"N/A"` 상태 공식 지원**:
   - 단순 정적 문서나 DDL 등 비즈니스 로직 검증 대상이 없어 모든 항목이 `N/A`인 경우에도 유효한 전체 상태를 표현할 수 있도록 Enum에 `"N/A"`를 정식 포함함 (`PASS`, `FAIL`, `WARN`, `N/A`, `NOT_VERIFIABLE`).
4. **필수 필드 문자열 최소 길이 보장 (`minLength: 1`)**:
   - 빈 문자열(`""`)로 필수 증거나 분석을 우회하지 못하도록 엄격한 길이 제약을 추가함.

---

## 3. 세부 필드 데이터 사전

### 3.1. 감사 메타데이터 (`audit_metadata`)
- `run_id` (String): 실행 고유 식별자 (`YYYYMMDD_HHMMSS_<bundle_hash_prefix>`).
- `timestamp_iso` (ISO 8601 String): 감사 완료 일시 (`2026-11-12T15:30:00+09:00`).
- `model_name` (String): 실행에 사용된 모델 식별자 (예: `qwen27b:latest`).
- `model_digest` (String): Ollama SHA-256 이미지 다이제스트.
- `execution_metrics` (Object):
  - `duration_seconds`: 소요 시간 (초).
  - `prompt_eval_count`: 입력 프롬프트 토큰 수.
  - `eval_count`: 생성된 출력 토큰 수.
  - `tokens_per_second`: 초당 생성 속도.

### 3.2. 번들 및 범위 정보 (`bundle_info`)
- `bundle_path` (String): 감사에 사용된 번들 파일 절대/상대 경로.
- `bundle_sha256` (String): 64자리 SHA-256 체크섬.
- `target_files` (Array[String]): 번들에 포함된 실제 소스 파일 목록.
- `missing_dependencies` (Array[String]): 번들에 없어 검증이 제한된 의존 클래스 목록.

### 3.3. 요약 통계 (`verdict_summary`)
- `overall_verdict` (Enum): `PASS`, `FAIL`, `WARN`, `N/A`, `NOT_VERIFIABLE`.
  - 단 1개라도 `FAIL`이 있으면 `FAIL`.
  - `FAIL`이 없고 `WARN`이 있으면 `WARN`.
  - 전수 `PASS`이거나 허용된 `N/A`만 있는 경우 `PASS`.
  - 전 항목이 `N/A`인 경우 `N/A`.
  - 핵심 의존성 결핍으로 판단 불능인 경우 `NOT_VERIFIABLE`.
- `total_findings` (Integer): findings 배열의 총 개수.
- `counts_by_verdict` (Object): `PASS`, `FAIL`, `WARN`, `N/A`, `NOT_VERIFIABLE` 5대 키 필수.
- `counts_by_severity` (Object): `P0`, `P1`, `P2`, `P3`, `INFO` 5대 키 필수.

### 3.4. 개별 결함 상세 (`findings[]`)
- `finding_id`: `^F-[0-9]{3}$` 정규식 규격 고유 번호 (예: `F-001`).
- `category`:
  - `CONCURRENCY_AND_LOCKING` (동시성/비관적 락)
  - `TRANSACTION_MANAGEMENT` (트랜잭션/전파/롤백)
  - `SECURITY_AND_IDOR` (권한 검증/인가/세션)
  - `LABOR_STANDARDS_ACT_37` (노동법 37조/가산수당/휴게)
  - `DATA_INTEGRITY_AND_VALIDATION` (데이터 무결성/경계값)
  - `RESOURCE_AND_PERFORMANCE` (스트림/워크북 해제/N+1)
  - `CODE_STYLE_AND_DOCS` (코드 스타일/주석/기술문서)
- `severity`: `P0`, `P1`, `P2`, `P3`, `INFO`.
- `verdict`: `PASS`, `FAIL`, `WARN`, `N/A`, `NOT_VERIFIABLE`.
- `tracking_status`: `OPEN`, `IN_PROGRESS`, `FIXED`, `VERIFIED_FIXED`, `REOPENED`, `ACCEPTED_RISK`.
- `location`: 파일 경로, 클래스, 메서드, 라인 시작/끝.
- `evidence`: 물리적 증거 객체 (`code_snippet_or_proof`, `expected_behavior`, `actual_behavior`, `reproduction_test_case`, `counterexample_data`).
- `analysis`: 위험도 평가(`risk_evaluation`), 관련 법률 조항(`applicable_laws`), 개선 권고안(`recommendation`).
