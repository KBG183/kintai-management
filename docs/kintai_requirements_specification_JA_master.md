# 📘 日本型シフト・勤怠・給与精算システム マスター技術仕様書および詳細要件定義書 (SRS)

> **システム名**: 日本型シフト・勤怠・給与精算システム (勤怠・シフト・給与管理システム / Kintai Management System)  
> **文書種別**: プロジェクト概論書 · アーキテクチャ設計書 · DB物理仕様書 · 業務ドメイン実装ガイド統合版  
> **ビルド基準**: uild.gradle 基準 Java toolchain 17, Spring Boot 4.0.8, MyBatis Spring Boot Starter 4.0.1, Spring Security, Thymeleaf, Spring WebMVC, Bean Validation, Apache POI 5.2.5。検証環境は ローカル MySQL 26.7（MySQL 8.4 互換）。画面ライブラリおよびその他の設定は実際の設定ファイルに準拠します。  
> **文書の目的と運用**: 本文書は企画・要件・DB・API・画面・法令計算ルールを一元管理する Single Source of Truth (SSOT) です。実装前に実プロジェクトのファイル群と照合し、仕様とコードに齟齬が生じた場合は検証証跡に基づき継続的に同期します。本文書は要件基準であり、記載のみをもって実装完了と判断しません。

---

> **文書ステータスおよび適用原則**
> - 本プロジェクトは**個人開発プロジェクト**です。実装スコープおよび優先順位は開発者が決定し、直接実装および検証を行い、AIツールは指示されたタスクの補助として利用します。AIの生成結果や文書上の完了表記は実行証跡を代替しません。
> - MVPの現在スコープ、検証ステータス、延期項目は日付付き作業ログ・RTM・証跡書と照合します。要件仕様と実際の実装・検証状態を厳格に区分します。
> - LINE通知・BI分析・多店舗合算労働時間管理等は実装完了とみなさず、正式決定まで拡張候補（Phase 2）として維持します。
> - **法令遵守範囲**: 本プロジェクトは技術検証・ポートフォリオ向けです。実務の給与支払いに適用する前には、地域・期間・労働者区分・法令改正事項を確認し、社会保険労務士・税理士等専門家の監修を受ける必要があります。

---

## 第0章. プロジェクト概論およびアーキテクチャ哲学 (Project Overview & Philosophy)

### 0.1 なぜ本システムを開発するのか？ (背景および課題定義)
日本の飲食業（居酒屋、カフェ、レストラン）および小売業（コンビニエンスストア、ドラッグストア）のアルバイト・パート労働環境では、複数店舗にまたがる雇用契約、変形シフト調整、リアルタイム打刻記録、割増手当計算が店舗ごとに分断されて管理されています。手書きタイムカード、メッセンジャーアプリ、手作業のエクセル集計に依存すると、記録漏れ、契約条件の混同、承認履歴の欠如、計算齟齬が恒常的に発生します。

1. **多店舗掛け持ち勤務と契約条件の一元管理**: 1人のスタッフが複数店舗で就労する場合、店舗ごとに基本時給・通勤手当・勤務条件が異なります。店舗横断の契約マスタと所属履歴の分離が必要です。
2. **シフト希望と確定案の整合性および履歴保全**: スタッフの希望シフトと店長の確定シフトを明確に区別し、提出・確定・差戻しの全履歴を追跡可能にする必要があります。
3. **打刻漏れ防止および監査証跡 (Audit Trail)**: 出勤・退勤の打刻漏れや誤打刻に対し、直接上書き修正を禁止し、申請・承認ワークフローによる変更前後の証跡保全が不可欠です。
4. **法令に準拠した割増賃金計算エンジン**: 深夜労働 (22:00 ~ 翌05:00, +25%)、時間外法定労働 (1日8時間超過, +25%)、無給休憩時間の厳格な分離控除を分単位で計算し、1円の計算誤差も許さない堅牢性が求められます。

> **設計思想**: 「雇用契約マスタ $\rightarrow$ シフト計画 $\rightarrow$ リアルタイム打刻 $\rightarrow$ 法令遵守給与計算」の4大ドメインを単一のデータフローとトランザクション境界で統合し、確定後の帳簿不変性 (Immutability) を担保します。

---

### 0.2 課題と主要成功要因 (CSF) のマッピング

| 部門 | 解決すべき課題 | 技術的解決方針 | 完了基準 (テスト検証可能な条件) |
| :--- | :--- | :--- | :--- |
| **1. 契約マスタ** | 店舗ごとの契約条件混同、他店舗データの不正アクセス、契約変更履歴の消失 | member, store, store_staff および契約履歴管理。サーバー側でのロール・所属店舗検証 (IDOR防御) | スタッフは所属店舗のみアクセス可能であり、契約変更後も過去の履歴を正確に照会できること |
| **2. シフト計画** | 希望案と確定案の照合不可、重複時間帯の発生、人員不足の放置 | FullCalendar 連携。状態遷移バリデーション、時間帯重複検査、ピーク時人員不足アラート | 希望提出・確定・差戻しが厳格に区別され、カレンダー境界日の表示不整合が発生しないこと |
| **3. 勤怠打刻** | 重複出勤打刻、休憩累積の計算誤差、恣意的なデータ改ざん | 状態遷移マシン。複数休憩区間の正規化保存。修正申請・承認・差戻しの監査証跡 | 重複打刻が遮断され、修正前・申請値・承認適用結果および承認者が完全に追跡可能であること |
| **4. 給与精算** | 深夜・残業の重畳割増計算ミス、契約変更時の過去遡及、確定後データの変造 | 1分単位の労働時間算出。手当別独立計算ロジック。スナップショット保存による帳簿ロック | 境界値テストを100%通過し、確定済み給与台帳が通常の再計算で上書き変更されないこと |
| **5. 拡張機能** | 連絡遅延および人件費分析の不足 | LINE Notify / WebSocket / BI ダッシュボードを独立した拡張モジュールとして設計 | コアMVP完了後に優先順位を判定し、段階的に導入すること |

**完了定義 (Definition of Done)**: 店舗・スタッフ所属検証 100%、承認済み変更履歴の完全保全、確定給与の通常再計算変更 0件 (帳簿不変性)、主要計算ロジックの単体・結合テスト全件合格。

---

### 0.3 個人プロジェクトの進行および検証基準

本プロジェクトは開発者が全責任を持ち実装・検証を主導する個人開発です。AIコーディング・監査ツールは実際の実行ログと入力範囲が検証された作業においてのみ補助として利用します。

開発は「契約・店舗」「シフト」「勤怠」「給与」「UI統合検証」のドメイン単位で進行します。各段階の完了判定は、ソースコード、単体・結合テスト結果、実行ログ、および実ブラウザでの動作確認に基づいて客観的に判定し、計画表やプロンプト上の記述のみで完了とみなしません。

---

## 第1章. プロジェクト共通アーキテクチャおよび実装標準 (Global Architecture & Conventions)

実装および補助ツールの適用時は、以下の共通ルールを遵守します。標準タイムゾーンは日本標準時 Asia/Tokyo と定義し、JavaのパーサーとDBセッションのタイムゾーンを厳格に一致させます。

### 1.1 パッケージ構造および配置標準
`	ext
com.example.kintai
├── KintaiApplication.java
├── config
│   └── SecurityConfig.java
├── global
│   ├── common
│   │   ├── ApiResponse.java
│   │   └── TimeUtils.java
│   └── exception
│       └── GlobalExceptionHandler.java
└── domain
    ├── home
    ├── member
    ├── store
    ├── shift
    ├── attendance
    ├── payroll
    ├── notification   # 拡張候補
    └── analytics      # 拡張候補
`

- Java パッケージ名、MyBatis Mapper XML、Thymeleaf テンプレートの配置は同一のドメイン名基準で統一します。
- MyBatis XML: src/main/resources/mapper/{domain}/{domain}Mapper.xml
- Thymeleaf: src/main/resources/templates/{domain}/{view}.html
- DB 初期化: src/main/resources/db/schema.sql, src/main/resources/db/data.sql を正本とします。
- 開発用モックデータおよび permitAll 設定は開発プロファイル (local) のみに限定し、本番プロファイルでは完全な認証・認可を強制します。

---

### 1.2 画面 (Thymeleaf View) ルーティング基準表

| メニュー名 | HTTP Method & URL | View パス | 認可ロール | 画面の主要役割 |
| :--- | :--- | :--- | :--- | :--- |
| ホーム / 開発サンプル | GET / | member/sample | 開発環境のみ | テストアカウント・所属店舗切替 (本番では非公開) |
| ログイン / 新規登録 | GET /login, GET /join | member/login, member/join | 匿名アクセス可 | 認証およびアカウント開設 |
| 店舗管理 | GET /stores | store/manage | ROLE_OWNER | 店舗情報、スタッフ一覧、契約条件設定 |
| シフト計画 | GET /shifts | shift/calendar | ROLE_OWNER, ROLE_STAFF | シフト希望提出およびカレンダー確定調整 |
| 勤怠打刻 | GET /attendance | ttendance/punch | ROLE_STAFF (オーナーは代理確認可) | 出勤・休憩・退勤打刻および月間履歴照会 |
| 勤怠承認管理 | GET /attendance/mod-requests | ttendance/mod-list | ROLE_OWNER | 打刻修正申請の承認・却下ワークフロー |
| 給与精算 | GET /payroll | payroll/list | ROLE_OWNER | 月次給与集計・帳簿確定・明細書・Excel出力 |
| 経営分析 (拡張) | GET /analytics | nalytics/dashboard | ROLE_OWNER (拡張時) | 参考用人件費・年収シミュレーション |

