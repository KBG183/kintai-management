# Kintai Final Portfolio (第1次プロジェクト締め・最終版)

## 成果物

- [**役員・面接官報告用 最終要約書（日本語）**](portfolio_summary_JA.md): Day 1〜13、B-05/B-06、最終検証までの主要事例要約
- [アーキテクチャ](architecture_ja.md) · [ローカルデモ手順](demo_guide_ja.md)
- [動画デモ進行台本](video_demo_script_JA.md) · [動画デモ準備エラー台帳](video_demo_error_ledger_JA.md)
- [日本語累積技術証跡書](../legacy_audit_reports/portfolio_audit_evidence_JA_cumulative_20261114.md) · [RTM](../rtm_ledger.md)

## 最終締め判定 (2026-11-14)

最新の全回帰は分離ローカル MySQL 26.7（`kintai_full_test_8a75679f5b1e`）で **94/94 PASS**（失敗 0、エラー 0、スキップ 0、Gradle exit 0）です。Day 13 時点の83件および残存検証時点の92件は過去のチェックポイントとして保持しています。

* **完了範囲**: Day 1-11、Day 13 機能・UI・ポートフォリオおよび残存バックログ `B-04`、`B-05`（`YearMonth` 入力の 400 検証）、`B-06`（給与詳細 JSON 機微フィールド除外テスト）、`B-09`（リモート監査ランナー修正）すべて完了および `main` ブランチマージ完了。
* **除外（`DEFERRED`）範囲**: `Day 12`（クラウドデプロイ）および `R-05`（MySQL 8.4 Testcontainers）はユーザーの指示により来年再検討へ分離しました。（`B-01`, `B-07` は共通パイプライン改善バックログ待機）
* **法令範囲の制約**: 週40時間、法定休日、月60時間超の時間外労働、税務・社会保険計算は現行 SRS の保証範囲外です。
