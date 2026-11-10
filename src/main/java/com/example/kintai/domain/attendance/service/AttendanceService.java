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
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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

        // 分割勤務（中抜け）対応: 同一スタッフ・同日の先行完了実労働時間の秒精度累計を取得
        long priorWorkSeconds = attendanceMapper.sumPriorNetWorkSecondsOnDate(
                active.getStoreStaffId(),
                active.getWorkDate(),
                active.getAttendanceId(),
                now
        );

        // 時間計算機（秒精度精密計算・先行累計連動）呼び出し
        AttendanceTimeCalculator.CalculationResult calc = AttendanceTimeCalculator.calculate(
                active.getClockInAt(),
                now,
                breakIntervals,
                priorWorkSeconds
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
        if (requestDto == null) {
            throw new IllegalArgumentException("修正申請データが空です。");
        }
        Long storeStaffId = requestDto.getStoreStaffId();
        LocalDate workDate = requestDto.getWorkDate();

        if (storeStaffId == null || workDate == null) {
            throw new IllegalArgumentException("店舗スタッフIDおよび勤務基準日は必須です。");
        }
        if (requestDto.getRequestedClockInAt() == null || requestDto.getRequestedClockOutAt() == null) {
            throw new IllegalArgumentException("修正希望の出退勤日時は必須です。");
        }
        if (requestDto.getReason() == null || requestDto.getReason().trim().isEmpty()) {
            throw new IllegalArgumentException("修正申請理由は必須入力です。");
        }

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
        if (!requestDto.getRequestedClockOutAt().isAfter(requestDto.getRequestedClockInAt())) {
            throw new IllegalArgumentException("退勤時刻は出勤時刻より後である必要があります。");
        }

        // 既存勤務記録との重複検証（時間帯重複防止）
        int overlapCount = attendanceMapper.countOverlappingAttendances(
                storeStaffId,
                requestDto.getRequestedClockInAt(),
                requestDto.getRequestedClockOutAt(),
                requestDto.getAttendanceId()
        );
        if (overlapCount > 0) {
            throw new IllegalStateException("申請された勤務時間帯に重複する既存の勤務記録が存在します。");
        }

        // 休憩区間の詳細検証
        if (requestDto.getRequestedBreaks() != null && !requestDto.getRequestedBreaks().isEmpty()) {
            List<AttendanceModRequestDto.BreakTimeDto> breaks = new ArrayList<>(requestDto.getRequestedBreaks());
            for (AttendanceModRequestDto.BreakTimeDto b : breaks) {
                if (b.getStartAt() == null || b.getEndAt() == null || !b.getEndAt().isAfter(b.getStartAt())) {
                    throw new IllegalArgumentException("休憩終了時刻は休憩開始時刻より後である必要があります。");
                }
                if (b.getStartAt().isBefore(requestDto.getRequestedClockInAt()) || b.getEndAt().isAfter(requestDto.getRequestedClockOutAt())) {
                    throw new IllegalArgumentException("休憩時間は勤務時間（出退勤）の範囲内である必要があります。");
                }
            }
            // 休憩区間同士の重複チェック
            breaks.sort(Comparator.comparing(AttendanceModRequestDto.BreakTimeDto::getStartAt));
            for (int i = 0; i < breaks.size() - 1; i++) {
                if (breaks.get(i).getEndAt().isAfter(breaks.get(i + 1).getStartAt())) {
                    throw new IllegalArgumentException("休憩時間同士が重複しています。");
                }
            }
        }

        // 既存勤怠が存在する場合は元のスナップショットを取得し、状態をMOD_REQUESTEDに遷移
        AttendanceDto original = null;
        if (requestDto.getAttendanceId() != null) {
            original = attendanceMapper.findById(requestDto.getAttendanceId());
            if (original == null) {
                throw new IllegalArgumentException("対象となる勤怠記録が存在しません。(ID: " + requestDto.getAttendanceId() + ")");
            }
            if (!original.getStoreStaffId().equals(storeStaffId)) {
                throw new IllegalArgumentException("他スタッフの勤怠記録に対して修正申請を行うことはできません。");
            }
            if ("MOD_REQUESTED".equals(original.getAttendanceStatus())) {
                throw new IllegalStateException("該当の勤怠は既に修正申請が行われており、承認待ちです。");
            }

            requestDto.setBeforeClockInAt(original.getClockInAt());
            requestDto.setBeforeClockOutAt(original.getClockOutAt());
            requestDto.setBeforeBreakMinutes(original.getTotalBreakMinutes());
            requestDto.setStoreId(original.getStoreId());
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

        // 2. 修正前休憩区間スナップショットの保存および既存勤怠の状態保留(MOD_REQUESTED)
        if (original != null) {
            List<AttendanceBreakDto> beforeBreaks = attendanceMapper.findBreaksByAttendanceId(original.getAttendanceId());
            for (AttendanceBreakDto b : beforeBreaks) {
                attendanceMapper.insertModBreak(modRequestId, "BEFORE", b.getBreakStartAt(), b.getBreakEndAt(), b.getDurationMinutes());
            }
            attendanceMapper.updateAttendanceStatus(original.getAttendanceId(), "MOD_REQUESTED");
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
     * 店舗別勤怠修正申請一覧取得（バッチ一括取得によりN+1クエリ問題を解消）
     */
    public List<AttendanceModResponseDto> getModRequests(Long storeId, String status) {
        List<AttendanceModResponseDto> list = attendanceMapper.findModRequestsByStoreId(storeId, status);
        if (list == null || list.isEmpty()) {
            return Collections.emptyList();
        }

        // 全申請IDを抽出して休憩区間を一括取得
        List<Long> requestIds = list.stream().map(AttendanceModResponseDto::getModRequestId).toList();
        List<AttendanceBreakDto> allBreaks = attendanceMapper.findModBreaksByRequestIds(requestIds);

        Map<Long, List<AttendanceBreakDto>> beforeMap = new HashMap<>();
        Map<Long, List<AttendanceBreakDto>> reqMap = new HashMap<>();

        if (allBreaks != null) {
            for (AttendanceBreakDto b : allBreaks) {
                Long reqId = b.getAttendanceId(); // mod_request_idをマッピング
                if ("BEFORE".equalsIgnoreCase(b.getBreakSetType())) {
                    beforeMap.computeIfAbsent(reqId, k -> new ArrayList<>()).add(b);
                } else if ("REQUESTED".equalsIgnoreCase(b.getBreakSetType())) {
                    reqMap.computeIfAbsent(reqId, k -> new ArrayList<>()).add(b);
                }
            }
        }

        for (AttendanceModResponseDto item : list) {
            item.setBeforeBreaks(beforeMap.getOrDefault(item.getModRequestId(), Collections.emptyList()));
            item.setRequestedBreaks(reqMap.getOrDefault(item.getModRequestId(), Collections.emptyList()));
        }

        return list;
    }

    /**
     * 店長による勤怠修正申請の承認または却下処理
     */
    @Transactional
    public void reviewModification(Long modRequestId, Long reviewerId, AttendanceModReviewDto reviewDto) {
        if (reviewDto == null || reviewDto.getAction() == null) {
            throw new IllegalArgumentException("審査アクションは必須です。");
        }

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

        // 却下（REJECT）処理
        if ("REJECT".equalsIgnoreCase(reviewDto.getAction())) {
            int affected = attendanceMapper.updateModRequestReview(modRequestId, "REJECTED", reviewerId, reviewDto.getRejectReason(), now);
            if (affected != 1) {
                throw new IllegalStateException("他の管理者によって既に申請が処理されたか、状態が変更されています。(CAS競合)");
            }
            if (req.getAttendanceId() != null) {
                // 既存勤怠の保留状態(MOD_REQUESTED)を通常完了状態(COMPLETED)に復帰
                attendanceMapper.updateAttendanceStatus(req.getAttendanceId(), "COMPLETED");
            }
            return;
        }

        // 承認（APPROVE）処理
        // 既存勤務記録との重複再検証
        int overlapCount = attendanceMapper.countOverlappingAttendances(
                req.getStoreStaffId(),
                req.getRequestedClockInAt(),
                req.getRequestedClockOutAt(),
                req.getAttendanceId()
        );
        if (overlapCount > 0) {
            throw new IllegalStateException("承認対象の勤務時間は、既存の他の勤務記録と重複しています。");
        }

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

        // 分割勤務（中抜け）対応: 同一スタッフ・同日の先行完了実労働時間の秒精度累計を取得
        long priorWorkSeconds = attendanceMapper.sumPriorNetWorkSecondsOnDate(
                req.getStoreStaffId(),
                req.getWorkDate(),
                req.getAttendanceId(),
                req.getRequestedClockInAt()
        );

        // 秒精度精密勤怠計算（労働基準法第37条準拠・先行累計連動）
        AttendanceTimeCalculator.CalculationResult calc = AttendanceTimeCalculator.calculate(
                req.getRequestedClockInAt(),
                req.getRequestedClockOutAt(),
                breakIntervals,
                priorWorkSeconds
        );

        if (req.getAttendanceId() != null) {
            // 既存勤怠の更新（事後承認マーク・COMPLETED復帰・再計算値反映）
            AttendanceDto att = attendanceMapper.findById(req.getAttendanceId());
            if (att != null) {
                att.setClockInAt(req.getRequestedClockInAt());
                att.setClockOutAt(req.getRequestedClockOutAt());
                att.setTotalBreakMinutes(Math.max(totalBreakMin, calc.totalBreakMinutes()));
                att.setNetWorkMinutes(calc.netWorkMinutes());
                att.setNightWorkMinutes(calc.nightWorkMinutes());
                att.setOvertimeMinutes(calc.overtimeMinutes());
                att.setClockInMethod("MANUAL_APPROVED");
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
            newAtt.setClockInMethod("MANUAL_APPROVED");
            newAtt.setAttendanceStatus("COMPLETED");

            Long shiftId = attendanceMapper.findConfirmedShiftIdForDate(req.getStoreStaffId(), req.getWorkDate());
            newAtt.setShiftId(shiftId);

            attendanceMapper.insertAttendance(newAtt);

            for (AttendanceBreakDto b : reqBreaks) {
                b.setAttendanceId(newAtt.getAttendanceId());
                attendanceMapper.insertBreak(b);
            }

            // 新規作成された勤怠IDを申請履歴に紐付け
            attendanceMapper.updateModRequestAttendanceId(modRequestId, newAtt.getAttendanceId());
        }

        int affected = attendanceMapper.updateModRequestReview(modRequestId, "APPROVED", reviewerId, null, now);
        if (affected != 1) {
            throw new IllegalStateException("他の管理者によって既に申請が処理されたか、状態が変更されています。(CAS競合)");
        }
    }
}
