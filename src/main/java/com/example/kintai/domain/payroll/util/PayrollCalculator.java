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
