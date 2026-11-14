# 2026-11-13 Codex 운영 안정화 개선 인수인계서

## 목표

격리 회귀 실행 결과를 자동 집계·해시하고, 상용 AI 요청 범위를 빠짐없이 전달할 운영 양식을 마련한다. 기능 코드는 이 작업 범위에 포함하지 않는다.

## 현재 상태

### 완료

- `scripts/run_isolated_regression.ps1`
  - `Day10` 단일 테스트와 `All` 전체 회귀 실행을 지원한다.
  - 허용된 격리 DB 이름과 `SELECT DATABASE()` 결과를 확인한다.
  - Gradle 콘솔 로그와 JUnit XML을 실행별 폴더에 보존한다.
  - JUnit XML의 `tests`, `failures`, `errors`, `skipped`를 집계해 `exit_summary.json`에 기록한다.
  - 콘솔 로그 및 JUnit XML의 SHA-256을 `sha256_manifest.json`에 기록한다.
  - `KINTAI_DB_PASSWORD`를 우선 사용한다.
- `docs/workflow/isolated_test_operation.md`에 실행 방법, 산출물, 성공 판정 조건을 기록했다.
- `docs/workflow/commercial_ai_request.template.md`에 작업 범위, 수정 금지 범위, SSOT/SRS/RTM 경로, 테스트, 성공 기준, 증적, 미검증·BLOCKED 항목을 정리했다.
- 한·일 증적서와 `docs/rtm_ledger.md`에 현재 검증 상태를 기록했다.
- `docs/audit_runs/isolated_all_20261113_164156_kintai_full_test_52bbea674f8d/`의 기존 실행 로그에서 Gradle 종료 코드 0을 확인했다. 기존 JUnit XML 33개를 사후 집계한 결과는 79 PASS, 실패 0, 오류 0, 스킵 0이다. 콘솔 로그와 XML의 SHA-256 매니페스트를 사후 생성하고 모든 해시의 일치를 확인했다.
- PowerShell 파서 기준 실행기 구문 오류 0건을 확인했다.

### 미완료 / 미검증

- 위 79건 실행은 상세 JUnit 집계와 해시 자동 생성 변경 전 실행이다. 따라서 수정된 실행기의 종단 간 실행 검증은 아직 완료되지 않았다.
- 새 실행기로 새 격리 DB를 대상으로 실행하려는 시도는 Codex 실행 환경에서 `127.0.0.1:3306` 연결이 차단되어 진행하지 못했다. 기존 PASS 결과를 수정된 실행기의 PASS로 주장하지 않는다.
- MySQL 8.4 Testcontainers는 Docker 부재로 `BLOCKED`다.
- 실행기 파일은 `.gitignore`의 `scripts/` 규칙에 걸려 Git 변경 목록에 표시되지 않는다. 현재 공유 작업 폴더에는 파일이 있지만, 별도 clone/브랜치로 전달할 때는 파일이 빠질 수 있으니 실제 파일 존재와 내용부터 확인한다.

## 결정 및 근거

- 기존 79건 회귀 결과는 기존 실행 로그와 JUnit XML로 입증된 상태 그대로 보존한다.
- 새 집계·해시 로직은 기존 XML에 대한 사후 집계와 해시 대조로 검증했지만, 이것은 실행기 전체 경로를 실행한 검증과 구분한다.
- 기능 구현은 Antigravity, 기존 Hermes/Qwen을 통한 독립 감사도 Antigravity가 사용자 지시에 따라 수행한다. Codex는 사용자가 요청할 때만 검수한다. 자동 AI 간 호출을 전제하지 않는다.
- 운영 DB `kintai_db`를 테스트 또는 정리 대상으로 삼지 않는다. 정리 스크립트는 우선 조회 모드만 사용한다.

## 관련 파일

- 실행기: `C:\dev\kintai\scripts\run_isolated_regression.ps1`
- 격리 DB 정리기: `C:\dev\kintai\scripts\cleanup_isolated_databases.ps1`
- 테스트 운영 규격: `C:\dev\kintai\docs\workflow\isolated_test_operation.md`
- 상용 AI 요청 양식: `C:\dev\kintai\docs\workflow\commercial_ai_request.template.md`
- 기존 전체 회귀 결과: `C:\dev\kintai\docs\audit_runs\isolated_all_20261113_164156_kintai_full_test_52bbea674f8d\`
- 한국어 증적: `C:\dev\kintai\docs\portfolio_audit_evidence_KO.md`
- 일본어 증적: `C:\dev\kintai\docs\portfolio_audit_evidence.md`
- RTM: `C:\dev\kintai\docs\rtm_ledger.md`

## 다음 작업자가 할 일

1. `C:\dev\kintai\AGENTS.md`와 이 인수인계서를 먼저 읽는다.
2. 실행기 파일이 현재 workspace에 있는지 확인하고, 스크립트에 비밀번호가 로그·매니페스트에 기록되지 않는지 정적으로 확인한다.
3. 사용자의 Windows PowerShell과 로컬 MySQL이 연결된 환경에서 고유한 허용 목록 격리 DB 이름으로 수정된 실행기를 실행한다.
4. 실제 `exit_summary.json`의 Gradle 종료 코드 및 JUnit 합계와 `sha256_manifest.json`의 해시를 검증한다. `ISOLATED_REGRESSION_SUCCESS`가 출력되어야 성공으로 판정한다.
5. 운영 DB 미접촉 여부와 실행 대상 DB를 확인한 뒤, 한·일 증적서·RTM·인수인계서를 같은 수치와 상태로 갱신한다. 테스트를 실행하지 못하면 `BLOCKED` 또는 `미검증`으로 기록한다.
6. 격리 DB 정리 전 조회 모드 결과와 증적 보존 여부를 확인한다. 삭제는 사용자의 명시적 지시 없이는 수행하지 않는다.

## 사용자에게 전달할 시작 지시 예시

“먼저 `C:\dev\kintai\AGENTS.md`와 `docs\handoff\20261113_1650_codex_stability_improvements_handoff.md`를 읽고, 이번 격리 실행기 개선의 미검증 항목부터 이어서 진행해 주세요. 기존 79/79 PASS는 수정된 실행기의 E2E 검증으로 취급하지 말고, 로컬 MySQL에서 새 격리 DB를 사용해 실제 실행 결과와 자동 집계·SHA-256을 확인해 주세요. 한·일 증적서와 RTM도 동일한 사실로 갱신하고, 기능 코드는 건드리지 마세요.”
