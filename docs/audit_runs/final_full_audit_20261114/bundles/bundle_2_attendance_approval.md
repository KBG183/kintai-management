# Final Full Audit Bundle: bundle_2_attendance_approval
Generated: 2026-11-14T12:48:13+09:00

## FILE: src\main\java\com\example\kintai\domain\attendance\controller\AttendanceApiController.java
```
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
```

## FILE: src\main\java\com\example\kintai\domain\attendance\controller\AttendanceController.java
```
package com.example.kintai.domain.attendance.controller;

import com.example.kintai.global.security.CustomUserDetails;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * 勤怠管理 画面遷移コントローラー
 */
@Controller
public class AttendanceController {

    /**
     * 打刻画面表示
     */
    @GetMapping("/attendance")
    public String attendancePunchView(
            @RequestParam(name = "storeStaffId", required = false) Long storeStaffId,
            @AuthenticationPrincipal CustomUserDetails userDetails,
            Model model
    ) {
        Long targetStaffId = (userDetails != null && userDetails.getStoreStaffId() != null)
                ? userDetails.getStoreStaffId()
                : (storeStaffId != null ? storeStaffId : 1L);

        model.addAttribute("storeStaffId", targetStaffId);
        if (userDetails != null) {
            model.addAttribute("loginUserName", userDetails.getName());
            model.addAttribute("loginUserRole", userDetails.getRole());
        }
        return "attendance/punch";
    }

    /**
     * 勤怠修正申請一覧（オーナー承認画面）表示
     */
    @GetMapping("/attendance/mod-requests")
    public String attendanceModListView(
            @RequestParam(name = "storeId", required = false) Long storeId,
            @AuthenticationPrincipal CustomUserDetails userDetails,
            Model model
    ) {
        Long targetStoreId = (userDetails != null && userDetails.getStoreId() != null)
                ? userDetails.getStoreId()
                : (storeId != null ? storeId : 1L);

        model.addAttribute("storeId", targetStoreId);
        if (userDetails != null) {
            model.addAttribute("loginUserName", userDetails.getName());
            model.addAttribute("loginUserRole", userDetails.getRole());
        }
        return "attendance/mod-list";
    }
}
```

## FILE: src\main\java\com\example\kintai\domain\attendance\controller\LaborCalculationApiController.java
```
package com.example.kintai.domain.attendance.controller;

import com.example.kintai.domain.attendance.dto.AttendanceDto;
import com.example.kintai.domain.attendance.dto.LaborDetailDto;
import com.example.kintai.domain.attendance.dto.LaborSimulationRequestDto;
import com.example.kintai.domain.attendance.mapper.AttendanceMapper;
import com.example.kintai.domain.attendance.service.LaborCalculationService;
import com.example.kintai.domain.store.service.StoreAuthorizationService;
import com.example.kintai.global.common.ApiResponse;
import com.example.kintai.global.security.CustomUserDetails;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

/**
 * 日本労働基準法第37条 労働時間・割増賃金精密算定 REST API コントローラー
 */
@RestController
@RequestMapping("/api/labor")
@RequiredArgsConstructor
public class LaborCalculationApiController {

    private final LaborCalculationService laborCalculationService;
    private final AttendanceMapper attendanceMapper;
    private final StoreAuthorizationService storeAuthorizationService;

    /**
     * 登録済み勤怠実績の労働基準法第37条精密内訳取得 API
     * IDOR（他人の給与・労働時間内訳閲覧）を防止するアクセス制御を適用
     */
    @GetMapping("/attendance/{attendanceId}")
    public ResponseEntity<ApiResponse<LaborDetailDto>> getDetailedLabor(
            @PathVariable("attendanceId") Long attendanceId,
            @AuthenticationPrincipal CustomUserDetails userDetails
    ) {
        if (userDetails == null) {
            throw new AccessDeniedException("認証情報が存在しません。(IDOR遮断)");
        }

        AttendanceDto attendance = attendanceMapper.findById(attendanceId);
        if (attendance == null) {
            throw new IllegalArgumentException("指定された勤怠実績が存在しません。(ID: " + attendanceId + ")");
        }

        // IDOR 防護チェック: スタッフは本人のみ、オーナーは所有店舗のみ許可
        if ("ROLE_STAFF".equals(userDetails.getRole())) {
            if (userDetails.getStoreStaffId() == null || !userDetails.getStoreStaffId().equals(attendance.getStoreStaffId())) {
                throw new AccessDeniedException("他人の勤怠労働法明細を閲覧する権限がありません。");
            }
        } else if ("ROLE_OWNER".equals(userDetails.getRole())) {
            storeAuthorizationService.validateStoreOwnership(attendance.getStoreId(), userDetails.getMemberId());
        } else {
            throw new AccessDeniedException("閲覧権限がありません。");
        }

        LaborDetailDto result = laborCalculationService.calculateDetailedLabor(attendanceId);
        return ResponseEntity.ok(ApiResponse.ok("労働基準法第37条精密内訳を取得しました。", result));
    }

    /**
     * 任意の日時および時給に基づく労働基準法第37条シミュレーション算定 API
     */
    @PostMapping("/simulate")
    public ResponseEntity<ApiResponse<LaborDetailDto>> simulateLabor(
            @RequestBody LaborSimulationRequestDto request
    ) {
        LaborDetailDto result = laborCalculationService.simulateLabor(request);
        return ResponseEntity.ok(ApiResponse.ok("シミュレーション試算が完了しました。", result));
    }
}
```

