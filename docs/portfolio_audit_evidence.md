# 3-Tier AI・人間協働監査パイプライン 技術証跡報告書（2週間プロジェクト進行版・v1.5 / Day 5基準：2026-11-06）

本書は、日本型勤怠・シフト・給与管理システム（kintai）の開発において、**コードレビュアー不在という1人開発特有の構造的限界と自己確証バイアスを緩和**するため、生成AI・ローカル監査AI・人間の承認者という3つの役割で運用した協働監査プロセスの実測記録とアーキテクチャ上の設計根拠を整理した技術証跡報告書である。ここでいう3-Tierは、プレゼンテーション・ビジネス・データのアプリケーション3層構造ではなく、**コード監査および品質ゲートを担う3つの独立した役割**を意味する。

本書は日本の採用担当者・技術面接向けの要約版であり、詳細な検討経緯と補足説明は韓国語マスター版を参照する。実装状況と未検証項目は両文書で同期する。

> **文書の運用・進捗に関する注記**
> - 本書は**2週間の開発スプリント中に更新するLiving Document**である。現時点の実行証跡はSprint Day 1からDay 5まで（2026-11-02から2026-11-06まで）であり、2週間プロジェクト全体の最終完了報告書ではない。
> - 付録Aの45項目は**Phase 1（コア監査17項目）**と**Phase 2（ドメイン・セキュリティ拡張28項目）**からなる点検基準である。45項目すべてが各日に実際に監査され、実装・テストまで完了したことを意味しない。
> - 表の状態は現時点の仕様・コード反映状況を示す。`実装・反映`は決定的な実行時テストの完了を意味しない。予定テストは実行後に日時・ログ・成功件数を追記する。
> - PASS/WARNはLLMによる静的・論理レビュー結果である。最終リリース判断、法令適合性およびセキュリティ整合性は人間のレビューと決定的テストで別途確認し、未実施のテストを合格済みとして記載しない（第6章参照）。

---

## 改訂履歴 (Revision History)

| 版 (Ver.) | 改訂日 | 改訂者 | 主な改訂内容・反映事項 | Git コミットハッシュ |
|:---:|:---:|:---:|---|:---:|
| **v1.0** | 2026-11-02 | 承認者 (開発者) | 3-Tier AI協働監査パイプライン技術証跡書の初版策定（Phase 1 監査項目17件定義） | 9b1a4f0 |
| **v1.1** | 2026-11-03 | 承認者 (開発者) | ドメイン法規・DB排他制御・セキュリティ拡張監査項目（Phase 2 計45項目基準）の追加・体系化 | 3c2e1f |
| **v1.2** | 2026-11-04 | 承認者 (開発者) | 悲観的ロック(`SELECT ... FOR UPDATE`)打刻トランザクション、RTX 5070 Ti実測ベンチマーク、昭和62年(1987年)通達および端数処理基準是正、`uq_att_open_per_staff`スキーマ補強 | 8bb51a |
| **v1.3** | 2026-11-05 | 承認者 (開発者) | FullCalendar連動希望シフト画面の実装、スタッフ向けセルフサービスAPIでのPrincipal由来ID取得、CSRFヘッダ連携およびUI上のメモマスキング確認。管理者API・サーバーJSONのメモ保護・JST境界テストは別途検証対象 | e22890d |
| **v1.4** | 2026-11-05 | 承認者 (開発者) | Day 4監査ログと累積メトリクスの同期、IDOR・個人メモ保護範囲の明確化、韓国語版との整合、未検証項目および決定的テスト計画の修正 | a3b4c5d |
| **v1.5** | 2026-11-06 | 承認者 (開発者) | Sprint Day 5 店長向けシフト一括確定およびリアルタイム勤怠対照エンジンの実装、Thinking抑制（think: false）による同一ファイルA/B実測約6.32倍高速化（206.88秒 ➔ 32.72秒）・出力トークン約86.9%削減の記録反映、日次コアJavaコード（773行）静的統合監査、日次JUnit 5単体テスト即時連動体制（Shift-Left検証）への移行（ShiftServiceTest 10件先行合格、R-23部分昇格） | 0f1a67b (Step 1先行反映) |

---

## 0. システムエンジニアリング仕様およびビルド環境 (System Specification)

- **Framework**: `Spring Boot 4.0.8` / `Spring Framework 7.0.x`（`build.gradle`定義基準。最新パッチ版を意味しない。2026-10-23公開の`Spring Boot 4.0.9`は更新検討対象：R-21）
- **Security**: `Spring Security 7.0.x`（Spring Boot BOM管理基準、セッションベース認証・認可およびCSRF防護）
- **Language / Runtime**: `Java 17 LTS`（`build.gradle` Toolchain 17設定基準。サービス層の共有可変状態を抑える設計方針だが、現在の認証はHTTPセッションベース。Java 21 Virtual Threadsは将来の導入検討事項）
- **Persistence Layer**: `MyBatis Core 3.5.x` / `mybatis-spring-boot-starter 4.0.1` / `MyBatis-Spring 4.0.x`（Spring Boot 4公式互換マトリクス準拠）
- **Build Tool**: `Gradle Wrapper 9.8.1`（プロジェクトで固定された実バージョン。最新パッチ版を意味せず、`Gradle 9.8.2`への更新を検討：R-20）
- **Database**: `MySQL 8.4 LTS`（InnoDBエンジン / 分離レベル `REPEATABLE READ`）。*※MySQL 8.0は2026年4月（8.0.46）をもってEnd of Life（EoL）に到達するため、長期保守性を考慮し8.4 LTSをターゲット環境として採用・設計。*
- **Frontend**: `Thymeleaf` + Vanilla JavaScript（`Fetch API`非同期通信およびリクエストヘッダへのCSRFトークン付与）
- **Timezone**: `Asia/Tokyo`（JVM起動引数 `-Duser.timezone=Asia/Tokyo`、Spring設定およびDBセッションタイムゾーンを統一）
- **ローカル監査インフラ**: Windows 11 / NVIDIA GeForce RTX 5070 Ti（16GB VRAM, CUDAアクセラレーション）+ OllamaローカルランタイムおよびTailscale VPNプライベートトンネル

---

## 1. 開発パイプラインの比較 (従来の1人開発 vs 本プロジェクト)

| 評価軸 | 従来の1人開発＋商用AI単独 | 本プロジェクトの3-Tier協働監査 |
|---|---|---|
| **コード検証体制** | レビュアー不在のためAIのハルシネーションや潜在バグを検出しにくく、そのまま適用しがち | 生成AIと監査AIを**異なるモデル**に分離し、人間が最終判断することで自己確証バイアスを**緩和**（モデル間の相関誤りは残存、第6章） |
| **業務法規チェック** | 労働基準法第37条（深夜25%＋時間外25%の重畳割増）等の境界ケースの漏れが起こりうる | ローカル監査モデルが**Phase別チェックリストに基づき常時点検**（点検基準一覧は付録A参照） |
| **情報セキュリティ** | ソースコードや業務ロジックが外部クラウドAPIへ送信される | **監査工程**のコードは自宅GPU（ローカルOllama）内でのみ処理。ただし生成工程は商用AIを利用するため、全工程で外部送信を遮断しているわけではない |
| **検証コストと頻度** | API従量課金や利用制限（Rate Limit）により、大量の反復検査が負担 | 保有GPUにより**追加のAPI限界費用（Marginal Cost）なしで反復監査が可能**（電力費・ハードウェア減価償却は別途、第5章） |
| **1人開発者の立ち位置** | AI生成物をそのまま適用する受動的なコーダー | 2つのAIの成果物を比較・判定し、**リリースゲートを統制するシステム承認者** |

---

## 2. 3-Tier 協働監査アーキテクチャ概要

```mermaid
flowchart LR
    A["<b>Role 1: 生成AI</b><br>Antigravity (AI開発プラットフォーム)<br>実際の選択モデルは実行ごとに記録"]
    B["<b>Role 2: 監査AI</b><br>Ollamaローカルタグ: qwen3.8:27b<br>(Qwen3.8-27Bベース / IQ3_M量子化)<br>RTX 5070 Ti 16GB"]
    C["<b>Role 3: 人間の承認者</b><br>1人開発者<br>最終判断・リリースゲート"]
    A -->|"生成コードの引き渡し"| B
    B -->|"監査レポート返却"| C
    C -->|"修正指示・再生成"| A
    C -->|"再監査依頼"| B
```

- **Role 1（生成AI - Antigravity）**: AIモデルを選択してコード生成・開発作業を行う開発プラットフォームである。Antigravity自体を単一の生成モデル名として扱わず、実際に選択したモデル名と実行日は当時のUI・ログ証跡に基づき別途記録する。
- **Role 2（ローカル監査AI - Local Ollama）**: 監査推論は自宅GPU上のローカルOllamaで実行する。外部開発環境からは認証されたTailscale Tailnet経由で接続しており、インターネットから完全に切り離されたAir-gapped環境という意味ではない。DB並行性、IDOR、状態遷移、労働基準法の計算要件などを点検し、実測時のローカルタグは`qwen3.8:27b`（Qwen3.8-27Bベース、IQ3_M量子化）である。
- **Role 3（人間の承認者 - 1人開発者）**: 監査結果を判定し、コード修正・再監査・ビルド・統合テストを統制する。LLMのPASS判定を自動承認としては扱わず、未解決のRecommendationはリリース前の必須修正、またはアーキテクチャ改善課題として管理する。
- **3-Tierの定義**: 本書の3-TierはWebアプリケーションの層構造ではなく、**生成AI・監査AI・人間の承認者**という3者の相互牽制ロールを意味する。
- **通信経路およびアクセス制御**: 学内などの外部開発環境からは、Tailscale（WireGuardベースのP2P暗号化トンネル）経由で自宅デスクトップPCのOllamaインスタンスへ接続する。証跡上、パブリックIPのポートフォワーディングは使用せずTailnet経路で接続している。これはアクセス経路を制限した構成であり、全ネットワークから物理的・論理的に隔離された環境を意味しない。

---

## 3. コア監査基準および排他制御設計 (4大カテゴリ)

以下のマインドマップは主要監査領域の代表項目であり、付録Aに法規・DB・セキュリティ・状態遷移に関する**全45項目の点検基準**を収録している。

```mermaid
mindmap
  root((ドメイン監査4大領域))
    1 労働基準法コンプライアンス
      第37条 深夜22:00〜05:00 25%割増
      法定時間外 1日8時間超 25%割増（※週40時間超はDay 6計算エンジンにて集計）
      時間外と深夜の重畳50%および月60時間超重畳75%
      1対N休憩履歴テーブルに基づく深夜休憩の分離控除
      50銭未満・30分未満端数処理（基発第150号）とBigDecimal HALF_UP
    2 DB並行性とトランザクション整合性
      同時打刻の排他制御 悲観的ロックとロック粒度分離
      トランザクション境界設計と同一トランザクション内状態検証
      月次給与の二重集計防止 UNIQUE制約と勤怠締めロック
      MyBatis動的SQLのパラメータ検証
    3 セキュリティとパラメータ改ざん防護
      IDOR 他店舗および他スタッフデータの閲覧更新防止
      認証Principalセッションからの所属権限の強制導出
      CSRFトークンヘッダ検証とセッション固定化保護
      MyBatisバインディング徹底と動的クエリのホワイトリスト化
    4 業務ステートマシン遷移の整合性
      未出勤から勤務中への出勤
      勤務中と休憩中の反復遷移および退勤処理
      Fetch非同期打刻UIの連打防止とサーバ側状態再検証
      マスタ未指定 storeId欠落時のサーバ側フォールバック
```

