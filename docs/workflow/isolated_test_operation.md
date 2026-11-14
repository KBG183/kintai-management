# 격리 테스트 운영 규격

## 표준 실행기

모든 로컬 MySQL 회귀 검증은 프로젝트 루트에서 다음 공용 실행기를 우선 사용한다.

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\run_isolated_regression.ps1 `
  -Scope Day10 `
  -DatabaseName kintai_day10_test_<12자리hex>

powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\run_isolated_regression.ps1 `
  -Scope All `
  -DatabaseName kintai_full_test_<12자리hex>
```

The standard runner writes JUnit totals (`tests`, `failures`, `errors`, `skipped`) to `exit_summary.json`. It also writes `sha256_manifest.json` with SHA-256 hashes for the captured Gradle console log and copied JUnit XML files. A missing JUnit result, malformed XML, test failure, or Gradle failure prevents a success result.

For commercial AI implementation, design, or review requests, copy and complete `commercial_ai_request.template.md` before submitting the task.

실행기는 대상 DB 이름을 허용 목록과 대조하고 `SELECT DATABASE()`로 연결 대상을 확인한 뒤 Gradle을 실행한다. 원본 콘솔 로그, JUnit XML, 실행 메타데이터, 종료 코드 요약은 `docs/audit_runs/isolated_<scope>_<run-id>_<database>/`에 보존한다.

기존 `run_day10_isolated_test.ps1`, `run_full_isolated_regression.ps1`은 호환성을 위해 유지한다. 새 작업에서는 공용 실행기를 기준으로 기록한다.

## 자격증명 규칙

- 표준 실행기는 `KINTAI_DB_PASSWORD` 환경변수를 우선 사용한다.
- 환경변수가 없을 때만 현재 프로젝트의 기존 설정을 호환용으로 읽는다.
- 비밀번호 값을 로그, 매니페스트, 증적서에 기록하지 않는다.
- 최종 배포 전에는 `application.yaml`의 평문 비밀번호를 제거하고 사용자 환경변수 또는 무추적 로컬 설정으로 전환한다. 이 전환은 기존 로컬 실행에 영향을 주므로 별도 환경 전환 시점에 수행한다.

## 격리 DB 정리

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\cleanup_isolated_databases.ps1
```

기본 실행은 후보 목록만 출력한다. 검토 후 생성 규칙에 맞는 DB만 삭제하려면 명시적으로 `-Apply`를 사용한다. `kintai_db` 및 허용 목록 밖의 DB는 삭제 대상이 될 수 없다.

## 판정

로컬 MySQL 실행 결과와 MySQL 8.4 Testcontainers 결과는 별도 환경으로 기록한다. Docker가 없는 실행 환경에서는 Testcontainers를 `BLOCKED`로 유지하며 로컬 MySQL 결과로 대체 승인하지 않는다.
