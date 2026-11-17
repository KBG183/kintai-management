-- Synthetic browser demo data for the isolated local demo database only.
-- The prepare script loads this after schema.sql and data.sql.

-- Extend only the demo contracts back to the start of the synthetic year.
UPDATE store_staff
SET joined_date = '2026-01-01'
WHERE store_staff_id = 1;

UPDATE store_staff
SET joined_date = '2026-01-01'
WHERE store_staff_id = 2;

UPDATE store_staff_contract_history
SET effective_from = '2026-01-01'
WHERE contract_history_id = 1;

UPDATE store_staff_contract_history
SET effective_from = '2026-01-01'
WHERE contract_history_id = 2;

-- Create synthetic shifts and completed attendance through all 12 months of 2026.
-- Staff 1 works Mon/Wed/Fri, 09:00-18:00 with a 60-minute break.
-- Staff 2 works Tue/Thu/Sat, 14:00-23:00 with a 60-minute break,
-- including 60 minutes in the 22:00-23:00 night window.
INSERT INTO shift_schedule (
    store_id, store_staff_id, work_date, confirmed_start_at, confirmed_end_at,
    planned_break_minutes, status
)
WITH RECURSIVE calendar_days AS (
    SELECT DATE('2026-01-01') AS work_date
    UNION ALL
    SELECT DATE_ADD(work_date, INTERVAL 1 DAY)
    FROM calendar_days
    WHERE work_date < '2026-12-31'
), demo_shifts AS (
    SELECT 1 AS store_staff_id, work_date, '09:00:00' AS start_at, '18:00:00' AS end_at
    FROM calendar_days WHERE WEEKDAY(work_date) IN (0, 2, 4)
    UNION ALL
    SELECT 2 AS store_staff_id, work_date, '14:00:00' AS start_at, '23:00:00' AS end_at
    FROM calendar_days WHERE WEEKDAY(work_date) IN (1, 3, 5)
)
SELECT 1, store_staff_id, work_date,
       TIMESTAMP(work_date, start_at), TIMESTAMP(work_date, end_at), 60, 'CONFIRMED'
FROM demo_shifts;

INSERT INTO attendance (
    store_id, store_staff_id, shift_id, work_date,
    clock_in_at, clock_out_at, break_start_at, break_end_at,
    total_break_minutes, net_work_minutes, night_work_minutes,
    overtime_minutes, clock_in_method, attendance_status
)
WITH RECURSIVE calendar_days AS (
    SELECT DATE('2026-01-01') AS work_date
    UNION ALL
    SELECT DATE_ADD(work_date, INTERVAL 1 DAY)
    FROM calendar_days
    WHERE work_date < '2026-12-31'
), demo_shifts AS (
    SELECT
        1 AS store_staff_id,
        work_date,
        TIMESTAMP(work_date, '09:00:00') AS clock_in_at,
        TIMESTAMP(work_date, '18:00:00') AS clock_out_at,
        TIMESTAMP(work_date, '13:00:00') AS break_start_at,
        TIMESTAMP(work_date, '14:00:00') AS break_end_at,
        0 AS night_work_minutes
    FROM calendar_days
    WHERE WEEKDAY(work_date) IN (0, 2, 4)
    UNION ALL
    SELECT
        2 AS store_staff_id,
        work_date,
        TIMESTAMP(work_date, '14:00:00') AS clock_in_at,
        TIMESTAMP(work_date, '23:00:00') AS clock_out_at,
        TIMESTAMP(work_date, '18:00:00') AS break_start_at,
        TIMESTAMP(work_date, '19:00:00') AS break_end_at,
        60 AS night_work_minutes
    FROM calendar_days
    WHERE WEEKDAY(work_date) IN (1, 3, 5)
)
SELECT
    1,
    store_staff_id,
    NULL,
    work_date,
    clock_in_at,
    clock_out_at,
    break_start_at,
    break_end_at,
    60,
    480,
    night_work_minutes,
    0,
    'BUTTON',
    'COMPLETED'
FROM demo_shifts;
