package com.example.kintai.domain.attendance.service;

import com.example.kintai.domain.attendance.dto.*;
import com.example.kintai.domain.attendance.mapper.AttendanceMapper;
import com.example.kintai.domain.attendance.util.AttendanceTimeCalculator;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Service
@Transactional(readOnly = true)
public class AttendanceService {

    private final AttendanceMapper attendanceMapper;

    public AttendanceService(AttendanceMapper attendanceMapper) {
        this.attendanceMapper = attendanceMapper;
    }

    /**
     * リアルタイム打刻処理（出勤・休憩開始・休憩終了・退勤）
     */
    @Transactional
    public AttendanceDto punch(AttendancePunchDto punchDto) {
        Long storeStaffId = punchDto.getStoreStaffId();
        String punchType = punchDto.getPunchType();
        LocalDateTime now = LocalDateTime.now();
        LocalDate today = now.toLocalDate();

        // 排他制御: 同時打刻の競合を防止する悲観的ロック
        attendanceMapper.lockStoreStaffForUpdate(storeStaffId);

        AttendanceDto active = attendanceMapper.findActiveAttendance(storeStaffId);

        switch (punchType) {
            case "CLOCK_IN":
                return handleClockIn(punchDto, today, now, active);

            case "BREAK_START":
                return handleBreakStart(now, active);

            case "BREAK_END":
                return handleBreakEnd(now, active);

            case "CLOCK_OUT":
                return handleClockOut(now, active);

            default:
                throw new IllegalArgumentException("無効な打刻タイプです: " + punchType);
        }
    }

    private AttendanceDto handleClockIn(AttendancePunchDto punchDto, LocalDate today, LocalDateTime now, AttendanceDto active) {
        if (active != null) {
            throw new IllegalStateException("既に出勤中の打刻が存在します。");
        }

        // 該当日の給与が既に確定済（CONFIRMED / PAID）の場合は新規打刻を遮断
        if (attendanceMapper.isPayrollConfirmedForDate(punchDto.getStoreStaffId(), today)) {
            throw new IllegalStateException("対象期間の給与は既に確定済みのため、新規打刻はできません。");
        }

        Long shiftId = attendanceMapper.findConfirmedShiftIdForDate(punchDto.getStoreStaffId(), today);

        // 店舗ID補正 (DTO未指定時は店舗所属マスタから補完)
        Long storeId = punchDto.getStoreId();
        if (storeId == null) {
            storeId = attendanceMapper.findStoreIdByStoreStaffId(punchDto.getStoreStaffId());
            if (storeId == null) {
                throw new IllegalStateException("所属店舗情報が存在しないため打刻できません。(storeStaffId: " + punchDto.getStoreStaffId() + ")");
            }
        }

        AttendanceDto attendance = new AttendanceDto();
        attendance.setStoreId(storeId);
        attendance.setStoreStaffId(punchDto.getStoreStaffId());
        attendance.setShiftId(shiftId);
        attendance.setWorkDate(today);
        attendance.setClockInAt(now);
        attendance.setTotalBreakMinutes(0);
        attendance.setNetWorkMinutes(0);
        attendance.setNightWorkMinutes(0);
        attendance.setOvertimeMinutes(0);
        attendance.setClockInMethod("BUTTON");
        attendance.setAttendanceStatus("WORKING");

        attendanceMapper.insertAttendance(attendance);
        return attendance;
    }

    private AttendanceDto handleBreakStart(LocalDateTime now, AttendanceDto active) {
        if (active == null || !"WORKING".equals(active.getAttendanceStatus())) {
            throw new IllegalStateException("勤務中のみ休憩開始が可能です。");
        }

        active.setBreakStartAt(now);
        active.setAttendanceStatus("ON_BREAK");
        attendanceMapper.updateAttendance(active);
        return active;
    }

    private AttendanceDto handleBreakEnd(LocalDateTime now, AttendanceDto active) {
        if (active == null || !"ON_BREAK".equals(active.getAttendanceStatus())) {
            throw new IllegalStateException("休憩中のみ休憩終了が可能です。");
        }

        LocalDateTime breakStart = active.getBreakStartAt();
        int breakDuration = Math.toIntExact(Duration.between(breakStart, now).toMinutes());

        AttendanceBreakDto breakDto = new AttendanceBreakDto(breakStart, now, breakDuration);
        breakDto.setAttendanceId(active.getAttendanceId());
        attendanceMapper.insertBreak(breakDto);

        active.setBreakStartAt(null);
        active.setBreakEndAt(now);
        active.setTotalBreakMinutes(active.getTotalBreakMinutes() + breakDuration);
        active.setAttendanceStatus("WORKING");
        attendanceMapper.updateAttendance(active);
        return active;
    }

