# R-02 policy acceptance and final scope audit

Confirm the explicit owner decision was transcribed accurately, R-02 policy matches implementation/test evidence, and R-05 / Day12 are excluded from current work per latest user direction. Verify Korean/Japanese evidence, RTM, checklist, handoff remain aligned.

## RTM R-02/R-05

| **R-02** | 최종 재조회 이후 신규 DRAFT 경쟁 조건 재현 및 격리 검증과 업무 정책 채택 | T1/T2 경쟁의 기술적 격리 실측, 경계 시 롤백 정책 문서화 및 사용자 채택 결정 기록 | 신규 DRAFT의 자동 확정 방지 및 fail-fast 전체 롤백 실측, 업무 정책 승인 상태 추적 | JUnit XML, DB 상태 증거, `docs/f04_evidence/r02_business_policy.md` | `PayrollService.java`, `PayrollMapper.xml`, `PayrollMonthlyConfirmPostQueryRaceConditionIntegrationTest.java` | **VERIFIED (기술 검증 및 정책 채택)** | 사용자께서 2026-11-14 스냅샷 고정 및 신규 DRAFT 경계 감지 시 전체 롤백 정책을 채택했습니다. 최신 전체 회귀에서 R-02 통합 테스트 2/2 PASS, 실패·오류 0. 격리 DB `kintai_full_test_d4124a317ed0`; 운영 DB 미사용. |
| **R-05** | MySQL 8.4 LTS 최종 검증 및 26.7 분리 | 로컬 `MySQL 26.7.0 Innovation` 결과와 `MySQL 8.4 LTS` 결과를 혼합하지 않고 각각 분리 기록 | `SELECT VERSION()` 실측치 명시 및 8.4 LTS 전용 검증 분리 | DB 버전 출력, EXPLAIN 결과 분리 파일 | `docs/f04_evidence/` | **DEFERRED (2027-01)** | MySQL 8.4/Testcontainers는 사용자 지시에 따라 현재 작업 범위에서 제외. Docker/Podman 부재로 미실행 상태를 보존하고 예정된 재검토일에 다시 판단 |

## Adopted R-02 policy

# R-02 신규 DRAFT 경합에 대한 업무 도메인 정책 명세서 (Business Policy on Late DRAFT)

## 1. 업무적 배경 및 결정

- **업무 정책 채택 승인:** 사용자께서 2026-11-14 본 문서의 스냅샷 고정 격리 및 신규 DRAFT 감지 시 전체 롤백 정책을 채택하기로 결정했습니다. 이 기록은 업무 정책 결정의 근거이며, 구현 검증 결과와는 별도로 관리합니다.

## 2. 업무적 배경
- **시나리오**: 점주가 '2026년 10월분 월간 급여 일괄 확정(Batch Confirm)' 버튼을 누르는 순간(T1), 시스템은 해당 월에 존재하는 DRAFT 급여 대상 직원들(예: staff1, staff2)을 선별하고 잠금을 획득합니다.
- **경합 발생**: 확정 처리 도중 다른 세션이나 직원이 뒤늦게 10월분 신규 출퇴근 기록을 생성하거나 DRAFT 급여(staff3)를 삽입했습니다(T2).
- **결정된 정책**: 뒤늦게 생성된 staff3의 DRAFT가 처리 중 재조회에 나타나면 월간 확정 전체를 롤백하고, 점주가 재확인 후 다시 실행합니다.

---

## 3. 채택된 일본 실무 근태·급여 비즈니스 정책 (Business Policy)

### [채택 정책: 스냅샷 고정 격리 및 경계 위반 시 트랜잭션 안전 롤백 2중 정책]

1. **원칙 1 (스냅샷 격리 원칙 - Deterministic Batch Scope)**:
   - 월간 일괄 확정은 **"확정 작업 시작 시점에 선별되어 부모 락(`store_staff`)이 정상적으로 확보된 대상 집합"**만을 원자적으로 CONFIRMED 전환합니다.
   - 확정 작업 착수 이후 뒤늦게 들어온 신규 직원의 DRAFT는 이번 확정 대상에 포함되지 않고 **`DRAFT` 상태 그대로 안전하게 남아있어야 합니다.** (조용히 자동 확정되는 오염 원천 방지)

