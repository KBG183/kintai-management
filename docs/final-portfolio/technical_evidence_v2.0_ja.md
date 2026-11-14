# Kintai 技術証跡 v2.0

**基準日:** 2026-11-14 (KST)　**判定:** Day 13のローカルMySQL 26.7検証範囲のみPASS

## 対象と構成

Kintaiは日本向けシフト・勤怠・月次給与アプリです。確認した流れは確定シフトのカレンダー表示、勤怠・休憩記録、月次給与計算、給与台帳Excel出力です。レビュー記録上の構成はSpring Boot 4.0.8、Java 17.0.12、MyBatis、Thymeleaf/JavaScript、MySQL、Apache POIです。詳細図は[アーキテクチャ](architecture_ja.md)を参照してください。

## 3役AI協働

1. **実装担当:** Antigravityが指示に従い実装とテスト証跡を準備。
2. **独立AIレビュー担当:** Hermesが凍結した証跡バンドルを、リモート`qwen27b:latest`でレビュー。監査は助言であり、実行テスト・人間の受入判断を代替しません。
3. **人間の決定者:** ユーザーが範囲、提案の採否、最終判断を決定。

最終Day 13監査先は `http://100.111.193.94:11434`。ローカルQwenへの代替やモデルのダウンロードはしていません。これはアプリの3層構造ではなく、監査・判断の役割分担です。

## 実測結果

| 項目 | 結果 | 主な証跡 |
|---|---|---|
| 分離デモDB | `kintai_demo_9f3f8487bc42`。2026年の確定シフト313件、勤怠313件。日本語名UTF-8確認 | `docs/audit_runs/day13_calendar_20261114/day13_completion_checklist.json` |
| カレンダー | 2026年11月のイベントを表示。11月2日は09:00〜18:00、休憩60分 | `docs/audit_runs/day13_calendar_20261114/ui_review_record.md` |
| 給与画面/API | 2026年1月のDRAFT総額は佐藤 花子 ¥141,700、鈴木 一郎 ¥164,332。画面と認証APIが一致 | `payroll_api_list.json`, `ui_review_record.md` |
| ブラウザーExcel | ダウンロード完了、5,092 bytes。月と2総額を検査。SHA-256 `ba127f65f193f8289c2b4e445efc09a06e1590e2230ed0b1ad603d839cfdcd98` | `excel_browser_download.json`, `payroll_excel_validation.json` |
| 全回帰 | 83件PASS、失敗・エラー・スキップ0、Gradle終了コード0 | `docs/audit_runs/isolated_all_20261114_081141_kintai_full_test_9f4b67f66b64/exit_summary.json` |
| Day 13 Qwen監査 | 15件PASS、WARN/FAIL 0。Run `20261113_234922_d69a9b63` | `docs/audit_runs/day13_calendar_20261114/qwen27b_day13_final/report.md` |

給与計算の代表的な根拠は、`LaborCalculationEngineTest`における13:00〜翌01:00勤務の深夜・時間外重複180分と、150%の総支給検証です。`PayrollCalculatorStatutoryRoundingTest`はHALF_UP境界（324.49→324、324.50→325）を検証します。実装は`AttendanceTimeCalculator.java`、`PayrollCalculator.java`です。認可の例は`StoreAuthorizationServicePayrollOwnershipTest`、`AttendanceApiSecurityTest`、行ロックSQLはattendance/payroll/shiftのMapper XMLにあります。代表例であり、全経路の検証を意味しません。

ブラウザー確認は、プロセス系譜から分離DBを使用していると確認した既存の8082アプリで行いました。8081は使用中だったため別起動に失敗し、元ログを保存しています。

## 요구사항 상태와 미검증 경계

- RTM R-15〜R-18: 로컬 MySQL 26.7 범위에서VERIFIED.
- R-11、R-12、R-14: PARTIALLY_VERIFIED。打刻API経路、一部の年間境界ケース、Day 11時点の最終ソース独立再レビューが未完了です。
- R-05: MySQL 8.4 Testcontainersは2027年1月にDEFERRED。8.4の検証結果は主張しません。
- 回帰83件のPASSは未実行の境界・経路まで通過したことを意味しません。本証跡は日本の労働法全体または本番環境への完全適合を認証しません。

데모는 반드시 새 분리 DB에서 실행하고 운영 DB를 사용하지 마십시오. 기준 기록: [완료 체크리스트](../audit_runs/day13_calendar_20261114/day13_completion_checklist.json), [RTM](../rtm_ledger.md), [인수인계](../handoff/20261114_0746_codex_day13_handoff.md).

