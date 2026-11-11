// ============================================================================
// Day 8 E2E Code Bundle for Local AI (Qwen 27B) Audit Pipeline v1.1.1
// Components:
// 1. PayrollMapper.java (Interface)
// 2. PayrollMapper.xml (MyBatis XML Mapping & Pessimistic Locks)
// 3. PayrollService.java (Domain Service & Concurrency Guards)
// 4. PayrollApiController.java (API Controller & Request Handling)
// 5. PayrollServiceTest.java (JUnit 5 Unit Tests)
// ============================================================================

// ============================================================================
// 1. PayrollMapper.java
// ============================================================================
package com.example.kintai.domain.payroll.mapper;

import com.example.kintai.domain.payroll.dto.PayrollDailyDetailDto;
import com.example.kintai.domain.payroll.dto.PayrollDto;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface PayrollMapper {

    List<PayrollDto> findPayrollsByStoreAndMonth(
            @Param("storeId") Long storeId,
            @Param("targetYearMonth") String targetYearMonth
    );

    PayrollDto findById(@Param("payrollId") Long payrollId);

    // 排他制御: 単一給与確定時の同時更新防止用悲観的ロック (SELECT ... FOR UPDATE)
    PayrollDto findByIdForUpdate(@Param("payrollId") Long payrollId);

    PayrollDto findByStaffAndMonth(
            @Param("storeStaffId") Long storeStaffId,
            @Param("targetYearMonth") String targetYearMonth
    );

    // 排他制御: 給与再計算・更新時のレコード競合防止用悲観的ロック (SELECT ... FOR UPDATE)
    PayrollDto findByStaffAndMonthForUpdate(
            @Param("storeStaffId") Long storeStaffId,
            @Param("targetYearMonth") String targetYearMonth
    );

    int upsertPayroll(PayrollDto payroll);

    int insertPayrollDailyDetail(PayrollDailyDetailDto detail);

    List<PayrollDailyDetailDto> findDailyDetailsByPayrollId(@Param("payrollId") Long payrollId);

    int deleteDailyDetailsByPayrollId(@Param("payrollId") Long payrollId);

    int updatePayrollStatusConfirm(
            @Param("payrollId") Long payrollId,
            @Param("confirmedAt") LocalDateTime confirmedAt
    );

    // 店舗全体の月次給与一括確定 (DRAFT -> CONFIRMED)
    int updateAllPayrollStatusConfirm(
            @Param("storeId") Long storeId,
            @Param("targetYearMonth") String targetYearMonth,
            @Param("confirmedAt") LocalDateTime confirmedAt
    );

    int countPayrollsByStoreAndMonthAndStatus(
            @Param("storeId") Long storeId,
            @Param("targetYearMonth") String targetYearMonth,
            @Param("payrollStatus") String payrollStatus
    );

    int countUnresolvedAttendance(
            @Param("storeStaffId") Long storeStaffId,
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate
    );

    Integer getPayCutoffDayByStoreId(@Param("storeId") Long storeId);

    Integer findHourlyWageForStaffOnDate(
            @Param("storeStaffId") Long storeStaffId,
            @Param("workDate") LocalDate workDate
    );

    Integer findTransportLimitForStaffOnDate(
            @Param("storeStaffId") Long storeStaffId,
            @Param("date") LocalDate date
    );

    Integer findDailyTransportFeeForStaffOnDate(
            @Param("storeStaffId") Long storeStaffId,
            @Param("date") LocalDate date
    );

    // 排他制御: 同時更新防止のための悲観的ロック (SELECT ... FOR UPDATE)
    void lockStoreStaffForUpdate(@Param("storeStaffId") Long storeStaffId);

    // 店舗全体の月次給与一括ロック (SELECT ... FOR UPDATE)
    List<PayrollDto> lockStorePayrollsForUpdate(
            @Param("storeId") Long storeId,
            @Param("targetYearMonth") String targetYearMonth
    );

    List<Long> findActiveStaffIdsByStoreId(@Param("storeId") Long storeId);
}

// ============================================================================
// 2. PayrollMapper.xml
// ============================================================================
<?xml version="1.0" encoding="UTF-8" ?>
<!DOCTYPE mapper PUBLIC "-//mybatis.org//DTD Mapper 3.0//EN"
        "http://mybatis.org/dtd/mybatis-3-mapper.dtd">

