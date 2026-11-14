# Codex 실행 지연 및 재시도 인시던트 기록 (2026-11-14)

## 범위와 결론

이번 감사 실행기 개선 작업이 사용자가 두 차례 진행을 지시한 뒤 약 1시간으로 늘어난 실행 문제를 기록합니다. 정상적인 작업 시간이 아니었습니다. 구현 자체보다 범위 통제 실패, 검수 입력 생성 실수, 원격 호출 전 환경 확인 누락, 실행 결과 자동 기록 부족이 시간을 늘렸습니다. 기존 실행 폴더와 실패 로그는 삭제하거나 성공으로 덮지 않았습니다.

이 문서는 기존에 분산돼 있던 run manifest/error, 번들, 로컬 검수 로그와 세션에서 확인한 실행 출력을 한 타임라인으로 모은 사후 기록입니다. 모든 초기 셸 출력이 별도 원본 로그 파일로 저장돼 있던 것은 아닙니다. 특히 v8 번들 생성 명령의 출력은 개별 run 폴더가 없었으므로, 실제 파일 크기·줄 수·SHA와 당시 검수 출력에 남은 값을 근거로 여기 기록합니다.

## 실행 타임라인

| 시각/ID | 실제 결과 | 원인·영향 | 보존 증거 |
|---|---|---|---|
| 기존 정확 번들 재시도 `20261114_021128_e399e0da` | Qwen HTTP 400, process exit 1, prompt/eval token 0 | v4 번들은 73,698 bytes / 1,080 lines였습니다. 컨텍스트 여유와 실제 요청 크기를 충분히 사전 확인하지 않고 한 번에 제출했습니다. 검토 결과는 생성되지 않았습니다. | `docs/audit_runs/20261114_021128_e399e0da/audit_run.json` |
| `preflight_fe27bef9c13a406b8891f791f9e28ef2` | 31개 섹션 분할 실행을 첫 섹션 이후 중단 | 분할 감사의 총 비용·완료 시간이 과도해 보였지만, 시작 전에 이를 차단할 단일 요청 전략을 만들지 않았습니다. 미완료 실행을 완료 증거로 쓰지 않았습니다. | 해당 preflight 폴더의 입력 snapshot과 preflight 기록 |
| v5 생성 | 50,173 bytes / 709 lines, 입력검증 `INPUT_READY`; 원격 감사 미실행 | 입력 validator는 파일·해시·필수 메타데이터를 확인하지만 입력 크기와 실제 단일/분할 실행 비용은 확인하지 않습니다. `INPUT_READY`를 전체 실행 적합성으로 오해할 위험이 드러났습니다. | `current_docs_audit_bundle_v5.md`, `current_docs_audit_manifest_v5.json` |
| v6 원격 실행 `codex_20261114_111454_v6` | process exit 1; 원격 소켓 연결 전에 sandbox 권한 거부 | 원격 감사를 승인된 주소로 실행할 때 필요한 네트워크 권한을 최초 실행에 요청하지 않았습니다. 이 오류는 Qwen 서버 거부가 아니라 실행 환경의 권한 경계였습니다. | `docs/audit_runs/codex_20261114_111454_v6/run_manifest.json`, `error.txt` |
| v7 원격 실행 `codex_20261114_111907_v7` | 서버 응답 종료·eval 1,430 tokens 확인, 감사 본문은 빈 문자열, process exit 1 | 설정에는 `think:false`가 있었지만 bridge와 `/api/chat` body에 전달하지 않았습니다. Qwen은 `message.thinking`만 반환했고, 빈 content가 검증기까지 도달하기 전에 PowerShell Mandatory 매개변수 바인딩 오류가 났습니다. | `docs/audit_runs/codex_20261114_111907_v7/run_manifest.json`, `error.txt` |
| v8 번들 생성 | 549,068 bytes / 12,728 lines, SHA `76ccb0520307f5cd628af4f74efb5de9fcbd751e4d80cad61dc5f188986b2222`; validator는 `INPUT_READY`; 원격 감사 미실행 | PowerShell range helper가 구간 쌍 대신 정수 인덱스 목록을 받아 각 줄을 구간으로 처리했습니다. 검수 대상이 약 11배로 팽창했습니다. 크기/줄 수/단일 요청 점검이 validator에 없어 구조 오류를 사전에 막지 못했습니다. | `current_docs_audit_bundle_v8.md`, `current_docs_audit_manifest_v8.json`; 생성 당시 출력은 본 인시던트 기록에 사후 보존 |
| v9 번들 생성 | 60,947 bytes / 711 lines, SHA `246834352b83c68be9a02b53d5848781a435c72870e5e90aebce7026dbe9a7f8`; validator는 `INPUT_READY`; 원격 감사 미실행 | 문서의 2단계 제목이 남아 자동 섹션 분할 조건에 걸릴 수 있었습니다. v4의 실패 원인과 v7의 빈 본문 실패를 토대로 한 번에 확인할 실행 입력임을 사전 검증하지 못했습니다. | `current_docs_audit_bundle_v9.md`, `current_docs_audit_manifest_v9.json` |
| 최종 v10 `codex_20261114_112428_v10` | 지정 원격 HTTP, `qwen27b:latest`, `think:false`; process exit 0, 완료 응답 및 구조화 보고서 확인, Overall PASS. Qwen 실행 51.41초 | 응답 설정 전달 수정 후 단일 번들 감사가 정상 완료됐습니다. 번들 SHA `80311a3d1ff7aecb1cac5890e9519106b9ce6c0e557386163c85ce06ee0f9f0a`. | `docs/audit_runs/codex_20261114_112428_v10/`의 `run_manifest.json`, `hermes_report.md`, `audit_result_summary.json` |

