# Final Full Audit Bundle: bundle_3_shift_calendar_ui
Generated: 2026-11-14T12:48:13+09:00

## FILE: src\main\java\com\example\kintai\domain\shift\controller\ShiftApiController.java
```
package com.example.kintai.domain.shift.controller;

import com.example.kintai.domain.shift.dto.CalendarEventDto;
import com.example.kintai.domain.shift.dto.ShiftBatchConfirmDto;
import com.example.kintai.domain.shift.dto.ShiftComparisonDto;
import com.example.kintai.domain.shift.dto.ShiftConfirmDto;
import com.example.kintai.domain.shift.dto.ShiftDto;
import com.example.kintai.domain.shift.dto.ShiftRequestDto;
import com.example.kintai.domain.shift.service.ShiftService;
import com.example.kintai.domain.store.service.StoreAuthorizationService;
import com.example.kintai.global.common.ApiResponse;
import com.example.kintai.global.security.CustomUserDetails;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/shifts")
@RequiredArgsConstructor
public class ShiftApiController {

    private final ShiftService shiftService;
    private final StoreAuthorizationService storeAuthorizationService;

    /**
     * 月間カレンダー用シフトイベント一覧取得 (店舗全体)
     * セッションの店舗IDを最優先とし、未認証・所属不明なリクエストは拒否 (クロス店舗IDOR防止)
     */
    @GetMapping("/events")
    public ResponseEntity<List<CalendarEventDto>> getEvents(
            @RequestParam(name = "storeId", required = false) Long storeId,
            @RequestParam("start") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate start,
            @RequestParam("end") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate end,
            @AuthenticationPrincipal CustomUserDetails userDetails
    ) {
        if (userDetails == null) {
            throw new AccessDeniedException("シフト閲覧にはログイン認証が必要です。(IDOR遮断)");
        }
        if (start.isAfter(end)) {
            throw new IllegalArgumentException("開始日は終了日以前である必要があります。");
        }

        boolean isManager = userDetails.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_OWNER") || a.getAuthority().equals("ROLE_MANAGER"));

        Long targetStoreId;
        if (isManager && storeId != null) {
            // 店長・管理者ロールであっても自身が所有する店舗であるかを厳格に検証 (多店舗IDOR完全遮断)
            storeAuthorizationService.validateStoreOwnership(storeId, userDetails.getMemberId());
            targetStoreId = storeId;
        } else if (userDetails.getStoreId() != null) {
            // 一般スタッフはセッションに紐づく自身の所属店舗のみ参照可能 (クロス店舗閲覧遮断)
            targetStoreId = userDetails.getStoreId();
        } else {
            throw new AccessDeniedException("所属店舗情報が特定できません。");
        }

        List<CalendarEventDto> events = shiftService.getCalendarEvents(
                targetStoreId, start, end, userDetails.getStoreStaffId(), isManager
        );
        return ResponseEntity.ok(events);
    }

    /**
     * スタッフ本人用シフト一覧取得 (IDOR防止: セッションからstoreStaffIdを厳格に強制導出)
     */
    @GetMapping("/my-events")
    public ResponseEntity<List<CalendarEventDto>> getMyEvents(
            @RequestParam("start") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate start,
            @RequestParam("end") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate end,
            @AuthenticationPrincipal CustomUserDetails userDetails
    ) {
        if (userDetails == null || userDetails.getStoreStaffId() == null) {
            throw new AccessDeniedException("スタッフ認証セッションが存在しません。(IDOR遮断)");
        }
        if (start.isAfter(end)) {
            throw new IllegalArgumentException("開始日は終了日以前である必要があります。");
        }

        List<CalendarEventDto> events = shiftService.getStaffShiftEvents(userDetails.getStoreStaffId(), start, end);
        return ResponseEntity.ok(events);
    }

    /**
     * スタッフによる希望シフト提出 (IDOR防止: セッション情報による強制上書き・クライアント値の盲信排除)
     */
    @PostMapping("/request")
    public ResponseEntity<ApiResponse<Long>> requestShift(
            @RequestBody ShiftRequestDto requestDto,
            @AuthenticationPrincipal CustomUserDetails userDetails
    ) {
        if (userDetails == null || userDetails.getStoreStaffId() == null || userDetails.getStoreId() == null) {
            throw new AccessDeniedException("有効なスタッフセッションが必要です。(IDOR遮断)");
        }

        // セッションから強制上書き (クライアントからのパラメータ改ざんを無効化)
        requestDto.setStoreStaffId(userDetails.getStoreStaffId());
        requestDto.setStoreId(userDetails.getStoreId());

        Long shiftId = shiftService.requestShift(requestDto);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok("希望シフトが登録されました。", shiftId));
    }

    /**
     * スタッフによる希望シフト申請の取消 (IDOR防止: セッション情報照合)
     */
    @DeleteMapping("/{shiftId}")
    public ResponseEntity<ApiResponse<Void>> deleteShift(
            @PathVariable("shiftId") Long shiftId,
            @AuthenticationPrincipal CustomUserDetails userDetails
    ) {
        if (userDetails == null || userDetails.getStoreStaffId() == null) {
            throw new AccessDeniedException("スタッフ認証セッションが存在しません。(IDOR遮断)");
        }

        shiftService.deleteShiftRequest(shiftId, userDetails.getStoreStaffId());
        return ResponseEntity.ok(ApiResponse.ok("希望シフトの申請を取り消しました。", null));
    }

    /**
     * 店長によるシフト確定・更新 (権限チェックおよび自店舗データ検証)
     */
    @PutMapping("/{shiftId}/confirm")
    public ResponseEntity<ApiResponse<Void>> confirmShift(
            @PathVariable("shiftId") Long shiftId,
            @RequestBody ShiftConfirmDto confirmDto,
            @AuthenticationPrincipal CustomUserDetails userDetails
    ) {
        if (userDetails == null || userDetails.getStoreId() == null) {
            throw new AccessDeniedException("店長認証セッションおよび店舗情報が必要です。(IDOR遮断)");
        }
        // 店長権限(ROLE_OWNER / ROLE_MANAGER)の検証
        boolean isManager = userDetails.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_OWNER") || a.getAuthority().equals("ROLE_MANAGER"));
        if (!isManager) {
            throw new AccessDeniedException("シフト確定権限がありません。(店長・管理者権限が必要)");
        }

        shiftService.confirmShift(shiftId, userDetails.getStoreId(), confirmDto);
        return ResponseEntity.ok(ApiResponse.ok("シフトが確定されました。", null));
    }

    /**
     * 店長によるシフト申請の却下 (自店舗検証および権限チェック)
     */
    @PutMapping("/{shiftId}/reject")
    public ResponseEntity<ApiResponse<Void>> rejectShift(
            @PathVariable("shiftId") Long shiftId,
            @RequestBody(required = false) ShiftConfirmDto confirmDto,
            @AuthenticationPrincipal CustomUserDetails userDetails
    ) {
        if (userDetails == null || userDetails.getStoreId() == null) {
            throw new AccessDeniedException("店長認証セッションおよび店舗情報が必要です。(IDOR遮断)");
        }
        boolean isManager = userDetails.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_OWNER") || a.getAuthority().equals("ROLE_MANAGER"));
        if (!isManager) {
            throw new AccessDeniedException("シフト却下権限がありません。(店長・管理者権限が必要)");
        }

        String memo = (confirmDto != null) ? confirmDto.getOwnerMemo() : null;
        shiftService.rejectShift(shiftId, userDetails.getStoreId(), memo);
        return ResponseEntity.ok(ApiResponse.ok("シフト申請を却下しました。", null));
    }

    /**
     * 店長による未確定シフトの複数一括確定
     */
    @PostMapping("/batch-confirm")
    public ResponseEntity<ApiResponse<Integer>> batchConfirmShifts(
            @RequestBody ShiftBatchConfirmDto batchDto,
            @AuthenticationPrincipal CustomUserDetails userDetails
    ) {
        if (userDetails == null || userDetails.getStoreId() == null) {
            throw new AccessDeniedException("店長認証セッションおよび店舗情報が必要です。(IDOR遮断)");
        }
        boolean isManager = userDetails.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_OWNER") || a.getAuthority().equals("ROLE_MANAGER"));
        if (!isManager) {
            throw new AccessDeniedException("一括確定権限がありません。(店長・管理者権限が必要)");
        }

        int confirmedCount = shiftService.batchConfirmShifts(userDetails.getStoreId(), batchDto);
        return ResponseEntity.ok(ApiResponse.ok(confirmedCount + "件のシフトを一括確定しました。", confirmedCount));
    }

    /**
     * 店舗内の申請中（未確定）シフト一覧取得
     */
    @GetMapping("/pending")
    public ResponseEntity<List<ShiftDto>> getPendingShifts(
            @RequestParam(value = "start", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate start,
            @RequestParam(value = "end", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate end,
            @AuthenticationPrincipal CustomUserDetails userDetails
    ) {
        if (userDetails == null || userDetails.getStoreId() == null) {
            throw new AccessDeniedException("店長認証セッションが必要です。(IDOR遮断)");
        }
        boolean isManager = userDetails.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_OWNER") || a.getAuthority().equals("ROLE_MANAGER"));
        if (!isManager) {
            throw new AccessDeniedException("未確定シフト閲覧権限がありません。");
        }

        List<ShiftDto> pendingList = shiftService.getPendingShifts(userDetails.getStoreId(), start, end);
        return ResponseEntity.ok(pendingList);
    }

    /**
     * シフト予定 vs 実績打刻 リアルタイム照合API
     */
    @GetMapping("/comparison")
    public ResponseEntity<List<ShiftComparisonDto>> getShiftComparison(
            @RequestParam(value = "date", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @AuthenticationPrincipal CustomUserDetails userDetails
    ) {
        if (userDetails == null || userDetails.getStoreId() == null) {
            throw new AccessDeniedException("店長認証セッションが必要です。(IDOR遮断)");
        }
        boolean isManager = userDetails.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_OWNER") || a.getAuthority().equals("ROLE_MANAGER"));
        if (!isManager) {
            throw new AccessDeniedException("シフト対照閲覧権限がありません。");
        }

        LocalDate targetDate = (date != null) ? date : LocalDate.now();
        List<ShiftComparisonDto> comparisonList = shiftService.compareShiftWithAttendance(userDetails.getStoreId(), targetDate);
        return ResponseEntity.ok(comparisonList);
    }
}
```