<mapper namespace="com.example.kintai.domain.payroll.mapper.PayrollMapper">

    <select id="findPayrollsByStoreAndMonth" resultType="com.example.kintai.domain.payroll.dto.PayrollDto">
        SELECT
            p.payroll_id,
            p.store_id,
            p.store_staff_id,
            m.name AS staff_name,
            p.target_year_month,
            p.range_start_date,
            p.range_end_date,
            p.work_days_count,
            p.total_work_minutes,
            p.total_night_minutes,
            p.total_overtime_minutes,
            p.applied_hourly_wage,
            p.applied_transport_limit,
            p.base_pay,
            p.night_allowance,
            p.overtime_allowance,
            p.transport_allowance,
            p.gross_pay,
            p.payroll_status,
            p.confirmed_at,
            p.paid_at
        FROM payroll p
        JOIN store_staff ss ON p.store_staff_id = ss.store_staff_id
        JOIN member m ON ss.member_id = m.member_id
        WHERE p.store_id = #{storeId}
          AND p.target_year_month = #{targetYearMonth}
        ORDER BY p.store_staff_id ASC
    </select>

    <select id="findById" resultType="com.example.kintai.domain.payroll.dto.PayrollDto">
        SELECT
            p.payroll_id,
            p.store_id,
            p.store_staff_id,
            m.name AS staff_name,
            p.target_year_month,
            p.range_start_date,
            p.range_end_date,
            p.work_days_count,
            p.total_work_minutes,
            p.total_night_minutes,
            p.total_overtime_minutes,
            p.applied_hourly_wage,
            p.applied_transport_limit,
            p.base_pay,
            p.night_allowance,
            p.overtime_allowance,
            p.transport_allowance,
            p.gross_pay,
            p.payroll_status,
            p.confirmed_at,
            p.paid_at
        FROM payroll p
        JOIN store_staff ss ON p.store_staff_id = ss.store_staff_id
        JOIN member m ON ss.member_id = m.member_id
        WHERE p.payroll_id = #{payrollId}
    </select>

    <select id="findByIdForUpdate" resultType="com.example.kintai.domain.payroll.dto.PayrollDto">
        SELECT
            p.payroll_id,
            p.store_id,
            p.store_staff_id,
            m.name AS staff_name,
            p.target_year_month,
            p.range_start_date,
            p.range_end_date,
            p.work_days_count,
            p.total_work_minutes,
            p.total_night_minutes,
            p.total_overtime_minutes,
            p.applied_hourly_wage,
            p.applied_transport_limit,
            p.base_pay,
            p.night_allowance,
            p.overtime_allowance,
            p.transport_allowance,
            p.gross_pay,
            p.payroll_status,
            p.confirmed_at,
            p.paid_at
        FROM payroll p
        JOIN store_staff ss ON p.store_staff_id = ss.store_staff_id
        JOIN member m ON ss.member_id = m.member_id
        WHERE p.payroll_id = #{payrollId}
        FOR UPDATE
    </select>

    <select id="findByStaffAndMonth" resultType="com.example.kintai.domain.payroll.dto.PayrollDto">
        SELECT
            p.payroll_id,
            p.store_id,
            p.store_staff_id,
            m.name AS staff_name,
            p.target_year_month,
            p.range_start_date,
            p.range_end_date,
            p.work_days_count,
            p.total_work_minutes,
            p.total_night_minutes,
            p.total_overtime_minutes,
            p.applied_hourly_wage,
            p.applied_transport_limit,
            p.base_pay,
            p.night_allowance,
            p.overtime_allowance,
            p.transport_allowance,
            p.gross_pay,
            p.payroll_status,
            p.confirmed_at,
            p.paid_at
        FROM payroll p
        JOIN store_staff ss ON p.store_staff_id = ss.store_staff_id
        JOIN member m ON ss.member_id = m.member_id
        WHERE p.store_staff_id = #{storeStaffId}
          AND p.target_year_month = #{targetYearMonth}
        LIMIT 1
    </select>

    <select id="findByStaffAndMonthForUpdate" resultType="com.example.kintai.domain.payroll.dto.PayrollDto">
        SELECT
            p.payroll_id,
            p.store_id,
            p.store_staff_id,
            m.name AS staff_name,
            p.target_year_month,
            p.range_start_date,
            p.range_end_date,
            p.work_days_count,
            p.total_work_minutes,
            p.total_night_minutes,
            p.total_overtime_minutes,
            p.applied_hourly_wage,
            p.applied_transport_limit,
            p.base_pay,
            p.night_allowance,
            p.overtime_allowance,
            p.transport_allowance,
            p.gross_pay,
            p.payroll_status,
            p.confirmed_at,
            p.paid_at
        FROM payroll p
        JOIN store_staff ss ON p.store_staff_id = ss.store_staff_id
        JOIN member m ON ss.member_id = m.member_id
        WHERE p.store_staff_id = #{storeStaffId}
          AND p.target_year_month = #{targetYearMonth}
        LIMIT 1
        FOR UPDATE
    </select>

    <insert id="upsertPayroll" useGeneratedKeys="true" keyProperty="payrollId">
        INSERT INTO payroll (
            store_id,
            store_staff_id,
            target_year_month,
            range_start_date,
            range_end_date,
            work_days_count,
            total_work_minutes,
            total_night_minutes,
            total_overtime_minutes,
            applied_hourly_wage,
            applied_transport_limit,
            base_pay,
            night_allowance,
            overtime_allowance,
            transport_allowance,
            gross_pay,
            payroll_status
        ) VALUES (
            #{storeId},
            #{storeStaffId},
            #{targetYearMonth},
            #{rangeStartDate},
            #{rangeEndDate},
            #{workDaysCount},
            #{totalWorkMinutes},
            #{totalNightMinutes},
            #{totalOvertimeMinutes},
            #{appliedHourlyWage},
            #{appliedTransportLimit},
            #{basePay},
            #{nightAllowance},
            #{overtimeAllowance},
            #{transportAllowance},
            #{grossPay},
            #{payrollStatus}
        )
        ON DUPLICATE KEY UPDATE
            work_days_count = VALUES(work_days_count),
            total_work_minutes = VALUES(total_work_minutes),
            total_night_minutes = VALUES(total_night_minutes),
            total_overtime_minutes = VALUES(total_overtime_minutes),
            applied_hourly_wage = VALUES(applied_hourly_wage),
            applied_transport_limit = VALUES(applied_transport_limit),
            base_pay = VALUES(base_pay),
            night_allowance = VALUES(night_allowance),
            overtime_allowance = VALUES(overtime_allowance),
            transport_allowance = VALUES(transport_allowance),
            gross_pay = VALUES(gross_pay),
            updated_at = CURRENT_TIMESTAMP
    </insert>

    <insert id="insertPayrollDailyDetail" useGeneratedKeys="true" keyProperty="payrollDailyDetailId">
        INSERT INTO payroll_daily_detail (
            payroll_id,
            attendance_id,
            work_date,
            applied_hourly_wage,
            total_break_minutes,
            net_work_minutes,
            night_work_minutes,
            overtime_minutes,
            base_pay,
            night_allowance,
            overtime_allowance,
            transport_amount
        ) VALUES (
            #{payrollId},
            #{attendanceId},
            #{workDate},
            #{appliedHourlyWage},
            #{totalBreakMinutes},
            #{netWorkMinutes},
            #{nightWorkMinutes},
            #{overtimeMinutes},
            #{basePay},
            #{nightAllowance},
            #{overtimeAllowance},
            #{transportAmount}
        )
    </insert>

    <select id="findDailyDetailsByPayrollId" resultType="com.example.kintai.domain.payroll.dto.PayrollDailyDetailDto">
        SELECT
            payroll_daily_detail_id,
            payroll_id,
            attendance_id,
            work_date,
            applied_hourly_wage,
            total_break_minutes,
            net_work_minutes,
            night_work_minutes,
            overtime_minutes,
            base_pay,
            night_allowance,
            overtime_allowance,
            transport_amount
        FROM payroll_daily_detail
        WHERE payroll_id = #{payrollId}
        ORDER BY work_date ASC
    </select>

    <delete id="deleteDailyDetailsByPayrollId">
        DELETE FROM payroll_daily_detail
        WHERE payroll_id = #{payrollId}
    </delete>

    <update id="updatePayrollStatusConfirm">
        UPDATE payroll
        SET payroll_status = 'CONFIRMED',
            confirmed_at = #{confirmedAt},
            updated_at = CURRENT_TIMESTAMP
        WHERE payroll_id = #{payrollId}
          AND payroll_status = 'DRAFT'
    </update>

    <update id="updateAllPayrollStatusConfirm">
        UPDATE payroll p
        SET p.payroll_status = 'CONFIRMED',
            p.confirmed_at = #{confirmedAt},
            p.updated_at = CURRENT_TIMESTAMP
        WHERE p.store_id = #{storeId}
          AND p.target_year_month = #{targetYearMonth}
          AND p.payroll_status = 'DRAFT'
          AND NOT EXISTS (
              SELECT 1
              FROM attendance a
              WHERE a.store_staff_id = p.store_staff_id
                AND a.work_date &gt;= p.range_start_date
                AND a.work_date &lt;= p.range_end_date
                AND a.attendance_status IN ('WORKING', 'ON_BREAK', 'MOD_REQUESTED')
          )
    </update>

    <select id="countPayrollsByStoreAndMonthAndStatus" resultType="int">
        SELECT COUNT(*)
        FROM payroll
        WHERE store_id = #{storeId}
          AND target_year_month = #{targetYearMonth}
          AND payroll_status = #{payrollStatus}
    </select>

    <select id="countUnresolvedAttendance" resultType="int">
        SELECT COUNT(*)
        FROM attendance
        WHERE store_staff_id = #{storeStaffId}
          AND work_date &gt;= #{startDate}
          AND work_date &lt;= #{endDate}
          AND attendance_status IN ('WORKING', 'ON_BREAK', 'MOD_REQUESTED')
    </select>

    <select id="getPayCutoffDayByStoreId" resultType="java.lang.Integer">
        SELECT pay_cutoff_day
        FROM store
        WHERE store_id = #{storeId}
    </select>

    <select id="findHourlyWageForStaffOnDate" resultType="java.lang.Integer">
        SELECT hourly_wage
        FROM store_staff_contract_history
        WHERE store_staff_id = #{storeStaffId}
          AND effective_from &lt;= #{workDate}
          AND (effective_to IS NULL OR effective_to &gt; #{workDate})
        ORDER BY effective_from DESC
        LIMIT 1
    </select>

    <select id="findTransportLimitForStaffOnDate" resultType="java.lang.Integer">
        SELECT monthly_transport_limit
        FROM store_staff_contract_history
        WHERE store_staff_id = #{storeStaffId}
          AND effective_from &lt;= #{date}
          AND (effective_to IS NULL OR effective_to &gt; #{date})
        ORDER BY effective_from DESC
        LIMIT 1
    </select>

    <select id="findDailyTransportFeeForStaffOnDate" resultType="java.lang.Integer">
        SELECT daily_transport_fee
        FROM store_staff_contract_history
        WHERE store_staff_id = #{storeStaffId}
          AND effective_from &lt;= #{date}
          AND (effective_to IS NULL OR effective_to &gt; #{date})
        ORDER BY effective_from DESC
        LIMIT 1
    </select>

    <select id="lockStoreStaffForUpdate">
        SELECT store_staff_id
        FROM store_staff
        WHERE store_staff_id = #{storeStaffId}
        FOR UPDATE
    </select>

    <select id="lockStorePayrollsForUpdate" resultType="com.example.kintai.domain.payroll.dto.PayrollDto">
        SELECT
            payroll_id,
            store_id,
            store_staff_id,
            target_year_month,
            range_start_date,
            range_end_date,
            work_days_count,
            total_work_minutes,
            total_night_minutes,
            total_overtime_minutes,
            applied_hourly_wage,
            applied_transport_limit,
            base_pay,
            night_allowance,
            overtime_allowance,
            transport_allowance,
            gross_pay,
            payroll_status,
            confirmed_at,
            paid_at
        FROM payroll
        WHERE store_id = #{storeId}
          AND target_year_month = #{targetYearMonth}
        ORDER BY store_staff_id ASC, payroll_id ASC
        FOR UPDATE
    </select>

    <select id="findActiveStaffIdsByStoreId" resultType="java.lang.Long">
        SELECT store_staff_id
        FROM store_staff
        WHERE store_id = #{storeId}
          AND employment_status = 'ACTIVE'
    </select>

</mapper>

// ============================================================================
// 3. PayrollService.java
// ============================================================================
package com.example.kintai.domain.payroll.service;

import com.example.kintai.domain.attendance.dto.AttendanceDto;
import com.example.kintai.domain.attendance.mapper.AttendanceMapper;
import com.example.kintai.domain.payroll.dto.*;
import com.example.kintai.domain.payroll.mapper.PayrollMapper;
import com.example.kintai.domain.payroll.util.PayrollCalculator;
import com.example.kintai.domain.payroll.util.PayrollExcelExporter;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Service
@Transactional(readOnly = true)
public class PayrollService {

    private final PayrollMapper payrollMapper;
    private final AttendanceMapper attendanceMapper;
    private final com.example.kintai.domain.store.service.StoreAuthorizationService storeAuthorizationService;

    public PayrollService(PayrollMapper payrollMapper, AttendanceMapper attendanceMapper, com.example.kintai.domain.store.service.StoreAuthorizationService storeAuthorizationService) {
        this.payrollMapper = payrollMapper;
        this.attendanceMapper = attendanceMapper;
        this.storeAuthorizationService = storeAuthorizationService;
    }

    public List<PayrollDto> getPayrollsByStoreAndMonth(Long storeId, String targetYearMonth) {
        return payrollMapper.findPayrollsByStoreAndMonth(storeId, targetYearMonth);
    }

    /**
     * 月別給与の一括集計および計算 (Upsert)
     */
    @Transactional
    public void calculateMonthlyPayroll(Long storeId, String targetYearMonth) {
        Integer cutoffDay = payrollMapper.getPayCutoffDayByStoreId(storeId);
        int payCutoffDay = (cutoffDay != null) ? cutoffDay : 99;

        PayrollCalculator.DateRange range = PayrollCalculator.resolveDateRange(targetYearMonth, payCutoffDay);
        LocalDate startDate = range.startDate();
        LocalDate endDate = range.endDate();

        List<Long> staffIds = payrollMapper.findActiveStaffIdsByStoreId(storeId);

        for (Long staffId : staffIds) {
            calculatePayrollForStaff(storeId, staffId, targetYearMonth, startDate, endDate);
        }
    }

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

        List<AttendanceDto> attendances = attendanceMapper.findCompletedAttendancesForStaffAndPeriod(staffId, startDate, endDate);

        // 通勤手当上限額（1回の精算あたりの上限）の取得
        Integer transportLimitVal = payrollMapper.findTransportLimitForStaffOnDate(staffId, endDate);
        int transportLimit = (transportLimitVal != null) ? transportLimitVal : 15000;

        int totalWorkMinutes = 0;
        int totalNightMinutes = 0;
        int totalOvertimeMinutes = 0;
        int totalBasePay = 0;
        int totalNightAllowance = 0;
        int totalOvertimeAllowance = 0;
        int totalTransportAllowance = 0;

        Set<Integer> uniqueWages = new HashSet<>();
        Set<LocalDate> workedDates = new HashSet<>();

        List<PayrollDailyDetailDto> detailsToInsert = new ArrayList<>();

        for (AttendanceDto att : attendances) {
            // 勤怠データの負値・異常値バリデーション (F-07 堅牢性保証)
            if (att.getNetWorkMinutes() < 0 || att.getNightWorkMinutes() < 0 || att.getOvertimeMinutes() < 0 || att.getTotalBreakMinutes() < 0) {
                throw new IllegalStateException("勤務時間または休憩時間に負の値が含まれているため、給与計算を中断しました。(勤怠ID: " + att.getAttendanceId() + ")");
            }

            LocalDate workDate = att.getWorkDate();
            Integer hourlyWageVal = payrollMapper.findHourlyWageForStaffOnDate(staffId, workDate);
            if (hourlyWageVal == null || hourlyWageVal <= 0) {
                throw new IllegalStateException("勤務日 (" + workDate + ") に有効な契約時給データが存在しません。");
            }
            int hourlyWage = hourlyWageVal;
            uniqueWages.add(hourlyWage);

            // 2026年東京都最低賃金 (1,280円) 割れを防止
            if (hourlyWage < 1280) {
                throw new IllegalStateException("契約時給 (" + hourlyWage + "円) が法定最低賃金 (1,280円) を下回っています。");
            }

            // 日別計算（端数は HALF_UP で厳密に丸め）
            PayrollCalculator.DailyPayResult dailyPay = PayrollCalculator.calculateDailyPay(
                    hourlyWage,
                    att.getNetWorkMinutes(),
                    att.getNightWorkMinutes(),
                    att.getOvertimeMinutes()
            );

            // 1日1回通勤手当支給および月額上限枠内での配分
            int transportAmount = 0;
            if (!workedDates.contains(workDate)) {
                workedDates.add(workDate);
                Integer dailyTransportVal = payrollMapper.findDailyTransportFeeForStaffOnDate(staffId, workDate);
                int dailyTransport = (dailyTransportVal != null) ? dailyTransportVal : 0;
                int remainingLimit = Math.max(0, transportLimit - totalTransportAllowance);
                transportAmount = Math.min(dailyTransport, remainingLimit);
                totalTransportAllowance += transportAmount;
            }

            totalWorkMinutes += att.getNetWorkMinutes();
            totalNightMinutes += att.getNightWorkMinutes();
            totalOvertimeMinutes += att.getOvertimeMinutes();
            totalBasePay += dailyPay.basePay();
            totalNightAllowance += dailyPay.nightAllowance();
            totalOvertimeAllowance += dailyPay.overtimeAllowance();

            PayrollDailyDetailDto detail = new PayrollDailyDetailDto();
            detail.setAttendanceId(att.getAttendanceId());
            detail.setWorkDate(workDate);
            detail.setAppliedHourlyWage(hourlyWage);
            detail.setTotalBreakMinutes(att.getTotalBreakMinutes());
            detail.setNetWorkMinutes(att.getNetWorkMinutes());
            detail.setNightWorkMinutes(att.getNightWorkMinutes());
            detail.setOvertimeMinutes(att.getOvertimeMinutes());
            detail.setBasePay(dailyPay.basePay());
            detail.setNightAllowance(dailyPay.nightAllowance());
            detail.setOvertimeAllowance(dailyPay.overtimeAllowance());
            detail.setTransportAmount(transportAmount);

            detailsToInsert.add(detail);
        }

        int grossPay = totalBasePay + totalNightAllowance + totalOvertimeAllowance + totalTransportAllowance;
        Integer appliedHourlyWage = (uniqueWages.size() == 1) ? uniqueWages.iterator().next() : null;

        PayrollDto payroll = new PayrollDto();
        payroll.setStoreId(storeId);
        payroll.setStoreStaffId(staffId);
        payroll.setTargetYearMonth(targetYearMonth);
        payroll.setRangeStartDate(startDate);
        payroll.setRangeEndDate(endDate);
        payroll.setWorkDaysCount(workedDates.size());
        payroll.setTotalWorkMinutes(totalWorkMinutes);
        payroll.setTotalNightMinutes(totalNightMinutes);
        payroll.setTotalOvertimeMinutes(totalOvertimeMinutes);
        payroll.setAppliedHourlyWage(appliedHourlyWage);
        payroll.setAppliedTransportLimit(transportLimit);
        payroll.setBasePay(totalBasePay);
        payroll.setNightAllowance(totalNightAllowance);
        payroll.setOvertimeAllowance(totalOvertimeAllowance);
        payroll.setTransportAllowance(totalTransportAllowance);
        payroll.setGrossPay(grossPay);
        payroll.setPayrollStatus("DRAFT");

        // 【通達 昭和62年(63年) 基発第150号 法定月間端数処理の適用】
        // 単一契約時給の場合は、月間割増手当総額の法定端数処理（30分境界丸め＋手当別50銭四捨五入）を反映
        if (appliedHourlyWage != null) {
            StatutoryPayrollResult statutoryResult = applyStatutoryMonthlyRounding(payroll);
            // 法定端数処理結果を手当額および総支給額に反映
            payroll.setOvertimeAllowance(statutoryResult.statutoryOvertimeAllowance());
            payroll.setNightAllowance(statutoryResult.statutoryNightAllowance());
            payroll.setGrossPay(payroll.getBasePay() + payroll.getNightAllowance() + payroll.getOvertimeAllowance() + payroll.getTransportAllowance());
        }

        payrollMapper.upsertPayroll(payroll);

        // 日別詳細スナップショットの再保存
        payrollMapper.deleteDailyDetailsByPayrollId(payroll.getPayrollId());
        for (PayrollDailyDetailDto detail : detailsToInsert) {
            detail.setPayrollId(payroll.getPayrollId());
            payrollMapper.insertPayrollDailyDetail(detail);
        }

        return payroll;
    }

    /**
     * 【行政通達 昭和62年3月14日付 基発第150号 準拠】
     * 月間3大端数処理（時間外・深夜・休日の30分境界月間丸めおよび50銭境界手当集計）を適用した法定集計
     */
    public StatutoryPayrollResult applyStatutoryMonthlyRounding(PayrollDto payroll) {
        return applyStatutoryMonthlyRounding(payroll, 0);
    }

    /**
     * 【行政通達 昭和62年3月14日付 基発第150号 準拠】
     * 月間3大端数処理（時間外・深夜・休日の30分境界月間丸めおよび50銭境界手当集計、休日労働時間連動版）
     */
    public StatutoryPayrollResult applyStatutoryMonthlyRounding(PayrollDto payroll, int totalHolidayMinutes) {
        if (payroll == null) {
            throw new IllegalArgumentException("給与データは必須です。");
        }

        // ① 月間労働時間の端数処理（時間外・深夜・休日それぞれ30分未満切り捨て、30分以上1時間に切り上げ）
        int roundedOvertimeHours = PayrollCalculator.StatutoryRounding.roundMonthlyHours(payroll.getTotalOvertimeMinutes());
        int roundedNightHours = PayrollCalculator.StatutoryRounding.roundMonthlyHours(payroll.getTotalNightMinutes());
        int roundedHolidayHours = PayrollCalculator.StatutoryRounding.roundMonthlyHours(Math.max(0, totalHolidayMinutes));

        int hourlyWage = (payroll.getAppliedHourlyWage() != null) ? payroll.getAppliedHourlyWage() : 1280;

        // ② 1時間あたりの割増賃金額（50銭未満切り捨て、50銭以上1円切り上げ）
        // 時間外: 25%加算
        int overtimeHourlyPremium = PayrollCalculator.StatutoryRounding.roundHourlyRate(
                java.math.BigDecimal.valueOf(hourlyWage).multiply(java.math.BigDecimal.valueOf(0.25))
        );
        // 深夜: 25%加算
        int nightHourlyPremium = PayrollCalculator.StatutoryRounding.roundHourlyRate(
                java.math.BigDecimal.valueOf(hourlyWage).multiply(java.math.BigDecimal.valueOf(0.25))
        );
        // 休日: 35%加算 (労基法第37条第1項)
        int holidayHourlyPremium = PayrollCalculator.StatutoryRounding.roundHourlyRate(
                java.math.BigDecimal.valueOf(hourlyWage).multiply(java.math.BigDecimal.valueOf(0.35))
        );

        // ③ 月間割増手当総額の端数処理（項目別独立集計・50銭未満切り捨て、50銭以上1円切り上げ）
        int roundedOvertimeAllowance = PayrollCalculator.StatutoryRounding.roundMonthlyAllowanceTotal(
                java.math.BigDecimal.valueOf((long) overtimeHourlyPremium * roundedOvertimeHours)
        );
        int roundedNightAllowance = PayrollCalculator.StatutoryRounding.roundMonthlyAllowanceTotal(
                java.math.BigDecimal.valueOf((long) nightHourlyPremium * roundedNightHours)
        );
        int roundedHolidayAllowance = PayrollCalculator.StatutoryRounding.roundMonthlyAllowanceTotal(
                java.math.BigDecimal.valueOf((long) holidayHourlyPremium * roundedHolidayHours)
        );

        return new StatutoryPayrollResult(
                roundedOvertimeHours,
                roundedNightHours,
                roundedHolidayHours,
                overtimeHourlyPremium,
                nightHourlyPremium,
                holidayHourlyPremium,
                roundedOvertimeAllowance,
                roundedNightAllowance,
                roundedHolidayAllowance
        );
    }

    public record StatutoryPayrollResult(
            int roundedOvertimeHours,
            int roundedNightHours,
            int roundedHolidayHours,
            int overtimeHourlyPremium,
            int nightHourlyPremium,
            int holidayHourlyPremium,
            int statutoryOvertimeAllowance,
            int statutoryNightAllowance,
            int statutoryHolidayAllowance
    ) {}

    /**
     * 給与確定ロック処理（単一レコードの帳簿不変性を保証、排他悲観的ロック適用）
     */
    @Transactional
    public void confirmPayroll(Long payrollId, PayrollConfirmRequestDto confirmDto) {
        // 排他制御: 確定対象の給与レコード自体を行ロック (SELECT ... FOR UPDATE)
        PayrollDto existing = payrollMapper.findByIdForUpdate(payrollId);
        if (existing == null) {
            throw new IllegalArgumentException("存在しない給与データです。(ID: " + payrollId + ")");
        }

        if (!"DRAFT".equals(existing.getPayrollStatus())) {
            throw new IllegalStateException("既に確定済みの給与データです。");
        }

        // スタッフの排他ロック
        payrollMapper.lockStoreStaffForUpdate(existing.getStoreStaffId());

        // 精算対象期間終了の検証（締め期間終了前の早期確定を防止）
        if (!LocalDate.now().isAfter(existing.getRangeEndDate())) {
            throw new IllegalStateException("精算対象期間 (" + existing.getRangeEndDate() + ") が終了する前に給与を確定することはできません。");
        }

        // 未処理勤怠の確認
        int unresolved = payrollMapper.countUnresolvedAttendance(existing.getStoreStaffId(), existing.getRangeStartDate(), existing.getRangeEndDate());
        if (unresolved > 0) {
            throw new IllegalStateException("未処理の勤怠修正申請または勤務中打刻が存在するため、確定できません。");
        }

        // 最新データによる再計算
        PayrollDto recalculated = calculatePayrollForStaff(
                existing.getStoreId(),
                existing.getStoreStaffId(),
                existing.getTargetYearMonth(),
                existing.getRangeStartDate(),
                existing.getRangeEndDate()
        );

        // 店長が確認した金額と再計算金額の不一致チェック
        if (confirmDto != null && confirmDto.getExpectedGrossPay() != null) {
            int expected = confirmDto.getExpectedGrossPay();
            int actual = recalculated.getGrossPay();
            if (expected != actual) {
                throw new IllegalStateException("確認時と金額が一致しません。(確認額: " + expected + "円, 再計算額: " + actual + "円)。再確認が必要です。");
            }
        }

        payrollMapper.updatePayrollStatusConfirm(payrollId, LocalDateTime.now());
    }

    /**
     * 店舗全体の月次給与一括確定バッチ処理（同月全レコード悲観的ロックおよび一括確定）
     *
     * @param storeId 対象店舗ID
     * @param targetYearMonth 対象年月 (YYYY-MM)
     * @return 確定された給与件数
     */
    @Transactional
    public int confirmMonthlyPayroll(Long storeId, String targetYearMonth) {
        // 排他制御: 店舗の該当年月における全給与レコードを一括悲観的ロック (FOR UPDATE)
        List<PayrollDto> payrolls = payrollMapper.lockStorePayrollsForUpdate(storeId, targetYearMonth);
        if (payrolls == null || payrolls.isEmpty()) {
            throw new IllegalStateException("指定年月 (" + targetYearMonth + ") の給与データが存在しません。先に一括計算を実行してください。");
        }

        List<PayrollDto> draftPayrolls = payrolls.stream()
                .filter(p -> "DRAFT".equals(p.getPayrollStatus()))
                .toList();

        if (draftPayrolls.isEmpty()) {
            throw new IllegalStateException("指定年月 (" + targetYearMonth + ") の給与は全件確定済みです。");
        }

        LocalDate today = LocalDate.now();

        // 締め期間終了および未処理勤怠の全件バリデーション
        for (PayrollDto p : draftPayrolls) {
            if (!today.isAfter(p.getRangeEndDate())) {
                throw new IllegalStateException("精算対象期間 (" + p.getRangeEndDate() + ") が終了する前に月次給与を一括確定することはできません。(対象スタッフID: " + p.getStoreStaffId() + ")");
            }

            int unresolved = payrollMapper.countUnresolvedAttendance(p.getStoreStaffId(), p.getRangeStartDate(), p.getRangeEndDate());
            if (unresolved > 0) {
                throw new IllegalStateException("未処理の勤怠修正申請または打刻が存在するため、月次給与を一括確定できません。(対象スタッフID: " + p.getStoreStaffId() + ")");
            }
        }

        // 店舗・対象年月の一括CONFIRMED更新 (CAS)
        int updatedCount = payrollMapper.updateAllPayrollStatusConfirm(storeId, targetYearMonth, LocalDateTime.now());
        return updatedCount;
    }

    /**
     * スタッフ個人別給与明細詳細モーダル用データ取得（認可検証付き）
     */
    public PayrollDetailResponseDto getPayrollDetail(Long payrollId, Long currentMemberId) {
        if (currentMemberId != null) {
            storeAuthorizationService.validatePayrollOwnership(payrollId, currentMemberId);
        }
        return getPayrollDetail(payrollId);
    }

    /**
     * スタッフ個人別給与明細詳細モーダル用データ取得
     */
    public PayrollDetailResponseDto getPayrollDetail(Long payrollId) {
        PayrollDto payroll = payrollMapper.findById(payrollId);
        if (payroll == null) {
            throw new IllegalArgumentException("存在しない給与データです。(ID: " + payrollId + ")");
        }

        List<PayrollDailyDetailDto> details = payrollMapper.findDailyDetailsByPayrollId(payrollId);

        PayrollDetailResponseDto response = new PayrollDetailResponseDto();
        response.setPayrollId(payroll.getPayrollId());
        response.setStaffName(payroll.getStaffName());
        response.setTargetYearMonth(payroll.getTargetYearMonth());
        response.setAppliedHourlyWage(payroll.getAppliedHourlyWage());

        PayrollDetailResponseDto.PayrollSummary summary = new PayrollDetailResponseDto.PayrollSummary();
        summary.setWorkDaysCount(payroll.getWorkDaysCount());
        summary.setTotalWorkMinutes(payroll.getTotalWorkMinutes());
        summary.setTotalNightMinutes(payroll.getTotalNightMinutes());
        summary.setTotalOvertimeMinutes(payroll.getTotalOvertimeMinutes());
        summary.setBasePay(payroll.getBasePay());
        summary.setNightAllowance(payroll.getNightAllowance());
        summary.setOvertimeAllowance(payroll.getOvertimeAllowance());
        summary.setTransportAllowance(payroll.getTransportAllowance());
        summary.setGrossPay(payroll.getGrossPay());
        summary.setPayrollStatus(payroll.getPayrollStatus());
        response.setSummary(summary);

        DateTimeFormatter timeFormatter = DateTimeFormatter.ofPattern("HH:mm");
        List<PayrollDetailResponseDto.DailyAttendanceDetail> dailyList = new ArrayList<>();

        for (PayrollDailyDetailDto d : details) {
            PayrollDetailResponseDto.DailyAttendanceDetail daily = new PayrollDetailResponseDto.DailyAttendanceDetail();
            daily.setWorkDate(d.getWorkDate().toString());
            daily.setBreakMinutes(d.getTotalBreakMinutes());
            daily.setNetWorkMinutes(d.getNetWorkMinutes());
            daily.setNightMinutes(d.getNightWorkMinutes());
            daily.setOvertimeMinutes(d.getOvertimeMinutes());
            daily.setAppliedHourlyWage(d.getAppliedHourlyWage());
            daily.setBasePay(d.getBasePay());
            daily.setNightAllowance(d.getNightAllowance());
            daily.setOvertimeAllowance(d.getOvertimeAllowance());
            daily.setTransportAmount(d.getTransportAmount());

            // 元勤怠データから打刻時刻情報を補完
            AttendanceDto originalAtt = attendanceMapper.findById(d.getAttendanceId());
            if (originalAtt != null) {
                daily.setClockInAt(originalAtt.getClockInAt() != null ? originalAtt.getClockInAt().format(timeFormatter) : "-");
                daily.setClockOutAt(originalAtt.getClockOutAt() != null ? originalAtt.getClockOutAt().format(timeFormatter) : "-");
            } else {
                daily.setClockInAt("-");
                daily.setClockOutAt("-");
            }

            dailyList.add(daily);
        }

        response.setDailyAttendances(dailyList);
        return response;
    }

    /**
     * 給与台帳 Excel エクスポート
     */
    public byte[] exportPayrollToExcel(Long storeId, String targetYearMonth) throws IOException {
        List<PayrollDto> payrolls = getPayrollsByStoreAndMonth(storeId, targetYearMonth);
        return PayrollExcelExporter.exportToExcel(targetYearMonth, payrolls);
    }
}

// ============================================================================
// 4. PayrollApiController.java
// ============================================================================
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

// ============================================================================
// 5. PayrollServiceTest.java
// ============================================================================
package com.example.kintai.domain.payroll.service;

import com.example.kintai.domain.attendance.mapper.AttendanceMapper;
import com.example.kintai.domain.payroll.dto.PayrollDto;
import com.example.kintai.domain.payroll.mapper.PayrollMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("PayrollService 法定端数処理連携および給与不変性保護単体テスト")
class PayrollServiceTest {

    @Mock
    private PayrollMapper payrollMapper;

    @Mock
    private AttendanceMapper attendanceMapper;

    @InjectMocks
    private PayrollService payrollService;

    @Test
    @DisplayName("確定済み給与(CONFIRMED/PAID)の再計算はIllegalStateExceptionで遮断され不変性が保護されること")
    void calculatePayroll_WhenAlreadyConfirmed_ShouldThrowIllegalStateException() {
        Long storeId = 1L;
        Long staffId = 10L;
        String targetMonth = "2026-11";
        LocalDate start = LocalDate.of(2026, 11, 1);
        LocalDate end = LocalDate.of(2026, 11, 30);

        PayrollDto confirmedPayroll = new PayrollDto();
        confirmedPayroll.setPayrollId(100L);
        confirmedPayroll.setPayrollStatus("CONFIRMED");

        when(payrollMapper.findByStaffAndMonthForUpdate(staffId, targetMonth)).thenReturn(confirmedPayroll);

        IllegalStateException ex = assertThrows(IllegalStateException.class, () ->
                payrollService.calculatePayrollForStaff(storeId, staffId, targetMonth, start, end)
        );

        assertEquals("指定年月 (2026-11) の給与は既に確定済み(CONFIRMED) のため、再計算できません。(給与不変性保護)", ex.getMessage());
    }

    @Test
    @DisplayName("行政通達 昭和62年3月14日付 基発第150号準拠の月間3大端数処理連携検証")
    void applyStatutoryMonthlyRounding_ShouldApplyMhlwNoticeRulesCorrectly() {
        PayrollDto payroll = new PayrollDto();
        payroll.setAppliedHourlyWage(1300); // 時給1,300円 -> 25%割増単価: 325円 (50銭境界ジャスト整数)
        payroll.setTotalOvertimeMinutes(449); // 7時間29分 -> 30分未満切り捨てで7時間
        payroll.setTotalNightMinutes(450);    // 7時間30分 -> 30分以上切り上げで8時間

        PayrollService.StatutoryPayrollResult result = payrollService.applyStatutoryMonthlyRounding(payroll, 329);

        // ① 月間時間の端数処理（時間外・深夜・休日 それぞれ独立に30分境界処理）
        assertEquals(7, result.roundedOvertimeHours(), "7時間29分は30分未満切り捨てで7時間となること");
        assertEquals(8, result.roundedNightHours(), "7時間30分は30分以上切り上げで8時間となること");
        assertEquals(5, result.roundedHolidayHours(), "5時間29分は30分未満切り捨てで5時間となること");

        // ② 1時間あたりの割増単価（1,300 * 0.25 = 325円、1,300 * 0.35 = 455円）
        assertEquals(325, result.overtimeHourlyPremium());
        assertEquals(325, result.nightHourlyPremium());
        assertEquals(455, result.holidayHourlyPremium());

        // ③ 月間手当総額の端数処理（項目別独立集計）
        // 時間外: 325円 * 7時間 = 2,275円
        assertEquals(2275, result.statutoryOvertimeAllowance());
        // 深夜: 325円 * 8時間 = 2,600円
        assertEquals(2600, result.statutoryNightAllowance());
        // 休日: 455円 * 5時間 = 2,275円
        assertEquals(2275, result.statutoryHolidayAllowance());
    }

    @Test
    @DisplayName("単一給与確定時、既に確定済み(CONFIRMED)の場合は悲観的ロック照会後にIllegalStateExceptionを投げること")
    void confirmPayroll_WhenAlreadyConfirmed_ShouldThrowIllegalStateException() {
        Long payrollId = 200L;
        PayrollDto existing = new PayrollDto();
        existing.setPayrollId(payrollId);
        existing.setPayrollStatus("CONFIRMED");

        when(payrollMapper.findByIdForUpdate(payrollId)).thenReturn(existing);

        IllegalStateException ex = assertThrows(IllegalStateException.class, () ->
                payrollService.confirmPayroll(payrollId, null)
        );

        assertEquals("既に確定済みの給与データです。", ex.getMessage());
    }

    @Test
    @DisplayName("単一給与確定時、締め期間終了前(早期確定)はIllegalStateExceptionで遮断されること")
    void confirmPayroll_WhenPeriodNotEnded_ShouldThrowIllegalStateException() {
        Long payrollId = 201L;
        PayrollDto existing = new PayrollDto();
        existing.setPayrollId(payrollId);
        existing.setStoreStaffId(5L);
        existing.setPayrollStatus("DRAFT");
        existing.setRangeEndDate(LocalDate.now().plusDays(5)); // 未来の日付

        when(payrollMapper.findByIdForUpdate(payrollId)).thenReturn(existing);

        IllegalStateException ex = assertThrows(IllegalStateException.class, () ->
                payrollService.confirmPayroll(payrollId, null)
        );

        assertEquals("精算対象期間 (" + existing.getRangeEndDate() + ") が終了する前に給与を確定することはできません。", ex.getMessage());
    }

    @Test
    @DisplayName("店舗全体の月次給与一括確定が正常に実行され全件確定されること")
    void confirmMonthlyPayroll_Success() {
        Long storeId = 1L;
        String targetMonth = "2026-10";

        PayrollDto p1 = new PayrollDto();
        p1.setPayrollId(1L);
        p1.setStoreStaffId(101L);
        p1.setPayrollStatus("DRAFT");
        p1.setRangeStartDate(LocalDate.of(2026, 10, 1));
        p1.setRangeEndDate(LocalDate.of(2026, 10, 31));

        PayrollDto p2 = new PayrollDto();
        p2.setPayrollId(2L);
        p2.setStoreStaffId(102L);
        p2.setPayrollStatus("DRAFT");
        p2.setRangeStartDate(LocalDate.of(2026, 10, 1));
        p2.setRangeEndDate(LocalDate.of(2026, 10, 31));

        when(payrollMapper.lockStorePayrollsForUpdate(storeId, targetMonth)).thenReturn(java.util.List.of(p1, p2));
        when(payrollMapper.countUnresolvedAttendance(101L, p1.getRangeStartDate(), p1.getRangeEndDate())).thenReturn(0);
        when(payrollMapper.countUnresolvedAttendance(102L, p2.getRangeStartDate(), p2.getRangeEndDate())).thenReturn(0);
        when(payrollMapper.updateAllPayrollStatusConfirm(org.mockito.ArgumentMatchers.eq(storeId), org.mockito.ArgumentMatchers.eq(targetMonth), org.mockito.ArgumentMatchers.any())).thenReturn(2);

        int confirmedCount = payrollService.confirmMonthlyPayroll(storeId, targetMonth);

        assertEquals(2, confirmedCount);
    }

    @Test
    @DisplayName("店舗全体の月次給与一括確定時、未処理勤怠が存在するスタッフがいる場合は一括確定を遮断すること")
    void confirmMonthlyPayroll_WhenUnresolvedAttendance_ShouldThrowIllegalStateException() {
        Long storeId = 1L;
        String targetMonth = "2026-10";

        PayrollDto p1 = new PayrollDto();
        p1.setPayrollId(1L);
        p1.setStoreStaffId(101L);
        p1.setPayrollStatus("DRAFT");
        p1.setRangeStartDate(LocalDate.of(2026, 10, 1));
        p1.setRangeEndDate(LocalDate.of(2026, 10, 31));

        when(payrollMapper.lockStorePayrollsForUpdate(storeId, targetMonth)).thenReturn(java.util.List.of(p1));
        when(payrollMapper.countUnresolvedAttendance(101L, p1.getRangeStartDate(), p1.getRangeEndDate())).thenReturn(1);

        IllegalStateException ex = assertThrows(IllegalStateException.class, () ->
                payrollService.confirmMonthlyPayroll(storeId, targetMonth)
        );

        assertEquals("未処理の勤怠修正申請または打刻が存在するため、月次給与を一括確定できません。(対象スタッフID: 101)", ex.getMessage());
    }
}

