# Pipeline Improvement Backlog (범용 AI 개발·감사 파이프라인 개선 원장)

> **운영 철칙**:
> 1. 지금 발견한 문제를 수정하면, `kintai`뿐 아니라 **전혀 다른 신규 프로젝트에서도 같은 문제가 예방되는가?**를 기준으로 분류한다.
> 2. **치명적 신뢰성 훼손**(잘못된 PASS 판정, 증거 유실/오염, 실패인데 정상 종료)만 즉시 수정한다.
> 3. 단순 가독성, 효율성, 프롬프트 문구 개선은 즉시 수정하지 않고 본 백로그에 기록 후 마일스톤 종료 시 모아서 일괄 검토한다.
> 4. 공통 시스템 개선 착수 전에는 반드시 정량적인 **완료 기준(Exit Criteria)**을 먼저 정의한다.

---

## 1. 개선 백로그 목록 (Backlog Items)

| ID | 발생 작업 | 문제 및 현상 | 분류 | 영향 | 발생 횟수 | 증거 파일/경로 | 조치 방향 | 완료 기준 (DoD) | 상태 |
|:---:|---|---|:---:|:---:|:---:|---|:---:|---|:---:|
| **B-01** | Day 8 F-01, F-02 | Qwen 27B가 일본 노동기준법 제37조 할증률(심야 25%, 연장 25%)을 50%, 75%로 환각 오판함 | **공통 시스템** | 정확성 | 2회 (반복) | `docs/temp_audit_day8_bundle.java`<br>`docs/audit_ledger.csv` | 추후 개선 (프롬프트/도메인 지식 팩 주입) | 법률/수치 감사 시 조문 출처 없는 환각 주장을 0건으로 억제하고 정답 케이스 5건 100% 통과 | 대기 (마일스톤 시 검토) |
| **B-02** | Day 8 F-04 | `confirmPayroll`과 `calculatePayrollForStaff` 간 락 획득 순서 역전으로 인한 MySQL 1213 데드락 | **프로젝트 결함** | 정확성, 안정성 | 1회 (재현) | `docs/f04_evidence/show_engine_innodb_status_deadlock.txt` | **즉시 수정 완료** | `confirmPayroll` 전역 락 순서 일치 및 동시 실행 시 1213 데드락 0건 실측 | **완료 (PASS-VERIFIED)** |
| **B-03** | Day 8 F-04 증적 | 디버깅 중 transient Thread ID(1114)가 공식 보존 로그(1112/1113)와 혼선될 뻔함 | **공통 시스템** | 재현성, 신뢰성 | 1회 | `docs/f04_evidence/deadlock_execution_ledger.md` | **즉시 수정 완료** | 공식 실행 번호, SHA-256, Connection ID를 원본과 1:1 매핑 분리 기록 | **완료 (해소됨)** |
| **B-04** | Day 8 F-04 배치 | `confirmMonthlyPayroll` 일괄 확정 시 다중 행 물리적 락 순서 미보장 (`ORDER BY`의 락 순서 불일치) | **프로젝트 결함** | 안정성 | 1회 (설계) | `docs/f04_evidence/lock_architecture_analysis.md`, `docs/rtm_ledger.md`, 최신 전체 회귀 증적 | 대상 DRAFT 직원만 동적 추출 후 ID 오름차순 2단계 선점 잠금 구현 및 다건 동시성 테스트 통과 | **완료 (VERIFIED)** |
| **B-05** | Day 8 F-07 | YearMonth 파라미터 유효성 검증 부재 및 HTTP 400 예외 매핑 미비 | **프로젝트 결함** | 안정성 | 1회 | `PayrollApiController.java`, `PayrollApiValidationAndSerializationIntegrationTest.java`, `docs/audit_runs/isolated_all_20261114_114819_kintai_full_test_8a75679f5b1e/` | `validateYearMonth` 공용 검증 적용 완료 | 올바르지 않은 YYYY-MM 입력 시 400 Bad Request 반환 통합 테스트 통과 | **완료 (VERIFIED, 94/94 PASS)** |
| **B-06** | Day 8 F-10 | 급여 명세 상세 응답 DTO에서 민감정보(계좌번호, 마이넘버) 노출 위험 방어 | **프로젝트 결함** | 보안성 | 1회 | `PayrollDetailResponseDto.java`, `PayrollApiValidationAndSerializationIntegrationTest.java`, `docs/audit_runs/isolated_all_20261114_114819_kintai_full_test_8a75679f5b1e/` | 명시적 DTO 유지 및 JSON 직렬화 회귀 테스트 추가 | 응답 JSON 직렬화 패킷 검증 테스트에서 민감 필드 누락/미포함 확인 | **완료 (VERIFIED, 94/94 PASS)** |
| **B-07** | 감사 파이프라인 | 로컬 감사 스크립트 실행 메트릭의 수동 마크다운 표 변환 공수 발생 | **개선 아이디어** | 시간, 편의성 | 다수 | `audit_with_hermes.ps1` | 추후 개선 | 스크립트 출력 결과에 한글 마크다운 표 블록을 자동 생성 옵션으로 내장 | 대기 (마일스톤 시 검토) |
| **B-08** | 파이프라인 헌법 준수 | 개발자 셀프 판정 오류(로컬 27B 감사 생략 후 외부 제출 시도) 발생 | **공통 시스템** | 신뢰성, 절차 무결성 | 1회 | 대화 로그 및 `qwen_final_audit_report.md` | **즉시 해결 완료** | 사용자의 지적 즉시 Qwen 27B 독립 감사 번들 투입 ➔ 공식 PASS 획득 후 외부에 보고하는 정석 순서 복원 및 `daily_audit_temp.log`에 실측치 누적 | **완료 (운영 규칙 확립)** |
| **B-09** | Project-wide handoff audit (2026-11-14) | API 오류의 false exit-0 외에 빈/미완료/잘린 응답·프롬프트와 충돌하는 감사 역할·잘못된 verdict 보고서가 성공처럼 기록될 수 있고, bridge Auto가 로컬 Ollama를 선택할 수 있음 | **공통 시스템** | 신뢰성·절차 무결성 | 1회 | `docs/audit_runs/project_wide_handoff_20261114/`; `scripts/audit_with_hermes.ps1`; `scripts/codex_hermes_bridge.ps1` | 지정 원격 HTTP 및 설정 inference 옵션(think 포함), 작업별 system prompt, 입력 토큰 예산, 종료 청크·본문·verdict 표 검증, 잘못된 입력/기존 출력/중복 run ID 거부, 해시 포함 실행 증거 | 빈·잘림·표/프롬프트 오류 테스트, 기존 증거 보존, HTTP/Auto 원격 preview, PowerShell parser와 wrapper/preflight mock, 지정 Qwen 27B 독립 재검수 PASS | **완료 (2026-11-14, Run `codex_20261114_112428_v10`, bundle SHA-256 `80311a3d1ff7aecb1cac5890e9519106b9ce6c0e557386163c85ce06ee0f9f0a`)** |

