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
