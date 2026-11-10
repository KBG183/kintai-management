-- ====================================================================
-- KINTAI SYSTEM - MySQL 8.4 LTS 標準スキーマ定義 (schema.sql)
-- 勤怠・シフト・給与管理システム 統合DDL
-- ====================================================================

-- 1. 会員マスタ (Member)
CREATE TABLE IF NOT EXISTS member (
    member_id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '会員ID',
    email VARCHAR(100) NOT NULL UNIQUE COMMENT 'ログインID (メールアドレス)',
    password VARCHAR(255) NOT NULL COMMENT 'BCrypt暗号化パスワード',
    name VARCHAR(50) NOT NULL COMMENT '氏名',
    phone VARCHAR(20) NOT NULL COMMENT '連絡先電話番号',
    role VARCHAR(20) NOT NULL COMMENT '権限 (ROLE_OWNER, ROLE_STAFF, ROLE_ADMIN)',
    is_active BOOLEAN NOT NULL DEFAULT TRUE COMMENT 'アカウント有効フラグ',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '作成日時',
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新日時'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 2. 店舗マスタ (Store)
CREATE TABLE IF NOT EXISTS store (
    store_id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '店舗ID',
    owner_id BIGINT NOT NULL COMMENT 'オーナー会員ID',
    store_name VARCHAR(100) NOT NULL COMMENT '店舗名',
    invite_code VARCHAR(20) NOT NULL UNIQUE COMMENT 'スタッフ参加用招待コード',
    qr_token VARCHAR(100) NOT NULL COMMENT '打刻QR検証トークン',
    open_time TIME NOT NULL DEFAULT '09:00:00' COMMENT '基本開店時間',
    close_time TIME NOT NULL DEFAULT '23:00:00' COMMENT '基本閉店時間',
    pay_cutoff_day INT NOT NULL DEFAULT 99 COMMENT '給与締め日 (末日は99)',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '作成日時',
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新日時',
    CONSTRAINT fk_store_owner FOREIGN KEY (owner_id) REFERENCES member (member_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 3. 店舗所属スタッフ (Store Staff & 労働条件)
CREATE TABLE IF NOT EXISTS store_staff (
    store_staff_id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '店舗スタッフID',
    store_id BIGINT NOT NULL COMMENT '店舗ID',
    member_id BIGINT NOT NULL COMMENT '会員ID',
    hourly_wage INT NOT NULL COMMENT '基本時給 (円)',
    daily_transport_fee INT NOT NULL DEFAULT 0 COMMENT '1日往復交通費 (円)',
    monthly_transport_limit INT NOT NULL DEFAULT 15000 COMMENT '月間交通費上限額 (円)',
    employment_status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE' COMMENT '在籍状態 (ACTIVE, RESIGNED)',
    joined_date DATE NOT NULL COMMENT '入社年月日',
    resigned_date DATE NULL COMMENT '退職年月日',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '作成日時',
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新日時',
    CONSTRAINT uq_staff_store_member UNIQUE (store_id, member_id),
    CONSTRAINT fk_staff_store FOREIGN KEY (store_id) REFERENCES store (store_id),
    CONSTRAINT fk_staff_member FOREIGN KEY (member_id) REFERENCES member (member_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 4. シフトスケジュール (Shift Schedule)
CREATE TABLE IF NOT EXISTS shift_schedule (
    shift_id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT 'シフトID',
    store_id BIGINT NOT NULL COMMENT '店舗ID',
    store_staff_id BIGINT NOT NULL COMMENT '店舗スタッフID',
    work_date DATE NOT NULL COMMENT '勤務予定日',
    requested_start_at DATETIME NOT NULL COMMENT '希望開始日時',
    requested_end_at DATETIME NOT NULL COMMENT '希望終了日時',
    confirmed_start_at DATETIME NULL COMMENT '確定開始日時',
    confirmed_end_at DATETIME NULL COMMENT '確定終了日時',
    break_minutes INT NOT NULL DEFAULT 0 COMMENT '予定休憩時間 (分)',
    status VARCHAR(20) NOT NULL DEFAULT 'SUBMITTED' COMMENT '状態 (SUBMITTED, CONFIRMED, REJECTED)',
    staff_memo VARCHAR(255) NULL COMMENT 'スタッフメモ',
    owner_memo VARCHAR(255) NULL COMMENT '店長メモ',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '作成日時',
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新日時',
    INDEX idx_shift_store_date (store_id, work_date),
    INDEX idx_shift_staff_date (store_staff_id, work_date),
    CONSTRAINT fk_shift_store FOREIGN KEY (store_id) REFERENCES store (store_id),
    CONSTRAINT fk_shift_staff FOREIGN KEY (store_staff_id) REFERENCES store_staff (store_staff_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 5. 勤怠打刻記録 (Attendance)
CREATE TABLE IF NOT EXISTS attendance (
    attendance_id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '勤怠ID',
    store_id BIGINT NOT NULL COMMENT '店舗ID',
    store_staff_id BIGINT NOT NULL COMMENT '店舗スタッフID',
    shift_id BIGINT NULL COMMENT '連携確定シフトID',
    work_date DATE NOT NULL COMMENT '勤務基準日',
    clock_in_at DATETIME NOT NULL COMMENT '出勤打刻日時',
    clock_out_at DATETIME NULL COMMENT '退勤打刻日時',
    break_start_at DATETIME NULL COMMENT '最終休憩開始日時',
    break_end_at DATETIME NULL COMMENT '最終休憩終了日時',
    total_break_minutes INT NOT NULL DEFAULT 0 COMMENT '累計休憩時間 (分)',
    net_work_minutes INT NOT NULL DEFAULT 0 COMMENT '実労働時間 (分)',
    night_work_minutes INT NOT NULL DEFAULT 0 COMMENT '深夜労働時間 (分: 22:00~05:00)',
    overtime_minutes INT NOT NULL DEFAULT 0 COMMENT '時間外残業時間 (分: 1日8時間超過)',
    clock_in_method VARCHAR(20) NOT NULL DEFAULT 'BUTTON' COMMENT '打刻方式 (BUTTON, QR, MANUAL_APPROVED)',
    attendance_status VARCHAR(20) NOT NULL DEFAULT 'WORKING' COMMENT '状態 (WORKING, ON_BREAK, COMPLETED, MOD_REQUESTED)',
    open_flag TINYINT GENERATED ALWAYS AS (IF(clock_out_at IS NULL, 1, NULL)) VIRTUAL COMMENT '未退勤ガード用仮想生成列',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '作成日時',
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新日時',
    INDEX idx_att_store_date (store_id, work_date),
    INDEX idx_att_staff_date (store_staff_id, work_date),
    UNIQUE KEY uq_att_open_per_staff (store_staff_id, open_flag),
    CONSTRAINT fk_att_store FOREIGN KEY (store_id) REFERENCES store (store_id),
    CONSTRAINT fk_att_staff FOREIGN KEY (store_staff_id) REFERENCES store_staff (store_staff_id),
    CONSTRAINT fk_att_shift FOREIGN KEY (shift_id) REFERENCES shift_schedule (shift_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 5-1. 勤怠休憩区間履歴 (Attendance Break 1:N)
CREATE TABLE IF NOT EXISTS attendance_break (
    break_id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '休憩区間ID',
    attendance_id BIGINT NOT NULL COMMENT '勤怠ID',
    break_start_at DATETIME NOT NULL COMMENT '休憩開始日時',
    break_end_at DATETIME NULL COMMENT '休憩終了日時',
    duration_minutes INT NOT NULL DEFAULT 0 COMMENT '区間休憩時間 (分)',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '作成日時',
    INDEX idx_break_attendance (attendance_id),
    CONSTRAINT fk_break_attendance FOREIGN KEY (attendance_id) REFERENCES attendance (attendance_id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 6. 勤怠修正申請 (Attendance Modification Request)
CREATE TABLE IF NOT EXISTS attendance_mod_request (
    mod_request_id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '修正申請ID',
    attendance_id BIGINT NULL COMMENT '対象勤怠ID',
    store_id BIGINT NOT NULL COMMENT '店舗ID',
    store_staff_id BIGINT NOT NULL COMMENT '店舗スタッフID',
    work_date DATE NOT NULL COMMENT '勤務基準日',
    before_clock_in_at DATETIME NULL COMMENT '修正前出勤日時',
    before_clock_out_at DATETIME NULL COMMENT '修正前退勤日時',
    before_break_minutes INT NOT NULL DEFAULT 0 COMMENT '修正前休憩時間 (分)',
    requested_clock_in_at DATETIME NOT NULL COMMENT '修正希望出勤日時',
    requested_clock_out_at DATETIME NOT NULL COMMENT '修正希望退勤日時',
    requested_break_minutes INT NOT NULL DEFAULT 0 COMMENT '修正希望休憩時間 (分)',
    reason VARCHAR(500) NOT NULL COMMENT '修正申請理由',
    approval_status VARCHAR(20) NOT NULL DEFAULT 'PENDING' COMMENT '承認状態 (PENDING, APPROVED, REJECTED)',
    reviewer_id BIGINT NULL COMMENT '審査者会員ID',
    reject_reason VARCHAR(500) NULL COMMENT '却下理由',
    reviewed_at DATETIME NULL COMMENT '審査日時',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '作成日時',
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新日時',
    INDEX idx_mod_store_status (store_id, approval_status),
    INDEX idx_mod_staff_date (store_staff_id, work_date),
    CONSTRAINT fk_mod_attendance FOREIGN KEY (attendance_id) REFERENCES attendance (attendance_id),
    CONSTRAINT fk_mod_store FOREIGN KEY (store_id) REFERENCES store (store_id),
    CONSTRAINT fk_mod_staff FOREIGN KEY (store_staff_id) REFERENCES store_staff (store_staff_id),
    CONSTRAINT fk_mod_reviewer FOREIGN KEY (reviewer_id) REFERENCES member (member_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 6-1. 修正申請休憩区間 (Attendance Mod Break 1:N)
CREATE TABLE IF NOT EXISTS attendance_mod_break (
    mod_break_id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '修正申請休憩区間ID',
    mod_request_id BIGINT NOT NULL COMMENT '修正申請ID',
    break_set_type VARCHAR(20) NOT NULL DEFAULT 'REQUESTED' COMMENT '区分 (BEFORE, REQUESTED)',
    break_start_at DATETIME NOT NULL COMMENT '休憩開始日時',
    break_end_at DATETIME NOT NULL COMMENT '休憩終了日時',
    duration_minutes INT NOT NULL DEFAULT 0 COMMENT '区間休憩時間 (分)',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '作成日時',
    INDEX idx_mod_break_request (mod_request_id),
    CONSTRAINT fk_mod_break_request FOREIGN KEY (mod_request_id) REFERENCES attendance_mod_request (mod_request_id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 7. 月次給与台帳 (Payroll)
CREATE TABLE IF NOT EXISTS payroll (
    payroll_id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '給与台帳ID',
    store_id BIGINT NOT NULL COMMENT '店舗ID',
    store_staff_id BIGINT NOT NULL COMMENT '店舗スタッフID',
    target_year_month CHAR(7) NOT NULL COMMENT '対象年月 (YYYY-MM)',
    range_start_date DATE NOT NULL COMMENT '集計期間開始日',
    range_end_date DATE NOT NULL COMMENT '集計期間終了日',
    work_days_count INT NOT NULL DEFAULT 0 COMMENT '出勤日数',
    total_work_minutes INT NOT NULL DEFAULT 0 COMMENT '実労働時間合計 (分)',
    total_night_minutes INT NOT NULL DEFAULT 0 COMMENT '深夜労働時間合計 (分)',
    total_overtime_minutes INT NOT NULL DEFAULT 0 COMMENT '時間外残業時間合計 (分)',
    applied_hourly_wage INT NOT NULL COMMENT '適用時給スナップショット (円)',
    base_pay INT NOT NULL DEFAULT 0 COMMENT '基本給 (円)',
    night_allowance INT NOT NULL DEFAULT 0 COMMENT '深夜割増手当 (円)',
    overtime_allowance INT NOT NULL DEFAULT 0 COMMENT '時間外割増手当 (円)',
    transport_allowance INT NOT NULL DEFAULT 0 COMMENT '交通費支給額 (円)',
    gross_pay INT NOT NULL DEFAULT 0 COMMENT '総支給額 (円)',
    payroll_status VARCHAR(20) NOT NULL DEFAULT 'DRAFT' COMMENT '状態 (DRAFT, CONFIRMED, PAID)',
    confirmed_at DATETIME NULL COMMENT '確定日時',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '作成日時',
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新日時',
    CONSTRAINT uq_payroll_staff_month UNIQUE (store_staff_id, target_year_month),
    CONSTRAINT fk_payroll_store FOREIGN KEY (store_id) REFERENCES store (store_id),
    CONSTRAINT fk_payroll_staff FOREIGN KEY (store_staff_id) REFERENCES store_staff (store_staff_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
