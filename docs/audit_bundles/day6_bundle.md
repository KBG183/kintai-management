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
package com.example.kintai.domain.attendance.util;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * 日本労働基準法第37条（時間外・深夜・休日割増）および第34条（休憩）に基づく勤怠労働時間精密算定ユーティリティ。
 * 秒単位の高精度タイムライン・スライシングにより、実労働時間、所定内深夜、法定外残業、および深夜残業（重畳割増: 150%）を
 * 1秒の誤差もなく厳密に分離算定し、DTOレベルで秒精度を完全保持する。
 */
public final class AttendanceTimeCalculator {

    private AttendanceTimeCalculator() {}

    public record BreakInterval(LocalDateTime startAt, LocalDateTime endAt) {
        public BreakInterval {
            if (startAt == null || endAt == null) {
                throw new IllegalArgumentException("休憩の開始・終了日時は必須です。");
            }
            if (!endAt.isAfter(startAt)) {
                throw new IllegalArgumentException("休憩終了日時は開始日時より後である必要があります。");
            }
        }
    }

    /**
     * 従来の基本算定結果（秒精度保持版）
     */
    public record CalculationResult(
            int totalStayMinutes,
            int totalBreakMinutes,
            int netWorkMinutes,
            int nightWorkMinutes,
            int overtimeMinutes,
            long netWorkSeconds
    ) {
        public CalculationResult(int totalStayMinutes, int totalBreakMinutes, int netWorkMinutes, int nightWorkMinutes, int overtimeMinutes) {
            this(totalStayMinutes, totalBreakMinutes, netWorkMinutes, nightWorkMinutes, overtimeMinutes, (long) netWorkMinutes * 60L);
        }
    }

    /**
     * 労働基準法第37条に基づく4区分精密算定結果（秒精度完全保持）
     *
     * @param totalStayMinutes 総拘束時間（分）
     * @param totalBreakMinutes 総休憩時間（分）
     * @param netWorkMinutes 総実労働時間（分: 総拘束 - 総休憩）
     * @param regularWorkMinutes 所定内通常労働時間（分: 100%支給、8時間以内かつ非深夜）
     * @param regularNightWorkMinutes 所定内深夜労働時間（分: 125%支給、8時間以内かつ深夜22:00~05:00）
     * @param overtimeWorkMinutes 法定外通常残業時間（分: 125%支給、8時間超過かつ非深夜）
     * @param nightOvertimeWorkMinutes 法定外深夜残業時間（分: 150%重畳割増支給、8時間超過かつ深夜22:00~05:00）
     * @param nightWorkMinutes 総深夜労働時間（分: regularNightWorkMinutes + nightOvertimeWorkMinutes）
     * @param overtimeMinutes 総法定外残業時間（分: overtimeWorkMinutes + nightOvertimeWorkMinutes）
     * @param netWorkSeconds 総実労働時間（秒）
     * @param regularWorkSeconds 所定内通常労働時間（秒）
     * @param regularNightWorkSeconds 所定内深夜労働時間（秒）
     * @param overtimeWorkSeconds 法定外通常残業時間（秒）
     * @param nightOvertimeWorkSeconds 法定外深夜残業時間（秒）
     * @param totalStaySeconds 総拘束時間（秒）
     * @param totalBreakSeconds 総休憩時間（秒）
     */
    public record DetailedCalculationResult(
            int totalStayMinutes,
            int totalBreakMinutes,
            int netWorkMinutes,
            int regularWorkMinutes,
            int regularNightWorkMinutes,
            int overtimeWorkMinutes,
            int nightOvertimeWorkMinutes,
            int nightWorkMinutes,
            int overtimeMinutes,
            long netWorkSeconds,
            long regularWorkSeconds,
            long regularNightWorkSeconds,
            long overtimeWorkSeconds,
            long nightOvertimeWorkSeconds,
            long totalStaySeconds,
            long totalBreakSeconds
    ) {}

