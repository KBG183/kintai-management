-- Additional isolated data for the KINTAI product video demo.
-- Loaded only after prepare_local_demo_db.ps1 creates a fresh database.

-- Four extra staff make the owner screens readable without creating a crowded demo.
-- The base data.sql creates these store_staff IDs and names before this file runs:
-- 3 = 高橋 美咲 (staff3@kintai.com), 4 = 伊藤 蓮, 5 = 山本 葵, 6 = 小林 恒一.
-- The recording follows staff3 / 高橋 美咲; the other three are calendar/payroll context.
-- 2026-11-02: baseline attendance for a live correction request,
-- 2026-11-03: night work, 2026-11-04: overtime, 2026-11-05: late/night work.
INSERT INTO shift_schedule (
    store_id, store_staff_id, work_date, confirmed_start_at, confirmed_end_at,
    planned_break_minutes, status
)
VALUES
    (1, 3, '2026-11-02', '2026-11-02 09:00:00', '2026-11-02 18:00:00', 60, 'CONFIRMED'),
    (1, 3, '2026-11-03', '2026-11-03 09:00:00', '2026-11-03 18:00:00', 60, 'CONFIRMED'),
    (1, 3, '2026-11-05', '2026-11-05 09:00:00', '2026-11-05 18:00:00', 60, 'CONFIRMED'),
    (1, 3, '2026-11-07', '2026-11-07 09:00:00', '2026-11-07 18:00:00', 60, 'CONFIRMED'),
    (1, 3, '2026-11-10', '2026-11-10 09:00:00', '2026-11-10 18:00:00', 60, 'CONFIRMED'),
    (1, 3, '2026-11-12', '2026-11-12 09:00:00', '2026-11-12 18:00:00', 60, 'CONFIRMED'),
    (1, 3, '2026-11-14', '2026-11-14 09:00:00', '2026-11-14 18:00:00', 60, 'CONFIRMED'),
    (1, 3, '2026-11-17', '2026-11-17 09:00:00', '2026-11-17 18:00:00', 60, 'CONFIRMED'),
    (1, 3, '2026-11-19', '2026-11-19 09:00:00', '2026-11-19 18:00:00', 60, 'CONFIRMED'),
    (1, 3, '2026-11-21', '2026-11-21 09:00:00', '2026-11-21 18:00:00', 60, 'CONFIRMED'),
    (1, 3, '2026-11-24', '2026-11-24 09:00:00', '2026-11-24 18:00:00', 60, 'CONFIRMED'),
    (1, 3, '2026-11-26', '2026-11-26 09:00:00', '2026-11-26 18:00:00', 60, 'CONFIRMED'),
    (1, 3, '2026-11-28', '2026-11-28 09:00:00', '2026-11-28 18:00:00', 60, 'CONFIRMED'),
    (1, 4, '2026-11-03', '2026-11-03 14:00:00', '2026-11-03 23:00:00', 60, 'CONFIRMED'),
    (1, 5, '2026-11-04', '2026-11-04 10:00:00', '2026-11-04 20:00:00', 60, 'CONFIRMED'),
    (1, 6, '2026-11-05', '2026-11-05 17:00:00', '2026-11-05 23:00:00', 30, 'CONFIRMED');

INSERT INTO attendance (
    store_id, store_staff_id, shift_id, work_date,
    clock_in_at, clock_out_at, break_start_at, break_end_at,
    total_break_minutes, net_work_minutes, night_work_minutes,
    overtime_minutes, clock_in_method, attendance_status
)
SELECT 1, 3, NULL, '2026-11-02', '2026-11-02 09:00:00', '2026-11-02 18:00:00',
       '2026-11-02 13:00:00', '2026-11-02 14:00:00', 60, 480, 0, 0, 'BUTTON', 'COMPLETED'
UNION ALL
SELECT 1, 4, NULL, '2026-11-03', '2026-11-03 14:00:00', '2026-11-03 23:00:00',
       '2026-11-03 18:00:00', '2026-11-03 19:00:00', 60, 480, 60, 0, 'BUTTON', 'COMPLETED'
UNION ALL
SELECT 1, 5, NULL, '2026-11-04', '2026-11-04 10:00:00', '2026-11-04 20:00:00',
       '2026-11-04 14:00:00', '2026-11-04 15:00:00', 60, 540, 0, 60, 'BUTTON', 'COMPLETED'
UNION ALL
SELECT 1, 6, NULL, '2026-11-05', '2026-11-05 17:00:00', '2026-11-05 23:00:00',
       '2026-11-05 20:00:00', '2026-11-05 20:30:00', 30, 330, 60, 0, 'BUTTON', 'COMPLETED';

