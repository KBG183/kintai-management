# B-05 / B-06 Audit Bundle (2026-11-14)

## File: src/main/java/com/example/kintai/domain/payroll/controller/PayrollApiController.java
```
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
import java.util.regex.Pattern;

@RestController
@RequestMapping("/api/payroll")
@RequiredArgsConstructor
public class PayrollApiController {

    private static final Pattern YEAR_MONTH_PATTERN = Pattern.compile("^\\d{4}-(0[1-9]|1[0-2])$");

    private final PayrollService payrollService;
    private final StoreAuthorizationService storeAuthorizationService;

    private void validateYearMonth(String yearMonth) {
        if (yearMonth == null || !YEAR_MONTH_PATTERN.matcher(yearMonth).matches()) {
            throw new IllegalArgumentException("無効な対象年月フォーマットです (形式: YYYY-MM): " + yearMonth);
        }
    }

    /**
     * 月別給与一覧取得 (オーナー店舗所有権検証必須)
     */
    @GetMapping("/list")
    public ResponseEntity<ApiResponse<List<PayrollDto>>> getPayrollList(
            @RequestParam("storeId") Long storeId,
            @RequestParam("yearMonth") String yearMonth,
            @AuthenticationPrincipal CustomUserDetails userDetails
    ) {
        validateYearMonth(yearMonth);
        storeAuthorizationService.validateStoreOwnership(storeId, userDetails.getMemberId());
        List<PayrollDto> list = payrollService.getPayrollsByStoreAndMonth(storeId, yearMonth);
        return ResponseEntity.ok(ApiResponse.ok("給与一覧を取得しました。", list));
    }

    /**
     * 月別給与一括計算 (Upsert, オーナー店舗所有権検証必須)
     */
    @PostMapping("/calculate")
    public ResponseEntity<ApiResponse<Void>> calculateMonthlyPayroll(
            @RequestBody(required = false) PayrollCalculateRequestDto requestDto,
            @AuthenticationPrincipal CustomUserDetails userDetails
    ) {
        if (requestDto == null || requestDto.getStoreId() == null) {
            throw new IllegalArgumentException("店舗IDは必須です。");
        }
        validateYearMonth(requestDto.getTargetYearMonth());
        storeAuthorizationService.validateStoreOwnership(requestDto.getStoreId(), userDetails.getMemberId());
        payrollService.calculateMonthlyPayroll(requestDto.getStoreId(), requestDto.getTargetYearMonth());
        return ResponseEntity.ok(ApiResponse.ok("給与計算が完了しました。", null));
    }

    /**
     * 店舗全体の月次給与一括確定 (排他悲観的ロック適用・帳簿ロック, オーナー店舗所有権検証必須)
     */
    @PostMapping("/confirm-month")
    public ResponseEntity<ApiResponse<Integer>> confirmMonthlyPayroll(
            @RequestBody(required = false) PayrollCalculateRequestDto requestDto,
            @AuthenticationPrincipal CustomUserDetails userDetails
    ) {
        if (requestDto == null || requestDto.getStoreId() == null) {
            throw new IllegalArgumentException("店舗IDは必須です。");
        }
        validateYearMonth(requestDto.getTargetYearMonth());
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
        validateYearMonth(yearMonth);

        storeAuthorizationService.validateStoreOwnership(storeId, userDetails.getMemberId());
        byte[] excelBytes = payrollService.exportPayrollToExcel(storeId, yearMonth);

        String fileName = URLEncoder.encode("給与台帳_" + yearMonth + ".xlsx", StandardCharsets.UTF_8).replace("+", "%20");

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + fileName + "\"; filename*=UTF-8''" + fileName)
                .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .body(excelBytes);
    }
}

```

