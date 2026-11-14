# Day 11 payroll audit, revised after finding F-03

Audit R-11/R-12. Check math and isolation. The prior audit warned that overlapping night and daily overtime were not asserted. The revised integration test now stores a 20:00–05:00 shift with 540 net minutes, 420 night minutes and 60 daily overtime minutes; review the monthly payroll expected values. Return a concise table, identify any remaining concrete finding.

## RTM rows

| **R-11** | Day 11 12개월 급여 계산 시나리오 및 계약 유효기간 | 월별 근무 분포, 계약 이력, API/Service 경로와 급여를 대조하고 독립 연간 합계를 검증한다. | 12개월×3근무일, 계약 임금 변경, 독립 연간 oracle, 타각 HTTP/API 경로, JUnit 및 격리 DB 로그 | `AnnualPayrollScenarioIntegrationTest.java`; `AttendancePunchApiIntegrationTest.java`; `docs/audit_runs/isolated_day11_20261114_093228_kintai_day11_test_3a25e5513c4f/`; `docs/audit_runs/isolated_day11_20261114_092752_kintai_day11_test_1c5791670791/` | **VERIFIED (로컬 MySQL 26.7)** | 12개월 각각 3일의 근태로 급여 및 독립 연간 oracle 대조 PASS, 계약 경계 6/30·7/1 PASS. 실제 Spring Security 필터→Controller→Service→DB 타각 API spoofed ID 차단·저장 검증 PASS. 최신 전체 회귀 91 PASS. |
| **R-12** | Day 11 경계·결함 시나리오 | 야간·다회 휴게·계약 경계·동일일 분할 근무 8시간 누적·월마감·반올림을 독립 기대값으로 검증한다. | 각 경계의 통합/단위 테스트, 저장된 실행 및 JUnit | `AnnualPayrollScenarioIntegrationTest.java`; `SplitShiftOvertimeDefectTest.java`; `PayrollCalculatorStatutoryRoundingTest.java`; `docs/audit_runs/isolated_day11_20261114_093228_kintai_day11_test_3a25e5513c4f/`; `docs/audit_runs/isolated_day11_20261114_092830_kintai_day11_test_1c5791670791/` | **VERIFIED (로컬 MySQL 26.7)** | 4h+5h 분할근무 후 CLOCK_OUT service에서 60분 초과 산출 및 월 급여 반영 PASS. 1/31 포함·2/1 제외, 계약·자정·다회휴게·HALF_UP 경계 PASS. 전체 회귀 91 PASS. |

## Annual integration test

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


## Split shift test

package com.example.kintai.domain.attendance.util;

import com.example.kintai.domain.attendance.util.AttendanceTimeCalculator.CalculationResult;
import com.example.kintai.domain.attendance.util.AttendanceTimeCalculator.DetailedCalculationResult;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * 分割勤務（中抜け）時における1日8時間超過（残業）算定漏れ欠陥の再現テストおよび秒精度不変式検証。
 */
class SplitShiftOvertimeDefectTest {

    @Test
    @DisplayName("分割勤務（昼4時間＋夜5時間）で同日合計9時間勤務した際、夜の勤務で1時間残業が正確に計上されること")
    void testSplitShiftOvertimeOmissionDefect() {
        // 1回目の勤務: 11:00 ~ 15:00（実働4時間 = 240分）
        LocalDateTime shift1Start = LocalDateTime.of(2026, 11, 10, 11, 0);
        LocalDateTime shift1End = LocalDateTime.of(2026, 11, 10, 15, 0);
        CalculationResult result1 = AttendanceTimeCalculator.calculate(shift1Start, shift1End, Collections.emptyList());

        assertEquals(240, result1.netWorkMinutes());
        assertEquals(0, result1.overtimeMinutes());

        // 2回目の勤務: 17:00 ~ 22:00（実働5時間 = 300分）
        LocalDateTime shift2Start = LocalDateTime.of(2026, 11, 10, 17, 0);
        LocalDateTime shift2End = LocalDateTime.of(2026, 11, 10, 22, 0);

        CalculationResult result2 = AttendanceTimeCalculator.calculate(shift2Start, shift2End, Collections.emptyList(), result1.netWorkSeconds());

        // 【欠陥解決の証明】1日の合計9時間勤務に対し、2回目の勤務で60分の法定外残業が正確に算定されることを実証
        assertEquals(60, result2.overtimeMinutes(), "先行勤務4時間が考慮され、5時間中1時間が正しく残業と算定されること");
        assertEquals(240, result2.netWorkMinutes() - result2.overtimeMinutes());
        assertEquals(300, result2.netWorkMinutes());
    }