    public static CalculationResult calculate(
            LocalDateTime clockInAt,
            LocalDateTime clockOutAt,
            List<BreakInterval> rawBreaks
    ) {
        return calculate(clockInAt, clockOutAt, rawBreaks, 0L);
    }

    public static CalculationResult calculate(
            LocalDateTime clockInAt,
            LocalDateTime clockOutAt,
            List<BreakInterval> rawBreaks,
            int priorNetWorkMinutes
    ) {
        return calculate(clockInAt, clockOutAt, rawBreaks, (long) priorNetWorkMinutes * 60L);
    }

    public static CalculationResult calculate(
            LocalDateTime clockInAt,
            LocalDateTime clockOutAt,
            List<BreakInterval> rawBreaks,
            long priorNetWorkSeconds
    ) {
        DetailedCalculationResult detailed = calculateDetailed(clockInAt, clockOutAt, rawBreaks, priorNetWorkSeconds);
        return new CalculationResult(
                detailed.totalStayMinutes(),
                detailed.totalBreakMinutes(),
                detailed.netWorkMinutes(),
                detailed.nightWorkMinutes(),
                detailed.overtimeMinutes(),
                detailed.netWorkSeconds()
        );
    }

    public static DetailedCalculationResult calculateDetailed(
            LocalDateTime clockInAt,
            LocalDateTime clockOutAt,
            List<BreakInterval> rawBreaks
    ) {
        return calculateDetailed(clockInAt, clockOutAt, rawBreaks, 0L);
    }

    public static DetailedCalculationResult calculateDetailed(
            LocalDateTime clockInAt,
            LocalDateTime clockOutAt,
            List<BreakInterval> rawBreaks,
            int priorNetWorkMinutes
    ) {
        return calculateDetailed(clockInAt, clockOutAt, rawBreaks, (long) priorNetWorkMinutes * 60L);
    }