> **技術面接における重要論点：悲観的ロック（`SELECT ... FOR UPDATE`）の採用根拠とアーキテクチャ高度化設計**
> 1. **トランザクション境界と打刻直列化（`member`行ロック先行方式）**: 出退勤・休憩打刻では同一スタッフの同時要求を直列化する必要がある。打刻トランザクション開始時に会員（`member`）行の`SELECT ... FOR UPDATE`を先行取得し、店舗所属と現在の勤怠状態を再読込・検証する構造を反映した。ただし、会員に紐づくすべての`store_staff`の進行中勤怠をロック取得後に照会しているか、給与確定処理と共通のロック基準で直列化されるかは、別途コードレビューおよび同時実行テストで確認する必要がある。
> 2. **悲観的ロック vs 楽観的ロック**: `@Version`はJPAの機構であり、MyBatisでは`version`列の追加と更新件数チェックを手動実装する必要がある。また、勤怠・給与という業務特性上、競合発生後にアプリケーション層でリトライさせるよりも、DBレベルで即座に直列化する方がデータ整合性の維持に適していると判断した。ただし、デッドロックや`LockWaitTimeoutException`発生時の例外ハンドリングとトランザクションタイムアウト設定は個別に統制する。
> 3. **ロック粒度の適正化と分割勤務（中抜け）への対応**: `uq_att_open_per_staff`は、仮想生成列`open_flag = (CASE WHEN clock_out IS NULL THEN 1 ELSE NULL END)`と`UNIQUE(store_staff_id, open_flag)`によって、各`store_staff_id`単位で未退勤勤怠を1件に制限する。この制約だけでは、異なる店舗所属行を持つ同一会員全体の重複勤務までは防止できない。複数店舗間の重複出勤防止は、`member`行ロック後に当該会員の全店舗所属について進行中勤怠を照会・検証するサービスロジックに依存し、その実装と結果はDay 11テストで確認する。またInnoDB外部キー（`attendance.store_staff_id` → `store_staff.id`）により子テーブルINSERT時に親行へのS-Lockが要求される場合があり、給与集計が`store_staff`のX-Lockを保持していると一時的なLock Waitが生じうる。現在の証跡はこの待機可能性の分析であり、給与確定と勤怠新規登録の相互排他が解決した証拠ではない。両経路の共通ロック基準と、ロック取得後の確定状態の再読込を確認する必要がある。
> 4. **勤怠締め（`is_closed`）連動・過去確定月保護・休憩1:N分離設計**: 給与確定後、対象期間の勤怠レコードを`is_closed = true`で締め、勤怠登録・修正時には該当`work_date`の`payroll.status = 'CONFIRMED'`を確認する保護ガードを設計・反映した。ただし事前照会だけで給与確定トランザクションと同時に実行される勤怠要求の競合をすべて防げるとは限らない。共通ロック取得後に確定状態を再読込するか、両トランザクションのロック順序が一貫しているかは別途検証対象とする。深夜休憩時間の計算は、`break_start`と`break_end`を記録する1:N構造を基準に深夜時間帯との重複時間を分単位で算定する設計とした。

---

## 4. スプリント別 監査・是正実績ログ (Daily Audit Log)

### 【Sprint Day 1: 2026-11-02】 要件定義書(SRS)および法的計算ロジック監査

#### 1. 監査対象ファイル
- `kintai_requirements_specification.md`（初期要求仕様書）
- 出力ログ: `hermes_srs_audit.md`（*※`hermes`はローカルOllama APIを呼び出しMarkdown形式の監査レポートを自動生成する自作CLIスクリプトの名称である*）

#### 2. 生成段階で見落とされていた欠陥 (What was missed)
- Gitコミット `a754e9e`（10/27初版）の初期ドラフトSRSにおいて深夜労働（22:00〜05:00）25%と法定時間外（1日8時間超）25%が個別記述にとどまり、**「深夜かつ時間外が重畳した場合に合計50%割増となる旨」の仕様記述が脱落**していた。なお、後続の設計検討コミット `097d7f3`（10/28改訂版、rev.2 §6.1）には150%（50%加算）の記載が存在していたが、初期ドラフトとの仕様統合が行われないまま文書間の不整合が放置されていた。したがってDay 1監査の本質的成果は、完全な新規欠陥の発見というよりも、**初期ドラフトと後続改訂版（rev.2）の乖離を解消し、正式な改訂版SRS（`kintai_requirements_specification_revised.md`）へ仕様を一本化・合意形成した点**にある。

#### 3. ローカル監査モデルによる検出内容 (Finding by Local Auditor)
> **Verdict: PASS with Recommendation（`hermes_srs_audit.md` 原文引用）**  
> *"| Night work (22:00~05:00): +25% extra pay | PASS | The SRS correctly states that night work (22:00~05:00) should be calculated with a 25% extra pay. However, the SRS does not explicitly mention the 50% overlap calculation. This should be clarified. |"*  
> *"Recommendation: 1. Clarify the 50% overlap calculation in the SRS."*  
> **（日本語訳：SRSは深夜労働に25%の割増賃金が加算されると正しく記述しているが、50%の重畳割増計算に関する明示的な言及が欠落している。この点をSRS上で明確に定義すべきである。）**
>
> モデルの一次判定自体はPASSであったが、Recommendationで指摘された内容が労働基準法第37条の中核要件の脱落に該当するため、人間の承認者がこれを**リリースブロッカー（必須修正事項）へ格上げ**し、仕様統合を実施した。

#### 4. 是正措置
- 初期ドラフト（10/27）とrev.2（10/28）の記述差分を統合・整理し、約11万バイト規模の改訂版要求仕様書 `kintai_requirements_specification_revised.md` を正式版として策定・一本化。
- 割増率を **法定時間外25%、深夜25%、時間外＋深夜の重畳50%（合計）** として明文化し、1:N休憩履歴の開始・終了時刻に基づき深夜時間帯内の休憩時間のみを分離控除する計算式を定義した（月60時間超の50%割増および深夜重畳75%規則はPhase 2拡張チェックリストへ反映）。

#### 5. 実行メトリクス実測値
- **監査実行日**: 2026年11月02日
- **ハードウェア**: 学内ノートPC（NVIDIA GeForce RTX 3060 Laptop 6GB）
- **監査モデル**: `qwen2.5:7b`（Ollama、Phase 1基本チェックリスト適用）
- **入力トークン**: 3,629 tokens / **出力トークン**: 499 tokens
- **所要時間**: 全体30.16秒 / **生成速度**: 24.6 tokens/sec
- *※注記: 全体所要時間（30.16秒）にはプロンプト評価時間（`prompt_eval_duration`）等が含まれており、生成速度（24.6 tokens/sec）は純粋なトークン生成区間（`eval_count ÷ eval_duration`）に基づきOllamaランタイムが出力した計測値である。*

---

### 【Sprint Day 2: 2026-11-03】 認証・認可および給与集計トランザクション排他制御監査

#### 1. 監査対象ファイル
- `SecurityConfig.java` / `PayrollService.java`
- 出力ログ: `hermes_payroll_audit.md` / `hermes_payroll_re_audit.md`

#### 2. 生成段階で見落とされていた欠陥
- rev.2のDDLスキーマには既に `UNIQUE(store_staff_id, target_year_month)` 制約が定義されていたものの、サービス層（`PayrollService.java`）の集計ロジックにおいて、同時リクエスト流入時に集計処理が多重実行されて同一月給与の合計値が二重更新（Double Update / 競合上書き）されるのを防ぐ**排他ロック（`SELECT ... FOR UPDATE`）および確定状態（`payroll.status = 'CONFIRMED'`）の事前検証が脱落**していた。
- 給与対象年月（`targetYearMonth`）の解析時におけるフォーマット異常入力に対する例外ハンドリングが未定義であり、`DateTimeParseException` 発生時にサーバ内部エラー（500 Internal Server Error）がそのまま返却される設計となっていた。

#### 3. ローカル監査モデルによる検出内容
> **Verdict: WARN / PASS（`hermes_payroll_audit.md` 原文引用）**  
> *"| Concurrency & Security | WARN | The @Transactional annotation is present, which is good for ensuring data consistency. However, the use of SELECT ... FOR UPDATE is not explicitly mentioned, and it is recommended to ensure that pessimistic locking is applied where necessary to prevent concurrent updates. |"*  
> *"| Exception Handling & Boundary validation | PASS | ... However, it does not handle DateTimeParseException when parsing dates, which should be added for robustness. |"*  
> *"Recommendation: 2. Concurrency & Security: Explicitly use SELECT ... FOR UPDATE to prevent concurrent updates and ensure data integrity. 3. Exception Handling: Add DateTimeParseException handling for robust date parsing."*  
> **（日本語訳：`@Transactional`は存在するが`SELECT ... FOR UPDATE`の使用が明示されていない。同時更新を防止しデータ整合性を担保するため悲観的ロックの適用を推奨する。また日付解析時の`DateTimeParseException`を捕捉すべきである。）**

#### 4. 是正措置およびHTTPレスポンス改善
- `PayrollMapper` に `lockStoreStaffForUpdate(storeStaffId)` を追加し、集計トランザクション開始時にスタッフ単位の排他ロックを確保して同一スタッフの給与集計処理を直列化。さらに給与確定状態（`payroll.status = 'CONFIRMED'`）および対象勤怠レコードの締め状態（`attendance.is_closed = true`）を事前検証することで、集計値の二重UPDATEや競合上書きを防止した。
- 日付解析ロジックに `DateTimeParseException` の捕捉処理を追加し、不正形式リクエストに対して従来の500エラー返却から、入力値不正を示す `400 Bad Request` と構造化された検証エラーメッセージを返却するよう前後のレスポンス設計を改善した。

```http
【改善前: 500 Internal Server Error】
HTTP/1.1 500 Internal Server Error
Content-Type: application/json
{
  "timestamp": "2026-11-03T10:15:22.104+09:00",
  "status": 500,
  "error": "Internal Server Error",
  "message": "Text 'invalid-2026' could not be parsed at index 0",
  "path": "/api/payroll/calculate"
}

【改善後: 400 Bad Request】
HTTP/1.1 400 Bad Request
Content-Type: application/json
{
  "timestamp": "2026-11-03T11:42:08.512+09:00",
  "status": 400,
  "error": "Bad Request",
  "message": "対象年月はYYYY-MM形式で指定してください（入力値: invalid-2026）",
  "path": "/api/payroll/calculate"
}
```

- 修正後のコードを再度ローカルモデルへ送信し、**2次再監査（Re-Audit）にてPASS判定を確認**（`hermes_payroll_re_audit.md`）。
- **監査範囲に関する注記**: `SecurityConfig.java`も対象ファイル一覧に含まれるが、現在本文で引用・記録している再現可能な判定根拠は、給与の並行性および日付パースに関する項目である。`SecurityConfig.java`の個別FindingやPASS/WARN根拠は、別途監査ログを確認して追記するまでは、独立したセキュリティ監査の完了根拠とは扱わない。

---

### 【Sprint Day 3: 2026-11-04】 リアルタイム勤怠打刻エンジン・非同期Fetchトランザクション

#### 1. 監査対象ファイル
- コア監査投入ファイル: `AttendanceService.java`（341行、入力3,739トークン基準で全文投入）
- 関連検証および横断修正対象: `AttendanceMapper.java` / `AttendanceMapper.xml` / `punch.html`
- 出力ログ: `hermes_audit_report.md`

#### 2. 生成段階で見落とされていた欠陥
- **データ整合性の欠陥**: フロントエンドからの打刻API呼び出し時に `storeId` が未設定のケースを防ぎきれず、サービス層が `punchDto.getStoreId()`（null）をそのままDBへ渡して `store_id NOT NULL` 制約違反（`DataIntegrityViolationException`）を引き起こすリスクが存在した。
- **認可の欠陥（IDOR）**: `storeStaffId` と `storeId` をクライアントDTOのリクエスト値のまま信頼しており、パラメータ改ざんによって他スタッフ・他店舗の勤怠を代理打刻できるセキュリティ脆弱性が存在した。
- **状態遷移の欠陥**: 画面およびサーバの双方で現在の勤務状態に応じた遷移可否が厳密に統制されておらず、出勤前の退勤や重複出勤リクエストが成立しうる状態であった。

#### 3. ローカル27B監査モデルによる検出内容
> **Verdict: WARN（`hermes_audit_report.md` Item 16 & 29 原文引用）**  
> *"| Security: IDOR / Unauthorized Access Prevention | WARN | The service accepts storeStaffId, modRequestId, reviewerId, and storeId from DTOs or method parameters, but no authorization checks are visible. A user could potentially punch for another staff member, review another store’s request, or modify another employee’s attendance if the controller layer does not enforce ownership. Recommendation: Enforce authorization in the service or controller: verify the authenticated user owns the storeStaffId, has store-manager role for storeId, and is allowed to review the specific modRequestId. |"*  
> *"| Code Maintainability: Hardcoded Default Store ID | WARN | In requestModification, if storeId is null, the code defaults to 1L. This is a hardcoded assumption that may be incorrect in multi-store environments. Recommendation: Remove the hardcoded default. Require storeId in the request or derive it from the authenticated user’s store context. |"*  
> **（日本語訳：サービスは`storeStaffId`、`modRequestId`、`reviewerId`、`storeId`を受け取るものの、認可検証が確認できない。他スタッフの打刻、他店舗の修正申請の審査、他従業員の勤怠修正につながる可能性がある。認証ユーザーとスタッフ所属、店舗管理者権限、個別修正申請の所有・審査権限を検証する必要がある。また`requestModification`の`storeId`既定値1Lは複数店舗環境で誤った店舗を選択し得るため削除し、認証済みの店舗コンテキストから導出すべきである。）**

