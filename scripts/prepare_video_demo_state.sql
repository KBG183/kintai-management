-- Apply only to the isolated video demo database before recording.
-- It preserves baseline records while removing pre-completed workflow outcomes.
UPDATE shift_schedule
SET status = 'CONFIRMED'
WHERE store_id = 1 AND store_staff_id = 5 AND work_date = '2026-11-06' AND status = 'SUBMITTED';

UPDATE attendance_mod_request
SET approval_status = 'REJECTED', reject_reason = '撮影前初期化された既存申請'
WHERE store_id = 1 AND approval_status = 'PENDING';
