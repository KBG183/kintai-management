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