---

### 1.2.1 Spring Security 権限管理およびレイアウト継承規則

- **アクセス制御の原則**: sec:authorize によるUI制御は表示切り替えに過ぎず、セキュリティ境界ではありません。すべての Controller / API レイヤーでログインユーザーの memberId と storeId の所属関係をサーバー側で厳格に検証します。
- **OWNER メニュー**: hasRole('OWNER') — 店舗・契約管理、勤怠修正承認、給与精算・確定、Excel出力
- **STAFF メニュー**: hasRole('STAFF') — シフト希望提出、自身の勤怠打刻および修正申請
- **コンテキスト取得**: ログインユーザーの memberId, ole, 選択中の storeId は Spring Security の Authentication コンテキストから安全に取得し、クライアントから送信されたリクエストボディの値を盲信しません。

---

### 1.3 多店舗コンテキストおよびセキュリティ認可規則 (IDOR防御)

本システムはマルチテナント構造を採用しているため、他店舗への不正アクセス (Insecure Direct Object References) を徹底的に防御します。

1. **店舗境界の検証**: すべてのデータ操作において、リクエストされた storeId が現在ログイン中のユーザーの所有・所属店舗であるかをサービス層で検証します。
2. **スタッフ所属の検証**: スタッフに対する契約更新、シフト登録、打刻操作は、対象スタッフがその店舗に有効な雇用関係 (store_staff) を持っていることを前提とします。
3. **他店舗データの混入防止**: 給与計算・シフト照会クエリには必ず store_id = #{storeId} 条件を含め、同一法人の他店舗データであっても厳格に分離します。

---

### 1.4 共通 JSON レスポンス仕様 (ApiResponse<T>)

REST API エンドポイントは、一貫したレスポンス構造を提供します。

- **成功時 (200 OK)**:
  `json
  { "success": true, "message": "処理が完了しました。", "data": { ... } }
  `
- **バリデーションエラー時 (400 Bad Request)**:
  `json
  { "success": false, "message": "入力内容を確認してください。", "data": { "fieldErrors": [ { "field": "hourlyWage", "reason": "時給は東京都最低賃金以上である必要があります。" } ] } }
  `
- **認可エラー (403 Forbidden)**:
  `json
  { "success": false, "message": "指定された店舗に対する操作権限がありません。", "data": null }
  `
- **HTTP ステータスコードの明確な区分**: 認証失敗 401, 認可権限不足 403, リソース不在 404, 状態衝突・二重打刻 409, 業務例外 400, システム例外 500。
- エラーレスポンスには SQL 文、スタックトレース、パスワードハッシュ等の内部機密情報を一切含めません。

---

### 1.5 ⭐ [日本現地就労対応] UI 日本語標準化原則および用語集 (用語集)

本システムは**日本のIT企業へのポートフォリオ提示および現地実務運用**を前提とします。  
したがって、すべての画面 (Thymeleaf HTML) のメニュー名、ボタン、テーブルヘッダー、カレンダー、モーダル、メッセージ、および Excel 出力様式は、自然で格式あるビジネス日本語で統一します。

| 分類 | 韓国語概念 | 日本語標準 UI 表記 | 読み (平仮名/片仮名) | 主な利用画面およびコンポーネント |
| :--- | :--- | :--- | :--- | :--- |
| **勤怠打刻** | 출근 | **出勤** | しゅっきん | タイムカード画面 メイン緑ボタン |
| **勤怠打刻** | 퇴근 | **退勤** | たいきん | タイムカード画面 メイン赤ボタン |
| **勤怠打刻** | 휴게 시작 | **休憩開始** | きゅうけいかいし | タイムカード画面 橙ボタン |
| **勤怠打刻** | 휴게 종료 | **休憩終了** | きゅうけいしゅうりょう | タイムカード画面 復帰ボタン |
| **勤怠打刻** | 타각 기록 | **打刻** | だこく | システム共通呼称 (勤怠打刻) |
| **勤怠打刻** | 근무중 / 휴게중 | **勤務中 / 休憩中** | きんむちゅう / きゅうけいちゅう | リアルタイム状態バッジ |
| **勤怠監査** | 근태 수정 신청 | **勤怠修正申請** | しゅうせいしんせい | スタッフ 打刻修正モーダル |
| **勤怠監査** | 승인 / 반려 | **承認 / 却下** | しょうにん / きゃっか | オーナー 監査承認ボタン |
| **シフト** | 희망 시프트 | **希望シフト** | きぼうしふと | スタッフ提出シフト (橙バッジ) |
| **シフト** | 확정 시프트 | **確定シフト** | かくていしふと | オーナー確定シフト (青バッジ) |
| **シフト** | 미배정 | **未割当** | みわりあて | 必要人数未達の時間帯 |
| **シフト** | 결원 경고 | **人員不足警告** | じんいんぶそくけいこく | ピーク時間帯アラート |
| **給与精算** | 기본급 | **基本給** | きほんきゅう | 実労働時間に適用される基本給与 |
| **給与精算** | 야간 수당 (25%) | **深夜手当** | しんやてあて | 22:00 ~ 翌05:00 割増分 (+25%) |
| **給与精算** | 연장 수당 (25%) | **残業手当** | ざんぎょうてあて | 1日8時間超過 割増分 (+25%) |
| **給与精算** | 통근수당 | **通勤手当** | つうきんてあて | 契約に基づく通勤費実費支給分 |
| **給与精算** | 총 지급액 | **総支給額** | そうしきゅうがく | 額面総支給合計金額 |
| **給与精算** | 급여 대장 (엑셀) | **給与台帳** | きゅうよだいちょう | Apache POI 出力 Excel ファイル名 |
| **給与精算** | 마감일 | **締め日** | しめび | 店舗ごとの給与計算締め日 (末日締め等) |
| **マスタ** | 점주 / 관리자 | **管理者 (オーナー)** | おーなー | 店舗管理者 (ROLE_OWNER) |
| **マスタ** | 알바생 / 직원 | **スタッフ (アルバイト)** | すたっふ (あるばいと) | 従業員 (ROLE_STAFF) |
| **マスタ** | 점포 / 매장 | **店舗** | てんぽ | 店舗管理メニュー (店舗管理) |
| **マスタ** | 초대 코드 | **招待コード** | しょうたいこーど | 店舗合流用8桁英数字コード |

---## 第2章. データベース物理設計および全DDL定義 (Database Schema)

実装およびMyBatisマッピングにおいて、テーブル名およびカラム名の不整合を防止するため、以下の確定DDLを正本とします。  
（pplication.yaml に map-underscore-to-camel-case: true が設定されているため、DBの store_staff_id は Java DTO の storeStaffId と自動マッピングされます。）

### 2.1 コアテーブルDDL (src/main/resources/db/schema.sql)

以下のDDLはMVPの基準スキーマです。基本テーブルに加え、契約履歴 (store_staff_contract_history)、複数回休憩 (ttendance_break)、修正申請休憩スナップショット (ttendance_mod_break)、および給与日別確定詳細 (payroll_daily_detail) を定義しています。Mapper および DTO は本スキーマに完全準拠させます。

