-- ============================================================
-- ローカル開発および動作確認用 初期ダミーデータ (schema.sql 適用後に実行)
-- ============================================================

-- 1. 会員 (パスワード: 1234 -> Spring Security BCrypt ハッシュ済み)
INSERT INTO member (member_id, email, password, name, phone, role, is_active)
VALUES 
    (1, 'owner@kintai.com', '$2a$10$hHU0CsbANOvlFw5XeHnHneRsiQcB.JQ15R.CsTgvZJ8kEjx.DAKqm', '田中 太郎 (オーナー)', '090-1111-2222', 'ROLE_OWNER', TRUE),
    (2, 'staff1@kintai.com', '$2a$10$hHU0CsbANOvlFw5XeHnHneRsiQcB.JQ15R.CsTgvZJ8kEjx.DAKqm', '佐藤 花子', '080-3333-4444', 'ROLE_STAFF', TRUE),
    (3, 'staff2@kintai.com', '$2a$10$hHU0CsbANOvlFw5XeHnHneRsiQcB.JQ15R.CsTgvZJ8kEjx.DAKqm', '鈴木 一郎', '070-5555-6666', 'ROLE_STAFF', TRUE)
ON DUPLICATE KEY UPDATE password=VALUES(password), name=VALUES(name);

-- 2. 店舗 (東京 新宿本店)
INSERT INTO store (store_id, owner_id, store_name, invite_code, qr_token, open_time, close_time, pay_cutoff_day)
VALUES 
    (1, 1, '東京 新宿本店', 'SHINJUKU01', 'TOKEN_QR_SHINJUKU_001', '09:00:00', '23:00:00', 99)
ON DUPLICATE KEY UPDATE store_name=VALUES(store_name);

-- 3. 店舗所属スタッフマスタ (2026年10月 東京都最低賃金 1,280円 準拠: 佐藤 1,300円, 鈴木 1,350円)
INSERT INTO store_staff (store_staff_id, store_id, member_id, hourly_wage, daily_transport_fee, monthly_transport_limit, employment_status, joined_date)
VALUES 
    (1, 1, 2, 1300, 500, 15000, 'ACTIVE', '2026-10-01'),
    (2, 1, 3, 1350, 600, 15000, 'ACTIVE', '2026-10-15')
ON DUPLICATE KEY UPDATE hourly_wage=VALUES(hourly_wage);

-- 3-A. スタッフ契約履歴 (給与計算の単一情報源)
INSERT INTO store_staff_contract_history (contract_history_id, store_staff_id, effective_from, effective_to, hourly_wage, daily_transport_fee, monthly_transport_limit)
VALUES 
    (1, 1, '2026-10-01', NULL, 1300, 500, 15000),
    (2, 2, '2026-10-15', NULL, 1350, 600, 15000)
ON DUPLICATE KEY UPDATE hourly_wage=VALUES(hourly_wage);