## FILE: src\main\java\com\example\kintai\domain\shift\controller\ShiftController.java
```
package com.example.kintai.domain.shift.controller;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
public class ShiftController {

    @GetMapping("/shifts")
    public String shiftCalendarView(
            @RequestParam(name = "storeId", defaultValue = "1") Long storeId,
            Model model
    ) {
        model.addAttribute("storeId", storeId);
        return "shift/calendar";
    }

    /**
     * 店長専用 シフト確定・勤怠リアルタイム照合管理画面
     */
    @GetMapping("/shifts/manage")
    public String shiftManagementView(
            @RequestParam(name = "storeId", defaultValue = "1") Long storeId,
            Model model
    ) {
        model.addAttribute("storeId", storeId);
        return "shift/manage";
    }
}
```

## FILE: src\main\java\com\example\kintai\domain\shift\service\ShiftService.java
```
package com.example.kintai.domain.shift.service;

import com.example.kintai.domain.attendance.dto.AttendanceDto;
import com.example.kintai.domain.attendance.mapper.AttendanceMapper;
import com.example.kintai.domain.shift.dto.CalendarEventDto;
import com.example.kintai.domain.shift.dto.ShiftBatchConfirmDto;
import com.example.kintai.domain.shift.dto.ShiftComparisonDto;
import com.example.kintai.domain.shift.dto.ShiftConfirmDto;
import com.example.kintai.domain.shift.dto.ShiftDto;
import com.example.kintai.domain.shift.dto.ShiftRequestDto;
import com.example.kintai.domain.shift.mapper.ShiftMapper;
import com.example.kintai.global.common.TimeUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

@Service
@Transactional(readOnly = true)
public class ShiftService {

    private final ShiftMapper shiftMapper;
    private final AttendanceMapper attendanceMapper;

    public ShiftService(ShiftMapper shiftMapper, AttendanceMapper attendanceMapper) {
        this.shiftMapper = shiftMapper;
        this.attendanceMapper = attendanceMapper;
    }

    /**
     * 月間カレンダー用シフトイベント一覧取得 (店舗全体・プライバシーマスク連動)
     */
    public List<CalendarEventDto> getCalendarEvents(Long storeId, LocalDate startDate, LocalDate endDate, Long currentStaffId, boolean isManager) {
        List<ShiftDto> shifts = shiftMapper.findShiftEvents(storeId, startDate, endDate);
        return shifts.stream()
                .map(s -> CalendarEventDto.from(s, currentStaffId, isManager))
                .toList();
    }

    public List<CalendarEventDto> getCalendarEvents(Long storeId, LocalDate startDate, LocalDate endDate) {
        return getCalendarEvents(storeId, startDate, endDate, null, true);
    }

    /**
     * スタッフ本人用シフト一覧取得 (IDOR防止: セッション認証済みのstoreStaffIdに基づく取得)
     */
    public List<CalendarEventDto> getStaffShiftEvents(Long storeStaffId, LocalDate startDate, LocalDate endDate) {
        if (storeStaffId == null) {
            throw new IllegalArgumentException("店舗スタッフIDが指定されていません。");
        }
        List<ShiftDto> shifts = shiftMapper.findStaffShiftList(storeStaffId, startDate, endDate);
        return shifts.stream()
                .map(CalendarEventDto::from)
                .toList();
    }

    /**
     * スタッフによる希望シフト提出 (過去日付拒否・時間逆転防止・排他ロック)
     */
    @Transactional
    public Long requestShift(ShiftRequestDto requestDto) {
        if (requestDto == null) {
            throw new IllegalArgumentException("リクエストデータが空です。");
        }
        if (requestDto.getStoreStaffId() == null) {
            throw new IllegalArgumentException("店舗スタッフIDは必須です。");
        }
        if (requestDto.getWorkDate() == null) {
            throw new IllegalArgumentException("勤務日は必須です。");
        }
        if (requestDto.getWorkDate().isBefore(LocalDate.now())) {
            throw new IllegalArgumentException("過去の日付に対する希望シフトは申請できません。");
        }
        if (requestDto.getRequestedStartTime() == null || requestDto.getRequestedEndTime() == null) {
            throw new IllegalArgumentException("開始時刻および終了時刻は必須です。");
        }

        // 排他制御: 悲観的ロックによる直列化 (同一スタッフの多重登録レースコンディション防止)
        shiftMapper.lockStoreStaffForUpdate(requestDto.getStoreStaffId());

        LocalDate workDate = requestDto.getWorkDate();
        LocalTime startLocalTime;
        LocalTime endLocalTime;
        try {
            startLocalTime = LocalTime.parse(requestDto.getRequestedStartTime());
            endLocalTime = LocalTime.parse(requestDto.getRequestedEndTime());
        } catch (DateTimeParseException e) {
            throw new IllegalArgumentException("時刻のフォーマットが不正です。(HH:mm形式で入力してください)", e);
        }

        LocalDateTime startAt = LocalDateTime.of(workDate, startLocalTime);
        LocalDateTime endAt = TimeUtils.resolveEndDateTime(workDate, requestDto.getRequestedStartTime(), requestDto.getRequestedEndTime());

        if (!endAt.isAfter(startAt)) {
            throw new IllegalArgumentException("終了日時は開始日時より後である必要があります。");
        }

        // 16時間超過検証の精度担保（Duration.compareToによる正確な比較）
        if (Duration.between(startAt, endAt).compareTo(Duration.ofHours(16)) > 0) {
            throw new IllegalArgumentException("1回のシフト時間は最大16時間以内である必要があります。");
        }

        // 重複シフト検証
        if (shiftMapper.existsOverlapShift(requestDto.getStoreStaffId(), workDate, startAt, endAt, null)) {
            throw new IllegalStateException("指定された時間帯に既に別のシフトが存在します。");
        }

        ShiftDto shift = new ShiftDto();
        shift.setStoreId(requestDto.getStoreId());
        shift.setStoreStaffId(requestDto.getStoreStaffId());
        shift.setWorkDate(workDate);
        shift.setRequestedStartAt(startAt);
        shift.setRequestedEndAt(endAt);
        shift.setPlannedBreakMinutes(0);
        shift.setStatus("SUBMITTED");
        shift.setStaffMemo(requestDto.getStaffMemo());

        shiftMapper.insertShift(shift);
        return shift.getShiftId();
    }

    /**
     * スタッフ本人による希望シフト申請の取消 (IDOR防御: 本人かつSUBMITTED状態のみ削除可能)
     */
    @Transactional
    public void deleteShiftRequest(Long shiftId, Long storeStaffId) {
        if (shiftId == null || storeStaffId == null) {
            throw new IllegalArgumentException("シフトIDおよび店舗スタッフIDは必須です。");
        }

        // 排他ロック
        shiftMapper.lockStoreStaffForUpdate(storeStaffId);

        ShiftDto existing = shiftMapper.findById(shiftId);
        if (existing == null) {
            throw new IllegalArgumentException("指定されたシフトが存在しません。(ID: " + shiftId + ")");
        }

        // IDOR検証: 自身のシフトであるかの確認
        if (!existing.getStoreStaffId().equals(storeStaffId)) {
            throw new SecurityException("他のスタッフのシフトを削除する権限がありません。(IDOR遮断)");
        }

        // 状態検証: すでに確定されたシフトはスタッフ側からは削除不可（店長へ要連絡）
        if (!"SUBMITTED".equals(existing.getStatus())) {
            throw new IllegalStateException("確定済または却下済のシフトは申請取消できません。店長へ直接ご相談ください。");
        }

        int deletedCount = shiftMapper.deleteShiftRequest(shiftId, storeStaffId);
        if (deletedCount == 0) {
            throw new IllegalStateException("シフトの取消に失敗しました。");
        }
    }

    /**
     * 店長によるシフト確定および法定休憩時間の検証 (自店舗データ検証によるクロス店舗IDOR防止)
     */
    @Transactional
    public void confirmShift(Long shiftId, Long storeId, ShiftConfirmDto confirmDto) {
        if (shiftId == null || storeId == null || confirmDto == null) {
            throw new IllegalArgumentException("シフト確定パラメータが不足しています。");
        }

        ShiftDto existing = shiftMapper.findById(shiftId);
        if (existing == null) {
            throw new IllegalArgumentException("存在しないシフトです。(ID: " + shiftId + ")");
        }

        // 店舗間IDOR検証: 自身の管理店舗のシフトであるかの確認
        if (!existing.getStoreId().equals(storeId)) {
            throw new SecurityException("他店舗のシフトを確定・変更する権限がありません。(店舗間IDOR遮断)");
        }

        // 排他ロック (スタッフIDに対して悲観的ロック)
        shiftMapper.lockStoreStaffForUpdate(existing.getStoreStaffId());

        if (confirmDto.getConfirmedStartTime() == null || confirmDto.getConfirmedEndTime() == null) {
            throw new IllegalArgumentException("確定開始時刻および終了時刻は必須です。");
        }

        LocalDate workDate = existing.getWorkDate();
        LocalTime startLocalTime;
        LocalTime endLocalTime;
        try {
            startLocalTime = LocalTime.parse(confirmDto.getConfirmedStartTime());
            endLocalTime = LocalTime.parse(confirmDto.getConfirmedEndTime());
        } catch (DateTimeParseException e) {
            throw new IllegalArgumentException("確定時刻のフォーマットが不正です。(HH:mm形式)", e);
        }

        LocalDateTime confirmedStartAt = LocalDateTime.of(workDate, startLocalTime);
        LocalDateTime confirmedEndAt = TimeUtils.resolveEndDateTime(workDate, confirmDto.getConfirmedStartTime(), confirmDto.getConfirmedEndTime());

        if (!confirmedEndAt.isAfter(confirmedStartAt)) {
            throw new IllegalArgumentException("終了日時は開始日時より後である必要があります。");
        }

        long grossMinutes = Duration.between(confirmedStartAt, confirmedEndAt).toMinutes();

        // 16時間超過検証
        if (Duration.between(confirmedStartAt, confirmedEndAt).compareTo(Duration.ofHours(16)) > 0) {
            throw new IllegalArgumentException("1回のシフト時間は最大16時間以内である必要があります。");
        }

        // 重複シフト検証（自身を除く）
        if (shiftMapper.existsOverlapShift(existing.getStoreStaffId(), workDate, confirmedStartAt, confirmedEndAt, shiftId)) {
            throw new IllegalStateException("指定された確定時間帯に他のシフトと重複が発生します。");
        }

        // 日本労働基準法第34条に基づく休憩時間計算 (6時間超45分、8時間超60分)
        int plannedBreak = (confirmDto.getPlannedBreakMinutes() != null)
                ? confirmDto.getPlannedBreakMinutes()
                : calculateRecommendedBreakMinutes(confirmedStartAt, confirmedEndAt);

        if (plannedBreak < 0 || plannedBreak >= grossMinutes) {
            throw new IllegalArgumentException("計画休憩時間は0分以上かつ総拘束時間未満である必要があります。");
        }

        existing.setConfirmedStartAt(confirmedStartAt);
        existing.setConfirmedEndAt(confirmedEndAt);
        existing.setPlannedBreakMinutes(plannedBreak);
        existing.setStatus(confirmDto.getStatus() != null ? confirmDto.getStatus() : "CONFIRMED");
        existing.setOwnerMemo(confirmDto.getOwnerMemo());

        int affected = shiftMapper.updateShiftConfirm(existing);
        if (affected != 1) {
            throw new IllegalStateException("他の管理者によって既にシフトが確定されたか、状態が変更されています。(CAS競合防止)");
        }
    }

    /**
     * 日本労働基準法第34条 休憩時間推奨算定
     */
    public int calculateRecommendedBreakMinutes(LocalDateTime startAt, LocalDateTime endAt) {
        long grossMinutes = Duration.between(startAt, endAt).toMinutes();
        if (grossMinutes <= 360) {
            return 0; // 6時間以下
        } else if (grossMinutes <= 525) {
            return 45; // 6時間超 8時間以下 (滞在 525分 - 45分 = 実働 480分=8時間)
        } else {
            return 60; // 8時間超
        }
    }

    /**
     * 店舗内の未確定（SUBMITTED）シフト一覧取得
     */
    public List<ShiftDto> getPendingShifts(Long storeId, LocalDate startDate, LocalDate endDate) {
        if (storeId == null) {
            throw new IllegalArgumentException("店舗IDは必須です。");
        }
        return shiftMapper.findPendingShifts(storeId, startDate, endDate);
    }

    /**
     * 店舗内の未確定シフト件数取得（通知バッジ用）
     */
    public int getPendingShiftCount(Long storeId) {
        if (storeId == null) {
            return 0;
        }
        return shiftMapper.countPendingShifts(storeId);
    }

    /**
     * 店長によるシフト申請の却下（REJECTED）
     */
    @Transactional
    public void rejectShift(Long shiftId, Long storeId, String ownerMemo) {
        if (shiftId == null || storeId == null) {
            throw new IllegalArgumentException("シフトIDおよび店舗IDは必須です。");
        }

        ShiftDto existing = shiftMapper.findById(shiftId);
        if (existing == null) {
            throw new IllegalArgumentException("指定されたシフトが存在しません。(ID: " + shiftId + ")");
        }

        if (!existing.getStoreId().equals(storeId)) {
            throw new SecurityException("他店舗のシフトを操作する権限がありません。(IDOR防御)");
        }

        shiftMapper.updateShiftStatus(shiftId, storeId, "REJECTED", ownerMemo);
    }

    /**
     * 店長による未確定シフトの複数一括確定処理
     */
    @Transactional
    public int batchConfirmShifts(Long storeId, ShiftBatchConfirmDto batchDto) {
        if (storeId == null || batchDto == null || batchDto.getShiftIds() == null || batchDto.getShiftIds().isEmpty()) {
            throw new IllegalArgumentException("一括確定の対象シフトが指定されていません。");
        }

        int confirmedCount = 0;
        for (Long shiftId : batchDto.getShiftIds()) {
            ShiftDto existing = shiftMapper.findById(shiftId);
            if (existing == null || !existing.getStoreId().equals(storeId)) {
                continue; // 存在しないまたは他店舗のデータはスキップ
            }

            if (!"SUBMITTED".equals(existing.getStatus())) {
                continue; // 既に確定または却下されたシフトは対象外
            }

            // 同時更新防止ロック
            shiftMapper.lockStoreStaffForUpdate(existing.getStoreStaffId());

            LocalDateTime startAt = existing.getRequestedStartAt();
            LocalDateTime endAt = existing.getRequestedEndAt();

            // 重複チェック
            if (shiftMapper.existsOverlapShift(existing.getStoreStaffId(), existing.getWorkDate(), startAt, endAt, shiftId)) {
                continue; // 重複がある場合は安全のためスキップ
            }

            // 法定休憩時間の自動算定
            int plannedBreak = calculateRecommendedBreakMinutes(startAt, endAt);

            existing.setConfirmedStartAt(startAt);
            existing.setConfirmedEndAt(endAt);
            existing.setPlannedBreakMinutes(plannedBreak);
            existing.setStatus("CONFIRMED");
            if (batchDto.getOwnerMemo() != null && !batchDto.getOwnerMemo().isBlank()) {
                existing.setOwnerMemo(batchDto.getOwnerMemo());
            }

            shiftMapper.updateShiftConfirm(existing);
            confirmedCount++;
        }

        return confirmedCount;
    }

    /**
     * シフト予定 vs 実績打刻 リアルタイム対照エンジン
     * 指定店舗・指定日の確定シフトと実際の打刻実績を照合し、遅刻・早退・欠勤・正常を判定する
     */
    public List<ShiftComparisonDto> compareShiftWithAttendance(Long storeId, LocalDate workDate) {
        if (storeId == null) {
            throw new IllegalArgumentException("店舗IDは必須です。");
        }
        LocalDate targetDate = (workDate != null) ? workDate : LocalDate.now();

        // 1. 指定日の確定シフト一覧取得
        List<ShiftDto> confirmedShifts = shiftMapper.findConfirmedShiftsByDate(storeId, targetDate);

        // 2. 指定日の打刻実績一覧取得
        List<AttendanceDto> actualAttendances = attendanceMapper.findAttendancesByStoreAndDate(storeId, targetDate);

        // スタッフIDをキーとする打刻マップ作成
        Map<Long, AttendanceDto> attendanceMap = actualAttendances.stream()
                .collect(Collectors.toMap(AttendanceDto::getStoreStaffId, a -> a, (a1, a2) -> a1));

        List<ShiftComparisonDto> comparisonList = new ArrayList<>();
        LocalDateTime now = LocalDateTime.now();

        // 3. 確定シフトを基準に対照判定
        for (ShiftDto shift : confirmedShifts) {
            if (shift.getConfirmedStartAt() == null || shift.getConfirmedEndAt() == null) {
                continue; // 確定時間が未設定のデータは防御的にスキップ
            }
            ShiftComparisonDto dto = new ShiftComparisonDto();
            dto.setShiftId(shift.getShiftId());
            dto.setStoreStaffId(shift.getStoreStaffId());
            dto.setStaffName(shift.getStaffName());
            dto.setWorkDate(shift.getWorkDate());
            dto.setPlannedStartAt(shift.getConfirmedStartAt());
            dto.setPlannedEndAt(shift.getConfirmedEndAt());
            dto.setPlannedBreakMinutes(shift.getPlannedBreakMinutes() != null ? shift.getPlannedBreakMinutes() : 0);

            // 実働予定時間（分）
            long grossPlannedMinutes = Duration.between(shift.getConfirmedStartAt(), shift.getConfirmedEndAt()).toMinutes();
            dto.setPlannedWorkMinutes(Math.max(0, grossPlannedMinutes - dto.getPlannedBreakMinutes()));

            AttendanceDto attendance = attendanceMap.remove(shift.getStoreStaffId());

            if (attendance != null) {
                dto.setAttendanceId(attendance.getAttendanceId());
                dto.setActualClockInAt(attendance.getClockInAt());
                dto.setActualClockOutAt(attendance.getClockOutAt());
                dto.setActualBreakMinutes(attendance.getTotalBreakMinutes() != null ? attendance.getTotalBreakMinutes() : 0);
                dto.setActualWorkMinutes(attendance.getNetWorkMinutes() != null ? (long) attendance.getNetWorkMinutes() : 0L);

                // 実働差異（実績 - 予定）
                dto.setDiffWorkMinutes(dto.getActualWorkMinutes() - dto.getPlannedWorkMinutes());

                // 遅刻判定（予定時刻より5分以上超過で出勤）
                long lateMinutes = 0;
                boolean isLate = false;
                if (attendance.getClockInAt() != null && attendance.getClockInAt().isAfter(shift.getConfirmedStartAt().plusMinutes(5))) {
                    lateMinutes = Duration.between(shift.getConfirmedStartAt(), attendance.getClockInAt()).toMinutes();
                    isLate = true;
                }
                dto.setLateMinutes(lateMinutes);

                // 早退判定（退勤済かつ予定終了時刻より5分以上前）
                long earlyLeaveMinutes = 0;
                boolean isEarlyLeave = false;
                if (attendance.getClockOutAt() != null && attendance.getClockOutAt().isBefore(shift.getConfirmedEndAt().minusMinutes(5))) {
                    earlyLeaveMinutes = Duration.between(attendance.getClockOutAt(), shift.getConfirmedEndAt()).toMinutes();
                    isEarlyLeave = true;
                }
                dto.setEarlyLeaveMinutes(earlyLeaveMinutes);

                // 総合ステータス判定
                if (attendance.getClockOutAt() == null) {
                    dto.setComparisonStatus("WORKING");
                    dto.setStatusLabel(isLate ? "勤務中 (遅刻 " + lateMinutes + "分)" : "勤務中 (正常)");
                } else if (isLate && isEarlyLeave) {
                    dto.setComparisonStatus("LATE_AND_EARLY");
                    dto.setStatusLabel("遅刻・早退 (遅刻 " + lateMinutes + "分 / 早退 " + earlyLeaveMinutes + "分)");
                } else if (isLate) {
                    dto.setComparisonStatus("LATE");
                    dto.setStatusLabel("遅刻 (" + lateMinutes + "分)");
                } else if (isEarlyLeave) {
                    dto.setComparisonStatus("EARLY_LEAVE");
                    dto.setStatusLabel("早退 (" + earlyLeaveMinutes + "分)");
                } else {
                    dto.setComparisonStatus("NORMAL");
                    dto.setStatusLabel("正常 (予定通り)");
                }

            } else {
                // 打刻なしのケース（欠勤判定）
                dto.setActualBreakMinutes(0);
                dto.setActualWorkMinutes(0L);
                dto.setLateMinutes(0L);
                dto.setEarlyLeaveMinutes(0L);
                dto.setDiffWorkMinutes(-dto.getPlannedWorkMinutes());

                if (now.isAfter(shift.getConfirmedStartAt().plusMinutes(30))) {
                    dto.setComparisonStatus("ABSENT");
                    dto.setStatusLabel("欠勤 (未出勤)");
                } else {
                    dto.setComparisonStatus("PENDING");
                    dto.setStatusLabel("出勤待ち");
                }
            }

            comparisonList.add(dto);
        }

        // 4. シフト予定がないにもかかわらず打刻があるスタッフ（予定外勤務）
        for (AttendanceDto extra : attendanceMap.values()) {
            ShiftComparisonDto dto = new ShiftComparisonDto();
            dto.setStoreStaffId(extra.getStoreStaffId());
            dto.setWorkDate(extra.getWorkDate());
            dto.setAttendanceId(extra.getAttendanceId());
            dto.setActualClockInAt(extra.getClockInAt());
            dto.setActualClockOutAt(extra.getClockOutAt());
            dto.setActualBreakMinutes(extra.getTotalBreakMinutes() != null ? extra.getTotalBreakMinutes() : 0);
            dto.setActualWorkMinutes(extra.getNetWorkMinutes() != null ? (long) extra.getNetWorkMinutes() : 0L);
            dto.setPlannedBreakMinutes(0);
            dto.setPlannedWorkMinutes(0L);
            dto.setDiffWorkMinutes(dto.getActualWorkMinutes());
            dto.setLateMinutes(0L);
            dto.setEarlyLeaveMinutes(0L);
            dto.setComparisonStatus("UNSCHEDULED");
            dto.setStatusLabel("予定外勤務 (シフト未登録)");
            comparisonList.add(dto);
        }

        return comparisonList;
    }
}
```

