# 【Day 6 技術面接対策パック】労働基準法第37条 時間外・深夜・重畳割増（150%）精密算定エンジン

本書は、日本型勤怠・給与管理システム（kintai）のDay 6スプリントで実装された「労働基準法第37条算定エンジン」のアーキテクチャ背景、法律的根拠、および全ソースコードを1つに統合した面接対策学習パックです。

---

## 1. ビジネス背景とアーキテクチャ設計根拠（Why & What）

### なぜ `if-else` の条件分岐ではなく「タイムライン・スライシング」なのか？
* **従来の課題（地獄の境界バグ）**:
  多くの勤怠システムでは、`if (現在時刻 > 22:00 && 累計時間 > 8時間)` のような単純な条件分岐で判定しようとします。しかし実務では以下の複合ケースで必ず計算誤差や未払い賃金訴訟リスクが発生します：
  1. 日跨ぎ勤務（例: 13:00出勤 ~ 翌01:00退勤）
  2. 深夜時間帯（22:00~05:00）の真っ只中に取った休憩時間（例: 23:00~00:00の休憩）
  3. 8時間超過（残業突入）の境界が、通常時間帯にあるのか深夜時間帯にあるのか
* **採用した解決策（Timeline Interval Slicing）**:
  1. 総拘束時間から休憩区間を完全にくり抜いた「純粋な実働区間（`WorkInterval`）」を生成。
  2. 実働区間を「05:00」「22:00」「累計480分（8時間）到達点」という**境界線でナイフで切るようにスライス分割**。
  3. 各スライスを以下の4大区分に1分の誤差もなく数学的に振り分け：
     - **所定内通常労働**（100%支給）: 8時間以内 かつ 通常時間帯
     - **所定内深夜労働**（125%支給）: 8時間以内 かつ 深夜時間帯（22:00~05:00）
     - **法定外通常残業**（125%支給）: 8時間超過 かつ 通常時間帯
     - **法定外深夜残業（重畳割増）**（150%支給）: 8時間超過 かつ 深夜時間帯

### 昭和62年労働省告示第37号（端数処理通達）の完全遵守
* 1日ごとの割増手当算定において、50銭（0.5円）未満を切り捨て、50銭以上を切り上げる「四捨五入（`RoundingMode.HALF_UP`）」を `BigDecimal` で厳格に実装。
* 浮動小数点数（`double` / `float`）による1円のズレ（丸め誤差）を完全に排除。

---

## 2. コアソースコード 1: AttendanceTimeCalculator.java

