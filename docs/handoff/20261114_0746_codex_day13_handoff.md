> **과거 체크포인트:** 이 문서는 Day 13 시점의 UI/감사 인수인계이며 이후 잔여 검증 전의 83건 실행 결과를 보존합니다. 현재 전체 회귀와 R-11/R-12/R-14 판정은 `docs/handoff/20261114_antigravity_project_wide_handoff.md`를 확인하십시오.

# Day 13 로컬 검수 인수인계

- 작업일: 2026-11-14
- 작업자: Codex
- 판정: **R-15~R-18 VERIFIED (로컬 MySQL 26.7 범위; MySQL 8.4는 별도 deferred)**
- 작업 트리 상태: 기존 변경사항을 보존했고 commit/push는 하지 않았다.

## 완료 및 검증된 항목

- 캘린더가 비어 보이던 원인은 `/api/shifts/events`의 `CalendarEventDto`와 화면의 `ShiftDto` 필드 접근 불일치였다. `calendar.html`이 실제 API 이벤트 형태를 유지하고, 날짜·시간·휴게를 올바른 필드에서 읽도록 수정했다.
- `prepare_local_demo_db.ps1`의 PowerShell 텍스트 파이프가 일본어 샘플명을 `?`로 손상시키는 것을 HEX로 확인했다. SQL 파일을 UTF-8 stdin으로 mysql에 전달하고, shift 수와 owner-name HEX를 검증하도록 수정했다.
- `scripts/` 경로는 `.gitignore`에 포함되어 `prepare_local_demo_db.ps1` 수정은 현재 로컬 파일에만 있다. Git에 force-add하지 않았다.
- 최종 새 격리 DB `kintai_demo_9f3f8487bc42`에서 DB 식별자, confirmed shift 313행, attendance 313행, 2026년 전체 기간, owner-name UTF-8 HEX를 확인했다. 잘못된 입력으로 생성된 `kintai_demo_c5567594663c`, `kintai_demo_cca4ec3cbf03`는 그대로 보존했다.
- 최종 Java 전체 회귀는 격리 DB `kintai_full_test_3df696be0820`에서 83/83 PASS, 실패·오류·스킵 0, Gradle exit 0, JUnit XML 35개다. 새 `StoreAuthorizationServicePayrollOwnershipTest`를 포함했다. 로그: `docs/audit_runs/isolated_all_20261114_081141_kintai_full_test_9f4b67f66b64/`.
- UI는 포트 8082의 격리 DB에서 검수했다. 2026년 11월 달력에서 두 직원의 확정 이벤트가 이름과 함께 표시됐고, 11월 2일 이벤트의 매니저 모달에서 이름·09:00–18:00·휴게 60분을 확인했다.
- 새 demo DB의 인증된 2026-01 계산 API가 HTTP 200을 반환했다. 두 직원의 월 급여는 각각 DRAFT ¥141,700 (13일, 104시간) 및 DRAFT ¥164,332 (14일, 112시간, 야간 14시간)이다. 실제 API 응답은 `docs/audit_runs/day13_calendar_20261114/payroll_api_list.json`에 저장했다.
- Excel API가 HTTP 200과 XLSX attachment를 반환했다. 다운로드 파일 5,092 bytes, OOXML workbook/sheet parts, 대상 월과 두 총액을 검증했으며 파일 SHA-256을 `payroll_excel_validation.json`에 보존했다.
- `CsrfCookieHeaderLifecycleIntegrationTest`, `CsrfSecurityIntegrationTest`를 포함한 최종 회귀를 실행했다.

## 별도 deferred 항목

- 후속 재검수에서 실제 브라우저 다운로드 이벤트와 저장된 XLSX를 확인했습니다. 상세는 아래 후속 기록과 `excel_browser_download.json`을 참조합니다. R-17은 VERIFIED로 갱신했습니다.
- MySQL 8.4 Testcontainers는 기존 일정대로 2027-01 재검토(DEFERRED)입니다.

## 독립 Qwen 27B 재검수