    @Test
    @DisplayName("秒精度不変式検証: 4時間30秒＋4時間30秒の分割勤務で、全28,860秒が1秒の損失もなく完全保存されること")
    void testSplitShiftSecondPrecisionInvariantPreservation() {
        // 1回目の勤務: 11:00:00 ~ 15:00:30 (4時間30秒 = 14,430秒)
        LocalDateTime shift1Start = LocalDateTime.of(2026, 11, 10, 11, 0, 0);
        LocalDateTime shift1End = LocalDateTime.of(2026, 11, 10, 15, 0, 30);
        DetailedCalculationResult result1 = AttendanceTimeCalculator.calculateDetailed(shift1Start, shift1End, Collections.emptyList());

        assertEquals(14430L, result1.netWorkSeconds(), "1回目の実労働秒数は14,430秒であること");
        assertEquals(14430L, result1.regularWorkSeconds(), "1回目は全額所定内であること");
        assertEquals(0L, result1.overtimeWorkSeconds(), "1回目は残業なしであること");

        // 2回目の勤務: 17:00:00 ~ 21:00:30 (4時間30秒 = 14,430秒)
        // 先行実働秒数(14,430秒)を累積引き継ぎ
        LocalDateTime shift2Start = LocalDateTime.of(2026, 11, 10, 17, 0, 0);
        LocalDateTime shift2End = LocalDateTime.of(2026, 11, 10, 21, 0, 30);
        DetailedCalculationResult result2 = AttendanceTimeCalculator.calculateDetailed(
                shift2Start, shift2End, Collections.emptyList(), result1.netWorkSeconds()
        );

        assertEquals(14430L, result2.netWorkSeconds(), "2回目の実労働秒数は14,430秒であること");

        // 【秒精度完全保存の不変式証明】
        long totalDaySeconds = result1.netWorkSeconds() + result2.netWorkSeconds();
        assertEquals(28860L, totalDaySeconds, "当日の総実労働時間は1秒の損失もなく28,860秒（8時間1分）であること");

        // 8時間法定上限 = 28,800秒。超過分 = 60秒
        assertEquals(14370L, result2.regularWorkSeconds(), "2回目の所定内秒数は 28,800 - 14,430 = 14,370秒であること");
        assertEquals(60L, result2.overtimeWorkSeconds(), "2回目の法定外残業秒数は正確に60秒（1分）であること");

        // 不変式: 2回目の所定内 + 残業 = 2回目の実労働秒数
        assertEquals(result2.netWorkSeconds(), result2.regularWorkSeconds() + result2.overtimeWorkSeconds());

        // 当日全体の所定内合計 = 14,430 + 14,370 = 28,800秒 (法定8時間ジャスト)
        assertEquals(28800L, result1.regularWorkSeconds() + result2.regularWorkSeconds(), "1日の所定内実労働秒数合計は法定限度28,800秒（8時間）と完全に一致すること");

        // 分単位表示への安全変換（切り捨てによる残業消失が起きず1分残業が保たれること）
        assertEquals(1, result2.overtimeMinutes(), "60秒の残業が分単位でも1分として完璧に保持されること");
    }
}


## Statutory rounding test

package com.example.kintai.domain.payroll.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("F-03: 行政通達（昭和63年3月14日付 基発第150号）法定端数処理境界値ゴールデンケース単体テスト")
class PayrollCalculatorStatutoryRoundingTest {

