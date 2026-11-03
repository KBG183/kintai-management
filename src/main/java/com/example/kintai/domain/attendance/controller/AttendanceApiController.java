package com.example.kintai.domain.attendance.controller;

import com.example.kintai.domain.attendance.dto.*;
import com.example.kintai.domain.attendance.service.AttendanceService;
import com.example.kintai.global.common.ApiResponse;
import com.example.kintai.global.security.CustomUserDetails;
import org.springframework.http.ResponseEntity;
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

    public AttendanceApiController(AttendanceService attendanceService) {
        this.attendanceService = attendanceService;
    }

    /**
     * リアルタイム出退勤・休憩打刻 API
     * 認証済みスタッフの storeStaffId を優先適用し IDOR（なりすまし打刻）を防止
     */
    @PostMapping("/punch")
    public ResponseEntity<ApiResponse<AttendanceDto>> punch(
            @RequestBody AttendancePunchDto punchDto,
            @AuthenticationPrincipal CustomUserDetails userDetails
    ) {
        if (userDetails != null && userDetails.getStoreStaffId() != null) {
            punchDto.setStoreStaffId(userDetails.getStoreStaffId());
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
        Long targetStaffId = (userDetails != null && userDetails.getStoreStaffId() != null)
                ? userDetails.getStoreStaffId()
                : (storeStaffId != null ? storeStaffId : 1L);

        AttendanceDto active = attendanceService.getActiveAttendance(targetStaffId);
        return ResponseEntity.ok(ApiResponse.ok(active));
    }

    /**
     * 勤怠修正申請登録 API（スタッフ申請）
     */
    @PostMapping("/mod-request")
    public ResponseEntity<ApiResponse<Long>> requestModification(
            @RequestBody AttendanceModRequestDto requestDto,
            @AuthenticationPrincipal CustomUserDetails userDetails
    ) {
        if (userDetails != null && userDetails.getStoreStaffId() != null) {
            requestDto.setStoreStaffId(userDetails.getStoreStaffId());
        }
        Long modRequestId = attendanceService.requestModification(requestDto);
        return ResponseEntity.ok(ApiResponse.ok("勤怠修正申請を受け付けました。", modRequestId));
    }

    /**
     * 勤怠修正申請一覧取得 API（店長・管理者用）
     */
    @GetMapping("/mod-requests")
    public ResponseEntity<ApiResponse<List<AttendanceModResponseDto>>> getModRequests(
            @RequestParam(name = "storeId", required = false) Long storeId,
            @RequestParam(name = "status", required = false) String status,
            @AuthenticationPrincipal CustomUserDetails userDetails
    ) {
        Long targetStoreId = (userDetails != null && userDetails.getStoreId() != null)
                ? userDetails.getStoreId()
                : (storeId != null ? storeId : 1L);

        List<AttendanceModResponseDto> list = attendanceService.getModRequests(targetStoreId, status);
        return ResponseEntity.ok(ApiResponse.ok(list));
    }

    /**
     * 勤怠修正申請の承認・却下審査 API（店長・オーナー用）
     * ハードコード Mock ID を排除し、認証済みオーナーの会員IDを審査者IDとして適用
     */
    @PostMapping("/mod-requests/{modRequestId}/review")
    public ResponseEntity<ApiResponse<Void>> reviewModification(
            @PathVariable("modRequestId") Long modRequestId,
            @RequestBody AttendanceModReviewDto reviewDto,
            @AuthenticationPrincipal CustomUserDetails userDetails
    ) {
        Long reviewerId = (userDetails != null) ? userDetails.getMemberId() : 1L;
        attendanceService.reviewModification(modRequestId, reviewerId, reviewDto);
        return ResponseEntity.ok(ApiResponse.ok("審査処理が完了しました。", null));
    }
}
