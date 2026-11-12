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
package com.example.kintai.domain.attendance.controller;

import com.example.kintai.domain.attendance.dto.*;
import com.example.kintai.domain.attendance.service.AttendanceService;
import com.example.kintai.domain.store.service.StoreAuthorizationService;
import com.example.kintai.global.common.ApiResponse;
import com.example.kintai.global.security.CustomUserDetails;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 勤怠管理 REST API コントローラー
 */
@RestController
@RequestMapping("/api/attendance")
public class AttendanceApiController {

    private final AttendanceService attendanceService;
    private final StoreAuthorizationService storeAuthorizationService;

    public AttendanceApiController(AttendanceService attendanceService, StoreAuthorizationService storeAuthorizationService) {
        this.attendanceService = attendanceService;
        this.storeAuthorizationService = storeAuthorizationService;
    }

    /**
     * リアルタイム出退勤・休憩打刻 API
     * 認証済みスタッフの storeStaffId を優先適用し IDOR（なりすまし打刻）を防止。
     * オーナー（OWNER）の代理打刻時は、対象スタッフが自身の所有店舗に所属しているかを検証。
     */
    @PostMapping("/punch")
    public ResponseEntity<ApiResponse<AttendanceDto>> punch(
            @RequestBody AttendancePunchDto punchDto,
            @AuthenticationPrincipal CustomUserDetails userDetails
    ) {
        if (userDetails != null) {
            if (userDetails.getStoreStaffId() != null) {
                // 一般スタッフ本人打刻：セッションの storeStaffId で強制上書き
                punchDto.setStoreStaffId(userDetails.getStoreStaffId());
                if (userDetails.getStoreId() != null) {
                    punchDto.setStoreId(userDetails.getStoreId());
                }
            } else if (punchDto.getStoreStaffId() != null) {
                // オーナー（OWNER）代理打刻：対象スタッフがオーナー店舗の所属員であるかを厳格検証し、実店舗IDを強制バインド
                Long validatedStoreId = storeAuthorizationService.validateStaffStoreOwnership(punchDto.getStoreStaffId(), userDetails.getMemberId());
                punchDto.setStoreId(validatedStoreId);
            }
        }

        AttendanceDto result = attendanceService.punch(punchDto);
        return ResponseEntity.ok(ApiResponse.ok("打刻が完了しました。", result));
    }

    /**
     * 現在進行中の勤務実績取得 API
     */
    @GetMapping("/active")
    public ResponseEntity<ApiResponse<AttendanceDto>> getActiveAttendance(
            @RequestParam(name = "storeStaffId", required = false) Long storeStaffId,
            @AuthenticationPrincipal CustomUserDetails userDetails
    ) {
        if (userDetails == null) {
            throw new AccessDeniedException("認証が必要です。");
        }

        Long targetStaffId;
        if (userDetails.getStoreStaffId() != null) {
            targetStaffId = userDetails.getStoreStaffId();
        } else {
            if (storeStaffId == null) {
                throw new IllegalArgumentException("スタッフIDが指定されていません。");
            }
            storeAuthorizationService.validateStaffStoreOwnership(storeStaffId, userDetails.getMemberId());
            targetStaffId = storeStaffId;
        }

        AttendanceDto active = attendanceService.getActiveAttendance(targetStaffId);
        return ResponseEntity.ok(ApiResponse.ok(active));
    }

    /**
     * 勤怠修正申請登録 API（スタッフ申請）
     * スタッフ本人の申請時はセッションの storeStaffId を強制適用。
     * オーナー等による代理申請時は対象スタッフの店舗所属権限を検証し IDOR を完全遮断。
     */
    @PostMapping("/mod-request")
    public ResponseEntity<ApiResponse<Long>> requestModification(
            @RequestBody AttendanceModRequestDto requestDto,
            @AuthenticationPrincipal CustomUserDetails userDetails
    ) {
        if (userDetails == null) {
            throw new AccessDeniedException("勤怠修正申請には認証が必要です。");
        }

        if (userDetails.getStoreStaffId() != null) {
            requestDto.setStoreStaffId(userDetails.getStoreStaffId());
            if (userDetails.getStoreId() != null) {
                requestDto.setStoreId(userDetails.getStoreId());
            }
        } else {
            if (requestDto.getStoreStaffId() == null) {
                throw new IllegalArgumentException("対象スタッフIDが指定されていません。");
            }
            Long validatedStoreId = storeAuthorizationService.validateStaffStoreOwnership(requestDto.getStoreStaffId(), userDetails.getMemberId());
            requestDto.setStoreId(validatedStoreId);
        }

        Long modRequestId = attendanceService.requestModification(requestDto);
        return ResponseEntity.ok(ApiResponse.ok("勤怠修正申請を受け付けました。", modRequestId));
    }

