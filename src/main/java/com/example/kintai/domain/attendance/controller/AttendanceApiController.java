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
                // オーナー（OWNER）代理打刻：対象スタッフがオーナー店舗の所属員であるかを厳格検証
                storeAuthorizationService.validateStaffStoreOwnership(punchDto.getStoreStaffId(), userDetails.getMemberId());
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
            storeAuthorizationService.validateStaffStoreOwnership(requestDto.getStoreStaffId(), userDetails.getMemberId());
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