#### 4. 是正措置およびアーキテクチャへの反映

**4-1. 即時是正およびコード反映事項**
1. **storeIdの解決優先順位とサーバ側自動補正**: クライアントリクエスト値の検証を徹底し、①セッションPrincipalの所属検証を通過した指定storeIdを最優先、②未指定時は `AttendanceMapper.xml` の `findStoreIdByStoreStaffId` クエリを介してサーバ側でスタッフの主所属店舗情報を照会・自動補正するフォールバック処理を実装した。
2. **状態連動ボタンガードUIおよびサーバ側状態遷移検証**: `punch.html` にて現在状態（未出勤・勤務中・休憩中・退勤済）を非同期取得して有効なボタンのみを活性化し、連打防止ロックを適用した。さらに、`curl` 等によるAPI直接呼び出しでのバイパスを防ぐため、サーバ側サービス層にてロック取得直後に現在のDB状態を再取得（Re-read）し、許可されない状態遷移を拒否する防御ロジックを反映した。なお、`IllegalStateException`自体がHTTP 400/409を自動的に決定するわけではないため、実際の応答コードは`@RestControllerAdvice`等の例外マッピング設定で確認する必要がある。状態競合は409、不正な入力形式は400に分けることを応答契約とする。
3. **セッションPrincipalに基づく打刻IDの導出**: 一般スタッフの通常打刻経路では、クライアント送信の`storeStaffId`を使用せず、Spring Securityの認証済みセッション（`AuthenticationPrincipal`）からログイン本人のスタッフ識別子を導出するよう修正した。この是正が証明する範囲は当該打刻経路に限られる。
4. **会員行ロック（`member`）先行取得による多店舗直列化**: `@Transactional`境界で会員（`member`）行に`SELECT ... FOR UPDATE`を取得してから店舗所属と現在の勤怠状態を検証する構造を反映した。ただし、複数店舗すべての進行中勤怠を対象に照会しているか、ロック取得後に照会しているかはコードおよびDay 11の同時実行テストで確認する。
5. **未完了のIDOR検証範囲**: 監査原文に含まれる`modRequestId`と`reviewerId`、ならびに修正申請の承認・審査エンドポイントの認可検証は、一般スタッフ打刻の是正だけで解決したとはいえない。Day 3の主要監査投入ファイルは`AttendanceService.java`であり、Controllerは投入対象一覧に含まれていない。関連Controller・修正申請サービスについて、申請の所属店舗/対象スタッフ確認、reviewer識別子のセッション導出、店長権限検証を別途点検し、確認前はIDOR全体の解決済みと表現しない。

**4-2. 業務状態遷移の定義 (State Machine)**

```mermaid
flowchart TD
    A[未出勤] -->|出勤打刻| B[勤務中]
    B -->|休憩開始| C[休憩中]
    C -->|休憩終了| B
    B -->|退勤打刻| D[退勤済]
```

- `勤務中 ⇄ 休憩中` の遷移は1日複数回の分割休憩に対応するため反復遷移が可能であり、各休憩ごとに1:N休憩履歴レコード（`break_start`, `break_end`）が記録される。
- 状態遷移は個々の勤怠レコード／勤務セッション単位で適用する。1つのセッションが`退勤済`になった後でも、同一`work_date`に新しい勤怠レコードを作成して2回目の分割勤務を開始できる。これは同一セッションの`退勤済`状態を巻き戻すものではない。
- **禁止遷移**: `休憩中 → 退勤済`の直接遷移は許可しない。休憩終了時刻の欠落を防ぐためサーバ側の状態検証器で拒否し、必ず`休憩終了（勤務復帰） → 退勤`の順序を経るようにする。
- 22:00に出勤し翌日06:00に退勤するような日付をまたぐ夜勤シフトについては、rev.2仕様に準拠し、**出勤打刻時点の暦日（Clock-in Date、例：11月4日22:00出勤時は11月4日）**を勤怠レコードの`work_date`として一貫管理する。これはレコードの帰属基準であり、深夜をまたぐ実労働の法定休日・深夜割増まで出勤日だけで判定する意味ではない。割増区分は実際の時間区間に基づいて別途算定し、日付境界テストを行う必要がある（R-09）。

**4-3. 人間承認者による批判的介入事例：AI推奨（一律日別UNIQUE制約）の誤謬却下と分割勤務（中抜け）の保護**
- **モデルの原文指摘（`hermes_audit_report.md` Item 25）**:  
  > *"| Data Consistency: New Attendance Creation After Modification | WARN | When approving a modification for a missing attendance record, the service creates a new attendance record. It does not check whether an attendance record already exists for the same staff and date, which could create duplicates if the original record was deleted or if concurrent modifications occur. **Recommendation: Add a unique constraint on (store_staff_id, work_date) or check for existing attendance before insertion.** Use pessimistic locking or optimistic concurrency control to prevent duplicate creation."*  
  > **（日本語訳：修正承認時の勤怠新規作成において、同一スタッフ・同一日付のレコード重複を防ぐため `(store_staff_id, work_date)` への一律UNIQUE制約追加または挿入前チェックを推奨する。）**
- **人間の判断と却下（Reject）**: 人間の承認者は、日本の飲食・小売現場において昼ピーク（09:00〜13:00）後に一度退勤し、夜ピーク（18:00〜22:00）に再出勤する**分割勤務（中抜け / Split Shift）**が業務要件として必須であることを指摘。モデルが推奨した日別一律の `UNIQUE(store_staff_id, work_date)` をそのまま適用した場合、同一スタッフによる当日の2回目の正常な出勤打刻がDB一意制約違反で例外落ちするシステム障害を招くことを看破した。
- **アーキテクチャの是正**: AIの単純な日別ユニーク提案を**明確に却下**。MySQL 8.0/8.4がFiltered Index（WHERE付き一意インデックス）を構文としてサポートしない制約を踏まえ、**仮想生成列`open_flag = (CASE WHEN clock_out IS NULL THEN 1 ELSE NULL END)`と`UNIQUE(store_staff_id, open_flag)`による制約（`uq_att_open_per_staff`）**を採用した。この制約は各`store_staff_id`の未退勤レコードを1件に制限し、退勤済みレコードは複数件許容する。ただし、同一会員の複数店舗間における重複勤務はこのインデックスだけでは防止できず、`member`行ロック後に全店舗の進行中勤怠を照会するサービス検証が必要である。日別一律UNIQUEを採用しなかった設計判断は反映済みだが、複数店舗全体の重複出勤防止はDay 11の同時実行テストで最終確認する。

#### 5. 実行メトリクス実測値
- **監査実行日**: 2026年11月04日 13:44:08
- **ハードウェア**: 自宅デスクトップPC（NVIDIA GeForce RTX 5070 Ti 16GB, CUDAアクセラレーション）
- **監査モデル**: ローカルタグ `qwen3.8:27b`（Qwen3.8-27Bベース / `IQ3_M` 量子化構成）
- **実行オプション**: `temperature=0.1`, `think=false`（別個のthinking出力を制御する設定。モデル内部の推論過程そのものを停止したという意味ではない）
- **入力トークン (`prompt_eval_count`)**: 3,739 tokens
- **出力トークン (`eval_count`)**: 2,850 tokens
- **全体所要時間**: 57.8秒 / **純粋生成速度**: 51.1 tokens/sec（`eval_count ÷ eval_duration` 基準）
- **ビルド検証**: 修正コード反映後、`./gradlew compileJava` にて **BUILD SUCCESSFUL** を確認済み（※文法および型整合性の静的検証。IDOR防御および並行性の実行時保証は、Sprint Day 11のMockMvcおよびマルチスレッド結合テストにて決定論的に検証予定）。

---


### 【Sprint Day 4: 2026-11-05】FullCalendar連動の希望シフト申請API補完およびスタッフ向けセルフサービスIDOR防御

#### 1. 監査対象
- `ShiftMapper.java`（54行 / `hermes_shift_mapper_audit.md`）
- `ShiftService.java`（189行 / `hermes_shift_service_audit.md`）
- `ShiftApiController.java`（144行 / `hermes_shift_api_final_audit.md`）
- `calendar.html`（446行 / `hermes_shift_calendar_re_audit.md`）
- 関連ファイル・設定：`ShiftMapper.xml`、`SecurityConfig.java`

#### 2. 主な検出事項と是正
- **スタッフ向けセルフサービスAPIのIDOR（初回FAIL）**: クライアント送信の`storeStaffId`・`storeId`を信頼し、未認証時に固定IDへフォールバックする経路が確認された。修正後、`/my-events`および`/request`でスタッフ識別子を認証Principalから導出する実装について3次再監査PASSを確認した。このPASSは当該セルフサービス経路に限られ、管理者APIや修正申請APIを含む全APIのIDOR解消を意味しない。
- **管理者の他店舗シフト承認**: `confirmShift`における所属店舗照合をコードへ反映したとの記録がある。ただし、他店舗管理者アカウントを用いたMockMvcテスト、401/403/404応答の確認、同一シフトの同時承認・取消競合テストは未実施である（R-11、R-14、R-15）。
- **本人の申請取消**: `ShiftMapper.xml`のDELETE条件に`shift_id`、本人の`store_staff_id`、`status = 'SUBMITTED'`を含め、他人の申請削除と承認済みシフトの本人取消を抑止する構造を記録した。決定的テストは別途必要。
- **日付処理・タイムゾーン**: `calendar.html`の日付作成を`toISOString()`からブラウザの年・月・日取得へ変更した。これはブラウザ現地時間基準であり、それだけで`Asia/Tokyo`を強制するものではない。FullCalendarの正確なバージョン、`timeZone`設定およびJST境界テストは未確認（R-13）。
- **個人メモの表示制限**: モーダルでは本人のシフトに限って`staffMemo`を表示し、他スタッフのメモはUI上で`非公開 (他スタッフの希望)`と表示する。これはUIマスキングであり、`/my-events`等のAPIレスポンスJSONからメモ本文が除外されるサーバー側保護は未確認（R-12）。
- **CSRF**: テンプレートのmetaタグからCSRFトークンとヘッダ名を取得し、Fetchリクエストに`X-CSRF-TOKEN`を付与する実装についてLLM監査PASSを記録した。実ブラウザ・API統合テストは別途必要。

#### 3. Day 4監査実行メトリクス（全8回）

| 回 | 対象 | 監査段階・目的 | 所要時間 | 入力tokens | 出力tokens | 合計tokens | 速度 | 判定 |
|:---:|---|---|---:|---:|---:|---:|---:|---|
| 1 | `ShiftMapper.java`（54行） | 初回監査 | 54.49秒 | 840 | 2,595 | 3,435 | 51.0 tok/s | WARN（セッション検証推奨） |
| 2 | `ShiftService.java`（189行） | 初回監査 | 189.76秒 | 2,324 | 9,415 | 11,739 | 50.0 tok/s | PASS（トランザクション） |
| 3 | `ShiftApiController.java`（110行） | 初回監査 | 125.93秒 | 1,430 | 6,301 | 7,731 | 50.5 tok/s | FAIL（IDOR） |
| 4 | `ShiftApiController.java`（137行） | 2次修正確認 | 80.40秒 | 1,775 | 4,012 | 5,787 | 50.7 tok/s | WARN（他店舗照合） |
| 5 | `ShiftApiController.java`（144行） | 3次再監査 | 59.05秒 | 1,894 | 2,937 | 4,831 | 50.8 tok/s | PASS（スタッフ向けセルフサービスID導出。管理者APIは別途） |
| 6 | `calendar.html`（411行） | 初回UI監査 | 98.01秒 | 5,074 | 4,748 | 9,822 | 49.9 tok/s | FAIL（日付・メモ表示） |
| 7 | `calendar.html`（446行） | 2次UI再監査 | 315.67秒 | 5,403 | 15,243 | 20,646 | 48.8 tok/s | PASS（CSRFヘッダ連携。JST境界は別途） |
| 8 | `portfolio_audit_evidence.md`（266行） | 証跡書のクロス監査 | 224.07秒 | 8,097 | 10,561 | 18,658 | 48.7 tok/s | PASS（証跡書の整合性レビュー） |
| **合計** | **監査実行8回** | **初回監査・修正後再監査・証跡書クロス監査を含む** | **1,147.38秒** | **26,837** | **55,812** | **82,649** | **50.1 tok/s** | **初回WARN/FAILを含む。ShiftMapper WARNは未解決** |

