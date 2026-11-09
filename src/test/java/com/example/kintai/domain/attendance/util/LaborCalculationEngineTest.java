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
        @DisplayName("昭和62年通達基準 1円単位四捨五入(HALF_UP)丸め検証")
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