## FILE: src\main\java\com\example\kintai\domain\shift\mapper\ShiftMapper.java
```
package com.example.kintai.domain.shift.mapper;

import com.example.kintai.domain.shift.dto.ShiftDto;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface ShiftMapper {

    List<ShiftDto> findShiftEvents(
            @Param("storeId") Long storeId,
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate
    );

    ShiftDto findById(@Param("shiftId") Long shiftId);

    int insertShift(ShiftDto shift);

    int updateShiftConfirm(ShiftDto shift);

    boolean existsOverlapShift(
            @Param("storeStaffId") Long storeStaffId,
            @Param("workDate") LocalDate workDate,
            @Param("startTime") LocalDateTime startTime,
            @Param("endTime") LocalDateTime endTime,
            @Param("excludeShiftId") Long excludeShiftId
    );

    // 排他制御: 同時更新防止のための悲観的ロック (SELECT ... FOR UPDATE)
    void lockStoreStaffForUpdate(@Param("storeStaffId") Long storeStaffId);

    /**
     * スタッフ本人による希望シフト申請の取消・削除 (IDOR防止および確定済削除禁止)
     */
    int deleteShiftRequest(
            @Param("shiftId") Long shiftId,
            @Param("storeStaffId") Long storeStaffId
    );

    /**
     * 特定スタッフのシフト一覧取得 (自店舗・自身のシフト確認用)
     */
    List<ShiftDto> findStaffShiftList(
            @Param("storeStaffId") Long storeStaffId,
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate
    );

    /**
     * 店舗内の申請中（未確定）シフト一覧取得
     */
    List<ShiftDto> findPendingShifts(
            @Param("storeId") Long storeId,
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate
    );

    /**
     * 店舗内の未確定シフト件数カウント
     */
    int countPendingShifts(@Param("storeId") Long storeId);

    /**
     * 特定勤務日の確定シフト一覧取得（実績打刻照合エンジン用）
     */
    List<ShiftDto> findConfirmedShiftsByDate(
            @Param("storeId") Long storeId,
            @Param("workDate") LocalDate workDate
    );

    /**
     * シフト状態の単件更新（店舗IDOR防御付き却下・承認処理）
     */
    int updateShiftStatus(
            @Param("shiftId") Long shiftId,
            @Param("storeId") Long storeId,
            @Param("status") String status,
            @Param("ownerMemo") String ownerMemo
    );

    /**
     * 複数シフトの一括確定処理 (申請時間を確定時間として一括昇格)
     */
    int batchConfirmShifts(
            @Param("storeId") Long storeId,
            @Param("shiftIds") List<Long> shiftIds,
            @Param("ownerMemo") String ownerMemo
    );
}
```