`sql
-- 1. 会員 (オーナーおよびスタッフのアカウントマスタ)
CREATE TABLE IF NOT EXISTS member (
    member_id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '会員ID (PK)',
    email VARCHAR(100) NOT NULL UNIQUE COMMENT 'ログインメールアドレス',
    password VARCHAR(255) NOT NULL COMMENT 'BCrypt暗号化パスワード',
    name VARCHAR(50) NOT NULL COMMENT '氏名',
    phone VARCHAR(20) NOT NULL COMMENT '連絡先電話番号',
    role VARCHAR(20) NOT NULL COMMENT '権限ロール (ROLE_OWNER, ROLE_STAFF)',
    is_active BOOLEAN NOT NULL DEFAULT TRUE COMMENT 'アカウント有効フラグ',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 2. 店舗 (店舗情報、給与締め日、打刻QRコードトークン)
CREATE TABLE IF NOT EXISTS store (
    store_id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '店舗ID (PK)',
    owner_id BIGINT NOT NULL COMMENT 'オーナー会員ID (FK)',
    store_name VARCHAR(100) NOT NULL COMMENT '店舗名',
    prefecture_code CHAR(2) NOT NULL DEFAULT '13' COMMENT '都道府県コード (デフォルト13=東京都)',
    invite_code VARCHAR(20) NOT NULL UNIQUE COMMENT 'スタッフ参加用招待コード',
    qr_token VARCHAR(100) NOT NULL UNIQUE COMMENT '店舗専用打刻QRトークン',
    open_time TIME NOT NULL DEFAULT '09:00:00' COMMENT '開店時間',
    close_time TIME NOT NULL DEFAULT '23:00:00' COMMENT '閉店時間',
    pay_cutoff_day INT NOT NULL DEFAULT 99 COMMENT '給与締め日 (1~28日 または 99=末日締め)',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT fk_store_owner FOREIGN KEY (owner_id) REFERENCES member (member_id),
    CONSTRAINT chk_store_cutoff CHECK (pay_cutoff_day BETWEEN 1 AND 28 OR pay_cutoff_day = 99)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 3. 店舗所属スタッフ (多店舗マッピングおよび契約マスタ)
CREATE TABLE IF NOT EXISTS store_staff (
    store_staff_id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '店舗スタッフ所属ID (PK)',
    store_id BIGINT NOT NULL COMMENT '店舗ID (FK)',
    member_id BIGINT NOT NULL COMMENT 'スタッフ会員ID (FK)',
    hourly_wage INT NOT NULL COMMENT '現在表示用時給 (円; 給与計算正本は契約履歴)',
    daily_transport_fee INT NOT NULL DEFAULT 0 COMMENT '1日往復通勤手当実費 (例: 500)',
    monthly_transport_limit INT NOT NULL DEFAULT 15000 COMMENT '月額通勤手当支給上限額 (例: 15000)',
    employment_status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE' COMMENT '在籍状態 (PENDING, ACTIVE, RESIGNED)',
    joined_date DATE NOT NULL COMMENT '入社年月日',
    resigned_date DATE NULL COMMENT '退職年月日',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT uq_store_member UNIQUE (store_id, member_id),
    CONSTRAINT uq_store_staff_id_store UNIQUE (store_staff_id, store_id),
    CONSTRAINT fk_staff_store FOREIGN KEY (store_id) REFERENCES store (store_id),
    CONSTRAINT fk_staff_member FOREIGN KEY (member_id) REFERENCES member (member_id),
    CONSTRAINT chk_staff_wage CHECK (hourly_wage > 0),
    CONSTRAINT chk_staff_transport CHECK (daily_transport_fee >= 0 AND monthly_transport_limit >= 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 3-A. スタッフ契約条件履歴 (適用期間基準; 給与計算時の適用時給・通勤手当参照元)
CREATE TABLE IF NOT EXISTS store_staff_contract_history (
    contract_history_id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '契約履歴ID (PK)',
    store_staff_id BIGINT NOT NULL COMMENT '店舗スタッフ所属ID (FK)',
    effective_from DATE NOT NULL COMMENT '適用開始日 (含む)',
    effective_to DATE NULL COMMENT '適用終了日 (含まない; NULLの場合は現在有効)',
    hourly_wage INT NOT NULL COMMENT '適用基本時給 (円)',
    daily_transport_fee INT NOT NULL DEFAULT 0 COMMENT '適用1日通勤手当支給額',
    monthly_transport_limit INT NOT NULL DEFAULT 15000 COMMENT '適用月額通勤手当上限額',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_contract_effective_from UNIQUE (store_staff_id, effective_from),
    CONSTRAINT fk_contract_staff FOREIGN KEY (store_staff_id) REFERENCES store_staff (store_staff_id),
    CONSTRAINT chk_contract_dates CHECK (effective_to IS NULL OR effective_to > effective_from),
    CONSTRAINT chk_contract_wage CHECK (hourly_wage > 0),
    CONSTRAINT chk_contract_transport CHECK (daily_transport_fee >= 0 AND monthly_transport_limit >= 0),
    INDEX idx_contract_staff_period (store_staff_id, effective_from, effective_to)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 4. シフトスケジュール (希望シフト提出およびオーナー確定統合テーブル)
CREATE TABLE IF NOT EXISTS shift_schedule (
    shift_id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT 'シフトID (PK)',
    store_id BIGINT NOT NULL COMMENT '店舗ID (FK)',
    store_staff_id BIGINT NOT NULL COMMENT '店舗スタッフ所属ID (FK)',
    work_date DATE NOT NULL COMMENT '勤務対象日',
    requested_start_at DATETIME NULL COMMENT '希望出勤日時',
    requested_end_at DATETIME NULL COMMENT '希望退勤日時',
    confirmed_start_at DATETIME NULL COMMENT '確定出勤日時',
    confirmed_end_at DATETIME NULL COMMENT '確定退勤日時',
    planned_break_minutes INT NOT NULL DEFAULT 0 COMMENT '計画休憩時間 (分)',
    status VARCHAR(20) NOT NULL DEFAULT 'SUBMITTED' COMMENT 'シフト状態 (SUBMITTED, CONFIRMED, REJECTED)',
    staff_memo VARCHAR(255) NULL COMMENT 'スタッフメモ',
    owner_memo VARCHAR(255) NULL COMMENT 'オーナーメモ',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_shift_store_date (store_id, work_date),
    INDEX idx_shift_staff_date (store_staff_id, work_date),
    CONSTRAINT fk_shift_store FOREIGN KEY (store_id) REFERENCES store (store_id),
    CONSTRAINT fk_shift_staff_store FOREIGN KEY (store_staff_id, store_id) REFERENCES store_staff (store_staff_id, store_id),
    CONSTRAINT chk_shift_break CHECK (planned_break_minutes >= 0),
    CONSTRAINT chk_shift_status CHECK (status IN ('SUBMITTED', 'CONFIRMED', 'REJECTED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 5. 出退勤・勤怠実績記録 (タイムカードおよび日別実労働・深夜・残業分単位集計)
CREATE TABLE IF NOT EXISTS attendance (
    attendance_id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '勤怠ID (PK)',
    store_id BIGINT NOT NULL COMMENT '店舗ID (FK)',
    store_staff_id BIGINT NOT NULL COMMENT '店舗スタッフ所属ID (FK)',
    shift_id BIGINT NULL COMMENT '関連確定シフトID (FK; 計画外出勤時はNULL)',
    work_date DATE NOT NULL COMMENT '出勤基準日',
    clock_in_at DATETIME NOT NULL COMMENT '出勤打刻日時',
    clock_out_at DATETIME NULL COMMENT '退勤打刻日時',
    break_start_at DATETIME NULL COMMENT '進行中休憩開始時刻 (ON_BREAK時のみ保持)',
    break_end_at DATETIME NULL COMMENT '最終完了休憩終了時刻 (表示用)',
    total_break_minutes INT NOT NULL DEFAULT 0 COMMENT '完了済み休憩合計時間 (分)',
    net_work_minutes INT NOT NULL DEFAULT 0 COMMENT '実労働時間 (分; 休憩控除後)',
    night_work_minutes INT NOT NULL DEFAULT 0 COMMENT '深夜労働時間 (分; 22:00~翌05:00)',
    overtime_minutes INT NOT NULL DEFAULT 0 COMMENT '時間外法定労働時間 (分; 1日8時間=480分超過分)',
    clock_in_method VARCHAR(20) NOT NULL DEFAULT 'BUTTON' COMMENT '打刻方式 (BUTTON, QR, MANUAL_APPROVED)',
    attendance_status VARCHAR(20) NOT NULL DEFAULT 'WORKING' COMMENT '勤怠状態 (WORKING, ON_BREAK, COMPLETED, MOD_REQUESTED)',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_att_store_date (store_id, work_date),
    INDEX idx_att_staff_date (store_staff_id, work_date),
    CONSTRAINT fk_att_store FOREIGN KEY (store_id) REFERENCES store (store_id),
    CONSTRAINT fk_att_staff_store FOREIGN KEY (store_staff_id, store_id) REFERENCES store_staff (store_staff_id, store_id),
    CONSTRAINT fk_att_shift FOREIGN KEY (shift_id) REFERENCES shift_schedule (shift_id),
    CONSTRAINT chk_attendance_minutes CHECK (total_break_minutes >= 0 AND net_work_minutes >= 0 AND night_work_minutes >= 0 AND overtime_minutes >= 0),
    CONSTRAINT chk_attendance_status CHECK (attendance_status IN ('WORKING', 'ON_BREAK', 'COMPLETED', 'MOD_REQUESTED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 5-A. 勤怠別複数休憩区間 (1日複数回の休憩実績を完全正規化保存)
CREATE TABLE IF NOT EXISTS attendance_break (
    attendance_break_id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '休憩区間ID (PK)',
    attendance_id BIGINT NOT NULL COMMENT '勤怠ID (FK)',
    break_start_at DATETIME NOT NULL COMMENT '休憩開始日時',
    break_end_at DATETIME NOT NULL COMMENT '休憩終了日時',
    duration_minutes INT NOT NULL COMMENT '当該休憩時間 (分)',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_break_attendance (attendance_id, break_start_at),
    CONSTRAINT fk_break_attendance FOREIGN KEY (attendance_id) REFERENCES attendance (attendance_id),
    CONSTRAINT chk_break_range CHECK (break_end_at > break_start_at),
    CONSTRAINT chk_break_duration CHECK (duration_minutes > 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 6. 勤怠修正申請 (打刻漏れ・誤打刻修正およびオーナー承認監査証跡)
CREATE TABLE IF NOT EXISTS attendance_mod_request (
    mod_request_id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '修正申請ID (PK)',
    attendance_id BIGINT NULL COMMENT '対象勤怠ID (FK; 打刻完全欠落による事後生成時はNULL許容)',
    store_id BIGINT NOT NULL COMMENT '店舗ID (FK; 新規生成時必須)',
    store_staff_id BIGINT NOT NULL COMMENT '申請スタッフID (FK)',
    work_date DATE NOT NULL COMMENT '勤務基準日 (FK)',
    before_clock_in_at DATETIME NULL COMMENT '修正前出勤日時 (新規生成時はNULL許容)',
    before_clock_out_at DATETIME NULL COMMENT '修正前退勤日時',
    before_break_minutes INT NOT NULL DEFAULT 0 COMMENT '修正前休憩時間 (分)',
    requested_clock_in_at DATETIME NOT NULL COMMENT '修正希望出勤日時',
    requested_clock_out_at DATETIME NOT NULL COMMENT '修正希望退勤日時',
    requested_break_minutes INT NOT NULL DEFAULT 0 COMMENT '修正希望休憩時間 (分)',
    reason VARCHAR(500) NOT NULL COMMENT '修正申請理由',
    approval_status VARCHAR(20) NOT NULL DEFAULT 'PENDING' COMMENT '承認状態 (PENDING, APPROVED, REJECTED)',
    reviewer_id BIGINT NULL COMMENT '承認/却下実施オーナー会員ID (FK)',
    reject_reason VARCHAR(500) NULL COMMENT '却下理由',
    reviewed_at DATETIME NULL COMMENT '承認/却下処理日時',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_mod_attendance FOREIGN KEY (attendance_id) REFERENCES attendance (attendance_id),
    CONSTRAINT fk_mod_store FOREIGN KEY (store_id) REFERENCES store (store_id),
    CONSTRAINT fk_mod_staff_store FOREIGN KEY (store_staff_id, store_id) REFERENCES store_staff (store_staff_id, store_id),
    CONSTRAINT fk_mod_reviewer FOREIGN KEY (reviewer_id) REFERENCES member (member_id),
    CONSTRAINT chk_mod_status CHECK (approval_status IN ('PENDING', 'APPROVED', 'REJECTED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 6-A. 修正申請休憩区間スナップショット (申請前・申請値の監査証跡保全)
CREATE TABLE IF NOT EXISTS attendance_mod_break (
    mod_break_id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '修正申請休憩区間ID (PK)',
    mod_request_id BIGINT NOT NULL COMMENT '修正申請ID (FK)',
    break_set_type VARCHAR(10) NOT NULL COMMENT 'BEFORE または REQUESTED',
    break_start_at DATETIME NOT NULL,
    break_end_at DATETIME NOT NULL,
    duration_minutes INT NOT NULL,
    CONSTRAINT fk_mod_break_request FOREIGN KEY (mod_request_id) REFERENCES attendance_mod_request (mod_request_id),
    CONSTRAINT chk_mod_break_type CHECK (break_set_type IN ('BEFORE', 'REQUESTED')),
    CONSTRAINT chk_mod_break_range CHECK (break_end_at > break_start_at),
    CONSTRAINT chk_mod_break_duration CHECK (duration_minutes > 0),
    INDEX idx_mod_break_request (mod_request_id, break_set_type)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 7. 月次給与精算 (法令遵守計算スナップショットおよびExcel台帳出力正本)
CREATE TABLE IF NOT EXISTS payroll (
    payroll_id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '給与精算ID (PK)',
    store_id BIGINT NOT NULL COMMENT '店舗ID (FK)',
    store_staff_id BIGINT NOT NULL COMMENT '店舗スタッフ所属ID (FK)',
    target_year_month CHAR(7) NOT NULL COMMENT '精算対象年月 (YYYY-MM)',
    range_start_date DATE NOT NULL COMMENT '集計期間開始日 (含む)',
    range_end_date DATE NOT NULL COMMENT '集計期間終了日 (含む)',
    work_days_count INT NOT NULL DEFAULT 0 COMMENT '実出勤日数',
    total_work_minutes INT NOT NULL DEFAULT 0 COMMENT '月間総実労働時間 (分)',
    total_night_minutes INT NOT NULL DEFAULT 0 COMMENT '月間総深夜労働時間 (分)',
    total_overtime_minutes INT NOT NULL DEFAULT 0 COMMENT '月間総法定時間外労働時間 (分)',
    applied_hourly_wage INT NULL COMMENT '精算時時給スナップショット (単一契約時のみ記録; 複数契約変動時はNULL)',
    applied_transport_limit INT NOT NULL DEFAULT 0 COMMENT '精算時通勤手当上限額スナップショット',
    base_pay INT NOT NULL DEFAULT 0 COMMENT '基本給 (日別適用時給基準 100%)',
    night_allowance INT NOT NULL DEFAULT 0 COMMENT '深夜労働割増手当 (25% 加算分)',
    overtime_allowance INT NOT NULL DEFAULT 0 COMMENT '時間外法定割増手当 (25% 加算分)',
    transport_allowance INT NOT NULL DEFAULT 0 COMMENT '通勤手当支給額 (月額上限適用後)',
    gross_pay INT NOT NULL DEFAULT 0 COMMENT '総支給額 (額面合計)',
    payroll_status VARCHAR(20) NOT NULL DEFAULT 'DRAFT' COMMENT '精算状態 (DRAFT, CONFIRMED, PAID)',
    confirmed_at DATETIME NULL COMMENT '精算確定日時',
    paid_at DATETIME NULL COMMENT '支払処理日時 (オプション)',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT uq_payroll_staff_month UNIQUE (store_staff_id, target_year_month),
    CONSTRAINT fk_payroll_store FOREIGN KEY (store_id) REFERENCES store (store_id),
    CONSTRAINT fk_payroll_staff_store FOREIGN KEY (store_staff_id, store_id) REFERENCES store_staff (store_staff_id, store_id),
    CONSTRAINT chk_payroll_range CHECK (range_end_date >= range_start_date),
    CONSTRAINT chk_payroll_status CHECK (payroll_status IN ('DRAFT', 'CONFIRMED', 'PAID'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 7-A. 給与確定時日別詳細スナップショット (確定後勤怠修正に伴う帳簿不整合を完全防止)
CREATE TABLE IF NOT EXISTS payroll_daily_detail (
    payroll_daily_detail_id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '給与日別詳細ID (PK)',
    payroll_id BIGINT NOT NULL COMMENT '給与精算ID (FK)',
    attendance_id BIGINT NOT NULL COMMENT '元勤怠ID (FK)',
    work_date DATE NOT NULL COMMENT '勤務年月日',
    applied_hourly_wage INT NOT NULL COMMENT '当該勤務日適用時給スナップショット',
    total_break_minutes INT NOT NULL DEFAULT 0,
    net_work_minutes INT NOT NULL DEFAULT 0,
    night_work_minutes INT NOT NULL DEFAULT 0,
    overtime_minutes INT NOT NULL DEFAULT 0,
    base_pay INT NOT NULL DEFAULT 0,
    night_allowance INT NOT NULL DEFAULT 0,
    overtime_allowance INT NOT NULL DEFAULT 0,
    transport_amount INT NOT NULL DEFAULT 0 COMMENT '日割り按分通勤手当',
    CONSTRAINT fk_payroll_detail_payroll FOREIGN KEY (payroll_id) REFERENCES payroll (payroll_id),
    CONSTRAINT fk_payroll_detail_attendance FOREIGN KEY (attendance_id) REFERENCES attendance (attendance_id),
    CONSTRAINT uq_payroll_detail_attendance UNIQUE (payroll_id, attendance_id),
    INDEX idx_payroll_detail_date (payroll_id, work_date)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
`

