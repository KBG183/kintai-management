package com.example.kintai.domain.payroll.controller;

import com.example.kintai.domain.payroll.dto.PayrollCalculateRequestDto;
import com.example.kintai.domain.payroll.dto.PayrollConfirmRequestDto;
import com.example.kintai.domain.payroll.dto.PayrollDetailResponseDto;
import com.example.kintai.domain.payroll.dto.PayrollDto;
import com.example.kintai.domain.payroll.service.PayrollService;
import com.example.kintai.domain.store.service.StoreAuthorizationService;
import com.example.kintai.global.common.ApiResponse;
import com.example.kintai.global.security.CustomUserDetails;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;

@RestController
@RequestMapping("/api/payroll")
@RequiredArgsConstructor
public class PayrollApiController {

    private final PayrollService payrollService;
    private final StoreAuthorizationService storeAuthorizationService;

    /**
     * 月別給与一覧取得 (オーナー店舗所有権検証必須)
     */
    @GetMapping("/list")
    public ResponseEntity<ApiResponse<List<PayrollDto>>> getPayrollList(
            @RequestParam("storeId") Long storeId,
            @RequestParam("yearMonth") String yearMonth,
            @AuthenticationPrincipal CustomUserDetails userDetails
    ) {
        storeAuthorizationService.validateStoreOwnership(storeId, userDetails.getMemberId());
        List<PayrollDto> list = payrollService.getPayrollsByStoreAndMonth(storeId, yearMonth);
        return ResponseEntity.ok(ApiResponse.ok("給与一覧を取得しました。", list));
    }

    /**
     * 月別給与一括計算 (Upsert, オーナー店舗所有権検証必須)
     */
    @PostMapping("/calculate")
    public ResponseEntity<ApiResponse<Void>> calculateMonthlyPayroll(
            @RequestBody PayrollCalculateRequestDto requestDto,
            @AuthenticationPrincipal CustomUserDetails userDetails
    ) {
        storeAuthorizationService.validateStoreOwnership(requestDto.getStoreId(), userDetails.getMemberId());
        payrollService.calculateMonthlyPayroll(requestDto.getStoreId(), requestDto.getTargetYearMonth());
        return ResponseEntity.ok(ApiResponse.ok("給与計算が完了しました。", null));
    }

    /**
     * 店舗全体の月次給与一括確定 (排他悲観的ロック適用・帳簿ロック, オーナー店舗所有権検証必須)
     */
    @PostMapping("/confirm-month")
    public ResponseEntity<ApiResponse<Integer>> confirmMonthlyPayroll(
            @RequestBody PayrollCalculateRequestDto requestDto,
            @AuthenticationPrincipal CustomUserDetails userDetails
    ) {
        storeAuthorizationService.validateStoreOwnership(requestDto.getStoreId(), userDetails.getMemberId());
        int confirmedCount = payrollService.confirmMonthlyPayroll(requestDto.getStoreId(), requestDto.getTargetYearMonth());
        return ResponseEntity.ok(ApiResponse.ok("月次給与の一括確定が完了しました。(確定件数: " + confirmedCount + "件)", confirmedCount));
    }

    /**
     * 給与確定処理 (帳簿ロック・排他制御適用, 給与データ所有権検証必須)
     */
    @PostMapping("/{payrollId}/confirm")
    public ResponseEntity<ApiResponse<Void>> confirmPayroll(
            @PathVariable("payrollId") Long payrollId,
            @RequestBody(required = false) PayrollConfirmRequestDto confirmDto,
            @AuthenticationPrincipal CustomUserDetails userDetails
    ) {
        storeAuthorizationService.validatePayrollOwnership(payrollId, userDetails.getMemberId());
        payrollService.confirmPayroll(payrollId, confirmDto);
        return ResponseEntity.ok(ApiResponse.ok("給与が確定されました。", null));
    }

    /**
     * スタッフ個人別給与明細詳細取得 (給与データ所有権検証必須)
     */
    @GetMapping("/{payrollId}/detail")
    public ResponseEntity<ApiResponse<PayrollDetailResponseDto>> getPayrollDetail(
            @PathVariable("payrollId") Long payrollId,
            @AuthenticationPrincipal CustomUserDetails userDetails
    ) {
        storeAuthorizationService.validatePayrollOwnership(payrollId, userDetails.getMemberId());
        PayrollDetailResponseDto detail = payrollService.getPayrollDetail(payrollId, userDetails.getMemberId());
        return ResponseEntity.ok(ApiResponse.ok("給与明細詳細を取得しました。", detail));
    }

    /**
     * 給与台帳 Excel エクスポート (オーナー店舗所有権検証必須)
     */
    @GetMapping("/excel")
    public ResponseEntity<byte[]> downloadExcel(
            @RequestParam("storeId") Long storeId,
            @RequestParam("yearMonth") String yearMonth,
            @AuthenticationPrincipal CustomUserDetails userDetails
    ) throws IOException {
        storeAuthorizationService.validateStoreOwnership(storeId, userDetails.getMemberId());
        byte[] excelBytes = payrollService.exportPayrollToExcel(storeId, yearMonth);

        String fileName = URLEncoder.encode("給与台帳_" + yearMonth + ".xlsx", StandardCharsets.UTF_8).replace("+", "%20");

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + fileName + "\"; filename*=UTF-8''" + fileName)
                .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .body(excelBytes);
    }
}
