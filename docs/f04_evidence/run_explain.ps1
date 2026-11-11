[Console]::OutputEncoding = [System.Text.Encoding]::UTF8
$OutputEncoding = [System.Text.Encoding]::UTF8

$query = @"
-- 다건 직원(1, 2, 3) 및 다건 급여 레코드 삽입
INSERT IGNORE INTO kintai_db.member (member_id, email, password, name, role) VALUES 
(3, 'staff3@example.com', 'dummy_hash', 'テストスタッフ3', 'ROLE_STAFF');

INSERT IGNORE INTO kintai_db.store_staff (store_staff_id, store_id, member_id, hourly_wage, joined_date) VALUES 
(3, 1, 3, 1400, '2026-10-01');

INSERT IGNORE INTO kintai_db.payroll (payroll_id, store_id, store_staff_id, target_year_month, range_start_date, range_end_date, applied_hourly_wage, gross_pay, payroll_status) VALUES 
(101, 1, 1, '2026-10', '2026-10-01', '2026-10-31', 1300, 100000, 'DRAFT'),
(102, 1, 2, '2026-10', '2026-10-01', '2026-10-31', 1350, 120000, 'DRAFT'),
(103, 1, 3, '2026-10', '2026-10-01', '2026-10-31', 1400, 130000, 'DRAFT');

EXPLAIN FORMAT=TREE SELECT payroll_id, store_id, store_staff_id, target_year_month 
FROM kintai_db.payroll 
WHERE store_id = 1 AND target_year_month = '2026-10' 
ORDER BY store_staff_id ASC, payroll_id ASC 
FOR UPDATE;

EXPLAIN FORMAT=JSON SELECT payroll_id, store_id, store_staff_id, target_year_month 
FROM kintai_db.payroll 
WHERE store_id = 1 AND target_year_month = '2026-10' 
ORDER BY store_staff_id ASC, payroll_id ASC 
FOR UPDATE;
"@

& 'C:\Program Files\MySQL\MySQL Server 26.7\bin\mysql.exe' -u root -p1234 -e $query