## File: src/main/java/com/example/kintai/domain/payroll/dto/PayrollDetailResponseDto.java
```
package com.example.kintai.domain.payroll.dto;

import java.util.List;

public class PayrollDetailResponseDto {
    private Long payrollId;
    private String staffName;
    private String targetYearMonth;
    private Integer appliedHourlyWage;
    private PayrollSummary summary;
    private List<DailyAttendanceDetail> dailyAttendances;

    public PayrollDetailResponseDto() {}

    public static class PayrollSummary {
        private Integer workDaysCount;
        private Integer totalWorkMinutes;
        private Integer totalNightMinutes;
        private Integer totalOvertimeMinutes;
        private Integer basePay;
        private Integer nightAllowance;
        private Integer overtimeAllowance;
        private Integer transportAllowance;
        private Integer grossPay;
        private String payrollStatus;

        public PayrollSummary() {}

        public Integer getWorkDaysCount() { return workDaysCount; }
        public void setWorkDaysCount(Integer workDaysCount) { this.workDaysCount = workDaysCount; }
        public Integer getTotalWorkMinutes() { return totalWorkMinutes; }
        public void setTotalWorkMinutes(Integer totalWorkMinutes) { this.totalWorkMinutes = totalWorkMinutes; }
        public Integer getTotalNightMinutes() { return totalNightMinutes; }
        public void setTotalNightMinutes(Integer totalNightMinutes) { this.totalNightMinutes = totalNightMinutes; }
        public Integer getTotalOvertimeMinutes() { return totalOvertimeMinutes; }
        public void setTotalOvertimeMinutes(Integer totalOvertimeMinutes) { this.totalOvertimeMinutes = totalOvertimeMinutes; }
        public Integer getBasePay() { return basePay; }
        public void setBasePay(Integer basePay) { this.basePay = basePay; }
        public Integer getNightAllowance() { return nightAllowance; }
        public void setNightAllowance(Integer nightAllowance) { this.nightAllowance = nightAllowance; }
        public Integer getOvertimeAllowance() { return overtimeAllowance; }
        public void setOvertimeAllowance(Integer overtimeAllowance) { this.overtimeAllowance = overtimeAllowance; }
        public Integer getTransportAllowance() { return transportAllowance; }
        public void setTransportAllowance(Integer transportAllowance) { this.transportAllowance = transportAllowance; }
        public Integer getGrossPay() { return grossPay; }
        public void setGrossPay(Integer grossPay) { this.grossPay = grossPay; }
        public String getPayrollStatus() { return payrollStatus; }
        public void setPayrollStatus(String payrollStatus) { this.payrollStatus = payrollStatus; }
    }

    public static class DailyAttendanceDetail {
        private String workDate;
        private String clockInAt;
        private String clockOutAt;
        private Integer breakMinutes;
        private Integer netWorkMinutes;
        private Integer nightMinutes;
        private Integer overtimeMinutes;
        private Integer appliedHourlyWage;
        private Integer basePay;
        private Integer nightAllowance;
        private Integer overtimeAllowance;
        private Integer transportAmount;

        public DailyAttendanceDetail() {}

        public String getWorkDate() { return workDate; }
        public void setWorkDate(String workDate) { this.workDate = workDate; }
        public String getClockInAt() { return clockInAt; }
        public void setClockInAt(String clockInAt) { this.clockInAt = clockInAt; }
        public String getClockOutAt() { return clockOutAt; }
        public void setClockOutAt(String clockOutAt) { this.clockOutAt = clockOutAt; }
        public Integer getBreakMinutes() { return breakMinutes; }
        public void setBreakMinutes(Integer breakMinutes) { this.breakMinutes = breakMinutes; }
        public Integer getNetWorkMinutes() { return netWorkMinutes; }
        public void setNetWorkMinutes(Integer netWorkMinutes) { this.netWorkMinutes = netWorkMinutes; }
        public Integer getNightMinutes() { return nightMinutes; }
        public void setNightMinutes(Integer nightMinutes) { this.nightMinutes = nightMinutes; }
        public Integer getOvertimeMinutes() { return overtimeMinutes; }
        public void setOvertimeMinutes(Integer overtimeMinutes) { this.overtimeMinutes = overtimeMinutes; }
        public Integer getAppliedHourlyWage() { return appliedHourlyWage; }
        public void setAppliedHourlyWage(Integer appliedHourlyWage) { this.appliedHourlyWage = appliedHourlyWage; }
        public Integer getBasePay() { return basePay; }
        public void setBasePay(Integer basePay) { this.basePay = basePay; }
        public Integer getNightAllowance() { return nightAllowance; }
        public void setNightAllowance(Integer nightAllowance) { this.nightAllowance = nightAllowance; }
        public Integer getOvertimeAllowance() { return overtimeAllowance; }
        public void setOvertimeAllowance(Integer overtimeAllowance) { this.overtimeAllowance = overtimeAllowance; }
        public Integer getTransportAmount() { return transportAmount; }
        public void setTransportAmount(Integer transportAmount) { this.transportAmount = transportAmount; }
    }

    public Long getPayrollId() { return payrollId; }
    public void setPayrollId(Long payrollId) { this.payrollId = payrollId; }
    public String getStaffName() { return staffName; }
    public void setStaffName(String staffName) { this.staffName = staffName; }
    public String getTargetYearMonth() { return targetYearMonth; }
    public void setTargetYearMonth(String targetYearMonth) { this.targetYearMonth = targetYearMonth; }
    public Integer getAppliedHourlyWage() { return appliedHourlyWage; }
    public void setAppliedHourlyWage(Integer appliedHourlyWage) { this.appliedHourlyWage = appliedHourlyWage; }
    public PayrollSummary getSummary() { return summary; }
    public void setSummary(PayrollSummary summary) { this.summary = summary; }
    public List<DailyAttendanceDetail> getDailyAttendances() { return dailyAttendances; }
    public void setDailyAttendances(List<DailyAttendanceDetail> dailyAttendances) { this.dailyAttendances = dailyAttendances; }
}

```