## 근본 원인과 문제점

1. **작업량을 통제하지 못함:** 완료 조건과 입력 크기 상한을 먼저 정하지 않고 여러 버전의 번들·실행을 만들었습니다. 사용자가 요청한 연속 실행 원칙과 달리 실패마다 원인을 좁혀 한 번에 보정하지 못했습니다.
2. **사전 연결 확인 누락:** 원격 호출 첫 시도 전에 sandbox 네트워크 제약을 확인하지 않아 v6 실행이 권한 경계에서 실패했습니다.
3. **실제 호출과 설정 파일의 불일치:** 설정 파일의 `think:false`를 bridge와 API 요청이 전달하지 않았습니다. 기존 preview 테스트는 manifest 설정만 확인했지 실제 request body를 확인하지 않았습니다.
4. **검증기 경계 오류:** Mandatory `[string]` 매개변수가 빈 content를 거부해, 응답 검증기가 의도한 `HERMES_FAILED: response contained no audit content` 대신 바인딩 오류를 냈습니다.
5. **번들 생성 오류와 validator 범위 부족:** 구간 선택 helper 오류로 v8이 팽창했습니다. validator의 `INPUT_READY`는 해시·필수 자료가 준비됐다는 뜻일 뿐, 크기·줄 수·헤더 분할 여부·요청 예산이 적정하다는 뜻이 아닙니다.
6. **실패·감사 상태를 한 기록으로 볼 수 없었음:** 개별 manifest와 오류 로그는 있었지만 모든 재시도, 원인, process exit, 의미 판정의 단일 실행 요약이 없었습니다. 사용자가 전체 타임라인을 다시 묻기 전까지 한눈에 볼 기록이 불충분했습니다.
7. **보고 방식 실패:** 완료되지 않은 작업을 중간에 멈추거나, 사용자에게 실패 원인·남은 항목을 시점에 맞춰 명료하게 알리지 못했습니다. 이로 인해 실제 실행 지연보다 더 큰 불확실성과 재질문을 만들었습니다.

## 해결 및 후속 방지

### 이번 실행에서 적용하고 확인한 수정