    @Test
    @DisplayName("① 月間総時間数の端数処理（30分未満切り捨て、30分以上1時間切り上げ）の厳密検証")
    void roundMonthlyHours_BoundaryCases() {
        // 0분 -> 0시간
        assertEquals(0, PayrollCalculator.StatutoryRounding.roundMonthlyHours(0));

        // 29분 -> 0시간 (30분 미만 절사)
        assertEquals(0, PayrollCalculator.StatutoryRounding.roundMonthlyHours(29));

        // 30분 -> 1시간 (30분 이상 1시간 올림)
        assertEquals(1, PayrollCalculator.StatutoryRounding.roundMonthlyHours(30));

        // 449분 (7시간 29분) -> 7시간 (30분 미만 절사)
        assertEquals(7, PayrollCalculator.StatutoryRounding.roundMonthlyHours(449));

        // 450분 (7시간 30분) -> 8시간 (30분 이상 1시간 올림)
        assertEquals(8, PayrollCalculator.StatutoryRounding.roundMonthlyHours(450));

        // 451분 (7시간 31분) -> 8시간
        assertEquals(8, PayrollCalculator.StatutoryRounding.roundMonthlyHours(451));

        // 음수 입력 시 예외 발생 검증
        assertThrows(IllegalArgumentException.class, () ->
                PayrollCalculator.StatutoryRounding.roundMonthlyHours(-1));
    }

    @Test
    @DisplayName("② 1時間当たりの賃金額および割増賃金額（50銭境界・HALF_UP）の厳密検証")
    void roundHourlyRate_BoundaryCases() {
        // 324.49엔 (50전 미만) -> 324엔 (버림)
        assertEquals(324, PayrollCalculator.StatutoryRounding.roundHourlyRate(new BigDecimal("324.49")));

        // 324.50엔 (50전 이상) -> 325엔 (올림, HALF_UP)
        assertEquals(325, PayrollCalculator.StatutoryRounding.roundHourlyRate(new BigDecimal("324.50")));

        // 324.51엔 -> 325엔
        assertEquals(325, PayrollCalculator.StatutoryRounding.roundHourlyRate(new BigDecimal("324.51")));

        // 0.49엔 -> 0엔
        assertEquals(0, PayrollCalculator.StatutoryRounding.roundHourlyRate(new BigDecimal("0.49")));

        // 0.50엔 -> 1엔
        assertEquals(1, PayrollCalculator.StatutoryRounding.roundHourlyRate(new BigDecimal("0.50")));

        // null 입력 시 예외 검증
        assertThrows(IllegalArgumentException.class, () ->
                PayrollCalculator.StatutoryRounding.roundHourlyRate(null));
    }

    @Test
    @DisplayName("③ 月間割増手当総額の端数処理（50銭境界・HALF_UP）の厳密検証")
    void roundMonthlyAllowanceTotal_BoundaryCases() {
        // 12345.49엔 (50전 미만) -> 12345엔
        assertEquals(12345, PayrollCalculator.StatutoryRounding.roundMonthlyAllowanceTotal(new BigDecimal("12345.49")));

        // 12345.50엔 (50전 이상) -> 12346엔 (올림, HALF_UP)
        assertEquals(12346, PayrollCalculator.StatutoryRounding.roundMonthlyAllowanceTotal(new BigDecimal("12345.50")));

        // 12345.51엔 -> 12346엔
        assertEquals(12346, PayrollCalculator.StatutoryRounding.roundMonthlyAllowanceTotal(new BigDecimal("12345.51")));

        // null 입력 시 예외 검증
        assertThrows(IllegalArgumentException.class, () ->
                PayrollCalculator.StatutoryRounding.roundMonthlyAllowanceTotal(null));
    }
}


## Revised targeted test result

{
  "gradle_exit_code": 0,
  "junit_xml_count": 1,
  "tests": 6,
  "failures": 0,
  "errors": 0,
  "skipped": 0,
  "junit_parse_errors": [],
  "result": "PASS"
}


## Latest full regression result