---

## 2. 운영 주기 및 규칙
1. **개발 중 (In Sprint)**: 새로운 결함/아이디어 발견 시 즉시 코드를 고치지 않고 위 표에 한 줄 추가(기록)하고 본래 기능 개발에 집중한다.
2. **기능 검증 후 (Post Feature)**: 치명적 결함(감사 신뢰성 훼손) 여부만 즉시 판단하여 조치한다.
3. **주간 마일스톤 종료 시 (Milestone Review)**: `공통 시스템` 분류 항목들을 모아서 완료 기준(DoD)에 맞춰 일괄 개선한다.

## 3. 2026-11-13 후속 과제와 확인 범위

- 감사 입력 보존: 프로젝트 사전검증 래퍼는 실행별 입력 복사본, 매니페스트, 검증 결과 및 해시를 보존한다. 성공 시 기본 출력도 실행 폴더에 저장한다. 실제 Qwen 성공 경로의 변경 후 종단 간 검증은 네트워크 접근 제약으로 대기한다.
- 호출 경로: 래퍼 사용은 운영 지침이며 사용자 계정 Hermes 직접 호출로 우회 가능하다. 전역 스크립트 변경 없이 자동 강제되었다고 판단하지 않는다.
- 모델 설정: Day 10 기록은 사용자 계정 Hermes와 `qwen27b:latest`를 사용했다. 프로젝트 내 동명 스크립트와 설정은 다른 기본값을 포함하므로 용도 확인 전 삭제·통합하지 않는다. 7B 설정의 실제 자동 폴백 경로는 미확인이다.
- 감사 품질: 독립적으로 만든 정상·결함 사례와 정답 기준을 사전 고정하고, Qwen의 탐지·오탐 및 미탐을 기록하는 소규모 벤치마크를 준비한다. 현재 실행 및 측정은 하지 않았다. 테스트 성공과 모델 판정은 별도로 기록한다.
- MySQL 8.4: Docker 명령을 찾지 못하여 Testcontainers 재실행은 대기한다. 기존 MySQL 26.7 증거와 분리한다.
- 원본 규칙 이관: `AGENTS_migration_map.md`는 대분류 기준 검토표이며, 기존 세부 조항별 이관의 완전성은 아직 검증되지 않았다.
- 한·일 증적 이력 대조: 아이치 노동국의 끝수 처리 자료는 1988년 3월 14일, 후생노동성 Q&A는 1987년 3월 14일을 표기한다. 일반 해석례 1988년 통달은 후생노동성 개정 통달에서 확인된다. 동일 원전 여부와 Q&A 연도 차이의 원인은 미확정이며 한·일 증적서 상단에 출처와 주의사항을 병행 기록했다.


