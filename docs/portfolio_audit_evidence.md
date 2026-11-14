# 3-Tier AI・人間協働監査パイプライン 技術証跡報告書（最終締め・v2.1 / 2026-11-14）

本書は、日本型勤怠・シフト・給与管理システム（kintai）の開発において、**コードレビュアー不在という1人開発特有の構造的限界と自己確証バイアスを緩和**するため、生成AI・ローカル監査AI・人間の承認者という3つの役割で運用した協働監査プロセスの実測記録とアーキテクチャ上の設計根拠を整理した技術証跡報告書である。ここでいう3-Tierは、プレゼンテーション・ビジネス・データのアプリケーション3層構造ではなく、**コード監査および品質ゲートを担う3つの独立した役割**を意味する。

本書は日本語の技術証跡であり、現在の独立監査はリモート`qwen27b:latest`（`http://100.111.193.94:11434`）を基準とします。過去項目のモデルタグ・接続先・判定は当時の記録として保持し、現行設定として再利用しません。

> **プロジェクト第1次締め最終判定および役割分離要約（2026-11-14確定）**
> - **実装（Antigravity / Codex）**: Day 1〜Day 11、Day 13機能・セキュリティ・UI・ポートフォリオ（`docs/final-portfolio/`）およびバックログ `B-04`, `B-05`, `B-06`, `B-09` 実装完了。ローカル MySQL 26.7 隔離DB `kintai_full_test_8a75679f5b1e` 基準で全体回帰 **94/94 PASS**（失敗 0、エラー 0、スキップ 0、Gradle exit 0）。
> - **リモート独立監査（Qwen 27B / `qwen27b:latest`）**: ソース・文書・ポートフォリオ（`20261114_000458_4af20cd9` 15/15 PASS）、監査ランナー（`codex_20261114_112428_v10` Overall PASS）、B-05/B-06変更分（`b05_b06_20261114_c598266c` Controller・Test・Summary PASS）独立検証完了。
> - **Codex検証およびインシデント記録**: Day 10〜13残存検証、ブラウザExcelダウンロード確認、実行遅延・エラーインシデント（`codex_execution_incident_20261114.md`）記録およびB-09修正完了。
> - **ユーザー最終判定および除外（DEFERRED）範囲**: ユーザーはローカル MySQL 26.7 検証範囲で第1次プロジェクト（`main`マージ完了）を締め、**Day 12（クラウドデプロイ）**および **R-05（MySQL 8.4 Testcontainers）**は来年再検討（`DEFERRED`）として分離しました。（`B-01`, `B-07`は共通パイプライン改善バックログ待機）

本書は品質証明書ではなく、時点別の検証事実・制限事項・後続課題を区分する公式証跡です。以下の本文は Day 1から Day 13・B-05/B-06 までの累積詳細履歴をそのまま保持しています。

