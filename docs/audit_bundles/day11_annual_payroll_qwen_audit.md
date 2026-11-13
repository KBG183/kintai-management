# Day 11 final-source focused Qwen review

## Review request
Review only the supplied latest test, mapper query, and invite-code schema constraint. Return at most 450 words, only a table with Severity, Finding, Evidence, Fix and a one-sentence verdict. Do not show analysis or chain-of-thought. Check compile-level issues, SQL column/length constraints, transaction fixture and cleanup safety, effective-date selection, shift classification and minute totals, monthly calculations, annual oracle counts, and what E2E layer is actually exercised. Explicitly state if execution evidence is absent.

## Relevant schema constraint
invite_code VARCHAR(20) NOT NULL UNIQUE

## Mapper query
```xml
<select id="findHourlyWageForStaffOnDate" resultType="java.lang.Integer">
        SELECT hourly_wage
        FROM store_staff_contract_history
        WHERE store_staff_id = #{storeStaffId}
          AND effective_from &lt;= #{workDate}
          AND (effective_to IS NULL OR effective_to &gt; #{workDate})
        ORDER BY effective_from DESC
        LIMIT 1
    </select>
```

## Current integration test
```java
package com.example.kintai.domain.payroll.service;

import com.example.kintai.domain.attendance.dto.LaborDetailDto;
import com.example.kintai.domain.attendance.service.LaborCalculationService;
import com.example.kintai.domain.payroll.dto.PayrollDto;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@SpringBootTest
@DisplayName("Day 11: 12-month attendance-to-payroll scenario")
class AnnualPayrollScenarioIntegrationTest {

    @Autowired
    private DataSource dataSource;

    @Autowired
    private LaborCalculationService laborCalculationService;

    @Autowired
    private PayrollService payrollService;

    private long memberId;
    private long storeId;
    private long staffId;
    private long staffMemberId;
    private final List<Long> attendanceIds = new ArrayList<>();

    private record ShiftCase(int month, int kind, int wage, int netMinutes,
                             int nightMinutes, int overtimeMinutes, int breakMinutes,
                             int breakCount) {}

    private static final ShiftCase[] CASES = {
            new ShiftCase(1, 0, 1300, 480, 0, 0, 60, 1),
            new ShiftCase(2, 1, 1300, 540, 0, 60, 60, 1),
            new ShiftCase(3, 2, 1300, 480, 360, 0, 0, 0),
            new ShiftCase(4, 3, 1300, 480, 0, 0, 60, 2),
            new ShiftCase(5, 0, 1300, 480, 0, 0, 60, 1),
            new ShiftCase(6, 1, 1300, 540, 0, 60, 60, 1),
            new ShiftCase(7, 2, 1400, 480, 360, 0, 0, 0),
            new ShiftCase(8, 3, 1400, 480, 0, 0, 60, 2),
            new ShiftCase(9, 0, 1400, 480, 0, 0, 60, 1),
            new ShiftCase(10, 1, 1400, 540, 0, 60, 60, 1),
            new ShiftCase(11, 2, 1400, 480, 360, 0, 0, 0),
            new ShiftCase(12, 3, 1400, 480, 0, 0, 60, 2)
    };

    @BeforeEach
    void seedAnnualScenario() throws Exception {
        try (Connection connection = dataSource.getConnection()) {
            memberId = insertAndGetId(connection,
                    "INSERT INTO member(email,password,name,phone,role) VALUES(?,?,?,?,'ROLE_OWNER')",
                    "day11-" + UUID.randomUUID() + "@example.invalid", "test-only", "Day11 Owner", "0000000000");
            storeId = insertAndGetId(connection,
                    "INSERT INTO store(owner_id,store_name,invite_code,qr_token,pay_cutoff_day) VALUES(?,?,?,?,99)",
                    memberId, "Day11 Annual Scenario",
                    "d11-" + UUID.randomUUID().toString().replace("-", "").substring(0, 16),
                    "d11-" + UUID.randomUUID());
            staffMemberId = insertAndGetId(connection,
                    "INSERT INTO member(email,password,name,phone,role) VALUES(?,?,?,?,'ROLE_STAFF')",
                    "day11-staff-" + UUID.randomUUID() + "@example.invalid", "test-only", "Day11 Staff", "0000000001");
            staffId = insertAndGetId(connection,
                    "INSERT INTO store_staff(store_id,member_id,hourly_wage,daily_transport_fee,monthly_transport_limit,joined_date) VALUES(?,?,1300,1200,1000,'2026-01-01')",
                    storeId, staffMemberId);

            execute(connection,
                    "INSERT INTO store_staff_contract_history(store_staff_id,effective_from,effective_to,hourly_wage,daily_transport_fee,monthly_transport_limit) VALUES(?, '2026-01-01','2026-07-01',1300,1200,1000)",
                    staffId);
            execute(connection,
                    "INSERT INTO store_staff_contract_history(store_staff_id,effective_from,effective_to,hourly_wage,daily_transport_fee,monthly_transport_limit) VALUES(?, '2026-07-01',NULL,1400,1200,1000)",
                    staffId);

            for (ShiftCase shift : CASES) {
                LocalDate date = LocalDate.of(2026, shift.month(), 15);
                LocalDateTime clockIn = date.atTime(shift.kind() == 2 ? 20 : 9, 0);
                LocalDateTime clockOut = shift.kind() == 2 ? date.plusDays(1).atTime(4, 0)
                        : date.atTime(shift.kind() == 1 ? 19 : 18, 0);
                long attendanceId = insertAndGetId(connection,
                        "INSERT INTO attendance(store_id,store_staff_id,work_date,clock_in_at,clock_out_at,total_break_minutes,net_work_minutes,night_work_minutes,overtime_minutes,clock_in_method,attendance_status) VALUES(?,?,?,?,?,?,?,?,?,'BUTTON','COMPLETED')",
                        storeId, staffId, Date.valueOf(date), java.sql.Timestamp.valueOf(clockIn),
                        java.sql.Timestamp.valueOf(clockOut), shift.breakMinutes(), shift.netMinutes(),
                        shift.nightMinutes(), shift.overtimeMinutes());
                attendanceIds.add(attendanceId);
                insertBreaks(connection, attendanceId, date, shift);
            }
        }
    }

    @AfterEach
    void cleanAnnualScenario() throws Exception {
        if (staffId == 0 && storeId == 0 && memberId == 0 && staffMemberId == 0) return;
        try (Connection connection = dataSource.getConnection()) {
            if (staffId != 0) {
                execute(connection, "DELETE FROM payroll_daily_detail WHERE payroll_id IN (SELECT payroll_id FROM payroll WHERE store_staff_id=?)", staffId);
                execute(connection, "DELETE FROM payroll WHERE store_staff_id=?", staffId);
                execute(connection, "DELETE FROM attendance_break WHERE attendance_id IN (SELECT attendance_id FROM attendance WHERE store_staff_id=?)", staffId);
                execute(connection, "DELETE FROM attendance WHERE store_staff_id=?", staffId);
                execute(connection, "DELETE FROM store_staff_contract_history WHERE store_staff_id=?", staffId);
                execute(connection, "DELETE FROM store_staff WHERE store_staff_id=?", staffId);
            }
            if (storeId != 0) execute(connection, "DELETE FROM store WHERE store_id=?", storeId);
            if (memberId != 0) execute(connection, "DELETE FROM member WHERE member_id=?", memberId);
            if (staffMemberId != 0) execute(connection, "DELETE FROM member WHERE member_id=?", staffMemberId);
        }
    }

    @Test
    void calculatesEveryMonthAndAnnualTotalAgainstIndependentOracle() {
        int annualGrossPay = 0;
        int actualAnnualGrossPay = 0;
        int annualWorkMinutes = 0;
        int annualNightMinutes = 0;
        int annualOvertimeMinutes = 0;

        for (int i = 0; i < CASES.length; i++) {
            ShiftCase shift = CASES[i];
            LaborDetailDto labor = laborCalculationService.calculateDetailedLabor(attendanceIds.get(i));
            assertEquals(shift.netMinutes(), labor.getNetWorkMinutes(), "net minutes for month " + shift.month());
            assertEquals(shift.nightMinutes(), labor.getTotalNightMinutes(), "night minutes for month " + shift.month());
            assertEquals(shift.overtimeMinutes(), labor.getTotalOvertimeMinutes(), "overtime minutes for month " + shift.month());
            assertEquals(shift.wage(), labor.getAppliedHourlyWage(), "contract wage for month " + shift.month());

            LocalDate start = LocalDate.of(2026, shift.month(), 1);
            LocalDate end = start.withDayOfMonth(start.lengthOfMonth());
            String yearMonth = String.format("2026-%02d", shift.month());
            PayrollDto payroll = payrollService.calculatePayrollForStaff(storeId, staffId, yearMonth, start, end);

            int expectedBase = shift.wage() * shift.netMinutes() / 60;
            int expectedNightAllowance = shift.wage() * shift.nightMinutes() / 240;
            int expectedOvertimeAllowance = shift.wage() * shift.overtimeMinutes() / 240;
            int expectedGross = expectedBase + expectedNightAllowance + expectedOvertimeAllowance + 1000;
            assertNotNull(payroll.getPayrollId(), "persisted payroll for month " + shift.month());
            assertEquals(1, payroll.getWorkDaysCount(), "work days for month " + shift.month());
            assertEquals(shift.netMinutes(), payroll.getTotalWorkMinutes(), "payroll work minutes for month " + shift.month());
            assertEquals(shift.nightMinutes(), payroll.getTotalNightMinutes(), "payroll night minutes for month " + shift.month());
            assertEquals(shift.overtimeMinutes(), payroll.getTotalOvertimeMinutes(), "payroll overtime minutes for month " + shift.month());
            assertEquals(expectedBase, payroll.getBasePay(), "base pay for month " + shift.month());
            assertEquals(expectedNightAllowance, payroll.getNightAllowance(), "night allowance for month " + shift.month());
            assertEquals(expectedOvertimeAllowance, payroll.getOvertimeAllowance(), "overtime allowance for month " + shift.month());
            assertEquals(1000, payroll.getTransportAllowance(), "monthly transport cap for month " + shift.month());
            assertEquals(expectedGross, payroll.getGrossPay(), "gross pay for month " + shift.month());

            annualGrossPay += expectedGross;
            actualAnnualGrossPay += payroll.getGrossPay();
            annualWorkMinutes += shift.netMinutes();
            annualNightMinutes += shift.nightMinutes();
            annualOvertimeMinutes += shift.overtimeMinutes();
        }

        assertEquals(annualGrossPay, actualAnnualGrossPay);
        int independentAnnualOracle = 3 * (8 * 1300 + 1000)
                + 2 * (9 * 1300 + 1300 / 4 + 1000)
                + (8 * 1300 + 3 * 1300 / 2 + 1000)
                + 3 * (8 * 1400 + 1000)
                + (9 * 1400 + 1400 / 4 + 1000)
                + 2 * (8 * 1400 + 3 * 1400 / 2 + 1000);
        assertEquals(independentAnnualOracle, annualGrossPay);
        assertEquals(12 * 480 + 3 * 60, annualWorkMinutes);
        assertEquals(3 * 360, annualNightMinutes);
        assertEquals(3 * 60, annualOvertimeMinutes);
    }

    private static void insertBreaks(Connection connection, long attendanceId, LocalDate date, ShiftCase shift) throws Exception {
        if (shift.breakCount() == 0) return;
        if (shift.breakCount() == 1) {
            insertBreak(connection, attendanceId, date.atTime(13, 0), date.atTime(14, 0));
        } else {
            insertBreak(connection, attendanceId, date.atTime(12, 0), date.atTime(12, 30));
            insertBreak(connection, attendanceId, date.atTime(15, 0), date.atTime(15, 30));
        }
    }

    private static void insertBreak(Connection connection, long attendanceId, LocalDateTime start, LocalDateTime end) throws Exception {
        try (PreparedStatement statement = connection.prepareStatement(
                "INSERT INTO attendance_break(attendance_id,break_start_at,break_end_at,duration_minutes) VALUES(?,?,?,?)")) {
            statement.setLong(1, attendanceId);
            statement.setTimestamp(2, java.sql.Timestamp.valueOf(start));
            statement.setTimestamp(3, java.sql.Timestamp.valueOf(end));
            statement.setLong(4, java.time.Duration.between(start, end).toMinutes());
            statement.executeUpdate();
        }
    }

    private static long insertAndGetId(Connection connection, String sql, Object... values) throws Exception {
        try (PreparedStatement statement = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            for (int i = 0; i < values.length; i++) statement.setObject(i + 1, values[i]);
            statement.executeUpdate();
            try (ResultSet keys = statement.getGeneratedKeys()) {
                if (!keys.next()) throw new IllegalStateException("No generated key returned for seed insert.");
                return keys.getLong(1);
            }
        }
    }

    private static void execute(Connection connection, String sql, Object... values) throws Exception {
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            for (int i = 0; i < values.length; i++) statement.setObject(i + 1, values[i]);
            statement.executeUpdate();
        }
    }
}
```