    private AttendanceDto handleClockOut(LocalDateTime now, AttendanceDto active) {
        if (active == null || !"WORKING".equals(active.getAttendanceStatus())) {
            throw new IllegalStateException("勤務中のみ退勤打刻が可能です。(休憩中の場合は先に休憩を終了してください)");
        }

        // 休憩区間一覧の取得
        List<AttendanceBreakDto> breakEntities = attendanceMapper.findBreaksByAttendanceId(active.getAttendanceId());
        List<AttendanceTimeCalculator.BreakInterval> breakIntervals = new ArrayList<>();
        for (AttendanceBreakDto b : breakEntities) {
            breakIntervals.add(new AttendanceTimeCalculator.BreakInterval(b.getBreakStartAt(), b.getBreakEndAt()));
        }

        // 時間計算機（分単位精密計算）呼び出し
        AttendanceTimeCalculator.CalculationResult calc = AttendanceTimeCalculator.calculate(
                active.getClockInAt(),
                now,
                breakIntervals
        );

        active.setClockOutAt(now);
        active.setTotalBreakMinutes(calc.totalBreakMinutes());
        active.setNetWorkMinutes(calc.netWorkMinutes());
        active.setNightWorkMinutes(calc.nightWorkMinutes());
        active.setOvertimeMinutes(calc.overtimeMinutes());
        active.setAttendanceStatus("COMPLETED");

        attendanceMapper.updateAttendance(active);
        return active;
    }

    public AttendanceDto getActiveAttendance(Long storeStaffId) {
        return attendanceMapper.findActiveAttendance(storeStaffId);
    }

    /**
     * 勤怠修正申請の登録（スタッフ申請）
     */
    @Transactional
    public Long requestModification(AttendanceModRequestDto requestDto) {
        Long storeStaffId = requestDto.getStoreStaffId();
        LocalDate workDate = requestDto.getWorkDate();

        // 排他制御: 悲観的ロックの確保
        attendanceMapper.lockStoreStaffForUpdate(storeStaffId);

        // 該当勤務日の給与が確定済の場合は修正申請を遮断
        if (attendanceMapper.isPayrollConfirmedForDate(storeStaffId, workDate)) {
            throw new IllegalStateException("指定された勤務日 (" + workDate + ") は既に給与が確定されているため、修正申請できません。");
        }

        // 出勤日と勤務基準日の一致検証
        if (!requestDto.getRequestedClockInAt().toLocalDate().equals(workDate)) {
            throw new IllegalArgumentException("出勤時刻の日付は勤務基準日と一致している必要があります。");
        }
        if (requestDto.getRequestedClockOutAt().isBefore(requestDto.getRequestedClockInAt())) {
            throw new IllegalArgumentException("退勤時刻は出勤時刻より後である必要があります。");
        }

        // 既存勤怠が存在する場合は元のスナップショットを取得
        AttendanceDto original = null;
        if (requestDto.getAttendanceId() != null) {
            original = attendanceMapper.findById(requestDto.getAttendanceId());
            if (original != null) {
                requestDto.setBeforeClockInAt(original.getClockInAt());
                requestDto.setBeforeClockOutAt(original.getClockOutAt());
                requestDto.setBeforeBreakMinutes(original.getTotalBreakMinutes());
                requestDto.setStoreId(original.getStoreId());
            }
        }

        if (requestDto.getStoreId() == null) {
            Long storeId = attendanceMapper.findStoreIdByStoreStaffId(storeStaffId);
            if (storeId == null) {
                throw new IllegalStateException("所属店舗情報が存在しないため修正申請できません。(storeStaffId: " + storeStaffId + ")");
            }
            requestDto.setStoreId(storeId);
        }

        // 申請合計休憩時間の計算
        int totalBreakMin = (requestDto.getRequestedBreakMinutes() != null) ? requestDto.getRequestedBreakMinutes() : 0;
        if (requestDto.getRequestedBreaks() != null && !requestDto.getRequestedBreaks().isEmpty()) {
            int sumBreak = 0;
            for (AttendanceModRequestDto.BreakTimeDto b : requestDto.getRequestedBreaks()) {
                sumBreak += (int) Duration.between(b.getStartAt(), b.getEndAt()).toMinutes();
            }
            totalBreakMin = sumBreak;
        }
        requestDto.setRequestedBreakMinutes(totalBreakMin);

        // 1. 修正申請レコード登録
        attendanceMapper.insertModRequest(requestDto);
        Long modRequestId = requestDto.getModRequestId();

        // 2. 修正前休憩区間スナップショットの保存
        if (original != null) {
            List<AttendanceBreakDto> beforeBreaks = attendanceMapper.findBreaksByAttendanceId(original.getAttendanceId());
            for (AttendanceBreakDto b : beforeBreaks) {
                attendanceMapper.insertModBreak(modRequestId, "BEFORE", b.getBreakStartAt(), b.getBreakEndAt(), b.getDurationMinutes());
            }
        }

        // 3. 修正希望休憩区間スナップショットの保存
        if (requestDto.getRequestedBreaks() != null) {
            for (AttendanceModRequestDto.BreakTimeDto b : requestDto.getRequestedBreaks()) {
                int dur = (int) Duration.between(b.getStartAt(), b.getEndAt()).toMinutes();
                attendanceMapper.insertModBreak(modRequestId, "REQUESTED", b.getStartAt(), b.getEndAt(), dur);
            }
        }

        return modRequestId;
    }