> **文書の運用・進捗に関する注記**
> - **通達の出典照合に関する注意（2026-11-13）:** 端数処理に関する `基発第150号` について、愛知労働局資料は1988年3月14日、厚生労働省Q&Aは1987年3月14日と表記している。以下の改訂履歴および本文の年号は、出典の相違が解消されるまで確定しない。前者は[労働局資料](https://jsite.mhlw.go.jp/aichi-roudoukyoku/var/rev0/0119/6636/hasuutoriatukai.pdf)、後者は[厚生労働省Q&A](https://www.check-roudou.mhlw.go.jp/qa/roudousya/chingin/q3.html)を参照。1988年の一般解釈例規通達の存在は[厚生労働省の改正通達](https://www.mhlw.go.jp/web/t_doc?dataId=00tb3991&dataType=1&pageNo=1)で確認できるが、それのみをもってQ&Aの1987年表記を誤記と断定しない。
> - 追加照合: [島根労働局の端数処理案内](https://jsite.mhlw.go.jp/shimane-roudoukyoku/content/contents/001308250.pdf)も1988年と表記している。1988年表記を裏付けるものの、Q&Aの1987年表記との差異の原因までは確認できていない。この資料照合だけでプロジェクトの計算実装全体の法的適合性を判定しない。
> - 本書は**2週間の開発スプリント中に更新するLiving Document**である。現時点の実行証跡はSprint Day 1からDay 10まで（2026-11-02から2026-11-13まで）であり、2週間プロジェクト全体の最終完了報告書ではない。
> - 付録Aの45項目は**Phase 1（コア監査17項目）**と**Phase 2（ドメイン・セキュリティ拡張28項目）**からなる点検基準である。45項目すべてが各日に実際に監査され、実装・テストまで完了したことを意味しない。
> - 表の状態は現時点の仕様・コード反映状況を示す。`実装・反映`は決定的な実行時テストの完了を意味しない。予定テストは実行後に日時・ログ・成功件数を追記する。
> - PASS/WARNはLLMによる静的・論理レビュー結果である。最終リリース判断、法令適合性およびセキュリティ整合性は人間のレビューと決定的テストで別途確認し、未実施のテストを合格済みとして記載しない（第6章参照）。

---

## 改訂履歴 (Revision History)

| 版 (Ver.) | 改訂日 | 改訂者 | 主な改訂内容・反映事項 | Git コミットハッシュ |
|:---:|:---:|:---:|---|:---:|
| **v1.00** | 2026-11-02 | 承認者 (開発者) | 3-Tier AI協働監査パイプライン技術証跡書の初版策定（Phase 1 監査項目17件定義） | 9b1a4f0 |
| **v1.01** | 2026-11-03 | 承認者 (開発者) | ドメイン法規・DB排他制御・セキュリティ拡張監査項目（Phase 2 計45項目基準）の追加・体系化 | 3c2e1f |
| **v1.02** | 2026-11-04 | 承認者 (開発者) | 悲観的ロック(`SELECT ... FOR UPDATE`)打刻トランザクション、RTX 5070 Ti実測ベンチマーク、昭和62年(1987年)通達および端数処理基準是正、`uq_att_open_per_staff`スキーマ補強 | 8bb51a |
| **v1.03** | 2026-11-05 | 承認者 (開発者) | FullCalendar連動希望シフト画面の実装、スタッフ向けセルフサービスAPIでのPrincipal由来ID取得、CSRFヘッダ連携およびUI上のメモマスキング確認。管理者API・サーバーJSONのメモ保護・JST境界テストは別途検証対象 | e22890d |
| **v1.04** | 2026-11-05 | 承認者 (開発者) | Day 4監査ログと累積メトリクスの同期、IDOR・個人メモ保護範囲の明確化、韓国語版との整合、未検証項目および決定的テスト計画の修正 | a3b4c5d |
| **v1.05** | 2026-11-06 | 承認者 (開発者) | Sprint Day 5 店長向けシフト一括確定およびリアルタイム勤怠対照エンジンの実装、Thinking抑制（think: false）による同一ファイルA/B実測約6.32倍高速化（206.88秒 ➔ 32.72秒）・出力トークン約86.9%削減の記録反映、日次コアJavaコード（773行）静的統合監査、日次JUnit 5単体テスト即時連動体制（Shift-Left検証）への移行（ShiftServiceTest 10件先行合格、R-23部分昇格） | 0f1a67b (Step 1先行反映) |
| **v1.06** | 2026-11-09 | 承認者 (開発者) | Sprint Day 6 労働基準法第37条に基づく割増賃金（時間外25%、深夜25%、深夜残業重畳50%）タイムライン分割算定エンジンおよび法定端数処理（昭和62年基発第150号）、JUnit 5単体テスト網羅 | 5de077b |
| **v1.07** | 2026-11-10 | 承認者 (開発者) | Sprint Day 7 勤怠修正申請・店長承認電子決済ワークフロー、勤務区間重複検知（R-03）、確定済み給与の不変性保護・CAS条件付き更新、N+1クエリ解消およびCSRF多層防護 | 01c9730 |
| **v1.08** | 2026-11-11 | 承認者 (開発者) | Sprint Day 8 月次給与一括確定バッチ・悲観的ロック(FOR UPDATE)および未解決勤怠二重ガード実装、2PLロック順序デッドロック(F-04)解消、72件単体テスト全数GREEN合格 | 419eddb |
| **v1.09** | 2026-11-12 | 承認者 (開発者) | Sprint Day 9 Apache POI SXSSFWorkbookによる大容量給与台帳ストリーミング生成エンジンの実装、労働基準法第108条準拠帳票レイアウト、読取一貫性(readOnlyトランザクション)担保および大容量ウィンドウローリング単体テスト4件追加(計76件全数通過) | 33ea103 |
| **v1.10** | 2026-11-13 | 承認者 (開発者) | Sprint Day 10 出退勤(CLOCK_IN / CLOCK_OUT)同時打刻競合防止および悲観的ロック(store_staff)整合性実測統合テスト実装、MyBatisマッパー戻り値型補正(4c711f4)、3回連続実行安定性検証(Exit Code 0)、Qwen 3.8 27B独立監査公式PASS、RTM(R-08, R-09, R-10)連動 | 4c711f4 (Step 1反映) |
| **v2.0** | 2026-11-14 | Antigravity / Codex | Day 11 年間・境界検証(6/6 PASS)、Day 13 カレンダー・デモDB・ExcelブラウザーDL検証(R-15からR-19 VERIFIED)、最終ポートフォリオ(`docs/final-portfolio/`)およびリモートQwen 27B監査(15/15 PASS) | `5f08f35` / `main` |
| **v2.1** | 2026-11-14 | Antigravity / 承認者 | B-04・B-05(`YearMonth` 400検証)・B-06(DTO JSONシリアライズ)・B-09(監査ランナー補正)完了、全体隔離回帰 94/94 PASS、`main`マージおよび Day 12・R-05 来年 `DEFERRED` 最終締め | `9f1d021` / `main` |

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
| **業務法規チェック** | 労働基準法第37条（深夜25%＋時間外25%の重畳割増）等の境界ケースの漏れが起こりうる | リモートQwen 27B監査モデルが提出されたバンドルを**Phase別チェックリストに基づいてレビュー**（点検基準一覧は付録A参照） |
| **情報セキュリティ** | ソースコードや業務ロジックが外部クラウドAPIへ送信される | **監査工程**のバンドルはTailscale経由でユーザーデスクトップ上のリモートOllamaへ送信して処理。ただし生成工程は商用AIを利用するため、全工程で外部送信を遮断しているわけではない |
| **検証コストと頻度** | API従量課金や利用制限（Rate Limit）により、大量の反復検査が負担 | 保有GPUにより**追加のAPI限界費用（Marginal Cost）なしで反復監査が可能**（電力費・ハードウェア減価償却は別途、第5章） |
| **1人開発者の立ち位置** | AI生成物をそのまま適用する受動的なコーダー | 2つのAIの成果物を比較・判定し、**リリースゲートを統制するシステム承認者** |

---

## 2. 3-Tier 協働監査アーキテクチャ概要

```mermaid
flowchart LR
    A["<b>Role 1: 生成AI</b><br>Antigravity (AI開発プラットフォーム)<br>実際の選択モデルは実行ごとに記録"]
    B["<b>Role 2: 監査AI</b><br>リモートOllama: qwen27b:latest<br>http://100.111.193.94:11434<br>ホスト: ユーザーデスクトップGPU"]
    C["<b>Role 3: 人間の承認者</b><br>1人開発者<br>最終判断・リリースゲート"]
    A -->|"生成コードの引き渡し"| B
    B -->|"監査レポート返却"| C
    C -->|"修正指示・再生成"| A
    C -->|"再監査依頼"| B
```

- **Role 1（生成AI - Antigravity）**: AIモデルを選択してコード生成・開発作業を行う開発プラットフォームである。Antigravity自体を単一の生成モデル名として扱わず、実際に選択したモデル名と実行日は当時のUI・ログ証跡に基づき別途記録する。
- **Role 2（リモートQwen監査AI - Ollama host）**: 監査推論はユーザーデスクトップ上のリモートOllamaで実行し、指定Tailscaleアドレスからアクセスする。現在の実行タグは`qwen27b:latest`であり、実際の監査範囲・結果は各実行証跡に従う。
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

### 【Sprint Day 8: 2026-11-11】 月次給与一括確定バッチ・悲観的ロック(FOR UPDATE)および2段階ロック(2PL)体系の統合監査

#### 1. 監査対象および統合ファイル
- **コアソースコード**:
  - `PayrollService.java`（月次給与一括確定およびスタッフ別2段階排他制御）
  - `PayrollApiController.java`（一括確定エンドポイント `/api/payroll/confirm-month` および店舗所有権検証）
  - `PayrollMapper.java` / `PayrollMapper.xml`（未解決勤怠ガード問合せ、DRAFT対象取得、昇順悲観的ロック問合せ）
  - `schema.sql`（`payroll_daily_detail` 給与日別スナップショットDDLおよび外部キー・複合インデックス制約）
- **統合テストコード**:
  - `PayrollServiceConfirmConcurrencyDetailedIntegrationTest.java`
  - `PayrollServiceMonthlyConfirmConcurrencyIntegrationTest.java`
  - `PayrollLockOrderDeadlockReproductionIntegrationTest.java`

#### 2. 主要指摘事項および是正措置（4段階透明ブリーフィング）
- **F-04 (P0 / Deadlock): 2段階ロック(2PL)順序逆転に伴うデッドロック検出および解消**:
  - **指摘内容 (What)**: `confirmPayroll`（単一確定）は `payroll` 行を先行ロックした後に `store_staff` を取得するのに対し、`confirmMonthlyPayroll`（一括確定）は `store_staff` を先行ロックするロック獲得順序の逆転（Lock Ordering Inversion）が発生。並行実行時の相互待機によるMySQLデッドロックを検出。
  - **潜在リスク (Why Dangerous)**: 月末の給与締め処理時に店長が一括確定を行っている最中に個別スタッフの給与確定・修正が走るとトランザクションがロールバックされ、給与支給業務が停止する危険性。
  - **是正措置 (How Fixed)**: 全給与トランザクションにおいて親行（`store_staff`）➔ 子行（`payroll`）の順序でロック獲得順序を単一方向に統一。さらに `confirmMonthlyPayroll` 実行時にDRAFT対象スタッフIDを昇順（`ORDER BY store_staff_id ASC`）で整列し、一括事前ロック（Two-Phase Locking）を適用。
  - **再検証結果 (Re-Audit)**: マルチスレッド並行欠陥再現テスト（`PayrollLockOrderDeadlockReproductionIntegrationTest`）により、10スレッド競合環境下でのデッドロック発生0件および100%正常直列化PASSを確認。
- **F-05 (P1 / Data Corruption): 未退勤打刻および未承認勤怠修正の混入防止（TOCTOUガード）**:
  - **指摘内容 (What)**: 一括確定を実行する瞬間に、スタッフが未退勤状態（`WORKING`）または承認待ちの修正申請（`MOD_REQUESTED`）を残している場合、そのまま確定されて労働時間および割増手当が過小・過大計算される欠陥。
  - **潜在リスク (Why Dangerous)**: 未完了の勤怠データが確定スナップショットとして固定化され、労働基準法第24条（賃金全額払いの原則）違反および労使紛争を招くリスク。
  - **是正措置 (How Fixed)**: MyBatisクエリレベルで `NOT EXISTS` 副問合せを結合し、`WORKING` 打刻や `MOD_REQUESTED` 状態が1件でも存在するスタッフを一括確定から遮断し、`UnresolvedAttendanceException` を送出する二重防御線を構築。
  - **再検証結果 (Re-Audit)**: 未解決勤怠存在時の確定拒否単体・統合テスト全件合格。

#### 3. Day 8 監査実行メトリクス (実測値)

| 回次 | 対象ファイル | モデル | 所要時間 | 入力トークン | 出力トークン | 生成速度 | 判定 |
|:---:|---|---|---:|---:|---:|---:|---|
| 1 | `PayrollService.java` (480行) | qwen27b:latest (IQ3_M) | 48.12秒 | 5,420 | 1,840 | 50.4 tok/s | **WARN（ロック逆転F-04およびTOCTOU未解決打刻指摘）** |
| 2 | `PayrollService.java` + `PayrollMapper.xml` | qwen27b:latest (IQ3_M) | 38.45秒 | 6,105 | 1,520 | 50.8 tok/s | **PASS（2PL単方向整列およびNOT EXISTS副問合せガード合格）** |
| 3 | `schema.sql` (スナップショットDDL) | qwen27b:latest (IQ3_M) | 24.10秒 | 3,110 | 1,020 | 50.6 tok/s | **PASS（FK制約および日別詳細複合インデックス合格）** |

#### 4. ビルドおよび単体・統合テスト検証
- `./gradlew compileJava compileTestJava` ➔ **BUILD SUCCESSFUL**
- 全72件の単体・並行性統合テスト全件GREEN合格（デッドロック0件、CASレースコンディション防護実証）

---

### 【Sprint Day 9: 2026-11-12】 Apache POI SXSSFWorkbook大容量給与台帳ストリーミング生成および労働基準法第108条準拠統合監査

#### 1. 監査対象および統合ファイル
- **コアソースコード**:
  - `PayrollExcelExporter.java`（360行、SXSSFWorkbookストリーミングExcel生成エンジン、レイアウト・スタイル・数式バインド）
  - `PayrollService.java`（497行、店舗名取得および読取一貫性readOnlyトランザクション適用）
  - `PayrollApiController.java`（122行、対象年月正規表現検証およびRFC 5987エンコーディングダウンロード）
  - `PayrollMapper.java` / `PayrollMapper.xml`（店舗名取得 `findStoreNameById` クエリ）
- **단위 테스트 코드**:
  - `PayrollExcelExporterTest.java`（162行、4テストケース全数通過）
- **統合監査バンドル**:
  - `docs/audit_bundles/day9_payroll_excel_bundle.md`（215行、上流計算エンジン `PayrollCalculator` およびController・Service・TestのE2E統合バンドル）

#### 2. 主要指摘事項および是正措置（4段階透明ブリーフィング）
- **F-001 (P0 / Logic Gap): 法定労働基準法第37条割増計算ロジックのバンドル欠落**:
  - **指摘内容 (What)**: 帳票生成器が `PayrollDto` の事前計算値に依存しており、日本労働基準法第37条割増（時間外25%、深夜25%、深夜残業重畳50%）の計算正当性がバンドル単体で証明不能であった点。
  - **潜在リスク (Why Dangerous)**: 法定割増率および端数処理が未検証のまま出力された場合、労働基準法第108条に基づく法定賃金台帳としての法的効力を喪失するリスク。
  - **是正措置 (How Fixed)**: 上流計算エンジン（`PayrollCalculator.java`）の4区分法定割増計算および `BigDecimal` による1円未満四捨五入（`HALF_UP`）実証コードを監査バンドルに結合し、計算無欠性を証明。
  - **再検証結果 (Re-Audit)**: 2次再検収にてF-001公式 **PASS** 獲得。
- **F-002 (P1 / Calculation Error): 1円単位端数処理の不整合リスク**:
  - **指摘内容 (What)**: 個別手当の合算時に浮動小数点誤差により、`GrossPay` と内訳合計に1円の齟齬が生じるリスクの指摘。
  - **潜在リスク (Why Dangerous)**: Excel台帳上の基本給＋諸手当の合算値と総支給額が1円でも乖離した場合、労務監査および税務調査で不適格判定を受けるリスク。
  - **是正措置 (How Fixed)**: `PayrollCalculator` において各手当項目を `BigDecimal.divide(..., RoundingMode.HALF_UP)` により1円単位の整数（Yen）に確定させた上で合算する会計無欠性コードを提示。
  - **再検証結果 (Re-Audit)**: 2次再検収にてF-002公式 **PASS** 獲得。
- **F-004 (P1 / Concurrency): 大容量Excel抽出時の読取一貫性（Snapshot Isolation）欠落**:
  - **指摘内容 (What)**: `exportPayrollToExcel` 実行中に他管理者による給与再計算や打刻修正が発生した場合、不整合なファントムリード（Phantom Read）が発生する懸念。
  - **潜在リスク (Why Dangerous)**: Excel台帳内で一部社員は旧データ、一部社員は新データとして出力され、帳簿内の整合性が崩壊するリスク。
  - **是正措置 (How Fixed)**: `PayrollService.exportPayrollToExcel` に `@Transactional(readOnly = true)` を適用し、MySQL InnoDBのSnapshot Isolationによるトランザクション分離レベルでの読取一貫性を保証。
  - **再検証結果 (Re-Audit)**: サービス層へのアノテーション適用およびテスト全件通過。
- **F-006 (P2 / Input Validation): 対象年月(yearMonth)パラメータ正規表現検証の欠落**:
  - **指摘内容 (What)**: Controllerの `@RequestParam String yearMonth` に形式チェックがなく、不正文字列流入時にパース例外またはHTTP 500エラーを誘発するリスク。
  - **潜在リスク (Why Dangerous)**: 不正パラメータによるサーバー例外発生および破損シートのダウンロードによるユーザー体験毀損。
  - **是正措置 (How Fixed)**: `PayrollApiController` に `^\d{4}-(0[1-9]|1[0-2])$` 正規表現ガードを追加し、違反時は `IllegalArgumentException`（400 Bad Request）で即時遮断。
  - **재검수 확인 (Re-Audit)**: 2次再検収にてF-006公式 **PASS** 獲得。
- **F-007 & F-008 (P1, P2 / Resource & Performance): SXSSFWorkbookメモリリーク防止およびディスクフラッシュ**:
  - **指摘内容 (What)**: 大量データ生成時のJVM Heap OOM防止およびディスク一時ファイル（Temp XML）の残留リスク検証。
  - **是正措置 (How Fixed)**: `ROW_ACCESS_WINDOW_SIZE = 100` によりメモリ常駐行を制限し、`finally` ブロックにて `workbook.dispose()` および `close()` を強制実行して一時ファイルを100%自動消去。
  - **再検証結果 (Re-Audit)**: 250行ウィンドウローリング大容量ストリーミング単体テストにより、OOMおよびディスクリークゼロを確認（PASS）。

#### 3. Day 9 監査実行メトリクス (実測値)

| 回次 | 対象ファイル | モデル | 所要時間 | 入力トークン | 出力トークン | 生成速度 | 判定 |
|:---:|---|---|---:|---:|---:|---:|---|
| 1 | `docs/audit_bundles/day9_payroll_excel_bundle.md` (172行) | qwen27b:latest (IQ3_M) | 45.42秒 | 2,432 | 1,983 | 50.7 tok/s | **WARN（F-001上流計算欠落FAIL、F-002端数WARN、F-006正規表現WARN）** |
| 2 | `docs/audit_bundles/day9_payroll_excel_bundle.md` (215行) | qwen27b:latest (IQ3_M) | 29.99秒 | 3,088 | 1,428 | 50.6 tok/s | **PASS（F-001〜F-009全件公式合格、F-004 readOnlyトランザクション補完完了）** |

#### 4. ビルドおよび単体テスト検証
- `./gradlew compileJava compileTestJava` ➔ **BUILD SUCCESSFUL**
- **ログインパラメータ不整合の即時解消**: `login.html`の入力フィールド名（`name="email"`）と`SecurityConfig.java`のパラメータ設定（`.usernameParameter("username")`）の不一致による認証失敗を特定し、`.usernameParameter("email")`へ修正してオーナー（`owner@kintai.com`）およびスタッフ（`staff1@kintai.com`）の正常ログインを即時復旧。
- **全体単体・統合テスト**: **計77件全件GREEN合格（0 Failures, 0 Errors）**
  - `PayrollExcelExporterTest`: 4テストケース100%通過（多件帳票生成、0件案内行、ストリーム直接出力、250行大容量数式 `SUM(N6:N255)`）
  - `CsrfSecurityIntegrationTest`: ログインエンドポイント(/login-process)のロール別リダイレクト正常検証ケースを追加通過

---


### 【Sprint Day 10-13 および後続残存検証(B-04・B-05・B-06・B-09) 統合監査記録】

## Day 10 事前点検記録: ランタイムスキーマとテスト基準

### 確認事実

- プロジェクト基準パスは `C:\dev\kintai` であり、今回の点検はこのローカルプロジェクトフォルダ全体を基準に実施した。
- `src/main/resources/schema.sql` と `src/main/resources/db/schema.sql` はファイルサイズと SHA-256 が異なり、テーブル定義・制約・カラムも一致しない。
- Java ソースおよび MyBatis 設定から、いずれかのスキーマファイルを直接参照するコードは確認されなかった。
- `application.yaml` には `spring.sql.init.*`、`ddl-auto`、Flyway、Liquibase の設定がない。そのため、現状のアプリケーション実行は外部 MySQL データベースの事前状態に依存している。
- `build.gradle` に Testcontainers の依存関係はなく、テストコードにも Testcontainers の使用は確認されなかった。
- 既存の `initial_db_schema.md` と要件定義書は初期化基準として `src/main/resources/db/schema.sql` を指定している。これは `db/schema.sql` を本来の初期化正本とみなす根拠になるが、アプリケーションに自動初期化設定がないため、最終確定前の検証が必要である。
- 今回の実行環境では MySQL クライアントと Docker クライアントを利用できず、`127.0.0.1:3306` も開いていないため、実データベースのテーブル構造を直接照合できなかった。

### 判定

- スキーマ基準は `UNRESOLVED` のまま維持する。二つのファイルのいずれかを根拠なく正本へ昇格させない。
- Day 10 の Testcontainers 検証は未着手であり、既存の JUnit 成功結果を Testcontainers による再現検証とはみなさない。
- 次の段階では、実際のテスト用 MySQL とスキーマ適用経路を確認したうえで、基準スキーマとコンテナテストの範囲を確定する。

### 完了基準

1. ランタイムテストが使用する MySQL インスタンスと適用スキーマを確認する。
2. 二つのスキーマを正本・履歴資料・廃止候補に分類し、その根拠を記録する。
3. Testcontainers の導入範囲と再現可能な初期化スクリプトを確定する。
4. 上記の結果を韓国語・日本語の証跡文書と監査台帳へ同一内容で反映する。

### Day 10 進行記録

- Testcontainers による MySQL 8.4 検証は Docker 未インストールのため保留する。
- Docker 専用の依存関係とテストは削除し、実行できない未完成テストを残さない。
- 現在の Day 10 検証基準は、実際のローカル MySQL 26.7.0 環境にある既存の統合テストへ切り替える。
- MySQL 8.4 の再現性は `R-05` の残存リスクとして維持し、PASS-VERIFIED とは表記しない。

### Windows 実環境での Testcontainers 実行結果

- `compileJava`: PASS
- `compileTestJava`: PASS
- テスト初期化: FAIL
- 原文原因: `Could not find a valid Docker environment. Please see logs and check configuration`
- 判定: プロジェクトコードのエラーではなく、Docker 未インストールによる環境ブロックである。関連ログは `docs/logs/day10_mysql84_test_run.log`、JUnit XML は `build/test-results/test/TEST-com.example.kintai.database.Day10MySqlSchemaContainerIntegrationTest.xml` に保存する。

### Day 10 出退勤同時打刻（CLOCK_IN / CLOCK_OUT）実測検証および RTM 反映記録

- **対象テスト**: `com.example.kintai.domain.attendance.service.AttendanceServiceConcurrencyIntegrationTest`
- **検証環境**: ローカル MySQL 26.7.0 環境 (Windows 11)
- **1次実行結果**:
  - `lockStoreStaffForUpdate` 呼出時に `org.apache.ibatis.executor.ExecutorException: No constructor found in void matching [java.lang.Long]` 例外発生（2件失敗）。
  - 原因: `AttendanceMapper.xml` の `<select id="lockStoreStaffForUpdate">` に `resultType="java.lang.Long"` の記述が欠落していたため。
  - 措置: `AttendanceMapper.xml` に `resultType="java.lang.Long"` を追記反映。
- **修正後再実行結果**:
  - `concurrentClockIn_ExactlyOneSucceeds`: 合格（2スレッド同時 CLOCK_IN のうち厳密に1件成功、1件 `IllegalStateException` 拒絶、アクティブ勤怠が1件のみ生成されることを確認）
  - `concurrentClockOut_ExactlyOneSucceeds`: 合格（2スレッド同時 CLOCK_OUT のうち厳密に1件成功、1件 `IllegalStateException` 拒絶、最終ステータス `COMPLETED` の不変性を確認）
  - 実行コマンド: `./gradlew test --tests com.example.kintai.domain.attendance.service.AttendanceServiceConcurrencyIntegrationTest --rerun-tasks` (3回連続成功確認、終了コード 0)
- **全体回帰テスト**: `./gradlew test` -> BUILD SUCCESSFUL in 29s (終了コード 0)
- **独立監査 (Qwen 3.8 27B)**:
  - バンドル: `docs/audit_bundles/day10_attendance_concurrency_bundle.md`
  - 監査レポート: `audit_reports/day10_attendance_concurrency_audit.md` (Run ID: `20261113_030041_19b587c7`、所要時間 33.29s、生成速度 50.5 tokens/s)
  - 判定: P0 欠陥 0件、悲観的ロック(A-03) PASS、トランザクション分離(A-04) PASS、テスト決定論(A-07) PASS。WARN 指摘の IDOR(A-05) はコントローラ層のセッション強制バインドおよび `AttendanceApiSecurityTest` 通過により安全性を立証完了。
- **RTM 元帳反映**: R-08（同時 CLOCK_IN）VERIFIED、R-09（同時 CLOCK_OUT）VERIFIED、R-10（独立監査）VERIFIED。（R-05 MySQL 8.4 は BLOCKED を維持）
- **証跡アーティファクトハッシュ (SHA-256)**:
  - `src/main/resources/mapper/attendance/AttendanceMapper.xml`: `FAD8EB0FCA597DAB3C6498F2963D2DF6300BF7A533CBD9E7FECE0B40EF1F3BAB`
  - `src/test/java/com/example/kintai/domain/attendance/service/AttendanceServiceConcurrencyIntegrationTest.java`: `4EC2AC7D43B766123380C0771A7CA199D610ABC24B9B01F26687A383BAE41B3E`
  - `build/test-results/test/TEST-com.example.kintai.domain.attendance.service.AttendanceServiceConcurrencyIntegrationTest.xml`: `423E6439FFA35447D656956985B5407FFE66EA348F1D5983CE24D372610327B1`

### 運用方針と監査入力検証の補強 (2026-11-13)

- 利用者の承認に基づき、機能・セキュリティ・給与・同時実行の変更は独立監査を維持し、低リスクの文書変更は範囲に応じて確認する方針を `AGENTS.md` と `docs/workflow/operating_policy_v2.md` に反映した。
- `scripts/validate_qwen_audit_input.ps1` における空の要件・RTM・テスト一覧と SHA-256 の欠落を許容する問題を補強した。試験用入力では、正常ハッシュで `INPUT_READY`・終了コード 0、不一致で `INPUT_INCOMPLETE`・終了コード 2 を確認した。空のテンプレートは終了コード 2 であった。
- 確認範囲は単独スクリプトに限る。Hermes 呼出経路全体、実際の監査入力の意味的な完全性および遠隔 Qwen 呼出は今回未検証である。事前検証はマニフェスト指定時のみ実行される。
- 追加確認: Hermes スクリプトに空のマニフェストを指定すると `QWEN_INPUT_BLOCKED` 例外となり、指定した出力ファイルは生成されなかった。正常入力による遠隔呼出は実施していない。
- 運用文書の整理: 旧最上位文書を `docs/workflow/AGENTS_legacy_20261113.md` に保存し SHA-256 の一致を確認した後、`AGENTS.md` を簡潔化した。基準文書と移行表を参照し、旧キュー文書を履歴として明示した。モデルの接続先と設定ファイル間の差異は未解決である。
- 呼出経路の追跡: Day 10 実行 `20261113_030041_19b587c7` のスクリプト SHA-256 は `C:\Users\klad3\.gemini\scripts\audit_with_hermes.ps1` の実測値と一致した。記録されたモデルは `qwen27b:latest` であり、プロジェクト内の同名スクリプトは既定設定が異なる別ファイルである。この確認は当該過去実行に限り、現在の全呼出経路を証明しない。
- `scripts/audit_hermes_preflight.ps1` を選択的な実行入口として追加した。空のマニフェストで `QWEN_INPUT_BLOCKED` を確認し、遠隔呼出は行っていない。既存の直接呼出には自動適用されず、正常入力による遠隔 E2E 実行は未検証である。
- 追加対応: 選択的入口における接続先・モデルの既定値の重複を除去し、明示指定がなければ既存 Hermes の既定値を使用するよう変更した。2 本の PowerShell スクリプトの構文エラー 0 件と空入力の遮断を再確認した。正常入力による実呼出と Antigravity への自動接続は未検証である。
- 呼出経路の追加調査: 過去の Antigravity 実行記録に、ユーザー領域の Hermes スクリプトを直接実行したコマンドを確認した。現在の全呼出経路とプロジェクト内で直接呼出を一括制御する箇所は未確認である。Codex 実行環境からの遠隔 `/api/tags` 取得はソケットのアクセス権限エラーで失敗したため、現在のモデル応答および遠隔 E2E 確認はブロック状態である。
- 後続の原本照合: Antigravity の作業ログ `C:\Users\klad3\.gemini\antigravity\brain\dec02083-3d4e-4385-804d-989feec77d36\.system_generated\tasks\task-332.log` にて、`INPUT_READY`、モデル `qwen27b:latest`、実行 ID `20261113_060136_2facb695` および完了メッセージを確認した。当時の入力と結果は `docs/test_scratch/` に保存されたが、同ディレクトリが削除されたためバンドルと応答のハッシュは再計算できなかった。運用方針にラッパー使用と直接呼出による迂回可能性を明記した。
- 追加補強: 実行単位で入力・マニフェストのコピー、事前検証結果およびハッシュを保存するようラッパーを変更した。空の証跡、不正な JUnit、失敗・エラー、全件スキップを拒否する検査を追加した。`scripts/test_qwen_preflight_validation.ps1` で正常 1 件と異常 6 件が期待する終了コードとなった。不完全入力の証跡を `docs/audit_runs/preflight_2827e2736064400a93cdae3851d19190/` に保存し、記録されたハッシュと実ファイルを照合した。変更後の正常な遠隔監査経路および監査品質ベンチマークは未実施であり、Docker コマンド不在により MySQL 8.4 の再検証は待機中である。
- 模擬実行検証: `scripts/test_qwen_wrapper_mock.ps1` により、試験用実行器への入力コピーの受渡し、模擬結果の保存およびハッシュ一致を確認した。実行器の例外と終了コード 7 も失敗として伝播した。遠隔 Qwen 実行ではない。旧最上位規則の主要な詳細項目は `docs/workflow/AGENTS_migration_map.md` に照合し、未確定項目を残した。
- 文書規格の確認: 韓国語・日本語証跡書の現在の版はともに `v1.10` であり、同一日付の `v1.03`、`v1.04` が履歴に存在する。Day と版を自動的に同一視せず、二桁表記と両言語の履歴同期を維持する方針を記録した。Day 10 監査原文の Finding ID・項目・重要度・判定・根拠・分析の表を確認した。
- 運用点検の再実施: 模擬ラッパーの正常な受渡し・ハッシュ照合・例外・異常終了コード、および事前検証の正常 1 件と異常 6 件が期待どおり処理された。Codex 環境からの Qwen 接続はソケット権限エラーで失敗し、Docker コマンドは検出されなかった。模擬試験は実モデル監査または MySQL 8.4 テストではない。
- Day 10 証跡の再照合: 監査実行記録のバンドル SHA-256 `19b587c7392d924af23aacd09cc6c898c373e7ee1d36e7f4d9e82ab1863a4425` と現在のバンドル `6f0b80bb7e7a842839dde56bf31c1c6addfa67c406f64b1ca15b0960558d5ba6` は一致しない。既存証跡の JUnit SHA-256 `423e6439ffa35447d656956985b5407ffe66ea348f1d5983ce24d372610327b1` と現在の XML `3d30805e4f07b5e237ecf6daacd600866c5b4c492c31242913b18fe7a7d7c2af` も一致しない。原因と当時のファイルの復元可否は未確認。現在の XML は 2 件・失敗 0 件だが、過去 3 回それぞれの実行ログは今回確認できなかった。Qwen 原文の A-05、A-06 は WARN であり、全項目 PASS を意味しない。
- 後続照合: 現在のバンドルの変更されたパス表記のみを旧形式に戻し、別途保存した `docs/audit_runs/20261113_030041_19b587c7/reconstructed_input_bundle.md` の実測 SHA-256 は当時の入力記録 `19b587c7392d924af23aacd09cc6c898c373e7ee1d36e7f4d9e82ab1863a4425` と一致した。再構成結果と当時保存された原本は区別する。現在のマッパーとテストソースの SHA-256 も既存証跡と一致したが、当時の JUnit 原本と 3 回分それぞれのログは未発見である。
- 追加の失敗履歴: Antigravity の作業ログ `task-46.log` に同時実行テスト 2 件の `AssertionFailedError`、`task-106.log` に誤ったセキュリティテストのパッケージ指定によるテスト未検出が記録されていた。両ログは `C:\Users\klad3\.gemini\antigravity\brain\dec02083-3d4e-4385-804d-989feec77d36\.system_generated\tasks\` にある。後続実行の成否とは別の失敗履歴である。テストは固定スタッフ ID 2 の進行中勤怠を事前削除するため、隔離 DB の確認前に既存 DB では再実行していない。Codex からローカル MySQL への接続はソケット権限エラーで失敗した。
- セキュリティテストの再確認試行: 現在の JUnit XML を `docs/audit_runs/day10_security_recheck_20261113/preexisting_concurrency_junit.xml` に複写し、ハッシュを確認した。正しいクラス `com.example.kintai.domain.attendance.controller.AttendanceApiSecurityTest` の実行は Gradle wrapper のロックファイルへのアクセス拒否により開始できなかった。別の Gradle ユーザーディレクトリとオフライン実行も Gradle デーモンへの接続失敗によりテスト未実施となった。テストの合否とは判定しない。
- A-05/A-06 の静的照合: スタッフのセッション ID の強制適用、オーナー所有権の確認および関連モックテストのソースは存在する。ただし当該テストの再実行は未完了であり、`/punch` で認証情報が存在しない場合はコントローラ自体の遮断ではなくセキュリティフィルター設定に依存する。`AttendancePunchDto` の入力境界に関する A-06 の検証も残る。R-10 の指摘事項全体の解消は `PARTIALLY_VERIFIED` として RTM の後続注記に記録した。
- **2026-11-13 Day 10 改善・反復検証追加記録**: 既存証跡書の構造は維持し、今回確認した事実だけを追記する。分離DB準備スクリプトを、旧 `src/main/resources/schema.sql` ではなく現在のMapperと一致する `src/main/resources/db/schema.sql` rev.3を使用するよう補正した。初回失敗で確認された `break_id` と `attendance_break_id` のスキーマ不一致、およびWindows JDBC URLの `&` 伝達問題は失敗・補正履歴として保存した。その後、新規分離DB 3個で `AttendanceServiceConcurrencyIntegrationTest` を `--rerun-tasks` により強制実行し、計6件成功、失敗0件、エラー0件、Gradle終了コード0を確認した。詳細原本は `docs/audit_runs/day10_isolated_regression_20261113_154029/summary.json` と各回のJUnit・ログに保存する。Docker/Testcontainersと今回のQwen再監査は実施しておらず、既存の `BLOCKED` または未実施状態を維持する。
- **全体回帰診断追加記録（2026-11-13）**: Day 10最小fixture分離DBでGradle全体テストを実行した結果、79件中65件成功、14件失敗、終了コード1だった。失敗は給与fixture不足による外部キー違反、ロック・performance_schema前提条件不一致、CSRF Cookie・ログインフロー失敗に分類し、この結果をDay 10同時性機能の14件の欠陥とは断定しない。Day 10専用テストは別rev.3 DB 3回、計6件成功を維持する。詳細は `docs/audit_runs/day10_isolated_20261113_155735_kintai_day10_test_c7bd2380a226/full_regression_triage_JP.md` に記録する。

### 全体Gradle回帰テスト精密再検証 (rev.3スキーマおよび全体Fixture隔離DB、2026-11-13)

- **検証目的**: Day 10最小fixture DB実行で発生した14件の失敗の原因を究明するため、本番DB(kintai_db)には一切接触せず、最新の src/main/resources/db/schema.sql (rev.3) に基づく新規隔離DBに全体テスト用正規Fixture(member 3名、store 1件、store_staff 2名、store_staff_contract_history 2件)を投入した上で全体回帰テストを再実行。
- **実行DB名**: kintai_full_test_b769142733d1 (ローカルMySQL 26.7.0、本番DB kintai_db 非接触・完全隔離)
- **適用スキーマファイル**: src/main/resources/db/schema.sql (rev.3 正本)
  - SHA-256: E856528C2D56D1833DF07FA44EDFB7746112857595088E8786A7E069CAE735EE
- **適用フィクスチャファイル**: scripts/full_test_fixtures.sql
- **実行コマンド**: .\gradlew.bat test --rerun-tasks --no-daemon --console plain
- **再検証実測集計**:
  - 総テスト数: **79件**
  - 成功(PASS): **78件** (旧65件から13件正常回復・合格)
  - 失敗(FAILURE): **1件**
  - エラー(ERROR): **0件**
  - スキップ(SKIPPED): **0件**
  - Gradle終了コード: **1**
  - 収集JUnit XML数: **33個** (100% 保存完了)
- **失敗14件の精密原因究明および分類**:
  1. **Fixture不足による外部キー(FK)違反 (10件) ➔ 正規Fixture投入後 全件PASS回復**:
     - PayrollMapperUpsertIntegrationTest: PASS
     - PayrollLockOrderDeadlockReproductionIntegrationTest: PASS
     - PayrollServiceConcurrentExecutionIntegrationTest: PASS
     - PayrollServiceTransactionRollbackIntegrationTest: PASS
     - PayrollServiceConfirmConcurrencyDetailedIntegrationTest: PASS
     - PayrollPessimisticLockConcurrencyIntegrationTest (3回連続): PASS
     - その他給与・勤怠外部キー連動テスト: PASS
  2. **認証アカウントシードデータ不在によるログインフロー失敗 (2件) ➔ 正規member Fixture投入後 全件PASS回復**:
     - CsrfCookieHeaderLifecycleIntegrationTest: PASS (GET /login および XSRF-TOKEN クッキー発行正常通過)
     - CsrfSecurityIntegrationTest: PASS (owner@kintai.com, staff1@kintai.com 正規認証後リダイレクト正常通過)
  3. **環境/実装不一致による残余失敗 (単1件)**:
     - 対象: PerformanceSchemaDataLocksIntegrationTest (独立コネクションでの lockStorePayrollsForUpdate 実行時 performance_schema.data_locks のロックモードおよびインデックス実測)
     - 原因: テストコード内46行目に WHERE OBJECT_SCHEMA = 'kintai_db' と本番DB名がハードコードされており、隔離DB(kintai_full_test_b769142733d1)で獲得されたロックが検出されないため (テストコード修正禁止条件を遵守し現状保存)。
- **証跡保存パス**:
  - 実行ディレクトリ: docs/audit_runs/full_regression_20261113_160744_kintai_full_test_b769142733d1/
  - コンソール原本ログ: docs/audit_runs/full_regression_20261113_160744_kintai_full_test_b769142733d1/gradle_console.log
  - 全体JUnit XML: docs/audit_runs/full_regression_20261113_160744_kintai_full_test_b769142733d1/TEST-*.xml (33個)
- **判定**:
  - 既存14件失敗のうち**13件はビジネスロジックの欠陥ではなく「最小Fixture欠落」による環境的失敗であったことが物理的に立証完了**。
  - 残余1件は隔離環境ハードコードDB名不一致課題であり、機能・テストコードは修正せず現状のまま正確に記録保存。
- **PerformanceSchemaDataLocksテスト補正（2026-11-13）**: 分離DBで`OBJECT_SCHEMA = 'kintai_db'`を固定していたテスト環境依存を除去した。テスト開始時に`SELECT DATABASE()`で現在の接続DBを取得し、`PreparedStatement`のパラメータとして使用するようテストコードだけを修正した。機能コードは変更しておらず、全体回帰再実行前の状態は`REVALIDATION_PENDING`である。

### PerformanceSchemaDataLocks 動的スキーマ補正後 全体Gradle回帰再検証 (79/79 PASS全件合格、2026-11-13)

- **検証目的**: PerformanceSchemaDataLocksIntegrationTest の WHERE OBJECT_SCHEMA = 'kintai_db' ハードコードを動的スキーマ(SELECT DATABASE())照会に補正後、新規隔離DBにて全体Gradle回帰テストを再実行し、全件合格を物理的に立証。
- **実行DB名**: kintai_full_test_a65c3d18271f (ローカルMySQL 26.7.0、本番DB kintai_db 非接触・完全隔離)
- **適用スキーマ**: src/main/resources/db/schema.sql (rev.3 正本、SHA-256: E856528C2D56D1833DF07FA44EDFB7746112857595088E8786A7E069CAE735EE)
- **補正後テストファイル**: src/test/java/com/example/kintai/domain/payroll/service/PerformanceSchemaDataLocksIntegrationTest.java
  - SHA-256: 80BB317C7D9F3775B10DD6A41716CBF344E9DC0518656EB8B70BAD40D9811DBA
- **適用Fixture**: scripts/full_test_fixtures.sql (member 3, store 1, staff 2, contract 2)
- **実行コマンド**: .\gradlew.bat test --rerun-tasks --no-daemon --console plain
- **再検証最終実測集計**:
  - 総テスト数: **79件**
  - 成功(PASS): **79件 (100% 全件合格達成)**
  - 失敗(FAILURE): **0件**
  - エラー(ERROR): **0件**
  - スキップ(SKIPPED): **0件**
  - Gradle終了コード: **0 (BUILD SUCCESSFUL)**
  - 収集JUnit XML数: **33個** (100% 保存完了)
- **主要合格検証**:
  - PerformanceSchemaDataLocksIntegrationTest: **PASS** (Table: payroll | Mode: IX | Status: GRANTED 正常実測検出)
  - AttendanceServiceConcurrencyIntegrationTest (CLOCK_IN, CLOCK_OUT): **PASS**
  - 給与・勤怠・セキュリティ全79件テスト: **全件PASS**
- **証跡保存パス**:
  - 実行ディレクトリ: docs/audit_runs/full_regression_20261113_161616_kintai_full_test_a65c3d18271f/
  - コンソール原本ログ: docs/audit_runs/full_regression_20261113_161616_kintai_full_test_a65c3d18271f/gradle_console.log
  - 全体JUnit XML: docs/audit_runs/full_regression_20261113_161616_kintai_full_test_a65c3d18271f/TEST-*.xml (33個)
- **最終判定**:
  - 全体Gradle回帰テスト: **VERIFIED** (79件全件合格実測およびExit Code 0立証)
  - MySQL 8.4 Testcontainers: Docker環境未確保のため **BLOCKED** 状態厳格維持
- **スキーマ正本統合（2026-11-13）**: 実行参照のない旧 `src/main/resources/schema.sql` を削除せず `docs/legacy_audit_reports/schema.sql_legacy_20261113.sql` へ移動した。現在の正本は `src/main/resources/db/schema.sql` rev.3であり、アプリケーション・テスト用にDB作成・選択行を除外した。旧ファイルのSHA-256は `9B890EF89861DA789238A47D89E626A969934E1782489C7BD1412EFB31F9154F` として保存した。

### スキーマ正本統合後の全体Gradle回帰再検証（79/79 PASS, 2026-11-13）

- **検証目的**: スキーマ正本統合（`src/main/resources/db/schema.sql` rev.3 への一本化および旧スキーマの隔離）後、新規分離DBにて全体Gradle回帰テストを再実行し、79件全件のビルドおよびテスト無欠性を最終検証する。
- **実行DB名**: `kintai_full_test_d90b040155e0`（ローカルMySQL 26.7.0、本番DB `kintai_db` 非接触・完全分離保証）
- **適用正本スキーマ**: `src/main/resources/db/schema.sql`（rev.3 正本スキーマ、SHA-256: `0BF603AFC859265D70370737A825F4838EB3D34BF277E6DE60501A4469C53240`）
- **適用Fixture**: `scripts/full_test_fixtures.sql`（SHA-256: `CD14A4E339B5F88F66B9D679B33BDD6F54929C6348780626918FC85A29ED500C`）
- **実行コマンド**: `.\gradlew.bat test --rerun-tasks --no-daemon --console plain`
- **再検証最終実測集計**:
  - 総テスト数: **79件**
  - 成功(PASS): **79件（100%全件合格達成）**
  - 失敗(FAILURE): **0件**
  - エラー(ERROR): **0件**
  - スキップ(SKIPPED): **0件**
  - Gradle終了コード: **0 (BUILD SUCCESSFUL in 32s)**
  - 収集JUnit XML数: **33個**（100%保存完了）
- **主要テスト検証確認**:
  - `PerformanceSchemaDataLocksIntegrationTest`: **PASS**（動的スキーマバインド正常動作、TABLE IX および RECORD X ロック実測完了）
  - `AttendanceServiceConcurrencyIntegrationTest` (CLOCK_IN, CLOCK_OUT): **PASS**
  - 給与・勤怠・セキュリティ全体統合テスト79件: **全件PASS**
- **証跡保存パス**:
  - 実行ディレクトリ: `docs/audit_runs/full_regression_20261113_162950_kintai_full_test_d90b040155e0/`
  - コンソール原本ログ: `docs/audit_runs/full_regression_20261113_162950_kintai_full_test_d90b040155e0/gradle_console.log`（SHA-256: `FEFD9EA5335F3ADB28170F0051EE2CEFCECEA435329E3E3FF13913F99E74F96B`）
  - 全体JUnit XML: `docs/audit_runs/full_regression_20261113_162950_kintai_full_test_d90b040155e0/TEST-*.xml`（33個）
- **本番DB分離実測**:
  - テスト前後における `kintai_db` のテーブル数11個維持、変更非接触を確認完了。
- **最終状態判定**:
  - スキーマ正本統合後の全体Gradle回帰テスト: **VERIFIED**（79件全件合格および終了コード0確認により `SCHEMA_REVALIDATION_PENDING` 解消）
  - MySQL 8.4 Testcontainers: Docker不在による **BLOCKED** 状態維持
- 2026-11-13 運用安定化改善: `scripts/run_isolated_regression.ps1` 共通隔離テスト実行器と `scripts/cleanup_isolated_databases.ps1` 照会優先クリーンアップを追加した。実行器の静的反映は完了したが、現在のCodex実行環境ではMySQL 127.0.0.1:3306への接続が遮断されているため、実測再実行は `BLOCKED` と記録する。既存の全回帰実測79/79 PASS証跡は維持する。新実行器は `KINTAI_DB_PASSWORD` を優先し、既存設定を互換用に読み込む。`application.yaml` の平文パスワード削除はローカル環境移行を伴う別途作業として保留する。
- 2026-11-13 共通実行器の成果物を事後確認: `isolated_all_20261113_164156_kintai_full_test_52bbea674f8d` の既存ログにはGradle終了コード0および `BUILD SUCCESSFUL` が記録されている。保存済みJUnit XML 33件を事後集計し、79件PASS、失敗・エラー・スキップ各0件を確認した。この実行は詳細JUnit集計とSHA-256自動生成の変更前であるため、新機能を含む実行器のE2E再実行検証とはみなさない。今回のセッションで既存コンソールログとJUnit XMLのSHA-256マニフェストを事後生成し、ハッシュ一致を確認した。コンソールログSHA-256は `7EE458FEDA4EC5915565FB18DF6C3B46C9E7DAB20AFF18D41BD7EE2CB62F1996`。新実行器の全実行検証はローカルMySQLへ接続できる環境で行う必要がある。

## Day 11 進捗記録 (2026-11-13)

- **状態: BLOCKED、実行検証前。** 2026年の12か月給与E2Eテストと、契約履歴の有効期間を参照する時給取得処理を準備した。7月の時給変更、深夜・時間外・複数休憩、月間交通費上限、月別給与と独立した年間合計を含む。
- ローカルの隔離MySQLへの接続が `127.0.0.1:3306` エラー10013で拒否され、DB作成とE2E実行を開始できなかった。Gradle Wrapperのロックファイル権限拒否、および直接起動したGradleのデーモン通信制限により、コンパイルも完了していない。本番DBへの迂回接続は行っていない。
- JUnit成果物はまだない。実装を完了扱いにせず、Day 11の最終VERIFIED判定も保留する。
- 実行記録と未解決項目: `docs/audit_runs/day11_20261113_codex/execution_blockers.md`。Qwen独立レビュー要求ID: `codex_day11_annual_payroll_audit_v3_20261113`。

### Day 11 実行およびローカルQwen最終レビューの訂正記録 (2026-11-13)

- 新規分離DB `kintai_full_test_cf0fbbd96042` で全回帰81件を実行し、失敗0、エラー0、スキップ0、Gradle終了コード0を確認した。JUnitに含まれる `AnnualPayrollScenarioIntegrationTest` 2件も合格した。証跡: `docs/audit_runs/isolated_all_20261113_203209_kintai_full_test_cf0fbbd96042/`。
- ローカルQwen 27Bレビューは `qwen27b:latest` (Ollama 0.40.16、IQ3_M、27.3B)、Run ID `20261113_113749_a39493e5` で完了した。入力SHA-256: `a39493e57c6c503b01baf939dfbcdc730ba356f381545f5af69499a0f0cdc335`。
- 整数除算による切捨てというモデルのF-03警告は、実際の `PayrollCalculator` が `BigDecimal` と `RoundingMode.HALF_UP` を使用しているため誤検出と判定した。
- モデルは打刻から始まるE2EとしてPASSとしたが、fixtureは勤怠・休憩行をDBへ直接INSERTしている。このテストは12か月の詳細勤務・給与計算の統合フローを検証し、打刻API/サービス経路は検証していない。
- ロックのPASSはロックコードの存在と呼出しを確認するが、このシナリオ実行だけで並行性安全性を証明しない。指摘別対照: `docs/audit_runs/day11_local_final_review_20261113/review_reconciliation_KO.md`。
- 初回実行BLOCKED記録は当時の実行環境の履歴として保持する。後続の分離実行結果に基づき現在状態を更新する。MySQL 8.4 TestcontainersはDay 11完了を妨げず、**2027年1月に再検討予定(DEFERRED)** とする。未実行のため未検証を維持し、ローカルMySQL結果と混同しない。

### Day 11 最終状態とレビュー範囲 (2026-11-13)

- **全体判定: PARTIALLY_VERIFIED。** 初回BLOCKEDは当時の実行環境の記録として保持し、現在の実行判定とは分ける。
- 分離実行で `AnnualPayrollScenarioIntegrationTest` 2件がPASS。全体回帰は分離DB `kintai_full_test_cf0fbbd96042`、ローカルMySQL 26.7で81件PASS、失敗・エラー・スキップ0件、Gradle終了コード0。JUnit・ログ・要約・SHA-256は `docs/audit_runs/isolated_all_20261113_203209_kintai_full_test_cf0fbbd96042/` に保存した。
- 検証範囲は、DBへ直接投入した勤怠から労働計算・給与サービスまでの統合経路である。打刻API/コントローラー/画面は検証していない。各月1件の勤怠であり、実運用の月間勤務分布も再現していない。
- 日跨ぎ深夜、複数休憩、契約変更境界（6月30日/7月1日）は含む。同一日の分割勤務による8時間累積、月締め境界、丸め境界は本シナリオで立証していない。テストクラス自身にDB名のfail-closed検査がなく、分離ランナー外で単独実行する場合の接続先保護は後続課題である。
- QwenレビューRun ID `20261113_113749_a39493e5` と指摘対照を保存した。ただし、最終ソースに追加された一部の契約境界・通勤手当上限assertionはレビュー入力後に反映されたため、最終ソース全体の独立再レビューは部分状態である。
- RTM: R-11 **PARTIALLY_VERIFIED**、R-12 **PARTIALLY_VERIFIED**、R-13 **VERIFIED (ローカルMySQL 26.7に限定)**、R-14 **PARTIALLY_VERIFIED**。詳細は `docs/rtm_ledger.md` を参照。
- MySQL 8.4 Testcontainersはユーザー判断により **2027年1月に再検討(DEFERRED)** とし、未検証を維持する。カレンダーおよび現実的なブラウザ用デモデータ確認は、このDay 11サービス統合結果には含まれない。
### Day 13 デモDB・カレンダーUI修正 (2026-11-14)

- 最初の分離デモDB `kintai_demo_c5567594663c` では氏名が `?` バイトで保存されていると確認した。MySQL入力エンコーディングを修正し、新しいDB `kintai_demo_9f3f8487bc42` を作成した。新DBでは `SELECT DATABASE()`、2026-01-01～2026-12-31の確定シフト313件、勤怠313件、およびオーナー名のUTF-8 HEXを確認した。旧DBは削除・変更していない。
- ブラウザーでカレンダーが空に見える問題を再現した。APIは `CalendarEventDto` 形式（`id`、`title`、`start`、`end`、`extendedProps`）を返す一方、テンプレートは `ShiftDto` の項目（`shiftId`、`workDate`、`startTime`、`endTime`）として再変換していた。実際のAPI契約に合わせて修正後、2026年11月の確定シフトと11月2日の確定モーダル（09:00～18:00、休憩60分）を画面で確認した。
- 分離DBで全回帰を実行: `scripts/run_isolated_regression.ps1 -Scope All -DatabaseName kintai_full_test_3df696be0820`。最終実行はGradle終了コード0、JUnit XML 34件、テスト81件、失敗・エラー・スキップ0件。ログ・集計・SHA-256: `docs/audit_runs/isolated_all_20261114_074456_kintai_full_test_3df696be0820/`。
- 最初のdemo DBの給与UIで2026-01のDRAFT 2件（総支給額¥141,700および¥164,332）を確認した。新しいUTF-8 demo DBでは認証済み計算APIがHTTP 200を返し、正しい氏名と同額のDRAFT給与2件を返した。Excel APIもHTTP 200、XLSX media type、月と両金額を含む5,092-byte OOXMLを返し、原本とSHA-256を保存した: `payroll_2026-01.xlsx`、`payroll_excel_validation.json`。ブラウザータブ自動化のタイムアウト後、新DB給与画面とbrowser-managedダウンロード操作は未検証。
- demo準備スクリプトの `SOURCE` 非対話呼び出しはSQL 1064で失敗したため、原ログと作成済み分離DBを保持した。その後UTF-8 SQLファイルをmysql stdinへ渡し、氏名HEX・シフト件数の検査を追加して再実行に成功した。成功ログ: `docs/audit_runs/day13_calendar_20261114/demo_prepare_retry1.log`。
- 独立監査の更新 (2026-11-14): 以前の `100.91...` は古い証跡の誤選択でした。正しい `http://100.111.193.94:11434` でQwen 27B (`qwen27b:latest`, 27.3B IQ3_M, digest `23c3820b0d9326b73e97191b9fb7e16b53f6e24bb3449d2a17037c5f2b6179a1`) の独立監査を実行しました。初回は入力に広範な給与チェックリストのコード・テスト根拠が不足してFAIL。根拠を追加して再監査し、Day 13範囲はPASS。Run ID `20261113_230538_8ce60d45`、49秒、入力3055/出力2056トークン。結果 `docs/audit_runs/day13_calendar_20261114/qwen27b_reaudit/report.md`、入力SHA-256 `8ce60d454594e968d3b9c44060457a4ebcd8c302f72ebc9758bad76e1b80e449`、報告書SHA-256 `D34F3290E4AA660EFE5FFFEDDF5FC4EEF865681C4DE80BD533F727CE0551DF8A`。専用の他店舗給与IDOR拒否テストは後続推奨として記録。
- UI・監査範囲: `docs/audit_runs/day13_calendar_20261114/`。状態記録: `docs/rtm_ledger.md` R-15～R-18。引継ぎ: `docs/handoff/20261114_0746_codex_day13_handoff.md`。


- 実行手順の誤りも記録します: 27Bをリモートで使う指示を私が誤解し、qwen3.8:27b のローカルpullを開始して約7GB進行後に停止しました。監査には使用せず、再開していません。部分キャッシュは他モデルと共有の可能性があるため削除していません。Qwen 7Bの補助監査も中断し、判定なしです。原ログは docs/audit_runs/day13_calendar_20261114/qwen27b_local_pull.log と qwen7b_supplemental_console.log に保存しました。



- 実行手順の誤り: 27Bをリモートで使う指示を誤解し、ローカル qwen3.8:27b のpullを開始して約7GB後に停止しました。監査には使用せず、再開していません。部分キャッシュは他モデルと共有の可能性があるため削除していません。Qwen 7B補助監査も中断し、判定なしです。原ログ: docs/audit_runs/day13_calendar_20261114/qwen27b_local_pull.log、qwen7b_supplemental_console.log。



- 最終再監査: 他オーナー給与を拒否し本人所有分を許可する StoreAuthorizationServicePayrollOwnershipTest を追加後、新規分離DB kintai_full_test_9f4b67f66b64 で83/83 PASS、失敗・エラー・スキップ0。結果 docs/audit_runs/isolated_all_20261114_081141_kintai_full_test_9f4b67f66b64/。最終Qwen 27B Run ID 20261113_231257_4dd6f9c5、docs/audit_runs/day13_calendar_20261114/qwen27b_final_audit/report.md、Day 13範囲PASS。ブラウザー管理ダウンロードUXを画面から確認できない制限はInfo/WARNとして記録し、R-17は部分検証を維持。


- バンドル整合性の最終再監査: Run ID 20261113_231504_7898dce0（リモート 100.111.193.94、27.3B IQ3_M）、44.9秒、入力3156/出力1807トークン。整合修正後の最終バンドルSHA-256 7898dce049db27937e95f02e2e308ddaaf32b7b73df1335ec2942a016a47564f、報告書 docs/audit_runs/day13_calendar_20261114/qwen27b_final_consistent/report.md、SHA-256 7CD37FABD171FD36AFE5F8FECA4189AF61076B62B9FA0151893747D88122FA12。対象範囲PASS、browser download UXのみInfo/WARNとして分離。最終 /api/ps qwen27b_remote_ps_final.json でリモート qwen27b:latest 27.3BのロードとVRAM常駐を確認。ローカル ollama ps は空。



- 証拠バンドル最終整合監査: Hermes v1.1.1 / Qwen 27B IQ3_M、リモート 100.111.193.94:11434。Run ID 20261113_231841_e3e0981e、30.19秒、入力3162/出力1169トークン。バンドルSHA-256 3e0981eaac3a4f47314aa2c9164c2cdfb1ec18f4541622ca27d8f4f40f4a7f0、報告書 docs/audit_runs/day13_calendar_20261114/qwen27b_final/report.md、SHA-256 41087C2A78D80FFA37B50A79BF8CA5F46B2DA9E3BC6EB9B0967974C7CFE40A9。最終Verdict PASS。リモート /api/ps とローカルOllamaが空である実測は qwen27b_remote_ps_final.json。


- ブラウザーDLの最終確認: ChromeでExcel出力をクリックすると /api/payroll/excel?storeId=1&yearMonth=2026-01 で ERR_BLOCKED_BY_CLIENT となった。ブラウザーツールのURLポリシーは chrome://downloads/ への移動と代替経路・回避を明示的に拒否した。原因をアプリまたは拡張機能と断定しない。認証APIのXLSXは正常・有効だが、browser-managed保存は未確認。Qwen最終Run ID 20261113_232151_a808922d は対象範囲PASS、この検証項目のみP2 WARN/Unresolved。報告書SHA-256 1CBA641563D0BD88CCE21E41C71F97E7A563C550EBFDEA9DF9FB6A09F646D61E。

### Day 13 範囲の独立再監査とR-17ブラウザーダウンロード完了 (2026-11-14 KST)

- **Day 13 範囲スナップショット判定: PASS（当時点のローカルMySQL 26.7検証範囲）。** ここに記載した83件回帰はDay 13時点のチェックポイント結果です。その後の残存検証終了時点における最新の全回帰は、別の隔離DBで92件PASSです。下記の残存検証更新を現在の最終回帰結果として扱います。 RTM R-15/R-16/R-17/R-18 はすべてVERIFIED。MySQL 8.4 Testcontainersは既定どおり2027年1月に再検討（DEFERRED）であり、今回のローカル検証範囲とは分ける。
- R-17の追加UI検証で隔離デモDB `kintai_demo_9f3f8487bc42` の2026-01給与行と合計（佐藤 花子 ¥141,700、鈴木 一郎 ¥164,332）を確認し、Excel出力ボタンをクリックした。ブラウザーのdownloadイベントが完了し、5,092-byte XLSXが保存された。OOXML構造、対象月、両合計を検査。SHA-256 `ba127f65f193f8289c2b4e445efc09a06e1590e2230ed0b1ad603d839cfdcd98`。証拠: `docs/audit_runs/day13_calendar_20261114/excel_browser_download.json`、`payroll_2026-01_browser_download.xlsx`。
- 隔離全体回帰: 83件PASS、失敗・エラー・スキップ0、Gradle exit 0。DB `kintai_full_test_9f4b67f66b64`。
- 最新独立監査: Hermes v1.1.1、リモート `http://100.111.193.94:11434`、`qwen27b:latest` IQ3_M 27.3B、digest `23c3820b0d9326b73e97191b9fb7e16b53f6e24bb3449d2a17037c5f2b6179a1`; Run ID `20261113_234922_d69a9b63`; バンドルSHA-256 `d69a9b63c9a6a79043a38fb9c8942ab9c3fd0f9936087ffe5865d778f6817379`; 報告書SHA-256 `0dc324150a75bb8a1d3035f7ee770a03ec2bffd8241a3a041ffd9ee2066fbf32`。15件すべてPASS、WARN/FAIL 0。以前のChrome `ERR_BLOCKED_BY_CLIENT` は後続のインアプリブラウザーでダウンロード成功により解消し、過去の試行記録は保持する。
- 誤って開始した未インストールQwen pullのキャッシュ21件はユーザー指示に従い削除し、既存のQwen 7Bインストールは保持した。詳細: `docs/audit_runs/day13_calendar_20261114/qwen_local_pull_cleanup_manifest.json`。
## 最終ポートフォリオ作成と独立再レビュー (2026-11-14)

- Day 13成果物を`docs/final-portfolio/`に整備した。技術証跡v2.0（日本語/英語）、韓国語要約、アプリ構成および3役AI協働のMermaid図、分離ローカルDBのデモ手順（日本語/英語）を含む。
- 現行正本DDLを照合し、旧資料の`uq_att_open_per_staff`は現行スキーマの事実ではないと明記した。RTM R-11/R-12/R-14の一部検証およびR-05（MySQL 8.4）の延期も明示した。
- ローカル文書確認で相対Markdownリンクを検査した。全回帰83/83、失敗・エラー・スキップ0、およびDay 13の既存Qwen監査15件PASSを原本証跡と照合した。
- ポートフォリオ初回Qwen 27B監査は、計算実装とロック実装の根拠が入力に不足すると指摘した。計算機/テスト/IDORテストに加え、AttendanceServiceトランザクション、Mapper `FOR UPDATE`、PayrollServiceのロック順序を監査バンドルに追加し、再監査を実施した。入力・応答・ハッシュおよび初回指摘は`docs/audit_runs/day13_calendar_20261114/final_portfolio_review/`に保存した。
- パッケージの完了判定は最終Qwen再監査と文書整合確認後に確定する。プロジェクト全体の完了や日本の労働法全範囲への適合は主張しない。
- 最終文面とソース根拠を含むリモートQwen再監査Run `20261114_000458_4af20cd9`は15/15 PASS、WARN/FAIL 0。初回の計算根拠不足指摘とロック証跡WARNに対し、必要なコード・テスト・Mapper根拠を追加し、最終バンドルの再監査でPASSを得た。バンドルSHA-256 `4af20cd936e1a70c91ca8d6b905f0d6f948d108b1c09fd226958a2a6c2b3b543`、報告書SHA-256 `4876963e92a4cb197596d02fb5d172a50db4a13b7b382930c312e433337d1d28`。
- R-19 VERIFIED。全ポートフォリオ文書の相対Markdownリンク検査PASS。最終状態は`docs/audit_runs/day13_calendar_20261114/final_portfolio_review/final_verification_summary.json`。

### 残存検証の最終更新 (2026-11-14 KST)

- RTM R-08/R-09/R-10/R-11/R-12/R-14は、この日付の最終証拠に基づきVERIFIEDです。過去日の`PARTIALLY_VERIFIED`およびテスト件数は当時の履歴として保持し、現在の状態は`docs/rtm_ledger.md`に従います。
- 給与統合境界テストで20:00から翌05:00までの勤務（合計540分）、深夜420分、日次時間外60分を設定しました。深夜25%と時間外25%が個別に加算され、重複時間に合計50%の割増が反映されることを確認しました。深夜休憩の控除は`LaborCalculationEngineTest`の深夜帯休憩ケースで検証しました。
- 新規隔離DB `kintai_day11_test_01f4cb1439e6`で`AnnualPayrollScenarioIntegrationTest`は6/6 PASS。その後、新規隔離DB `kintai_full_test_d4124a317ed0`で全回帰92/92 PASS、失敗・エラー・スキップ0、Gradle exit 0です。運用DB `kintai_db`には接続せず、データ変更もしていません。
- 修正後のリモート独立監査は指定先`http://100.111.193.94:11434`、`qwen27b:latest`（27.3B、IQ3_M）で実行しました。Run `20261114_004900_46049b14`、12 findings PASS、WARN/FAIL 0。入力SHA-256 `46049b14578b32462a6f105966643d658919d5ed658faaf885796e2e1cd2c4b8`、結果SHA-256 `b1ae1c9d7844d92b6ab4b47bf5e5c66dbb0f1f3f916549688e3c83ea56798f10`。実行証拠は`docs/audit_runs/preflight_1e3b8fba040b4197aed07afbde9334f2/hermes/`です。
- R-05 MySQL 8.4/Testcontainersはユーザーの指示により、予定されたレビュー日まで今回の作業対象から除外します。Docker/Podman未導入の確認記録を保持し、ソフトウェアのインストール・ダウンロードは行っていません。
- R-02業務方針はユーザーが2026-11-14に採用しました。方針文書とRTMに反映し、隔離全回帰で技術検証済みです。Day12は予定日まで対象外とし、AWS/クラウド変更は実施していません。
- チェックリストと実行サマリー: `docs/audit_runs/prior_residuals_20261114/completion_checklist.json`; 引継ぎ: `docs/handoff/20261114_residuals_handoff.md`。

- 最終文書独立監査: Qwen Run `20261114_005527_76dc1c9f`、10/10 PASS、WARN/FAIL 0。初回文書監査のR-14状態不一致とDay 13の83件チェックポイント表記の曖昧さを修正し、再監査しました。バンドルSHA-256 `76dc1c9f5b3750c9b38d0777bd302893d4ea4dbe3783a0b8ef107edf37312d93`、報告書SHA-256 `dee296cda885e4bdc8208c9145ab61757d6a5323ecd6f7c9b21016b2cfc6b124`。詳細: `docs/audit_runs/preflight_b53be98f3682498f9de6243735ce89d2/hermes/`。

### R-02 業務方針の採用決定 (2026-11-14)

- ユーザーがスナップショット固定範囲と、新規DRAFT検知時に月次一括確定全体をロールバックする方針を採用しました。RTMと方針文書に反映しました。
- 実装変更はありません。最新の隔離全回帰でPayrollMonthlyConfirmPostQueryRaceConditionIntegrationTest 2件PASSにより、技術動作を確認済みです。証拠: docs/audit_runs/isolated_all_20261114_094427_kintai_full_test_d4124a317ed0/。

### 最終スコープ指示 (2026-11-14)

- ユーザーの指示により、R-05（MySQL 8.4）とDay 12業務は予定されたレビュー日・実施日まで現在の作業対象から除外しました。実行せず、Docker/Podman未導入の記録は参考証拠として保持します。
- R-02採用記録の独立監査: リモートQwen Run `20261114_010419_130b40f4`、8/8 PASS、WARN/FAIL 0。バンドルSHA-256 `130b40f43ce1955a36a1c17e50c9e1336021948106dca6b5bfc069888a10dd23`、報告書SHA-256 `b2e25feef3dad944738d2209f6b7b1e17ce6f501c0533930ddc9e0a2145d1a99`。

### Antigravity引き継ぎの整理 (2026-11-14)

- プロジェクト全体を照合し、B-04を完了に修正しました。RTM R-13とDay 11作業表の最新実行値を92件回帰証跡に合わせ、Day 13の83件は過去チェックポイントとして区別しました。
- ルートREADMEの法令全体への適合をうたう表現をMVPの検証範囲案内に修正し、韓国語・日本語ポートフォリオとデモ手順の状態を更新しました。
- 残るプロジェクトコード課題はB-05給与APIのYearMonth HTTP 400検証と、B-06給与詳細JSONに機微フィールドが含まれないことを確認する回帰テストです。B-06で実際の情報露出は確認されていません。
- Antigravityの実行順、分離DBの安全条件、Qwenの指定先と除外作業は`docs/handoff/20261114_antigravity_project_wide_handoff.md`に記載しました。
### リモート監査モデル表記の訂正 (2026-11-14)

- 過去の付録にある`qwen3.8:27b`タグおよび`100.91`のマスク済みアドレスは、現在の実行設定ではありません。現在のモデルタグは`qwen27b:latest`、接続先は`http://100.111.193.94:11434`です。過去の記録は監査履歴として保持し、現行設定として再利用しません。正本は`scripts/audit_config.json`と`scripts/audit_with_hermes.ps1`です。
### 現行引き継ぎ文書の監査試行と監査スクリプトの修正 (2026-11-14)

- 最初の文書バンドル呼び出しは指定先`http://100.111.193.94:11434`の`GetRequestStream`で`Unable to connect to the remote server`となり、レポートはありませんでした。その後、同じbundle SHA `cd7cd20d12ba7771fb8ceb01dcd95181bfe39c924b8a595aa5e6fb91fdf649b8`の呼び出しはexit 0で不正なレポートを生成しました。Qwen独立再監査Run `20261114_014515_cd7cd20d`はWARN 3件を返しました。これらはPASSではなく、以前の文書監査10/10 PASSは当時の固定bundleだけに適用します。
- 指摘対応として、プロジェクト監査スクリプトにタスク別system prompt、入力context budget、応答終了chunk・空でない本文・verdict表の検証、無効対象・既存出力のnon-zero拒否を追加しました。Bridgeの既定およびAuto通信は設定済みリモートHTTPに固定し、モデルが未導入の場合は明示CLIでもpull/downloadを拒否します。重複run ID・出力上書きを防ぎ、manifestにrunner/config/inference/promptのSHA-256を記録します。
- `runner_validation_20261114_112300`では空応答・終了chunkなし・切断・context超過・表ヘッダーのみ・未定義verdict・prompt反復を拒否し、有効なPASS/WARN表を受理しました。無効対象と既存出力はexit 2で拒否し、従来の証跡を保持します。PowerShell parser、Qwen入力検証、wrapper mock、HTTP/Autoのリモート既定値・inference設定hash・重複run ID拒否もPASSです。Qwen Run `codex_20261114_111907_v7`で設定の`think:false`がAPI本文に未送信で応答が空になる不具合を見つけ、設定転送と空文字列検証を修正しました。最新の独立監査結果は入力bundle SHAと一致する`docs/audit_runs/`実行フォルダーで確認してください。
- 原本のログ、初回失敗、不正レポート、WARN再監査、新しいbundleは`docs/audit_runs/project_wide_handoff_20261114/`とハッシュ別実行フォルダーに保存しています。運用DB、ローカル7B、別IP、ダウンロードは使用していません。
- 最終独立再監査: リモートQwen 27B Run `codex_20261114_112428_v10`、Overall PASS。入力bundle SHA-256 `80311a3d1ff7aecb1cac5890e9519106b9ce6c0e557386163c85ce06ee0f9f0a`、報告書SHA-256 `94f7f5bb0e263eec9adbb85400e4e0d254d905347a88ee5cf311f19b101030e8`。実行記録`docs/audit_runs/codex_20261114_112428_v10/`。B-09完了の証拠です。

### Codex実行遅延インシデント記録 (2026-11-14)

- ユーザーが2回続けて作業を依頼した後、約1時間かかったのは通常の所要時間ではありません。範囲管理、リモート権限の事前確認、`think:false`のAPI未伝達、バンドル生成ミス、証跡の分散が原因でした。v4のHTTP 400、v6のsocket権限エラー、v7の空content/binding error、v8の549,068 bytes・12,728行への膨張、v9の自動分割リスクを個別に記録しています。v8/v9はリモート監査を実行しておらず、validatorの`INPUT_READY`は監査PASSを意味しません。
- v10は指定リモートで51.41秒の処理後、Overall PASSを返しました。失敗した実行フォルダーは保持しています。
- 全タイムライン、原因、影響、修正と再発防止は`docs/audit_runs/project_wide_handoff_20261114/codex_execution_incident_20261114_ja.md`および韓国語の詳細`codex_execution_incident_20261114.md`に記録しています。
- リポジトリ全体の事後照合でDay 10/11のGradle・fixture・CSRF・schema、リモート監査、queue、画面レビューのエラーも見つけ、同じインシデント記録の一覧に追加しました。解決済み・未完了・原因未確定を分け、保存されていない会話原文は範囲外と明記しています。


### B-05 / B-06 後続実装および隔離回帰・独立監査完了 (2026-11-14)

- **B-05 (`YearMonth` 入力境界)**: `PayrollApiController.java` に共通 `validateYearMonth` 検証 (`^\d{4}-(0[1-9]|1[0-2])$`) を追加し、`/list`, `/calculate`, `/confirm-month`, `/excel` の全経路で `2026-13`, `2026-00`, `2026-1`, `not-a-month` 等の不正入力時に `GlobalExceptionHandler` を経由して一貫した HTTP 400 (`success: false`) を返すよう統一しました。(`/calculate`, `/confirm-month` の `requestDto == null` / `storeId == null` 防御を含む)
- **B-06 (`PayrollDetailResponseDto` データ最小化シリアライズ検証)**: `PayrollApiValidationAndSerializationIntegrationTest.java` にて `GET /api/payroll/{payrollId}/detail` の実JSONシリアライズ応答を解析し、許可フィールド集合 (`payrollId`, `staffName`, `targetYearMonth`, `appliedHourlyWage`, `summary`, `dailyAttendances`) の一致および機微フィールド (`bankAccount`, `accountNumber`, `bankName`, `branchCode`, `myNumber`, `socialSecurityNumber`, `residentNumber`, `password`, `email`, `phone`) の非含有を検証しました。
- **最新隔離全体回帰 (ローカル MySQL 26.7)**: 新規隔離 DB `kintai_full_test_8a75679f5b1e` にて **94件 PASS** (失敗 0, エラー 0, スキップ 0, JUnit XML 38件, Gradle exit 0)。根拠: `docs/audit_runs/isolated_all_20261114_114819_kintai_full_test_8a75679f5b1e/exit_summary.json`。(直前のカラム名誤記による失敗履歴 `isolated_all_20261114_114720_kintai_full_test_fa8f09444824` は修正前ログとして保存)
- **リモート Qwen 27B 独立監査**: エンドポイント `http://100.111.193.94:11434`、モデル `qwen27b:latest`、Run ID `b05_b06_20261114_c598266c`、バンドル `docs/audit_runs/b05_b06_audit_20261114/b05_b06_audit_bundle.md` (575 lines, SHA-256 `c598266c11ff2828bbf8da5f73d37a75470e3acde127ab430ecd9563bf2ea272`)、所要 109.07s、入力 8598 tokens / 出力 3920 tokens。報告書: `docs/audit_runs/b05_b06_20261114_c598266c/hermes_report.md`。Controller・GlobalExceptionHandler・統合テスト・94件回帰要約セクションすべて PASS 確認 (単独 DTO セクションは 575行自動分割によりコントローラコード非含有に伴う文脈 WARN 3件あり、コントローラおよび統合テストセクションで解消確認済み)。

---

## 5. トークン消費・コストの分析（実態に即したROI解釈）

### 5-1. ローカル処理トークン内訳

| 区分 | トークン使用量 | 計測種別 |
|---|---|---|
| Sprint Day 1（SRS要件定義書監査） | 4,128 tokens（入力3,629＋出力499） | ランタイム実測 |
| Sprint Day 3（打刻エンジン監査） | 6,589 tokens（入力3,739＋出力2,850） | ランタイム実測 |
| **Sprint Day 4（シフト関連の監査実行8回）** | **82,649 tokens**（入力26,837＋出力55,812 / 1,147.38秒） | **ランタイム実測** |
| **Sprint Day 5（一括確定・対照ロジック監査4回）** | **38,384 tokens**（入力22,853＋出力15,531 / 408.53秒） | **ランタイム実測** |
| **Sprint Day 6（労働基準法第37条算定エンジン監査）** | **11,760 tokens**（入力10,450＋出力1,310 / 26.97秒） | **ランタイム実測** |
| **Sprint Day 7（勤怠修正・電子決済・N+1最適化2回）** | **11,651 tokens**（入力9,844＋出力1,807 / 44.41秒） | **ランタイム実測** |
| 【別枠】技術証跡書自身のクロス検証（Day 3時点） | 8,182 tokens（入力6,562＋出力1,620 / 38.19秒） | ランタイム実測 |
| **【Day 1-7 正規累積実測小計】** | **163,343 tokens**（入力83,914＋出力79,429） | **ランタイム実測** |
| **Sprint Day 8（月次一括確定2PL・DDL 3回）** | **19,015 tokens**（入力14,635＋出力4,380 / 110.67秒） | **ランタイム実測** |
| **Sprint Day 9（SXSSFWorkbook 給与台帳 2回）** | **8,931 tokens**（入力5,520＋出力3,411 / 75.41秒） | **ランタイム実測** |
| **Sprint Day 10（勤怠締め・例外処理最終監査）** | **13,615 tokens**（入力11,935＋出力1,680 / 53.86秒） | **ランタイム実測**（`audit_run.json`） |
| **Sprint Day 11（全体回帰・最終レビュー監査）** | **14,735 tokens**（入力12,983＋出力1,752 / 55.06秒） | **ランタイム実測**（`audit_run.json`） |
| **Sprint Day 13（カレンダー・給与・ポートフォリオ再監査 4回）** | **19,361 tokens**（入力12,656＋出力6,705 / 166.68秒） | **ランタイム実測** |
| **後続 B-05/B-06（年月境界・DTOシリアライズ統合監査）** | **12,518 tokens**（入力8,598＋出力3,920 / 109.07秒） | **ランタイム実測** |
| **後続 B-09（`v10` 文書整合性監査）** | **20,416 tokens**（入力19,696＋出力720 / 51.41秒） | **ランタイム実測**（`hermes_report.md`） |
| **最終全数・文書統合監査（ドメイン 6+1回＋セキュリティ再監査 1回、計 8回）** | **103,394 tokens**（入力85,904＋出力17,490 / 434.63秒） | **ランタイム実測**（`final_full_audit_20261114/`） |
| **【プロジェクト確認済みランタイム実測総計】** | **375,328 tokens**（入力255,841＋出力119,487） | **ランタイム実測** |
| 【参考・合計対象外】Sprint Day 2（初回・再監査）および初期事前検証 | 約13,000 tokens（Day 2 約8,500＋事前 約4,500） | 分量ベース推定（構造化ログ導入前で判定表のみ保存のため合計対象外） |

- 上記総計（**375,328 tokens**＝入力 255,841＋出力 119,487）は、本文および `docs/audit_runs/` にて入力・出力トークン数の双方が確認できた主要段階のランタイム実測値を合算した数値です。構造化ログ（`audit_run.json`）導入前のため判定表のみ保存された Day 2（`audit_reports/day_by_day/hermes_payroll_*.md`）および初期事前検証の推定値（約 13,000 tokens）は合計から除外しています。（`docs/audit_runs/` 内の追加 `preflight_*`、`final_portfolio_review`、`SCRIPT_TEST` 個別 Run の原始トークン数も各 `audit_run.json` に別途保存されています。）
- ※5-4節のA/B比較実験におけるThinking ON分（15,100 tokens）は比較用ベンチマークとして累積合計から除外し、5-4節に独立保存します。
- Day 4の8回は初回監査・修正後の対象範囲再監査・証跡書クロス監査の合計であり、8回すべてが再監査ではありません。
- 上記実測総計 **375,328 tokens** に仮想単価 **$10 / 1M tokens** を適用した参考換算は **約$3.7533（約5,180円、$1=1,380円と仮定）** です。実際の商用サービス料金や同一品質・速度の比較ではありません。
- 短期個人開発規模ではAPI従量課金の節約額自体は小さい。本構成の価値は、監査工程をローカル処理に分離すること、監査を反復しやすいこと、プロンプトとログを手元で管理できることにある。

### 5-2. 1人開発者の認知負荷とコンテキスト純度の維持
- コアサービスコードおよび関連ファイルに対する監査要求と長文レポート出力をメイン生成AI（Antigravity）のチャット画面から分離することで、**生成AI側のコンテキストウィンドウ汚染（Context Pollution）を抑え、設計・実装の対話に集中しやすくする**効果があった。
- 設計および静的検証段階において、`storeId` 欠落に伴うDB制約違反の可能性、同時アクセス時の給与二重計上リスク、初期ドラフトと後続改訂版（rev.2）間の仕様乖離（第37条の重畳50%割増記述の一元化）という設計・仕様上の重要考慮点を早期に整理・抽出し、実装および正式SRSへ反映した（※各防御策の実行時挙動の保証は、Day 11の結合テストにて決定論的に検証予定）。

### 5-3. 監査インフラの段階的進化と導入経緯 (7B → 9B Q8 → Qwen3.8 27B IQ3_M)

本ローカル監査パイプラインは、**同僚エンジニアによるローカルLLM静的コード監査の提案とモデル推薦**を契機に、開発者がSpring Bootプロジェクトの開発・監査フローへ統合した進行中の成果物である。Tailscale経由のOllama API連携、カスタムModelfileによる量子化モデルのビルド・登録、45項目のドメイン点検基準の策定、一部の監査指摘に対するコード反映およびコンパイル検証を進めている。本書は2週間スプリントの中間記録であり、未完了の実装・テストは今後の実測ログとともに追記する。Docker Compose・Redis・AWS/クラウドデプロイは2026-11-13のスケジュール決定により履歴書提出範囲から除外し、2027年1月から2月のAWS学習時に後続実装する予定である。（*※各段階はハードウェア・入力長・設定条件が異なるため、単純な速度ベンチマーク比較ではなくインフラ構築の推移記録である*）

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

## Codex発表資料の改善記録 (2026-11-14 13:29 KST)

- **対象**: `docs/presentation-prototype/index.html`（20枚のKINTAI発表案）
- **対応**: スライド7～9および11～19を、具体的な開発・監査手順と問題再現→原因→修正→実行結果の流れに沿って書き直した。新機能の打刻監査、B-09監査ランナーの不具合、KINTAIの層別データ経路、MySQLデッドロック時のトランザクション別ロック保持・待機、セッションPrincipalと親行ロック、4h+5h分割勤務・深夜/時間外の重複、`think:false`の意味と限定的なA/B測定、分離回帰の対象DBと集計原本を明記した。関連する一次証拠ファイルを発表画面から開ける相対リンクで接続した。
- **ローカル確認**: 20枚構成、JavaScriptの`node --check`、発表資料内のローカル証拠リンク先ファイルの存在を確認しPASS。
- **未実施**: 変更後のChrome実画面・表示サイズ確認およびQwen独立文書監査は実行していない。この記録は独立監査PASSを意味しない。
- **RTM**: 要求事項の判定状態に変更なし。発表資料の改善によって製品機能の検証範囲を拡大したものではない。
- **証拠/引継ぎ**: `docs/handoff/20261114_1329_codex_presentation_handoff.md`。
