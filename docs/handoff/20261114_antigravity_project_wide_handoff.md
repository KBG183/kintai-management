# Antigravity project-wide handoff (2026-11-14)

## 목적과 기준 상태

Codex가 2026-11-14 기준 저장소·증적·잔여 작업을 대조한 인수인계입니다. Antigravity에서 아래 남은 작업을 순서대로 수행해 주세요. 저장소에는 사용자 및 이전 작업자의 미커밋 변경과 감사 증적이 많습니다. 기존 변경을 보존하고 reset/revert/clean을 하지 마세요.

- 최신 격리 전체 회귀: 92 PASS, 실패 0, 오류 0, 스킵 0, Gradle exit 0. DB `kintai_full_test_d4124a317ed0`; `docs/audit_runs/isolated_all_20261114_094427_kintai_full_test_d4124a317ed0/`.
- Day 11 연간·경계 대상 실행: 6/6 PASS, 격리 DB `kintai_day11_test_01f4cb1439e6`; `docs/audit_runs/isolated_day11_20261114_094349_kintai_day11_test_01f4cb1439e6/`.
- R-02 정책 채택 및 기술 검증 완료. R-11/R-12/R-14는 명시된 로컬 MySQL 26.7 범위에서 VERIFIED.
- 이전 독립 감사: Qwen 27B 소스 Run `20261114_004900_46049b14` 12/12 PASS; 고정 문서 Run `20261114_005527_76dc1c9f` 10/10 PASS. 두 결과는 당시 입력 bundle에만 유효합니다. 프로젝트 전체 문서 bundle SHA `cd7cd20d12ba7771fb8ceb01dcd95181bfe39c924b8a595aa5e6fb91fdf649b8`는 첫 연결 실패 뒤 잘못된 형식의 보고서가 exit 0으로 생성됐고, 독립 재감사 Run `20261114_014515_cd7cd20d`는 WARN을 반환했습니다. 이 결과들을 PASS로 합산하지 않습니다.

## 고정 범위와 실행 제한

1. R-05(MySQL 8.4 Testcontainers) 및 Day 12 작업은 사용자 지시에 따라 예정일까지 제외합니다. 지금 실행하지 마세요.
2. 운영 DB는 사용 금지. 대상/전체 테스트와 시연마다 새 allowlisted 격리 DB를 사용하고 DB명을 증거로 확인하세요. 기존 격리 DB도 삭제하지 마세요.
3. 원격 Qwen 감사는 `http://100.111.193.94:11434`, `qwen27b:latest`만 사용하세요. 저장소 실행 진입점은 `scripts/codex_hermes_bridge.ps1`이며 HTTP 전송을 기본값으로 사용하고 `scripts/audit_config.json`에서 주소와 모델을 읽습니다. 문서 감사에는 `-SystemPromptOverride`로 작업에 맞는 지시를 지정하세요. 로컬 7B 대체, 다른 IP, 모델 pull/download/install 금지. 연결 실패 시 원본 주소와 오류를 보존하고 미검증으로 남기세요.
4. 외부 배포, 원격 Git 반영, 메시지 전송은 하지 마세요. 해당 작업은 사용자 지시가 별도로 필요합니다.
5. 기존 실패 로그와 증거는 덮어쓰거나 삭제하지 말고 새 실행 디렉터리에 보존하세요.

## 잔여 백로그 구분

- 즉시 넘길 프로젝트 코드 작업은 B-05와 B-06입니다. B-04는 VERIFIED 완료입니다.
- B-01(독립 감사 정확도 벤치마크)과 B-07(감사 메트릭 마크다운 자동화)은 범용 파이프라인 개선 백로그로서 마일스톤 검토 대기입니다. 현재 기능 검증을 막는 결함으로 취급하지 말고, 사용자 일정에 맞춰 별도 우선순위를 정하세요.
- B-02, B-03, B-08은 백로그에서 완료 상태입니다. B-09에는 원격 오류뿐 아니라 빈 응답·종료 청크 누락·잘못된 verdict 표·잘못된 대상 경로의 fail-closed 확인이 포함됩니다. 최신 코드와 실행 증거는 백로그 및 `docs/audit_runs/project_wide_handoff_20261114/`를 확인하세요. R-05와 Day 12는 제외 상태이므로 현재 구현 대상으로 넣지 마세요.