### 2.2 選択拡張DDL (フェーズ2拡張機能用)
`sql
-- LINE通知機能導入時
ALTER TABLE member ADD COLUMN line_user_id VARCHAR(100) NULL UNIQUE COMMENT 'LINE Messaging API User ID';

-- ピーク時間帯最小人員警告機能導入時
ALTER TABLE store ADD COLUMN min_peak_staff INT NOT NULL DEFAULT 3 COMMENT 'ピーク時間帯 (18~22時) 最小推奨人員';
`

---## 第3章. [ドメイン 1] 会員認証および店舗・契約マスタ (member, store, store_staff)

> **実装領域**: 会員認証、店舗管理および雇用契約マスタ  
> **パッケージ**: com.example.kintai.domain.member, com.example.kintai.domain.store

### 3.1 ドメイン概論および設計意図
* **会員 (member) と店舗所属 (store_staff) の分離理由**: member はログインアカウントであり、store_staff は特定店舗との所属関係を表します。時給・通勤手当の契約条件は store_staff_contract_history において適用期間ごとに履歴保全し、store_staff の表示用フィールドとトランザクション内で同期します。
* **招待コード (invite_code) および QRトークン (qr_token)**: 店舗開設時に推測困難な英数字コードを発行し、DB UNIQUE 制約で一意性を担保します。本トークンは所属招待およびデモ認証の補助手段として機能します。

