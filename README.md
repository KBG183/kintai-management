# KINTAI — 勤怠・シフト・給与管理システム

日本の店舗・アルバイト現場向けに、シフト提出、出退勤打刻、勤怠修正申請、給与計算、Excel台帳出力を一つの業務フローとして実装したWebアプリケーションです。

> このリポジトリで確認できる動作・計算・検証結果は、記録されたテストと監査の範囲に限ります。日本の労働法全体への適合、税務・社会保険計算、本番運用条件を保証するものではありません。

## まず見るもの

| 資料 | 内容 |
|---|---|
| [デモ動画](https://youtu.be/LepOoQU8LgI) | スタッフの打刻からオーナーの給与確定・Excel出力まで |
| [20枚プレゼンテーション](docs/presentation-prototype/index.html) | プロジェクトの機能、問題解決、検証過程 |
| [プロジェクト要約書](docs/final-portfolio/portfolio_summary_JA.md) | 主要な成果と制約を短く確認 |
| [技術証跡書](docs/legacy_audit_reports/portfolio_audit_evidence_JA_cumulative_20261114.md) | 実装・監査・修正・再検証の記録 |
| [デモ操作ガイド](docs/final-portfolio/demo_guide_ja.md) | ローカルでの確認手順 |

## プロジェクトの業務フロー

### スタッフ

- シフトの確認
- 出勤・休憩開始・休憩終了・退勤の打刻
- 勤怠修正申請
- 自分の勤務実績と給与詳細の確認

### 店舗オーナー（`ROLE_OWNER`）

- シフトの登録・承認
- 勤怠修正申請の確認
- 勤怠の審査
- 月次給与の計算・確定
- Excel給与台帳のダウンロード

本プロジェクトで中心となる管理権限は、スキーマと認証実装で確認できる `ROLE_OWNER` です。独立した `ROLE_MANAGER` アカウントの検証済み運用を前提にはしていません。

## 主な実装範囲

### シフト・勤怠

- 月間カレンダーによるシフト確認
- 4状態の打刻管理（CLOCK_IN / BREAK_START / BREAK_END / CLOCK_OUT）
- 勤怠修正申請と承認フロー
- セッションの本人情報と店舗所有権に基づく認可
- 同一スタッフの競合打刻に対するDB行ロック

### 給与計算

- 分単位の勤務時間集計
- 22:00〜翌05:00の深夜時間帯
- 1日8時間超の時間外勤務
- 深夜と時間外が重なる区間
- 時給変更、日付変更、休憩、月末などの境界条件
- `BigDecimal` による金額計算
- Apache POIによる `.xlsx` 出力

労働基準法第37条に関係する対象ケースを実装・テストしていますが、週40時間、法定休日、月60時間超の時間外労働、税務・社会保険計算などは現行SRSの保証範囲外です。

## 技術構成

| 分類 | 技術 |
|---|---|
| Backend | Java 17 / Spring Boot 4.0.8 |
| Web | Thymeleaf / Spring Security |
| Data access | MyBatis |
| Database | MySQL / InnoDB |
| Excel | Apache POI |
| Build | Gradle |
| Test | JUnit / Gradle Test |

## アーキテクチャ

```
ブラウザ
  ↓
Thymeleaf / Spring Security
  ↓
Controller
  ↓
Service・トランザクション
  ↓
MyBatis
  ↓
MySQL（InnoDB）
  ↓
Apache POIによるExcel出力
```

主要テーブルは次のとおりです。

- `member`
- `store`
- `store_staff`
- `store_staff_contract_history`
- `shift_schedule`
- `attendance`
- `attendance_break`
- `attendance_mod_request`
- `payroll`
- `payroll_daily_detail`

## このプロジェクトで確認した技術課題

### デッドロック

給与一括確定と個別再計算でロック取得順序が逆転し、MySQL Error 1213が発生するケースを再現しました。親行を先に一定順序でロックする方式に変更し、再現テストと隔離DB回帰で確認しました。

### IDORと競合打刻

クライアントが送信したスタッフIDを権限判断に使わず、ログインセッションの本人情報と店舗所有権を確認する方式に変更しました。同一スタッフの同時打刻はDB行ロックで直列化し、記録した競合テストの範囲で1件成功・1件拒否を確認しました。

### 給与計算の境界条件

4時間と5時間に分かれた勤務、深夜と時間外が重なる60分、時給変更日、日付変更、休憩控除を独立した期待値と比較しました。

## 検証結果

新しい隔離DBで実行した最新の全体回帰は、記録上 **94/94 PASS** でした。

- 失敗: 0
- エラー: 0
- スキップ: 0
- 対象: 記録されたJUnit・統合テストの範囲
- 対象外: 未実施の本番環境、MySQL 8.4、未定義の法令・運用要件

94/94 PASSは、システム全体の無条件な品質保証や、ローカルAI監査の代替を意味しません。

## ローカルで起動する

### 前提条件

- JDK 17
- ローカルMySQL
- 検証記録で使用したMySQLはローカルの26.7.0 Innovation
- MySQL 8.4 Testcontainersは未検証

### 分離デモDBを準備する

運用DBを使わず、デモ用の新しいDBを作成します。

```powershell
$env:KINTAI_DB_PASSWORD = "<ローカルMySQLパスワード>"
.\scripts\prepare_local_demo_db.ps1
```

スクリプトが出力するDB接続設定を使ってアプリを起動してください。詳しい画面操作と期待結果は、[日本語デモガイド](docs/final-portfolio/demo_guide_ja.md)を参照してください。

## 検証資料

- [SRS](kintai_requirements_specification.md)
- [日本語プロジェクト要約](docs/final-portfolio/portfolio_summary_JA.md)
- [日本語技術証跡書](docs/legacy_audit_reports/portfolio_audit_evidence_JA_cumulative_20261114.md)
- [RTM（日本語）](docs/rtm_ledger.md)
- [監査・テスト原本](docs/audit_runs)
- [監査原本の読み方](docs/audit_runs/README.md)
- [プレゼンテーション](docs/presentation-prototype/index.html)

## 資料の使い分け

1. **要約本**: プロジェクトの目的、成果、制約を最初に読む資料です。
2. **技術証跡書**: 実装・監査・修正・再検証の時系列と判定を確認する詳細資料です。
3. **RTM**: 要件ごとに実装・テスト・監査証拠を追跡する一覧です。
4. **audit_runs**: 必要なときだけ開く実行原本の保管庫です。成功ログだけでなく、失敗・WARN・再試行履歴も保存しています。

このREADMEはプロジェクトの入口です。個々の数値・判定・未検証範囲は、リンク先のテスト結果と技術証跡書で確認してください。