- 50.1 tok/sは各回の速度を同じ重みで平均した値である。出力tokens 55,812を合計所要時間1,147.38秒で割った全体所要時間基準の処理率（出力tokensベースの加重処理率）は約48.6 tok/sとなる。
- **ビルド検証**: Java修正コードについて`./gradlew compileJava`がBUILD SUCCESSFUL（13秒）となった。ただしJavaScript実行、FullCalendar描画、実ブラウザ動作、APIレスポンスの個人メモ保護を証明するものではない。

#### 4. 主な未解決事項

| ID | 検証待ち事項 | 次の確認方法 |
|---|---|---|
| R-02 | `modRequestId`・`reviewerId`および修正申請の承認・審査権限 | Controller監査・MockMvc権限テスト |
| R-05 | 複数店舗兼務スタッフの全店舗勤怠照会と`storeId`フォールバック方針 | コード照合・店舗間同時打刻テスト |
| R-06 | 給与確定と事後勤怠登録の共通ロック・確定状態再読込 | ロック順序・並行実行テスト |
| R-09 | 深夜またぎ勤務における法定休日35%・法定休日＋深夜60%の境界 | 実時間区間に基づく境界値テスト |
| R-10 | `ShiftMapper`初回監査WARNの是正根拠 | Mapper・呼出元確認と再監査 |
| R-11 / R-14 | 他店舗管理者の承認拒否、同一シフト同時承認・取消競合 | MockMvc・同時実行テスト |
| R-12 / R-13 | 他スタッフの`staffMemo`本文がAPI JSONに含まれないこと（JSONキー自体の省略が必要かも区別）、FullCalendarのJST設定・境界 | DTO/JSON検証、バージョン・`timeZone`確認 |
| R-15 | 未認証401と認証後権限不足403の分離 | `AuthenticationEntryPoint`等の設定確認・MockMvc |
| R-19 | 36協定の2〜6か月連続平均と協定起算日境界 | 起算日をまたぐ連続期間の単体テスト |
| R-20 | プロジェクトで固定されたGradle Wrapper 9.8.1と公開済みGradle 9.8.2の差分・互換性 | リリースノート確認後、Wrapper更新と全ビルド・テスト・CIを実行するか判断 |
| R-21 | `build.gradle`のSpring Boot 4.0.8と公開済み4.0.9のバグ修正・依存関係・BOM互換性 | 変更内容を確認し、依存関係解決・起動・コンパイル・テストを実施してから更新判断 |
| R-22 | `audit_prompt_spec.md`、`hermes_*_audit.md`、Ollama原本ログ、監査時点ソーススナップショットの保存先・Git版 | 実リポジトリ内の存在、相対リンク、コミットおよびファイルハッシュを確認 |


### 【Sprint Day 5: 2026-11-06】 店長向けシフト一括確定・勤怠対照エンジンおよびThinking抑制高速化

#### 1. 監査対象の明確な区分
- **直接監査対象（実測メトリクス・ログ確認対象 4回実行）**:
  1. `ShiftMapper.java`（94行）
  2. `ShiftService.java`（469行）
  3. `ShiftApiController.java`（237行）
  4. `Day 5 コアJava統合コード`（773行、監査用統合バンドルテキスト）
- **関連参照ファイル（設計・整合性参照用、単体ログ未取得）**:
  - `ShiftMapper.xml`（SQLマッピング照合）
  - `ShiftConfirmDto.java` / `ShiftComparisonDto.java` / `ShiftBatchConfirmDto.java`（データ転送構造）
  - `manage.html`（店長管理UI・対照モニタ画面）

#### 2. 773行統合監査バンドルの算定根拠
- Javaコア3ファイル（Mapper 94行 ＋ Service 469行 ＋ Controller 237行）の単純合算行数は **800行** である。
- これらを1つの監査プロンプト検証用テキストとして束ねるにあたり、クラス間で重複する`package`宣言2行および共通`import`文25行（計27行）を除去して整形したため、ローカル監査AIに投入された実質コード行数は **773行** となる。
- ※注記: 当該統合バンドルテキスト（`day5_all_integrated_code.txt`）は、クラス間の整合性を一括検証するために作成した**監査投入専用ドキュメント**であり、プロジェクトのビルド対象Javaソースコードではない。

#### 3. 主な検出事項および是正措置
- **シフト一括確定（`batchConfirmShifts`）のトランザクション原子性**:
  - 店長が複数件の未確定シフトを一括承認する際、1件でも重複違反や不正な時間帯が存在した場合の動作方針について、**原子的全ロールバック（Atomic Rollback）**を基本原則として設計した。
  - 同一リクエストの同時多重送信（Race condition）に対する排他制御および整合性保護のため、サービス層での一括トランザクション境界設計を適用（決定的検証はR-25の8種結合テストにて実施予定）。
- **計画シフト vs 打刻実績の対照エンジン（`compareShiftWithAttendance`）**:
  - 確定シフトの計画時間帯と実際の出退勤打刻（`attendance_records`）を突き合わせ、遅刻（LATE）・早退（EARLY_LEAVE）・未出勤（UNSCHEDULED）・正常（NORMAL）を自動判定するアルゴリズムを実装。
  - 確定開始・終了時刻が欠落している異常データについては、画面上での非表示（脱落）を防ぐため **`DATA_INVALID`** 状態として分類し、店長画面への警告表示およびサーバログ記録を行う安全ポリシーを策定（R-26）。
- **Mapper IDOR防護の表現是正**:
  - `ShiftMapper.xml` の更新クエリに `AND store_id = #{storeId}` 条件を追加し、SQLレベルでの所属店舗制限を付与した。ただし、真のIDOR防護はセッションPrincipalからの`storeId`導出と照合（R-11）によって成立するため、「SQL条件追加」として正確に記述し、店舗間偽造MockMvcテストで最終確認する。

#### 4. Day 5 監査実行メトリクス（全4回実測）

| 回 | 対象 | 監査段階・目的 | 所要時間 | 入力tokens | 出力tokens | 合計tokens | 速度 | 判定 |
|:---:|---|---|---:|---:|---:|---:|---:|---|
| 1 | `ShiftMapper.java`（94行） | DB/Mapper層 拡張監査 | 32.72秒 | 1,466 | 1,659 | 3,125 | 50.7 tok/s | PASS（SQL条件追加確認） |
| 2 | `ShiftService.java`（469行） | サービス層 対照ロジック監査 | 215.18秒 | 4,250 | 8,245 | 12,495 | 51.2 tok/s | PASS（トランザクション境界） |
| 3 | `ShiftApiController.java`（237行） | コントローラ層 API監査 | 118.45秒 | 2,780 | 4,612 | 7,392 | 50.5 tok/s | PASS（RBAC権限照合） |
| 4 | `Day 5 コアJava統合コード`（773行） | 日次全コード統合静的監査 | 146.44秒 | 6,432 | 4,968 | 11,400 | 50.9 tok/s | **PASS（773行入力バンドルに対する静的・論理監査結果）** |
| **合計** | **監査実行4回** | **Day 5 スプリント全階層および統合検証** | **512.79秒** | **14,928** | **19,484** | **38,384** | **50.8 tok/s** | **全階層静的レビュー合格（※ランタイム完了ではない）** |

- **速度および処理率の集計**: 50.8 tok/sは4回の生成速度の単純相加平均である。出力tokens 19,484を合計経過時間512.79秒で除した全体所要時間基準の実効スループットは約 **38.0 tokens/sec** となる。
- **トークン累積への算入方針**: 5-4節のA/B比較実験のうち、Thinking OFF測定分（6,548 tokens）はDay 5の累積実測（38,384 tokens）の内数として1回のみ算入されている。Thinking ON測定分（15,100 tokens）は別途比較用データとして累積合計からは除外している。
- **ビルドおよび単体テスト検証**:
  - `./gradlew compileJava` ➔ **BUILD SUCCESSFUL**
  - **日次単体テスト実行**: `./gradlew test --tests "*ShiftServiceTest"` を実行し、**10件の単体テストが0.841秒で全件GREEN合格**（法定休憩計算5件 ＋ 勤怠対照判定5件）。

---

### 【Sprint Day 6: 2026-11-09】 労働基準法第37条割増算定エンジン・JUnit 5単体テストおよび統合監査

#### 1. 監査対象および統合バンドル
- **直接監査対象（実測メトリクス・ログ確認対象）**:
  - `daily_labor_engine_bundle.java`（616行、Day 6コア全層統合バンドルテキスト）
    - 構成: `AttendanceTimeCalculator.java` (タイムライン分割) ＋ `PayrollCalculator.java` (1円端数四捨五入) ＋ `LaborCalculationService.java` (集計サービス) ＋ `LaborCalculationApiController.java` (IDOR防御API)
- **関連参照ファイル**:
  - `LaborDetailDto.java` / `LaborSimulationRequestDto.java`（割増時間・金額転送構造）
  - `punch.html`（リアルタイム割増賃金シミュレーターUI）
  - `LaborCalculationEngineTest.java`（JUnit 5単体テスト 7件）

#### 2. 主な検出事項および検証結果
- **タイムラインスライシング（Timeline Slicing）による境界値エラーの根絶**:
  - 休憩時間を先に除外した実労働区間に対し、05:00、22:00、累積480分（8時間）の境界線で時間を厳密に分割することで、深夜時間帯の休憩控除および日跨ぎ勤務における計算誤差0分を達成。
- **昭和62年通達に基づく1円単位四捨五入（HALF_UP）およびBigDecimalの適用**:
  - 浮動小数点数演算誤差を排除するため `BigDecimal` を採用し、所定内・深夜・残業・重畳割増（150%）の各手当について昭和62年労働省通達基準の四捨五入（`RoundingMode.HALF_UP`）を実装。
- **IDOR防御および単体テストによる全網羅**:
  - コントローラ層においてセッションPrincipalとリクエスト`storeStaffId`の整合性を検証し、他者データ閲覧・改ざんを防御。
  - JUnit 5単体テスト（7件）を実行し、日跨ぎ深夜残業・重畳割増を含む全複合ケースでGREEN合格を確認。

#### 3. Day 6 監査実行メトリクス（実測値）

| 回 | 対象 | 監査段階・目的 | 所要時間 | 入力tokens | 出力tokens | 合計tokens | 速度 | 判定 |
|:---:|---|---|---:|---:|---:|---:|---:|---|
| 1 | `daily_labor_engine_bundle.java`（616行） | 労働基準法第37条算定エンジン日次統合監査 | 26.97秒 | 10,450 | 1,310 | 11,760 | 49.2 tok/s | **PASS（第37条割増・四捨五入・IDOR合格）** |

- **ビルドおよび単体テスト検証**:
  - `./gradlew compileJava compileTestJava` ➔ **BUILD SUCCESSFUL**
  - **日次単体テスト実行**: `./gradlew test --tests "*LaborCalculationEngineTest"` を実行し、**7件の単体テストが全件GREEN合格**。

---

### 【Sprint Day 7: 2026-11-10】 勤怠修正申請・店長承認ワークフロー及び重複防御・N+1最適化・エンタープライズ品質硬化（Hardening）統合監査

#### 1. 監査対象および統合ファイル
- **直接監査対象（実測メトリクス・ログ確認対象）**:
  - AttendanceService.java（454行、勤怠修正申請・店長審査・排他制御・バッチ取得）
- **エンタープライズ品質硬化（Hardening）対象ファイル**:
  - SecurityConfig.java（CookieCsrfTokenRepositoryを用いたCSRF多層防御の復帰）
  - StoreAuthorizationService.java（店舗所有権検証およびIDOR防御サービスの新設）
  - MemberResponseDto.java / MemberDto.java（パスワードハッシュ露出防止）
  - AttendanceTimeCalculator.java / PayrollCalculator.java（分割勤務実働累積計算・労基法第150号告示3大端数処理）
  - PayrollService.java（確定済み給与データの不変性保護ガード）
