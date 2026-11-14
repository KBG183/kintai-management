package com.example.kintai.domain.payroll.service;

import com.example.kintai.database.IsolatedDatabaseGuard;
import com.example.kintai.domain.attendance.dto.AttendancePunchDto;
import com.example.kintai.domain.attendance.dto.LaborDetailDto;
import com.example.kintai.domain.attendance.mapper.AttendanceMapper;
import com.example.kintai.domain.attendance.service.AttendanceService;
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
import static org.junit.jupiter.api.Assertions.assertThrows;

@SpringBootTest
@DisplayName("Day 11: 12-month attendance-to-payroll scenario")
class AnnualPayrollScenarioIntegrationTest {

    @Autowired
    private DataSource dataSource;

    @Autowired
    private LaborCalculationService laborCalculationService;

    @Autowired
    private AttendanceMapper attendanceMapper;

    @Autowired
    private AttendanceService attendanceService;

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
        IsolatedDatabaseGuard.requireAllowedDatabase(dataSource, "kintai_(day11_test|full_test)_[0-9a-f]{12}");
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
        insertAdditionalMonthlyWorkdays();
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

            int monthlyNetMinutes = shift.netMinutes() + 2 * 480;
            int expectedBase = shift.wage() * monthlyNetMinutes / 60;
            int expectedNightAllowance = shift.wage() * shift.nightMinutes() / 240;
            int expectedOvertimeAllowance = shift.wage() * shift.overtimeMinutes() / 240;
            int expectedGross = expectedBase + expectedNightAllowance + expectedOvertimeAllowance + 1000;
            assertNotNull(payroll.getPayrollId(), "persisted payroll for month " + shift.month());
            assertEquals(3, payroll.getWorkDaysCount(), "three distinct work dates for month " + shift.month());
            assertEquals(monthlyNetMinutes, payroll.getTotalWorkMinutes(), "payroll work minutes for month " + shift.month());
            assertEquals(shift.nightMinutes(), payroll.getTotalNightMinutes(), "payroll night minutes for month " + shift.month());
            assertEquals(shift.overtimeMinutes(), payroll.getTotalOvertimeMinutes(), "payroll overtime minutes for month " + shift.month());
            assertEquals(expectedBase, payroll.getBasePay(), "base pay for month " + shift.month());
            assertEquals(expectedNightAllowance, payroll.getNightAllowance(), "night allowance for month " + shift.month());
            assertEquals(expectedOvertimeAllowance, payroll.getOvertimeAllowance(), "overtime allowance for month " + shift.month());
            assertEquals(1000, payroll.getAppliedTransportLimit(), "contract transport limit for month " + shift.month());
            assertEquals(1000, payroll.getTransportAllowance(), "monthly transport cap for month " + shift.month());
            assertEquals(expectedGross, payroll.getGrossPay(), "gross pay for month " + shift.month());

