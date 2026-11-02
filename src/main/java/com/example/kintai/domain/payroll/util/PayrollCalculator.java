package com.example.kintai.domain.payroll.util;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeParseException;

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
     *
     * @param hourlyWage 勤務日基準の適用契約時給
     * @param totalNetWorkMinutes 該当日の総実労働時間 (分)
     * @param totalNightMinutes 該当日の総深夜労働時間 (分)
     * @param totalOvertimeMinutes 該当日の総時間外労働時間 (分)
     */
    public static DailyPayResult calculateDailyPay(
            int hourlyWage,
            int totalNetWorkMinutes,
            int totalNightMinutes,
            int totalOvertimeMinutes
    ) {
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
     * 日本円の整数四捨五入 (RoundingMode.HALF_UP)
     */
    public static int yenRound(BigDecimal numerator, int denominator) {
        return numerator.divide(BigDecimal.valueOf(denominator), 0, RoundingMode.HALF_UP)
                .intValueExact();
    }
}
