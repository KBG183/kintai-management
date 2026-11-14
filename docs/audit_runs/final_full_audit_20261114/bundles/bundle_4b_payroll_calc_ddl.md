# Final Full Audit Bundle: bundle_4b_payroll_calc_ddl
Generated: 2026-11-14T12:51:27+09:00

## FILE: src\main\java\com\example\kintai\domain\payroll\util\PayrollCalculator.java
```
package com.example.kintai.domain.payroll.util;

import com.example.kintai.domain.attendance.util.AttendanceTimeCalculator;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeParseException;

/**
 * 日本労働基準法第37条および昭和62年3月14日付 基発第150号（行政通達: 賃金計算等の端数処理）に準拠した給与算定ユーティリティ。
 * 時間外（25%）、深夜（25%）、深夜残業（重畳割増: 50%）および法定端数処理（50銭・30分境界）を厳密に処理する。
 * （※なお、労働基準法関係解釈例の一般通達は昭和63年3月14日付 基発第150号・婦発第47号）
 */
public final class PayrollCalculator {

    private PayrollCalculator() {}

    public record DateRange(LocalDate startDate, LocalDate endDate) {}

    public record DailyPayResult(
            int basePay,
            int nightAllowance,
            int overtimeAllowance,
            int totalPay
    ) {}

    /**
     * 労働基準法第37条に基づく4区分支給額詳細結果
     *
     * @param regularPay 所定内通常労働給与（100%）
     * @param regularNightPay 所定内深夜労働給与（125% = 基本100% + 深夜割増25%）
     * @param overtimePay 法定外通常残業給与（125% = 基本100% + 時間外割増25%）
     * @param nightOvertimePay 法定外深夜残業給与（150% = 基本100% + 時間外25% + 深夜25% 重畳割増）
     * @param basePay 基本給相当（実労働時間全体の100%分）
     * @param nightAllowance 深夜割増手当（総深夜時間の25%加算分）
     * @param overtimeAllowance 時間外割増手当（総残業時間の25%加算分）
     * @param totalPay 総支給額（税引前合計）
     */
    public record DetailedDailyPayResult(
            int regularPay,
            int regularNightPay,
            int overtimePay,
            int nightOvertimePay,
            int basePay,
            int nightAllowance,
            int overtimeAllowance,
            int totalPay
    ) {}

    /**
     * 店舗の給与締日（payCutoffDay: 1~28 または 99=末日締）に基づく実際の集計期間を算出
     */
    public static DateRange resolveDateRange(String targetYearMonthStr, int payCutoffDay) {
        YearMonth ym;
        try {
            ym = YearMonth.parse(targetYearMonthStr);
        } catch (DateTimeParseException e) {
            throw new IllegalArgumentException("無効な年月フォーマットです。(例: 2026-10): " + targetYearMonthStr, e);
        }

        if (payCutoffDay == 99) {
            LocalDate start = ym.atDay(1);
            LocalDate end = ym.atEndOfMonth();
            return new DateRange(start, end);
        } else {
            YearMonth prevYm = ym.minusMonths(1);
            LocalDate start = prevYm.atDay(payCutoffDay + 1);
            LocalDate end = ym.atDay(payCutoffDay);
            return new DateRange(start, end);
        }
    }

    /**
     * 同一勤務日の実労働、深夜、時間外残業分数を合算し、HALF_UP丸めによって日別支給額を算出
     */
    public static DailyPayResult calculateDailyPay(
            int hourlyWage,
            int totalNetWorkMinutes,
            int totalNightMinutes,
            int totalOvertimeMinutes
    ) {
        if (hourlyWage < 0 || totalNetWorkMinutes < 0 || totalNightMinutes < 0 || totalOvertimeMinutes < 0) {
            throw new IllegalArgumentException("時給および勤務時間数は0以上である必要があります。");
        }

        // 基本給 (100%): 時給 * 実労働分 / 60
        int basePay = yenRound(BigDecimal.valueOf(hourlyWage).multiply(BigDecimal.valueOf(totalNetWorkMinutes)), 60);

        // 深夜割増 (25%): 時給 * 0.25 * 深夜分 / 60 = 時給 * 深夜分 / 240
        int nightAllowance = yenRound(BigDecimal.valueOf(hourlyWage).multiply(BigDecimal.valueOf(totalNightMinutes)), 240);

        // 時間外割増 (25%): 時給 * 0.25 * 残業分 / 60 = 時給 * 残業分 / 240
        int overtimeAllowance = yenRound(BigDecimal.valueOf(hourlyWage).multiply(BigDecimal.valueOf(totalOvertimeMinutes)), 240);

        int totalPay = basePay + nightAllowance + overtimeAllowance;
        return new DailyPayResult(basePay, nightAllowance, overtimeAllowance, totalPay);
    }

    /**
     * 労働基準法第37条の4区分精密集計結果に基づき日別支給額の内訳を算定
     */
    public static DetailedDailyPayResult calculateDetailedDailyPay(
            int hourlyWage,
            AttendanceTimeCalculator.DetailedCalculationResult calc
    ) {
        if (hourlyWage < 0 || calc == null) {
            throw new IllegalArgumentException("時給および計算結果オブジェクトは必須です。");
        }

        // 基本項目（総計視点）
        int basePay = yenRound(BigDecimal.valueOf(hourlyWage).multiply(BigDecimal.valueOf(calc.netWorkMinutes())), 60);
        int nightAllowance = yenRound(BigDecimal.valueOf(hourlyWage).multiply(BigDecimal.valueOf(calc.nightWorkMinutes())), 240);
        int overtimeAllowance = yenRound(BigDecimal.valueOf(hourlyWage).multiply(BigDecimal.valueOf(calc.overtimeMinutes())), 240);
        int totalPay = basePay + nightAllowance + overtimeAllowance;

        // 区分別（内訳視点）
        // 所定内通常 (100%): 時給 * 分 / 60
        int regularPay = yenRound(BigDecimal.valueOf(hourlyWage).multiply(BigDecimal.valueOf(calc.regularWorkMinutes())), 60);

        // 所定内深夜 (125%): 時給 * 1.25 * 分 / 60 = 時給 * 5 * 分 / 240
        int regularNightPay = yenRound(BigDecimal.valueOf(hourlyWage).multiply(BigDecimal.valueOf(5)).multiply(BigDecimal.valueOf(calc.regularNightWorkMinutes())), 240);

        // 法定外通常 (125%): 時給 * 1.25 * 分 / 60 = 時給 * 5 * 分 / 240
        int overtimePay = yenRound(BigDecimal.valueOf(hourlyWage).multiply(BigDecimal.valueOf(5)).multiply(BigDecimal.valueOf(calc.overtimeWorkMinutes())), 240);

        // 法定外深夜 (150% - 重畳割増): 時給 * 1.50 * 分 / 60 = 時給 * 3 * 分 / 120
        int nightOvertimePay = yenRound(BigDecimal.valueOf(hourlyWage).multiply(BigDecimal.valueOf(3)).multiply(BigDecimal.valueOf(calc.nightOvertimeWorkMinutes())), 120);

        return new DetailedDailyPayResult(
                regularPay,
                regularNightPay,
                overtimePay,
                nightOvertimePay,
                basePay,
                nightAllowance,
                overtimeAllowance,
                totalPay
        );
    }

    /**
     * 日本円の整数四捨五入 (RoundingMode.HALF_UP)
     */
    public static int yenRound(BigDecimal numerator, int denominator) {
        return numerator.divide(BigDecimal.valueOf(denominator), 0, RoundingMode.HALF_UP)
                .intValueExact();
    }

    /**
     * 【行政通達 昭和62年3月14日付 基発第150号 準拠】月間3大端数処理ユーティリティ
     */
    public static final class StatutoryRounding {

        private StatutoryRounding() {}

        /**
         * ① 1箇月における時間外労働・休日労働・深夜労働の各時間数の合計における端数処理:
         * 30分未満の端数を切り捨て、30分以上を1時間に切り上げる。
         *
         * @param totalMinutes 月間の対象労働時間（分）
         * @return 端数処理後の時間（時間単位、整数）
         */
        public static int roundMonthlyHours(int totalMinutes) {
            if (totalMinutes < 0) {
                throw new IllegalArgumentException("時間数は0以上である必要があります。");
            }
            int hours = totalMinutes / 60;
            int remainingMinutes = totalMinutes % 60;
            return (remainingMinutes >= 30) ? hours + 1 : hours;
        }

        /**
         * ② 1時間当たりの賃金額および割増賃金額における端数処理:
         * 50銭未満の端数を切り捨て、50銭以上1円未満を1円に切り上げる（HALF_UP）。
         *
         * @param exactRateExact 未丸めの時間給または割増単価（BigDecimal）
         * @return 端数処理後の1円単位単価
         */
        public static int roundHourlyRate(BigDecimal exactRateExact) {
            if (exactRateExact == null) {
                throw new IllegalArgumentException("単価は必須です。");
            }
            return exactRateExact.setScale(0, RoundingMode.HALF_UP).intValueExact();
        }

        /**
         * ③ 1箇月における時間外労働・休日労働・深夜労働の各割増賃金の総額における端数処理:
         * 1円未満の端数を50銭未満切り捨て、50銭以上1円未満を1円に切り上げる（HALF_UP）。
         *
         * @param exactMonthlyAllowance 未丸めの月間割増手当総額（BigDecimal）
         * @return 端数処理後の1円単位月間手当額
         */
        public static int roundMonthlyAllowanceTotal(BigDecimal exactMonthlyAllowance) {
            if (exactMonthlyAllowance == null) {
                throw new IllegalArgumentException("手当額は必須です。");
            }
            return exactMonthlyAllowance.setScale(0, RoundingMode.HALF_UP).intValueExact();
        }
    }
}
```

## FILE: src\main\java\com\example\kintai\domain\payroll\mapper\PayrollMapper.java
```
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

    List<Long> findDraftStaffIdsByStoreAndMonth(
            @Param("storeId") Long storeId,
            @Param("targetYearMonth") String targetYearMonth
    );

    // 店舗名取得 (給与台帳エクスポート帳票ヘッダー用)
    String findStoreNameById(@Param("storeId") Long storeId);
}
```

## FILE: src\main\resources\mapper\payroll\PayrollMapper.xml
```
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

    <select id="lockStoreStaffForUpdate" resultType="java.lang.Long">
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
        ORDER BY store_staff_id ASC
    </select>

    <select id="findDraftStaffIdsByStoreAndMonth" resultType="java.lang.Long">
        SELECT DISTINCT store_staff_id
        FROM payroll
        WHERE store_id = #{storeId}
          AND target_year_month = #{targetYearMonth}
          AND payroll_status = 'DRAFT'
        ORDER BY store_staff_id ASC
    </select>

    <select id="findStoreNameById" resultType="java.lang.String">
        SELECT store_name
        FROM store
        WHERE store_id = #{storeId}
    </select>

</mapper>
```

## FILE: src\main\resources\db\schema.sql
```
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
```

