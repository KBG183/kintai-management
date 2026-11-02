-- ==============================================================================
-- 日本型 勤怠・シフト・給与管理システム (Kintai Management System)
-- データベース スキーマ 定義書 (schema.sql) - rev.3 最終完全版
-- ==============================================================================

CREATE DATABASE IF NOT EXISTS kintai_db CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE kintai_db;

-- 1. 会員 (オーナーおよびスタッフアカウントマスタ)
CREATE TABLE IF NOT EXISTS member (
    member_id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '会員ID (PK)',
    email VARCHAR(100) NOT NULL UNIQUE COMMENT 'ログインメールアドレス',
    password VARCHAR(255) NOT NULL COMMENT 'BCrypt暗号化パスワード',
    name VARCHAR(50) NOT NULL COMMENT '氏名',
    phone VARCHAR(20) NOT NULL COMMENT '電話番号',
    role VARCHAR(20) NOT NULL COMMENT '権限 (ROLE_OWNER, ROLE_STAFF)',
    is_active BOOLEAN NOT NULL DEFAULT TRUE COMMENT 'アカウント有効状態',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 2. 店舗 (店舗情報および給与締日・打刻基準設定)
CREATE TABLE IF NOT EXISTS store (
    store_id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '店舗ID (PK)',
    owner_id BIGINT NOT NULL COMMENT 'オーナー会員ID (FK)',
    store_name VARCHAR(100) NOT NULL COMMENT '店舗名',
    prefecture_code CHAR(2) NOT NULL DEFAULT '13' COMMENT '都道府県コード (初期値 13=東京都)',
    invite_code VARCHAR(20) NOT NULL UNIQUE COMMENT 'スタッフ登録用招待コード',
    qr_token VARCHAR(100) NOT NULL UNIQUE COMMENT '店舗別打刻QRトークン',
    open_time TIME NOT NULL DEFAULT '09:00:00' COMMENT '基本開店時刻',
    close_time TIME NOT NULL DEFAULT '23:00:00' COMMENT '基本閉店時刻',
    pay_cutoff_day INT NOT NULL DEFAULT 99 COMMENT '給与締日 (1~28 または 99=末日締)',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT fk_store_owner FOREIGN KEY (owner_id) REFERENCES member (member_id),
    CONSTRAINT chk_store_cutoff CHECK (pay_cutoff_day BETWEEN 1 AND 28 OR pay_cutoff_day = 99)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 3. 店舗所属スタッフ (掛け持ちマッピングおよび個別時給・通勤手当契約)
CREATE TABLE IF NOT EXISTS store_staff (
    store_staff_id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '店舗スタッフ所属ID (PK)',
    store_id BIGINT NOT NULL COMMENT '店舗ID (FK)',
    member_id BIGINT NOT NULL COMMENT '会員ID (FK)',
    hourly_wage INT NOT NULL COMMENT '現在表示用時給 (円; 計算元本は契約履歴を参照)',
    daily_transport_fee INT NOT NULL DEFAULT 0 COMMENT '1日往復交通費 (円)',
    monthly_transport_limit INT NOT NULL DEFAULT 15000 COMMENT '月額交通費支給上限 (円)',
    employment_status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE' COMMENT '在籍状態 (PENDING, ACTIVE, RESIGNED)',
    joined_date DATE NOT NULL COMMENT '入社日',
    resigned_date DATE NULL COMMENT '退職日',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT uq_store_member UNIQUE (store_id, member_id),
    CONSTRAINT uq_store_staff_id_store UNIQUE (store_staff_id, store_id),
    CONSTRAINT fk_staff_store FOREIGN KEY (store_id) REFERENCES store (store_id),
    CONSTRAINT fk_staff_member FOREIGN KEY (member_id) REFERENCES member (member_id),
    CONSTRAINT chk_staff_wage CHECK (hourly_wage > 0),
    CONSTRAINT chk_staff_transport CHECK (daily_transport_fee >= 0 AND monthly_transport_limit >= 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 3-A. スタッフ契約条件履歴 (適用日基準; 給与計算時の適用時給・交通費参照マスタ)
CREATE TABLE IF NOT EXISTS store_staff_contract_history (
    contract_history_id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '契約履歴ID (PK)',
    store_staff_id BIGINT NOT NULL COMMENT '店舗スタッフID (FK)',
    effective_from DATE NOT NULL COMMENT '適用開始日 (含む)',
    effective_to DATE NULL COMMENT '適用終了日 (含まない; NULLの場合は現在有効)',
    hourly_wage INT NOT NULL COMMENT '適用時給 (円)',
    daily_transport_fee INT NOT NULL DEFAULT 0 COMMENT '適用1日通勤手当支給額 (円)',
    monthly_transport_limit INT NOT NULL DEFAULT 15000 COMMENT '適用月額通勤手当支給上限 (円)',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_contract_effective_from UNIQUE (store_staff_id, effective_from),
    CONSTRAINT fk_contract_staff FOREIGN KEY (store_staff_id) REFERENCES store_staff (store_staff_id),
    CONSTRAINT chk_contract_dates CHECK (effective_to IS NULL OR effective_to > effective_from),
    CONSTRAINT chk_contract_wage CHECK (hourly_wage > 0),
    CONSTRAINT chk_contract_transport CHECK (daily_transport_fee >= 0 AND monthly_transport_limit >= 0),
    INDEX idx_contract_staff_period (store_staff_id, effective_from, effective_to)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 4. シフトスケジュール (希望シフト提出および店長確定統合テーブル)
CREATE TABLE IF NOT EXISTS shift_schedule (
    shift_id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT 'シフトID (PK)',
    store_id BIGINT NOT NULL COMMENT '店舗ID (FK)',
    store_staff_id BIGINT NOT NULL COMMENT '店舗スタッフID (FK)',
    work_date DATE NOT NULL COMMENT '勤務基準日',
    requested_start_at DATETIME NULL COMMENT '希望出勤日時',
    requested_end_at DATETIME NULL COMMENT '希望退勤日時',
    confirmed_start_at DATETIME NULL COMMENT '確定出勤日時',
    confirmed_end_at DATETIME NULL COMMENT '確定退勤日時',
    planned_break_minutes INT NOT NULL DEFAULT 0 COMMENT '計画休憩時間 (分)',
    status VARCHAR(20) NOT NULL DEFAULT 'SUBMITTED' COMMENT '状態 (SUBMITTED, CONFIRMED, REJECTED)',
    staff_memo VARCHAR(255) NULL COMMENT 'スタッフ側メモ',
    owner_memo VARCHAR(255) NULL COMMENT '店長側メモ',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_shift_store_date (store_id, work_date),
    INDEX idx_shift_staff_date (store_staff_id, work_date),
    CONSTRAINT fk_shift_store FOREIGN KEY (store_id) REFERENCES store (store_id),
    CONSTRAINT fk_shift_staff_store FOREIGN KEY (store_staff_id, store_id) REFERENCES store_staff (store_staff_id, store_id),
    CONSTRAINT chk_shift_break CHECK (planned_break_minutes >= 0),
    CONSTRAINT chk_shift_status CHECK (status IN ('SUBMITTED', 'CONFIRMED', 'REJECTED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 5. 出退勤勤怠実績 (タイムカードおよび日別実労働・深夜・時間外の分単位集計)
CREATE TABLE IF NOT EXISTS attendance (
    attendance_id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '勤怠実績ID (PK)',
    store_id BIGINT NOT NULL COMMENT '店舗ID (FK)',
    store_staff_id BIGINT NOT NULL COMMENT '店舗スタッフID (FK)',
    shift_id BIGINT NULL COMMENT '紐付く確定シフトID (未計画出勤時はNULL)',
    work_date DATE NOT NULL COMMENT '出勤基準日',
    clock_in_at DATETIME NOT NULL COMMENT '出勤打刻日時',
    clock_out_at DATETIME NULL COMMENT '退勤打刻日時',
    break_start_at DATETIME NULL COMMENT '進行中休憩の開始時刻 (ON_BREAK時のみ設定)',
    break_end_at DATETIME NULL COMMENT '直近完了休憩の終了時刻 (合算値はattendance_breakを参照)',
    total_break_minutes INT NOT NULL DEFAULT 0 COMMENT '完了済み休憩合計時間 (分)',
    net_work_minutes INT NOT NULL DEFAULT 0 COMMENT '実労働時間 (分; 休憩を除く)',
    night_work_minutes INT NOT NULL DEFAULT 0 COMMENT '深夜労働時間 (分; 22:00 ~ 05:00)',
    overtime_minutes INT NOT NULL DEFAULT 0 COMMENT '時間外残業時間 (分; 1日8時間=480分超過分)',
    clock_in_method VARCHAR(20) NOT NULL DEFAULT 'BUTTON' COMMENT '打刻方式 (BUTTON, QR, MANUAL_APPROVED)',
    attendance_status VARCHAR(20) NOT NULL DEFAULT 'WORKING' COMMENT 'ステータス (WORKING, ON_BREAK, COMPLETED, MOD_REQUESTED)',
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

-- 5-A. 勤怠別休憩区間 (1日複数回の休憩区間を完全保存)
CREATE TABLE IF NOT EXISTS attendance_break (
    attendance_break_id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '休憩区間ID (PK)',
    attendance_id BIGINT NOT NULL COMMENT '勤怠ID (FK)',
    break_start_at DATETIME NOT NULL COMMENT '休憩開始日時',
    break_end_at DATETIME NOT NULL COMMENT '休憩終了日時',
    duration_minutes INT NOT NULL COMMENT '休憩区間長 (分)',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_break_attendance (attendance_id, break_start_at),
    CONSTRAINT fk_break_attendance FOREIGN KEY (attendance_id) REFERENCES attendance (attendance_id),
    CONSTRAINT chk_break_range CHECK (break_end_at > break_start_at),
    CONSTRAINT chk_break_duration CHECK (duration_minutes > 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 6. 勤怠修正申請 (打刻漏れ・修正および店長承認の監査ログ)
CREATE TABLE IF NOT EXISTS attendance_mod_request (
    mod_request_id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '勤怠修正申請ID (PK)',
    attendance_id BIGINT NULL COMMENT '対象勤怠ID (打刻漏れによる新規生成時はNULL許容)',
    store_id BIGINT NOT NULL COMMENT '店舗ID (FK)',
    store_staff_id BIGINT NOT NULL COMMENT '申請スタッフID (FK)',
    work_date DATE NOT NULL COMMENT '勤務基準日',
    before_clock_in_at DATETIME NULL COMMENT '修正前出勤日時',
    before_clock_out_at DATETIME NULL COMMENT '修正前退勤日時',
    before_break_minutes INT NOT NULL DEFAULT 0 COMMENT '修正前休憩時間 (分)',
    requested_clock_in_at DATETIME NOT NULL COMMENT '修正希望出勤日時',
    requested_clock_out_at DATETIME NOT NULL COMMENT '修正希望退勤日時',
    requested_break_minutes INT NOT NULL DEFAULT 0 COMMENT '修正希望休憩時間 (分)',
    reason VARCHAR(500) NOT NULL COMMENT '修正申請理由',
    approval_status VARCHAR(20) NOT NULL DEFAULT 'PENDING' COMMENT '承認状態 (PENDING, APPROVED, REJECTED)',
    reviewer_id BIGINT NULL COMMENT '承認/却下を行ったオーナー会員ID (FK)',
    reject_reason VARCHAR(500) NULL COMMENT '却下理由',
    reviewed_at DATETIME NULL COMMENT '承認/却下処理日時',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_mod_attendance FOREIGN KEY (attendance_id) REFERENCES attendance (attendance_id),
    CONSTRAINT fk_mod_store FOREIGN KEY (store_id) REFERENCES store (store_id),
    CONSTRAINT fk_mod_staff_store FOREIGN KEY (store_staff_id, store_id) REFERENCES store_staff (store_staff_id, store_id),
    CONSTRAINT fk_mod_reviewer FOREIGN KEY (reviewer_id) REFERENCES member (member_id),
    CONSTRAINT chk_mod_status CHECK (approval_status IN ('PENDING', 'APPROVED', 'REJECTED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 6-A. 修正申請の休憩区間スナップショット (承認前・申請値を監査目的で保存)
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

-- 7. 月次給与精算 (適用ルールに基づく計算スナップショットおよび帳票元本)
CREATE TABLE IF NOT EXISTS payroll (
    payroll_id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '給与精算ID (PK)',
    store_id BIGINT NOT NULL COMMENT '店舗ID (FK)',
    store_staff_id BIGINT NOT NULL COMMENT '店舗スタッフID (FK)',
    target_year_month CHAR(7) NOT NULL COMMENT '精算対象年月 (YYYY-MM)',
    range_start_date DATE NOT NULL COMMENT '集計期間開始日 (含む)',
    range_end_date DATE NOT NULL COMMENT '集計期間終了日 (含む)',
    work_days_count INT NOT NULL DEFAULT 0 COMMENT '総出勤日数',
    total_work_minutes INT NOT NULL DEFAULT 0 COMMENT '月間総実労働時間 (分)',
    total_night_minutes INT NOT NULL DEFAULT 0 COMMENT '月間総深夜労働時間 (分)',
    total_overtime_minutes INT NOT NULL DEFAULT 0 COMMENT '月間総時間外労働時間 (分)',
    applied_hourly_wage INT NULL COMMENT '精算期間中に時給が単一の場合の表示用スナップショット',
    applied_transport_limit INT NOT NULL DEFAULT 0 COMMENT '精算時に適用した月額交通費上限スナップショット',
    base_pay INT NOT NULL DEFAULT 0 COMMENT '基本給 (日別適用時給基準 100%)',
    night_allowance INT NOT NULL DEFAULT 0 COMMENT '深夜割増手当 (25% 加算分)',
    overtime_allowance INT NOT NULL DEFAULT 0 COMMENT '時間外割増手当 (25% 加算分)',
    transport_allowance INT NOT NULL DEFAULT 0 COMMENT '交通費支給額 (月額上限適用後)',
    gross_pay INT NOT NULL DEFAULT 0 COMMENT '総支給額 (税引前)',
    payroll_status VARCHAR(20) NOT NULL DEFAULT 'DRAFT' COMMENT '状態 (DRAFT, CONFIRMED, PAID)',
    confirmed_at DATETIME NULL COMMENT '精算確定日時',
    paid_at DATETIME NULL COMMENT '支払処理日時',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT uq_payroll_staff_month UNIQUE (store_staff_id, target_year_month),
    CONSTRAINT fk_payroll_store FOREIGN KEY (store_id) REFERENCES store (store_id),
    CONSTRAINT fk_payroll_staff_store FOREIGN KEY (store_staff_id, store_id) REFERENCES store_staff (store_staff_id, store_id),
    CONSTRAINT chk_payroll_range CHECK (range_end_date >= range_start_date),
    CONSTRAINT chk_payroll_status CHECK (payroll_status IN ('DRAFT', 'CONFIRMED', 'PAID'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 7-A. 給与確定時点の日別詳細スナップショット (確定明細書と勤怠変更後の値の不整合防止)
CREATE TABLE IF NOT EXISTS payroll_daily_detail (
    payroll_daily_detail_id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '給与日別詳細ID (PK)',
    payroll_id BIGINT NOT NULL COMMENT '給与精算ID (FK)',
    attendance_id BIGINT NOT NULL COMMENT '元勤怠ID (FK)',
    work_date DATE NOT NULL COMMENT '勤務日',
    applied_hourly_wage INT NOT NULL COMMENT '該当勤務日適用時給スナップショット',
    total_break_minutes INT NOT NULL DEFAULT 0,
    net_work_minutes INT NOT NULL DEFAULT 0,
    night_work_minutes INT NOT NULL DEFAULT 0,
    overtime_minutes INT NOT NULL DEFAULT 0,
    base_pay INT NOT NULL DEFAULT 0,
    night_allowance INT NOT NULL DEFAULT 0,
    overtime_allowance INT NOT NULL DEFAULT 0,
    transport_amount INT NOT NULL DEFAULT 0 COMMENT '日割り配分交通費',
    CONSTRAINT fk_payroll_detail_payroll FOREIGN KEY (payroll_id) REFERENCES payroll (payroll_id),
    CONSTRAINT fk_payroll_detail_attendance FOREIGN KEY (attendance_id) REFERENCES attendance (attendance_id),
    CONSTRAINT uq_payroll_detail_attendance UNIQUE (payroll_id, attendance_id),
    INDEX idx_payroll_detail_date (payroll_id, work_date)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
