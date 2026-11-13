# Day 11 final local model audit input

## Integration test [src\test\java\com\example\kintai\domain\payroll\service\AnnualPayrollScenarioIntegrationTest.java]
``text
package com.example.kintai.domain.payroll.service;

import com.example.kintai.domain.attendance.dto.LaborDetailDto;
import com.example.kintai.domain.attendance.mapper.AttendanceMapper;
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
    private AttendanceMapper attendanceMapper;

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
            assertEquals(1000, payroll.getAppliedTransportLimit(), "contract transport limit for month " + shift.month());
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

    @Test
    void contractHistoryUsesInclusiveStartAndExclusiveEndDates() {
        assertEquals(1300, attendanceMapper.findHourlyWageForStaffOnDate(staffId, LocalDate.of(2026, 6, 30)));
        assertEquals(1400, attendanceMapper.findHourlyWageForStaffOnDate(staffId, LocalDate.of(2026, 7, 1)));
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

````

## Attendance mapper [src\main\resources\mapper\attendance\AttendanceMapper.xml]
``text
<?xml version="1.0" encoding="UTF-8" ?>
<!DOCTYPE mapper PUBLIC "-//mybatis.org//DTD Mapper 3.0//EN"
        "http://mybatis.org/dtd/mybatis-3-mapper.dtd">

<mapper namespace="com.example.kintai.domain.attendance.mapper.AttendanceMapper">

    <select id="findActiveAttendance" resultType="com.example.kintai.domain.attendance.dto.AttendanceDto">
        SELECT
            attendance_id,
            store_id,
            store_staff_id,
            shift_id,
            work_date,
            clock_in_at,
            clock_out_at,
            break_start_at,
            break_end_at,
            total_break_minutes,
            net_work_minutes,
            night_work_minutes,
            overtime_minutes,
            clock_in_method,
            attendance_status
        FROM attendance
        WHERE store_staff_id = #{storeStaffId}
          AND attendance_status IN ('WORKING', 'ON_BREAK')
        ORDER BY attendance_id DESC
        LIMIT 1
    </select>

    <select id="findById" resultType="com.example.kintai.domain.attendance.dto.AttendanceDto">
        SELECT
            attendance_id,
            store_id,
            store_staff_id,
            shift_id,
            work_date,
            clock_in_at,
            clock_out_at,
            break_start_at,
            break_end_at,
            total_break_minutes,
            net_work_minutes,
            night_work_minutes,
            overtime_minutes,
            clock_in_method,
            attendance_status
        FROM attendance
        WHERE attendance_id = #{attendanceId}
    </select>

    <insert id="insertAttendance" useGeneratedKeys="true" keyProperty="attendanceId">
        INSERT INTO attendance (
            store_id,
            store_staff_id,
            shift_id,
            work_date,
            clock_in_at,
            clock_out_at,
            break_start_at,
            break_end_at,
            total_break_minutes,
            net_work_minutes,
            night_work_minutes,
            overtime_minutes,
            clock_in_method,
            attendance_status
        ) VALUES (
            #{storeId},
            #{storeStaffId},
            #{shiftId},
            #{workDate},
            #{clockInAt},
            #{clockOutAt},
            #{breakStartAt},
            #{breakEndAt},
            #{totalBreakMinutes},
            #{netWorkMinutes},
            #{nightWorkMinutes},
            #{overtimeMinutes},
            #{clockInMethod},
            #{attendanceStatus}
        )
    </insert>

    <update id="updateAttendance">
        UPDATE attendance
        <set>
            <if test="clockInAt != null">clock_in_at = #{clockInAt},</if>
            <if test="clockOutAt != null">clock_out_at = #{clockOutAt},</if>
            <if test="breakStartAt != null">break_start_at = #{breakStartAt},</if>
            <if test="breakEndAt != null">break_end_at = #{breakEndAt},</if>
            <if test="totalBreakMinutes != null">total_break_minutes = #{totalBreakMinutes},</if>
            <if test="netWorkMinutes != null">net_work_minutes = #{netWorkMinutes},</if>
            <if test="nightWorkMinutes != null">night_work_minutes = #{nightWorkMinutes},</if>
            <if test="overtimeMinutes != null">overtime_minutes = #{overtimeMinutes},</if>
            <if test="clockInMethod != null">clock_in_method = #{clockInMethod},</if>
            <if test="attendanceStatus != null">attendance_status = #{attendanceStatus},</if>
            updated_at = CURRENT_TIMESTAMP
        </set>
        WHERE attendance_id = #{attendanceId}
    </update>

    <insert id="insertBreak" useGeneratedKeys="true" keyProperty="attendanceBreakId">
        INSERT INTO attendance_break (
            attendance_id,
            break_start_at,
            break_end_at,
            duration_minutes
        ) VALUES (
            #{attendanceId},
            #{breakStartAt},
            #{breakEndAt},
            #{durationMinutes}
        )
    </insert>

    <select id="findBreaksByAttendanceId" resultType="com.example.kintai.domain.attendance.dto.AttendanceBreakDto">
        SELECT
            attendance_break_id,
            attendance_id,
            break_start_at,
            break_end_at,
            duration_minutes
        FROM attendance_break
        WHERE attendance_id = #{attendanceId}
        ORDER BY break_start_at ASC
    </select>

    <delete id="deleteBreaksByAttendanceId">
        DELETE FROM attendance_break
        WHERE attendance_id = #{attendanceId}
    </delete>

    <select id="isPayrollConfirmedForDate" resultType="boolean">
        SELECT COUNT(*) &gt; 0
        FROM payroll
        WHERE store_staff_id = #{storeStaffId}
          AND #{workDate} &gt;= range_start_date
          AND #{workDate} &lt;= range_end_date
          AND payroll_status IN ('CONFIRMED', 'PAID')
    </select>

    <select id="lockStoreStaffForUpdate" resultType="java.lang.Long">
        SELECT store_staff_id
        FROM store_staff
        WHERE store_staff_id = #{storeStaffId}
        FOR UPDATE
    </select>

    <select id="findStoreIdByStoreStaffId" resultType="java.lang.Long">
        SELECT store_id
        FROM store_staff
        WHERE store_staff_id = #{storeStaffId}
        LIMIT 1
    </select>

    <select id="findConfirmedShiftIdForDate" resultType="java.lang.Long">
        SELECT shift_id
        FROM shift_schedule
        WHERE store_staff_id = #{storeStaffId}
          AND work_date = #{workDate}
          AND status = 'CONFIRMED'
        LIMIT 1
    </select>

    <select id="findCompletedAttendancesForStaffAndPeriod" resultType="com.example.kintai.domain.attendance.dto.AttendanceDto">
        SELECT
            attendance_id,
            store_id,
            store_staff_id,
            shift_id,
            work_date,
            clock_in_at,
            clock_out_at,
            total_break_minutes,
            net_work_minutes,
            night_work_minutes,
            overtime_minutes,
            clock_in_method,
            attendance_status
        FROM attendance
        WHERE store_staff_id = #{storeStaffId}
          AND work_date &gt;= #{startDate}
          AND work_date &lt;= #{endDate}
          AND attendance_status = 'COMPLETED'
        ORDER BY work_date ASC
    </select>

    <!-- 勤怠修正申請の新規登録 -->
    <insert id="insertModRequest" useGeneratedKeys="true" keyProperty="modRequestId">
        INSERT INTO attendance_mod_request (
            attendance_id,
            store_id,
            store_staff_id,
            work_date,
            before_clock_in_at,
            before_clock_out_at,
            before_break_minutes,
            requested_clock_in_at,
            requested_clock_out_at,
            requested_break_minutes,
            reason,
            approval_status
        ) VALUES (
            #{attendanceId},
            #{storeId},
            #{storeStaffId},
            #{workDate},
            #{beforeClockInAt},
            #{beforeClockOutAt},
            #{beforeBreakMinutes},
            #{requestedClockInAt},
            #{requestedClockOutAt},
            #{requestedBreakMinutes},
            #{reason},
            'PENDING'
        )
    </insert>

    <!-- 勤怠修正休憩区間の登録 -->
    <insert id="insertModBreak">
        INSERT INTO attendance_mod_break (
            mod_request_id,
            break_set_type,
            break_start_at,
            break_end_at,
            duration_minutes
        ) VALUES (
            #{modRequestId},
            #{breakSetType},
            #{startAt},
            #{endAt},
            #{duration}
        )
    </insert>

    <!-- 店舗別勤怠修正申請一覧取得 -->
    <select id="findModRequestsByStoreId" resultType="com.example.kintai.domain.attendance.dto.AttendanceModResponseDto">
        SELECT
            r.mod_request_id,
            r.attendance_id,
            r.store_id,
            r.store_staff_id,
            m.name AS staff_name,
            r.work_date,
            r.before_clock_in_at,
            r.before_clock_out_at,
            r.before_break_minutes,
            r.requested_clock_in_at,
            r.requested_clock_out_at,
            r.requested_break_minutes,
            r.reason,
            r.approval_status,
            r.reject_reason,
            r.created_at
        FROM attendance_mod_request r
        JOIN store_staff ss ON r.store_staff_id = ss.store_staff_id
        JOIN member m ON ss.member_id = m.member_id
        WHERE r.store_id = #{storeId}
        <if test="status != null and status != ''">
            AND r.approval_status = #{status}
        </if>
        ORDER BY r.mod_request_id DESC
    </select>

    <!-- 修正申請単件取得 -->
    <select id="findModRequestById" resultType="com.example.kintai.domain.attendance.dto.AttendanceModResponseDto">
        SELECT
            r.mod_request_id,
            r.attendance_id,
            r.store_id,
            r.store_staff_id,
            m.name AS staff_name,
            r.work_date,
            r.before_clock_in_at,
            r.before_clock_out_at,
            r.before_break_minutes,
            r.requested_clock_in_at,
            r.requested_clock_out_at,
            r.requested_break_minutes,
            r.reason,
            r.approval_status,
            r.reject_reason,
            r.created_at
        FROM attendance_mod_request r
        JOIN store_staff ss ON r.store_staff_id = ss.store_staff_id
        JOIN member m ON ss.member_id = m.member_id
        WHERE r.mod_request_id = #{modRequestId}
    </select>

    <!-- 修正申請の休憩区間一覧取得 -->
    <select id="findModBreaksByRequestId" resultType="com.example.kintai.domain.attendance.dto.AttendanceBreakDto">
        SELECT
            mod_break_id AS attendance_break_id,
            mod_request_id AS attendance_id,
            break_set_type,
            break_start_at,
            break_end_at,
            duration_minutes
        FROM attendance_mod_break
        WHERE mod_request_id = #{modRequestId}
          AND break_set_type = #{breakSetType}
        ORDER BY break_start_at ASC
    </select>

    <!-- 複数修正申請の全休憩区間一括取得（N+1クエリ解消） -->
    <select id="findModBreaksByRequestIds" resultType="com.example.kintai.domain.attendance.dto.AttendanceBreakDto">
        SELECT
            mod_break_id AS attendance_break_id,
            mod_request_id AS attendance_id,
            break_set_type,
            break_start_at,
            break_end_at,
            duration_minutes
        FROM attendance_mod_break
        WHERE mod_request_id IN
        <foreach item="id" collection="requestIds" open="(" separator="," close=")">
            #{id}
        </foreach>
        ORDER BY break_start_at ASC
    </select>

    <!-- 修正申請の承認・却下状態更新 (CAS: PENDING状態の場合のみ1件更新) -->
    <update id="updateModRequestReview">
        UPDATE attendance_mod_request
        SET approval_status = #{status},
            reviewer_id = #{reviewerId},
            reject_reason = #{rejectReason},
            reviewed_at = #{reviewedAt},
            updated_at = CURRENT_TIMESTAMP
        WHERE mod_request_id = #{modRequestId}
          AND approval_status = 'PENDING'
    </update>

    <!-- 勤怠ステータスの単体更新 -->
    <update id="updateAttendanceStatus">
        UPDATE attendance
        SET attendance_status = #{attendanceStatus},
            updated_at = CURRENT_TIMESTAMP
        WHERE attendance_id = #{attendanceId}
    </update>

    <!-- 同一スタッフにおける勤務時間の重複件数をカウント（勤務時間重複防止） -->
    <select id="countOverlappingAttendances" resultType="int">
        SELECT COUNT(*)
        FROM attendance
        WHERE store_staff_id = #{storeStaffId}
          <if test="excludeAttendanceId != null">
              AND attendance_id != #{excludeAttendanceId}
          </if>
          AND clock_in_at &lt; #{clockOutAt}
          AND (clock_out_at IS NULL OR clock_out_at &gt; #{clockInAt})
    </select>

    <!-- 事後新規登録された勤怠IDを修正申請レコードに紐付け -->
    <update id="updateModRequestAttendanceId">
        UPDATE attendance_mod_request
        SET attendance_id = #{attendanceId},
            updated_at = CURRENT_TIMESTAMP
        WHERE mod_request_id = #{modRequestId}
    </update>

    <!-- 店舗および指定勤務日の全スタッフ勤怠実績取得（シフト・勤怠リアルタイム対照用） -->
    <select id="findAttendancesByStoreAndDate" resultType="com.example.kintai.domain.attendance.dto.AttendanceDto">
        SELECT
            attendance_id,
            store_id,
            store_staff_id,
            shift_id,
            work_date,
            clock_in_at,
            clock_out_at,
            break_start_at,
            break_end_at,
            total_break_minutes,
            net_work_minutes,
            night_work_minutes,
            overtime_minutes,
            clock_in_method,
            attendance_status
        FROM attendance
        WHERE store_id = #{storeId}
          AND work_date = #{workDate}
        ORDER BY clock_in_at ASC
    </select>

    <!-- スタッフ氏名取得 -->
    <select id="findStaffNameByStoreStaffId" resultType="java.lang.String">
        SELECT m.name
        FROM store_staff ss
        JOIN member m ON ss.member_id = m.member_id
        WHERE ss.store_staff_id = #{storeStaffId}
        LIMIT 1
    </select>

    <!-- 勤務日時点の契約時給取得 -->
    <select id="findHourlyWageForStaffOnDate" resultType="java.lang.Integer">
        SELECT hourly_wage
        FROM store_staff_contract_history
        WHERE store_staff_id = #{storeStaffId}
          AND effective_from &lt;= #{workDate}
          AND (effective_to IS NULL OR effective_to &gt; #{workDate})
        ORDER BY effective_from DESC
        LIMIT 1
    </select>

    <!-- 同一スタッフ・同一勤務日の先行完了勤務の実労働時間合計（分）を取得（分割勤務中抜け対応） -->
    <select id="sumPriorNetWorkMinutesOnDate" resultType="java.lang.Integer">
        SELECT COALESCE(SUM(net_work_minutes), 0)
        FROM attendance
        WHERE store_staff_id = #{storeStaffId}
          AND work_date = #{workDate}
          AND attendance_status = 'COMPLETED'
          <if test="excludeAttendanceId != null">
              AND attendance_id != #{excludeAttendanceId}
          </if>
          <if test="beforeTime != null">
              AND clock_out_at &lt;= #{beforeTime}
          </if>
    </select>

    <!-- 同一スタッフ・同一勤務日の先行完了勤務の実労働時間合計（秒精度）を取得（分割勤務中抜け対応） -->
    <select id="sumPriorNetWorkSecondsOnDate" resultType="java.lang.Long">
        SELECT COALESCE(SUM(TIMESTAMPDIFF(SECOND, clock_in_at, clock_out_at) - (total_break_minutes * 60)), 0)
        FROM attendance
        WHERE store_staff_id = #{storeStaffId}
          AND work_date = #{workDate}
          AND attendance_status = 'COMPLETED'
          <if test="excludeAttendanceId != null">
              AND attendance_id != #{excludeAttendanceId}
          </if>
          <if test="beforeTime != null">
              AND clock_out_at &lt;= #{beforeTime}
          </if>
    </select>

</mapper>

````

## Relevant schema [src\main\resources\db\schema.sql]
``text
-- ==============================================================================
-- 日本型 勤怠・シフト・給与管理システム (Kintai Management System)
-- データベース スキーマ 定義書 (schema.sql) - rev.3 最終完全版
-- ==============================================================================


-- 1. 会員 (オーナーおよびスタッフアカウントマスタ)
CREATE TABLE IF NOT EXISTS member (
    member_id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '会員ID (PK)',
    email VARCHAR(100) NOT NULL UNIQUE COMMENT 'ログインメールアドレス',
    password VARCHAR(255) NOT NULL COMMENT 'BCrypt暗号化パスワード',
    name VARCHAR(50) NOT NULL COMMENT '氏名',
    phone VARCHAR(20) NOT NULL COMMENT '電話番号',
    role VARCHAR(20) NOT NULL COMMENT '権限 (ROLE_OWNER, ROLE_STAFF)',
    is_active BOOLEAN NOT NULL DEFAULT TRUE COMMENT 'アカウント有効状態',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 2. 店舗 (店舗情報および給与締日・打刻基準設定)
CREATE TABLE IF NOT EXISTS store (
    store_id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '店舗ID (PK)',
    owner_id BIGINT NOT NULL COMMENT 'オーナー会員ID (FK)',
    store_name VARCHAR(100) NOT NULL COMMENT '店舗名',
    prefecture_code CHAR(2) NOT NULL DEFAULT '13' COMMENT '都道府県コード (初期値 13=東京都)',
    invite_code VARCHAR(20) NOT NULL UNIQUE COMMENT 'スタッフ登録用招待コード',
    qr_token VARCHAR(100) NOT NULL UNIQUE COMMENT '店舗別打刻QRトークン',
    open_time TIME NOT NULL DEFAULT '09:00:00' COMMENT '基本開店時刻',
    close_time TIME NOT NULL DEFAULT '23:00:00' COMMENT '基本閉店時刻',
    pay_cutoff_day INT NOT NULL DEFAULT 99 COMMENT '給与締日 (1~28 または 99=末日締)',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT fk_store_owner FOREIGN KEY (owner_id) REFERENCES member (member_id),
    CONSTRAINT chk_store_cutoff CHECK (pay_cutoff_day BETWEEN 1 AND 28 OR pay_cutoff_day = 99)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 3. 店舗所属スタッフ (掛け持ちマッピングおよび個別時給・通勤手当契約)
CREATE TABLE IF NOT EXISTS store_staff (
    store_staff_id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '店舗スタッフ所属ID (PK)',
    store_id BIGINT NOT NULL COMMENT '店舗ID (FK)',
    member_id BIGINT NOT NULL COMMENT '会員ID (FK)',
    hourly_wage INT NOT NULL COMMENT '現在表示用時給 (円; 計算元本は契約履歴を参照)',
    daily_transport_fee INT NOT NULL DEFAULT 0 COMMENT '1日往復交通費 (円)',
    monthly_transport_limit INT NOT NULL DEFAULT 15000 COMMENT '月額交通費支給上限 (円)',
    employment_status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE' COMMENT '在籍状態 (PENDING, ACTIVE, RESIGNED)',
    joined_date DATE NOT NULL COMMENT '入社日',
    resigned_date DATE NULL COMMENT '退職日',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT uq_store_member UNIQUE (store_id, member_id),
    CONSTRAINT uq_store_staff_id_store UNIQUE (store_staff_id, store_id),
    CONSTRAINT fk_staff_store FOREIGN KEY (store_id) REFERENCES store (store_id),
    CONSTRAINT fk_staff_member FOREIGN KEY (member_id) REFERENCES member (member_id),
    CONSTRAINT chk_staff_wage CHECK (hourly_wage > 0),
    CONSTRAINT chk_staff_transport CHECK (daily_transport_fee >= 0 AND monthly_transport_limit >= 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 3-A. スタッフ契約条件履歴 (適用日基準; 給与計算時の適用時給・交通費参照マスタ)
CREATE TABLE IF NOT EXISTS store_staff_contract_history (
    contract_history_id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '契約履歴ID (PK)',
    store_staff_id BIGINT NOT NULL COMMENT '店舗スタッフID (FK)',
    effective_from DATE NOT NULL COMMENT '適用開始日 (含む)',
    effective_to DATE NULL COMMENT '適用終了日 (含まない; NULLの場合は現在有効)',
    hourly_wage INT NOT NULL COMMENT '適用時給 (円)',
    daily_transport_fee INT NOT NULL DEFAULT 0 COMMENT '適用1日通勤手当支給額 (円)',
    monthly_transport_limit INT NOT NULL DEFAULT 15000 COMMENT '適用月額通勤手当支給上限 (円)',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_contract_effective_from UNIQUE (store_staff_id, effective_from),
    CONSTRAINT fk_contract_staff FOREIGN KEY (store_staff_id) REFERENCES store_staff (store_staff_id),
    CONSTRAINT chk_contract_dates CHECK (effective_to IS NULL OR effective_to > effective_from),
    CONSTRAINT chk_contract_wage CHECK (hourly_wage > 0),
    CONSTRAINT chk_contract_transport CHECK (daily_transport_fee >= 0 AND monthly_transport_limit >= 0),
    INDEX idx_contract_staff_period (store_staff_id, effective_from, effective_to)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 4. シフトスケジュール (希望シフト提出および店長確定統合テーブル)
CREATE TABLE IF NOT EXISTS shift_schedule (
    shift_id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT 'シフトID (PK)',
    store_id BIGINT NOT NULL COMMENT '店舗ID (FK)',
    store_staff_id BIGINT NOT NULL COMMENT '店舗スタッフID (FK)',
    work_date DATE NOT NULL COMMENT '勤務基準日',
    requested_start_at DATETIME NULL COMMENT '希望出勤日時',
    requested_end_at DATETIME NULL COMMENT '希望退勤日時',
    confirmed_start_at DATETIME NULL COMMENT '確定出勤日時',
    confirmed_end_at DATETIME NULL COMMENT '確定退勤日時',
    planned_break_minutes INT NOT NULL DEFAULT 0 COMMENT '計画休憩時間 (分)',
    status VARCHAR(20) NOT NULL DEFAULT 'SUBMITTED' COMMENT '状態 (SUBMITTED, CONFIRMED, REJECTED)',
    staff_memo VARCHAR(255) NULL COMMENT 'スタッフ側メモ',
    owner_memo VARCHAR(255) NULL COMMENT '店長側メモ',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_shift_store_date (store_id, work_date),
    INDEX idx_shift_staff_date (store_staff_id, work_date),
    CONSTRAINT fk_shift_store FOREIGN KEY (store_id) REFERENCES store (store_id),
    CONSTRAINT fk_shift_staff_store FOREIGN KEY (store_staff_id, store_id) REFERENCES store_staff (store_staff_id, store_id),
    CONSTRAINT chk_shift_break CHECK (planned_break_minutes >= 0),
    CONSTRAINT chk_shift_status CHECK (status IN ('SUBMITTED', 'CONFIRMED', 'REJECTED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 5. 出退勤勤怠実績 (タイムカードおよび日別実労働・深夜・時間外の分単位集計)
CREATE TABLE IF NOT EXISTS attendance (
    attendance_id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '勤怠実績ID (PK)',
    store_id BIGINT NOT NULL COMMENT '店舗ID (FK)',
    store_staff_id BIGINT NOT NULL COMMENT '店舗スタッフID (FK)',
    shift_id BIGINT NULL COMMENT '紐付く確定シフトID (未計画出勤時はNULL)',
    work_date DATE NOT NULL COMMENT '出勤基準日',
    clock_in_at DATETIME NOT NULL COMMENT '出勤打刻日時',
    clock_out_at DATETIME NULL COMMENT '退勤打刻日時',
    break_start_at DATETIME NULL COMMENT '進行中休憩の開始時刻 (ON_BREAK時のみ設定)',
    break_end_at DATETIME NULL COMMENT '直近完了休憩の終了時刻 (合算値はattendance_breakを参照)',
    total_break_minutes INT NOT NULL DEFAULT 0 COMMENT '完了済み休憩合計時間 (分)',
    net_work_minutes INT NOT NULL DEFAULT 0 COMMENT '実労働時間 (分; 休憩を除く)',
    night_work_minutes INT NOT NULL DEFAULT 0 COMMENT '深夜労働時間 (分; 22:00 ~ 05:00)',
    overtime_minutes INT NOT NULL DEFAULT 0 COMMENT '時間外残業時間 (分; 1日8時間=480分超過分)',
    clock_in_method VARCHAR(20) NOT NULL DEFAULT 'BUTTON' COMMENT '打刻方式 (BUTTON, QR, MANUAL_APPROVED)',
    attendance_status VARCHAR(20) NOT NULL DEFAULT 'WORKING' COMMENT 'ステータス (WORKING, ON_BREAK, COMPLETED, MOD_REQUESTED)',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_att_store_date (store_id, work_date),
    INDEX idx_att_staff_date (store_staff_id, work_date),
    CONSTRAINT fk_att_store FOREIGN KEY (store_id) REFERENCES store (store_id),
    CONSTRAINT fk_att_staff_store FOREIGN KEY (store_staff_id, store_id) REFERENCES store_staff (store_staff_id, store_id),
    CONSTRAINT fk_att_shift FOREIGN KEY (shift_id) REFERENCES shift_schedule (shift_id),
    CONSTRAINT chk_attendance_minutes CHECK (total_break_minutes >= 0 AND net_work_minutes >= 0 AND night_work_minutes >= 0 AND overtime_minutes >= 0),
    CONSTRAINT chk_attendance_status CHECK (attendance_status IN ('WORKING', 'ON_BREAK', 'COMPLETED', 'MOD_REQUESTED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 5-A. 勤怠別休憩区間 (1日複数回の休憩区間を完全保存)
CREATE TABLE IF NOT EXISTS attendance_break (
    attendance_break_id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '休憩区間ID (PK)',
    attendance_id BIGINT NOT NULL COMMENT '勤怠ID (FK)',
    break_start_at DATETIME NOT NULL COMMENT '休憩開始日時',
    break_end_at DATETIME NOT NULL COMMENT '休憩終了日時',
    duration_minutes INT NOT NULL COMMENT '休憩区間長 (分)',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_break_attendance (attendance_id, break_start_at),
    CONSTRAINT fk_break_attendance FOREIGN KEY (attendance_id) REFERENCES attendance (attendance_id),
    CONSTRAINT chk_break_range CHECK (break_end_at > break_start_at),
    CONSTRAINT chk_break_duration CHECK (duration_minutes > 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 6. 勤怠修正申請 (打刻漏れ・修正および店長承認の監査ログ)
CREATE TABLE IF NOT EXISTS attendance_mod_request (
    mod_request_id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '勤怠修正申請ID (PK)',
    attendance_id BIGINT NULL COMMENT '対象勤怠ID (打刻漏れによる新規生成時はNULL許容)',
    store_id BIGINT NOT NULL COMMENT '店舗ID (FK)',
    store_staff_id BIGINT NOT NULL COMMENT '申請スタッフID (FK)',
    work_date DATE NOT NULL COMMENT '勤務基準日',
    before_clock_in_at DATETIME NULL COMMENT '修正前出勤日時',
    before_clock_out_at DATETIME NULL COMMENT '修正前退勤日時',
    before_break_minutes INT NOT NULL DEFAULT 0 COMMENT '修正前休憩時間 (分)',
    requested_clock_in_at DATETIME NOT NULL COMMENT '修正希望出勤日時',
    requested_clock_out_at DATETIME NOT NULL COMMENT '修正希望退勤日時',
    requested_break_minutes INT NOT NULL DEFAULT 0 COMMENT '修正希望休憩時間 (分)',
    reason VARCHAR(500) NOT NULL COMMENT '修正申請理由',
    approval_status VARCHAR(20) NOT NULL DEFAULT 'PENDING' COMMENT '承認状態 (PENDING, APPROVED, REJECTED)',
    reviewer_id BIGINT NULL COMMENT '承認/却下を行ったオーナー会員ID (FK)',
    reject_reason VARCHAR(500) NULL COMMENT '却下理由',
    reviewed_at DATETIME NULL COMMENT '承認/却下処理日時',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_mod_attendance FOREIGN KEY (attendance_id) REFERENCES attendance (attendance_id),
    CONSTRAINT fk_mod_store FOREIGN KEY (store_id) REFERENCES store (store_id),
    CONSTRAINT fk_mod_staff_store FOREIGN KEY (store_staff_id, store_id) REFERENCES store_staff (store_staff_id, store_id),
    CONSTRAINT fk_mod_reviewer FOREIGN KEY (reviewer_id) REFERENCES member (member_id),
    CONSTRAINT chk_mod_status CHECK (approval_status IN ('PENDING', 'APPROVED', 'REJECTED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 6-A. 修正申請の休憩区間スナップショット (承認前・申請値を監査目的で保存)
CREATE TABLE IF NOT EXISTS attendance_mod_break (
    mod_break_id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '修正申請休憩区間ID (PK)',
    mod_request_id BIGINT NOT NULL COMMENT '修正申請ID (FK)',
    break_set_type VARCHAR(10) NOT NULL COMMENT 'BEFORE または REQUESTED',
    break_start_at DATETIME NOT NULL,
    break_end_at DATETIME NOT NULL,
    duration_minutes INT NOT NULL,
    CONSTRAINT fk_mod_break_request FOREIGN KEY (mod_request_id) REFERENCES attendance_mod_request (mod_request_id),
    CONSTRAINT chk_mod_break_type CHECK (break_set_type IN ('BEFORE', 'REQUESTED')),
    CONSTRAINT chk_mod_break_range CHECK (break_end_at > break_start_at),
    CONSTRAINT chk_mod_break_duration CHECK (duration_minutes > 0),
    INDEX idx_mod_break_request (mod_request_id, break_set_type)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 7. 月次給与精算 (適用ルールに基づく計算スナップショットおよび帳票元本)
CREATE TABLE IF NOT EXISTS payroll (
    payroll_id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '給与精算ID (PK)',
    store_id BIGINT NOT NULL COMMENT '店舗ID (FK)',
    store_staff_id BIGINT NOT NULL COMMENT '店舗スタッフID (FK)',
    target_year_month CHAR(7) NOT NULL COMMENT '精算対象年月 (YYYY-MM)',
    range_start_date DATE NOT NULL COMMENT '集計期間開始日 (含む)',
    range_end_date DATE NOT NULL COMMENT '集計期間終了日 (含む)',
    work_days_count INT NOT NULL DEFAULT 0 COMMENT '総出勤日数',
    total_work_minutes INT NOT NULL DEFAULT 0 COMMENT '月間総実労働時間 (分)',
    total_night_minutes INT NOT NULL DEFAULT 0 COMMENT '月間総深夜労働時間 (分)',
    total_overtime_minutes INT NOT NULL DEFAULT 0 COMMENT '月間総時間外労働時間 (分)',
    applied_hourly_wage INT NULL COMMENT '精算期間中に時給が単一の場合の表示用スナップショット',
    applied_transport_limit INT NOT NULL DEFAULT 0 COMMENT '精算時に適用した月額交通費上限スナップショット',
    base_pay INT NOT NULL DEFAULT 0 COMMENT '基本給 (日別適用時給基準 100%)',
    night_allowance INT NOT NULL DEFAULT 0 COMMENT '深夜割増手当 (25% 加算分)',
    overtime_allowance INT NOT NULL DEFAULT 0 COMMENT '時間外割増手当 (25% 加算分)',
    transport_allowance INT NOT NULL DEFAULT 0 COMMENT '交通費支給額 (月額上限適用後)',
    gross_pay INT NOT NULL DEFAULT 0 COMMENT '総支給額 (税引前)',
    payroll_status VARCHAR(20) NOT NULL DEFAULT 'DRAFT' COMMENT '状態 (DRAFT, CONFIRMED, PAID)',
    confirmed_at DATETIME NULL COMMENT '精算確定日時',
    paid_at DATETIME NULL COMMENT '支払処理日時',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT uq_payroll_staff_month UNIQUE (store_staff_id, target_year_month),
    CONSTRAINT fk_payroll_store FOREIGN KEY (store_id) REFERENCES store (store_id),
    CONSTRAINT fk_payroll_staff_store FOREIGN KEY (store_staff_id, store_id) REFERENCES store_staff (store_staff_id, store_id),
    CONSTRAINT chk_payroll_range CHECK (range_end_date >= range_start_date),
    CONSTRAINT chk_payroll_status CHECK (payroll_status IN ('DRAFT', 'CONFIRMED', 'PAID'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 7-A. 給与確定時点の日別詳細スナップショット (確定明細書と勤怠変更後の値の不整合防止)
CREATE TABLE IF NOT EXISTS payroll_daily_detail (
    payroll_daily_detail_id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '給与日別詳細ID (PK)',
    payroll_id BIGINT NOT NULL COMMENT '給与精算ID (FK)',
    attendance_id BIGINT NOT NULL COMMENT '元勤怠ID (FK)',
    work_date DATE NOT NULL COMMENT '勤務日',
    applied_hourly_wage INT NOT NULL COMMENT '該当勤務日適用時給スナップショット',
    total_break_minutes INT NOT NULL DEFAULT 0,
    net_work_minutes INT NOT NULL DEFAULT 0,
    night_work_minutes INT NOT NULL DEFAULT 0,
    overtime_minutes INT NOT NULL DEFAULT 0,
    base_pay INT NOT NULL DEFAULT 0,
    night_allowance INT NOT NULL DEFAULT 0,
    overtime_allowance INT NOT NULL DEFAULT 0,
    transport_amount INT NOT NULL DEFAULT 0 COMMENT '日割り配分交通費',
    CONSTRAINT fk_payroll_detail_payroll FOREIGN KEY (payroll_id) REFERENCES payroll (payroll_id),
    CONSTRAINT fk_payroll_detail_attendance FOREIGN KEY (attendance_id) REFERENCES attendance (attendance_id),
    CONSTRAINT uq_payroll_detail_attendance UNIQUE (payroll_id, attendance_id),
    INDEX idx_payroll_detail_date (payroll_id, work_date)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

````

## Execution summary [docs\audit_runs\isolated_all_20261113_203209_kintai_full_test_cf0fbbd96042\exit_summary.json]
``text
{
    "gradle_exit_code":  0,
    "junit_xml_count":  34,
    "tests":  81,
    "failures":  0,
    "errors":  0,
    "skipped":  0,
    "junit_parse_errors":  [

                           ],
    "result":  "PASS"
}

````

## Day 11 JUnit XML [docs\audit_runs\isolated_all_20261113_203209_kintai_full_test_cf0fbbd96042\TEST-com.example.kintai.domain.payroll.service.AnnualPayrollScenarioIntegrationTest.xml]
``text
<?xml version="1.0" encoding="UTF-8"?>
<testsuite name="Day 11: 12-month attendance-to-payroll scenario" tests="2" skipped="0" failures="0" errors="0" timestamp="2026-11-13T11:32:35.217Z" hostname="BK" time="0.557">
  <properties/>
  <testcase name="calculatesEveryMonthAndAnnualTotalAgainstIndependentOracle()" classname="com.example.kintai.domain.payroll.service.AnnualPayrollScenarioIntegrationTest" time="0.413"/>
  <testcase name="contractHistoryUsesInclusiveStartAndExclusiveEndDates()" classname="com.example.kintai.domain.payroll.service.AnnualPayrollScenarioIntegrationTest" time="0.09"/>
  <system-out><![CDATA[2026-11-13T20:32:35.208+09:00  INFO 20960 --- [    Test worker] t.c.s.AnnotationConfigContextLoaderUtils : Could not detect default configuration classes for test class [com.example.kintai.domain.payroll.service.AnnualPayrollScenarioIntegrationTest]: AnnualPayrollScenarioIntegrationTest does not declare any static, non-private, non-final, nested classes annotated with @Configuration.
2026-11-13T20:32:35.265+09:00  INFO 20960 --- [    Test worker] .b.t.c.SpringBootTestContextBootstrapper : Found @SpringBootConfiguration com.example.kintai.KintaiApplication for test class com.example.kintai.domain.payroll.service.AnnualPayrollScenarioIntegrationTest
2026-11-13T20:32:35.266+09:00  INFO 20960 --- [    Test worker] t.c.s.AnnotationConfigContextLoaderUtils : Could not detect default configuration classes for test class [com.example.kintai.domain.payroll.service.AnnualPayrollScenarioIntegrationTest]: AnnualPayrollScenarioIntegrationTest does not declare any static, non-private, non-final, nested classes annotated with @Configuration.
2026-11-13T20:32:35.267+09:00  INFO 20960 --- [    Test worker] .b.t.c.SpringBootTestContextBootstrapper : Found @SpringBootConfiguration com.example.kintai.KintaiApplication for test class com.example.kintai.domain.payroll.service.AnnualPayrollScenarioIntegrationTest
]]></system-out>
  <system-err><![CDATA[]]></system-err>
</testsuite>

````

## Gradle console log [docs\audit_runs\isolated_all_20261113_203209_kintai_full_test_cf0fbbd96042\gradle_console.log]
``text
To honour the JVM settings for this build a single-use Daemon process will be forked. For more on this, please refer to https://docs.gradle.org/9.8.1/userguide/gradle_daemon.html#sec:disabling_the_daemon in the Gradle documentation.
Daemon will be stopped at the end of the build 
> Task :compileJava
> Task :processResources
> Task :classes
> Task :compileTestJava
> Task :processTestResources NO-SOURCE
> Task :testClasses
Java HotSpot(TM) 64-Bit Server VM warning: Sharing is only supported for boot loader classes because bootstrap classpath has been appended
2026-11-13T20:32:39.081+09:00  INFO 20960 --- [ionShutdownHook] com.zaxxer.hikari.HikariDataSource       : HikariPool-1 - Shutdown initiated...
2026-11-13T20:32:39.092+09:00  INFO 20960 --- [ionShutdownHook] com.zaxxer.hikari.HikariDataSource       : HikariPool-1 - Shutdown completed.
> Task :test

BUILD SUCCESSFUL in 29s
4 actionable tasks: 4 executed
Consider enabling configuration cache to speed up this build: https://docs.gradle.org/9.8.1/userguide/configuration_cache_enabling.html

````