## File: src/main/java/com/example/kintai/global/exception/GlobalExceptionHandler.java
```
package com.example.kintai.global.exception;

import com.example.kintai.global.common.ApiResponse;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    // 入力値バリデーション (@Valid) 失敗時のハンドリング
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Void>> handleValidationException(MethodArgumentNotValidException ex) {
        String errorMessage = ex.getBindingResult().getAllErrors().get(0).getDefaultMessage();
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.fail(errorMessage));
    }

    // ビジネスロジック例外 (IllegalArgumentException / IllegalStateException) のハンドリング
    @ExceptionHandler({IllegalArgumentException.class, IllegalStateException.class})
    public ResponseEntity<ApiResponse<Void>> handleBusinessException(RuntimeException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.fail(ex.getMessage()));
    }

    // 認可・権限不一致例外 (AccessDeniedException) のハンドリング（HTTP 403 Forbidden）
    @ExceptionHandler(org.springframework.security.access.AccessDeniedException.class)
    public ResponseEntity<ApiResponse<Void>> handleAccessDeniedException(org.springframework.security.access.AccessDeniedException ex) {
        log.warn("アクセス拒絶 (AccessDenied): {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(ApiResponse.fail(ex.getMessage()));
    }

    // その他サーバー内部例外のハンドリング（SQLやスタックトレースの隠蔽およびロギング）
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleGeneralException(Exception ex, HttpServletRequest request) throws Exception {
        // HTML画面リクエストの場合はデフォルトのエラーページへ委譲
        String accept = request.getHeader("Accept");
        if (accept != null && accept.contains("text/html") && !request.getRequestURI().startsWith("/api/")) {
            throw ex;
        }
        log.error("Unhandled exception at {}: ", request.getRequestURI(), ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiResponse.fail("サーバー内部エラーが発生しました。しばらくしてから再度お試しください。"));
    }
}

```

