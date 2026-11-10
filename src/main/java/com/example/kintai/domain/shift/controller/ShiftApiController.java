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
