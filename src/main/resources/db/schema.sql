-- 데이터베이스 생성 및 선택
CREATE DATABASE IF NOT EXISTS kintai_db CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE kintai_db;

-- 1. 회원 (점주 및 알바생)
CREATE TABLE IF NOT EXISTS member (
    member_id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '회원 PK',
    email VARCHAR(100) NOT NULL UNIQUE COMMENT '로그인 이메일',
    password VARCHAR(255) NOT NULL COMMENT 'BCrypt 암호화 비밀번호',
    name VARCHAR(50) NOT NULL COMMENT '이름',
    phone VARCHAR(20) NOT NULL COMMENT '연락처',
    role VARCHAR(20) NOT NULL COMMENT '권한 (ROLE_OWNER, ROLE_STAFF)',
    is_active BOOLEAN NOT NULL DEFAULT TRUE COMMENT '계정 활성화 여부',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 2. 점포
CREATE TABLE IF NOT EXISTS store (
    store_id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '점포 PK',
    owner_id BIGINT NOT NULL COMMENT '점주 회원 FK',
    store_name VARCHAR(100) NOT NULL COMMENT '매장명',
    invite_code VARCHAR(20) NOT NULL UNIQUE COMMENT '직원 등록용 초대 코드',
    qr_token VARCHAR(100) NOT NULL COMMENT 'QR 출퇴근 인증 토큰',
    open_time TIME NOT NULL DEFAULT '09:00:00' COMMENT '기본 오픈 시간',
    close_time TIME NOT NULL DEFAULT '23:00:00' COMMENT '기본 마감 시간',
    pay_cutoff_day INT NOT NULL DEFAULT 99 COMMENT '급여 마감일 (99=말일)',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT fk_store_owner FOREIGN KEY (owner_id) REFERENCES member (member_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 3. 점포 소속 직원 (근로 계약 및 시급/교통비 기준)
CREATE TABLE IF NOT EXISTS store_staff (
    store_staff_id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '점포 직원 매핑 PK',
    store_id BIGINT NOT NULL COMMENT '점포 FK',
    member_id BIGINT NOT NULL COMMENT '직원 회원 FK',
    hourly_wage INT NOT NULL COMMENT '기본 시급(엔/원)',
    daily_transport_fee INT NOT NULL DEFAULT 0 COMMENT '1일 왕복 교통비',
    monthly_transport_limit INT NOT NULL DEFAULT 15000 COMMENT '월 교통비 상한액',
    employment_status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE' COMMENT '재직상태 (PENDING, ACTIVE, RESIGNED)',
    joined_date DATE NOT NULL COMMENT '입사일',
    resigned_date DATE NULL COMMENT '퇴사일',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT uq_store_member UNIQUE (store_id, member_id),
    CONSTRAINT fk_staff_store FOREIGN KEY (store_id) REFERENCES store (store_id),
    CONSTRAINT fk_staff_member FOREIGN KEY (member_id) REFERENCES member (member_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 4. 시프트 스케줄 (팀원 A: 희망 제출 및 확정)
CREATE TABLE IF NOT EXISTS shift_schedule (
    shift_id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '시프트 PK',
    store_id BIGINT NOT NULL COMMENT '점포 FK',
    store_staff_id BIGINT NOT NULL COMMENT '점포 직원 FK',
    work_date DATE NOT NULL COMMENT '근무 기준일',
    requested_start_at DATETIME NULL COMMENT '희망 출근 일시',
    requested_end_at DATETIME NULL COMMENT '희망 퇴근 일시',
    confirmed_start_at DATETIME NULL COMMENT '확정 출근 일시',
    confirmed_end_at DATETIME NULL COMMENT '확정 퇴근 일시',
    planned_break_minutes INT NOT NULL DEFAULT 0 COMMENT '계획 휴게시간(분)',
    status VARCHAR(20) NOT NULL DEFAULT 'SUBMITTED' COMMENT '상태 (SUBMITTED, CONFIRMED, REJECTED)',
    staff_memo VARCHAR(255) NULL COMMENT '알바생 메모',
    owner_memo VARCHAR(255) NULL COMMENT '점주 메모',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_shift_store_date (store_id, work_date),
    INDEX idx_shift_staff_date (store_staff_id, work_date),
    CONSTRAINT fk_shift_store FOREIGN KEY (store_id) REFERENCES store (store_id),
    CONSTRAINT fk_shift_staff FOREIGN KEY (store_staff_id) REFERENCES store_staff (store_staff_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 5. 출퇴근 근태 기록 (팀원 B: 타임카드 및 일별 시간 산출)
CREATE TABLE IF NOT EXISTS attendance (
    attendance_id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '근태 PK',
    store_id BIGINT NOT NULL COMMENT '점포 FK',
    store_staff_id BIGINT NOT NULL COMMENT '점포 직원 FK',
    shift_id BIGINT NULL COMMENT '연결된 확정 시프트 FK (미계획 출근 시 NULL)',
    work_date DATE NOT NULL COMMENT '출근 기준일',
    clock_in_at DATETIME NOT NULL COMMENT '출근 타각 일시',
    clock_out_at DATETIME NULL COMMENT '퇴근 타각 일시',
    break_start_at DATETIME NULL COMMENT '휴게 시작 일시',
    break_end_at DATETIME NULL COMMENT '휴게 종료 일시',
    total_break_minutes INT NOT NULL DEFAULT 0 COMMENT '총 휴게시간(분)',
    net_work_minutes INT NOT NULL DEFAULT 0 COMMENT '실근무시간(분, 휴게 제외)',
    night_work_minutes INT NOT NULL DEFAULT 0 COMMENT '야간근로시간(분, 22:00~05:00)',
    overtime_minutes INT NOT NULL DEFAULT 0 COMMENT '연장잔업시간(분, 1일 8시간 초과분)',
    clock_in_method VARCHAR(20) NOT NULL DEFAULT 'BUTTON' COMMENT '타각 방식 (BUTTON, QR, MANUAL_APPROVED)',
    attendance_status VARCHAR(20) NOT NULL DEFAULT 'WORKING' COMMENT '상태 (WORKING, ON_BREAK, COMPLETED, MOD_REQUESTED)',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_att_store_date (store_id, work_date),
    INDEX idx_att_staff_date (store_staff_id, work_date),
    CONSTRAINT fk_att_store FOREIGN KEY (store_id) REFERENCES store (store_id),
    CONSTRAINT fk_att_staff FOREIGN KEY (store_staff_id) REFERENCES store_staff (store_staff_id),
    CONSTRAINT fk_att_shift FOREIGN KEY (shift_id) REFERENCES shift_schedule (shift_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 6. 근태 수정 요청 (팀원 B: 타각 누락/오류 수정 및 점주 승인)
CREATE TABLE IF NOT EXISTS attendance_mod_request (
    mod_request_id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '근태 수정 요청 PK',
    attendance_id BIGINT NULL COMMENT '대상 근태 FK (타각 완전 누락 사후 생성 시 NULL 허용)',
    store_staff_id BIGINT NOT NULL COMMENT '요청 직원 FK',
    before_clock_in_at DATETIME NULL COMMENT '수정 전 출근 일시 (사후 신규 생성 시 NULL 허용)',
    before_clock_out_at DATETIME NULL COMMENT '수정 전 퇴근 일시',
    before_break_minutes INT NOT NULL DEFAULT 0 COMMENT '수정 전 휴게시간(분)',
    requested_clock_in_at DATETIME NOT NULL COMMENT '수정 요청 출근 일시',
    requested_clock_out_at DATETIME NOT NULL COMMENT '수정 요청 퇴근 일시',
    requested_break_minutes INT NOT NULL DEFAULT 0 COMMENT '수정 요청 휴게시간(분)',
    reason VARCHAR(500) NOT NULL COMMENT '수정 요청 사유',
    approval_status VARCHAR(20) NOT NULL DEFAULT 'PENDING' COMMENT '상태 (PENDING, APPROVED, REJECTED)',
    reviewer_id BIGINT NULL COMMENT '승인/반려 처리한 점주 회원 FK',
    reject_reason VARCHAR(500) NULL COMMENT '반려 사유',
    reviewed_at DATETIME NULL COMMENT '승인/반려 처리 일시',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_mod_attendance FOREIGN KEY (attendance_id) REFERENCES attendance (attendance_id),
    CONSTRAINT fk_mod_staff FOREIGN KEY (store_staff_id) REFERENCES store_staff (store_staff_id),
    CONSTRAINT fk_mod_reviewer FOREIGN KEY (reviewer_id) REFERENCES member (member_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 7. 월별 급여 정산 (팀원 B: 일본 노동기준법 계산 결과 및 엑셀 출력 원본)
CREATE TABLE IF NOT EXISTS payroll (
    payroll_id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '급여 정산 PK',
    store_id BIGINT NOT NULL COMMENT '점포 FK',
    store_staff_id BIGINT NOT NULL COMMENT '점포 직원 FK',
    target_year_month CHAR(7) NOT NULL COMMENT '귀속 연월 (YYYY-MM)',
    work_days_count INT NOT NULL DEFAULT 0 COMMENT '총 출근 일수',
    total_work_minutes INT NOT NULL DEFAULT 0 COMMENT '월 총 실근무시간(분)',
    total_night_minutes INT NOT NULL DEFAULT 0 COMMENT '월 총 야간근로시간(분)',
    total_overtime_minutes INT NOT NULL DEFAULT 0 COMMENT '월 총 연장근로시간(분)',
    applied_hourly_wage INT NOT NULL COMMENT '정산 시점 기본 시급 스냅샷',
    base_pay INT NOT NULL DEFAULT 0 COMMENT '기본급 (실근무시간 기준)',
    night_allowance INT NOT NULL DEFAULT 0 COMMENT '야간 근로 할증 수당 (25% 가산분)',
    overtime_allowance INT NOT NULL DEFAULT 0 COMMENT '연장 잔업 할증 수당 (25% 가산분)',
    transport_allowance INT NOT NULL DEFAULT 0 COMMENT '교통비 지급액 (월 상한액 적용 후)',
    gross_pay INT NOT NULL DEFAULT 0 COMMENT '총 지급액',
    payroll_status VARCHAR(20) NOT NULL DEFAULT 'DRAFT' COMMENT '상태 (DRAFT, CONFIRMED, PAID)',
    confirmed_at DATETIME NULL COMMENT '정산 확정 일시',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT uq_payroll_staff_month UNIQUE (store_staff_id, target_year_month),
    CONSTRAINT fk_payroll_store FOREIGN KEY (store_id) REFERENCES store (store_id),
    CONSTRAINT fk_payroll_staff FOREIGN KEY (store_staff_id) REFERENCES store_staff (store_staff_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