## FILE: src\main\resources\mapper\shift\ShiftMapper.xml
```
<?xml version="1.0" encoding="UTF-8" ?>
<!DOCTYPE mapper PUBLIC "-//mybatis.org//DTD Mapper 3.0//EN"
        "http://mybatis.org/dtd/mybatis-3-mapper.dtd">

<mapper namespace="com.example.kintai.domain.shift.mapper.ShiftMapper">

    <select id="findShiftEvents" resultType="com.example.kintai.domain.shift.dto.ShiftDto">
        SELECT
            s.shift_id,
            s.store_id,
            s.store_staff_id,
            m.name AS staff_name,
            s.work_date,
            s.requested_start_at,
            s.requested_end_at,
            s.confirmed_start_at,
            s.confirmed_end_at,
            s.planned_break_minutes,
            s.status,
            s.staff_memo,
            s.owner_memo
        FROM shift_schedule s
        JOIN store_staff ss ON s.store_staff_id = ss.store_staff_id
        JOIN member m ON ss.member_id = m.member_id
        WHERE s.store_id = #{storeId}
          AND s.work_date &gt;= #{startDate}
          AND s.work_date &lt; #{endDate}
        ORDER BY s.work_date ASC, COALESCE(s.confirmed_start_at, s.requested_start_at) ASC
    </select>

    <select id="findById" resultType="com.example.kintai.domain.shift.dto.ShiftDto">
        SELECT
            s.shift_id,
            s.store_id,
            s.store_staff_id,
            m.name AS staff_name,
            s.work_date,
            s.requested_start_at,
            s.requested_end_at,
            s.confirmed_start_at,
            s.confirmed_end_at,
            s.planned_break_minutes,
            s.status,
            s.staff_memo,
            s.owner_memo
        FROM shift_schedule s
        JOIN store_staff ss ON s.store_staff_id = ss.store_staff_id
        JOIN member m ON ss.member_id = m.member_id
        WHERE s.shift_id = #{shiftId}
    </select>

    <insert id="insertShift" useGeneratedKeys="true" keyProperty="shiftId">
        INSERT INTO shift_schedule (
            store_id,
            store_staff_id,
            work_date,
            requested_start_at,
            requested_end_at,
            confirmed_start_at,
            confirmed_end_at,
            planned_break_minutes,
            status,
            staff_memo,
            owner_memo
        ) VALUES (
            #{storeId},
            #{storeStaffId},
            #{workDate},
            #{requestedStartAt},
            #{requestedEndAt},
            #{confirmedStartAt},
            #{confirmedEndAt},
            #{plannedBreakMinutes},
            #{status},
            #{staffMemo},
            #{ownerMemo}
        )
    </insert>

    <!-- シフト確定・却下更新 (CAS: SUBMITTED状態のシフトのみ1件更新) -->
    <update id="updateShiftConfirm">
        UPDATE shift_schedule
        SET confirmed_start_at = #{confirmedStartAt},
            confirmed_end_at = #{confirmedEndAt},
            planned_break_minutes = #{plannedBreakMinutes},
            status = #{status},
            owner_memo = #{ownerMemo},
            updated_at = CURRENT_TIMESTAMP
        WHERE shift_id = #{shiftId}
          AND status = 'SUBMITTED'
    </update>

    <select id="existsOverlapShift" resultType="boolean">
        SELECT COUNT(*) &gt; 0
        FROM shift_schedule
        WHERE store_staff_id = #{storeStaffId}
          AND work_date = #{workDate}
          AND status != 'REJECTED'
          <if test="excludeShiftId != null">
              AND shift_id != #{excludeShiftId}
          </if>
          AND (
              (confirmed_start_at IS NOT NULL AND confirmed_start_at &lt; #{endTime} AND confirmed_end_at &gt; #{startTime})
              OR
              (confirmed_start_at IS NULL AND requested_start_at &lt; #{endTime} AND requested_end_at &gt; #{startTime})
          )
    </select>

    <select id="lockStoreStaffForUpdate">
        SELECT store_staff_id
        FROM store_staff
        WHERE store_staff_id = #{storeStaffId}
        FOR UPDATE
    </select>

    <delete id="deleteShiftRequest">
        DELETE FROM shift_schedule
        WHERE shift_id = #{shiftId}
          AND store_staff_id = #{storeStaffId}
          AND status = 'SUBMITTED'
    </delete>

    <select id="findStaffShiftList" resultType="com.example.kintai.domain.shift.dto.ShiftDto">
        SELECT
            s.shift_id,
            s.store_id,
            s.store_staff_id,
            m.name AS staff_name,
            s.work_date,
            s.requested_start_at,
            s.requested_end_at,
            s.confirmed_start_at,
            s.confirmed_end_at,
            s.planned_break_minutes,
            s.status,
            s.staff_memo,
            s.owner_memo
        FROM shift_schedule s
        JOIN store_staff ss ON s.store_staff_id = ss.store_staff_id
        JOIN member m ON ss.member_id = m.member_id
        WHERE s.store_staff_id = #{storeStaffId}
          AND s.work_date &gt;= #{startDate}
          AND s.work_date &lt; #{endDate}
        ORDER BY s.work_date ASC, COALESCE(s.confirmed_start_at, s.requested_start_at) ASC
    </select>

    <select id="findPendingShifts" resultType="com.example.kintai.domain.shift.dto.ShiftDto">
        SELECT
            s.shift_id,
            s.store_id,
            s.store_staff_id,
            m.name AS staff_name,
            s.work_date,
            s.requested_start_at,
            s.requested_end_at,
            s.confirmed_start_at,
            s.confirmed_end_at,
            s.planned_break_minutes,
            s.status,
            s.staff_memo,
            s.owner_memo
        FROM shift_schedule s
        JOIN store_staff ss ON s.store_staff_id = ss.store_staff_id
        JOIN member m ON ss.member_id = m.member_id
        WHERE s.store_id = #{storeId}
          AND s.status = 'SUBMITTED'
          <if test="startDate != null">
              AND s.work_date &gt;= #{startDate}
          </if>
          <if test="endDate != null">
              AND s.work_date &lt; #{endDate}
          </if>
        ORDER BY s.work_date ASC, s.requested_start_at ASC
    </select>

    <select id="countPendingShifts" resultType="int">
        SELECT COUNT(*)
        FROM shift_schedule
        WHERE store_id = #{storeId}
          AND status = 'SUBMITTED'
    </select>

    <select id="findConfirmedShiftsByDate" resultType="com.example.kintai.domain.shift.dto.ShiftDto">
        SELECT
            s.shift_id,
            s.store_id,
            s.store_staff_id,
            m.name AS staff_name,
            s.work_date,
            s.requested_start_at,
            s.requested_end_at,
            s.confirmed_start_at,
            s.confirmed_end_at,
            s.planned_break_minutes,
            s.status,
            s.staff_memo,
            s.owner_memo
        FROM shift_schedule s
        JOIN store_staff ss ON s.store_staff_id = ss.store_staff_id
        JOIN member m ON ss.member_id = m.member_id
        WHERE s.store_id = #{storeId}
          AND s.work_date = #{workDate}
          AND s.status = 'CONFIRMED'
        ORDER BY s.confirmed_start_at ASC
    </select>

    <update id="updateShiftStatus">
        UPDATE shift_schedule
        SET status = #{status},
            owner_memo = #{ownerMemo},
            updated_at = CURRENT_TIMESTAMP
        WHERE shift_id = #{shiftId}
          AND store_id = #{storeId}
    </update>

    <update id="batchConfirmShifts">
        UPDATE shift_schedule
        SET confirmed_start_at = COALESCE(confirmed_start_at, requested_start_at),
            confirmed_end_at = COALESCE(confirmed_end_at, requested_end_at),
            status = 'CONFIRMED',
            owner_memo = COALESCE(#{ownerMemo}, owner_memo),
            updated_at = CURRENT_TIMESTAMP
        WHERE store_id = #{storeId}
          AND status = 'SUBMITTED'
          AND shift_id IN
          <foreach item="item" collection="shiftIds" open="(" separator="," close=")">
              #{item}
          </foreach>
    </update>

</mapper>
```