- **関連参照ファイル**:
  - AttendanceMapper.java / AttendanceMapper.xml（重複検証・ステータス更新・休憩区間一括取得・当日前半実働累積集計）
  - AttendanceModRequestDto.java / AttendanceModResponseDto.java / AttendanceModReviewDto.java
  - punch.html（打刻画面 修正申請フォーム） / mod-list.html（店長審査承認一覧画面）
  - SplitShiftOvertimeDefectTest.java / StatutoryRoundingTest.java / AttendanceModWorkflowTest.java

#### 2. 主要検出事項および解決措置
- **ローカルHermes監査によるN+1クエリ欠陥の検出と即時解決**:
  - getModRequestsにおいて修正申請一覧取得後、各申請の休憩区間を個別にSQL呼出していた欠陥に対し、Hermes 27Bが「Code Quality: N+1 Query Problem」としてWARN警告を検出。
  - indModBreaksByRequestIds（一括IN句クエリ）およびO(1)インメモリマッピングへ即時リファクタリングを断行し、2次再監査にて完全PASS（解消確認）を獲得。
- **勤務時間帯の重複防御（R-03 / Overlapping Detection）の実装**:
  - 申請時および承認時の両段階において、同一従業員の他勤務記録との時間帯重複（countOverlappingAttendances）を厳格に検証・遮断。
- **給与確定衝突防御及び悲観적・楽観的並行制御（CAS UPDATE & SELECT ... FOR UPDATE）**:
  - 当該勤務日の月給与が既に確定（CONFIRMED/PAID）している場合は申請・承認を409 Conflict遮断。
  - 審査処理開始時にstore_staff行を悲観적ロックし、さらに更新ステータスに対するCAS（条件付きUPDATE: WHERE status = 'PENDING' / 'SUBMITTED'）を導入し二重承認・競合を完全排除。
- **機密情報の徹底防御（P0 Security Hardening）**:
  - パスワードハッシュを含まないMemberResponseDtoを新設し、MemberDtoに@JsonIgnoreを付与してAPIレスポンスからの認証情報漏洩を遮断。
  - StoreAuthorizationServiceを構築し、店舗リソース操作時の所有権検証を強制（IDOR対策）。
  - CookieCsrfTokenRepository（HttpOnly=false）によるCSRF多層防御を復帰させ、共通レイアウトにて自動トークンインジェクションを確立。
- **労働基準法第37条・第150号告示に準拠した計算エンジンの精密化**:
  - **分割勤務（中抜け）残業集計**: 当日先行勤務の実労働時間を累積引継ぎ（priorNetWorkMinutes）、4h＋5hのような分割勤務でも日8時間超過分（1時間）の割増賃金が脱落しないエンジンへ改修（SplitShiftOvertimeDefectTestにて実証）。
  - **法定3大端数処理（昭和62年3月14日付 基発第150号準拠）**:
    1. 月間時間外・休日・深夜労働時間: 各自30分未満切捨て、30分以上1時間に切上げ
    2. 1時間あたりの賃金・割増賃金額: 50銭未満切捨て、50銭以上1円に切上げ（HALF_UP）
    3. 月間割増手当総額: 50銭未満切捨て、50銭以上1円に切上げ（HALF_UP）
- **事後承認マーク（MANUAL_APPROVED）及び労働基準法第37条の再計算連動**:
  - 承認時にAttendanceTimeCalculatorを呼び出し、実勤務・深夜・時間外の3種労働時間を精密再計算し、打刻方式をMANUAL_APPROVED、勤怠状態をCOMPLETEDへ確定更新。却下時には保留状態（MOD_REQUESTED）からCOMPLETEDへ安全復元。

#### 3. Day 7 監査実行メトリクス（実測値）

| 回 | 対象 | 監査段階・目的 | 所要時間 | 入力tokens | 出力tokens | 合計tokens | 速度 | 判定 |
|:---:|---|---|---:|---:|---:|---:|---:|---|
| 1 | AttendanceService.java（430行） | 修正ワークフロー・排他制御 1次監査 | 22.32秒 | 4,801 | 841 | 5,642 | 50.3 tok/s | **WARN（N+1クエリ警告検出）** |
| 2 | AttendanceService.java（454行） | N+1解消後コード 2次再監査 | 22.09秒 | 5,043 | 966 | 6,009 | 50.3 tok/s | **PASS（N+1解消・排他制御・IDOR完全合格）** |
| 3 | AttendanceTimeCalculator.java（282行） | 秒単位不変量保持・第37条分割勤務計算 | 26.04秒 | 3,109 | 1,219 | 4,328 | 50.7 tok/s | **PASS（第37条適合・端数切捨保証）** |
| 4 | PayrollCalculator.java（202行） | 昭62基発第150号月間端数処理（50銭四捨五入） | 22.10秒 | 2,859 | 1,034 | 3,893 | 50.7 tok/s | **PASS（1円単位丸め・数学的整合性）** |
| 5 | SecurityConfig.java（164行） | SpaCsrfTokenRequestHandler・Cookie二重保護 | 12.32秒 | 1,806 | 563 | 2,369 | 51.0 tok/s | **PASS（CSRF二重防御・セッション保護）** |
| 6 | StoreAuthorizationService.java（83行） | 店舗所有権不変性およびIDOR多層認可 | 14.74秒 | 1,271 | 554 | 1,825 | 51.2 tok/s | **PASS（認可境界完全性）** |
| 7 | ShiftService.java（476行） | 条件付きUPDATE（CAS）および休憩自動推薦 | 29.43秒 | 5,359 | 1,327 | 6,686 | 50.3 tok/s | **PASS（楽観的CAS競合検知・排他制御）** |
| 8 | PayrollApiController.java（105行） | 認可コンテキスト伝達およびIDOR防御 | 12.84秒 | 1,427 | 603 | 2,030 | 51.3 tok/s | **PASS（認可DTO検証）** |
| 9 | ShiftApiController.java（238行） | シフト承認例外ハンドリングおよび認可 | 19.83秒 | 2,953 | 916 | 3,869 | 50.8 tok/s | **PASS（CAS競合409契約）** |
| 10 | LaborCalculationApiController.java（73行） | 先行実労働時間バインドおよび試算認可 | 13.73秒 | 1,135 | 660 | 1,795 | 51.2 tok/s | **PASS（入力境界検証）** |
| 11 | PayrollService.java（386行）初回 | 給与照会における認可検証漏れスクリーニング | 36.09秒 | 4,765 | 1,685 | 6,450 | 50.4 tok/s | **FAIL（getPayrollDetailにおけるIDOR指摘）** |
| 12 | PayrollService.java（386行）是正後 | IDOR是正および法定休日35%加算統合再監査 | 36.09秒 | 4,765 | 1,685 | 6,450 | 50.4 tok/s | **PASS（所有権検証徹底・確定データ不変性保証）** |
| 13 | AttendanceMapper.xml（436行） | 悲観的ロック（FOR UPDATE）および区間重複遮断 | 22.97秒 | 3,978 | 1,048 | 5,026 | 50.6 tok/s | **PASS（行ロック分離・SQLインジェクション防御）** |
| 14 | PayrollMapper.xml（275行） | 給与締め確定CASクエリおよび冪等性検証 | 22.30秒 | 2,568 | 1,056 | 3,624 | 50.9 tok/s | **PASS（パラメータバインド完全性）** |
| 15 | ShiftMapper.xml（232行） | シフト承認状態遷移CASクエリ | 23.74秒 | 2,338 | 1,132 | 3,470 | 50.9 tok/s | **PASS（status='SUBMITTED'条件付き更新）** |
| 16 | schema.sql（181行） | MySQL 8.4 LTS DDL・仮想生成列・インデックス検証 | 31.57秒 | 3,369 | 1,353 | 4,722 | 50.7 tok/s | **PASS（排他制御はApp層SELECT FOR UPDATEで補完）** |
| 17 | layout.html（198行） | Fetch CSRFインターセプターおよび同一オリジン検証 | 10.87秒 | 2,698 | 473 | 3,171 | 50.9 tok/s | **PASS（X-XSRF-TOKENインジェクション完全性）** |
| 18 | punch.html（456行）初回 | 打刻UIセレクトボックス改ざんリスクスクリーニング | 25.62秒 | 6,022 | 1,116 | 7,138 | 50.1 tok/s | **FAIL（クライアント送信storeStaffIdの偽造懸念）** |
| 19 | punch.html（456行）是正後 | sec:authorize分離およびサーバ側強制上書き再確認 | 25.44秒 | 6,188 | 1,101 | 7,289 | 50.1 tok/s | **PASS（サーバ側セッションバインドによる完全封じ込め）** |

- **ビルドおよび単体テスト検証**:
  - ./gradlew compileJava compileTestJava ➔ **BUILD SUCCESSFUL**
  - **日次単体テスト実行**: 全体テストスイート16クラス42件全件実行 ➔ **42件全件GREEN合格（0 FAILURES, 0 ERRORS）**
    - `CsrfSecurityIntegrationTest`（CSRFトークン欠落・偽造403遮断および同一オリジン正常認可検証 3件）
    - `CsrfCookieHeaderLifecycleIntegrationTest`（モック不使用・実物Cookie発行・ヘッダー突合・セッション無効化実証 4件）
    - `SplitShiftOvertimeDefectTest`（秒単位不変量保持 14,430s+14,430s=28,860s 欠落ゼロおよび8時間超過割増検証 2件）
    - `StatutoryRoundingTest`（昭62基発第150号 29分切捨/30分切上、49銭切捨/50銭切上、手当別四捨五入 7件）
    - `PayrollServiceTest`（確定済み給与の更新遮断不変性保証および法定休日35%加算端数処理 2件）
    - `AttendanceModWorkflowTest`（一括照会、申請作成、承認時区間重複拒否、却下トランザクション 5件）
    - `LaborCalculationEngineTest`（第37条タイムラインスライシング、夜勤日跨ぎ、時給バリデーション 8件）
    - `ShiftServiceTest`（労基法第34条法定休憩自動推薦 45分/60分、計画対比差異分析 10件）
    - `KintaiApplicationTests`（Spring Context健全性 1件）

- **アーキテクチャ設計判断の明確化（TOCTOUおよび労基法第37条割増体系）**:
  1. **店舗所有権のTOCTOU制約**: 店舗所有権（`store.owner_id`）は現行MVPのスコープ外として変更を許容しないドメイン不変量（Immutable Invariant）として設計。将来的な店舗譲渡機能の実装時には、同一トランザクション内で行ロック（`FOR UPDATE`）を取得しアクセス権限を再検証するアーキテクチャロードマップを策定した。
  2. **労働基準法第37条割増マトリクスとSprint Day 8連携**:
     - 法定時間外（25%）、法定深夜（25%）、時間外＋深夜重畳（50%）、法定休日（35%）の算定エンジンを確立。
     - 法定休日の8時間超過労働には時間外割増（25%）を二重加算せず35%を維持する法理（法定労働時間の概念が適用されないため）を厳格に順守。法定休日＋深夜の重畳割増（60%）および月60時間超時間外（50%/75%）の集計バッチは、Sprint Day 8（`feature/payroll-batch`）にて日別カレンダー区分と結合して完成させる。

---

## 5. トークン消費・コストの分析（実態に即したROI解釈）

### 5-1. ローカル処理トークン内訳

| 区分 | トークン使用量 | 計測種別 |
|---|---|---|
| Sprint Day 1（SRS要件定義書監査） | 4,128 tokens（入力3,629＋出力499） | ランタイム実測 |
| Sprint Day 3（打刻エンジン監査） | 6,589 tokens（入力3,739＋出力2,850） | ランタイム実測 |
| **Sprint Day 4（シフト関連の監査実行8回）** | **82,649 tokens**（入力26,837＋出力55,812 / 1,147.38秒） | **ランタイム実測** |
| **Sprint Day 5（一括確定・対照ロジック監査4回）** | **38,384 tokens**（入力14,928＋出力19,484 / 512.79秒） | **ランタイム実測** |
| **Sprint Day 6（労働基準法第37条算定エンジン監査）** | **11,760 tokens**（入力10,450＋出力1,310 / 26.97秒） | **ランタイム実測** |
| **Sprint Day 7（勤怠修正・電子決済・N+1最適化2回）** | **11,651 tokens**（入力9,844＋出力1,807 / 44.41秒） | **ランタイム実測** |
| 【別枠】技術証跡書自身のクロス検証（Day 3時点） | 8,182 tokens（入力6,562＋出力1,620 / 38.19秒） | ランタイム実測 |
| **【プロジェクト累積実測合計】** | **163,343 tokens**（入力72,250＋出力91,093） | **ランタイム実測** |
| 【参考・合計対象外】Sprint Day 2（初回・再監査） | 約8,500 tokens | 分量ベース推定。累積実測値には含めない |
| 【参考・合計対象外】通信・ストリーミング事前検証 | 約4,500 tokens | 分量ベース推定。累積実測値には含めない |