## FILE: src\main\java\com\example\kintai\domain\attendance\service\AttendanceService.java
```
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
        if (punchDto == null) {
            throw new IllegalArgumentException("打刻データは必須です。");
        }
        Long storeStaffId = punchDto.getStoreStaffId();
        String punchType = punchDto.getPunchType();
        if (storeStaffId == null || storeStaffId <= 0) {
            throw new IllegalArgumentException("有効なスタッフIDは必須です。");
        }
        if (punchType == null || !List.of("CLOCK_IN", "CLOCK_OUT", "BREAK_START", "BREAK_END").contains(punchType)) {
            throw new IllegalArgumentException("有効な打刻タイプは必須です。");
        }
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
```

## FILE: src\main\java\com\example\kintai\domain\attendance\service\LaborCalculationService.java
```
package com.example.kintai.domain.attendance.service;

import com.example.kintai.domain.attendance.dto.AttendanceBreakDto;
import com.example.kintai.domain.attendance.dto.AttendanceDto;
import com.example.kintai.domain.attendance.dto.LaborDetailDto;
import com.example.kintai.domain.attendance.dto.LaborSimulationRequestDto;
import com.example.kintai.domain.attendance.mapper.AttendanceMapper;
import com.example.kintai.domain.attendance.util.AttendanceTimeCalculator;
import com.example.kintai.domain.payroll.util.PayrollCalculator;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

/**
 * 日本労働基準法第37条に基づく時間外・深夜・重畳割増（150%）および手当精密算定サービス。
 */
@Service
@Transactional(readOnly = true)
public class LaborCalculationService {

    private final AttendanceMapper attendanceMapper;

    public LaborCalculationService(AttendanceMapper attendanceMapper) {
        this.attendanceMapper = attendanceMapper;
    }

    /**
     * 登録済み勤怠実績IDに基づく労働基準法第37条精密内訳の算定
     */
    public LaborDetailDto calculateDetailedLabor(Long attendanceId) {
        if (attendanceId == null) {
            throw new IllegalArgumentException("勤怠IDは必須です。");
        }

        AttendanceDto attendance = attendanceMapper.findById(attendanceId);
        if (attendance == null) {
            throw new IllegalArgumentException("指定された勤怠実績が存在しません。(ID: " + attendanceId + ")");
        }

        if (attendance.getClockOutAt() == null) {
            throw new IllegalStateException("退勤打刻が完了していない勤怠データは労働法集計できません。");
        }

        List<AttendanceBreakDto> breakEntities = attendanceMapper.findBreaksByAttendanceId(attendanceId);
        List<AttendanceTimeCalculator.BreakInterval> breakIntervals = new ArrayList<>();
        if (breakEntities != null) {
            for (AttendanceBreakDto b : breakEntities) {
                breakIntervals.add(new AttendanceTimeCalculator.BreakInterval(b.getBreakStartAt(), b.getBreakEndAt()));
            }
        }

        AttendanceTimeCalculator.DetailedCalculationResult calc = AttendanceTimeCalculator.calculateDetailed(
                attendance.getClockInAt(),
                attendance.getClockOutAt(),
                breakIntervals
        );

        Integer hourlyWageVal = attendanceMapper.findHourlyWageForStaffOnDate(
                attendance.getStoreStaffId(),
                attendance.getWorkDate()
        );
        int hourlyWage = (hourlyWageVal != null) ? hourlyWageVal : 1280;

        PayrollCalculator.DetailedDailyPayResult pay = PayrollCalculator.calculateDetailedDailyPay(hourlyWage, calc);
        String staffName = attendanceMapper.findStaffNameByStoreStaffId(attendance.getStoreStaffId());

        return mapToDto(attendance, staffName, hourlyWage, calc, pay);
    }

    /**
     * 任意の日時および時給に基づく労働基準法第37条シミュレーション算定
     */
    public LaborDetailDto simulateLabor(LaborSimulationRequestDto request) {
        if (request == null || request.getClockInAt() == null || request.getClockOutAt() == null) {
            throw new IllegalArgumentException("シミュレーションの出退勤日時は必須です。");
        }

        int hourlyWage = (request.getHourlyWage() != null && request.getHourlyWage() > 0)
                ? request.getHourlyWage()
                : 1280;

        List<AttendanceTimeCalculator.BreakInterval> breakIntervals = new ArrayList<>();
        if (request.getBreaks() != null) {
            for (LaborSimulationRequestDto.SimulationBreakDto b : request.getBreaks()) {
                if (b.getStartAt() != null && b.getEndAt() != null) {
                    breakIntervals.add(new AttendanceTimeCalculator.BreakInterval(b.getStartAt(), b.getEndAt()));
                }
            }
        }

        AttendanceTimeCalculator.DetailedCalculationResult calc = AttendanceTimeCalculator.calculateDetailed(
                request.getClockInAt(),
                request.getClockOutAt(),
                breakIntervals
        );

        PayrollCalculator.DetailedDailyPayResult pay = PayrollCalculator.calculateDetailedDailyPay(hourlyWage, calc);

        AttendanceDto dummyAtt = new AttendanceDto();
        dummyAtt.setWorkDate(request.getClockInAt().toLocalDate());
        dummyAtt.setClockInAt(request.getClockInAt());
        dummyAtt.setClockOutAt(request.getClockOutAt());

        return mapToDto(dummyAtt, "シミュレーション試算", hourlyWage, calc, pay);
    }

    private LaborDetailDto mapToDto(
            AttendanceDto attendance,
            String staffName,
            int hourlyWage,
            AttendanceTimeCalculator.DetailedCalculationResult calc,
            PayrollCalculator.DetailedDailyPayResult pay
    ) {
        LaborDetailDto dto = new LaborDetailDto();
        dto.setAttendanceId(attendance.getAttendanceId());
        dto.setStoreStaffId(attendance.getStoreStaffId());
        dto.setStaffName(staffName);
        dto.setWorkDate(attendance.getWorkDate());
        dto.setClockInAt(attendance.getClockInAt());
        dto.setClockOutAt(attendance.getClockOutAt());

        // 時間区分（分）
        dto.setTotalStayMinutes(calc.totalStayMinutes());
        dto.setTotalBreakMinutes(calc.totalBreakMinutes());
        dto.setNetWorkMinutes(calc.netWorkMinutes());
        dto.setRegularWorkMinutes(calc.regularWorkMinutes());
        dto.setRegularNightWorkMinutes(calc.regularNightWorkMinutes());
        dto.setOvertimeWorkMinutes(calc.overtimeWorkMinutes());
        dto.setNightOvertimeWorkMinutes(calc.nightOvertimeWorkMinutes());
        dto.setTotalNightMinutes(calc.nightWorkMinutes());
        dto.setTotalOvertimeMinutes(calc.overtimeMinutes());

        // 金額区分（円）
        dto.setAppliedHourlyWage(hourlyWage);
        dto.setRegularPay(pay.regularPay());
        dto.setRegularNightPay(pay.regularNightPay());
        dto.setOvertimePay(pay.overtimePay());
        dto.setNightOvertimePay(pay.nightOvertimePay());
        dto.setBasePay(pay.basePay());
        dto.setNightAllowance(pay.nightAllowance());
        dto.setOvertimeAllowance(pay.overtimeAllowance());
        dto.setTotalPay(pay.totalPay());

        return dto;
    }
}
```

