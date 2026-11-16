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
        // 0分 -> 0時間
        assertEquals(0, PayrollCalculator.StatutoryRounding.roundMonthlyHours(0));

        // 29分 -> 0時間（30分未満切り捨て）
        assertEquals(0, PayrollCalculator.StatutoryRounding.roundMonthlyHours(29));

        // 30分 -> 1時間（30分以上1時間切り上げ）
        assertEquals(1, PayrollCalculator.StatutoryRounding.roundMonthlyHours(30));

        // 449分（7時間29分）-> 7時間（30分未満切り捨て）
        assertEquals(7, PayrollCalculator.StatutoryRounding.roundMonthlyHours(449));

        // 450分（7時間30分）-> 8時間（30分以上1時間切り上げ）
        assertEquals(8, PayrollCalculator.StatutoryRounding.roundMonthlyHours(450));

        // 451分（7時間31分）-> 8時間
        assertEquals(8, PayrollCalculator.StatutoryRounding.roundMonthlyHours(451));

        // 負数入力時の例外発生検証
        assertThrows(IllegalArgumentException.class, () ->
                PayrollCalculator.StatutoryRounding.roundMonthlyHours(-1));
    }

    @Test
    @DisplayName("② 1時間当たりの賃金額および割増賃金額（50銭境界・HALF_UP）の厳密検証")
    void roundHourlyRate_BoundaryCases() {
        // 324.49円（50銭未満）-> 324円（切り捨て）
        assertEquals(324, PayrollCalculator.StatutoryRounding.roundHourlyRate(new BigDecimal("324.49")));

        // 324.50円（50銭以上）-> 325円（切り上げ、HALF_UP）
        assertEquals(325, PayrollCalculator.StatutoryRounding.roundHourlyRate(new BigDecimal("324.50")));

        // 324.51円 -> 325円
        assertEquals(325, PayrollCalculator.StatutoryRounding.roundHourlyRate(new BigDecimal("324.51")));

        // 0.49円 -> 0円
        assertEquals(0, PayrollCalculator.StatutoryRounding.roundHourlyRate(new BigDecimal("0.49")));

        // 0.50円 -> 1円
        assertEquals(1, PayrollCalculator.StatutoryRounding.roundHourlyRate(new BigDecimal("0.50")));

        // null 入力時の例外検証
        assertThrows(IllegalArgumentException.class, () ->
                PayrollCalculator.StatutoryRounding.roundHourlyRate(null));
    }

    @Test
    @DisplayName("③ 月間割増手当総額の端数処理（50銭境界・HALF_UP）の厳密検証")
    void roundMonthlyAllowanceTotal_BoundaryCases() {
        // 12345.49円（50銭未満）-> 12345円（切り捨て）
        assertEquals(12345, PayrollCalculator.StatutoryRounding.roundMonthlyAllowanceTotal(new BigDecimal("12345.49")));

        // 12345.50円（50銭以上）-> 12346円（切り上げ、HALF_UP）
        assertEquals(12346, PayrollCalculator.StatutoryRounding.roundMonthlyAllowanceTotal(new BigDecimal("12345.50")));

        // 12345.51円 -> 12346円
        assertEquals(12346, PayrollCalculator.StatutoryRounding.roundMonthlyAllowanceTotal(new BigDecimal("12345.51")));

        // null 入力時の例外検証
        assertThrows(IllegalArgumentException.class, () ->
                PayrollCalculator.StatutoryRounding.roundMonthlyAllowanceTotal(null));
    }
}
