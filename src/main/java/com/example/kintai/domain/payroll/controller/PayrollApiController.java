package com.example.kintai.domain.payroll.controller;

import com.example.kintai.domain.payroll.dto.PayrollCalculateRequestDto;
import com.example.kintai.domain.payroll.dto.PayrollConfirmRequestDto;
import com.example.kintai.domain.payroll.dto.PayrollDetailResponseDto;
import com.example.kintai.domain.payroll.dto.PayrollDto;
import com.example.kintai.domain.payroll.service.PayrollService;
import com.example.kintai.global.common.ApiResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;

@RestController
@RequestMapping("/api/payroll")
public class PayrollApiController {

    private final PayrollService payrollService;

    public PayrollApiController(PayrollService payrollService) {
        this.payrollService = payrollService;
    }

    /**
     * 月別給与一覧取得
     */
    @GetMapping("/list")
    public ResponseEntity<ApiResponse<List<PayrollDto>>> getPayrollList(
            @RequestParam("storeId") Long storeId,
            @RequestParam("yearMonth") String yearMonth
    ) {
        List<PayrollDto> list = payrollService.getPayrollsByStoreAndMonth(storeId, yearMonth);
        return ResponseEntity.ok(ApiResponse.ok("給与一覧を取得しました。", list));
    }

    /**
     * 月別給与一括計算 (Upsert)
     */
    @PostMapping("/calculate")
    public ResponseEntity<ApiResponse<Void>> calculateMonthlyPayroll(
            @RequestBody PayrollCalculateRequestDto requestDto
    ) {
        payrollService.calculateMonthlyPayroll(requestDto.getStoreId(), requestDto.getTargetYearMonth());
        return ResponseEntity.ok(ApiResponse.ok("給与計算が完了しました。", null));
    }

    /**
     * 給与確定処理 (帳簿ロック・排他制御適用)
     */
    @PostMapping("/{payrollId}/confirm")
    public ResponseEntity<ApiResponse<Void>> confirmPayroll(
            @PathVariable("payrollId") Long payrollId,
            @RequestBody(required = false) PayrollConfirmRequestDto confirmDto
    ) {
        payrollService.confirmPayroll(payrollId, confirmDto);
        return ResponseEntity.ok(ApiResponse.ok("給与が確定されました。", null));
    }

    /**
     * スタッフ個人別給与明細詳細取得 (日別勤怠および計算内訳)
     */
    @GetMapping("/{payrollId}/detail")
    public ResponseEntity<ApiResponse<PayrollDetailResponseDto>> getPayrollDetail(
            @PathVariable("payrollId") Long payrollId
    ) {
        PayrollDetailResponseDto detail = payrollService.getPayrollDetail(payrollId);
        return ResponseEntity.ok(ApiResponse.ok("給与明細詳細を取得しました。", detail));
    }

    /**
     * 給与台帳 Excel エクスポート
     */
    @GetMapping("/excel")
    public ResponseEntity<byte[]> downloadExcel(
            @RequestParam("storeId") Long storeId,
            @RequestParam("yearMonth") String yearMonth
    ) throws IOException {
        byte[] excelBytes = payrollService.exportPayrollToExcel(storeId, yearMonth);

        String fileName = URLEncoder.encode("給与台帳_" + yearMonth + ".xlsx", StandardCharsets.UTF_8).replace("+", "%20");

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + fileName + "\"; filename*=UTF-8''" + fileName)
                .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .body(excelBytes);
    }
}