## FILE: src\main\java\com\example\kintai\domain\shift\dto\CalendarEventDto.java
```
package com.example.kintai.domain.shift.dto;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public class CalendarEventDto {
    private String id;
    private String title;
    private String start;
    private String end;
    private String color;
    private ExtendedProps extendedProps;

    public CalendarEventDto() {}

    public static class ExtendedProps {
        private Long shiftId;
        private Long storeStaffId;
        private String staffName;
        private String status;
        private Integer plannedBreakMinutes;
        private String staffMemo;
        private String ownerMemo;

        public ExtendedProps() {}

        public Long getShiftId() { return shiftId; }
        public void setShiftId(Long shiftId) { this.shiftId = shiftId; }
        public Long getStoreStaffId() { return storeStaffId; }
        public void setStoreStaffId(Long storeStaffId) { this.storeStaffId = storeStaffId; }
        public String getStaffName() { return staffName; }
        public void setStaffName(String staffName) { this.staffName = staffName; }
        public String getStatus() { return status; }
        public void setStatus(String status) { this.status = status; }
        public Integer getPlannedBreakMinutes() { return plannedBreakMinutes; }
        public void setPlannedBreakMinutes(Integer plannedBreakMinutes) { this.plannedBreakMinutes = plannedBreakMinutes; }
        public String getStaffMemo() { return staffMemo; }
        public void setStaffMemo(String staffMemo) { this.staffMemo = staffMemo; }
        public String getOwnerMemo() { return ownerMemo; }
        public void setOwnerMemo(String ownerMemo) { this.ownerMemo = ownerMemo; }
    }

    public static CalendarEventDto from(ShiftDto shift) {
        return from(shift, null, true);
    }

    /**
     * プライバシー保護・サーバーサイドマスキング対応
     * 他スタッフの個人メモは管理者(ROLE_OWNER/ROLE_MANAGER)または本人のみに限定して返却
     */
    public static CalendarEventDto from(ShiftDto shift, Long currentStaffId, boolean isManager) {
        CalendarEventDto dto = new CalendarEventDto();
        dto.setId(String.valueOf(shift.getShiftId()));

        DateTimeFormatter isoFormatter = DateTimeFormatter.ISO_LOCAL_DATE_TIME;
        DateTimeFormatter timeFormatter = DateTimeFormatter.ofPattern("HH:mm");

        String status = shift.getStatus();
        LocalDateTime eventStart;
        LocalDateTime eventEnd;

        // オーナー直接割り当てシフト（requested_*がNULL）の場合でもイベント区間がNULLにならないよう防衛
        if ("CONFIRMED".equalsIgnoreCase(status)) {
            eventStart = shift.getConfirmedStartAt() != null ? shift.getConfirmedStartAt() : shift.getRequestedStartAt();
            eventEnd = shift.getConfirmedEndAt() != null ? shift.getConfirmedEndAt() : shift.getRequestedEndAt();
            dto.setColor("#0d6efd"); // 青色（確定）
            String timeRange = (eventStart != null && eventEnd != null) 
                    ? " (" + eventStart.format(timeFormatter) + " - " + eventEnd.format(timeFormatter) + ")" 
                    : "";
            dto.setTitle("[確定] " + shift.getStaffName() + timeRange);
        } else if ("SUBMITTED".equalsIgnoreCase(status)) {
            eventStart = shift.getRequestedStartAt();
            eventEnd = shift.getRequestedEndAt();
            dto.setColor("#fd7e14"); // オレンジ色（希望）
            String timeRange = (eventStart != null && eventEnd != null) 
                    ? " (" + eventStart.format(timeFormatter) + " - " + eventEnd.format(timeFormatter) + ")" 
                    : "";
            dto.setTitle("[希望] " + shift.getStaffName() + timeRange);
        } else { // REJECTED など
            eventStart = shift.getRequestedStartAt() != null ? shift.getRequestedStartAt() : shift.getConfirmedStartAt();
            eventEnd = shift.getRequestedEndAt() != null ? shift.getRequestedEndAt() : shift.getConfirmedEndAt();
            dto.setColor("#6c757d"); // グレー
            dto.setTitle("[未割当] " + shift.getStaffName());
        }

        if (eventStart != null) dto.setStart(eventStart.format(isoFormatter));
        if (eventEnd != null) dto.setEnd(eventEnd.format(isoFormatter));

        ExtendedProps props = new ExtendedProps();
        props.setShiftId(shift.getShiftId());
        props.setStoreStaffId(shift.getStoreStaffId());
        props.setStaffName(shift.getStaffName());
        props.setStatus(shift.getStatus());
        props.setPlannedBreakMinutes(shift.getPlannedBreakMinutes());

        // サーバーサイド・プライバシー保護マスク
        boolean isSelf = (currentStaffId != null && currentStaffId.equals(shift.getStoreStaffId()));
        if (isManager || isSelf) {
            props.setStaffMemo(shift.getStaffMemo());
            props.setOwnerMemo(shift.getOwnerMemo());
        } else {
            props.setStaffMemo(null); // 他スタッフのメモはAPIペイロードから完全除外
            props.setOwnerMemo(null);
        }

        dto.setExtendedProps(props);
        return dto;
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getStart() { return start; }
    public void setStart(String start) { this.start = start; }
    public String getEnd() { return end; }
    public void setEnd(String end) { this.end = end; }
    public String getColor() { return color; }
    public void setColor(String color) { this.color = color; }
    public ExtendedProps getExtendedProps() { return extendedProps; }
    public void setExtendedProps(ExtendedProps extendedProps) { this.extendedProps = extendedProps; }
}
```