### 3.2 画面 (View) ルーティングおよび UI 仕様
1. **GET /login $\rightarrow$ 	emplates/member/login.html & GET /join $\rightarrow$ 	emplates/member/join.html**
   * メールアドレス/パスワードによる認証および会員登録。一般リクエストから送信された ole 値を盲信せず、サーバー側の登録ポリシーに基づき権限を確定します。
2. **GET /stores $\rightarrow$ 	emplates/store/manage.html**
   * **Model バインディング**:
     * store: 現在選択中の店舗詳細情報 (StoreDto)
     * staffList: 当該店舗の所属スタッフ契約一覧 (List<StoreStaffDetailDto> - member.name, member.email, member.phone 結合)
   * **主要 UI コンポーネント**:
     * 上段情報カード: 店舗名、営業時間 (openTime ~ closeTime)、給与締め日、**招待コードコピー機能**、**打刻QR表示モーダル**、**[新規店舗開設] ボタン**。
     * 下段テーブル: 所属スタッフ一覧 (氏名、メールアドレス、電話番号、**適用基本時給**、**1日通勤手当**、**月額通勤手当上限**、在籍状態、入社日、**[契約条件変更] ボタン**)。
     * 右上補助機能: **[招待コードによる店舗合流申請] モーダル**。

### 3.3 REST API 詳細仕様

#### ① 会員登録 (POST /api/members/join) およびログイン (POST /api/members/login)
* **会員登録 Request JSON (MemberJoinRequestDto)**:
  `json
  {
    "email": "staff3@kintai.com",
    "password": "Password123!",
    "name": "高橋 健太",
    "phone": "080-9999-8888"
  }
  `
  * PasswordEncoder.encode(password) によりハッシュ化して保存し、平文パスワードはログやレスポンスに一切露出させません。
* **ログイン Request JSON (MemberLoginRequestDto)**:
  `json
  {
    "email": "owner@kintai.com",
    "password": "Password123!"
  }
  `
  * Spring Security の標準認証フローを適用し、認証成功後はセキュリティコンテキストおよび保護されたセッションで管理します。

#### ② 新規店舗開設 (POST /api/stores)
* **Request JSON (StoreCreateRequestDto)**:
  `json
  {
    "storeName": "東京 渋谷2号店",
    "prefectureCode": "13",
    "openTime": "10:00",
    "closeTime": "23:30",
    "payCutoffDay": 25
  }
  `
* **サーバー側処理ロジック**:
  1. inviteCode: 接頭辞 ST と安全な乱数により8桁の英数字コードを生成し、UNIQUE 衝突時は再試行。
  2. qrToken: "TOKEN_QR_" + UUID.randomUUID().toString() により一意トークンを発行。
  3. オーナーID (ownerId) はリクエストボディではなく、認証コンテキスト (Authentication) から安全にバインド。

#### ③ 招待コードによるスタッフ合流 (POST /api/stores/join-staff)
* **Request JSON (StaffJoinRequestDto)**:
  `json
  {
    "inviteCode": "ST7K4P2Q"
  }
  `
* **サーバー側処理ロジック**:
  1. StoreMapper.findByInviteCode(inviteCode) により店舗存在確認（存在しない場合は 400 Bad Request）。
  2. StoreMapper.existsStoreStaff(storeId, memberId) により多重登録を防止。
  3. 初回登録時は employment_status = 'PENDING' で store_staff を登録し、オーナーが契約条件を確定承認した時点で ACTIVE に移行させます。

#### ④ スタッフ契約条件更新および承認 (PUT /api/stores/staff/{storeStaffId})
* **Request JSON (StaffContractUpdateDto)**:
  `json
  {
    "effectiveFrom": "2026-11-01",
    "hourlyWage": 1350,
    "dailyTransportFee": 600,
    "monthlyTransportLimit": 15000,
    "employmentStatus": "ACTIVE"
  }
  `
* **サーバー側処理ロジック**:
  * **最低賃金バリデーション**: 店舗の都道府県（東京都）基準の地域別最低賃金（2026年10月基準 1,280円/時）以上であることを厳格に検証します。
  * **履歴管理**: 既存契約の上書きを禁止し、前履歴の effective_to を新規 effectiveFrom で確定終了させた後、新契約レコードを store_staff_contract_history に INSERT します。単一トランザクション内で store_staff の表示フィールドと同期します。

#### ⑤ スタッフ退職処理および効力日整合性 (PUT /api/stores/staff/{storeStaffId}/resign) (F03対応)
* **Request JSON (StaffResignRequestDto)**:
  `json
  {
    "resignedDate": "2026-10-31"
  }
  `
* **サーバー側処理ロジック (効力日と在籍状態の競合防止)**:
  * **先行検証**: 対象スタッフは ACTIVE である必要があり、esignedDate >= joined_date を満たす必要があります。また、退職日以降 (work_date > resignedDate) に登録済みの勤怠やシフトが存在する場合は 409 Conflict で処理を遮断します。
  * **即時退職と予約退職の分離**: esignedDate <= LocalDate.now() の場合は即時に RESIGNED に更新。未来日退職の場合は在籍中の打刻を阻害しないよう、日付到来まで打刻権限を維持します。
  * **契約終了処理**: 最新の契約履歴の effective_to を esignedDate + 1日 で安全に確定終了させ、DDL制約 (effective_to > effective_from) の衝突を防止します。

---

## 第4章. [ドメイン 2] 統合シフトスケジューラー (shift_schedule & FullCalendar)

> **実装領域**: シフト計画およびカレンダー調整  
> **パッケージ**: com.example.kintai.domain.shift

### 4.1 ドメイン概論および単一テーブル対照ビュー設計
* 従来の「希望シフトテーブル」と「確定シフトテーブル」の分離構成を排し、shift_schedule 単一レコード内に **スタッフ希望時間 (equested_start_at, equested_end_at)** と **オーナー確定時間 (confirmed_start_at, confirmed_end_at)** を対照配置しました。
  * スタッフ提出時: status = 'SUBMITTED', equested_* に値が設定され、confirmed_* は NULL。
  * オーナー確定時: status = 'CONFIRMED', confirmed_* に確定値が設定され、状態遷移を完了。

### 4.2 画面 (View) ルーティングおよび FullCalendar UI 仕様
* **GET /shifts $\rightarrow$ 	emplates/shift/calendar.html**
  * **上段操作バー**: 店舗選択、スタッフ別フィルタ、状態フィルタ (すべて, 希望提出(橙), 確定済み(青), 未割当(灰))。
  * **ピーク時人員不足アラート**: 18:00 ~ 22:00 のピーク時間帯に確定人数が基準値（3名）未満の日程がある場合、画面上部に警告バッジを表示。
  * **FullCalendar 6.x 日本語ローカライズ**:
    `javascript
    const calendar = new FullCalendar.Calendar(calendarEl, {
        initialView: 'dayGridMonth',
        locale: 'ja', // 完全日本語表記
        buttonText: { today: '今日', month: '月', week: '週' },
        headerToolbar: {
            left: 'prev,next today',
            center: 'title',
            right: 'dayGridMonth,timeGridWeek'
        },
        events: '/api/shifts/events?storeId=' + storeId,
        dateClick: function(info) { openShiftRequestModal(info.dateStr); },
        eventClick: function(info) { openShiftConfirmModal(info.event); }
    });
    `
  * カレンダー空欄クリック (dateClick): **[スタッフ希望シフト提出モーダル]** 表示。
  * 登録済みイベントクリック (eventClick): **[オーナーシフト調整・確定モーダル]** 表示。スタッフの元希望時間とオーナー確定入力欄を上下に対照表示。

### 4.3 REST API およびコア検証ロジック仕様

#### ① 月間カレンダーイベント取得 (GET /api/shifts/events)
* **境界日パラメータ仕様**: FullCalendar の end パラメータは**排他境界 (Exclusive)**です。work_date >= #{startDate} AND work_date < #{endDate} 条件により、月跨ぎの境界不整合を防止します。
* **状態別表示カラー規則**:
  * CONFIRMED: #0d6efd (青), 標題 [確定] 氏名 (HH:mm~HH:mm)
  * SUBMITTED: #fd7e14 (橙), 標題 [希望] 氏名 (HH:mm~HH:mm)
  * REJECTED: #6c757d (灰), 標題 [未割当] 氏名 (F08対応: COALESCE により日付を保全)

#### ② 希望シフト提出 (POST /api/shifts/request)
* **Request JSON (ShiftRequestDto)**:
  `json
  {
    "storeId": 1,
    "storeStaffId": 1,
    "workDate": "2026-10-28",
    "requestedStartTime": "17:00",
    "requestedEndTime": "23:30",
    "staffMemo": "大学の講義終了後に出勤可能です。"
  }
  `
