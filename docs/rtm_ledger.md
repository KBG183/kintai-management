# 要件トレーサビリティ・マトリクス（RTM）

KINTAIの要件、実装、テスト、監査証跡を対応付ける日本語版RTMです。状態は、実行証拠が確認できた範囲だけを示します。法令全体への適合や未実施環境の動作を意味しません。

## 最終確認範囲（2026-11-14）

- 対象DB: 新規隔離ローカルMySQL 26.7
- 最新全体回帰: **94/94 PASS**（失敗0・エラー0・スキップ0・Gradle exit 0）
- 独立監査: 指定リモートのQwen 27B（`qwen27b:latest`）
- DEFERRED: MySQL 8.4/Testcontainers、クラウドデプロイ、現行SRS外の法令・運用要件
- 過去の92件・83件・79件などは、その時点のチェックポイントとして原文ログに保存

## 要件と証拠の対応

| ID | 確認対象 | 状態 | 実装・検証の概要 | 主な証拠 |
|---|---|---|---|---|
| R-01 | 月次給与確定のトランザクション境界 | VERIFIED | 単一Connection・実トランザクション・再取得・更新を確認 | `PayrollService.java`、統合テスト |
| R-02 | 新規DRAFT競合時の業務方針 | VERIFIED | 新規DRAFT検知時は月次確定全体をロールバックする方針を採用し、2件の統合テストで確認 | `r02_business_policy.md`、隔離回帰 |
| R-03 | 給与確定SQLとDRAFT条件 | VERIFIED | 対象4クエリの状態条件、パラメータ、更新件数を確認 | `PayrollMapper.xml`、DDL照合 |
| R-04 | InnoDBデッドロックの再現 | VERIFIED | 逆順ロックによるError 1213と相互待機を原文ログで確認 | `docs/f04_evidence/show_engine_innodb_status_deadlock.txt` |
| R-05 | MySQL 8.4/Testcontainers | DEFERRED | Docker/Podman未導入のため現時点では実施せず、26.7の結果と混同しない | `docs/f04_evidence/`、2027年再検討 |
| R-06 | テストフィクスチャの独立性 | VERIFIED | 対象統合テストを3回連続実行し、安定性を確認 | Gradle/JUnit結果 |
| R-07 | 最終独立監査と報告形式 | VERIFIED | 指定項目を含む監査報告と最終判定を保存 | `docs/audit_runs/codex_20261114_112428_v10/` |
| R-08 | CLOCK_INの同時重複防止 | VERIFIED（MySQL 26.7範囲） | 隔離DB 3個で計6件を実行し、各競合で1件成功・1件拒否を確認 | `AttendanceServiceConcurrencyIntegrationTest`、`day10_isolated_regression_20261114_093404` |
| R-09 | CLOCK_OUTの同時競合 | VERIFIED（MySQL 26.7範囲） | 完了状態の保持と重複退勤拒否を同じ競合テストで確認 | `AttendanceServiceConcurrencyIntegrationTest` |
| R-10 | 打刻・認証境界の独立監査 | VERIFIED（限定範囲） | セッション本人情報、店舗所有権、入力境界を確認。WARN/FAILなし | Day 10 Qwen報告書 |
| R-11 | 12か月給与・契約有効期間 | VERIFIED（MySQL 26.7範囲） | 12か月分の勤務、時給変更境界、実API経路を期待値と比較 | `AnnualPayrollScenarioIntegrationTest` |
| R-12 | 深夜・残業・分割勤務の境界 | VERIFIED（MySQL 26.7範囲） | 4時間+5時間、深夜と残業の重複60分、休憩、月末、HALF_UPを確認 | `LaborCalculationEngineTest`、`SplitShiftOvertimeDefectTest` |
| R-13 | 隔離実行と回帰証拠の保存 | VERIFIED | DB名、JUnit XML、終了コード、マニフェスト、ハッシュを保存 | `isolated_all_20261114_114819_kintai_full_test_8a75679f5b1e` |
| R-14 | 独立監査と文書の整合性 | VERIFIED | RTM、日韓証跡、引継ぎ、監査結果の数値と状態を照合 | `preflight_*`、引継ぎ文書 |
| R-15 | 隔離デモDBと年間データ | VERIFIED | 2026年のシフト313件・勤怠313件とDB識別子・UTF-8を確認 | `docs/audit_runs/day13_calendar_20261114/` |
| R-16 | カレンダーAPIと画面表示 | VERIFIED | DTOのフィールド、月間イベント、詳細モーダルをブラウザで確認 | `CalendarEventDto.java`、UI記録 |
| R-17 | 給与画面とExcel出力 | VERIFIED（MySQL 26.7範囲） | ブラウザのダウンロード完了、XLSXのOOXML・月・金額を確認 | `day13_calendar_20261114/excel_browser_download.json` |
| R-18 | Day 13独立監査と証跡追跡 | VERIFIED（限定範囲） | 更新済みバンドル、UI証拠、15件の監査判定を照合 | `day13_calendar_20261114/qwen27b_day13_final/` |
| R-19 | 最終ポートフォリオ一式 | VERIFIED | 日韓要約、技術証跡、デモ手順、相対リンク、最終監査を確認 | `docs/final-portfolio/` |

## 状態の読み方

- **VERIFIED**: 記載されたテスト・監査・環境の範囲で実行証拠を確認済み
- **DEFERRED**: 実施対象から分離しており、完了とは扱わない
- 過去ログにあるBLOCKED、PARTIALLY_VERIFIED、旧テスト件数は履歴であり、最新状態を上書きしない
- 94/94 PASSは、対象テスト範囲の結果であり、UI全体・本番環境・法令全体の保証ではない

詳細な時系列、入力ハッシュ、監査レポート原文は[日本語技術証跡書](legacy_audit_reports/portfolio_audit_evidence_JA_cumulative_20261114.md)と[監査実行原本一覧](audit_runs/README.md)を参照してください。

韓国語の旧RTMはGitHub公開資料から除外し、ローカル作業用の保管ファイルとしてのみ保持します。
