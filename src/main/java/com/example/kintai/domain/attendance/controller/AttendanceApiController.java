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
     * 실시간 출퇴근/휴게 타각 API
     */
    @PostMapping("/punch")
    public ResponseEntity<ApiResponse<AttendanceDto>> punch(@RequestBody AttendancePunchDto punchDto) {
        AttendanceDto result = attendanceService.punch(punchDto);
        return ResponseEntity.ok(ApiResponse.ok("打刻が完了しました。", result));
    }

    /**
     * 현재 진행 중인 근태 조회 API
     */
    @GetMapping("/active")
    public ResponseEntity<ApiResponse<AttendanceDto>> getActiveAttendance(@RequestParam("storeStaffId") Long storeStaffId) {
        AttendanceDto active = attendanceService.getActiveAttendance(storeStaffId);
        return ResponseEntity.ok(ApiResponse.ok(active));
    }

    /**
     * 근태 수정 신청 등록 API (알바생)
     */
    @PostMapping("/mod-request")
    public ResponseEntity<ApiResponse<Long>> requestModification(@RequestBody AttendanceModRequestDto requestDto) {
        Long modRequestId = attendanceService.requestModification(requestDto);
        return ResponseEntity.ok(ApiResponse.ok("勤怠修正申請を受け付けました。", modRequestId));
    }

    /**
     * 근태 수정 신청 목록 조회 API (점주)
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
     * 근태 수정 신청 승인/반려 API (점주)
     */
    @PostMapping("/mod-requests/{modRequestId}/review")
    public ResponseEntity<ApiResponse<Void>> reviewModification(
            @PathVariable("modRequestId") Long modRequestId,
            @RequestBody AttendanceModReviewDto reviewDto
    ) {
        // 개발 모드: 점주 ID 1 임시 적용
        Long reviewerId = 1L;
        attendanceService.reviewModification(modRequestId, reviewerId, reviewDto);
        return ResponseEntity.ok(ApiResponse.ok("審査処理が完了しました。", null));
    }
}