{
  "gradle_exit_code": 0,
  "junit_xml_count": 37,
  "tests": 92,
  "failures": 0,
  "errors": 0,
  "skipped": 0,
  "junit_parse_errors": [],
  "result": "PASS"
}


## Labor calculation engine boundary tests
package com.example.kintai.domain.attendance.util;

import com.example.kintai.domain.payroll.util.PayrollCalculator;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 日本労働基準法第37条に基づく時間計算・手当算定エンジンの単体テスト（Unit Test）。
 * タイムライン・スライシングによる所定内、深夜、時間外、および重畳割増（150%）の正確性を多角的に検証する。
 */
@DisplayName("労働基準法第37条 労働時間・割増賃金算定エンジン 単体テスト")
class LaborCalculationEngineTest {

    @Nested
    @DisplayName("タイムライン・スライシング時間算定テスト")
    class TimelineSlicingTimeTest {

        @Test
        @DisplayName("標準日勤8時間（09:00~18:00、休憩1時間）: 所定内100%のみ発生し残業・深夜は0分であること")
        void standardDayShift_ShouldBeOnlyRegularWork() {
            LocalDateTime in = LocalDateTime.of(2026, 11, 9, 9, 0);
            LocalDateTime out = LocalDateTime.of(2026, 11, 9, 18, 0);
            List<AttendanceTimeCalculator.BreakInterval> breaks = List.of(
                    new AttendanceTimeCalculator.BreakInterval(
                            LocalDateTime.of(2026, 11, 9, 12, 0),
                            LocalDateTime.of(2026, 11, 9, 13, 0)
                    )
            );

            AttendanceTimeCalculator.DetailedCalculationResult result =
                    AttendanceTimeCalculator.calculateDetailed(in, out, breaks);

            assertThat(result.totalStayMinutes()).isEqualTo(540); // 9時間
            assertThat(result.totalBreakMinutes()).isEqualTo(60);  // 1時間
            assertThat(result.netWorkMinutes()).isEqualTo(480);    // 実働8時間
            assertThat(result.regularWorkMinutes()).isEqualTo(480);
            assertThat(result.regularNightWorkMinutes()).isZero();
            assertThat(result.overtimeWorkMinutes()).isZero();
            assertThat(result.nightOvertimeWorkMinutes()).isZero();
            assertThat(result.nightWorkMinutes()).isZero();
            assertThat(result.overtimeMinutes()).isZero();
        }

        @Test
        @DisplayName("日中残業（09:00~21:00、休憩1時間）: 8時間超過分が通常残業（125%）として分単位で分離されること")
        void overtimeDayShift_ShouldDistinguishStatutoryOvertime() {
            LocalDateTime in = LocalDateTime.of(2026, 11, 9, 9, 0);
            LocalDateTime out = LocalDateTime.of(2026, 11, 9, 21, 0);
            List<AttendanceTimeCalculator.BreakInterval> breaks = List.of(
                    new AttendanceTimeCalculator.BreakInterval(
                            LocalDateTime.of(2026, 11, 9, 13, 0),
                            LocalDateTime.of(2026, 11, 9, 14, 0)
                    )
            );

            AttendanceTimeCalculator.DetailedCalculationResult result =
                    AttendanceTimeCalculator.calculateDetailed(in, out, breaks);

            assertThat(result.netWorkMinutes()).isEqualTo(660); // 11時間実働
            assertThat(result.regularWorkMinutes()).isEqualTo(480); // 所定内8時間
            assertThat(result.overtimeWorkMinutes()).isEqualTo(180); // 残業3時間
            assertThat(result.regularNightWorkMinutes()).isZero();
            assertThat(result.nightOvertimeWorkMinutes()).isZero();
            assertThat(result.overtimeMinutes()).isEqualTo(180);
        }

