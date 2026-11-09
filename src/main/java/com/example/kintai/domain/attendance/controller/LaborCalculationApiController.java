package com.example.kintai.domain.attendance.controller;

import com.example.kintai.domain.attendance.dto.AttendanceDto;
import com.example.kintai.domain.attendance.dto.LaborDetailDto;
import com.example.kintai.domain.attendance.dto.LaborSimulationRequestDto;
import com.example.kintai.domain.attendance.mapper.AttendanceMapper;
import com.example.kintai.domain.attendance.service.LaborCalculationService;
import com.example.kintai.global.common.ApiResponse;
import com.example.kintai.global.security.CustomUserDetails;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

/**
 * 日本労働基準法第37条 労働時間・割増賃金精密算定 REST API コントローラー
 */
@RestController
@RequestMapping("/api/labor")
public class LaborCalculationApiController {

    private final LaborCalculationService laborCalculationService;
    private final AttendanceMapper attendanceMapper;

    public LaborCalculationApiController(
            LaborCalculationService laborCalculationService,
            AttendanceMapper attendanceMapper
    ) {
        this.laborCalculationService = laborCalculationService;
        this.attendanceMapper = attendanceMapper;
    }

    /**
     * 登録済み勤怠実績の労働基準法第37条精密内訳取得 API
     * IDOR（他人の給与・労働時間内訳閲覧）を防止するアクセス制御を適用
     */
    @GetMapping("/attendance/{attendanceId}")
    public ResponseEntity<ApiResponse<LaborDetailDto>> getDetailedLabor(
            @PathVariable("attendanceId") Long attendanceId,
            @AuthenticationPrincipal CustomUserDetails userDetails
    ) {
        AttendanceDto attendance = attendanceMapper.findById(attendanceId);
        if (attendance == null) {
            throw new IllegalArgumentException("指定された勤怠実績が存在しません。(ID: " + attendanceId + ")");
        }

        // IDOR 防護チェック
        if (userDetails != null) {
            if ("ROLE_STAFF".equals(userDetails.getRole())) {
                if (userDetails.getStoreStaffId() != null && !userDetails.getStoreStaffId().equals(attendance.getStoreStaffId())) {
                    throw new AccessDeniedException("他人の勤怠労働法明細を閲覧する権限がありません。");
                }
            } else if ("ROLE_OWNER".equals(userDetails.getRole())) {
                if (userDetails.getStoreId() != null && !userDetails.getStoreId().equals(attendance.getStoreId())) {
                    throw new AccessDeniedException("他店舗のスタッフ勤怠労働法明細を閲覧する権限がありません。");
                }
            }
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