* **日跨ぎ夜勤および同時申請制御 (F04 / F07 防御)**:
  * 終了時刻が開始時刻より早い場合は翌日退勤 (workDate.plusDays(1)) として解決。
  * **F04 防御**: シフト同時申請の衝突を防止するため、lockStoreStaffForUpdate(storeStaffId) による悲観的ロック (SELECT ... FOR UPDATE) を適用。
  * **F07 防御**: 1回あたりの最大勤務時間は16時間以内 (Duration.between.compareTo(Duration.ofHours(16)) <= 0) を厳格に判定（小数点切り捨てバグを防止）。

#### ③ シフト確定および労働基準法第34条法定休憩時間の自動算定 (PUT /api/shifts/{shiftId}/confirm)
* **労働基準法第34条に基づく休憩時間自動算定ロジック**:
  * 実労働時間が **6時間を超える場合は少なくとも45分**、**8時間を超える場合は少なくとも60分** の休憩を勤務途中に付与することが義務付けられています。
  * 計画用レコメンド基準:
    * 拘束時間 grossMinutes <= 360 (6時間以下): 0分
    * 360 < grossMinutes <= 525: 45分
    * grossMinutes > 525: 60分
  * オーナーが計画休憩時間を入力した場合、法定下限を満たしているかを検証し、不足している場合は入力エラーとして差し戻します。

---## 第5章. [ドメイン 3] 監査証跡 出退勤打刻および修正承認ワークフロー (ttendance, ttendance_mod_request)

> **実装領域**: リアルタイム勤怠打刻および改ざん防止監査ワークフロー  
> **パッケージ**: com.example.kintai.domain.attendance

### 5.1 ドメイン概論および状態遷移マシン (State Machine)
スタッフの1日の勤怠状態 (ttendance_status) は、以下の厳格な有限状態遷移モデルに従います:
`	ext
[未出勤] ──(出勤打刻: CLOCK_IN)──▶ [WORKING (勤務中)]
                                       │         ▲
                   (休憩開始: BREAK_START)     (休憩終了: BREAK_END)
                                       ▼         │
                                  [ON_BREAK (休憩中)]
                                       
[WORKING] ──(退勤打刻: CLOCK_OUT)──▶ [COMPLETED (退勤完了 / 3種労働時間自動算出)]
                                       │
                   (スタッフ修正申請)   │
                                       ▼
                              [MOD_REQUESTED (修正待機)] ──(オーナー承認/却下)──▶ [COMPLETED (再計算反映)]
`

### 5.2 画面 (View) ルーティングおよび UI 仕様
1. **GET /attendance $\rightarrow$ 	emplates/attendance/punch.html**
   * **リアルタイム・タイムカード打刻パネル**:
     * 現在時刻デジタル時計 (1秒単位 JavaScript リアルタイム更新)。
     * 選択中スタッフおよび本日の勤怠ステータスバッジ (未出勤 / 勤務中 / 休憩中 / 退勤済)。
     * **4大打刻ボタン (標準日本語表記)**: [🟢 出勤], [☕ 休憩開始], [☕ 休憩終了], [🔴 退勤] (現在の状態遷移ルールに応じて有効/無効を動的制御)。
   * **月別出退勤実績および勤怠修正申請**:
     * 日別一覧: 出勤時刻、退勤時刻、休憩(分)、**実労働(分)**、**深夜(分)**、**残業(分)**、ステータス、**[勤怠修正申請] ボタン**。
     * 修正申請モーダル: 訂正後の出退勤時刻、休憩区間、および **申請理由 (必須入力)** を入力。
2. **GET /attendance/mod-requests $\rightarrow$ 	emplates/attendance/mod-list.html (オーナー監査画面)**
   * **対照監査 UI**: [修正前 (Before) 出退勤時刻] $\rightarrow$ [修正後 (Requested) 出退勤時刻] を赤/青の対照差分で表示し、申請理由を確認のうえ **[承認]** または **[却下]** を実行。

### 5.3 ⭐ [コアアルゴリズム仕様] 日別労働時間3種 (実労働・深夜・法定時間外) 算出エンジン

入力パラメータは出退勤日時と **完了済み全休憩区間リスト (ttendance_break)** です。ttendance.break_start_at は進行中休憩のUI表示にのみ利用し、実際の計算は全区間の実績に基づき厳密に算出します。

`java
package com.example.kintai.domain.attendance.util;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public final class AttendanceTimeCalculator {
    private AttendanceTimeCalculator() {}

    public record BreakInterval(LocalDateTime startAt, LocalDateTime endAt) {}

    public record WorkTimeResult(
            int grossMinutes,
            int totalBreakMinutes,
            int netWorkMinutes,
            int nightWorkMinutes,
            int overtimeMinutes) {}

    public static WorkTimeResult calculate(
            LocalDateTime clockIn,
            LocalDateTime clockOut,
            List<BreakInterval> breakIntervals) {
        if (clockIn == null || clockOut == null || !clockOut.isAfter(clockIn)) {
            throw new IllegalArgumentException("出退勤時刻を正しく入力してください。");
        }
        if (breakIntervals == null) {
            throw new IllegalArgumentException("休憩記録リストがnullです。空リストを指定してください。");
        }

        int grossMinutes = Math.toIntExact(Duration.between(clockIn, clockOut).toMinutes());
        List<BreakInterval> sorted = new ArrayList<>(breakIntervals);
        sorted.sort(Comparator.comparing(BreakInterval::startAt));

        int totalBreakMinutes = 0;
        int nightBreakMinutes = 0;
        LocalDateTime previousEnd = null;
        for (BreakInterval interval : sorted) {
            if (interval.startAt() == null || interval.endAt() == null || !interval.endAt().isAfter(interval.startAt())) {
                throw new IllegalArgumentException("無効な休憩時間帯が含まれています。");
            }
            if (interval.startAt().isBefore(clockIn) || interval.endAt().isAfter(clockOut)) {
                throw new IllegalArgumentException("休憩時間は出退勤の範囲内である必要があります。");
            }
            if (previousEnd != null && interval.startAt().isBefore(previousEnd)) {
                throw new IllegalArgumentException("休憩時間帯が重複しています。");
            }
            int duration = Math.toIntExact(Duration.between(interval.startAt(), interval.endAt()).toMinutes());
            totalBreakMinutes = Math.addExact(totalBreakMinutes, duration);
            nightBreakMinutes = Math.addExact(nightBreakMinutes, overlapNightMinutes(interval.startAt(), interval.endAt()));
            previousEnd = interval.endAt();
        }

        int netWorkMinutes = grossMinutes - totalBreakMinutes;
        if (netWorkMinutes < 0) {
            throw new IllegalArgumentException("休憩時間が総滞在時間を超過しています。");
        }
        int grossNightMinutes = overlapNightMinutes(clockIn, clockOut);
        int nightWorkMinutes = Math.max(0, grossNightMinutes - nightBreakMinutes);
        int overtimeMinutes = Math.max(0, netWorkMinutes - 480); // 1日8時間(480分)超過分

        return new WorkTimeResult(grossMinutes, totalBreakMinutes, netWorkMinutes, nightWorkMinutes, overtimeMinutes);
    }

    // [start, end) と 各日付の 22:00~翌05:00 の積集合を分単位で集計
    private static int overlapNightMinutes(LocalDateTime start, LocalDateTime end) {
        int total = 0;
        LocalDate first = start.toLocalDate().minusDays(1);
        LocalDate last = end.toLocalDate();
        for (LocalDate date = first; !date.isAfter(last); date = date.plusDays(1)) {
            LocalDateTime windowStart = LocalDateTime.of(date, LocalTime.of(22, 0));
            LocalDateTime windowEnd = LocalDateTime.of(date.plusDays(1), LocalTime.of(5, 0));
            LocalDateTime overlapStart = start.isAfter(windowStart) ? start : windowStart;
            LocalDateTime overlapEnd = end.isBefore(windowEnd) ? end : windowEnd;
            if (overlapEnd.isAfter(overlapStart)) {
                total = Math.addExact(total, Math.toIntExact(Duration.between(overlapStart, overlapEnd).toMinutes()));
            }
        }
        return total;
    }
}
`

**必須単体テスト仕様**:
1. 18:00 ~ 23:00 (休憩なし): 総拘束 300分, 深夜労働 60分, 残業 0分
2. 18:00 ~ 翌06:00 (21:00~21:30, 23:00~23:30 休憩): 総拘束 720分, 休憩 60分, 実労働 660分, 深夜実働 390分, 8時間超過法定残業 180分
3. 休憩が出退勤範囲外、または休憩時間帯の重複: 例外送出

### 5.4 REST API 詳細仕様

#### ① リアルタイム打刻 (POST /api/attendance/punch)
* **Request JSON (AttendancePunchDto)**:
  `json
  {
    "storeId": 1,
    "storeStaffId": 1,
    "qrToken": "TOKEN_QR_SHINJUKU_001",
    "punchType": "CLOCK_IN"
  }
  `