## File: src/test/java/com/example/kintai/domain/payroll/controller/PayrollApiValidationAndSerializationIntegrationTest.java
```
package com.example.kintai.domain.payroll.controller;

import com.example.kintai.database.IsolatedDatabaseGuard;
import com.example.kintai.domain.member.dto.MemberDto;
import com.example.kintai.global.security.CustomUserDetails;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.json.JsonParserFactory;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class PayrollApiValidationAndSerializationIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private DataSource dataSource;

    private long ownerId;
    private long staffMemberId;
    private long storeId;
    private long storeStaffId;
    private long attendanceId;
    private long payrollId;
    private long payrollDailyDetailId;

    @BeforeEach
    void setUpIsolatedFixture() throws Exception {
        IsolatedDatabaseGuard.requireAllowedDatabase(dataSource, "kintai_(day11_test|full_test)_[0-9a-f]{12}");
        try (Connection connection = dataSource.getConnection()) {
            ownerId = insertAndGetId(connection,
                    "INSERT INTO member(email,password,name,phone,role) VALUES(?,?,?,?,'ROLE_OWNER')",
                    "payroll-api-owner-" + UUID.randomUUID() + "@example.invalid", "test-only", "Payroll API Owner", "0000000000");
            staffMemberId = insertAndGetId(connection,
                    "INSERT INTO member(email,password,name,phone,role) VALUES(?,?,?,?,'ROLE_STAFF')",
                    "payroll-api-staff-" + UUID.randomUUID() + "@example.invalid", "test-only", "Payroll API Staff", "0000000001");
            storeId = insertAndGetId(connection,
                    "INSERT INTO store(owner_id,store_name,invite_code,qr_token,pay_cutoff_day) VALUES(?,?,?,?,99)",
                    ownerId, "Payroll API Store", token("inv"), token("qrt"));
            storeStaffId = insertAndGetId(connection,
                    "INSERT INTO store_staff(store_id,member_id,hourly_wage,joined_date) VALUES(?,?,1200,?)",
                    storeId, staffMemberId, LocalDate.of(2026, 1, 1));
            attendanceId = insertAndGetId(connection,
                    "INSERT INTO attendance(store_staff_id,store_id,work_date,clock_in_at,clock_out_at,attendance_status,total_break_minutes,net_work_minutes,night_work_minutes,overtime_minutes) " +
                            "VALUES(?,?, '2026-01-10', '2026-01-10 09:00:00', '2026-01-10 18:00:00', 'COMPLETED', 60, 480, 0, 0)",
                    storeStaffId, storeId);
            payrollId = insertAndGetId(connection,
                    "INSERT INTO payroll(store_id,store_staff_id,target_year_month,range_start_date,range_end_date,work_days_count,total_work_minutes,total_night_minutes,total_overtime_minutes,applied_hourly_wage,applied_transport_limit,base_pay,night_allowance,overtime_allowance,transport_allowance,gross_pay,payroll_status) " +
                            "VALUES(?,?,'2026-01','2026-01-01','2026-01-31',1,480,0,0,1200,0,9600,0,0,0,9600,'DRAFT')",
                    storeId, storeStaffId);
            payrollDailyDetailId = insertAndGetId(connection,
                    "INSERT INTO payroll_daily_detail(payroll_id,attendance_id,work_date,applied_hourly_wage,total_break_minutes,net_work_minutes,night_work_minutes,overtime_minutes,base_pay,night_allowance,overtime_allowance,transport_amount) " +
                            "VALUES(?,?,'2026-01-10',1200,60,480,0,0,9600,0,0,0)",
                    payrollId, attendanceId);
        }
    }

    @AfterEach
    void cleanUpIsolatedFixture() throws Exception {
        try (Connection connection = dataSource.getConnection()) {
            if (payrollDailyDetailId > 0) delete(connection, "DELETE FROM payroll_daily_detail WHERE payroll_daily_detail_id=?", payrollDailyDetailId);
            if (payrollId > 0) delete(connection, "DELETE FROM payroll WHERE payroll_id=?", payrollId);
            if (attendanceId > 0) delete(connection, "DELETE FROM attendance WHERE attendance_id=?", attendanceId);
            if (storeStaffId > 0) delete(connection, "DELETE FROM store_staff WHERE store_staff_id=?", storeStaffId);
            if (storeId > 0) delete(connection, "DELETE FROM store WHERE store_id=?", storeId);
            if (staffMemberId > 0) delete(connection, "DELETE FROM member WHERE member_id=?", staffMemberId);
            if (ownerId > 0) delete(connection, "DELETE FROM member WHERE member_id=?", ownerId);
        }
    }

    @Test
    void listCalculateConfirmAndExcelRejectInvalidYearMonthWithHttp400() throws Exception {
        CustomUserDetails ownerPrincipal = buildOwnerPrincipal();
        List<String> invalidMonths = List.of("2026-13", "2026-00", "2026-1", "not-a-month");

        for (String invalidMonth : invalidMonths) {
            mockMvc.perform(get("/api/payroll/list")
                            .param("storeId", String.valueOf(storeId))
                            .param("yearMonth", invalidMonth)
                            .with(user(ownerPrincipal)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.success").value(false));

            mockMvc.perform(post("/api/payroll/calculate")
                            .with(user(ownerPrincipal))
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"storeId\":" + storeId + ",\"targetYearMonth\":\"" + invalidMonth + "\"}"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.success").value(false));

            mockMvc.perform(post("/api/payroll/confirm-month")
                            .with(user(ownerPrincipal))
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"storeId\":" + storeId + ",\"targetYearMonth\":\"" + invalidMonth + "\"}"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.success").value(false));

            mockMvc.perform(get("/api/payroll/excel")
                            .param("storeId", String.valueOf(storeId))
                            .param("yearMonth", invalidMonth)
                            .with(user(ownerPrincipal)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.success").value(false));
        }

        // Valid YYYY-MM still succeeds and fixture payroll remains DRAFT (not confirmed by invalid requests)
        mockMvc.perform(get("/api/payroll/list")
                        .param("storeId", String.valueOf(storeId))
                        .param("yearMonth", "2026-01")
                        .with(user(ownerPrincipal)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data[0].payrollStatus").value("DRAFT"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void payrollDetailJsonSerializationExcludesSensitiveFieldsAndMatchesAllowlist() throws Exception {
        CustomUserDetails ownerPrincipal = buildOwnerPrincipal();

        MvcResult result = mockMvc.perform(get("/api/payroll/" + payrollId + "/detail")
                        .with(user(ownerPrincipal)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andReturn();

        String responseBody = result.getResponse().getContentAsString();
        Map<String, Object> root = JsonParserFactory.getJsonParser().parseMap(responseBody);
        Map<String, Object> data = (Map<String, Object>) root.get("data");
        assertNotNull(data);

        assertEquals(
                Set.of("payrollId", "staffName", "targetYearMonth", "appliedHourlyWage", "summary", "dailyAttendances"),
                data.keySet()
        );

        Set<String> allJsonKeys = new HashSet<>();
        collectKeys(root, allJsonKeys);

        List<String> forbiddenSensitiveKeys = List.of(
                "bankAccount", "accountNumber", "bankName", "branchCode",
                "myNumber", "socialSecurityNumber", "residentNumber",
                "password", "email", "phone"
        );
        for (String sensitiveKey : forbiddenSensitiveKeys) {
            assertFalse(allJsonKeys.contains(sensitiveKey), "Sensitive field leaked in JSON: " + sensitiveKey);
            assertFalse(responseBody.contains("\"" + sensitiveKey + "\""), "Sensitive key string found in JSON: " + sensitiveKey);
        }

        Map<String, Object> summary = (Map<String, Object>) data.get("summary");
        assertNotNull(summary);
        assertEquals(9600, ((Number) summary.get("grossPay")).intValue());

        List<Object> dailyAttendances = (List<Object>) data.get("dailyAttendances");
        assertNotNull(dailyAttendances);
        assertEquals(1, dailyAttendances.size());
        assertTrue(((Map<String, Object>) dailyAttendances.get(0)).containsKey("workDate"));
    }

    @SuppressWarnings("unchecked")
    private void collectKeys(Object node, Set<String> keys) {
        if (node instanceof Map<?, ?> map) {
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                keys.add(String.valueOf(entry.getKey()));
                collectKeys(entry.getValue(), keys);
            }
        } else if (node instanceof List<?> list) {
            for (Object item : list) {
                collectKeys(item, keys);
            }
        }
    }

    private CustomUserDetails buildOwnerPrincipal() {
        MemberDto member = new MemberDto();
        member.setMemberId(ownerId);
        member.setEmail("payroll-api-owner@example.invalid");
        member.setName("Payroll API Owner");
        member.setRole("ROLE_OWNER");
        member.setIsActive(true);
        return new CustomUserDetails(member, null, storeId);
    }

    private static long insertAndGetId(Connection connection, String sql, Object... params) throws Exception {
        try (PreparedStatement statement = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            for (int i = 0; i < params.length; i++) {
                statement.setObject(i + 1, params[i]);
            }
            statement.executeUpdate();
            try (ResultSet keys = statement.getGeneratedKeys()) {
                if (!keys.next()) {
                    throw new IllegalStateException("No generated key returned for: " + sql);
                }
                return keys.getLong(1);
            }
        }
    }

    private static void delete(Connection connection, String sql, long id) throws Exception {
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, id);
            statement.executeUpdate();
        }
    }

    private static String token(String prefix) {
        return prefix + "_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
    }
}

```

## File: docs/audit_runs/isolated_all_20261114_114819_kintai_full_test_8a75679f5b1e/exit_summary.json
```
{
    "gradle_exit_code":  0,
    "junit_xml_count":  38,
    "tests":  94,
    "failures":  0,
    "errors":  0,
    "skipped":  0,
    "junit_parse_errors":  [

                           ],
    "result":  "PASS"
}

```