```java
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
 * 実労働時間、所定内深夜、法定外残業、および深夜残業（重畳割増: 150%）を分単位のタイムライン・スライシングにより厳密に分離算定する。
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
     * 従来の基本算定結果（互換性維持用）
     */
    public record CalculationResult(
            int totalStayMinutes,
            int totalBreakMinutes,
            int netWorkMinutes,
            int nightWorkMinutes,
            int overtimeMinutes
    ) {}

    /**
     * 労働基準法第37条に基づく4区分精密算定結果
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
            int overtimeMinutes
    ) {
        public DetailedCalculationResult {
            // 不変式の整合性検証
            if (netWorkMinutes != regularWorkMinutes + regularNightWorkMinutes + overtimeWorkMinutes + nightOvertimeWorkMinutes) {
                throw new IllegalStateException("4区分実労働時間の合計が総実労働時間と一致しません。");
            }
            if (nightWorkMinutes != regularNightWorkMinutes + nightOvertimeWorkMinutes) {
                throw new IllegalStateException("総深夜時間が所定内深夜と深夜残業の合計と一致しません。");
            }
            if (overtimeMinutes != overtimeWorkMinutes + nightOvertimeWorkMinutes) {
                throw new IllegalStateException("総残業時間が通常残業と深夜残業の合計と一致しません。");
            }
        }
    }

    /**
     * 出退勤時刻および休憩区間一覧に基づき、実労働、深夜労働、法定時間外残業を分単位で厳密に算出（基本互換版）
     */
    public static CalculationResult calculate(
            LocalDateTime clockInAt,
            LocalDateTime clockOutAt,
            List<BreakInterval> rawBreaks
    ) {
        DetailedCalculationResult detailed = calculateDetailed(clockInAt, clockOutAt, rawBreaks);
        return new CalculationResult(
                detailed.totalStayMinutes(),
                detailed.totalBreakMinutes(),
                detailed.netWorkMinutes(),
                detailed.nightWorkMinutes(),
                detailed.overtimeMinutes()
        );
    }

    /**
     * 労働基準法第37条に基づくタイムライン・インターバル・スライシング精密算定エンジン
     */
    public static DetailedCalculationResult calculateDetailed(
            LocalDateTime clockInAt,
            LocalDateTime clockOutAt,
            List<BreakInterval> rawBreaks
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

        int totalStayMinutes = Math.toIntExact(Duration.between(clockInAt, clockOutAt).toMinutes());
        int totalBreakMinutes = 0;
        for (BreakInterval b : breaks) {
            totalBreakMinutes = Math.addExact(totalBreakMinutes,
                    Math.toIntExact(Duration.between(b.startAt(), b.endAt()).toMinutes()));
        }

        if (totalBreakMinutes >= totalStayMinutes) {
            throw new IllegalArgumentException("休憩時間が総滞在時間以上になることはできません。");
        }

        int netWorkMinutes = totalStayMinutes - totalBreakMinutes;

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

        // 4区分集計用アキュムレータ
        int regularWorkMinutes = 0;
        int regularNightWorkMinutes = 0;
        int overtimeWorkMinutes = 0;
        int nightOvertimeWorkMinutes = 0;

        int cumulativeNetMinutes = 0;
        final int STATUTORY_LIMIT_MINUTES = 480; // 1日法定労働時間 8時間 = 480分

        for (WorkInterval wi : workIntervals) {
            LocalDateTime segCursor = wi.startAt();
            LocalDateTime segEnd = wi.endAt();

            while (segCursor.isBefore(segEnd)) {
                LocalDateTime nextBound = findNextBoundary(segCursor, segEnd);
                int sliceMinutes = Math.toIntExact(Duration.between(segCursor, nextBound).toMinutes());
                boolean isNight = isNightTime(segCursor);

                if (cumulativeNetMinutes + sliceMinutes <= STATUTORY_LIMIT_MINUTES) {
                    // 全体が所定内（8時間以下）
                    if (isNight) {
                        regularNightWorkMinutes += sliceMinutes;
                    } else {
                        regularWorkMinutes += sliceMinutes;
                    }
                    cumulativeNetMinutes += sliceMinutes;
                } else if (cumulativeNetMinutes >= STATUTORY_LIMIT_MINUTES) {
                    // 全体が法定外（8時間超過）
                    if (isNight) {
                        nightOvertimeWorkMinutes += sliceMinutes;
                    } else {
                        overtimeWorkMinutes += sliceMinutes;
                    }
                    cumulativeNetMinutes += sliceMinutes;
                } else {
                    // スライス内で8時間の境界を跨ぐ場合
                    int regularPart = STATUTORY_LIMIT_MINUTES - cumulativeNetMinutes;
                    int overtimePart = sliceMinutes - regularPart;

                    if (isNight) {
                        regularNightWorkMinutes += regularPart;
                        nightOvertimeWorkMinutes += overtimePart;
                    } else {
                        regularWorkMinutes += regularPart;
                        overtimeWorkMinutes += overtimePart;
                    }
                    cumulativeNetMinutes += sliceMinutes;
                }

                segCursor = nextBound;
            }
        }

        int nightWorkMinutes = regularNightWorkMinutes + nightOvertimeWorkMinutes;
        int overtimeMinutes = overtimeWorkMinutes + nightOvertimeWorkMinutes;

        return new DetailedCalculationResult(
                totalStayMinutes,
                totalBreakMinutes,
                netWorkMinutes,
                regularWorkMinutes,
                regularNightWorkMinutes,
                overtimeWorkMinutes,
                nightOvertimeWorkMinutes,
                nightWorkMinutes,
                overtimeMinutes
        );
    }

    private record WorkInterval(LocalDateTime startAt, LocalDateTime endAt) {}

    public static boolean isNightTime(LocalDateTime time) {
        LocalTime lt = time.toLocalTime();
        return !lt.isBefore(LocalTime.of(22, 0)) || lt.isBefore(LocalTime.of(5, 0));
    }

    private static LocalDateTime findNextBoundary(LocalDateTime current, LocalDateTime maxEnd) {
        LocalDate date = current.toLocalDate();
        LocalDateTime t05 = LocalDateTime.of(date, LocalTime.of(5, 0));
        LocalDateTime t22 = LocalDateTime.of(date, LocalTime.of(22, 0));
        LocalDateTime nextT05 = LocalDateTime.of(date.plusDays(1), LocalTime.of(5, 0));

        LocalDateTime boundary;
        if (current.isBefore(t05)) {
            boundary = t05;
        } else if (current.isBefore(t22)) {
            boundary = t22;
        } else {
            boundary = nextT05;
        }

        return boundary.isBefore(maxEnd) ? boundary : maxEnd;
    }
}
```