        @Test
        @DisplayName("労働基準法第37条核心 重畳割増（13:00~翌01:00、休憩17:00~18:00）: 深夜帯かつ残業の重複が150%割増として厳密に分離されること")
        void overlappingNightOvertime_ShouldCalculateDoubleAdditionCorrectly() {
            LocalDateTime in = LocalDateTime.of(2026, 11, 9, 13, 0);
            LocalDateTime out = LocalDateTime.of(2026, 11, 10, 1, 0);
            List<AttendanceTimeCalculator.BreakInterval> breaks = List.of(
                    new AttendanceTimeCalculator.BreakInterval(
                            LocalDateTime.of(2026, 11, 9, 17, 0),
                            LocalDateTime.of(2026, 11, 9, 18, 0)
                    )
            );

            AttendanceTimeCalculator.DetailedCalculationResult result =
                    AttendanceTimeCalculator.calculateDetailed(in, out, breaks);

            // 13:00~17:00(240分) + 18:00~22:00(240分) = 480分（所定内通常）
            // 22:00~翌01:00(180分) = 8時間超過 かつ 22:00~05:00深夜 -> 法定外深夜残業（重畳割増）
            assertThat(result.netWorkMinutes()).isEqualTo(660);
            assertThat(result.regularWorkMinutes()).isEqualTo(480);
            assertThat(result.regularNightWorkMinutes()).isZero();
            assertThat(result.overtimeWorkMinutes()).isZero();
            assertThat(result.nightOvertimeWorkMinutes()).isEqualTo(180); // 重畳割増対象3時間
            assertThat(result.nightWorkMinutes()).isEqualTo(180);
            assertThat(result.overtimeMinutes()).isEqualTo(180);
        }

        @Test
        @DisplayName("深夜休憩控除（18:00~翌04:00、深夜帯23:00~00:00に1時間休憩）: 深夜休憩が適正に控除され所定内深夜と深夜残業が正しく配分されること")
        void nightBreakDeduction_ShouldDeductOnlyFromNightRange() {
            LocalDateTime in = LocalDateTime.of(2026, 11, 9, 18, 0);
            LocalDateTime out = LocalDateTime.of(2026, 11, 10, 4, 0);
            List<AttendanceTimeCalculator.BreakInterval> breaks = List.of(
                    new AttendanceTimeCalculator.BreakInterval(
                            LocalDateTime.of(2026, 11, 9, 23, 0),
                            LocalDateTime.of(2026, 11, 10, 0, 0)
                    )
            );

            AttendanceTimeCalculator.DetailedCalculationResult result =
                    AttendanceTimeCalculator.calculateDetailed(in, out, breaks);

            // 18:00~22:00 (240分 通常)
            // 22:00~23:00 (60分 深夜) -> 累計300分
            // 23:00~00:00 (休憩 60分)
            // 00:00~03:00 (180分 深夜) -> 累計480分到達（所定内深夜 = 60 + 180 = 240分）
            // 03:00~04:00 (60分 深夜かつ残業 -> 深夜残業 = 60分）
            assertThat(result.totalStayMinutes()).isEqualTo(600);
            assertThat(result.totalBreakMinutes()).isEqualTo(60);
            assertThat(result.netWorkMinutes()).isEqualTo(540); // 9時間
            assertThat(result.regularWorkMinutes()).isEqualTo(240); // 4時間
            assertThat(result.regularNightWorkMinutes()).isEqualTo(240); // 4時間
            assertThat(result.overtimeWorkMinutes()).isZero();
            assertThat(result.nightOvertimeWorkMinutes()).isEqualTo(60); // 1時間（重畳割増）
            assertThat(result.nightWorkMinutes()).isEqualTo(300); // 5時間（実働深夜）
            assertThat(result.overtimeMinutes()).isEqualTo(60);
        }
    }

    @Nested
    @DisplayName("給与割増計算および端数処理テスト")
    class PayrollAllowanceAndRoundingTest {