    /**
     * 労働基準法第37条に基づく秒精度タイムライン・インターバル・スライシング精密算定エンジン（先行累計秒数連動版）
     */
    public static DetailedCalculationResult calculateDetailed(
            LocalDateTime clockInAt,
            LocalDateTime clockOutAt,
            List<BreakInterval> rawBreaks,
            long priorNetWorkSeconds
    ) {
        if (clockInAt == null || clockOutAt == null) {
            throw new IllegalArgumentException("出勤および退勤日時は必須です。");
        }
        if (!clockOutAt.isAfter(clockInAt)) {
            throw new IllegalArgumentException("退勤日時は出勤日時より後である必要があります。");
        }

        List<BreakInterval> breaks = (rawBreaks != null) ? new ArrayList<>(rawBreaks) : new ArrayList<>();

        // 休憩区間のソートおよび整合性検証（出退勤範囲内かつ重複禁止）
        breaks.sort(Comparator.comparing(BreakInterval::startAt));
        for (int i = 0; i < breaks.size(); i++) {
            BreakInterval b = breaks.get(i);
            if (b.startAt().isBefore(clockInAt) || b.endAt().isAfter(clockOutAt)) {
                throw new IllegalArgumentException("休憩時間は勤務時間内である必要があります。");
            }
            if (i > 0) {
                BreakInterval prev = breaks.get(i - 1);
                if (b.startAt().isBefore(prev.endAt())) {
                    throw new IllegalArgumentException("休憩時間帯同士が重複しています。");
                }
            }
        }

        long totalStaySeconds = Duration.between(clockInAt, clockOutAt).getSeconds();
        long totalBreakSeconds = 0;
        for (BreakInterval b : breaks) {
            totalBreakSeconds += Duration.between(b.startAt(), b.endAt()).getSeconds();
        }

        if (totalBreakSeconds >= totalStaySeconds) {
            throw new IllegalArgumentException("休憩時間が総滞在時間以上になることはできません。");
        }

        long netWorkSeconds = totalStaySeconds - totalBreakSeconds;

        // タイムラインから休憩区間を除外した「実働区間」の一覧を生成
        List<WorkInterval> workIntervals = new ArrayList<>();
        LocalDateTime cursor = clockInAt;

        for (BreakInterval b : breaks) {
            if (b.startAt().isAfter(cursor)) {
                workIntervals.add(new WorkInterval(cursor, b.startAt()));
            }
            cursor = b.endAt();
        }
        if (clockOutAt.isAfter(cursor)) {
            workIntervals.add(new WorkInterval(cursor, clockOutAt));
        }

        // 4区分集計用秒単位アキュムレータ
        long regularWorkSeconds = 0;
        long regularNightWorkSeconds = 0;
        long overtimeWorkSeconds = 0;
        long nightOvertimeWorkSeconds = 0;

        long cumulativeNetSeconds = Math.max(0L, priorNetWorkSeconds);
        final long STATUTORY_LIMIT_SECONDS = 8L * 3600L; // 1日法定労働時間 8時間 = 28,800秒

        for (WorkInterval wi : workIntervals) {
            LocalDateTime segCursor = wi.startAt();
            LocalDateTime segEnd = wi.endAt();

            while (segCursor.isBefore(segEnd)) {
                LocalDateTime nextBound = findNextBoundary(segCursor, segEnd);
                long sliceSeconds = Duration.between(segCursor, nextBound).getSeconds();
                boolean isNight = isNightTime(segCursor);

                if (cumulativeNetSeconds + sliceSeconds <= STATUTORY_LIMIT_SECONDS) {
                    // 全体が所定内（8時間以下）
                    if (isNight) {
                        regularNightWorkSeconds += sliceSeconds;
                    } else {
                        regularWorkSeconds += sliceSeconds;
                    }
                    cumulativeNetSeconds += sliceSeconds;
                } else if (cumulativeNetSeconds >= STATUTORY_LIMIT_SECONDS) {
                    // 全体が法定外（8時間超過）
                    if (isNight) {
                        nightOvertimeWorkSeconds += sliceSeconds;
                    } else {
                        overtimeWorkSeconds += sliceSeconds;
                    }
                    cumulativeNetSeconds += sliceSeconds;
                } else {
                    // スライス内で8時間の境界を跨ぐ場合
                    long regularPart = STATUTORY_LIMIT_SECONDS - cumulativeNetSeconds;
                    long overtimePart = sliceSeconds - regularPart;

                    if (isNight) {
                        regularNightWorkSeconds += regularPart;
                        nightOvertimeWorkSeconds += overtimePart;
                    } else {
                        regularWorkSeconds += regularPart;
                        overtimeWorkSeconds += overtimePart;
                    }
                    cumulativeNetSeconds += sliceSeconds;
                }
                segCursor = nextBound;
            }
        }

        int totalStayMinutes = (int) (totalStaySeconds / 60);
        int totalBreakMinutes = (int) (totalBreakSeconds / 60);
        int netWorkMinutes = (int) (netWorkSeconds / 60);
        int regMin = (int) (regularWorkSeconds / 60);
        int regNightMin = (int) (regularNightWorkSeconds / 60);
        int otMin = (int) (overtimeWorkSeconds / 60);
        int nightOtMin = (int) (nightOvertimeWorkSeconds / 60);

        int totalNightMinutes = regNightMin + nightOtMin;
        int totalOvertimeMinutes = otMin + nightOtMin;

        return new DetailedCalculationResult(
                totalStayMinutes,
                totalBreakMinutes,
                netWorkMinutes,
                regMin,
                regNightMin,
                otMin,
                nightOtMin,
                totalNightMinutes,
                totalOvertimeMinutes,
                netWorkSeconds,
                regularWorkSeconds,
                regularNightWorkSeconds,
                overtimeWorkSeconds,
                nightOvertimeWorkSeconds,
                totalStaySeconds,
                totalBreakSeconds
        );
    }

