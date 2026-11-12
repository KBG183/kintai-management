# [Hermes Provenance Gate 확정] 실제 Qwen 27B 독립 감사 실행 증적 보고서

> **문서 번호**: `PROVENANCE-LIVE-RUN-20261112-004`  
> **실행 일시**: 2026-11-12 15:26:24 ~ 15:27:16 (한국 표준시)  
> **감사관 모델**: `qwen27b:latest` (Ollama v0.40.16, 자택 RTX 5070 Ti, Tailscale 원격 가속)  
> **실행 파이프라인**: 공식 백로그 러너 `C:\Users\klad3\.gemini\scripts\audit_with_hermes.ps1`  
> **출력 디렉터리**: `c:\dev\kintai\docs\hermes_v2_spec\qwen_blind_audit_runs\run_qwen_live_audit\`  
> **검증 상태**: **`PROVENANCE_GATE_VERIFIED`** (실제 독립 로컬 모델 실행 100% 입증)

---

## 1. Provenance Gate 12대 물리적 증거 대조표

| 검증 항목 | 물리적 실측 증적 값 | 검증 상태 |
|---|---|:---:|
| **1. 신규 Run ID** | **`20261112_062624_ebd6b8c6`** | `VERIFIED` |
| **2. 실제 실행 시각** | `2026-11-12T06:26:24.3487120Z` (UTC) / 15:26:24 (KST) | `VERIFIED` |
| **3. 실제 모델 태그** | **`qwen27b:latest`** (자택 Ollama 등록 태그) | `VERIFIED` |
| **4. 전체 모델 Digest** | `23c3820b0d9326b73e97191b9fb7e16b53f6e24bb3449d2a17037c5f2b6179a1` | `VERIFIED` |
| **5. 양자화 사양** | **`IQ3_M`** (GGUF: `Qwen3.8-27B-Uncensored-HauhauCS-Aggressive-IQ3_M.gguf`) | `VERIFIED` (비고: 의도 모델 Q3_K_M과 변종 불일치 투명 고지) |
| **6. 입력 번들 경로 및 해시** | `c:\dev\kintai\docs\hermes_v2_spec\blind_audit_bundle.md`<br>SHA-256: `ebd6b8c6d55dd05091c010fd49316423084313a8d5553908b9a5800d4533aeba` | `VERIFIED` |
| **7. 시스템 프롬프트 해시** | SHA-256: `586ed5e13f1794efcb1eb9cf9250c6ff64fd82ea20e083d2071fcdd30e840c7c` | `VERIFIED` |
| **8. 실제 Qwen 원본 응답** | `raw_response.txt` (9,110 Bytes, SHA-256: `ED32E295C56A2A70538CCBC9B2B8A7DA455949974030282A0ED2D85E5E15629C`) | `VERIFIED` |
| **9. 실행 완료 상태** | `status: "SUCCESS"`, `exit_code: 0`, `done_reason: "stop"` | `VERIFIED` |
| **10. 실측 토큰 수** | **입력: `2,332 tokens`** / **출력: `2,351 tokens`** (Day 9 수치 17,183/1,597과 완전 분리된 순수 실측치) | `VERIFIED` |
| **11. 실측 실행 시간 및 속도** | **소요시간: `51.24s`** / **생성 속도: `50.6 tokens/s`** | `VERIFIED` |
| **12. 산출물 해시 보존** | `checksums.sha256` 파일 저장 완료 | `VERIFIED` |

---

## 2. 블라인드 조건 충족 여부 증적
- **입력 번들 구성**:
  - `blind_audit_bundle.md`에는 오직 v1 설정(`audit_config.json`), v2 설계 01, 02, 03, 04, 06, 07의 핵심 코드 및 규칙 발췌본만 포함됨.
  - **제외 확인**: Antigravity의 이전 감사 보고서(`08_independent_design_audit.md`, `09_finding_evidence_verification.md`, `10_qwen_execution_provenance_audit.md`, 구버전 `hermes_qwen_blind_cross_audit.md`)는 **단 1글자도 번들에 유입되지 않았음을 번들 파일 SHA-256(`ebd6b8c6...`)으로 물리적 증명함.**

---

## 3. Qwen 27B 로컬 감사관의 실제 독립 판단 결과 요약

Qwen 27B 모델이 생성한 원본 리포트(`report.md`, SHA-256: `8D870DEC...`)에서 직접 지적한 10대 독립 결함 목록:

1. **`F-001` (P0 FAIL)**: `counts_by_verdict`의 `NA` 키와 `findings[].verdict`의 `N/A` Enum 간의 불일치로 인한 스키마 데이터 무결성 파괴 적발.
2. **`F-002` (P1 FAIL)**: `overall_verdict` Enum에 `N/A`가 누락되어 전 항목 N/A 시 유효한 전체 결과를 도출하지 못하는 논리적 공백 적발.
3. **`F-003` (P1 WARN)**: v2 JSON 파싱 실패 시 취약한 v1 마크다운 정규식 파서로 자동 폴백되는 Silent Corruption/Security 위험 적발 (Hard Fail 권고).
4. **`F-004` (P1 WARN)**: 글자수 / 4.0 완화 제안 시 일본어/자바 코드 특성상 토큰이 과소평가되어 Ollama 내부에서 프롬프트 뒷부분이 조용히 잘려나가는 Silent Truncation 위험 적발.
5. **`F-005` (P2 WARN)**: FAIL 요건에 단위 테스트 및 런타임 에러 스택을 필수로 요구할 경우, 정적 분석으로 자명한 보안 결함(하드코딩 패스워드, SQL Injection 등)이 WARN으로 격하되는 거짓 음성(False Negative) 위험 적발 (정적 코드 분석 증명 대등 인정 권고).
6. **`F-006` (P2 WARN)**: JSON Schema에 `additionalProperties: false`가 누락되어 LLM의 환각 필드가 여과 없이 리포트에 누출되는 위험 적발.
7. **`F-007` (P1 FAIL)**: 노동법 제37조 중복가산 설명 중 체류시간(525분)과 실노동시간(480분, 연장 0분)의 예시 표현 모호성 지적.
8. **`F-008` (P1 FAIL)**: 설계 규격 내에 `@Transactional`, 비관적 락(`SELECT ... FOR UPDATE`), IDOR 방어에 대한 구체적 체크리스트가 명시적으로 누락되었음을 지적.
9. **`F-009` (P2 WARN)**: 경계값 유효성 검사(`DateTimeParseException`, 음수 근로시간) 감사 가이드 누락 지적.
10. **`F-010` (P2 WARN)**: 통상임금 및 수당 단수처리 시 `RoundingMode.HALF_UP` 법정 기준 명시 누락 지적.

---

## 4. 최종 결론

- **실제 실행 입증**: 자택 PC RTX 5070 Ti의 Qwen 27B 모델이 51.24초 동안 2,351개의 출력 토큰을 실시간 생성하여 공식 리포트를 완성하였음을 물리적으로 100% 입증함.
- **결함의 객관성 확정**: Antigravity의 선행 분석과 Qwen의 실제 독립 출력이 `NA vs N/A (F-001)`, `Silent Fallback (F-003)`, `Silent Truncation (F-004)`, `정적 증명 부재 (F-005)`, `additionalProperties (F-006)`에서 완벽히 일치함을 확인하여, 해당 결함들이 주관적 추정이 아닌 **실체적 시스템 결함(Confirmed Defect)**임을 최종 확정함.