        @Test
        @DisplayName("時給1,500円重畳割増勤務: 総計視点と区分別内訳視点の支給額が完全に一致すること")
        void overlappingNightOvertime_PayEquivalenceTest() {
            LocalDateTime in = LocalDateTime.of(2026, 11, 9, 13, 0);
            LocalDateTime out = LocalDateTime.of(2026, 11, 10, 1, 0);
            List<AttendanceTimeCalculator.BreakInterval> breaks = List.of(
                    new AttendanceTimeCalculator.BreakInterval(
                            LocalDateTime.of(2026, 11, 9, 17, 0),
                            LocalDateTime.of(2026, 11, 9, 18, 0)
                    )
            );

            AttendanceTimeCalculator.DetailedCalculationResult calc =
                    AttendanceTimeCalculator.calculateDetailed(in, out, breaks);

            int wage = 1500;
            PayrollCalculator.DetailedDailyPayResult pay = PayrollCalculator.calculateDetailedDailyPay(wage, calc);

            // 基本視点: 基本給(100%) + 深夜手当(25%) + 残業手当(25%)
            // 1500 * 660 / 60 = 16,500
            // 1500 * 180 / 240 = 1,125
            // 1500 * 180 / 240 = 1,125
            // 合計 = 18,750
            assertThat(pay.basePay()).isEqualTo(16500);
            assertThat(pay.nightAllowance()).isEqualTo(1125);
            assertThat(pay.overtimeAllowance()).isEqualTo(1125);
            assertThat(pay.totalPay()).isEqualTo(18750);

            // 区分視点: 所定内通常(100%) + 法定外深夜(150%)
            // regularPay = 1500 * 480 / 60 = 12,000
            // nightOvertimePay = 1500 * 1.5 * 180 / 60 = 6,750
            // 合計 = 12,000 + 6,750 = 18,750
            assertThat(pay.regularPay()).isEqualTo(12000);
            assertThat(pay.regularNightPay()).isZero();
            assertThat(pay.overtimePay()).isZero();
            assertThat(pay.nightOvertimePay()).isEqualTo(6750);
            assertThat(pay.regularPay() + pay.nightOvertimePay()).isEqualTo(pay.totalPay());
        }

        @Test
        @DisplayName("昭和62年3月14日付 基発第150号基準 1円単位四捨五入(HALF_UP)丸め検証")
        void yenRounding_ShouldRoundHalfUpCorrectly() {
            // 時給1,280円で33分労働の場合: 1280 * 33 / 60 = 704.0円 -> 704円
            assertThat(PayrollCalculator.calculateDailyPay(1280, 33, 0, 0).totalPay()).isEqualTo(704);

            // 時給1,280円で35分労働の場合: 1280 * 35 / 60 = 746.666...円 -> 747円
            assertThat(PayrollCalculator.calculateDailyPay(1280, 35, 0, 0).totalPay()).isEqualTo(747);
        }
    }

    @Nested
    @DisplayName("異常系・境界値バリデーションテスト")
    class ValidationTest {

        @Test
        @DisplayName("退勤日時が出勤日時以前の場合はIllegalArgumentExceptionがスローされること")
        void clockOutBeforeClockIn_ShouldThrowException() {
            LocalDateTime in = LocalDateTime.of(2026, 11, 9, 18, 0);
            LocalDateTime out = LocalDateTime.of(2026, 11, 9, 9, 0);

            assertThatThrownBy(() -> AttendanceTimeCalculator.calculateDetailed(in, out, null))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("退勤日時は出勤日時より後である必要があります");
        }

        @Test
        @DisplayName("休憩時間が勤務範囲外の場合はIllegalArgumentExceptionがスローされること")
        void breakOutOfRange_ShouldThrowException() {
            LocalDateTime in = LocalDateTime.of(2026, 11, 9, 9, 0);
            LocalDateTime out = LocalDateTime.of(2026, 11, 9, 18, 0);
            List<AttendanceTimeCalculator.BreakInterval> breaks = List.of(
                    new AttendanceTimeCalculator.BreakInterval(
                            LocalDateTime.of(2026, 11, 9, 18, 1),
                            LocalDateTime.of(2026, 11, 9, 19, 0)
                    )
            );

            assertThatThrownBy(() -> AttendanceTimeCalculator.calculateDetailed(in, out, breaks))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("休憩時間は勤務時間内である必要があります");
        }
    }
}