---

## 3. コアソースコード 2: PayrollCalculator.java

```java
package com.example.kintai.domain.payroll.util;

import com.example.kintai.domain.attendance.util.AttendanceTimeCalculator;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeParseException;

/**
 * 日本労働基準法第37条および昭和62年労働省告示第37号（端数処理通達）に準拠した給与算定ユーティリティ。
 * 時間外（25%）、深夜（25%）、深夜残業（重畳割増: 50%）および1円単位四捨五入（RoundingMode.HALF_UP）を厳密に処理する。
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

    public static DailyPayResult calculateDailyPay(
            int hourlyWage,
            int totalNetWorkMinutes,
            int totalNightMinutes,
            int totalOvertimeMinutes
    ) {
        if (hourlyWage < 0 || totalNetWorkMinutes < 0 || totalNightMinutes < 0 || totalOvertimeMinutes < 0) {
            throw new IllegalArgumentException("時給および勤務時間数は0以上である必要があります。");
        }

        int basePay = yenRound(BigDecimal.valueOf(hourlyWage).multiply(BigDecimal.valueOf(totalNetWorkMinutes)), 60);
        int nightAllowance = yenRound(BigDecimal.valueOf(hourlyWage).multiply(BigDecimal.valueOf(totalNightMinutes)), 240);
        int overtimeAllowance = yenRound(BigDecimal.valueOf(hourlyWage).multiply(BigDecimal.valueOf(totalOvertimeMinutes)), 240);

        int totalPay = basePay + nightAllowance + overtimeAllowance;
        return new DailyPayResult(basePay, nightAllowance, overtimeAllowance, totalPay);
    }

    public static DetailedDailyPayResult calculateDetailedDailyPay(
            int hourlyWage,
            AttendanceTimeCalculator.DetailedCalculationResult calc
    ) {
        if (hourlyWage < 0 || calc == null) {
            throw new IllegalArgumentException("時給および計算結果オブジェクトは必須です。");
        }

        int basePay = yenRound(BigDecimal.valueOf(hourlyWage).multiply(BigDecimal.valueOf(calc.netWorkMinutes())), 60);
        int nightAllowance = yenRound(BigDecimal.valueOf(hourlyWage).multiply(BigDecimal.valueOf(calc.nightWorkMinutes())), 240);
        int overtimeAllowance = yenRound(BigDecimal.valueOf(hourlyWage).multiply(BigDecimal.valueOf(calc.overtimeMinutes())), 240);
        int totalPay = basePay + nightAllowance + overtimeAllowance;

        int regularPay = yenRound(BigDecimal.valueOf(hourlyWage).multiply(BigDecimal.valueOf(calc.regularWorkMinutes())), 60);
        int regularNightPay = yenRound(BigDecimal.valueOf(hourlyWage).multiply(BigDecimal.valueOf(5)).multiply(BigDecimal.valueOf(calc.regularNightWorkMinutes())), 240);
        int overtimePay = yenRound(BigDecimal.valueOf(hourlyWage).multiply(BigDecimal.valueOf(5)).multiply(BigDecimal.valueOf(calc.overtimeWorkMinutes())), 240);
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

    public static int yenRound(BigDecimal numerator, int denominator) {
        return numerator.divide(BigDecimal.valueOf(denominator), 0, RoundingMode.HALF_UP)
                .intValueExact();
    }
}
```