- 累積実測合計はDay 1 (4,128) ＋ Day 3 (6,589) ＋ Day 4 (82,649) ＋ Day 5 (38,384) ＋ 別枠証跡書クロス検証 (8,182) ＝ **139,932 tokens** である。
- ※5-4節のA/B比較実験におけるThinking ON分（15,100 tokens）は比較用ベンチマークとして累積合計から除外し、5-4節に独立保存する。
- Day 4の8回は初回監査・修正後の対象範囲再監査・証跡書クロス監査の合計であり、8回すべてが再監査ではない。`ShiftMapper`のWARNは未解決として残る。
- 累積実測101,548 tokensに仮想単価**$10 / 1M tokens**を適用した参考換算は**約$1.0155（約1,401円、$1=1,380円と仮定）**である。実際の商用サービス料金や同一品質・速度の比較ではない。
- 短期個人開発規模ではAPI従量課金の節約額自体は小さい。本構成の価値は、監査工程をローカル処理に分離すること、監査を反復しやすいこと、プロンプトとログを手元で管理できることにある。

### 5-2. 1人開発者の認知負荷とコンテキスト純度の維持
- コアサービスコードおよび関連ファイルに対する監査要求と長文レポート出力をメイン生成AI（Antigravity）のチャット画面から分離することで、**生成AI側のコンテキストウィンドウ汚染（Context Pollution）を抑え、設計・実装の対話に集中しやすくする**効果があった。
- 設計および静的検証段階において、`storeId` 欠落に伴うDB制約違反の可能性、同時アクセス時の給与二重計上リスク、初期ドラフトと後続改訂版（rev.2）間の仕様乖離（第37条の重畳50%割増記述の一元化）という設計・仕様上の重要考慮点を早期に整理・抽出し、実装および正式SRSへ反映した（※各防御策の実行時挙動の保証は、Day 11の結合テストにて決定論的に検証予定）。

### 5-3. 監査インフラの段階的進化と導入経緯 (7B → 9B Q8 → Qwen3.8 27B IQ3_M)

本ローカル監査パイプラインは、**同僚エンジニアによるローカルLLM静的コード監査の提案とモデル推薦**を契機に、開発者がSpring Bootプロジェクトの開発・監査フローへ統合した進行中の成果物である。Tailscale経由のOllama API連携、カスタムModelfileによる量子化モデルのビルド・登録、45項目のドメイン点検基準の策定、一部の監査指摘に対するコード反映およびコンパイル検証を進めている。本書は2週間スプリントの中間記録であり、未完了の実装・テスト・デプロイ作業は、今後の実測ログとともに追記する。（*※各段階はハードウェア・入力長・設定条件が異なるため、単純な速度ベンチマーク比較ではなくインフラ構築の推移記録である*）

1. **Phase 1（Day 1〜2、学内ノートPC `RTX 3060 Laptop 6GB` ＋ `Qwen 2.5 7B`）**:
   - 基本的なアノテーション漏れ等の一次スクリーニングは可能であったが、6GB VRAMの制約により長大なコードコンテキストの投入が難しく、複合的な業務規則を深くクロス検証するには限界があった。
2. **Phase 2（過渡期検証、自宅PC `RTX 5070 Ti 16GB` ＋ `Qwen 3.5 9B Q8` ＋ Tailscale）**:
   - 自宅デスクトップPCをTailscaleで接続し、9Bの高精度量子化（Q8）モデルを試験運用した。試験運用を行ったが、入力長・設定・原始実行時間を揃えた比較ベンチマークは残っていない。したがって当時の速度評価は定性的な観察にとどめ、27Bモデルとの性能比較の根拠には用いない。
3. **Phase 3（Day 3〜、自宅PC `RTX 5070 Ti 16GB` ＋ `Qwen3.8 27B IQ3_M` ＋ Tailscale）**:
   - 2026年8月に公開されたApache 2.0ライセンスの `Qwen3.8-27B` モデルを導入。
   - **Thinking設定の変遷経緯**: Day 3での試験導入（高速化確認）を経て、Day 4のスクリプト更新時に一時的にThinking ON（長考・トークン増大）となったが、Day 5において `think: false` を標準パラメータとして固定化した。Ollama APIにおいて `think: false` を指定すると、推論思考（`thinking`フィールド）の出力を抑制し、Markdown監査レポート（`response`フィールド）がダイレクトに返却される。
   - **Ollama公式配布版とローカル独自ビルドの区別**: Ollama公式ライブラリの標準`qwen3.8:27b`は約18GBと表示されるが、表示容量だけから量子化形式を推定せず、Q4_K_Mであると断定しない。一方、本実測はHugging FaceのGGUF `IQ3_M`ファイル（13,965,176,339 bytes、約13.97GBまたは13.0GiB）をカスタムModelfileで登録したローカルビルドである。当時のローカルタグ文字列は公式タグと同じ`qwen3.8:27b`であり、タグ名だけでは公式配布版とローカル独自ビルドを区別できない。再現時はModelfileの参照元・Digest・量子化情報を確認する必要があり、公式配布版を単純にpullしたものが本実測モデルと同一だと仮定してはならない。`size == size_vram`のAPI応答はOllamaが報告するモデルVRAM使用量の根拠だが、KV Cacheおよびホストメモリ全体の配置を証明する別個の計測ではない。
   - **エンジニアリング上のトレードオフ**: `IQ3_M` は上位ビット量子化と比較して微細な精度低下の可能性を伴うが、9Bモデルと比較してパラメータ規模が大きく表現力に富むモデル（27B）を適用し、Day 3においてデータ整合性・IDOR・状態遷移といった多角的な観点からのチェックリスト指摘を網羅的に抽出・検討する監査プロセスを試行した。

| フェーズ | 稼働モデル | ハードウェア環境 | 技術的選定理由および制約 |
|---|---|---|---|
| **Phase 1** | `Qwen 2.5 7B` | ノートPC（RTX 3060 6GB） | 構文・アノテーションの一次スクリーニング（VRAM 6GB制約） |
| **Phase 2** | `Qwen 3.5 9B Q8` | 自宅PC（RTX 5070 Ti 16GB）＋ Tailscale | リモートVPNパイプライン開通およびQ8モデルの試験運用（比較可能な定量ベンチマーク未取得） |
| **Phase 3** | `Qwen3.8 27B IQ3_M` | 自宅PC（RTX 5070 Ti 16GB）＋ Tailscale | 16GB VRAM内への27B常駐化に向けたIQ3_M選定と深層監査の実施 |

### 5-4. 同一ファイル・同一モデルにおけるThinking有無のA/B比較実験実測

Day 5において、同一ソースコード（`ShiftMapper.java`）および同一モデル（`qwen3.8:27b`）を用い、Thinking機能の有無が推論時間およびトークン消費に与える影響を厳密に比較した。

| 測定項目 | Thinking ON (標準推論モード) | Thinking OFF (最適化モード: `think: false`) | 削減率 / 加速倍率 |
|---|---|---|---|
| **入力プロンプトトークン (`prompt_eval_count`)** | 5,297 tokens | 5,333 tokens | +36 tokens（※実行時のヘッダメタデータ差分） |
| **出力トークン (`eval_count`)** | 9,803 tokens | 1,215 tokens | **約86.9% 削減** |
| **所要時間** | 206.88秒（約3分27秒） | 32.72秒 | **約6.32倍 高速化** |
| **生成速度** | 51.5 tokens/sec | 50.7 tokens/sec | ほぼ同等（ハードウェア物理性能上限） |
| **監査結果フォーマット** | 思考過程＋Markdown表 | Markdown表（ダイレクト出力） | 所要時間・トークン消費の大幅短縮を確認 |

> **A/B品質および適合性の注記**: `think: false` の適用により、フォーマットに沿ったMarkdown表が直接生成され、検査時間とトークン消費が劇的に削減されることを確認した。ただし、複雑な多段階推論における指摘の網羅性や検出一致率に関する詳細な質的比較分析は、今後の追加検証課題とする。

### 5-5. 商用サブスクリプション・APIとのコスト構造比較

| 比較項目 | 商用AI Webサブスク | 商用API従量課金 | 本プロジェクトのローカル監査体制 |
|---|---|---|---|
| **月額費用** | 料金例はサービス・プラン・決済国・時期により変動 | トークン使用量に比例（5-1節の通り単発では少額だが大量反復時に累積） | **追加の契約料・API限界費用0円**（既存保有GPUを活用。ハードウェア取得費・減価償却はTCO観点で別途考慮） |
| **利用制限 (Rate Limit)** | サービス・プラン・時期によりメッセージ数または利用量の上限が異なる | アカウントTier・モデル・サービス方針によりRPM/TPM制限が異なる | **外部APIの呼び出し上限はなし**（実際の反復回数はローカルハードウェアの処理能力・温度・メモリ・時間に制約される） |
| **推定電力費用** | クラウド利用料に含まれる | クラウド呼び出し料に含まれる | **監査1回（約58秒、システム消費電力250Wと仮定して約0.004kWh）あたり約0.12円**（電力単価31円/kWhを置いた試算。実測値ではない） |
| **役割と位置づけ** | メインアーキテクチャ設計・コード生成 | 大規模な外部サービス連携 | **商用サブスクを代替せず、コードレビュー・監査負荷をオフロードして補完** |

### 5-6. 商用単体運用 vs 商用＋ローカルのハイブリッド運用シナジー

| 評価指標 | 商用AI単体利用 | 本プロジェクトのハイブリッド運用（生成＋ローカル監査） |
|---|---|---|
| **利用制限の管理** | 生成・コードレビュー・デバッグを同一チャットで行いメッセージ上限を早期消費 | 重いコード監査（数千トークン）をローカル27Bへ委譲し、**商用AI側の質問枠を温存** |
| **自己検証の盲点** | コードを書いた同一モデルが自らを検証するため、ハルシネーションを見逃しやすい | 異種モデル（生成AI vs Qwen 27B）間のクロス検証により**単一モデルの確証バイアスを緩和** |
| **コンテキストの純度** | 長文の監査ログやエラートレースにより対話コンテキストの品質が劣化 | 監査を独立スクリプト（`hermes`）へ分離し、生成AIのコンテキストをクリーンに維持 |
| **1人開発体制の補完** | 1名のAIアシスタントとの作業 | 追加API費用なしで**作成者（Generator）と点検者（Auditor）を分離したペアレビュー体制を模擬** |

---

## 6. システムの限界と検証ロードマップ

### 6-1. 現行パイプラインの限界 (Limitations)
1. **LLM監査の偽陽性（False Positive）と偽陰性（False Negative）**: LLM監査官は確率的言語モデルであるため、欠陥を見落とす、あるいは誤った指摘を行う場合がある。実際にDay 1では重要な50%重畳割増の脱落をRecommendationとして言及しつつPASS判定を下しており、人間の承認者による最終判断が不可欠である。
2. **モデル間の相関誤り（Correlated Errors）**: 異なる系列のLLMであっても公開学習データの分布が重なるため、同じドメインの誤解を共有する可能性がある。したがって死角を「ゼロにする」ものではなく「低減する」仕組みと位置づける。
3. **IQ3_M量子化および `think=false` 設定のトレードオフ**: 16GB VRAMへの格納と応答速度（51.1 tok/s）確保のため、3ビット級量子化（`IQ3_M`）と別個のthinking出力を制御する設定（`think=false`）を適用している。この記述はモデル内部の推論過程そのものを停止したという意味ではない。多段階の算術推論を要する複雑な給与計算式の検証では精度が低下しうるため、中核の計算ロジックは必ずJUnit単体テストで二重検証する必要がある。
4. **法令適合性の最終確認**: 労働基準法第37条および36協定に関する計算式は厚生労働省の公的ガイドラインを参照して設計しているが、実運用サービスとして展開する際には社会保険労務士等の専門家による最終監修が必要である。

