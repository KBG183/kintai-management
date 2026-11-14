# Kintai 技術証跡 v2.1

**基準日:** 2026-11-14 (KST、残存検証反映)　**判定:** Day 13のローカルMySQL 26.7検証範囲のみPASS

## 対象と構成

Kintaiは日本向けシフト・勤怠・月次給与アプリです。確認した流れは確定シフトのカレンダー表示、勤怠・休憩記録、月次給与計算、給与台帳Excel出力です。レビュー記録上の構成はSpring Boot 4.0.8、Java 17.0.12、MyBatis、Thymeleaf/JavaScript、MySQL、Apache POIです。詳細図は[アーキテクチャ](architecture_ja.md)を参照してください。

## AI監査と判断の役割

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
| Day 13チェックポイント回帰 | 83件PASS、失敗・エラー・スキップ0、Gradle終了コード0 | `docs/audit_runs/isolated_all_20261114_081141_kintai_full_test_9f4b67f66b64/exit_summary.json` |
| 残存修正後の最新全回帰 | 92件PASS、失敗・エラー・スキップ0、Gradle exit 0。分離DB `kintai_full_test_d4124a317ed0` | `docs/audit_runs/isolated_all_20261114_094427_kintai_full_test_d4124a317ed0/exit_summary.json` |
| Day 13 Qwen監査 | 15件PASS、WARN/FAIL 0。Run `20261113_234922_d69a9b63` | `docs/audit_runs/day13_calendar_20261114/qwen27b_day13_final/report.md` |

給与計算の代表的な根拠は、`LaborCalculationEngineTest`における13:00〜翌01:00勤務の深夜・時間外重複180分と、150%の総支給検証です。`PayrollCalculatorStatutoryRoundingTest`はHALF_UP境界（324.49→324、324.50→325）を検証します。実装は`AttendanceTimeCalculator.java`、`PayrollCalculator.java`です。認可の例は`StoreAuthorizationServicePayrollOwnershipTest`、`AttendanceApiSecurityTest`、行ロックSQLはattendance/payroll/shiftのMapper XMLにあります。代表例であり、全経路の検証を意味しません。

ブラウザー確認は、プロセス系譜から分離DBを使用していると確認した既存の8082アプリで行いました。8081は使用中だったため別起動に失敗し、元ログを保存しています。

## 요구사항 상태와 미검증 경계

- RTM R-15〜R-18: ローカルMySQL 26.7の範囲でVERIFIED。
- R-11、R-12、R-14: 記載したローカルMySQL 26.7範囲でVERIFIED。最新ソース監査はリモートQwen 27B Run `20261114_004900_46049b14`（12/12 PASS、WARN/FAIL 0）。全経路・全境界の網羅を意味しません。
- R-02: ユーザーが2026-11-14にスナップショット/Fail-Fast方針を採用し、統合テスト2件が最新全回帰でPASSしました。
- スナップショット後の文書監査履歴: `http://100.111.193.94:11434`への初回接続は`Unable to connect to the remote server`で失敗しました。その後、バンドルSHA `cd7cd20d12ba7771fb8ceb01dcd95181bfe39c924b8a595aa5e6fb91fdf649b8`への実行はexit 0でしたが、不正なレポートを生成しました。独立Qwen Run `20261114_014515_cd7cd20d`は具体的な指摘3件を返し、WARNです。Qwen Run `codex_20261114_111907_v7`では、設定された`think:false`がchat APIリクエストに渡されず、応答本文が空になる不具合を検出しました。設定値の転送と空応答検証を修正し、ローカル検証は`runner_validation_20261114_112300/`に保存しました。最終Qwen 27B独立監査Run `codex_20261114_112428_v10`はバンドルSHA `80311a3d1ff7aecb1cac5890e9519106b9ce6c0e557386163c85ce06ee0f9f0a`でPASSしました。報告書SHA `94f7f5bb0e263eec9adbb85400e4e0d254d905347a88ee5cf311f19b101030e8`、証拠`docs/audit_runs/codex_20261114_112428_v10/`。以前の10/10レビューは別の固定バンドルだけに適用します。監査ランナーは応答完了・context budget・verdict表を検証し、HTTPリモートを既定化、inference設定とprompt hashを記録します。exit 0のみで監査PASSとしません。
- B-04は完了です。B-05（YearMonth形式検証/HTTP 400テスト）とB-06（機微フィールドがJSONに含まれない回帰テスト）は次の実装サイクルに残っています。確認したDTOに機微情報の直接露出は見つかっていません。
- R-05: ユーザーの指示により現在の作業から除外。Docker/Podmanがなく、MySQL 8.4の検証結果はありません。予定されたレビュー時に再確認します。
- Day 12のCompose/Redis/クラウド作業は、ユーザーの指示により予定日まで除外しています。デプロイ済みとは主張しません。
- Day 13の83件PASSと後続の92件PASSはいずれも未実行の境界・経路まで通過したことを意味しません。本証跡は日本の労働法全体または本番環境への完全適合を認証しません。

데모는 반드시 새 분리 DB에서 실행하고 운영 DB를 사용하지 마십시오. 기준 기록: [완료 체크리스트](../audit_runs/day13_calendar_20261114/day13_completion_checklist.json), [RTM](../rtm_ledger.md), [인수인계](../handoff/20261114_0746_codex_day13_handoff.md).

