# 監査実行原本（audit_runs）

このディレクトリは、KINTAIの開発・テスト・独立監査で生成された**実行原本の保存場所**です。発表で最初に読む資料ではありません。

## 何が入っているか

| 種類 | 例 | 役割 |
|---|---|---|
| 全体回帰 | `isolated_all_20261117_165259_kintai_full_test_c9889478763f/` | 最新の98/98 PASS、JUnit XML、終了サマリー |
| ポートフォリオ監査 | `day13_calendar_20261114/final_portfolio_review/` | 要約・証跡・発表資料の文書監査 |
| ローカルAI監査 | `codex_20261114_112428_v10/` | B-09修正後の監査ランナー最終レポート |
| B-05/B-06監査 | `b05_b06_audit_20261114/` | YearMonth入力境界と給与詳細JSONの監査 |
| UI・デモ確認 | `day13_calendar_20261114/`、`codex_*` | カレンダー、給与画面、ブラウザExcel出力の確認 |
| 失敗・再試行履歴 | `test_fail_run/`、`preflight_*/`、旧日付フォルダ | 失敗原因、無効応答、再実行前の状態を保存 |

## 発表で参照する代表原本

- [最新全体回帰の集計](isolated_all_20261117_165259_kintai_full_test_c9889478763f/exit_summary.json)
- [最新ポートフォリオ監査](day13_calendar_20261114/final_portfolio_review/)
- [監査ランナー最終レポート](codex_20261114_112428_v10/hermes_report.md)
- [B-05/B-06監査レポート](b05_b06_20261114_c598266c/hermes_report.md)
- [デモ・ブラウザ確認](day13_calendar_20261114/)

## 読み方

- フォルダ名の日付・時刻は実行時点を示します。
- 同じ課題に複数フォルダがある場合、後続の実行が最新とは限りません。各フォルダの `run_manifest.json`、`exit_summary.json`、レポート本文、ハッシュを照合してください。
- 失敗やWARNのフォルダも削除せず、原因分析と再検証の履歴として残しています。
- 「PASS」はその実行の入力・環境・対象範囲にだけ適用されます。システム全体や未検証環境の保証ではありません。

最初に読む資料は[プロジェクト要約書](../final-portfolio/portfolio_summary_JA.md)、詳細確認は[日本語技術証跡書](../legacy_audit_reports/portfolio_audit_evidence_JA_cumulative_20261114.md)です。ここは必要なときに原本へ降りるための保管庫です。