    /**
     * 勤怠修正申請一覧取得 API（店長・管理者用）
     * ハードコード店舗IDを排除し、店舗オーナーシップを厳格検証
     */
    @GetMapping("/mod-requests")
    public ResponseEntity<ApiResponse<List<AttendanceModResponseDto>>> getModRequests(
            @RequestParam(name = "storeId", required = false) Long storeId,
            @RequestParam(name = "status", required = false) String status,
            @AuthenticationPrincipal CustomUserDetails userDetails
    ) {
        if (userDetails == null) {
            throw new AccessDeniedException("修正申請一覧の取得には認証が必要です。");
        }

        Long targetStoreId;
        if (userDetails.getStoreId() != null) {
            targetStoreId = userDetails.getStoreId();
        } else if (storeId != null) {
            storeAuthorizationService.validateStoreOwnership(storeId, userDetails.getMemberId());
            targetStoreId = storeId;
        } else {
            throw new IllegalArgumentException("店舗IDが指定されていません。");
        }

        List<AttendanceModResponseDto> list = attendanceService.getModRequests(targetStoreId, status);
        return ResponseEntity.ok(ApiResponse.ok(list));
    }

    /**
     * 勤怠修正申請の承認・却下審査 API（店長・オーナー用）
     * 審査対象の申請がオーナー所有店舗のリソースであることを検証し、不正審査を完全遮断
     */
    @PostMapping("/mod-requests/{modRequestId}/review")
    public ResponseEntity<ApiResponse<Void>> reviewModification(
            @PathVariable("modRequestId") Long modRequestId,
            @RequestBody AttendanceModReviewDto reviewDto,
            @AuthenticationPrincipal CustomUserDetails userDetails
    ) {
        if (userDetails == null) {
            throw new AccessDeniedException("審査処理には認証が必要です。");
        }

        storeAuthorizationService.validateModRequestStoreOwnership(modRequestId, userDetails.getMemberId());
        attendanceService.reviewModification(modRequestId, userDetails.getMemberId(), reviewDto);
        return ResponseEntity.ok(ApiResponse.ok("審査処理が完了しました。", null));
    }
}
<?xml version="1.0" encoding="UTF-8" ?>
<!DOCTYPE mapper PUBLIC "-//mybatis.org//DTD Mapper 3.0//EN"
        "http://mybatis.org/dtd/mybatis-3-mapper.dtd">