### B-04 상태 정정 (2026-11-14)

구현과 동시성 회귀는 완료되어 RTM 및 최신 전체 회귀에 증거가 있습니다. PayrollMonthlyConfirmPostQueryRaceConditionIntegrationTest 2건을 포함한 최신 격리 전체 회귀 92/92 PASS (docs/audit_runs/isolated_all_20261114_094427_kintai_full_test_d4124a317ed0/). 과거 상태 문구를 완료로 정정했습니다.


### B-09 현재 실행 결과 (2026-11-14)

이전 실패 경로 수정은 원격 연결 오류에서 exit 1을 반환했지만, Qwen 후속 문서 감사는 exit 0으로 잘못된 보고서를 만들었습니다. 독립 재감사 Run `20261114_014515_cd7cd20d`도 fail-closed 범위와 실행 상태 표기에 구체적 지적을 남겼습니다. 이를 반영해 프로젝트 스크립트에 작업별 system prompt, 입력 컨텍스트 예산과 종료 청크·본문·verdict 표 검증, 잘못된 대상·기존 출력 거부를 추가했습니다. HTTP/Auto 원격 기본값, 설정 해시 기록, 중복 run ID 거부를 확인했습니다. Qwen Run `codex_20261114_111907_v7`에서 `think:false` 미전달로 응답 본문이 비어 있던 결함을 추가로 발견해 설정값을 API 요청에 전달하고 빈 문자열 검증을 fail-closed 처리했습니다. `runner_validation_20261114_112300`에서 parser, 응답 계약, HTTP/Auto preview, preflight 입력 검사, wrapper mock이 PASS했습니다. 최종 번들 SHA-256 `80311a3d1ff7aecb1cac5890e9519106b9ce6c0e557386163c85ce06ee0f9f0a`는 원격 Qwen Run `codex_20261114_112428_v10`에서 PASS했습니다. 보고서와 run manifest는 `docs/audit_runs/codex_20261114_112428_v10/`에 보존합니다.
