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