<mapper namespace="com.example.kintai.domain.attendance.mapper.AttendanceMapper">

    <select id="findActiveAttendance" resultType="com.example.kintai.domain.attendance.dto.AttendanceDto">
        SELECT
            attendance_id,
            store_id,
            store_staff_id,
            shift_id,
            work_date,
            clock_in_at,
            clock_out_at,
            break_start_at,
            break_end_at,
            total_break_minutes,
            net_work_minutes,
            night_work_minutes,
            overtime_minutes,
            clock_in_method,
            attendance_status
        FROM attendance
        WHERE store_staff_id = #{storeStaffId}
          AND attendance_status IN ('WORKING', 'ON_BREAK')
        ORDER BY attendance_id DESC
        LIMIT 1
    </select>

    <select id="findById" resultType="com.example.kintai.domain.attendance.dto.AttendanceDto">
        SELECT
            attendance_id,
            store_id,
            store_staff_id,
            shift_id,
            work_date,
            clock_in_at,
            clock_out_at,
            break_start_at,
            break_end_at,
            total_break_minutes,
            net_work_minutes,
            night_work_minutes,
            overtime_minutes,
            clock_in_method,
            attendance_status
        FROM attendance
        WHERE attendance_id = #{attendanceId}
    </select>

    <insert id="insertAttendance" useGeneratedKeys="true" keyProperty="attendanceId">
        INSERT INTO attendance (
            store_id,
            store_staff_id,
            shift_id,
            work_date,
            clock_in_at,
            clock_out_at,
            break_start_at,
            break_end_at,
            total_break_minutes,
            net_work_minutes,
            night_work_minutes,
            overtime_minutes,
            clock_in_method,
            attendance_status
        ) VALUES (
            #{storeId},
            #{storeStaffId},
            #{shiftId},
            #{workDate},
            #{clockInAt},
            #{clockOutAt},
            #{breakStartAt},
            #{breakEndAt},
            #{totalBreakMinutes},
            #{netWorkMinutes},
            #{nightWorkMinutes},
            #{overtimeMinutes},
            #{clockInMethod},
            #{attendanceStatus}
        )
    </insert>

    <update id="updateAttendance">
        UPDATE attendance
        <set>
            <if test="clockInAt != null">clock_in_at = #{clockInAt},</if>
            <if test="clockOutAt != null">clock_out_at = #{clockOutAt},</if>
            <if test="breakStartAt != null">break_start_at = #{breakStartAt},</if>
            <if test="breakEndAt != null">break_end_at = #{breakEndAt},</if>
            <if test="totalBreakMinutes != null">total_break_minutes = #{totalBreakMinutes},</if>
            <if test="netWorkMinutes != null">net_work_minutes = #{netWorkMinutes},</if>
            <if test="nightWorkMinutes != null">night_work_minutes = #{nightWorkMinutes},</if>
            <if test="overtimeMinutes != null">overtime_minutes = #{overtimeMinutes},</if>
            <if test="clockInMethod != null">clock_in_method = #{clockInMethod},</if>
            <if test="attendanceStatus != null">attendance_status = #{attendanceStatus},</if>
            updated_at = CURRENT_TIMESTAMP
        </set>
        WHERE attendance_id = #{attendanceId}
    </update>

    <insert id="insertBreak" useGeneratedKeys="true" keyProperty="attendanceBreakId">
        INSERT INTO attendance_break (
            attendance_id,
            break_start_at,
            break_end_at,
            duration_minutes
        ) VALUES (
            #{attendanceId},
            #{breakStartAt},
            #{breakEndAt},
            #{durationMinutes}
        )
    </insert>

    <select id="findBreaksByAttendanceId" resultType="com.example.kintai.domain.attendance.dto.AttendanceBreakDto">
        SELECT
            attendance_break_id,
            attendance_id,
            break_start_at,
            break_end_at,
            duration_minutes
        FROM attendance_break
        WHERE attendance_id = #{attendanceId}
        ORDER BY break_start_at ASC
    </select>

    <delete id="deleteBreaksByAttendanceId">
        DELETE FROM attendance_break
        WHERE attendance_id = #{attendanceId}
    </delete>

    <select id="isPayrollConfirmedForDate" resultType="boolean">
        SELECT COUNT(*) &gt; 0
        FROM payroll
        WHERE store_staff_id = #{storeStaffId}
          AND #{workDate} &gt;= range_start_date
          AND #{workDate} &lt;= range_end_date
          AND payroll_status IN ('CONFIRMED', 'PAID')
    </select>

    <select id="lockStoreStaffForUpdate">
        SELECT store_staff_id
        FROM store_staff
        WHERE store_staff_id = #{storeStaffId}
        FOR UPDATE
    </select>

    <select id="findStoreIdByStoreStaffId" resultType="java.lang.Long">
        SELECT store_id
        FROM store_staff
        WHERE store_staff_id = #{storeStaffId}
        LIMIT 1
    </select>

    <select id="findConfirmedShiftIdForDate" resultType="java.lang.Long">
        SELECT shift_id
        FROM shift_schedule
        WHERE store_staff_id = #{storeStaffId}
          AND work_date = #{workDate}
          AND status = 'CONFIRMED'
        LIMIT 1
    </select>

    <select id="findCompletedAttendancesForStaffAndPeriod" resultType="com.example.kintai.domain.attendance.dto.AttendanceDto">
        SELECT
            attendance_id,
            store_id,
            store_staff_id,
            shift_id,
            work_date,
            clock_in_at,
            clock_out_at,
            total_break_minutes,
            net_work_minutes,
            night_work_minutes,
            overtime_minutes,
            clock_in_method,
            attendance_status
        FROM attendance
        WHERE store_staff_id = #{storeStaffId}
          AND work_date &gt;= #{startDate}
          AND work_date &lt;= #{endDate}
          AND attendance_status = 'COMPLETED'
        ORDER BY work_date ASC
    </select>

    <!-- 勤怠修正申請の新規登録 -->
    <insert id="insertModRequest" useGeneratedKeys="true" keyProperty="modRequestId">
        INSERT INTO attendance_mod_request (
            attendance_id,
            store_id,
            store_staff_id,
            work_date,
            before_clock_in_at,
            before_clock_out_at,
            before_break_minutes,
            requested_clock_in_at,
            requested_clock_out_at,
            requested_break_minutes,
            reason,
            approval_status
        ) VALUES (
            #{attendanceId},
            #{storeId},
            #{storeStaffId},
            #{workDate},
            #{beforeClockInAt},
            #{beforeClockOutAt},
            #{beforeBreakMinutes},
            #{requestedClockInAt},
            #{requestedClockOutAt},
            #{requestedBreakMinutes},
            #{reason},
            'PENDING'
        )
    </insert>

    <!-- 勤怠修正休憩区間の登録 -->
    <insert id="insertModBreak">
        INSERT INTO attendance_mod_break (
            mod_request_id,
            break_set_type,
            break_start_at,
            break_end_at,
            duration_minutes
        ) VALUES (
            #{modRequestId},
            #{breakSetType},
            #{startAt},
            #{endAt},
            #{duration}
        )
    </insert>

    <!-- 店舗別勤怠修正申請一覧取得 -->
    <select id="findModRequestsByStoreId" resultType="com.example.kintai.domain.attendance.dto.AttendanceModResponseDto">
        SELECT
            r.mod_request_id,
            r.attendance_id,
            r.store_id,
            r.store_staff_id,
            m.name AS staff_name,
            r.work_date,
            r.before_clock_in_at,
            r.before_clock_out_at,
            r.before_break_minutes,
            r.requested_clock_in_at,
            r.requested_clock_out_at,
            r.requested_break_minutes,
            r.reason,
            r.approval_status,
            r.reject_reason,
            r.created_at
        FROM attendance_mod_request r
        JOIN store_staff ss ON r.store_staff_id = ss.store_staff_id
        JOIN member m ON ss.member_id = m.member_id
        WHERE r.store_id = #{storeId}
        <if test="status != null and status != ''">
            AND r.approval_status = #{status}
        </if>
        ORDER BY r.mod_request_id DESC
    </select>

    <!-- 修正申請単件取得 -->
    <select id="findModRequestById" resultType="com.example.kintai.domain.attendance.dto.AttendanceModResponseDto">
        SELECT
            r.mod_request_id,
            r.attendance_id,
            r.store_id,
            r.store_staff_id,
            m.name AS staff_name,
            r.work_date,
            r.before_clock_in_at,
            r.before_clock_out_at,
            r.before_break_minutes,
            r.requested_clock_in_at,
            r.requested_clock_out_at,
            r.requested_break_minutes,
            r.reason,
            r.approval_status,
            r.reject_reason,
            r.created_at
        FROM attendance_mod_request r
        JOIN store_staff ss ON r.store_staff_id = ss.store_staff_id
        JOIN member m ON ss.member_id = m.member_id
        WHERE r.mod_request_id = #{modRequestId}
    </select>

    <!-- 修正申請の休憩区間一覧取得 -->
    <select id="findModBreaksByRequestId" resultType="com.example.kintai.domain.attendance.dto.AttendanceBreakDto">
        SELECT
            mod_break_id AS attendance_break_id,
            mod_request_id AS attendance_id,
            break_set_type,
            break_start_at,
            break_end_at,
            duration_minutes
        FROM attendance_mod_break
        WHERE mod_request_id = #{modRequestId}
          AND break_set_type = #{breakSetType}
        ORDER BY break_start_at ASC
    </select>

    <!-- 複数修正申請の全休憩区間一括取得（N+1クエリ解消） -->
    <select id="findModBreaksByRequestIds" resultType="com.example.kintai.domain.attendance.dto.AttendanceBreakDto">
        SELECT
            mod_break_id AS attendance_break_id,
            mod_request_id AS attendance_id,
            break_set_type,
            break_start_at,
            break_end_at,
            duration_minutes
        FROM attendance_mod_break
        WHERE mod_request_id IN
        <foreach item="id" collection="requestIds" open="(" separator="," close=")">
            #{id}
        </foreach>
        ORDER BY break_start_at ASC
    </select>

    <!-- 修正申請の承認・却下状態更新 (CAS: PENDING状態の場合のみ1件更新) -->
    <update id="updateModRequestReview">
        UPDATE attendance_mod_request
        SET approval_status = #{status},
            reviewer_id = #{reviewerId},
            reject_reason = #{rejectReason},
            reviewed_at = #{reviewedAt},
            updated_at = CURRENT_TIMESTAMP
        WHERE mod_request_id = #{modRequestId}
          AND approval_status = 'PENDING'
    </update>

    <!-- 勤怠ステータスの単体更新 -->
    <update id="updateAttendanceStatus">
        UPDATE attendance
        SET attendance_status = #{attendanceStatus},
            updated_at = CURRENT_TIMESTAMP
        WHERE attendance_id = #{attendanceId}
    </update>

    <!-- 同一スタッフにおける勤務時間の重複件数をカウント（勤務時間重複防止） -->
    <select id="countOverlappingAttendances" resultType="int">
        SELECT COUNT(*)
        FROM attendance
        WHERE store_staff_id = #{storeStaffId}
          <if test="excludeAttendanceId != null">
              AND attendance_id != #{excludeAttendanceId}
          </if>
          AND clock_in_at &lt; #{clockOutAt}
          AND (clock_out_at IS NULL OR clock_out_at &gt; #{clockInAt})
    </select>

    <!-- 事後新規登録された勤怠IDを修正申請レコードに紐付け -->
    <update id="updateModRequestAttendanceId">
        UPDATE attendance_mod_request
        SET attendance_id = #{attendanceId},
            updated_at = CURRENT_TIMESTAMP
        WHERE mod_request_id = #{modRequestId}
    </update>

    <!-- 店舗および指定勤務日の全スタッフ勤怠実績取得（シフト・勤怠リアルタイム対照用） -->
    <select id="findAttendancesByStoreAndDate" resultType="com.example.kintai.domain.attendance.dto.AttendanceDto">
        SELECT
            attendance_id,
            store_id,
            store_staff_id,
            shift_id,
            work_date,
            clock_in_at,
            clock_out_at,
            break_start_at,
            break_end_at,
            total_break_minutes,
            net_work_minutes,
            night_work_minutes,
            overtime_minutes,
            clock_in_method,
            attendance_status
        FROM attendance
        WHERE store_id = #{storeId}
          AND work_date = #{workDate}
        ORDER BY clock_in_at ASC
    </select>

    <!-- スタッフ氏名取得 -->
    <select id="findStaffNameByStoreStaffId" resultType="java.lang.String">
        SELECT m.name
        FROM store_staff ss
        JOIN member m ON ss.member_id = m.member_id
        WHERE ss.store_staff_id = #{storeStaffId}
        LIMIT 1
    </select>

    <!-- 勤務日時点の契約時給取得 -->
    <select id="findHourlyWageForStaffOnDate" resultType="java.lang.Integer">
        SELECT ss.hourly_wage
        FROM store_staff ss
        WHERE ss.store_staff_id = #{storeStaffId}
        LIMIT 1
    </select>

    <!-- 同一スタッフ・同一勤務日の先行完了勤務の実労働時間合計（分）を取得（分割勤務中抜け対応） -->
    <select id="sumPriorNetWorkMinutesOnDate" resultType="java.lang.Integer">
        SELECT COALESCE(SUM(net_work_minutes), 0)
        FROM attendance
        WHERE store_staff_id = #{storeStaffId}
          AND work_date = #{workDate}
          AND attendance_status = 'COMPLETED'
          <if test="excludeAttendanceId != null">
              AND attendance_id != #{excludeAttendanceId}
          </if>
          <if test="beforeTime != null">
              AND clock_out_at &lt;= #{beforeTime}
          </if>
    </select>

    <!-- 同一スタッフ・同一勤務日の先行完了勤務の実労働時間合計（秒精度）を取得（分割勤務中抜け対応） -->
    <select id="sumPriorNetWorkSecondsOnDate" resultType="java.lang.Long">
        SELECT COALESCE(SUM(TIMESTAMPDIFF(SECOND, clock_in_at, clock_out_at) - (total_break_minutes * 60)), 0)
        FROM attendance
        WHERE store_staff_id = #{storeStaffId}
          AND work_date = #{workDate}
          AND attendance_status = 'COMPLETED'
          <if test="excludeAttendanceId != null">
              AND attendance_id != #{excludeAttendanceId}
          </if>
          <if test="beforeTime != null">
              AND clock_out_at &lt;= #{beforeTime}
          </if>
    </select>

</mapper>
package com.example.kintai.domain.attendance.service;

import com.example.kintai.domain.attendance.dto.AttendanceBreakDto;
import com.example.kintai.domain.attendance.dto.AttendanceDto;
import com.example.kintai.domain.attendance.dto.AttendanceModRequestDto;
import com.example.kintai.domain.attendance.dto.AttendanceModResponseDto;
import com.example.kintai.domain.attendance.dto.AttendanceModReviewDto;
import com.example.kintai.domain.attendance.mapper.AttendanceMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * AttendanceModWorkflowTest（勤怠修正申請・電子決裁ワークフロー単体テスト）
 * 悲観的ロック(排他制御)、勤務時間帯重複防御(R-03)、給与確定衝突防止、
 * 承認時の労働基準法第37条再計算および事後承認マーク付与を検証する。
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("AttendanceService 勤怠修正ワークフロー単体テスト")
class AttendanceModWorkflowTest {

    @Mock
    private AttendanceMapper attendanceMapper;

    @InjectMocks
    private AttendanceService attendanceService;

    @Nested
    @DisplayName("勤怠修正申請（スタッフ申請）の検証")
    class RequestModificationTest {

        @Test
        @DisplayName("勤務時間重複防御: 既存勤務記録と重複する場合はIllegalStateExceptionがスローされること")
        void requestModification_WhenOverlappingAttendanceExists_ShouldThrowException() {
            LocalDate workDate = LocalDate.of(2026, 11, 10);
            LocalDateTime clockIn = LocalDateTime.of(2026, 11, 10, 9, 0);
            LocalDateTime clockOut = LocalDateTime.of(2026, 11, 10, 18, 0);

            AttendanceModRequestDto dto = new AttendanceModRequestDto();
            dto.setStoreStaffId(10L);
            dto.setWorkDate(workDate);
            dto.setRequestedClockInAt(clockIn);
            dto.setRequestedClockOutAt(clockOut);
            dto.setReason("打刻忘れによる事後修正申請");

            when(attendanceMapper.isPayrollConfirmedForDate(10L, workDate)).thenReturn(false);
            // 重複件数 1件検知
            when(attendanceMapper.countOverlappingAttendances(10L, clockIn, clockOut, null)).thenReturn(1);

            assertThatThrownBy(() -> attendanceService.requestModification(dto))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("重複する既存の勤務記録が存在します");

            verify(attendanceMapper).lockStoreStaffForUpdate(10L);
            verify(attendanceMapper, never()).insertModRequest(any());
        }

        @Test
        @DisplayName("正常系: 既存勤怠に対する修正申請時、悲観的ロックが確保され状態がMOD_REQUESTEDに保留されること")
        void requestModification_WhenValid_ShouldLockAndSetModRequested() {
            LocalDate workDate = LocalDate.of(2026, 11, 10);
            LocalDateTime clockIn = LocalDateTime.of(2026, 11, 10, 9, 0);
            LocalDateTime clockOut = LocalDateTime.of(2026, 11, 10, 18, 0);

            AttendanceModRequestDto dto = new AttendanceModRequestDto();
            dto.setAttendanceId(100L);
            dto.setStoreStaffId(10L);
            dto.setWorkDate(workDate);
            dto.setRequestedClockInAt(clockIn);
            dto.setRequestedClockOutAt(clockOut);
            dto.setReason("打刻漏れの修正申請");

            AttendanceDto original = new AttendanceDto();
            original.setAttendanceId(100L);
            original.setStoreStaffId(10L);
            original.setStoreId(1L);
            original.setClockInAt(clockIn);
            original.setClockOutAt(clockOut);
            original.setTotalBreakMinutes(60);
            original.setAttendanceStatus("COMPLETED");

            when(attendanceMapper.isPayrollConfirmedForDate(10L, workDate)).thenReturn(false);
            when(attendanceMapper.countOverlappingAttendances(10L, clockIn, clockOut, 100L)).thenReturn(0);
            when(attendanceMapper.findById(100L)).thenReturn(original);
            when(attendanceMapper.findBreaksByAttendanceId(100L)).thenReturn(Collections.emptyList());

            attendanceService.requestModification(dto);

            // 排他制御・申請登録・状態保留(MOD_REQUESTED)の呼出検証
            verify(attendanceMapper).lockStoreStaffForUpdate(10L);
            verify(attendanceMapper).insertModRequest(dto);
            verify(attendanceMapper).updateAttendanceStatus(100L, "MOD_REQUESTED");
        }
    }

    @Nested
    @DisplayName("店長による審査（承認・却下）の検証")
    class ReviewModificationTest {

        @Test
        @DisplayName("承認処理: 承認時に労働法再計算が行われ、MANUAL_APPROVEDおよびCOMPLETEDが付与されること")
        void reviewModification_WhenApproved_ShouldUpdateAttendanceWithManualApproved() {
            Long modRequestId = 1L;
            Long reviewerId = 999L;
            LocalDate workDate = LocalDate.of(2026, 11, 10);
            LocalDateTime reqIn = LocalDateTime.of(2026, 11, 10, 13, 0);
            LocalDateTime reqOut = LocalDateTime.of(2026, 11, 10, 23, 0); // 10時間拘束

            AttendanceModResponseDto req = new AttendanceModResponseDto();
            req.setModRequestId(modRequestId);
            req.setAttendanceId(100L);
            req.setStoreStaffId(10L);
            req.setStoreId(1L);
            req.setWorkDate(workDate);
            req.setRequestedClockInAt(reqIn);
            req.setRequestedClockOutAt(reqOut);
            req.setRequestedBreakMinutes(60);
            req.setApprovalStatus("PENDING");

            AttendanceDto existing = new AttendanceDto();
            existing.setAttendanceId(100L);
            existing.setStoreStaffId(10L);
            existing.setAttendanceStatus("MOD_REQUESTED");

            AttendanceBreakDto breakDto = new AttendanceBreakDto();
            breakDto.setBreakStartAt(LocalDateTime.of(2026, 11, 10, 17, 0));
            breakDto.setBreakEndAt(LocalDateTime.of(2026, 11, 10, 18, 0));
            breakDto.setDurationMinutes(60);

            when(attendanceMapper.findModRequestById(modRequestId)).thenReturn(req);
            when(attendanceMapper.isPayrollConfirmedForDate(10L, workDate)).thenReturn(false);
            when(attendanceMapper.countOverlappingAttendances(10L, reqIn, reqOut, 100L)).thenReturn(0);
            when(attendanceMapper.findModBreaksByRequestId(modRequestId, "REQUESTED")).thenReturn(List.of(breakDto));
            when(attendanceMapper.findById(100L)).thenReturn(existing);
            when(attendanceMapper.updateModRequestReview(eq(modRequestId), eq("APPROVED"), eq(reviewerId), isNull(), any())).thenReturn(1);

            AttendanceModReviewDto reviewDto = new AttendanceModReviewDto();
            reviewDto.setAction("APPROVE");

            attendanceService.reviewModification(modRequestId, reviewerId, reviewDto);

            // 悲観的ロックおよび更新キャプチャ検証
            verify(attendanceMapper).lockStoreStaffForUpdate(10L);

            ArgumentCaptor<AttendanceDto> captor = ArgumentCaptor.forClass(AttendanceDto.class);
            verify(attendanceMapper).updateAttendance(captor.capture());

            AttendanceDto updated = captor.getValue();
            assertThat(updated.getClockInMethod()).isEqualTo("MANUAL_APPROVED");
            assertThat(updated.getAttendanceStatus()).isEqualTo("COMPLETED");
            assertThat(updated.getNetWorkMinutes()).isEqualTo(540); // 9時間 = 540分
            assertThat(updated.getNightWorkMinutes()).isEqualTo(60); // 22:00~23:00 = 60分
            assertThat(updated.getOvertimeMinutes()).isEqualTo(60); // 8時間超過 = 60分

            verify(attendanceMapper).updateModRequestReview(eq(modRequestId), eq("APPROVED"), eq(reviewerId), isNull(), any());
        }

        @Test
        @DisplayName("却下処理: 却下時に既存勤怠の保留状態がCOMPLETEDに復帰し却下理由が記録されること")
        void reviewModification_WhenRejected_ShouldRestoreCompletedStatus() {
            Long modRequestId = 1L;
            Long reviewerId = 999L;
            LocalDate workDate = LocalDate.of(2026, 11, 10);

            AttendanceModResponseDto req = new AttendanceModResponseDto();
            req.setModRequestId(modRequestId);
            req.setAttendanceId(100L);
            req.setStoreStaffId(10L);
            req.setWorkDate(workDate);
            req.setApprovalStatus("PENDING");

            when(attendanceMapper.findModRequestById(modRequestId)).thenReturn(req);
            when(attendanceMapper.isPayrollConfirmedForDate(10L, workDate)).thenReturn(false);
            when(attendanceMapper.updateModRequestReview(eq(modRequestId), eq("REJECTED"), eq(reviewerId), eq("防犯カメラ記録と勤務時間が一致しないため"), any())).thenReturn(1);

            AttendanceModReviewDto reviewDto = new AttendanceModReviewDto();
            reviewDto.setAction("REJECT");
            reviewDto.setRejectReason("防犯カメラ記録と勤務時間が一致しないため");

            attendanceService.reviewModification(modRequestId, reviewerId, reviewDto);

            // COMPLETED状態復帰およびREJECTED更新の検証
            verify(attendanceMapper).updateAttendanceStatus(100L, "COMPLETED");
            verify(attendanceMapper).updateModRequestReview(eq(modRequestId), eq("REJECTED"), eq(reviewerId), eq("防犯カメラ記録と勤務時間が一致しないため"), any());
        }
    }

    @Nested
    @DisplayName("勤怠修正申請一覧取得（バッチ取得・N+1解消）の検証")
    class GetModRequestsTest {

        @Test
        @DisplayName("バッチ取得: 全申請の休憩スナップショットが一括取得され、N+1クエリが発生しないこと")
        void getModRequests_ShouldBatchLoadBreaksAndPreventNPlusOne() {
            Long storeId = 1L;
            AttendanceModResponseDto item1 = new AttendanceModResponseDto();
            item1.setModRequestId(101L);
            AttendanceModResponseDto item2 = new AttendanceModResponseDto();
            item2.setModRequestId(102L);

            when(attendanceMapper.findModRequestsByStoreId(storeId, "PENDING")).thenReturn(List.of(item1, item2));

            AttendanceBreakDto break1 = new AttendanceBreakDto();
            break1.setAttendanceId(101L);
            break1.setBreakSetType("BEFORE");

            AttendanceBreakDto break2 = new AttendanceBreakDto();
            break2.setAttendanceId(102L);
            break2.setBreakSetType("REQUESTED");

            when(attendanceMapper.findModBreaksByRequestIds(List.of(101L, 102L))).thenReturn(List.of(break1, break2));

            List<AttendanceModResponseDto> result = attendanceService.getModRequests(storeId, "PENDING");

            assertThat(result).hasSize(2);
            assertThat(result.get(0).getBeforeBreaks()).containsExactly(break1);
            assertThat(result.get(0).getRequestedBreaks()).isEmpty();
            assertThat(result.get(1).getBeforeBreaks()).isEmpty();
            assertThat(result.get(1).getRequestedBreaks()).containsExactly(break2);

            // N+1防止: findModBreaksByRequestId(単件)は一切呼ばれず、一括メソッドが1回のみ呼ばれたことを検証
            verify(attendanceMapper, times(1)).findModBreaksByRequestIds(List.of(101L, 102L));
            verify(attendanceMapper, never()).findModBreaksByRequestId(anyLong(), anyString());
        }
    }
}