- 올바른 원격 엔드포인트는 `http://100.111.193.94:11434`이며 `/api/tags`에서 `qwen27b:latest`, 27.3B, IQ3_M, digest `23c3820b0d9326b73e97191b9fb7e16b53f6e24bb3449d2a17037c5f2b6179a1`을 확인했습니다. `100.91...`은 과거 증적을 잘못 선택한 에이전트 오류였습니다.
- 최초 시도는 지시를 오해해 로컬 `qwen3.8:27b` pull을 시작했다가 약 7GB에서 중단했으며 모델은 설치·감사에 사용되지 않았습니다. 사용자의 삭제 지시 후 미설치·미참조 blob 21개를 제거하고 기존 Qwen 7B 설치를 보존했습니다. 증거는 `qwen_local_pull_cleanup_manifest.json`입니다.
- 최종 원격 감사는 Hermes v1.1.1, Qwen 27B IQ3_M, Run ID `20261113_232151_a808922d`, 42.59초, 3283 입력/1673 출력 토큰입니다. 최종 번들 SHA-256 `a808922ded9a6ea3cf62c981501967389e57215917c9bc1d156d9685c3923052`, 보고서 SHA-256 `1CBA641563D0BD88CCE21E41C71F97E7A563C550EBFDEA9DF9FB6A09F646D61E`. Qwen은 주요 Day 13 항목 PASS, Chrome 다운로드 검증을 P2 WARN/미해결로 판정했습니다. 전용 cross-owner 권한 테스트 추가 후 새 격리 DB 전체 회귀 83건이 통과했습니다. Chrome은 export URL에서 `ERR_BLOCKED_BY_CLIENT`를 표시했습니다. 브라우저 도구 정책이 `chrome://downloads/` 확인과 우회 시도를 차단해 저장 여부는 검증할 수 없습니다. 이는 다운로드 이벤트 재확인 전의 감사입니다. 후속 검증 파일을 추가한 뒤 R-18 재감사를 진행 중입니다.
- 감사 보고서 `docs/audit_runs/day13_calendar_20261114/qwen27b_final/report.md`, 실행 원문·메타데이터 `qwen27b_final/`, 콘솔 `qwen27b_final_console.log`; 첫 감사와 중간 재감사도 별도 디렉터리에 원문 보존했습니다.

## 원본 및 추적

- 번들·manifest·UI 증거·DB 준비 로그·API 목록: `docs/audit_runs/day13_calendar_20261114/`
- 검수 로그: `docs/audit_runs/isolated_all_20261114_081141_kintai_full_test_9f4b67f66b64/`
- RTM: `docs/rtm_ledger.md` R-15~R-18
- 한·일 누적 기록: `docs/portfolio_audit_evidence_KO.md`, `docs/portfolio_audit_evidence.md`






## Excel 브라우저 다운로드 후속 검수 (2026-11-14 KST)

- Codex 인앱 브라우저에서 격리 데모 급여 화면을 열고 2026-01의 두 DRAFT 행과 총액을 확인했습니다.
- Excel 출력 버튼을 눌러 브라우저 download 이벤트가 완료된 것을 확인했습니다. 저장 파일은 `給与台帳_2026-01.xlsx`, 5,092 bytes이며 증적 사본은 `docs/audit_runs/day13_calendar_20261114/payroll_2026-01_browser_download.xlsx`입니다.
- 실제 저장 파일에서 OOXML workbook/sheet, 2026-01, 총액 ¥141,700 및 ¥164,332를 검사했습니다. SHA-256 `ba127f65f193f8289c2b4e445efc09a06e1590e2230ed0b1ad603d839cfdcd98`; 상세 `excel_browser_download.json`.
- 따라서 Chrome의 이전 `ERR_BLOCKED_BY_CLIENT`는 과거 시도 결과로 보존하고, R-17의 현재 판정은 VERIFIED입니다. 최신 원격 Qwen 27B 재감사에서 R-18도 VERIFIED로 확정했습니다.
## 최종 Day 13 독립 재검수 판정 (2026-11-14 KST)

- **Day 13 판정: PASS (로컬 MySQL 26.7 검증 범위).** R-15/R-16/R-17/R-18이 RTM에서 모두 VERIFIED입니다.
- 전체 회귀는 격리 DB `kintai_full_test_9f4b67f66b64`에서 83/83 PASS, 실패·오류·스킵 0, Gradle exit 0입니다.
- Qwen 27B 최종 재감사: Hermes v1.1.1, 원격 `http://100.111.193.94:11434`, `qwen27b:latest` 27.3B IQ3_M, digest `'+$audit.model_digest+'`; Run ID `'+$audit.run_id+'`; 입력 SHA-256 `'+$bundleHash+'`; report SHA-256 `'+$reportHash+'`. finding 15개 PASS, WARN/FAIL 0.
- 브라우저에서 실제 Excel 다운로드 이벤트와 파일을 검증했으며, 증거 `docs/audit_runs/day13_calendar_20261114/excel_browser_download.json` 및 최종 점검표 `docs/audit_runs/day13_calendar_20261114/day13_completion_checklist.json`입니다.
- MySQL 8.4 Testcontainers는 기존 일정대로 2027-01 재검토(DEFERRED)이며 Day 13 로컬 검수 범위와 구분합니다.