package com.example.kintai.domain.attendance.controller;

import com.example.kintai.domain.attendance.dto.*;
import com.example.kintai.domain.attendance.service.AttendanceService;
import com.example.kintai.global.common.ApiResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/attendance")
public class AttendanceApiController {

    private final AttendanceService attendanceService;

    public AttendanceApiController(AttendanceService attendanceService) {
        this.attendanceService = attendanceService;
    }

    /**
     * リアルタイム出退勤・休憩打刻 API
     */
    @PostMapping("/punch")
    public ResponseEntity<ApiResponse<AttendanceDto>> punch(@RequestBody AttendancePunchDto punchDto) {
        AttendanceDto result = attendanceService.punch(punchDto);
        return ResponseEntity.ok(ApiResponse.ok("打刻が完了しました。", result));
    }

    /**
     * 現在進行中の勤務実績取得 API
     */
    @GetMapping("/active")
    public ResponseEntity<ApiResponse<AttendanceDto>> getActiveAttendance(@RequestParam("storeStaffId") Long storeStaffId) {
        AttendanceDto active = attendanceService.getActiveAttendance(storeStaffId);
        return ResponseEntity.ok(ApiResponse.ok(active));
    }

    /**
     * 勤怠修正申請登録 API（スタッフ申請）
     */
    @PostMapping("/mod-request")
    public ResponseEntity<ApiResponse<Long>> requestModification(@RequestBody AttendanceModRequestDto requestDto) {
        Long modRequestId = attendanceService.requestModification(requestDto);
        return ResponseEntity.ok(ApiResponse.ok("勤怠修正申請を受け付けました。", modRequestId));
    }

    /**
     * 勤怠修正申請一覧取得 API（店長・管理者用）
     */
    @GetMapping("/mod-requests")
    public ResponseEntity<ApiResponse<List<AttendanceModResponseDto>>> getModRequests(
            @RequestParam("storeId") Long storeId,
            @RequestParam(name = "status", required = false) String status
    ) {
        List<AttendanceModResponseDto> list = attendanceService.getModRequests(storeId, status);
        return ResponseEntity.ok(ApiResponse.ok(list));
    }

    /**
     * 勤怠修正申請の承認・却下審査 API（店長用）
     */
    @PostMapping("/mod-requests/{modRequestId}/review")
    public ResponseEntity<ApiResponse<Void>> reviewModification(
            @PathVariable("modRequestId") Long modRequestId,
            @RequestBody AttendanceModReviewDto reviewDto
    ) {
        // 開発環境用: オーナーID 1 を適用
        Long reviewerId = 1L;
        attendanceService.reviewModification(modRequestId, reviewerId, reviewDto);
        return ResponseEntity.ok(ApiResponse.ok("審査処理が完了しました。", null));
    }
}
