# HERMES V2 INDEPENDENT BLIND CROSS AUDIT BUNDLE

## AUDIT OBJECTIVE
You are the independent Lead Technical Auditor for the Hermes Local AI Audit System.
Your task is to conduct an aggressive, independent, objective technical cross-audit of the newly proposed Hermes v2 specifications (Files 01 through 07) and the existing Hermes v1 pipeline.
Do not flatter or assume correctness. Identify all syntax errors, schema discrepancies, silent fallback vulnerabilities, token risks, and regression issues.

---

### [SECTION 1: EXISTING HERMES V1 PIPELINE CONFIGURATION]
#### scripts/audit_config.json
```json
{
  "primary_auditor": {
    "network_type": "Tailscale Overlay Network",
    "endpoint": "http://100.111.193.94:11434",
    "model_tag": "qwen27b:latest",
    "model_digest": "23c3820b0d9326b73e97191b9fb7e16b53f6e24bb3449d2a17037c5f2b6179a1",
    "think": false,
    "options": {
      "temperature": 0.1,
      "num_ctx": 24576,
      "seed": 42
    },
    "verdict_authority": "AUDIT_PASSED"
  }
}
```

---

### [SECTION 2: HERMES V2 SPECIFICATIONS (01 ~ 07)]

#### FILE 01: docs/hermes_v2_spec/01_hermes_v1_survey_report.md
```markdown
# Hermes 로컬 AI 감사 파이프라인 v1 아키텍처 및 실행 흐름 조사 보고서
- Token calculation: ($systemPrompt.Length + $userPromptText.Length) / 3.0
- Pre-check threshold: 90% of num_ctx (22,118 tokens)
- State machine regex parsing: PASS/VERIFIED vs FAIL/WARN
```

#### FILE 02: docs/hermes_v2_spec/02_judgment_constitution_v2_draft.md
```markdown
# Hermes 판정 헌법 v2 개정 초안
## 제2조 (Severity와 Verdict의 완전 분리)
- Severity Enum: P0, P1, P2, P3, INFO
- Verdict Enum: PASS, FAIL, WARN, N/A, NOT_VERIFIABLE

## 제3조 (FAIL 판정 7대 필수 성립 요건)
1. 실제 코드 존재성
2. 실행 경로 특정
3. 정확한 위치 좌표
4. 예상 정상 동작(Expected) 명시
5. 실제 결함 동작(Actual) 증명
6. 물리적 증거 또는 반례 제시 (실패하는 단위 테스트 코드, 수학적 반례 입력값, 또는 컴파일/런타임 에러 스택)
7. 대안적 구현 여부 확인

## 제5조 (일본 노동기준법 제37조 및 근태 계산 판정 가이드라인)
- 심야(22:00~05:00) + 연장(8h 초과) 중복 가산: 25% + 25% = 50%
- 총 체류시간(525분) vs 실노동시간(480분, 휴게 45분 차감 시 연장 0분)
```

#### FILE 03: docs/hermes_v2_spec/03_result_schema_v2_draft.json
```json
{
  "$schema": "http://json-schema.org/draft-07/schema#",
  "title": "HermesAuditResultV2",
  "type": "object",
  "required": [
    "schema_version",
    "audit_metadata",
    "bundle_info",
    "verdict_summary",
    "findings"
  ],
  "properties": {
    "schema_version": { "type": "string", "enum": ["2.0.0"] },
    "verdict_summary": {
      "type": "object",
      "required": ["overall_verdict", "total_findings", "counts_by_verdict", "counts_by_severity"],
      "properties": {
        "overall_verdict": {
          "type": "string",
          "enum": ["PASS", "FAIL", "WARN", "NOT_VERIFIABLE"]
        },
        "total_findings": { "type": "integer", "minimum": 0 },
        "counts_by_verdict": {
          "type": "object",
          "required": ["PASS", "FAIL", "WARN", "NA", "NOT_VERIFIABLE"],
          "properties": {
            "PASS": { "type": "integer", "minimum": 0 },
            "FAIL": { "type": "integer", "minimum": 0 },
            "WARN": { "type": "integer", "minimum": 0 },
            "NA": { "type": "integer", "minimum": 0 },
            "NOT_VERIFIABLE": { "type": "integer", "minimum": 0 }
          }
        }
      }
    },
    "findings": {
      "type": "array",
      "items": {
        "type": "object",
        "required": ["finding_id", "category", "title", "severity", "verdict", "tracking_status", "location", "evidence", "analysis"],
        "properties": {
          "finding_id": { "type": "string", "pattern": "^F-[0-9]{3}$" },
          "severity": { "type": "string", "enum": ["P0", "P1", "P2", "P3", "INFO"] },
          "verdict": { "type": "string", "enum": ["PASS", "FAIL", "WARN", "N/A", "NOT_VERIFIABLE"] },
          "tracking_status": { "type": "string", "enum": ["OPEN", "IN_PROGRESS", "FIXED", "VERIFIED_FIXED", "REOPENED", "ACCEPTED_RISK"] },
          "evidence": {
            "type": "object",
            "required": ["code_snippet_or_proof", "expected_behavior", "actual_behavior"]
          }
        }
      }
    }
  }
}
```

#### FILE 04: docs/hermes_v2_spec/04_v1_v2_compatibility_migration.md
```markdown
# 4. state_manager.ps1 하위 호환성 및 이중 파싱 전략
- Fallback logic: If report.json verification fails or is invalid, falls back to legacy v1 report.md regex parser.
```

#### FILE 06: docs/hermes_v2_spec/06_deterministic_verifier_design.md
```markdown
# verify_audit_result.ps1
- Exit 10: JSON Syntax Error
- Exit 11: Duplicate Finding ID
- Exit 12: Missing FAIL evidence
- Exit 13: Summary count mismatch
```

#### FILE 07: docs/hermes_v2_spec/07_unresolved_and_pending_verification.md
```markdown
- ISSUE-02: Proposal to change character-to-token formula from / 3.0 to / 4.0 and increase NumCtx to 32768.
```

---

## REQUIRED AUDIT OUTPUT FORMAT
Audit the files above independently. Produce a comprehensive markdown report containing:
1. Executive Summary & Audit Verdict (ACCEPT, REJECT, or CONDITIONAL_ACCEPT)
2. Detailed Findings table:
   | Finding ID | Target Item / Spec Location | Severity (P0/P1/P2/Info) | Verdict (PASS / WARN / FAIL / N/A) | Code & Evidence | Detailed Analysis & Recommendation |
3. Critical checks to explicitly address:
   - Check A: Does `counts_by_verdict` key `NA` match `findings[].verdict` enum `N/A`? What is the impact?
   - Check B: Does `overall_verdict` support `N/A` when all findings are N/A?
   - Check C: Is the fallback to v1 markdown parser when v2 JSON fails a security or silent corruption risk?
   - Check D: Is changing token ratio to / 4.0 safe, or does it risk silent context truncation in Ollama?
   - Check E: Does requiring unit tests for FAIL prevent flagging obvious static code defects like hardcoded credentials?
   - Check F: Are additionalProperties restricted or can hallucinated fields leak through?