## 실행 순서

### 1. B-05: YearMonth 입력 경계

- `PayrollApiController.java`의 급여 `/list`, `/calculate`, `/confirm-month` 입력 검증을 확인하세요. 현재 원장은 이 라우트들의 엄격한 `YYYY-MM` 검사와 HTTP 400 컨트롤러 테스트가 남았다고 기록합니다. `/excel`의 현재 검증과 응답 동작을 먼저 대조하세요.
- 잘못된 달(예: `2026-13`, `2026-1`, `not-a-month`)이 계산/확정으로 진행되지 않고 일관된 400 응답을 반환하는지 테스트하세요. 기존 API 계약과 오류 형식은 보존하세요.
- 요청 바인딩 수준을 포함하는 controller/API 통합 테스트로 입증하세요. 코드 변경 시 관련 테스트, 격리 타깃 실행, 전체 격리 회귀를 실행합니다.

### 2. B-06: 급여 상세 응답의 데이터 최소화 증거

- `PayrollDetailResponseDto.java`와 직렬화 경로를 확인하세요. 현재 조사에서는 필요한 필드만 가진 명시적 DTO로 확인되어 계좌번호/마이넘버 노출의 직접 증거는 없습니다.
- 민감 필드가 JSON 응답에 포함되지 않는 serializer/controller 회귀 테스트를 추가하세요. 테스트는 DTO 선언만 보지 말고 실제 JSON 직렬화 결과를 확인해야 합니다.
- 노출이 확인될 경우에만 근본 원인과 최소 수정을 수행하고, 데이터베이스 실데이터/운영 DB는 사용하지 마세요.

### 3. 증적과 범위 동기화

- B-04는 ID 오름차순 2단계 잠금과 동시성 테스트가 끝났으므로 완료로 기록하세요. 이 handoff 작성 시 `docs/workflow/pipeline_improvement_backlog.md`에서 완료로 정정했으며 RTM/92건 회귀 증거를 연결했습니다.
- B-05/B-06의 구현·테스트·상태를 RTM 또는 적절한 프로젝트 추적 문서, 한국어/일본어 누적 증적, 작업표와 인수인계서에서 동일하게 반영하세요.
- Day 13의 83건은 과거 체크포인트입니다. 최신 92건 결과를 최신값으로 쓰고 과거 원본은 수정하지 마세요. MySQL 버전, DB명, 실행 결과, 감사 run ID는 원본 증거와 대조하세요.
- 범위를 넘어 법률 완전 준수를 주장하지 마세요. SRS가 제외한 주 40시간, 법정휴일, 월 60시간 초과·세무 영역을 PASS로 표시하지 마세요.

### 4. 독립 Qwen 27B 감사 및 최종 확인