* **コアロジック (二重打刻防止、帳簿確定ロック保護、シフト自動連携)**:
  * **二重打刻防止**: punchType == 'CLOCK_IN' 時、未退勤 (WORKING, ON_BREAK) のレコードが存在する場合は二重出勤を遮断 (409 Conflict)。
  * **F01 防御 (確定給与期間の保護)**: 当日を含む給与精算がすでに CONFIRMED または PAID にロックされている場合、新規打刻を遮断し帳簿不変性を担保。
  * **確定シフト連携**: 当日の shift_schedule (CONFIRMED) が存在する場合、ttendance.shift_id に自動紐付け。
  * **休憩・退勤処理**: BREAK_START は WORKING からのみ、BREAK_END は ON_BREAK からのみ許可。CLOCK_OUT 時に全休憩区間を集計して AttendanceTimeCalculator.calculate() を呼び出し、分単位集計値を保存。

#### ② 勤怠修正申請 (POST /api/attendance/mod-requests)
* **Request JSON (AttendanceModRequestDto)**:
  `json
  {
    "attendanceId": 1,
    "workDate": "2026-10-27",
    "requestedClockInAt": "2026-10-27T18:00:00",
    "requestedClockOutAt": "2026-10-28T01:00:00",
    "requestedBreaks": [
      {"startAt": "2026-10-27T21:00:00", "endAt": "2026-10-27T21:30:00"},
      {"startAt": "2026-10-27T23:00:00", "endAt": "2026-10-27T23:15:00"}
    ],
    "reason": "閉店後の清掃作業により退勤打刻が漏れました。"
  }
  `
* **完全打刻漏れ時の事後生成**: ttendanceId == null の場合でも、勤務日・出退勤時刻・休憩区間を PENDING 申請として受け付け、承認時に勤怠レコードを新規生成。

#### ③ オーナー承認・却下レビュー (PUT /api/attendance/mod-requests/{modRequestId}/review)
* **Request JSON**: { "action": "APPROVE", "rejectReason": null }
* **F01 / F04 / F05 防御ロジック**:
  * トランザクション開始時に store_staff 行を悲観的ロック (SELECT ... FOR UPDATE) で直列化し、給与確定処理との競合を完全遮断。
  * 対象勤怠の勤務日を含む給与台帳が確定済み (CONFIRMED) の場合は承認を即時遮断 (409 Conflict)。
  * **F05 防御**: 出勤日 (work_date) 自体を変更する申請はデータ整合性保護のため遮断。
  * 承認時は修正前データを ttendance_mod_break (BEFORE) に退避し、申請値を ttendance に適用して労働時間を再計算。

---## 第6章. [ドメイン 4] 日本国労働基準法準拠 給与精算エンジンおよび Apache POI 給与台帳 (payroll)

> **実装領域**: 法令準拠給与計算、帳簿確定ロックおよび帳票出力  
> **パッケージ**: com.example.kintai.domain.payroll

### 6.1 ドメイン概論および法理的設計意図
1. **なぜ「基本給(100%) + 深夜割増(+25%) + 時間外割増(+25%)」の分離加算を採用するのか？**
   * 実労働時間を昼間(100%)、深夜(125%)、深夜残業(150%)と時間帯別に細分化して累積計算すると、条件分岐が複雑化し丸め誤差の原因となります。
   * これに対し、**① 全実労働時間に対して基本時給 (100%) を算定し、② 深夜労働時間 (22:00 ~ 翌05:00) に対して深夜割増手当 (+25%) を加算し、③ 1日8時間超過労働に対して時間外法定割増手当 (+25%) を加算する** アプローチを採用します。これにより、深夜かつ法定時間外が重畳する区間は自動的に 100% + 25% + 25% = 150% となり、法令上の割増義務を漏れなく充足できます。
   * **MVP スコープ**: 労働基準法第37条に基づく日別8時間超過の時間外労働割増 (25%) および深夜労働割増 (25%) を対象とします。週40時間超過、法定休日労働、月60時間超過割増等は将来の拡張モジュールとして整理します。
   * 契約条件は store_staff_contract_history の適用期間に基づいて勤務日ごとに判定し、月中に時給や交通費の改定があった場合でも勤務日時点の契約を日別に正確に適用します。

2. **店舗別給与締め日 (pay_cutoff_day) の柔軟な集計**:
   * 日本の店舗実務における多様な締め日（末日締め 99、15日締め 15、20日締め 20、25日締め 25 等）を完全サポートします。

### 6.2 ⭐ [コアアルゴリズム仕様] 給与締め日期間判定および日別時給適用計算エンジン

給与計算は期間全体に単一の時給を乗じるのではなく、COMPLETED 状態の勤怠ごとに勤務日基準で有効な契約履歴を取得し、日別に計算したうえで月次集計を行います。

`java
package com.example.kintai.domain.payroll.util;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.YearMonth;

public final class PayrollCalculator {
    private PayrollCalculator() {}

    public record DateRange(LocalDate startDate, LocalDate endDate) {}

    public record DailyPayResult(
            int basePay,
            int nightAllowance,
            int overtimeAllowance,
            int grossWage) {}

    /** 締め日 (1~28日 または 99=末日) に基づく集計対象期間 (両端日を含む) を算出 */
    public static DateRange getCutoffDateRange(String targetYearMonth, int payCutoffDay) {
        YearMonth ym = YearMonth.parse(targetYearMonth);
        if (payCutoffDay == 99) {
            return new DateRange(ym.atDay(1), ym.atEndOfMonth());
        }
        if (payCutoffDay < 1 || payCutoffDay > 28) {
            throw new IllegalArgumentException("締め日は1～28または99（末日）で指定してください。");
        }
        LocalDate endDate = ym.atDay(payCutoffDay);
        LocalDate startDate = ym.minusMonths(1).atDay(payCutoffDay).plusDays(1);
        return new DateRange(startDate, endDate);
    }

    /**
     * 当該勤務日に適用される契約時給に基づき、日別の基本給・深夜割増・時間外割増を算出する。
     */
    public static DailyPayResult calculateDailyPay(
            int appliedHourlyWage,
            int netWorkMinutes,
            int nightWorkMinutes,
            int dailyOvertimeMinutes) {

        if (appliedHourlyWage <= 0) {
            throw new IllegalArgumentException("適用時給は0より大きい値を指定してください。");
        }
        if (netWorkMinutes < 0 || nightWorkMinutes < 0 || dailyOvertimeMinutes < 0
                || nightWorkMinutes > netWorkMinutes || dailyOvertimeMinutes > netWorkMinutes) {
            throw new IllegalArgumentException("勤務時間の値が不正です。");
        }

        // 基本給 = 実労働時間(分) * 時給 / 60
        int basePay = yenRound(BigDecimal.valueOf(netWorkMinutes)
                .multiply(BigDecimal.valueOf(appliedHourlyWage)), 60);
        // 深夜手当 (+25%) = 深夜時間(分) * 時給 * 0.25 / 60
        int nightAllowance = yenRound(BigDecimal.valueOf(nightWorkMinutes)
                .multiply(BigDecimal.valueOf(appliedHourlyWage)).multiply(BigDecimal.valueOf(25)), 6000);
        // 残業手当 (+25%) = 時間外時間(分) * 時給 * 0.25 / 60
        int overtimeAllowance = yenRound(BigDecimal.valueOf(dailyOvertimeMinutes)
                .multiply(BigDecimal.valueOf(appliedHourlyWage)).multiply(BigDecimal.valueOf(25)), 6000);
        int grossWage = Math.addExact(Math.addExact(basePay, nightAllowance), overtimeAllowance);

        return new DailyPayResult(basePay, nightAllowance, overtimeAllowance, grossWage);
    }

    /** 日本の労務慣行標準: 四捨五入 (RoundingMode.HALF_UP) による1円単位端数処理 */
    private static int yenRound(BigDecimal numerator, long denominator) {
        return numerator.divide(BigDecimal.valueOf(denominator), 0, RoundingMode.HALF_UP).intValueExact();
    }
}
`

**端数処理および検証整合性原則 (F06 / F09 / F10 防御)**:
1. **日別集計後の四捨五入 (F06 防御)**: 打刻レコード単位で端数処理を行うと累積誤差が発生するため、同一勤務日 (work_date) の実労働・深夜・残業分数を合算した後に金額換算・端数処理を行い、payroll_daily_detail に保存します。
2. **最低賃金再検証 (F09 防御)**: 契約締結時のみならず給与計算実行時にも、当該勤務日時点の法定最低賃金（東京都 1,280円/時等）を下回っていないかを再検証し、法令違反を未然に防止します。
3. **通勤手当月額上限 (F10 防御)**: 店舗契約に基づく月額支給上限額を厳格に適用します。

### 6.3 画面 (View) および REST API 仕様

1. **GET /payroll $\rightarrow$ 	emplates/payroll/list.html**
   * 上段操作パネル: 対象年月セレクター、**[⚡ 選択期間給与一括計算]** ボタン、**[📊 給与台帳Excel(.xlsx)ダウンロード]** ボタン。
   * 月次集計カード: 総支給額合計、総労働時間合計、割増手当合計、通勤手当合計。
   * 下段テーブル: スタッフ別一覧 (氏名, 適用時給, 出勤日数, 実労働時間, 基本給, 深夜手当, 残業手当, 通勤手当, 総支給額, ステータス, [給与明細詳細], [確定] ボタン)。

