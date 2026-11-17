INSERT INTO shift_schedule (
    store_id, store_staff_id, work_date, confirmed_start_at, confirmed_end_at,
    planned_break_minutes, status
)
SELECT 1, 3, d.work_date,
       TIMESTAMP(d.work_date, '09:00:00'), TIMESTAMP(d.work_date, '18:00:00'), 60, 'CONFIRMED'
FROM (
    SELECT DATE('2026-11-03') AS work_date UNION ALL
    SELECT DATE('2026-11-05') UNION ALL SELECT DATE('2026-11-07')
    UNION ALL SELECT DATE('2026-11-10') UNION ALL SELECT DATE('2026-11-12')
    UNION ALL SELECT DATE('2026-11-14') UNION ALL SELECT DATE('2026-11-17')
    UNION ALL SELECT DATE('2026-11-19') UNION ALL SELECT DATE('2026-11-21')
    UNION ALL SELECT DATE('2026-11-24') UNION ALL SELECT DATE('2026-11-26')
    UNION ALL SELECT DATE('2026-11-28')
) d
WHERE NOT EXISTS (
    SELECT 1 FROM shift_schedule s
    WHERE s.store_id = 1 AND s.store_staff_id = 3 AND s.work_date = d.work_date
);