## FILE: src\main\java\com\example\kintai\domain\attendance\mapper\AttendanceMapper.java
```
package com.example.kintai.domain.attendance.mapper;

import com.example.kintai.domain.attendance.dto.AttendanceBreakDto;
import com.example.kintai.domain.attendance.dto.AttendanceDto;
import com.example.kintai.domain.attendance.dto.AttendanceModRequestDto;
import com.example.kintai.domain.attendance.dto.AttendanceModResponseDto;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface AttendanceMapper {

    AttendanceDto findActiveAttendance(@Param("storeStaffId") Long storeStaffId);

    AttendanceDto findById(@Param("attendanceId") Long attendanceId);

    int insertAttendance(AttendanceDto attendance);

    int updateAttendance(AttendanceDto attendance);

    int insertBreak(AttendanceBreakDto breakDto);

    List<AttendanceBreakDto> findBreaksByAttendanceId(@Param("attendanceId") Long attendanceId);

    int deleteBreaksByAttendanceId(@Param("attendanceId") Long attendanceId);

    // 該当勤務日の給与が確定済（CONFIRMED / PAID）か確認
    boolean isPayrollConfirmedForDate(
            @Param("storeStaffId") Long storeStaffId,
            @Param("workDate") LocalDate workDate
    );

    // 同時更新防止のための悲観的ロック (SELECT ... FOR UPDATE)
    void lockStoreStaffForUpdate(@Param("storeStaffId") Long storeStaffId);

    // 店舗スタッフ所属情報から店舗IDを取得
    Long findStoreIdByStoreStaffId(@Param("storeStaffId") Long storeStaffId);

    Long findConfirmedShiftIdForDate(
            @Param("storeStaffId") Long storeStaffId,
            @Param("workDate") LocalDate workDate
    );

    List<AttendanceDto> findCompletedAttendancesForStaffAndPeriod(
            @Param("storeStaffId") Long storeStaffId,
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate
    );

    // 勤怠修正申請関連
    /**
     * 店舗および指定勤務日の全スタッフ勤怠実績取得（シフト・勤怠リアルタイム対照用）
     */
    List<AttendanceDto> findAttendancesByStoreAndDate(
            @Param("storeId") Long storeId,
            @Param("workDate") LocalDate workDate
    );

    /**
     * 店舗スタッフIDに基づくスタッフ氏名取得（労働基準法明細表示用）
     */
    String findStaffNameByStoreStaffId(@Param("storeStaffId") Long storeStaffId);

    /**
     * 勤務日時点の契約時給取得
     */
    Integer findHourlyWageForStaffOnDate(
            @Param("storeStaffId") Long storeStaffId,
            @Param("workDate") LocalDate workDate
    );

    int insertModRequest(AttendanceModRequestDto modRequest);

    int insertModBreak(
            @Param("modRequestId") Long modRequestId,
            @Param("breakSetType") String breakSetType,
            @Param("startAt") LocalDateTime startAt,
            @Param("endAt") LocalDateTime endAt,
            @Param("duration") int duration
    );

    List<AttendanceModResponseDto> findModRequestsByStoreId(
            @Param("storeId") Long storeId,
            @Param("status") String status
    );

    AttendanceModResponseDto findModRequestById(@Param("modRequestId") Long modRequestId);

    List<AttendanceBreakDto> findModBreaksByRequestId(
            @Param("modRequestId") Long modRequestId,
            @Param("breakSetType") String breakSetType
    );

    /**
     * 複数申請IDに対する休憩区間一括取得（N+1問題の解消）
     */
    List<AttendanceBreakDto> findModBreaksByRequestIds(@Param("requestIds") List<Long> requestIds);

    int updateModRequestReview(
            @Param("modRequestId") Long modRequestId,
            @Param("status") String status,
            @Param("reviewerId") Long reviewerId,
            @Param("rejectReason") String rejectReason,
            @Param("reviewedAt") LocalDateTime reviewedAt
    );

    /**
     * 勤怠ステータスの単体更新
     */
    int updateAttendanceStatus(
            @Param("attendanceId") Long attendanceId,
            @Param("attendanceStatus") String attendanceStatus
    );

    /**
     * 同一スタッフにおける勤務時間の重複件数をカウント（勤務時間重複防止）
     */
    int countOverlappingAttendances(
            @Param("storeStaffId") Long storeStaffId,
            @Param("clockInAt") LocalDateTime clockInAt,
            @Param("clockOutAt") LocalDateTime clockOutAt,
            @Param("excludeAttendanceId") Long excludeAttendanceId
    );

    /**
     * 事後新規登録された勤怠IDを修正申請レコードに紐付け
     */
    int updateModRequestAttendanceId(
            @Param("modRequestId") Long modRequestId,
            @Param("attendanceId") Long attendanceId
    );

    /**
     * 同一スタッフ・同一勤務日の先行完了勤務の実労働時間合計（分）を取得（分割勤務中抜け対応）
     */
    int sumPriorNetWorkMinutesOnDate(
            @Param("storeStaffId") Long storeStaffId,
            @Param("workDate") LocalDate workDate,
            @Param("excludeAttendanceId") Long excludeAttendanceId,
            @Param("beforeTime") LocalDateTime beforeTime
    );

    /**
     * 同一スタッフ・同一勤務日の先行完了勤務の実労働時間合計（秒）を取得（秒精度・中抜け対応）
     */
    long sumPriorNetWorkSecondsOnDate(
            @Param("storeStaffId") Long storeStaffId,
            @Param("workDate") LocalDate workDate,
            @Param("excludeAttendanceId") Long excludeAttendanceId,
            @Param("beforeTime") LocalDateTime beforeTime
    );
}
```

## FILE: src\main\resources\mapper\attendance\AttendanceMapper.xml
```
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

    <select id="lockStoreStaffForUpdate" resultType="java.lang.Long">
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
        SELECT hourly_wage
        FROM store_staff_contract_history
        WHERE store_staff_id = #{storeStaffId}
          AND effective_from &lt;= #{workDate}
          AND (effective_to IS NULL OR effective_to &gt; #{workDate})
        ORDER BY effective_from DESC
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
```