2. **給与一括計算および保存 (POST /api/payroll/calculate)**
   * Request JSON: { "storeId": 1, "targetYearMonth": "2026-10" }
   * 期間内に承認待ち (PENDING, MOD_REQUESTED) の勤怠が存在する場合は安全のため計算を中断し、未確定データの処理を要求。
   * 確定済み (CONFIRMED, PAID) の給与台帳は通常再計算での上書きを禁止。

3. **給与台帳 Excel エクスポート (GET /api/payroll/excel)**
   * Apache POI (XSSFWorkbook) により、月次給与集計シート (給与台帳) を生成しバイナリストリーム出力。

4. **給与明細詳細照会 (GET /api/payroll/{payrollId}/detail)**
   * 日別明細 (payroll_daily_detail) を含めた詳細 JSON を返却し、モーダル上に日別の出退勤時刻、休憩、各手当の内訳を対照表示。

5. **給与確定ロックおよび帳簿不変性担保 (POST /api/payroll/{payrollId}/confirm) (F01, F02, F04 防御)**
   * **Request JSON (PayrollConfirmRequestDto)**:
     `json
     {
       "expectedGrossPay": 131325,
       "expectedTotalPay": 131325
     }
     `
   * **前提条件バリデーション (F01 防御)**:
     1. 現在のステータスが DRAFT であること。
     2. **集計対象期間の終了必須**: LocalDate.now() > payroll.range_end_date であること。締日到来前の早期確定を禁止し、将来日の正常出勤が遮断される矛盾を防止。
     3. 当該期間内に未処理の修正申請が存在しないこと。
   * **直列化トランザクションおよび金額再確認 (F02 & F04 防御)**:
     1. トランザクション開始時に store_staff 行を SELECT ... FOR UPDATE で悲観的ロック。
     2. 確定直前に最新データに基づき再計算を実行。
     3. **オーナー確認額不一致防止 (F02)**: 再計算結果がリクエスト本文の expectedGrossPay と1円でも異なる場合、自動上書きを禁止し 409 Conflict と差分情報を返却して再確認を要求。
     4. 金額一致時に payroll_status = 'CONFIRMED', confirmed_at = LocalDateTime.now() を記録してコミット。確定後は勤怠修正および再計算が永久に遮断されます。

---## 第7章. [選択的拡張] LINE メッセンジャー連携および Web 通知センター

> **導入条件**: コアMVPの検証完了後、必要性および優先順位を判定して段階的に導入します。  
> **パッケージ**: com.example.kintai.domain.notification

### 7.1 機能目的および制約事項
LINE Messaging API を通じて、シフト確定通知、打刻修正申請・承認通知、給与確定通知をプッシュ送信する拡張機能です。個人開発アカウントの利用枠・料金規程および認証トークンは公式ドキュメント（https://developers.line.biz/ja/docs/messaging-api/overview/）に準拠します。

### 7.2 実装基準およびモック検証
1. **セキュリティ管理**: チャネルアクセストークンは環境変数から読み込み、Gitリポジトリには一切コミットしません。
2. **アカウント連携**: member.line_user_id は本機能を有効化した場合にのみ利用します。
3. **シミュレーションモード**: line.enabled=false 時は実送信を行わず、UI上に「送信プレビュー」を表示し、コンソールには機密情報を除外したダイジェストログのみを出力します。

---

## 第8章. [選択的拡張] 経営分析 (BI) および年収シミュレーションダッシュボード

> **導入条件**: コアMVP完了後の選択的実装。税務・社保の公的判定ではなく、累積支給額の可視化を目的とします。  
> **パッケージ**: com.example.kintai.domain.analytics

### 8.1 日本の「年収の壁」を扱う基本原則
日本実務におけるいわゆる「年収の壁」は単一の金額基準ではありません。所得税の課税最低限、配偶者控除・扶養控除、特定親族特別控除、社会保険（健康保険・厚生年金）の被扶養者基準等は、各法令・制度ごとに適用条件が異なります。

- **所得税制の動向**: 2026年12月1日施行の税制改正等、最新の国税庁告示（https://www.nta.go.jp/users/gensen/2026kiso/）を継続確認します。
- **通勤手当非課税枠**: 公共交通機関利用時の月額上限（15万円）等の非課税枠と給与課税区分を明確に分離します。
- **免責事項の明記**: 本ダッシュボードは店舗の人件費推移およびアルバイトスタッフの就業調整に向けた参考情報であり、公的な税務申告・社保適格判定を代替するものではない旨を画面上に明記します。

---

## 付録 A. 統合検証チェックリストおよび未実装事項の明確化

| 領域 | 検証項目 | 判定 | 根拠・現状ステータス |
| :--- | :--- | :--- | :--- |
| **環境** | ローカル MySQL 26.7 (8.4互換) | PASS | 厳格な外部キー・CHECK制約およびUTF8MB4環境で動作検証済み |
| **環境** | MySQL 8.4 Testcontainers | BLOCKED | Docker未導入環境のため、次回フェーズへ延期（未完了明記） |
| **環境** | Redis / クラウドデプロイ | 保留 | コアMVP完遂を最優先とし、必要時に段階的導入 |
| **ドメイン** | 契約マスタ・所属分離・最低賃金バリデーション | PASS | 東京都最低賃金 (1,280円) 基準単体テスト全件通過 |
| **ドメイン** | シフト調整・FullCalendar完全日本語化・悲観的ロック | PASS | shift_schedule 単一テーブル対照ビューおよび同時実行制御検証完了 |
| **ドメイン** | 勤怠打刻・複数回休憩集計・修正承認監査ログ | PASS | 有限状態遷移マシン、事前事後スナップショット保全完了 |
| **ドメイン** | 労働基準法第37条 (深夜・時間外25%割増) 計算 | PASS | 分単位集計、1円単位四捨五入 (HALF_UP)、給与台帳Excel出力確認済み |
| **ドメイン** | 確定給与の帳簿ロック・不変性担保 | PASS | 期間外早期確定遮断、再計算による上書き完全防止 |

---

## 付録 B. プロジェクト運用履歴およびWBS移行記録
本プロジェクトは、初期検討時の外部連携想定（LINE、BI等）を整理し、エンタープライズ領域における「店舗契約 $\rightarrow$ シフト計画 $\rightarrow$ リアルタイム打刻 $\rightarrow$ 法令遵守給与計算」の4大コアトランザクションおよび監査証跡の無欠性を実証することにフォーカスして開発されました。

---

## 第9章. ドメイン別 AI コーディング・監査プロンプトガイド (参考)

各ドメインの実装および検証において、ローカル監査AI（Qwen 3.8 27B）およびコーディングAIに提示する標準プロンプト基準です。

### 💬 [ドメイン A] 契約・店舗マスタ実装プロンプト基準
`	ext
要件定義書「第3章. [ドメイン 1] 会員認証および店舗・契約マスタ」に基づき実装を行ってください。
memberとstore_staffを明確に分離し、契約条件（時給・通勤手当）はstore_staff_contract_historyにて適用期間管理を行ってください。
東京都最低賃金バリデーション、招待コード生成、およびF03（退職日と在籍状態の競合防止）を遵守してください。
`

### 💬 [ドメイン B] シフト管理実装プロンプト基準
`	ext
要件定義書「第4章. [ドメイン 2] 統合シフトスケジューラー」に基づき実装を行ってください。
shift_schedule単一テーブルに対照ビューを構成し、FullCalendar 6.xを完全日本語ロケールで初期化してください。
日跨ぎ夜勤対応、F04（悲観的ロック直列化）、F07（16時間超過判定精度）、および労働基準法第34条法定休憩時間の自動算定を実装してください。
`

### 💬 [ドメイン C] 勤怠打刻・修正監査実装プロンプト基準
`	ext
要件定義書「第5章. [ドメイン 3] 監査証跡 出退勤打刻および修正承認ワークフロー」に基づき実装を行ってください。
4大打刻（出勤、退勤、休憩開始、休憩終了）の状態遷移マシンを実装し、複数回休憩はattendance_breakに完全正規化保存してください。
AttendanceTimeCalculatorによる分単位3種労働時間算出、F01（確定給与期間保護）、F04（直列化ロック）、F05（出勤日変更遮断）を遵守してください。
`

### 💬 [ドメイン D] 給与精算・Excel台帳出力プロンプト基準
`	ext
要件定義書「第6章. [ドメイン 4] 日本国労働基準法準拠 給与精算エンジンおよび Apache POI 給与台帳」に基づき実装を行ってください。
店舗締め日に基づく期間集計、日別適用時給による計算、1円単位四捨五入（HALF_UP）を適用してください。
F01（締日未到来の早期確定遮断）、F02（オーナー確認額照合）、F04（悲観的ロック）、F06（日別集計後端数処理）を実装し、Apache POIによる給与台帳(.xlsx)出力を完備してください。
`

---