- Bridge가 설정의 `think` 값을 manifest와 감사 스크립트에 전달하고, 감사 요청 본문에 `think = $Think`를 설정하도록 수정했습니다.
- 빈 응답도 함수 검증기 안으로 전달되도록 `[AllowEmptyString()]`을 추가해, 일반 바인딩 오류가 아니라 명시적 fail-closed 오류가 나게 했습니다.
- 응답·routing·preflight·wrapper mock 4종과 PowerShell parser 7개 파일을 `runner_validation_20261114_112300/`에서 PASS 확인했습니다.
- 검수 입력 manifest, bundle hash, endpoint/model, prompt/runner/config hash, process 상태와 독립 의미 판정을 담은 `audit_result_summary.json`을 최종 run 폴더에 남겼습니다.
- 원격 Qwen 27B의 독립 최종 재검수가 v10에 PASS를 반환했으며, 해당 결과는 B-09 완료의 근거입니다.

### 다음 실행부터 적용할 절차 개선

- 검수 번들을 만들기 전에 생성 스크립트 자체 테스트, 파일 bytes/lines, 제목 수, SHA, 토큰 예산을 검사합니다. validator의 `INPUT_READY`를 실행 준비 전체의 승인으로 취급하지 않습니다.
- 원격 연결 권한과 지정 endpoint를 사전 확인하고, 첫 네트워크 호출부터 사용자 지정 주소만 사용합니다. 실패 시 즉시 해당 오류와 상태를 기록하고 다른 주소·모델로 대체하지 않습니다.
- 설정 전달 검사는 preview manifest에 더해 최종 HTTP request body까지 단위/통합 검사로 확인합니다.
- 각 시도마다 새 run ID와 원본 오류를 보존하고, process exit와 semantic verdict를 구분해 남깁니다. 미실행·연결 실패·잘못된 출력·감사 WARN/PASS를 서로 바꿔 기록하지 않습니다.
- 감사 범위와 완료 조건을 먼저 고정하고, 실행되지 않은 중간 번들은 더 만들지 않습니다. 기능·문서 단위는 내부 검증 단위로 나누되, 요청된 전체 사이클이 끝나기 전에는 턴을 끝내지 않습니다.

## 결과 해석과 남은 범위

이번에는 감사 실행기 개선 B-09만 PASS로 완료했습니다. 애플리케이션 회귀 결과 92/92와 Day 11 6/6은 기존 격리 DB 실행 증거이며, 이번 PowerShell 변경 후 애플리케이션 회귀를 새로 실행했다는 뜻은 아닙니다. 프로젝트 코드 잔여 B-05와 B-06은 별도 구현·테스트 작업으로 남아 있습니다. v6~v9 실패/미실행 이력은 v10의 PASS로 덮지 않았습니다.

## 지침 준수 인시던트

사용자가 제공한 AGENTS 지침에는 오류 시 원본 로그와 실패 상태를 보존하고 원인을 수정한 뒤 재실행할 것, 실행 증거가 있는 항목만 완료로 표시할 것, 전체 완료 조건 전에는 턴을 종료하지 말 것, 모든 원격 실행에 bundle/runner/endpoint/model/hash/process 상태/의미 판정을 append-only 증거로 남길 것이 명시돼 있습니다. Codex는 이 지침을 알고 있었지만, 작업 중 전체 시도 기록을 바로 모으지 않았고, 먼저 일반적인 완료 답변을 반환한 뒤 사용자가 로그 누락을 다시 지적하게 했습니다. 이는 지침 불이행입니다.

원인은 완료 판정을 Qwen PASS와 코드·로컬 검수 결과에만 맞추고, 실패 이력의 통합 기록과 사용자의 로그 요구까지 완료 체크리스트에 넣지 않은 것입니다. 조치는 이번 문서에 v4~v10 시도와 미실행 번들의 상태를 모으고, v10 실행에는 별도 semantic summary를 추가하며, 한·일 기록을 맞춘 것입니다. 다만 초기 v8 셸 출력은 별도 원본 로그가 아니어서 현재 남은 bundle/manifest와 세션 출력에 근거한 사후 기록입니다. 다음에는 각 시도를 시작할 때 실행 폴더에 입력·명령·상태를 남기고, 실패 즉시 원본 오류를 저장한 후 원인/수정/재실행 결과를 같은 기록에 연결해야 합니다.

## 저장소에서 추가 확인한 2026-11-13~14 오류·문제 전체 목록