2. **원칙 2 (부모 락 미확보 신규 DRAFT 감지 시 트랜잭션 롤백 - Fail-Fast Safety Guard)**:
   - 만약 Step 1(직원 선별)과 Step 2(부모 락 획득) 사이에 새로운 직원의 DRAFT가 생성되어 급여 재조회 Step 3에 걸려들었을 경우:
   - 해당 직원은 부모 락(`store_staff`)을 선점하지 않은 상태이므로, 다른 개별 계산 트랜잭션과 데드락이나 데이터 불일치를 일으킬 위험이 있습니다.
   - 따라서 현 시스템은 `!draftStaffIds.contains(p.getStoreStaffId())`를 감지하여 **즉시 `IllegalStateException`을 던지고 월간 확정 전체를 안전하게 롤백(Aborting for Consistency)**하도록 설계되었습니다.
   - 점주는 "새로운 급여가 생성되었으므로 재확인 후 다시 확정하라"는 안내를 받게 됩니다.

3. **결론 및 증거 연계**:
   - [`PayrollMonthlyConfirmPostQueryRaceConditionIntegrationTest.java`](file:///c:/dev/kintai/src/test/java/com/example/kintai/domain/payroll/service/PayrollMonthlyConfirmPostQueryRaceConditionIntegrationTest.java)의 `testLateDraftCreation_IsolationRisk` 테스트는 위 정책에 따라:
     - 확정 작업 이후 생성된 신규 급여가 `CONFIRMED`로 잘못 오염되지 않고 `DRAFT` 상태로 완벽히 격리 보존됨을 실측 증명했습니다.


## 5. 승인 및 검증 상태

- 업무 정책 채택: 사용자 승인 완료 (2026-11-14).
- 기술 검증: 격리 DB에서 PayrollMonthlyConfirmPostQueryRaceConditionIntegrationTest 실행 완료. 최신 전체 회귀 JUnit에서 이 클래스 2건 PASS, 실패·오류 0이며, 실행 증거는 docs/audit_runs/isolated_all_20261114_094427_kintai_full_test_d4124a317ed0/입니다.
- 승인 결정은 정책 문서와 RTM에 반영했습니다. 운영 DB는 사용하지 않았습니다.


## Checklist

{
  "work_date": "2026-11-14",
  "objective": "Close feasible pre-Day13 residual verification gaps; explicitly exclude R-05 and Day12 until scheduled review per user direction.",
  "items": [
    {
      "id": "R-08/R-09",
      "task": "Fail-closed isolation guard and 3 fresh local MySQL concurrency runs",
      "status": "VERIFIED"
    },
    {
      "id": "R-10",
      "task": "Re-run attendance security and input-boundary checks; remote Qwen 27B audit exact final source",
      "status": "VERIFIED"
    },
    {
      "id": "R-11",
      "task": "Improve annual scenario distribution and verify service/API-path scope with independent totals",
      "status": "VERIFIED"
    },
    {
      "id": "R-12",
      "task": "Add/execute split-shift, month-boundary and rounding boundary integration checks",
      "status": "VERIFIED"
    },
    {
      "id": "R-14",
      "task": "Audit final frozen source and reconcile Korean/Japanese evidence, RTM, handoff",
      "status": "VERIFIED_DOCUMENT_SCOPE"
    },
    {
      "id": "R-05",
      "task": "Run MySQL 8.4 Testcontainers separately if Docker is available; otherwise preserve exact blocker",
      "status": "EXCLUDED_BY_USER_UNTIL_SCHEDULED_REVIEW"
    },
    {
      "id": "R-02",
      "task": "Resolve documented business-policy acceptance status without silently claiming user approval",
      "status": "ACCEPTED_BY_USER_AND_VERIFIED"
    },
    {
      "id": "Day12",
      "task": "Review deferred Compose/Redis/cloud scope; no external deployment without separate explicit authorization",
      "status": "EXCLUDED_BY_USER_UNTIL_SCHEDULED_REVIEW"
    }
  ],
  "database_policy": "Use only newly generated allowlisted isolated test databases; never kintai_db; do not delete retained databases.",
  "audit_policy": "Remote Qwen only at http://100.111.193.94:11434 with qwen27b:latest; no local fallback or downloads.",
  "evidence": {
    "targeted_payroll_run": "isolated_day11_20261114_094349_kintai_day11_test_01f4cb1439e6",
    "full_regression_run": "isolated_all_20261114_094427_kintai_full_test_d4124a317ed0",
    "full_regression": "92 PASS, 0 failures, 0 errors, 0 skipped",
    "qwen_run": "20261114_004900_46049b14",
    "qwen_findings": "12 PASS, 0 WARN, 0 FAIL",
    "qwen_endpoint": "http://100.111.193.94:11434",
    "qwen_model": "qwen27b:latest",
    "mysql84_probe": "docs/audit_runs/prior_residuals_20261114/mysql84_runtime_probe.log",
    "final_document_audit": {
      "run_id": "20261114_005527_76dc1c9f",
      "findings": "10 PASS, 0 WARN, 0 FAIL",
      "bundle_sha256": "76dc1c9f5b3750c9b38d0777bd302893d4ea4dbe3783a0b8ef107edf37312d93",
      "report_sha256": "dee296cda885e4bdc8208c9145ab61757d6a5323ecd6f7c9b21016b2cfc6b124",
      "first_audit_warnings_corrected": [
        "R-14 status synchronized",
        "historic 83-test Day13 checkpoint distinguished from latest 92-test residual regression"
      ]
    },
    "r02_business_policy_acceptance": {
      "decision": "ACCEPTED",
      "decision_date": "2026-11-14",
      "policy": "Snapshot scope; late DRAFT remains DRAFT; abort and rollback entire batch if a late DRAFT is detected",
      "verification_database": "kintai_full_test_d4124a317ed0",
      "test_class": "PayrollMonthlyConfirmPostQueryRaceConditionIntegrationTest",
      "tests": "2 PASS, 0 failures, 0 errors"
    }
  }
}

## Korean evidence latest section

### 잔여 검증 최종 갱신 (2026-11-14 KST)

- RTM R-08/R-09/R-10/R-11/R-12/R-14는 이 날짜 최종 증거 기준으로 VERIFIED입니다. 이전 날짜의 `PARTIALLY_VERIFIED` 및 테스트 개수는 당시 이력으로 보존하며, 현재 상태는 `docs/rtm_ledger.md`를 따릅니다.
- 급여 통합 경계 테스트에서 20:00~다음 날 05:00 근무(총 540분), 야간 420분, 일일 연장 60분을 구성했습니다. 급여에 야간 25%와 연장 25%가 각각 가산되어 겹친 시간의 합산 50% 프리미엄이 반영되는 것을 확인했습니다. 야간 휴게 차감은 `LaborCalculationEngineTest`의 야간 시간대 휴게 사례로 검증했습니다.
- 새 격리 DB `kintai_day11_test_01f4cb1439e6`에서 `AnnualPayrollScenarioIntegrationTest` 6/6 PASS. 이후 전체 회귀는 새 격리 DB `kintai_full_test_d4124a317ed0`에서 92/92 PASS, 실패·오류·스킵 0, Gradle exit 0입니다. 운영 DB `kintai_db`에는 연결하거나 데이터를 변경하지 않았습니다.
- 수정 뒤 원격 독립 검수는 지정 주소 `http://100.111.193.94:11434`, `qwen27b:latest` (27.3B, IQ3_M)에서 실행했습니다. Run `20261114_004900_46049b14`; 12 findings PASS, WARN/FAIL 0. 입력 SHA-256 `46049b14578b32462a6f105966643d658919d5ed658faaf885796e2e1cd2c4b8`; 결과 SHA-256 `b1ae1c9d7844d92b6ab4b47bf5e5c66dbb0f1f3f916549688e3c83ea56798f10`. 실행 증거는 `docs/audit_runs/preflight_1e3b8fba040b4197aed07afbde9334f2/hermes/`입니다.
- R-05 MySQL 8.4/Testcontainers는 Docker/Podman 실행 파일이 없어 실행되지 않았고 `BLOCKED`로 남깁니다. 소프트웨어 설치·다운로드를 하지 않았습니다. 2027년 1월 재검토 범위입니다.
- R-02는 기술 검증은 완료했지만 `r02_business_policy.md`의 업무정책 채택은 사용자 결정 전이므로 승인 완료로 기록하지 않았습니다. Day12의 외부 AWS 배포/클라우드 변경도 이번 로컬 검수에 포함하거나 실행하지 않았습니다.
- 체크리스트 및 실행 요약: `docs/audit_runs/prior_residuals_20261114/completion_checklist.json`; 인수인계: `docs/handoff/20261114_residuals_handoff.md`.

- 최종 문서 독립 검수: Qwen Run `20261114_005527_76dc1c9f`, 10/10 PASS, WARN/FAIL 0. 최초 문서 감사에서 나온 R-14 상태 불일치와 Day 13 83건 체크포인트 표기 모호성을 수정하고 재검수했습니다. 번들 SHA-256 `76dc1c9f5b3750c9b38d0777bd302893d4ea4dbe3783a0b8ef107edf37312d93`; 보고서 SHA-256 `dee296cda885e4bdc8208c9145ab61757d6a5323ecd6f7c9b21016b2cfc6b124`. 상세: `docs/audit_runs/preflight_b53be98f3682498f9de6243735ce89d2/hermes/`.

### R-02 업무 정책 채택 결정 (2026-11-14)

- 사용자께서 스냅샷 고정 범위 및 신규 DRAFT 감지 시 일괄 확정 전체 롤백 정책을 채택했습니다. 이를 RTM과 정책 문서에 반영했습니다.
- 구현은 변경하지 않았습니다. 최신 격리 전체 회귀의 `PayrollMonthlyConfirmPostQueryRaceConditionIntegrationTest` 2건 PASS로 기술 동작을 확인했습니다. 전체 실행 증거: ``docs/audit_runs/isolated_all_20261114_094427_kintai_full_test_d4124a317ed0/``.

docs/portfolio_audit_evidence_KO.md

## Japanese evidence latest section

- 新規隔離DB `kintai_day11_test_01f4cb1439e6`で`AnnualPayrollScenarioIntegrationTest`は6/6 PASS。その後、新規隔離DB `kintai_full_test_d4124a317ed0`で全回帰92/92 PASS、失敗・エラー・スキップ0、Gradle exit 0です。運用DB `kintai_db`には接続せず、データ変更もしていません。
- 修正後のリモート独立監査は指定先`http://100.111.193.94:11434`、`qwen27b:latest`（27.3B、IQ3_M）で実行しました。Run `20261114_004900_46049b14`、12 findings PASS、WARN/FAIL 0。入力SHA-256 `46049b14578b32462a6f105966643d658919d5ed658faaf885796e2e1cd2c4b8`、結果SHA-256 `b1ae1c9d7844d92b6ab4b47bf5e5c66dbb0f1f3f916549688e3c83ea56798f10`。実行証拠は`docs/audit_runs/preflight_1e3b8fba040b4197aed07afbde9334f2/hermes/`です。
- R-05 MySQL 8.4/TestcontainersはDocker/Podman実行ファイルがないため実行できず、`BLOCKED`のままです。ソフトウェアのインストール・ダウンロードは行っていません。2027年1月の再検討対象です。
- R-02は技術検証済みですが、`r02_business_policy.md`に記載した業務方針の採用はユーザー決定前のため、承認済みとは記録していません。Day12のAWSデプロイ・クラウド変更も今回のローカル検証に含めず、実行していません。
- チェックリストと実行サマリー: `docs/audit_runs/prior_residuals_20261114/completion_checklist.json`; 引継ぎ: `docs/handoff/20261114_residuals_handoff.md`。

- 最終文書独立監査: Qwen Run `20261114_005527_76dc1c9f`、10/10 PASS、WARN/FAIL 0。初回文書監査のR-14状態不一致とDay 13の83件チェックポイント表記の曖昧さを修正し、再監査しました。バンドルSHA-256 `76dc1c9f5b3750c9b38d0777bd302893d4ea4dbe3783a0b8ef107edf37312d93`、報告書SHA-256 `dee296cda885e4bdc8208c9145ab61757d6a5323ecd6f7c9b21016b2cfc6b124`。詳細: `docs/audit_runs/preflight_b53be98f3682498f9de6243735ce89d2/hermes/`。

### R-02 業務方針の採用決定 (2026-11-14)

- ユーザーがスナップショット固定範囲と、新規DRAFT検知時に月次一括確定全体をロールバックする方針を採用しました。RTMと方針文書に反映しました。
- 実装変更はありません。最新の隔離全回帰でPayrollMonthlyConfirmPostQueryRaceConditionIntegrationTest 2件PASSにより、技術動作を確認済みです。証拠: docs/audit_runs/isolated_all_20261114_094427_kintai_full_test_d4124a317ed0/。

## Handoff relevant section

- Full regression after the last source change: fresh isolated DB `kintai_full_test_d4124a317ed0`; 92 tests PASS, 0 failures/errors/skips, Gradle exit 0. Evidence: `docs/audit_runs/isolated_all_20261114_094427_kintai_full_test_d4124a317ed0/`.
- Final remote independent review used only `http://100.111.193.94:11434`, model `qwen27b:latest` (27.3B, IQ3_M). Run `20261114_004900_46049b14`: 12/12 PASS, WARN/FAIL 0. Bundle SHA-256 `46049b14578b32462a6f105966643d658919d5ed658faaf885796e2e1cd2c4b8`; report SHA-256 `b1ae1c9d7844d92b6ab4b47bf5e5c66dbb0f1f3f916549688e3c83ea56798f10`. Evidence folder: `docs/audit_runs/preflight_1e3b8fba040b4197aed07afbde9334f2/hermes/`.
- R-14: Korean/Japanese evidence, RTM and this handoff have been reconciled to the above evidence. The first independent document audit Run `20261114_005138_3485208b` found two WARNs (stale RTM R-14 state and an ambiguous historic Day 13 83-test heading). Both were corrected. Final frozen-document audit Run `20261114_005527_76dc1c9f` returned 10/10 PASS, WARN/FAIL 0. Bundle SHA-256 `76dc1c9f5b3750c9b38d0777bd302893d4ea4dbe3783a0b8ef107edf37312d93`; report SHA-256 `dee296cda885e4bdc8208c9145ab61757d6a5323ecd6f7c9b21016b2cfc6b124`.

## Excluded items and accepted policy

- R-02: the user accepted the documented snapshot/fail-fast policy on 2026-11-14. The RTM and policy record now show it as accepted; the implementation was unchanged. The isolated full regression includes 2/2 passing tests for `PayrollMonthlyConfirmPostQueryRaceConditionIntegrationTest`.
- R-05: excluded from current work by the user. The Docker/Podman absence probe remains preserved at `docs/audit_runs/prior_residuals_20261114/mysql84_runtime_probe.log`; no install/download was performed. Revisit at its scheduled review.
- Day 12 is excluded until its scheduled work date, per the user. No cloud deployment was performed.
- No operating DB was used. Test runs used newly created, allowlisted isolated databases. No DBs were deleted.

## Tracking

- RTM: `docs/rtm_ledger.md`
- Residual checklist: `docs/audit_runs/prior_residuals_20261114/completion_checklist.json`
- Korean evidence: `docs/portfolio_audit_evidence_KO.md`
- Japanese evidence: `docs/portfolio_audit_evidence.md`

## R-02 integration test result

<testsuite name="F-04: confirmMonthlyPayroll 트랜잭션 동일성 및 최종 재조회 직후 신규 DRAFT 경쟁 조건 정밀 실측" tests="2" skipped="0" failures="0" errors="0" timestamp="2026-11-14T00:44:55.735Z" hostname="BK" time="0.072">

## Full regression result

{
  "gradle_exit_code": 0,
  "junit_xml_count": 37,
  "tests": 92,
  "failures": 0,
  "errors": 0,
  "skipped": 0,
  "junit_parse_errors": [],
  "result": "PASS"
}
