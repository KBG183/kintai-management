# Gradle全体回帰実行の分類記録

## 実行結果

- 実行DB: `kintai_day10_test_c7bd2380a226`
- 実行範囲: `.\gradlew.bat test --rerun-tasks --no-daemon --console plain`
- 総テスト: 79件
- 成功: 65件
- 失敗: 14件
- 終了コード: 1
- JUnit XML: 33件
- Day 10用の最小fixture DBで実行した。

## 失敗分類

### 1. Fixture不足または前提データ不一致

給与テストは `store_staff_id`・`store_id` の組み合わせ、勤怠データなどの追加fixtureを要求する。今回の最小fixtureにはowner 1名、staff 1名、store 1個だけを登録したため、外部キー違反が発生した。

- `PayrollMapperUpsertIntegrationTest`
- `PayrollServiceConcurrentExecutionIntegrationTest`
- `PayrollServiceTransactionRollbackIntegrationTest`
- `PayrollLockOrderDeadlockReproductionIntegrationTest`

代表エラー: `fk_att_staff_store`、`fk_payroll_staff_store` 外部キー違反

### 2. ロックテストの前提条件または環境差

- `PayrollPessimisticLockConcurrencyIntegrationTest`: 3回ともconcurrency guardの期待値不一致
- `PayrollServiceConfirmConcurrencyDetailedIntegrationTest`: 対象日付・給与前提条件の不一致
- `PerformanceSchemaDataLocksIntegrationTest`: `performance_schema.data_locks` の有効化・参照条件不一致

### 3. CSRFテストフロー失敗

- `CsrfCookieHeaderLifecycleIntegrationTest`: XSRF-TOKEN Cookie未発行と後続NullPointerException
- `CsrfSecurityIntegrationTest`: ログインフローが `/login?error=true` へ遷移

最小fixture DBと標準プロファイルでの実行であるため、認証アカウント、seedデータ、セキュリティテスト設定を別途確認する必要がある。この結果だけでCSRF機能欠陥とは断定しない。

## Day 10判定との関係

- Day 10専用テスト: 新規rev.3分離DB 3回、計6件成功
- 全体回帰: `BLOCKED_BY_TEST_FIXTURE_AND_ENVIRONMENT`
- 全体回帰の14件の失敗をDay 10同時性欠陥として合算しない。
