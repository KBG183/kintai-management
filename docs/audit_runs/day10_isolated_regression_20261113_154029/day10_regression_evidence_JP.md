# Day 10 分離 MySQL 反復回帰証跡

## 判定

- 対象テスト: `com.example.kintai.domain.attendance.service.AttendanceServiceConcurrencyIntegrationTest`
- 実行環境: Windows 11、Java 17.0.12、Gradle 9.8.1、MySQL 26.7.0
- スキーマ: `src/main/resources/db/schema.sql` rev.3からデータベース選択行だけを除外して適用
- 本番DB `kintai_db`: 接続なし
- 反復実行: 3回
- 総テスト数: 6件
- 成功: 6件
- 失敗: 0件
- エラー: 0件
- 最終判定: `LOCAL_MYSQL_REPEAT_VERIFIED`
- MySQL 8.4 Testcontainers: `BLOCKED`（Docker環境未確保）
- Qwen独立監査: 今回の回帰実行では未実施

## 実行結果

| 回 | 分離DB | テスト | 失敗 | エラー | 終了コード | 判定 |
|---:|---|---:|---:|---:|---:|---|
| 1 | `kintai_day10_test_858b35511a0d` | 2 | 0 | 0 | 0 | SUCCESS |
| 2 | `kintai_day10_test_e6c1a49494b7` | 2 | 0 | 0 | 0 | SUCCESS |
| 3 | `kintai_day10_test_934cea2c347d` | 2 | 0 | 0 | 0 | SUCCESS |

## SHA-256証跡

| 回 | JUnit XML SHA-256 | 実行ログ SHA-256 |
|---:|---|---|
| 1 | `ed9848e9b84c1bc0ff740cf92121d39928da74cc844ce6d800b5c48e5aac5dda` | `cb69a27ec94667e04347190ee0fc28809e4e90d21522d68a3f76c28446339789` |
| 2 | `6874e4a0db9e86a81ac47af2257fea567fec8ff770aae336d9cd4952ff914205` | `9239039f2a78f89ff0a30b9f6abdf5f0ea0e814985c0b49d91455ea86467104e` |
| 3 | `b550c5bd1a5a57b70d5f739364865ebdabb5c83a0615aaaa7eda58b451f24c33` | `04577319fa9c7050d06178cfc1d8469403d354eeb8cdc8af527d4526116c3bdc` |

## 失敗履歴と補正

1. 初回の分離実行は、旧 `schema.sql` の `break_id` とMapperの `attendance_break_id` の不一致で失敗した。
2. JDBC URLの `&` オプション伝達がWindows環境変数処理と衝突したため、単純なJDBC URLに補正した。
3. その後、正式なrev.3スキーマによる分離DBで3回の反復実行を完了した。

失敗履歴は削除せず、原因と補正経路として保存する。