---

## 4. コアソースコード 3: LaborCalculationEngineTest.java（JUnit 5）

```java
package com.example.kintai.domain.attendance.util;

import com.example.kintai.domain.payroll.util.PayrollCalculator;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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

            assertThat(result.totalStayMinutes()).isEqualTo(540);
            assertThat(result.totalBreakMinutes()).isEqualTo(60);
            assertThat(result.netWorkMinutes()).isEqualTo(480);
            assertThat(result.regularWorkMinutes()).isEqualTo(480);
            assertThat(result.regularNightWorkMinutes()).isZero();
            assertThat(result.overtimeWorkMinutes()).isZero();
            assertThat(result.nightOvertimeWorkMinutes()).isZero();
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

            assertThat(result.netWorkMinutes()).isEqualTo(660);
            assertThat(result.regularWorkMinutes()).isEqualTo(480);
            assertThat(result.overtimeWorkMinutes()).isEqualTo(180);
            assertThat(result.regularNightWorkMinutes()).isZero();
            assertThat(result.nightOvertimeWorkMinutes()).isZero();
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

            assertThat(result.netWorkMinutes()).isEqualTo(660);
            assertThat(result.regularWorkMinutes()).isEqualTo(480);
            assertThat(result.regularNightWorkMinutes()).isZero();
            assertThat(result.overtimeWorkMinutes()).isZero();
            assertThat(result.nightOvertimeWorkMinutes()).isEqualTo(180); // 重畳割増150%
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

            assertThat(result.totalStayMinutes()).isEqualTo(600);
            assertThat(result.totalBreakMinutes()).isEqualTo(60);
            assertThat(result.netWorkMinutes()).isEqualTo(540);
            assertThat(result.regularWorkMinutes()).isEqualTo(240);
            assertThat(result.regularNightWorkMinutes()).isEqualTo(240);
            assertThat(result.overtimeWorkMinutes()).isZero();
            assertThat(result.nightOvertimeWorkMinutes()).isEqualTo(60);
            assertThat(result.nightWorkMinutes()).isEqualTo(300);
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

            assertThat(pay.basePay()).isEqualTo(16500);
            assertThat(pay.nightAllowance()).isEqualTo(1125);
            assertThat(pay.overtimeAllowance()).isEqualTo(1125);
            assertThat(pay.totalPay()).isEqualTo(18750);
            assertThat(pay.regularPay()).isEqualTo(12000);
            assertThat(pay.nightOvertimePay()).isEqualTo(6750);
            assertThat(pay.regularPay() + pay.nightOvertimePay()).isEqualTo(pay.totalPay());
        }

        @Test
        @DisplayName("昭和62年通達基準 1円単位四捨五入(HALF_UP)丸め検証")
        void yenRounding_ShouldRoundHalfUpCorrectly() {
            assertThat(PayrollCalculator.calculateDailyPay(1280, 33, 0, 0).totalPay()).isEqualTo(704);
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
```
