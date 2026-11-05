package com.example.kintai.domain.shift.controller;

import com.example.kintai.domain.shift.dto.CalendarEventDto;
import com.example.kintai.domain.shift.dto.ShiftConfirmDto;
import com.example.kintai.domain.shift.dto.ShiftRequestDto;
import com.example.kintai.domain.shift.service.ShiftService;
import com.example.kintai.global.common.ApiResponse;
import com.example.kintai.global.security.CustomUserDetails;
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
public class ShiftApiController {

    private final ShiftService shiftService;

    public ShiftApiController(ShiftService shiftService) {
        this.shiftService = shiftService;
    }

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
            // 店長・管理者ロールのみ明示的な他店舗参照を許可
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
}