아래는 현재 저장소의 audit run, 한·일 누적 증거, Antigravity 작업 로그, 로컬 화면검수 로그와 Codex queue 기록에서 추가 발견한 항목입니다. 상세 원본은 각 경로에 그대로 두고 이 표에는 원인·해결 여부를 연결합니다. 저장소 밖의 GPT 대화나 보존되지 않은 터미널 세션까지 전부 열람할 수 있다는 뜻은 아닙니다.

| 영역/원본 | 관찰된 오류·문제 | 확인된 원인 | 해결·현재 상태 |
|---|---|---|---|
| Day 10 출퇴근 동시성, Antigravity `task-46.log` 및 누적 증거 | CLOCK_IN/CLOCK_OUT 동시성 테스트 2건 실패. 별도 1차 실행에서 `No constructor found in void matching [java.lang.Long]` | `AttendanceMapper.xml`의 `lockStoreStaffForUpdate` select에 `resultType="java.lang.Long"` 누락 | 매퍼에 resultType 추가. 후속 격리 DB 3회, 총 6건 PASS로 기록. 증거 `docs/portfolio_audit_evidence_KO.md` Day 10 기록 및 `day10_isolated_regression_20261113_154029/` |
| Day 10 격리 DB 준비 | `break_id`/`attendance_break_id` 불일치, Windows JDBC URL의 `&` 전달 실패 | 구형 schema.sql 사용과 PowerShell/JDBC 인자 전달 처리 미흡 | 준비 스크립트를 `src/main/resources/db/schema.sql` rev.3으로 변경하고 URL 인자 전달을 보정. 새 격리 DB 3회/6건 PASS. Docker/Testcontainers는 별도로 BLOCKED |
| Day 10 최소 fixture 전체 회귀 `day10_isolated_20261113_155735...` | 79건 중 14건 실패(65 PASS) | 급여 테스트용 관계 fixture 부족으로 FK 오류, lock/performance_schema 전제 불일치, 로그인 시드 및 CSRF 흐름 불일치 | 상세 분류 `full_regression_triage_KO.md`/`_JP.md`. 전체 fixture 주입 후 13건 회복, 남은 `PerformanceSchemaDataLocksIntegrationTest`는 `kintai_db` 하드코딩이 격리 DB와 불일치. 테스트에서 `SELECT DATABASE()`를 쓰도록 수정한 뒤 새 DB `kintai_full_test_a65c3d18271f`에서 79/79 PASS. 이후 최신 전체 격리는 92/92 PASS |
| Day 10 추가 전체 회귀 `kintai_full_test_b769142733d1` | 79건 중 1건 실패: `PerformanceSchemaDataLocksIntegrationTest` | 테스트가 `OBJECT_SCHEMA='kintai_db'`를 하드코딩해 격리 DB에서 lock을 찾지 못함 | 테스트가 현재 DB명을 동적으로 읽게 수정. 후속 79/79 PASS 및 최신 92/92 PASS 증거 존재 |
| Day 10 CSRF 타깃 실행 `kintai_day10_test_08cd8d370aac` | 5건 중 `CsrfSecurityIntegrationTest` 1건 실패, 로그인 redirect 기대와 실제가 다름 | 대상 실행이 전체 인증 seed/fixture가 아닌 제한된 DB/프로파일을 사용 | 전체 fixture 회귀에서 CSRF/login 두 테스트 PASS 기록. 브라우저 fetch에서도 Cookie 원문 토큰을 우선 사용하도록 layout 수정. 별도 초기 최소 DB 결과는 실패 이력으로 보존 |
| Day 10 보안 재검사 | `task-106.log`: 잘못된 test FQN으로 “No tests found”; 후속 타깃 재실행은 Gradle wrapper lock 접근 거부 및 daemon 연결 실패 | 테스트 클래스 패키지 오기와 실행 환경의 Gradle 잠금/daemon 문제 | 잘못된 FQN 원문 보존. 전체 회귀에는 보안 테스트 결과가 있으나 당시 별도 타깃 재검사는 미실행으로 분류. A-05/A-06 잔여는 RTM R-10 `PARTIALLY_VERIFIED`로 유지 |
| Day 11 타깃 실행 `kintai_day11_test_1c5791670791` | `compileTestJava`에서 MockMvc 자동설정 패키지를 찾지 못함 | Spring Boot 4 모듈형 테스트 패키지와 구 패키지 import 불일치 | 현재 테스트는 `org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc` 사용, `spring-boot-starter-webmvc-test` 의존성 사용. 후속 Day 11 6/6 PASS |
| Day 11 타깃 실행 `kintai_day11_test_4973e0e61e96` | `AnnualPayrollScenarioIntegrationTest.java:269`에서 unreported `Exception` 컴파일 오류 | 테스트 호출 메서드의 checked exception 선언 누락 | 테스트 메서드에 `throws Exception` 추가. 후속 Day 11 6/6 PASS, 최신 전체 92/92 PASS |
| Qwen 사전검사 `20261113_072301_d39330bd` | 예상 입력 31,888 tokens가 90% 한도 22,118 초과, exit 2/INVALID | 누적 증거 문서 전체를 단일 입력으로 제출 | 이후 7,014 prompt tokens로 실행한 `20261113_072343_d39330bd`는 exit 0/SUCCESS. 다만 별도 bundle 상태만 판정 |
| Qwen 감사 `20261114_014406_cd7cd20d` | 생성 총 22,421 tokens가 22,118 한도를 초과해 INVALID/exit 2 | 응답 상한 도달 전에 큰 단일 입력을 충분히 분할·축약하지 않음 | 같은 입력의 다음 독립 실행 `20261114_014515_cd7cd20d`는 유효 응답 WARN 3건을 반환. 이후 실행기 수정과 v10 원격 재검수 PASS |
| Codex queue `failed/codex_test_remote_ok` | 실제 HTTP JSON을 byte[]로 변환하려다 `Cannot convert argument "bytes"` 오류 | `Invoke-WebRequest.Content`가 이미 string인 경우와 byte[]인 경우를 구분하지 않음 | 현재 `process_codex_queue.ps1`는 string을 그대로 사용하고 그 외만 UTF-8 decode. 후속 실제 원격 relay probe `codex_live_relay_probe_20261113_9d646ebc` SUCCESS/`REMOTE_OK`. 초기 실패 파일은 보존 |
| Codex queue negative tests | 잘못된 JSON, traversal ID, whitelist 밖 localhost endpoint 거부 | 의도된 보안·입력 검증 시험 | 오류 파일의 `JSON_PARSE_ERROR`/`SECURITY_VIOLATION`은 기대된 거부이며 기능 결함으로 집계하지 않음. `worker.error.log`는 0 bytes |
| 로컬 UI 검수 `local-review.log` | `/favicon.ico` 요청마다 `NoResourceFoundException`을 ERROR로 기록 10회; Hikari housekeeper thread starvation/clock leap WARN 2회 | favicon 정적 파일 없음은 확인됨. housekeeper 경고의 시스템 원인은 로그만으로 확정 불가 | favicon은 아이콘 제공 또는 예상 404의 ERROR 로깅 완화가 해결책이나 아직 적용하지 않음. housekeeper는 호스트 중단/부하/GC를 추가 계측해야 하며 원인·해결 미확정. `local-review-error.log`는 0 bytes |
| Antigravity 로그 무결성 | 과거 3회 동시성 테스트의 개별 원본 JUnit/log 일부를 찾지 못함. 재구성 bundle은 당시 SHA와 일치 | 당시 실행 로그·JUnit을 매 회차 별도 보존하지 않음 | 누적 증거에 재구성과 원본의 구분, 남은 미확인 사항을 기록. 향후 회차별 원본 JUnit/log 필수 저장 |

## 범위 한계

위 목록은 2026-11-13~14 저장소에서 발견한 원본 기록과 현재 대화의 실행을 합친 것입니다. 저장소 밖 GPT 대화의 원문이나 이미 삭제된/저장하지 않은 터미널 출력은 확인할 수 없으므로, 이를 “어제·오늘 GPT의 모든 오류를 완전히 회수했다”고 단정하지 않습니다. 확인 가능한 누락 증거는 `미확인`으로 남겼습니다.