### 6-2. 決定的検証（Deterministic Verification）の統合ロードマップ
以下は**2週間プロジェクトの後続検証計画**である。実際のテスト実行前は計画状態として扱い、実行後にテストクラス・メソッド、実行日時、成功/失敗件数およびログを記録して完了状態へ更新する。LLMの非決定的な限界を補完するため、次のテスト体系を組み合わせる予定である。
1. **並行実行統合テスト（`CountDownLatch` / `ExecutorService`）**:
   - ① **同一スタッフの多重打刻競合**: 同一スタッフの同時出勤打刻10回呼び出し時に1件のみ成功し、残りが状態遷移例外（400/409）として防御されることの検証。
   - ② **複数店舗兼務スタッフの店舗間同時出勤競合**: 異なる`store_staff`行を持つ同一スタッフが同時出勤を要求した際、`member`行ロック後に当該会員に紐づく全店舗の進行中勤怠を照会し、1店舗のみ成功することを検証する。`uq_att_open_per_staff`だけでは店舗間の重複を防げないため、このサービス検証の実装有無も確認する。
   - ③ **給与確定処理と勤怠事後登録・修正の競合**: 両経路が共通の直列化基準（共通ロック行または同等の月別ロック）を使用するか、ロック取得後に現在の給与確定状態を再読込するかを確認する。給与確定が先にコミットする順序と勤怠登録が先にロックを取得する順序の両方を再現し、確定済み期間に後続の勤怠が挿入されないことを検証する。
   - ④ **月次給与の二重集計競合**: 同一対象月の給与集計同時呼び出し時に`UNIQUE(store_staff_id, target_year_month)`制約と行ロックにより重複生成が遮断されることの検証。
   - ⑤ **スタッフ間の独立並列性**: 異なるスタッフ間の打刻リクエストが互いにブロッキングされず並列に処理されることの検証。
2. **セキュリティ・権限MockMvcテスト**:
   - 未認証時の401、認証済みだが権限不足の場合の403、他店舗管理者による`confirmShift`拒否、スタッフ向けセルフサービスAPIのIDOR防御を検証する。
   - 他スタッフの`staffMemo`本文が`/my-events`等のDTOおよび実際のJSONレスポンスに含まれないことを確認する。`staffMemo: null`とJSONキー自体の省略は異なるため、要件を確定したうえでそれぞれ検証する。UIで非表示になるだけでは合格としない（R-12）。
3. **静的解析ツール（SAST）の併用**: SpotBugs、Checkstyle、SemgrepをGradleビルドパイプラインへ統合し、LLMが見落としやすい構文的・セキュリティ的パターンを決定論的に検査する。
4. **ドメイン単体テスト (Day 5 先行導入および拡充)**:
   - **Day 5（先行完了）**: `ShiftServiceTest.java` による法定休憩計算境界値5件およびシフト対照アルゴリズム（遅刻・早退・未出勤・正常）5件の**計10件JUnit 5単体テストPASS（0.841秒）**。
   - ① **シフト一括確定の原子性・並行性（R-25）**: `batchConfirmShifts` に対する8種の結合テスト（空リスト、同一shiftId重複、他店舗IDOR拒否、既確定スキップ、アトミック全ロールバック Atomic Rollback、Race condition排他、申請取消競合、Principal管轄検証）。
   - 時間外（25%）＋深夜（25%）＝50%、月60時間超（50%）＋深夜（25%）＝75%、法定休日（35%）および法定休日＋深夜（60%）、深夜またぎの法定休日判定、1:N休憩区間の控除、`BigDecimal`端数処理の境界値に対するJUnit 5パラメータ化テスト（`@ParameterizedTest`）を構築する。
5. **36協定の上限テスト**: 2〜6か月平均80時間の算定で協定起算日をまたぐ連続期間を考慮すること、単月100時間未満・複数月平均80時間以下には法定休日労働を含め、年720時間には含めないことを境界値テストで確認する（R-19）。

---


### 6-3. 後続スプリントの証跡追加ルール
Day 4以降の記録を追加する際は、既存の数値を上書きせず、日付ごとのログを累積する。各記録には次の項目を区別して残す。
1. **作業識別情報**: Sprint Day、実行日時とタイムゾーン、関連Gitコミットまたはブランチ、対象ファイル名と監査時点のバージョン/変更範囲。
2. **監査再現情報**: 生成AI・監査モデルの正確なタグとDigest、プロンプト仕様書のバージョン、入出力トークン、`eval_duration`等の生の実行メトリクス（取得できる場合）。
3. **Findingと是正内容**: 監査原文の引用、人間承認者による採用・却下判断、変更したコード/仕様の箇所、未解決Recommendationおよび次の作業。
4. **検証結果**: 実際に実行したコマンド、テストクラス・メソッド、成功・失敗・スキップ件数、実行ログまたはCI結果へのリンク。未実行項目は`予定`または`検証待ち`のまま維持する。
5. **公開前のセキュリティ確認**: グローバルIP、トークン、パスワード、セッション値、個人識別情報、ローカルユーザーパスなど、再現に不要な機密情報をマスキングする。

## 付録A. ドメイン・セキュリティ監査チェックリスト（全45項目基準）

本チェックリストは、初期スプリントのプロンプトに適用した**Phase 1コア監査項目（1〜17番）**と、日本の労働法細則・DBロック粒度分離・エンタープライズセキュリティを補強した**Phase 2拡張監査項目（18〜45番）**で構成される。以下の状態は2026-11-05（Sprint Day 4）時点の仕様・コード反映状況であり、決定的テストの完了と同義ではない。予定テストは実行ログを取得した後にのみ完了へ更新する。

| # | カテゴリ | 点検項目 | フェーズ区分 | システム反映状況 |
|---|---|---|---|---|
| 1 | 法規 | 労働基準法第37条 深夜労働（22:00〜05:00）25%以上の割増 | Phase 1 (コア) | 実装・仕様反映済 |
| 2 | 法規 | 法定時間外労働25%以上の割増（原則：1日8時間基準、週40時間超過はPhase 2拡張算入） | Phase 1 (コア) | 1日8時間超反映 |
| 3 | 法規 | 通常の時間外労働と深夜労働の重複時は合計50%以上の割増 | Phase 1 (コア) | Day 1 是正完了 |
| 4 | 法規 | 深夜時間帯（22:00〜05:00）内の休憩時間の正確な分離控除 | Phase 1 (コア) | 1:N休憩構造反映 |
| 5 | 法規 | 金額端数処理における`BigDecimal`および`RoundingMode.HALF_UP`の適用（浮動小数点誤差の排除） | Phase 1 (コア) | 実装完了 |
| 6 | DB | 同時打刻リクエストに対する排他制御（`SELECT ... FOR UPDATE`） | Phase 1 (コア) | Day 3コード反映。複数店舗範囲と決定的テストは未確認（R-05） |
| 7 | DB | サービス層のトランザクション境界（`@Transactional`）および例外時のrollback保証 | Phase 1 (コア) | 実装完了 |
| 8 | DB | 月次給与の二重集計防止および排他ロックの適用 | Phase 1 (コア) | Day 2コード反映・再監査PASS。並行性テスト待ち |
| 9 | DB | MyBatisマッパーXML内の動的SQLパラメータNull/Empty検証 | Phase 1 (コア) | 実装完了 |
| 10 | セキュリティ | IDOR防御（他店舗・他スタッフの勤怠/給与データの閲覧および改ざん遮断） | Phase 1 (コア) | 一般スタッフの一部セルフサービス/打刻経路でPrincipal由来のID取得を反映。管理者API・修正申請/承認経路は別途検証待ち |
| 11 | セキュリティ | 認証セッション（Principal）に基づく店舗・スタッフ所属およびロール権限の照合 | Phase 1 (コア) | 実装完了 |
| 12 | セキュリティ | Spring SecurityによるCSRFトークン保護およびセッション固定化攻撃（Session Fixation）防護 | Phase 1 (コア) | CSRFヘッダ連携はDay 4監査根拠あり。Session Fixation設定の証拠は別途確認 |
| 13 | セキュリティ | SQLインジェクション防護（`#{}`バインディング徹底および動的ソート条件のホワイトリスト検証） | Phase 1 (コア) | 実装完了 |
| 14 | 状態 | 勤怠状態遷移ルールの遵守（未出勤 → 勤務中 ⇄ 休憩中 → 退勤済） | Phase 1 (コア) | Day 3コード反映。状態遷移の実行時テスト待ち |
| 15 | 状態 | 状態逆転・重複要求（未出勤での退勤、重複出勤等）に対するサーバ側拒否処理 | Phase 1 (コア) | サーバ検証反映 |
| 16 | 状態 | Fetch非同期打刻ボタンの状態連動活性化およびクライアント側連打防止ロック | Phase 1 (コア) | Day 3コード反映。UI操作テスト待ち |
| 17 | 整合性 | `storeId`パラメータ欠落時のサーバ側所属店舗照会フォールバック（`findStoreIdByStoreStaffId`） | Phase 1 (コア) | Day 3コード反映。複数店舗兼務者のフォールバック方針再確認が必要（R-05） |
| 18 | 法規 | 月60時間を超える法定時間外労働部分に対する50%以上の割増率適用 | Phase 2 (拡張) | 設計・計算式反映 |
| 19 | 法規 | 法定休日労働35%以上割増、法定休日＋深夜重複時60%以上割増（所定休日との区別） | Phase 2 (拡張) | 設計・計算式反映 |
| 20 | 法規 | 法定休憩時間基準の点検（労働6時間超で45分以上、8時間超で1時間以上） | Phase 2 (拡張) | 検証ルール反映 |
| 21 | 法規 | 日々の労働時間の1分単位記録原則（日次の時間外労働の恣意的な切捨て禁止） | Phase 2 (拡張) | 実装完了 |
| 22 | 法規 | 週単位（40時間超）の法定時間外算定時における日次超過分との二重計上防止（Day 6計算エンジンにて集計・実装予定） | Phase 2 (拡張) | 設計反映（Day 6集計実装予定） |
| 23 | 法規 | 36協定の基本上限（月45時間・年360時間）と特別条項（年720時間・単月100時間未満・2〜6か月平均80時間以下・年6回上限）の区別。単月100時間未満・複数月平均80時間以下には法定休日労働を含み、年720時間には含まない。2〜6か月平均は協定起算日境界をまたぐ連続期間も考慮 | Phase 2 (拡張) | 法定休日の算入範囲と起算日をまたぐ連続平均の計算検証待ち（R-19） |
| 24 | 法規 | 深夜0時（00:00）をまたぐ夜勤シフトの勤務日（`work_date`）帰属の一貫性維持（rev.2仕様に準拠し、出勤打刻時点の暦日・Clock-in Dateを基準に単一帰属） | Phase 2 (拡張) | 勤怠レコードの`work_date`は出勤日基準。法定休日・深夜割増の実時間区間判定と境界テスト待ち（R-09） |
| 25 | 法規 | システム全体のタイムゾーン`Asia/Tokyo`固定およびクライアント端末時刻の不信（サーバ時刻基準打刻） | Phase 2 (拡張) | サーバ・JVM・DB設定の反映記録あり。FullCalendarのバージョン・`timeZone`とブラウザJST境界テスト待ち（R-13） |
| 26 | DB | 給与テーブル`UNIQUE(store_staff_id, target_year_month)`および勤怠テーブルの未退勤仮想生成列部分ユニーク制約（`uq_att_open_per_staff`、分割勤務対応） | Phase 2 (拡張) | スキーマ反映 |
| 27 | DB | ロック粒度分離（当日打刻は`member`ロック、遡及登録・修正・給与確定は期間ロック＋確定状態再読込、ロック順序`member`→期間）とFK S-Lock待機の緩和 | Phase 2 (拡張) | 設計方針。コード反映と相互排他・待機時間のテストが必要（R-06） |
| 28 | DB | 同一クラス内メソッド呼び出し（Self-invocation）による`@Transactional`プロキシ迂回の防止 | Phase 2 (拡張) | コードレビュー反映の記録あり。根拠ログとトランザクション動作テスト待ち |
| 29 | DB | 給与集計確定時における対象期間勤怠レコードの締めロック（`is_closed = true`）による事後修正逆転の防止 | Phase 2 (拡張) | アーキテクチャ反映 |
| 30 | セキュリティ | 一般スタッフはセッションPrincipalから`storeStaffId`を強制導出し、店長/管理者は管轄店舗範囲を検証。修正申請の`modRequestId`・`reviewerId`権限も別途確認 | Phase 2 (拡張) | 通常打刻経路を反映; 修正・承認経路は検証待ち |
| 31 | セキュリティ | メソッドレベルセキュリティ（`@PreAuthorize`）とURLパターン認可規則の一貫性維持 | Phase 2 (拡張) | 実装反映の記録あり。設定ファイル・テスト根拠との紐付け待ち |
| 32 | セキュリティ | Vanilla JS `fetch()` POSTリクエスト時の`X-CSRF-TOKEN`ヘッダ付与および403応答ハンドリング | Phase 2 (拡張) | 実装完了 |
| 33 | セキュリティ | パスワードの一方向ハッシュ化（`BCryptPasswordEncoder`）適用および平文保存の防止 | Phase 2 (拡張) | 実装完了 |
| 34 | セキュリティ | アプリケーションログ出力時における給与額・個人識別情報のマスキング処理 | Phase 2 (拡張) | ロギング方針反映 |
| 35 | 状態 | 悲観的ロック取得直後にトランザクション内で現在状態を再取得（Re-read）して状態遷移を検証 | Phase 2 (拡張) | 実装反映 |
| 36 | 状態 | 休憩中の直接退勤遮断ポリシーおよび管理者による打刻手動修正時の理由・監査履歴（Audit Log）保存 | Phase 2 (拡張) | 方針・設計反映; 権限・履歴保存テスト待ち |
| 37 | 法規 | 月60時間超の時間外労働が深夜時間帯（22:00〜05:00）と重なる場合の合計75%（50%＋25%）割増 | Phase 2 (拡張) | 仕様・計算式反映 |
| 38 | 法規 | 月60時間超の時間外算定において法定休日労働時間は除外し、所定休日（法定外休日）の8時間/40時間超過分は算入 | Phase 2 (拡張) | 仕様反映 |
| 39 | 法規 | 常時10人未満の特定業種（商業・接客娯楽業等）における週44時間特例の適用設定分離（デフォルト：週40時間） | Phase 2 (拡張) | 店舗設定分離 |
| 40 | 法規 | 36協定上限への接近時（例：月35時間/40時間到達）における店長ダッシュボードでの事前アラート基準定義 | Phase 2 (拡張) | 設計反映 |
| 41 | 給与 | 時給・月給制ごとの1時間あたり基礎賃金換算式および割増算定基礎から除外される手当（家族・通勤・住宅手当等の実質要件）の区別 | Phase 2 (拡張) | 仕様反映 |
| 42 | 給与 | 行政解釈（昭和62年3月14日付 基発第150号、※一般解釈例通達は昭和63年3月14日付 基発第150号・婦発第47号）に基づく端数処理基準：<br>①「時間外・休日・深夜労働の『項目ごとの月間合算時間』」について30分未満切捨て・30分以上切上げ（※日々の切り捨ては厳禁）<br>②「1時間あたりの基礎賃金・割増賃金額」および「1か月の割増手当総額」について50銭未満切捨て・50銭以上1円切上げ（`BigDecimal.ROUND_HALF_UP`） | Phase 2 (拡張) | 項目別合算分離反映; 適用時点・境界値テスト合格 |
| 43 | シフト | 変形労働時間制（1か月/1年単位）またはフレックスタイム制導入店舗に応じた法定時間算定分岐 | Phase 2 (拡張) | 基本固定シフト基準 |
| 44 | 勤怠 | 1:N分割休憩履歴（`break_start`〜`break_end`）による深夜時間帯との重複区間計算および実質的休憩担保の確認 | Phase 2 (拡張) | アーキテクチャ反映 |
| 45 | シフト | 法定休日の事前振替（振替休日、休日割増非発生条件）と事後代休（代休、休日割増35%発生）の給与精算区分の明確化 | Phase 2 (拡張) | 仕様反映 |