## Transaction and pessimistic-lock source excerpts

`AttendanceService.punch` uses method-level `@Transactional` and calls `lockStoreStaffForUpdate` before reading active attendance.

```java

        this.attendanceMapper = attendanceMapper;
    }

    /**
     * リアルタイム打刻処理（出勤・休憩開始・休憩終了・退勤）
     */
    @Transactional
    public AttendanceDto punch(AttendancePunchDto punchDto) {
        if (punchDto == null) {
            throw new IllegalArgumentException("打刻データは必須です。");
        }
        Long storeStaffId = punchDto.getStoreStaffId();
        String punchType = punchDto.getPunchType();
        if (storeStaffId == null || storeStaffId <= 0) {
            throw new IllegalArgumentException("有効なスタッフIDは必須です。");
        }
        if (punchType == null || !List.of("CLOCK_IN", "CLOCK_OUT", "BREAK_START", "BREAK_END").contains(punchType)) {
            throw new IllegalArgumentException("有効な打刻タイプは必須です。");
        }
        LocalDateTime now = LocalDateTime.now();
        LocalDate today = now.toLocalDate();

        // 排他制御: 同時打刻の競合を防止する悲観的ロック
        attendanceMapper.lockStoreStaffForUpdate(storeStaffId);

        AttendanceDto active = attendanceMapper.findActiveAttendance(storeStaffId);

        switch (punchType) {
            case "CLOCK_IN":
                return handleClockIn(punchDto, today, now, active);

            case "BREAK_START":
                return handleBreakStart(now, active);

            case "BREAK_END":
                return handleBreakEnd(now, active);

            case "CLOCK_OUT":
                return handleClockOut(now, active);

            default:
                throw new IllegalArgumentException("無効な打刻タイプです: " + punchType);

```

`AttendanceMapper.xml` lock query:

```xml

    <select id="lockStoreStaffForUpdate" resultType="java.lang.Long">
        SELECT store_staff_id
        FROM store_staff
        WHERE store_staff_id = #{storeStaffId}
        FOR UPDATE
    </select>

    <select id="findStoreIdByStoreStaffId" resultType="java.lang.Long">

```

`PayrollService.calculatePayrollForStaff` is method-level `@Transactional`, locks staff and existing payroll rows before recalculation.

```java


    @Transactional
    public PayrollDto calculatePayrollForStaff(Long storeId, Long staffId, String targetYearMonth, LocalDate startDate, LocalDate endDate) {
        // 排他制御: 同時計算競合を防止する悲観的ロック (スタッフ単位)
        payrollMapper.lockStoreStaffForUpdate(staffId);

        // 確定済み給与の不変性保護 (CONFIRMED / PAID の場合は再計算・上書きを遮断, 行ロック付き照会)
        PayrollDto existingPayroll = payrollMapper.findByStaffAndMonthForUpdate(staffId, targetYearMonth);
        if (existingPayroll != null && !"DRAFT".equals(existingPayroll.getPayrollStatus())) {
            throw new IllegalStateException("指定年月 (" + targetYearMonth + ") の給与は既に確定済み(" +
                    existingPayroll.getPayrollStatus() + ") のため、再計算できません。(給与不変性保護)");
        }

        // 未処理勤怠の確認（打刻途中や未承認申請がある場合は計算中断）
        int unresolvedCount = payrollMapper.countUnresolvedAttendance(staffId, startDate, endDate);
        if (unresolvedCount > 0) {
            throw new IllegalStateException("未承認または打刻途中の勤怠が存在するため、給与計算を中断しました。(職員ID: " + staffId + ")");
        }

```

`PayrollMapper.xml` staff lock query:

```xml

    <select id="lockStoreStaffForUpdate" resultType="java.lang.Long">
        SELECT store_staff_id
        FROM store_staff
        WHERE store_staff_id = #{storeStaffId}
        FOR UPDATE
    </select>

    <select id="lockStorePayrollsForUpdate" resultType="com.example.kintai.domain.payroll.dto.PayrollDto">

```