            annualGrossPay += expectedGross;
            actualAnnualGrossPay += payroll.getGrossPay();
            annualWorkMinutes += monthlyNetMinutes;
            annualNightMinutes += shift.nightMinutes();
            annualOvertimeMinutes += shift.overtimeMinutes();
        }

        assertEquals(annualGrossPay, actualAnnualGrossPay);
        int independentAnnualOracle = 3 * (8 * 1300 + 1000)
                + 2 * (9 * 1300 + 1300 / 4 + 1000)
                + (8 * 1300 + 3 * 1300 / 2 + 1000)
                + 3 * (8 * 1400 + 1000)
                + (9 * 1400 + 1400 / 4 + 1000)
                + 2 * (8 * 1400 + 3 * 1400 / 2 + 1000)
                + (2 * 8 * 1300 * 6)
                + (2 * 8 * 1400 * 6);
        assertEquals(independentAnnualOracle, annualGrossPay);
        assertEquals(12 * (3 * 480) + 3 * 60, annualWorkMinutes);
        assertEquals(3 * 360, annualNightMinutes);
        assertEquals(3 * 60, annualOvertimeMinutes);
    }

    @Test
    void contractHistoryUsesInclusiveStartAndExclusiveEndDates() {
        assertEquals(1300, attendanceMapper.findHourlyWageForStaffOnDate(staffId, LocalDate.of(2026, 6, 30)));
        assertEquals(1400, attendanceMapper.findHourlyWageForStaffOnDate(staffId, LocalDate.of(2026, 7, 1)));
    }

    @Test
    void isolatedDatabaseGuardRejectsAnyDatabaseOutsideTheExactAllowlist() {
        assertThrows(IllegalStateException.class,
                () -> IsolatedDatabaseGuard.requireAllowedDatabase(dataSource, "kintai_never_allowed_[0-9a-f]{12}"));
    }

    @Test
    void splitShiftClockOutAccumulatesPriorWorkAndFlowsIntoMonthlyPayroll() throws Exception {
        LocalDateTime now = LocalDateTime.now().withNano(0);
        LocalDateTime secondShiftStart = now.minusHours(5);
        LocalDateTime firstShiftStart = secondShiftStart.minusHours(4);
        LocalDate workDate = firstShiftStart.toLocalDate();
        insertCompletedAttendance(workDate, firstShiftStart, secondShiftStart, 240);

        com.example.kintai.domain.attendance.dto.AttendanceDto active = new com.example.kintai.domain.attendance.dto.AttendanceDto();
        active.setStoreId(storeId);
        active.setStoreStaffId(staffId);
        active.setWorkDate(workDate);
        active.setClockInAt(secondShiftStart);
        active.setTotalBreakMinutes(0);
        active.setNetWorkMinutes(0);
        active.setNightWorkMinutes(0);
        active.setOvertimeMinutes(0);
        active.setClockInMethod("BUTTON");
        active.setAttendanceStatus("WORKING");
        attendanceMapper.insertAttendance(active);

        AttendancePunchDto clockOut = new AttendancePunchDto();
        clockOut.setStoreId(storeId);
        clockOut.setStoreStaffId(staffId);
        clockOut.setPunchType("CLOCK_OUT");
        com.example.kintai.domain.attendance.dto.AttendanceDto completed = attendanceService.punch(clockOut);
        assertEquals(300, completed.getNetWorkMinutes());
        assertEquals(60, completed.getOvertimeMinutes(), "the second split shift must include the prior four hours");

        LocalDate monthStart = workDate.withDayOfMonth(1);
        LocalDate monthEnd = workDate.withDayOfMonth(workDate.lengthOfMonth());
        PayrollDto payroll = payrollService.calculatePayrollForStaff(
                storeId, staffId, workDate.format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM")), monthStart, monthEnd);
        assertEquals(1020, payroll.getTotalWorkMinutes());
        assertEquals(60, payroll.getTotalOvertimeMinutes());
        assertEquals(2, payroll.getWorkDaysCount());
    }

    @Test
    void payrollMonthRangeIncludesMonthEndButExcludesNextMonthFirstDay() throws Exception {
        LocalDate januaryEnd = LocalDate.of(2026, 1, 31);
        LocalDate februaryStart = LocalDate.of(2026, 2, 1);
        insertCompletedAttendance(januaryEnd, januaryEnd.atTime(9, 0), januaryEnd.atTime(17, 0), 480);
        insertCompletedAttendance(februaryStart, februaryStart.atTime(9, 0), februaryStart.atTime(17, 0), 480);

        PayrollDto january = payrollService.calculatePayrollForStaff(
                storeId, staffId, "2026-01", LocalDate.of(2026, 1, 1), januaryEnd);
        PayrollDto february = payrollService.calculatePayrollForStaff(
                storeId, staffId, "2026-02", februaryStart, LocalDate.of(2026, 2, 28));

        assertEquals(2, january.getWorkDaysCount());
        assertEquals(960, january.getTotalWorkMinutes());
        assertEquals(2, february.getWorkDaysCount());
        assertEquals(1020, february.getTotalWorkMinutes());
    }

    @Test
    void nightWorkOverlappingDailyOvertimeReceivesBothPremiums() throws Exception {
        LocalDate workDate = LocalDate.of(2026, 1, 14);
        insertCompletedAttendance(workDate, workDate.atTime(20, 0),
                workDate.plusDays(1).atTime(5, 0), 540);
        long attendanceId = attendanceIds.get(attendanceIds.size() - 1);
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "UPDATE attendance SET night_work_minutes=420,overtime_minutes=60 WHERE attendance_id=?")) {
            statement.setLong(1, attendanceId);
            assertEquals(1, statement.executeUpdate());
        } catch (Exception e) {
            throw new IllegalStateException("Failed to set the overlapping night/overtime test case.", e);
        }

        PayrollDto payroll = payrollService.calculatePayrollForStaff(
                storeId, staffId, "2026-01", LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 31));

        assertEquals(1020, payroll.getTotalWorkMinutes());
        assertEquals(420, payroll.getTotalNightMinutes());
        assertEquals(60, payroll.getTotalOvertimeMinutes());
        assertEquals(22_100, payroll.getBasePay());
        assertEquals(2_275, payroll.getNightAllowance());
        assertEquals(325, payroll.getOvertimeAllowance());
        assertEquals(25_700, payroll.getGrossPay());
    }

    private void insertAdditionalMonthlyWorkdays() {
        for (int month = 1; month <= 12; month++) {
            LocalDate first = LocalDate.of(2026, month, 5);
            LocalDate second = LocalDate.of(2026, month, 25);
            insertCompletedAttendance(first, first.atTime(9, 0), first.atTime(18, 0), 480, 60);
            insertCompletedAttendance(second, second.atTime(9, 0), second.atTime(18, 0), 480, 60);
        }
    }

    private void insertCompletedAttendance(LocalDate workDate, LocalDateTime clockIn, LocalDateTime clockOut,
                                           int netMinutes) throws Exception {
        insertCompletedAttendance(workDate, clockIn, clockOut, netMinutes, 0);
    }

    private void insertCompletedAttendance(LocalDate workDate, LocalDateTime clockIn, LocalDateTime clockOut,
                                           int netMinutes, int breakMinutes) {
        try (Connection connection = dataSource.getConnection()) {
            long attendanceId = insertAndGetId(connection,
                    "INSERT INTO attendance(store_id,store_staff_id,work_date,clock_in_at,clock_out_at,total_break_minutes,net_work_minutes,night_work_minutes,overtime_minutes,clock_in_method,attendance_status) VALUES(?,?,?,?,?,?,?,0,0,'BUTTON','COMPLETED')",
                    storeId, staffId, Date.valueOf(workDate), java.sql.Timestamp.valueOf(clockIn),
                    java.sql.Timestamp.valueOf(clockOut), breakMinutes, netMinutes);
            attendanceIds.add(attendanceId);
            if (breakMinutes > 0) {
                insertBreak(connection, attendanceId, workDate.atTime(13, 0), workDate.atTime(14, 0));
            }
        } catch (Exception e) {
            throw new IllegalStateException("Failed to insert isolated attendance scenario for " + workDate, e);
        }
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