- 기존 bundle `current_docs_audit_bundle_final.md`의 최초 지정 주소 호출은 `GetRequestStream: Unable to connect to the remote server`로 실패했습니다. 후속 실행은 exit 0과 보고서 생성으로 기록됐지만 본문 검증 결과 유효한 감사표가 아니었고, 같은 bundle SHA의 Qwen 재감사 Run `20261114_014515_cd7cd20d`는 구체적 지적 3건을 반환했습니다. 이전 10/10 PASS는 다른 고정 bundle에만 해당합니다. 기존 원본을 덮어쓰지 마세요.
- 새 실행은 `scripts/codex_hermes_bridge.ps1 -Transport Http`를 사용하고, 문서 감사용 system prompt와 판정 표 형식을 지정하세요. 프로세스 exit 0만으로 감사 PASS를 선언하지 마세요. 스크립트가 종료 청크, 본문, verdict 열 및 완료된 보고서를 확인하고 독립 감사가 PASS를 반환해야 합니다. B-05/B-06 변경분은 별도 번들로 감사하세요.
- 최신 수정 후 인수인계 bundle은 `docs/audit_runs/project_wide_handoff_20261114/current_docs_audit_bundle_v10.md`, manifest는 `current_docs_audit_manifest_v10.json`입니다. 매니페스트의 SHA-256과 실제 bundle을 대조한 뒤 원격 감사를 실행하세요. 이전 입력·실패·잘못된 보고서·WARN 결과는 같은 폴더의 별도 실행 디렉터리에 보존합니다.
- 최종 변경 파일과 관련 테스트/증거를 동결해 Qwen 번들을 만들고 실제 원격 감사를 수행하세요. 감사 미실행·연결 실패는 PASS로 기록하지 마세요.
- 지적사항이 있으면 구현과 분리된 검수 결과를 보존하고, 필요한 수정 후 관련 테스트와 동일 범위 감사를 재실행하세요.
- 최종 회귀 실행 결과, Qwen 결과, UI 검수가 요청된 경우 실제 브라우저 흐름, 한·일 문서 수치·상태·경로 및 작업 트리를 대조한 뒤 완료를 보고하세요.

## Codex 감사 실행기 보정 기록

Qwen Run `codex_20261114_111907_v7`에서 지정 원격의 응답이 종료 청크와 함께 도착했으나 `message.content`가 비어 있어 검증기가 PowerShell 매개변수 바인딩 오류로 종료했습니다. 설정의 `think:false`가 `/api/chat` 요청 본문에 전달되지 않은 것이 원인이었습니다. 이를 전달하도록 수정하고 빈 문자열 응답을 fail-closed 검증기로 넘기도록 보정했습니다. 새 로컬 검증은 `docs/audit_runs/project_wide_handoff_20261114/runner_validation_20261114_112300/`이며, 새 독립 감사의 최종 판정은 번들 SHA와 일치하는 실행 증거로 확인해야 합니다.

최종 원격 Qwen 27B 재검수 Run `codex_20261114_112428_v10`은 bundle SHA-256 `80311a3d1ff7aecb1cac5890e9519106b9ce6c0e557386163c85ce06ee0f9f0a`에 대해 Overall Verdict PASS를 반환했습니다. 보고서 SHA-256 `94f7f5bb0e263eec9adbb85400e4e0d254d905347a88ee5cf311f19b101030e8`; run manifest와 report는 `docs/audit_runs/codex_20261114_112428_v10/`에 있습니다. B-09는 이 독립 결과와 로컬 검증에 따라 완료입니다.

## 문서 우선순위와 역사 자료

- 현재 작업 기준은 루트 `AGENTS.md`, `scripts/audit_config.json`, 최신 RTM/백로그/증적과 이 인수인계입니다. `docs/workflow/AGENTS_legacy_20261113.md`, Day 8 학습·과거 브리지 보고서, 날짜가 지난 인수인계는 스냅샷으로만 취급하세요. 그 안의 모델명·주소·완료 상태를 현재 지시로 재사용하지 마세요.

## 다음 담당자가 우선 읽을 파일

`AGENTS.md`; `docs/workflow/operating_policy_v2.md`; `docs/workflow/evidence_status_policy.md`; `docs/workflow/isolated_test_operation.md`; `docs/rtm_ledger.md`; `docs/workflow/pipeline_improvement_backlog.md`; `docs/working/day11_task.md`; `docs/final-portfolio/README_KO.md`; `docs/final-portfolio/README.md`; `docs/study_guides/audit_prompt_spec.md`; `docs/workflow/AGENTS_review.md`.
