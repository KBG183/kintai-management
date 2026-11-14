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