    /**
     * 店舗別勤怠修正申請一覧取得
     */
    public List<AttendanceModResponseDto> getModRequests(Long storeId, String status) {
        return attendanceMapper.findModRequestsByStoreId(storeId, status);
    }

    /**
     * 店長による勤怠修正申請の承認または却下処理
     */
    @Transactional
    public void reviewModification(Long modRequestId, Long reviewerId, AttendanceModReviewDto reviewDto) {
        AttendanceModResponseDto req = attendanceMapper.findModRequestById(modRequestId);
        if (req == null) {
            throw new IllegalArgumentException("存在しない修正申請です。(ID: " + modRequestId + ")");
        }

        if (!"PENDING".equals(req.getApprovalStatus())) {
            throw new IllegalStateException("既に処理された申請です。(状態: " + req.getApprovalStatus() + ")");
        }

        // 排他制御: 悲観的ロックの確保
        attendanceMapper.lockStoreStaffForUpdate(req.getStoreStaffId());

        // 該当勤務日の給与が確定済の場合は承認不可
        if (attendanceMapper.isPayrollConfirmedForDate(req.getStoreStaffId(), req.getWorkDate())) {
            throw new IllegalStateException("指定された勤務日は既に給与が確定されているため、承認できません。");
        }

        LocalDateTime now = LocalDateTime.now();

        if ("REJECT".equalsIgnoreCase(reviewDto.getAction())) {
            attendanceMapper.updateModRequestReview(modRequestId, "REJECTED", reviewerId, reviewDto.getRejectReason(), now);
            return;
        }

        // 承認（APPROVE）処理
        List<AttendanceBreakDto> reqBreaks = attendanceMapper.findModBreaksByRequestId(modRequestId, "REQUESTED");
        List<AttendanceTimeCalculator.BreakInterval> breakIntervals = new ArrayList<>();
        int totalBreakMin = 0;

        for (AttendanceBreakDto b : reqBreaks) {
            breakIntervals.add(new AttendanceTimeCalculator.BreakInterval(b.getBreakStartAt(), b.getBreakEndAt()));
            totalBreakMin += b.getDurationMinutes();
        }

        if (totalBreakMin == 0 && req.getRequestedBreakMinutes() != null && req.getRequestedBreakMinutes() > 0) {
            totalBreakMin = req.getRequestedBreakMinutes();
        }

        // 分単位精密勤怠計算
        AttendanceTimeCalculator.CalculationResult calc = AttendanceTimeCalculator.calculate(
                req.getRequestedClockInAt(),
                req.getRequestedClockOutAt(),
                breakIntervals
        );

        if (req.getAttendanceId() != null) {
            // 既存勤怠の更新
            AttendanceDto att = attendanceMapper.findById(req.getAttendanceId());
            if (att != null) {
                att.setClockInAt(req.getRequestedClockInAt());
                att.setClockOutAt(req.getRequestedClockOutAt());
                att.setTotalBreakMinutes(Math.max(totalBreakMin, calc.totalBreakMinutes()));
                att.setNetWorkMinutes(calc.netWorkMinutes());
                att.setNightWorkMinutes(calc.nightWorkMinutes());
                att.setOvertimeMinutes(calc.overtimeMinutes());
                att.setAttendanceStatus("COMPLETED");
                attendanceMapper.updateAttendance(att);

                // 休憩テーブルの置換
                attendanceMapper.deleteBreaksByAttendanceId(att.getAttendanceId());
                for (AttendanceBreakDto b : reqBreaks) {
                    b.setAttendanceId(att.getAttendanceId());
                    attendanceMapper.insertBreak(b);
                }
            }
        } else {
            // 完全打刻漏れによる事後新規勤怠生成
            AttendanceDto newAtt = new AttendanceDto();
            newAtt.setStoreId(req.getStoreId());
            newAtt.setStoreStaffId(req.getStoreStaffId());
            newAtt.setWorkDate(req.getWorkDate());
            newAtt.setClockInAt(req.getRequestedClockInAt());
            newAtt.setClockOutAt(req.getRequestedClockOutAt());
            newAtt.setTotalBreakMinutes(Math.max(totalBreakMin, calc.totalBreakMinutes()));
            newAtt.setNetWorkMinutes(calc.netWorkMinutes());
            newAtt.setNightWorkMinutes(calc.nightWorkMinutes());
            newAtt.setOvertimeMinutes(calc.overtimeMinutes());
            newAtt.setClockInMethod("WEB_MOD");
            newAtt.setAttendanceStatus("COMPLETED");

            Long shiftId = attendanceMapper.findConfirmedShiftIdForDate(req.getStoreStaffId(), req.getWorkDate());
            newAtt.setShiftId(shiftId);

            attendanceMapper.insertAttendance(newAtt);

            for (AttendanceBreakDto b : reqBreaks) {
                b.setAttendanceId(newAtt.getAttendanceId());
                attendanceMapper.insertBreak(b);
            }
        }

        attendanceMapper.updateModRequestReview(modRequestId, "APPROVED", reviewerId, null, now);
    }
}
