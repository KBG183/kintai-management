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
import static org.junit.jupiter.api.Assertions.assertTrue;
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

        when(payrollMapper.findByStaffAndMonthForUpdate(staffId, targetMonth)).thenReturn(confirmedPayroll);

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

    @Test
    @DisplayName("単一給与確定時、既に確定済み(CONFIRMED)の場合は悲観的ロック照会後にIllegalStateExceptionを投げること")
    void confirmPayroll_WhenAlreadyConfirmed_ShouldThrowIllegalStateException() {
        Long payrollId = 200L;
        PayrollDto existing = new PayrollDto();
        existing.setPayrollId(payrollId);
        existing.setStoreId(1L);
        existing.setStoreStaffId(5L);
        existing.setPayrollStatus("CONFIRMED");

        when(payrollMapper.findById(payrollId)).thenReturn(existing);
        when(payrollMapper.findByIdForUpdate(payrollId)).thenReturn(existing);

        IllegalStateException ex = assertThrows(IllegalStateException.class, () ->
                payrollService.confirmPayroll(payrollId, null)
        );

        assertTrue(ex.getMessage().contains("既に確定済みの給与データです"));
    }

    @Test
    @DisplayName("単一給与確定時、締め期間終了前(早期確定)はIllegalStateExceptionで遮断されること")
    void confirmPayroll_WhenPeriodNotEnded_ShouldThrowIllegalStateException() {
        Long payrollId = 201L;
        PayrollDto existing = new PayrollDto();
        existing.setPayrollId(payrollId);
        existing.setStoreId(1L);
        existing.setStoreStaffId(5L);
        existing.setPayrollStatus("DRAFT");
        existing.setRangeEndDate(LocalDate.now().plusDays(5)); // 未来の日付

        when(payrollMapper.findById(payrollId)).thenReturn(existing);
        when(payrollMapper.findByIdForUpdate(payrollId)).thenReturn(existing);

        IllegalStateException ex = assertThrows(IllegalStateException.class, () ->
                payrollService.confirmPayroll(payrollId, null)
        );

        assertEquals("精算対象期間 (" + existing.getRangeEndDate() + ") が終了する前に給与を確定することはできません。", ex.getMessage());
    }

    @Test
    @DisplayName("店舗全体の月次給与一括確定が正常に実行され全件確定されること")
    void confirmMonthlyPayroll_Success() {
        Long storeId = 1L;
        String targetMonth = "2026-10";

        PayrollDto p1 = new PayrollDto();
        p1.setPayrollId(1L);
        p1.setStoreStaffId(101L);
        p1.setPayrollStatus("DRAFT");
        p1.setRangeStartDate(LocalDate.of(2026, 10, 1));
        p1.setRangeEndDate(LocalDate.of(2026, 10, 31));

        PayrollDto p2 = new PayrollDto();
        p2.setPayrollId(2L);
        p2.setStoreStaffId(102L);
        p2.setPayrollStatus("DRAFT");
        p2.setRangeStartDate(LocalDate.of(2026, 10, 1));
        p2.setRangeEndDate(LocalDate.of(2026, 10, 31));

        when(payrollMapper.findDraftStaffIdsByStoreAndMonth(storeId, targetMonth)).thenReturn(java.util.List.of(101L, 102L));
        when(payrollMapper.lockStorePayrollsForUpdate(storeId, targetMonth)).thenReturn(java.util.List.of(p1, p2));
        when(payrollMapper.countUnresolvedAttendance(101L, p1.getRangeStartDate(), p1.getRangeEndDate())).thenReturn(0);
        when(payrollMapper.countUnresolvedAttendance(102L, p2.getRangeStartDate(), p2.getRangeEndDate())).thenReturn(0);
        when(payrollMapper.updateAllPayrollStatusConfirm(org.mockito.ArgumentMatchers.eq(storeId), org.mockito.ArgumentMatchers.eq(targetMonth), org.mockito.ArgumentMatchers.any())).thenReturn(2);

        int confirmedCount = payrollService.confirmMonthlyPayroll(storeId, targetMonth);

        assertEquals(2, confirmedCount);
    }

    @Test
    @DisplayName("店舗全体の月次給与一括確定時、未処理勤怠が存在するスタッフがいる場合は一括確定を遮断すること")
    void confirmMonthlyPayroll_WhenUnresolvedAttendance_ShouldThrowIllegalStateException() {
        Long storeId = 1L;
        String targetMonth = "2026-10";

        PayrollDto p1 = new PayrollDto();
        p1.setPayrollId(1L);
        p1.setStoreStaffId(101L);
        p1.setPayrollStatus("DRAFT");
        p1.setRangeStartDate(LocalDate.of(2026, 10, 1));
        p1.setRangeEndDate(LocalDate.of(2026, 10, 31));

        when(payrollMapper.findDraftStaffIdsByStoreAndMonth(storeId, targetMonth)).thenReturn(java.util.List.of(101L));
        when(payrollMapper.lockStorePayrollsForUpdate(storeId, targetMonth)).thenReturn(java.util.List.of(p1));
        when(payrollMapper.countUnresolvedAttendance(101L, p1.getRangeStartDate(), p1.getRangeEndDate())).thenReturn(1);

        IllegalStateException ex = assertThrows(IllegalStateException.class, () ->
                payrollService.confirmMonthlyPayroll(storeId, targetMonth)
        );

        assertEquals("未処理の勤怠修正申請または打刻が存在するため、月次給与を一括確定できません。(対象スタッフID: 101)", ex.getMessage());
    }
}