    private static LocalDateTime findNextBoundary(LocalDateTime current, LocalDateTime limit) {
        LocalDate date = current.toLocalDate();
        List<LocalDateTime> boundaries = new ArrayList<>();

        boundaries.add(date.atTime(LocalTime.of(5, 0)));
        boundaries.add(date.atTime(LocalTime.of(22, 0)));
        boundaries.add(date.plusDays(1).atTime(LocalTime.of(5, 0)));
        boundaries.add(date.plusDays(1).atTime(LocalTime.of(22, 0)));
        boundaries.add(date.plusDays(2).atTime(LocalTime.of(5, 0)));

        Collections.sort(boundaries);

        for (LocalDateTime b : boundaries) {
            if (b.isAfter(current)) {
                return b.isBefore(limit) ? b : limit;
            }
        }
        return limit;
    }

    public static boolean isNightTime(LocalDateTime dt) {
        LocalTime t = dt.toLocalTime();
        return !t.isBefore(LocalTime.of(22, 0)) || t.isBefore(LocalTime.of(5, 0));
    }

    private record WorkInterval(LocalDateTime startAt, LocalDateTime endAt) {}
}
package com.example.kintai.domain.payroll.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * 行政通達（昭和62年3月14日付 基発第150号）に基づく月間3大端数処理の境界値テスト
 */
class StatutoryRoundingTest {

    @Nested
    @DisplayName("① 月間労働時間の端数処理（29分切り捨て / 30分切り上げ）")
    class MonthlyHoursRoundingTest {
        @Test
        @DisplayName("29分端数は切り捨てられること")
        void testRoundDownAt29Minutes() {
            // 7時間29分 = 449分 -> 7時間
            assertEquals(7, PayrollCalculator.StatutoryRounding.roundMonthlyHours(449));
        }

        @Test
        @DisplayName("30分端数は1時間に切り上げられること")
        void testRoundUpAt30Minutes() {
            // 7時間30分 = 450分 -> 8時間
            assertEquals(8, PayrollCalculator.StatutoryRounding.roundMonthlyHours(450));
        }

        @Test
        @DisplayName("59分端数は1時間に切り上げられること")
        void testRoundUpAt59Minutes() {
            // 7時間59分 = 479分 -> 8時間
            assertEquals(8, PayrollCalculator.StatutoryRounding.roundMonthlyHours(479));
        }
    }

    @Nested
    @DisplayName("② 時間当たり賃金額・割増賃金額の端数処理（49銭切り捨て / 50銭切り上げ）")
    class HourlyRateRoundingTest {
        @Test
        @DisplayName("0.49円（49銭）は切り捨てられること")
        void testRoundDownAt49Sen() {
            BigDecimal rate = new BigDecimal("1625.49");
            assertEquals(1625, PayrollCalculator.StatutoryRounding.roundHourlyRate(rate));
        }

        @Test
        @DisplayName("0.50円（50銭）は1円に切り上げられること")
        void testRoundUpAt50Sen() {
            BigDecimal rate = new BigDecimal("1625.50");
            assertEquals(1626, PayrollCalculator.StatutoryRounding.roundHourlyRate(rate));
        }
    }

    @Nested
    @DisplayName("③ 月間割増賃金総額の端数処理（49銭切り捨て / 50銭切り上げ）")
    class MonthlyAllowanceRoundingTest {
        @Test
        @DisplayName("総額で0.49円端数は切り捨てられること")
        void testRoundDownAt49Sen() {
            BigDecimal allowance = new BigDecimal("32450.49");
            assertEquals(32450, PayrollCalculator.StatutoryRounding.roundMonthlyAllowanceTotal(allowance));
        }

        @Test
        @DisplayName("総額で0.50円端数は1円に切り上げられること")
        void testRoundUpAt50Sen() {
            BigDecimal allowance = new BigDecimal("32450.50");
            assertEquals(32451, PayrollCalculator.StatutoryRounding.roundMonthlyAllowanceTotal(allowance));
        }
    }
}
