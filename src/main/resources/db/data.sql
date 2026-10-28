-- ============================================================
-- 로컬 개발 및 테스트용 초기 더미 데이터 (schema.sql 실행 후 실행)
-- ============================================================

-- 1. 회원 (비밀번호: 1234 -> Spring Security BCrypt 검증 완료 해시)
INSERT INTO member (member_id, email, password, name, phone, role, is_active)
VALUES 
    (1, 'owner@kintai.com', '$2a$10$hHU0CsbANOvlFw5XeHnHneRsiQcB.JQ15R.CsTgvZJ8kEjx.DAKqm', '田中 太郎 (オーナー)', '090-1111-2222', 'ROLE_OWNER', TRUE),
    (2, 'staff1@kintai.com', '$2a$10$hHU0CsbANOvlFw5XeHnHneRsiQcB.JQ15R.CsTgvZJ8kEjx.DAKqm', '佐藤 花子', '080-3333-4444', 'ROLE_STAFF', TRUE),
    (3, 'staff2@kintai.com', '$2a$10$hHU0CsbANOvlFw5XeHnHneRsiQcB.JQ15R.CsTgvZJ8kEjx.DAKqm', '鈴木 一郎', '070-5555-6666', 'ROLE_STAFF', TRUE)
ON DUPLICATE KEY UPDATE password=VALUES(password), name=VALUES(name);

-- 2. 점포 (도쿄 신주쿠 본점)
INSERT INTO store (store_id, owner_id, store_name, invite_code, qr_token, open_time, close_time, pay_cutoff_day)
VALUES 
    (1, 1, '東京 新宿本店', 'SHINJUKU01', 'TOKEN_QR_SHINJUKU_001', '09:00:00', '23:00:00', 99)
ON DUPLICATE KEY UPDATE store_name=VALUES(store_name);

-- 3. 점포 직원 매핑 (2026년 10월 도쿄도 최저임금 1,280엔 이상 기준: 佐藤 1300엔, 鈴木 1350엔)
INSERT INTO store_staff (store_staff_id, store_id, member_id, hourly_wage, daily_transport_fee, monthly_transport_limit, employment_status, joined_date)
VALUES 
    (1, 1, 2, 1300, 500, 15000, 'ACTIVE', '2026-10-01'),
    (2, 1, 3, 1350, 600, 15000, 'ACTIVE', '2026-10-15')
ON DUPLICATE KEY UPDATE hourly_wage=VALUES(hourly_wage);