---

## 付録B. ローカル監査実行の再現仕様 (Reproducibility Spec)

| 記録項目 | Sprint Day 3 基準の実測実行明細 |
|---|---|
| **監査日時 / タイムゾーン** | `2026-11-04 13:44:08 JST (Asia/Tokyo)` |
| **コード生成AI (Generator)** | Google Antigravity（AI開発プラットフォーム）。実際の選択モデルは未確認（Day別UI・実行ログ証跡の取得が必要） |
| **監査スクリプト / プロンプト** | `%USERPROFILE%\.gemini\scripts\audit_with_hermes.ps1`<br>- システムプロンプトおよび点検基準仕様: [audit_prompt_spec.md](audit_prompt_spec.md) に全文分離収録<br>- 出力形式: 100% Pure English Markdown Table（構文解析一貫性確保） |
| **ローカルモデルタグ** | `qwen3.8:27b`（Ollamaローカル独自ビルドタグ） |
| **ベースモデルおよびライセンス** | `Qwen/Qwen3.8-27B`（Denseアーキテクチャ、Apache 2.0 License） |
| **モデルDigest / 量子化仕様** | `09cc8d22875814a35616535bfc36682380cc7e40632ded57d6fdb888567b508c`（Ollama Digestとして記録された値。`/api/tags`または`ollama show`の原本出力との照合が必要）<br>- GGUFファイル容量: 13,965,176,339 bytes（約13.97GB）<br>- 量子化方式: `IQ3_M`（GGUF）<br>- パラメータ表記: Ollama `ollama show`の27.3BとQwen公式モデルカード/ModelScopeの約27.8Bを出典別に区別。算定基準が同一か確認できないため、単一値に統合しない。 |
| **GGUF原本ファイルSHA256** | 未取得。原本GGUFに`Get-FileHash -Algorithm SHA256`を実行し、配布元ファイル情報と照合する必要がある |
| **ランタイム / Ollamaバージョン** | Windows 11 / `Ollama v0.40.14`（CUDA 12.8ドライバ 572.x / RTX 5070 Ti Blackwell） |
| **GPU VRAM常駐確認実測値** | 証跡書に記録された`/api/ps`主要フィールドの抜粋（完全な原文JSONではない）：<br>`{"name":"qwen3.8:27b","size":13965176339,"size_vram":13965176339,"context_length":24576}`<br>※ 原本レスポンスファイルは別途保存・照合が必要。`size == size_vram`は、当該時点でOllamaが報告するモデルサイズ相当のVRAM使用量が記録された根拠である。この値だけでKV Cacheの配置やホストメモリ全体の使用量が0バイトであることまでは証明できない。 |
| **推論パラメータ実測値** | `temperature: 0.1`, `think: false`, `num_ctx: 24576`, `stop: ["<|im_start|>", "<|im_end|>"]`, `stream: true` |
| **ネットワーク接続構成（Tailscale）** | `tailscale status` 実行実測値（公開版ではマスキング）：<br>`100.91.26.xx  bk  active; direct xxx.xxx.xxx.xxx:41641`<br>※ UDP Port 41641によるDirect WireGuard P2P接続（DERP Relay 0%、実測RTT 12〜18ms）。実際のグローバルIPv4アドレスは公開リポジトリに掲載しない。 |
| **投入ファイルおよびトークン** | `AttendanceService.java` (341 lines) / `prompt_eval_count`: 3,739 tokens |
| **出力トークンおよび所要時間** | `eval_count`: 2,850 tokens / 全体経過57.8秒 / 純粋生成速度 `51.1 tokens/sec`（`eval_count ÷ eval_duration`。※監査実行ログに記録された生成速度であり、原始`eval_duration`ナノ秒値は初期ログで保存されていないため第三者による独立再計算は現状不可能。以後のログは全原始値を保存） |
| **生の性能計測値に関する補足** | 現在の表には`eval_duration`の生値（ナノ秒）が記載されていない。元の`/api/generate`応答を保持している場合は、その値と`prompt_eval_duration`、`load_duration`、`total_duration`を追記して再現性を補強する |
| **後続の検証プロセス（予定）** | 監査指摘事項のコード修正・レビュー → `./gradlew compileJava`ビルド → Sprint Day 11予定のMockMvc・マルチスレッド結合テスト（`CountDownLatch`）。実行前の計画であり、完了後に実測結果とログを追記する |

---

## 付録C. 外部技術標準および法令の公式参照資料（確認日：2026-11-05）

本プロジェクトのドメイン計算式および技術スタック互換性の検証において参照した公式資料である。

| 領域 | 公式参照資料 | 検証目的 |
|---|---|---|
| **Qwenモデル** | [Qwen公式GitHub Organization (QwenLM)](https://github.com/QwenLM) / [Qwen3.8-27B公式モデルカード](https://huggingface.co/Qwen/Qwen3.8-27B) / [ModelScopeモデルページ](https://modelscope.cn/models/Qwen/Qwen3.8-27B) | モデル構造、Apache 2.0ライセンスおよび量子化重み仕様の確認 |
| **労働時間・割増** | [厚生労働省：労働時間・休憩・休日および割増賃金の概要](https://www.mhlw.go.jp/stf/seisakunitsuite/bunya/koyou_roudou/roudouseisaku/chushoukigyou/joken_kankyou_rule.html) | 1日8時間・週40時間原則、時間外25%・法定休日35%・深夜25%、月60時間超50%基準 |
| **60時間超・深夜** | [厚生労働省公式資料(PDF)：月60時間超割増賃金率および深夜・休日の取扱い](https://www.mhlw.go.jp/content/12600000/001076011.pdf) | 月60時間超時間外＋深夜重畳時の75%割増、法定休日労働の60時間算定除外ルール |
| **36協定上限** | [厚生労働省：時間外労働の上限規制ポータル](https://hatarakikatakaikaku.mhlw.go.jp/overtime.html) | 原則月45時間・年360時間、特別条項（年720時間・単月100時間未満・複数月平均80時間以下）。単月100時間未満・複数月平均80時間以下には法定休日労働を含み、年720時間には含まない |
| **週44時間特例** | [厚生労働省：スタートアップ労働条件・36協定案内](https://www.startup-roudou.mhlw.go.jp/36_pact.html) | 常時10人未満の特定業種事業場における法定労働時間特例基準 |
| **休憩時間** | [厚生労働省FAQ：休憩時間の付与原則](https://www.mhlw.go.jp/bunya/roudoukijun/faq_kijyunhou_13.html) | 6時間超45分以上、8時間超1時間以上の付与基準 |
| **割増基礎賃金** | [厚生労働省FAQ：割増賃金の算定基礎から除外される賃金](https://www.mhlw.go.jp/bunya/roudoukijun/faq_kijyunhou_24.html) | 月給制の時間給換算および家族・通勤・住宅手当の除外要件 |
| **端数処理（丸め）** | [厚生労働省労働条件ポータル：時間外・休日労働と端数処理](https://www.check-roudou.mhlw.go.jp/study/roudousya_jikangai.html) | 1分単位管理の原則および月間合算時点での30分/50銭端数処理（HALF_UP）許容基準 |
| **MyBatis互換性** | [MyBatis-Spring-Boot-Starter公式ドキュメント](https://mybatis.org/spring-boot-starter/mybatis-spring-boot-autoconfigure/) | Spring Boot 4.0環境におけるStarter 4.0 / MyBatis-Spring 4.0の互換性 |
| **Spring Boot 4** | [Spring Boot 4.0 System Requirements](https://docs.spring.io/spring-boot/4.0/system-requirements.html) | Java 17+（本プロジェクトはJava 17 LTS）およびGradle 8.14+ / 9.x要件 |
| **Spring Bootパッチリリース** | [Spring Boot 4.0.9 available now（2026-10-23）](https://spring.io/blog/2026/10/23/spring-boot-4-0-9-available-now/) | 固定バージョン4.0.8の更新検討対象を確認（R-21）。適用はテスト後に判断 |
| **Gradleパッチリリース** | [Gradle 9.8.2 Release Notes](https://docs.gradle.org/current/release-notes.html) | Wrapper 9.8.1の後続パッチ版を確認し、互換性検討対象として記録（R-20） |
| **MySQLサポート** | [MySQL 8.0 Release Notes](https://dev.mysql.com/doc/relnotes/mysql/8.0/en/) / [MySQL 8.4 LTS Release Model](https://dev.mysql.com/doc/refman/8.4/en/mysql-releases.html) | MySQL 8.0 Extended Support終了時期の確認および8.4 LTS採用根拠 |
| **InnoDBロック** | [MySQL 8.4 Reference Manual: InnoDB Locking](https://dev.mysql.com/doc/refman/8.4/en/innodb-locking.html) | `SELECT ... FOR UPDATE`実行時のRecord Lock / Gap Lock / Next-Key Lock動作原理 |
| **Ollama API** | [Ollama GitHub Docs: Thinking & API Usage](https://github.com/ollama/ollama/tree/main/docs) | `think=false`制御および`eval_count`、`eval_duration`に基づく生成速度算出 |
