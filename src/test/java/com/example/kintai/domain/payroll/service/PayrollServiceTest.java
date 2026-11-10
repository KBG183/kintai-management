package com.example.kintai.domain.payroll.service;

import com.example.kintai.domain.attendance.mapper.AttendanceMapper;
import com.example.kintai.domain.payroll.dto.PayrollDto;
import com.example.kintai.domain.payroll.mapper.PayrollMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("PayrollService 法定端数処理連携および給与不変性保護単体テスト")
class PayrollServiceTest {

    @Mock
    private PayrollMapper payrollMapper;

    @Mock
    private AttendanceMapper attendanceMapper;

    @InjectMocks
    private PayrollService payrollService;

    @Test
    @DisplayName("確定済み給与(CONFIRMED/PAID)の再計算はIllegalStateExceptionで遮断され不変性が保護されること")
    void calculatePayroll_WhenAlreadyConfirmed_ShouldThrowIllegalStateException() {
        Long storeId = 1L;
        Long staffId = 10L;
        String targetMonth = "2026-11";
        LocalDate start = LocalDate.of(2026, 11, 1);
        LocalDate end = LocalDate.of(2026, 11, 30);

        PayrollDto confirmedPayroll = new PayrollDto();
        confirmedPayroll.setPayrollId(100L);
        confirmedPayroll.setPayrollStatus("CONFIRMED");

        when(payrollMapper.findByStaffAndMonth(staffId, targetMonth)).thenReturn(confirmedPayroll);

        IllegalStateException ex = assertThrows(IllegalStateException.class, () ->
                payrollService.calculatePayrollForStaff(storeId, staffId, targetMonth, start, end)
        );

        assertEquals("指定年月 (2026-11) の給与は既に確定済み(CONFIRMED) のため、再計算できません。(給与不変性保護)", ex.getMessage());
    }

    @Test
    @DisplayName("行政通達 昭和62年3月14日付 基発第150号準拠の月間3大端数処理連携検証")
    void applyStatutoryMonthlyRounding_ShouldApplyMhlwNoticeRulesCorrectly() {
        PayrollDto payroll = new PayrollDto();
        payroll.setAppliedHourlyWage(1300); // 時給1,300円 -> 25%割増単価: 325円 (50銭境界ジャスト整数)
        payroll.setTotalOvertimeMinutes(449); // 7時間29分 -> 30分未満切り捨てで7時間
        payroll.setTotalNightMinutes(450);    // 7時間30分 -> 30分以上切り上げで8時間

        PayrollService.StatutoryPayrollResult result = payrollService.applyStatutoryMonthlyRounding(payroll, 329);

        // ① 月間時間の端数処理（時間外・深夜・休日 それぞれ独立に30分境界処理）
        assertEquals(7, result.roundedOvertimeHours(), "7時間29分は30分未満切り捨てで7時間となること");
        assertEquals(8, result.roundedNightHours(), "7時間30分は30分以上切り上げで8時間となること");
        assertEquals(5, result.roundedHolidayHours(), "5時間29分は30分未満切り捨てで5時間となること");

        // ② 1時間あたりの割増単価（1,300 * 0.25 = 325円、1,300 * 0.35 = 455円）
        assertEquals(325, result.overtimeHourlyPremium());
        assertEquals(325, result.nightHourlyPremium());
        assertEquals(455, result.holidayHourlyPremium());

        // ③ 月間手当総額の端数処理（項目別独立集計）
        // 時間外: 325円 * 7時間 = 2,275円
        assertEquals(2275, result.statutoryOvertimeAllowance());
        // 深夜: 325円 * 8時間 = 2,600円
        assertEquals(2600, result.statutoryNightAllowance());
        // 休日: 455円 * 5時間 = 2,275円
        assertEquals(2275, result.statutoryHolidayAllowance());
    }
}