## FILE: src\main\resources\templates\shift\calendar.html
```
<!DOCTYPE html>
<html lang="ja" xmlns:th="http://www.thymeleaf.org"
      xmlns:sec="http://www.thymeleaf.org/extras/spring-security"
      th:replace="~{common/layout :: layout(~{::title}, ~{::#content})}">
<head>
    <title>シフト管理・希望提出 | KINTAI SYSTEM</title>
</head>
<body>
<div id="content">
    <!-- CSRF トークン保持用 -->
    <meta name="_csrf" th:content="${_csrf?.token}"/>
    <meta name="_csrf_header" th:content="${_csrf?.headerName}"/>

    <div class="d-flex justify-content-between align-items-center mb-3">
        <div>
            <h3 class="fw-bold mb-1"><i class="fa-regular fa-calendar-days text-primary me-2"></i>シフト管理・希望提出</h3>
            <p class="text-muted small mb-0">カレンダーの日付をクリックして希望シフトを登録、または登録済みシフトをクリックして詳細・確定を行います。</p>
        </div>
        <div class="d-flex align-items-center gap-2">
            <span class="badge bg-success"><i class="fa-solid fa-check me-1"></i>確定 (CONFIRMED)</span>
            <span class="badge bg-warning text-dark"><i class="fa-solid fa-clock me-1"></i>申請中 (SUBMITTED)</span>
            <span class="badge bg-secondary"><i class="fa-solid fa-ban me-1"></i>却下 (REJECTED)</span>
        </div>
    </div>

    <!-- カレンダー本体カード -->
    <div class="card shadow-sm border-0 mb-4">
        <div class="card-body p-3">
            <div id="calendar"></div>
        </div>
    </div>

    <!-- 1. アルバイト用: 希望シフト提出モーダル -->
    <div class="modal fade" id="shiftRequestModal" tabindex="-1" aria-labelledby="shiftRequestModalLabel" aria-hidden="true">
        <div class="modal-dialog">
            <div class="modal-content">
                <div class="modal-header bg-primary text-white">
                    <h5 class="modal-title fw-bold" id="shiftRequestModalLabel"><i class="fa-solid fa-calendar-plus me-2"></i>希望シフト登録</h5>
                    <button type="button" class="btn-close btn-close-white" data-bs-dismiss="modal" aria-label="閉じる"></button>
                </div>
                <div class="modal-body">
                    <form id="shiftRequestForm">
                        <div class="mb-3">
                            <label class="form-label fw-bold">勤務予定日</label>
                            <input type="date" class="form-control" id="reqWorkDate" readonly required>
                        </div>
                        <div class="row">
                            <div class="col-6 mb-3">
                                <label class="form-label fw-bold">希望出勤時刻</label>
                                <input type="time" class="form-control" id="reqStartTime" value="09:00" required>
                            </div>
                            <div class="col-6 mb-3">
                                <label class="form-label fw-bold">希望退勤時刻</label>
                                <input type="time" class="form-control" id="reqEndTime" value="18:00" required>
                            </div>
                        </div>
                        <div class="mb-3">
                            <label class="form-label fw-bold">スタッフ備考・連絡事項 (任意)</label>
                            <textarea class="form-control" id="reqStaffMemo" rows="2" placeholder="例: 授業のため15分前後する可能性があります"></textarea>
                        </div>
                        <div class="alert alert-info py-2 small mb-0">
                            <i class="fa-solid fa-circle-info me-1"></i>提出された希望シフトは店長による確認・確定を経て確定シフトとなります。
                        </div>
                    </form>
                </div>
                <div class="modal-footer">
                    <button type="button" class="btn btn-secondary" data-bs-dismiss="modal">キャンセル</button>
                    <button type="button" class="btn btn-primary" id="btnSubmitShiftRequest">
                        <i class="fa-solid fa-paper-plane me-1"></i>希望提出
                    </button>
                </div>
            </div>
        </div>
    </div>

    <!-- 2. スタッフ用: 自身の希望シフト詳細・取消モーダル -->
    <div class="modal fade" id="shiftDetailModal" tabindex="-1" aria-labelledby="shiftDetailModalLabel" aria-hidden="true">
        <div class="modal-dialog">
            <div class="modal-content">
                <div class="modal-header">
                    <h5 class="modal-title fw-bold" id="shiftDetailModalLabel"><i class="fa-solid fa-calendar-check me-2"></i>シフト詳細</h5>
                    <button type="button" class="btn-close" data-bs-dismiss="modal" aria-label="閉じる"></button>
                </div>
                <div class="modal-body">
                    <input type="hidden" id="detailShiftId">
                    <table class="table table-bordered mb-3">
                        <tbody>
                            <tr>
                                <th class="table-light w-25">スタッフ</th>
                                <td id="detailStaffName">-</td>
                            </tr>
                            <tr>
                                <th class="table-light">勤務日</th>
                                <td id="detailWorkDate">-</td>
                            </tr>
                            <tr>
                                <th class="table-light">時間帯</th>
                                <td id="detailTimeRange">-</td>
                            </tr>
                            <tr>
                                <th class="table-light">ステータス</th>
                                <td id="detailStatus">-</td>
                            </tr>
                            <tr>
                                <th class="table-light">備考</th>
                                <td id="detailMemo">-</td>
                            </tr>
                        </tbody>
                    </table>
                    <div id="detailAlertMessage" class="alert alert-warning py-2 small mb-0 d-none"></div>
                </div>
                <div class="modal-footer d-flex justify-content-between">
                    <div>
                        <button type="button" class="btn btn-outline-danger d-none" id="btnDeleteShiftRequest">
                            <i class="fa-solid fa-trash me-1"></i>申請取消 (削除)
                        </button>
                    </div>
                    <button type="button" class="btn btn-secondary" data-bs-dismiss="modal">閉じる</button>
                </div>
            </div>
        </div>
    </div>

    <!-- 3. 店長用: シフト確定・調整モーダル -->
    <div class="modal fade" id="shiftConfirmModal" tabindex="-1" aria-labelledby="shiftConfirmModalLabel" aria-hidden="true">
        <div class="modal-dialog">
            <div class="modal-content">
                <div class="modal-header bg-success text-white">
                    <h5 class="modal-title fw-bold" id="shiftConfirmModalLabel"><i class="fa-solid fa-clipboard-check me-2"></i>シフト確定 / 調整 (店長管理)</h5>
                    <button type="button" class="btn-close btn-close-white" data-bs-dismiss="modal" aria-label="閉じる"></button>
                </div>
                <div class="modal-body">
                    <form id="shiftConfirmForm">
                        <input type="hidden" id="confShiftId">
                        <div class="mb-3">
                            <label class="form-label fw-bold">スタッフ名</label>
                            <input type="text" class="form-control-plaintext fw-bold" id="confStaffName" readonly>
                        </div>
                        <div class="mb-3">
                            <label class="form-label fw-bold">勤務日</label>
                            <input type="date" class="form-control" id="confWorkDate" readonly>
                        </div>
                        <div class="row">
                            <div class="col-6 mb-3">
                                <label class="form-label fw-bold">確定出勤時刻</label>
                                <input type="time" class="form-control" id="confStartTime" required>
                            </div>
                            <div class="col-6 mb-3">
                                <label class="form-label fw-bold">確定退勤時刻</label>
                                <input type="time" class="form-control" id="confEndTime" required>
                            </div>
                        </div>
                        <div class="mb-3">
                            <label class="form-label fw-bold">計画休憩時間 (分)</label>
                            <input type="number" class="form-control" id="confBreakMinutes" min="0" step="15" required>
                            <div class="form-text">6時間超45分以上、8時間超60分以上の法定休憩が推奨されます。</div>
                        </div>
                        <div class="mb-3">
                            <label class="form-label fw-bold">ステータス</label>
                            <select class="form-select" id="confStatus">
                                <option value="CONFIRMED">確定 (CONFIRMED)</option>
                                <option value="REJECTED">却下 (REJECTED)</option>
                            </select>
                        </div>
                        <div class="mb-3">
                            <label class="form-label fw-bold">店長メモ</label>
                            <textarea class="form-control" id="confOwnerMemo" rows="2" placeholder="調整理由などを記入"></textarea>
                        </div>
                    </form>
                </div>
                <div class="modal-footer">
                    <button type="button" class="btn btn-secondary" data-bs-dismiss="modal">閉じる</button>
                    <button type="button" class="btn btn-success" id="btnSubmitShiftConfirm">
                        <i class="fa-solid fa-check me-1"></i>確定保存
                    </button>
                </div>
            </div>
        </div>
    </div>

    <!-- FullCalendar初期化 & イベント処理スクリプト -->
    <script th:inline="javascript">
        document.addEventListener('DOMContentLoaded', function () {
            const storeId = /*[[${storeId}]]*/ 1;
            const isManager = /*[[${#authorization.expression('hasAnyRole(''OWNER'', ''MANAGER'')')}]]*/ false;
            const currentStaffId = /*[[${#authentication?.principal?.storeStaffId}]]*/ null;

            const calendarEl = document.getElementById('calendar');
            const reqModal = new bootstrap.Modal(document.getElementById('shiftRequestModal'));
            const detailModal = new bootstrap.Modal(document.getElementById('shiftDetailModal'));
            const confModal = new bootstrap.Modal(document.getElementById('shiftConfirmModal'));

            // CSRF トークン取得
            const csrfTokenMeta = document.querySelector('meta[name="_csrf"]');
            const csrfHeaderMeta = document.querySelector('meta[name="_csrf_header"]');
            const csrfToken = csrfTokenMeta ? csrfTokenMeta.getAttribute('content') : '';
            const csrfHeader = csrfHeaderMeta ? csrfHeaderMeta.getAttribute('content') : 'X-CSRF-TOKEN';

            const headers = {
                'Content-Type': 'application/json'
            };
            if (csrfToken) {
                headers[csrfHeader] = csrfToken;
            }

            const calendar = new FullCalendar.Calendar(calendarEl, {
                initialView: 'dayGridMonth',
                locale: 'ja',
                headerToolbar: {
                    left: 'prev,next today',
                    center: 'title',
                    right: 'dayGridMonth,timeGridWeek,timeGridDay'
                },
                navLinks: true,
                editable: false,
                selectable: true,
                events: function (info, successCallback, failureCallback) {
                    const startStr = info.startStr.substring(0, 10);
                    const endStr = info.endStr.substring(0, 10);
                    fetch(`/api/shifts/events?storeId=${storeId}&start=${startStr}&end=${endStr}`)
                        .then(res => {
                            if (!res.ok) throw new Error('シフトデータの取得に失敗しました。');
                            return res.json();
                        })
                        .then(data => {
                            const events = data.map(ev => {
                                let bg = '#ffc107'; // SUBMITTED
                                let border = '#e0a800';
                                let text = '#000000';
                                const status = ev.extendedProps.status;
                                if (status === 'CONFIRMED') {
                                    bg = '#198754';
                                    border = '#157347';
                                    text = '#ffffff';
                                } else if (status === 'REJECTED' || status === 'CANCELED') {
                                    bg = '#6c757d';
                                    border = '#5a6268';
                                    text = '#ffffff';
                                }
                                return {
                                    ...ev,
                                    backgroundColor: bg,
                                    borderColor: border,
                                    textColor: text
                                };
                            });
                            successCallback(events);
                        })
                        .catch(err => {
                            console.error(err);
                            failureCallback(err);
                        });
                },
                dateClick: function (info) {
                    // JST(日本標準時)基準での過去日チェック (UTCズレ防止)
                    const now = new Date();
                    const year = now.getFullYear();
                    const month = String(now.getMonth() + 1).padStart(2, '0');
                    const day = String(now.getDate()).padStart(2, '0');
                    const todayStr = `${year}-${month}-${day}`;

                    if (info.dateStr < todayStr) {
                        alert('過去の日付に対する希望シフトは申請できません。');
                        return;
                    }
                    document.getElementById('reqWorkDate').value = info.dateStr;
                    document.getElementById('reqStartTime').value = '09:00';
                    document.getElementById('reqEndTime').value = '18:00';
                    document.getElementById('reqStaffMemo').value = '';
                    reqModal.show();
                },
                eventClick: function (info) {
                    const ev = info.event.extendedProps;
                    const isOwnShift = currentStaffId && (String(currentStaffId) === String(ev.storeStaffId));
                    const workDate = info.event.startStr.substring(0, 10);
                    const startTime = info.event.startStr.substring(11, 16);
                    const endTime = info.event.endStr.substring(11, 16);

                    if (isManager) {
                        // 店長: 確定・調整モーダル
                        document.getElementById('confShiftId').value = ev.shiftId;
                        document.getElementById('confStaffName').value = ev.staffName;
                        document.getElementById('confWorkDate').value = workDate;
                        document.getElementById('confStartTime').value = startTime;
                        document.getElementById('confEndTime').value = endTime;
                        document.getElementById('confBreakMinutes').value = ev.plannedBreakMinutes || 0;
                        document.getElementById('confStatus').value = (ev.status === 'REJECTED') ? 'REJECTED' : 'CONFIRMED';
                        document.getElementById('confOwnerMemo').value = ev.ownerMemo || '';
                        confModal.show();
                    } else {
                        // スタッフ: 詳細・取消モーダル (他スタッフのプライベートメモ非表示)
                        document.getElementById('detailShiftId').value = ev.shiftId;
                        document.getElementById('detailStaffName').innerText = ev.staffName;
                        document.getElementById('detailWorkDate').innerText = workDate;
                        document.getElementById('detailTimeRange').innerText = `${startTime} 〜 ${endTime}`;
                        
                        const statusBadge = (ev.status === 'CONFIRMED')
                            ? '<span class="badge bg-success">確定 (CONFIRMED)</span>'
                            : (ev.status === 'SUBMITTED')
                                ? '<span class="badge bg-warning text-dark">申請中 (SUBMITTED)</span>'
                                : '<span class="badge bg-secondary">却下 (REJECTED)</span>';
                        document.getElementById('detailStatus').innerHTML = statusBadge;

                        // プライバシー保護: 自身のリクエストのみメモ表示
                        if (isOwnShift) {
                            document.getElementById('detailMemo').innerText = ev.staffMemo || '特になし';
                        } else {
                            document.getElementById('detailMemo').innerText = '非公開 (他スタッフの希望)';
                        }

                        const btnDelete = document.getElementById('btnDeleteShiftRequest');
                        const alertBox = document.getElementById('detailAlertMessage');

                        // 自身の申請かつSUBMITTED状態のみ取消ボタンを表示
                        if (isOwnShift && ev.status === 'SUBMITTED') {
                            btnDelete.classList.remove('d-none');
                            alertBox.classList.add('d-none');
                        } else {
                            btnDelete.classList.add('d-none');
                            if (ev.status === 'CONFIRMED') {
                                alertBox.innerText = '確定済のシフトはスタッフ側からは取消できません。変更が必要な場合は店長へご連絡ください。';
                                alertBox.classList.remove('d-none');
                            } else {
                                alertBox.classList.add('d-none');
                            }
                        }
                        detailModal.show();
                    }
                }
            });

            calendar.render();

            // 1. 希望シフト提出
            document.getElementById('btnSubmitShiftRequest').addEventListener('click', function () {
                const startTime = document.getElementById('reqStartTime').value;
                const endTime = document.getElementById('reqEndTime').value;
                if (!startTime || !endTime) {
                    alert('希望時刻を入力してください。');
                    return;
                }
                if (startTime === endTime) {
                    alert('希望開始時刻と終了時刻を同一にすることはできません。');
                    return;
                }

                const payload = {
                    workDate: document.getElementById('reqWorkDate').value,
                    requestedStartTime: startTime + ':00',
                    requestedEndTime: endTime + ':00',
                    staffMemo: document.getElementById('reqStaffMemo').value
                };

                fetch('/api/shifts/request', {
                    method: 'POST',
                    headers: headers,
                    body: JSON.stringify(payload)
                })
                .then(async res => {
                    const data = await res.json();
                    if (res.ok) {
                        alert(data.message || '希望シフトを登録しました。');
                        reqModal.hide();
                        calendar.refetchEvents();
                    } else {
                        alert('登録エラー: ' + (data.message || 'シフトの登録に失敗しました。'));
                    }
                })
                .catch(err => alert('通信エラーが発生しました: ' + err));
            });

            // 2. スタッフによる希望シフト申請取消
            document.getElementById('btnDeleteShiftRequest').addEventListener('click', function () {
                const shiftId = document.getElementById('detailShiftId').value;
                if (!confirm('この希望シフトの申請を取り消しますか？')) {
                    return;
                }

                fetch(`/api/shifts/${shiftId}`, {
                    method: 'DELETE',
                    headers: headers
                })
                .then(async res => {
                    const data = await res.json();
                    if (res.ok) {
                        alert(data.message || '希望シフトの申請を取り消しました。');
                        detailModal.hide();
                        calendar.refetchEvents();
                    } else {
                        alert('取消エラー: ' + (data.message || '申請の取消に失敗しました。'));
                    }
                })
                .catch(err => alert('通信エラーが発生しました: ' + err));
            });

            // 3. 店長によるシフト確定
            document.getElementById('btnSubmitShiftConfirm').addEventListener('click', function () {
                const shiftId = document.getElementById('confShiftId').value;
                const startTime = document.getElementById('confStartTime').value;
                const endTime = document.getElementById('confEndTime').value;
                if (!startTime || !endTime) {
                    alert('確定時刻を入力してください。');
                    return;
                }
                if (startTime === endTime) {
                    alert('確定開始時刻と終了時刻を同一にすることはできません。');
                    return;
                }

                const breakMinRaw = document.getElementById('confBreakMinutes').value;
                const breakMin = parseInt(breakMinRaw, 10);
                if (isNaN(breakMin) || breakMin < 0) {
                    alert('計画休憩時間は0分以上の整数を入力してください。');
                    return;
                }

                const payload = {
                    confirmedStartTime: startTime + ':00',
                    confirmedEndTime: endTime + ':00',
                    plannedBreakMinutes: breakMin,
                    status: document.getElementById('confStatus').value,
                    ownerMemo: document.getElementById('confOwnerMemo').value
                };

                fetch(`/api/shifts/${shiftId}/confirm`, {
                    method: 'PUT',
                    headers: headers,
                    body: JSON.stringify(payload)
                })
                .then(async res => {
                    const data = await res.json();
                    if (res.ok) {
                        alert(data.message || 'シフトを確定保存しました。');
                        confModal.hide();
                        calendar.refetchEvents();
                    } else {
                        alert('保存エラー: ' + (data.message || 'シフトの保存に失敗しました。'));
                    }
                })
                .catch(err => alert('通信エラーが発生しました: ' + err));
            });
        });
    </script>
</div>
</body>
</html>
```

