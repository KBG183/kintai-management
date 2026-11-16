package com.example.kintai.domain.payroll.service;

import com.example.kintai.domain.attendance.dto.AttendanceDto;
import com.example.kintai.domain.attendance.mapper.AttendanceMapper;
import com.example.kintai.domain.payroll.dto.PayrollConfirmRequestDto;
import com.example.kintai.domain.payroll.dto.PayrollDailyDetailDto;
import com.example.kintai.domain.payroll.dto.PayrollDto;
import com.example.kintai.domain.payroll.mapper.PayrollMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.Statement;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
// ★重要: テストメソッドレベルの @Transactional を明示的に排除し、テストフレームワークのロールバック偽陽性を完全に防止
@DisplayName("F-08: PayrollService内部呼出時の実DB変更およびトランザクション全体ロールバックの原子性精密実測")
class PayrollServiceTransactionRollbackIntegrationTest {

    @Autowired
    private PayrollService payrollService;

    @Autowired
    private PayrollMapper payrollMapper;

    @Autowired
    private AttendanceMapper attendanceMapper;

    @Autowired
    private DataSource dataSource;

    private Long testPayrollId;
    private Long testAttendanceId1;
    private Long testAttendanceId2;

    @BeforeEach
    void setUp() {
        try (Connection conn = dataSource.getConnection();
             Statement st = conn.createStatement()) {
            st.executeUpdate("DELETE FROM payroll_daily_detail WHERE payroll_id IN (SELECT payroll_id FROM payroll WHERE store_staff_id = 1 AND target_year_month = '2026-10')");
            st.executeUpdate("DELETE FROM payroll WHERE store_staff_id = 1 AND target_year_month = '2026-10'");
            st.executeUpdate("DELETE FROM attendance WHERE store_staff_id = 1 AND work_date IN ('2026-10-15', '2026-10-16')");
        } catch (Exception ignored) {}

        // テスト開始前: 実際の DB に初期 DRAFT 給与と1件の勤怠/明細データを物理的に INSERT & COMMIT
        String targetMonth = "2026-10";
        LocalDate start = LocalDate.of(2026, 10, 1);
        LocalDate end = LocalDate.of(2026, 10, 31);

        AttendanceDto att1 = new AttendanceDto();
        att1.setStoreId(1L);
        att1.setStoreStaffId(1L);
        att1.setWorkDate(LocalDate.of(2026, 10, 15));
        att1.setClockInAt(LocalDateTime.of(2026, 10, 15, 9, 0));
        att1.setClockOutAt(LocalDateTime.of(2026, 10, 15, 18, 0));
        att1.setTotalBreakMinutes(60);
        att1.setNetWorkMinutes(480);
        att1.setNightWorkMinutes(0);
        att1.setOvertimeMinutes(0);
        att1.setClockInMethod("BUTTON");
        att1.setAttendanceStatus("COMPLETED");
        attendanceMapper.insertAttendance(att1);
        testAttendanceId1 = att1.getAttendanceId();

        PayrollDto draft = new PayrollDto();
        draft.setStoreId(1L);
        draft.setStoreStaffId(1L);
        draft.setTargetYearMonth(targetMonth);
        draft.setRangeStartDate(start);
        draft.setRangeEndDate(end);
        draft.setWorkDaysCount(1);
        draft.setTotalWorkMinutes(480);
        draft.setTotalNightMinutes(0);
        draft.setTotalOvertimeMinutes(0);
        draft.setAppliedHourlyWage(1300);
        draft.setAppliedTransportLimit(15000);
        draft.setBasePay(10400);
        draft.setNightAllowance(0);
        draft.setOvertimeAllowance(0);
        draft.setTransportAllowance(500);
        draft.setGrossPay(10900);
        draft.setPayrollStatus("DRAFT");
        payrollMapper.upsertPayroll(draft);
        testPayrollId = draft.getPayrollId();

        PayrollDailyDetailDto d1 = new PayrollDailyDetailDto();
        d1.setPayrollId(testPayrollId);
        d1.setAttendanceId(testAttendanceId1);
        d1.setWorkDate(LocalDate.of(2026, 10, 15));
        d1.setAppliedHourlyWage(1300);
        d1.setTotalBreakMinutes(60);
        d1.setNetWorkMinutes(480);
        d1.setOvertimeMinutes(0);
        d1.setNightWorkMinutes(0);
        d1.setBasePay(10400);
        d1.setOvertimeAllowance(0);
        d1.setNightAllowance(0);
        d1.setTransportAmount(500);
        payrollMapper.insertPayrollDailyDetail(d1);
    }

    @AfterEach
    void tearDown() {
        // テスト終了後の DB クリーンアップ
        if (testPayrollId != null) {
            payrollMapper.deleteDailyDetailsByPayrollId(testPayrollId);
        }
    }

    @Test
    @DisplayName("内部再計算(calculatePayrollForStaff)による金額/明細変更中に例外発生時、実DBが初期状態へ100%ロールバックされることの実測")
    void confirmPayroll_WhenInnerValidationFails_ShouldRollbackActualDbMutations() {
        // Given: 勤怠データが1件追加され、再計算時に金額と明細レコードが大幅に変更される環境を構築
        // (再計算時: workDaysCount=2, totalWorkMinutes=960, grossPay=21800 へ変更対象)
        AttendanceDto att2 = new AttendanceDto();
        att2.setStoreId(1L);
        att2.setStoreStaffId(1L);
        att2.setWorkDate(LocalDate.of(2026, 10, 16));
        att2.setClockInAt(LocalDateTime.of(2026, 10, 16, 9, 0));
        att2.setClockOutAt(LocalDateTime.of(2026, 10, 16, 18, 0));
        att2.setTotalBreakMinutes(60);
        att2.setNetWorkMinutes(480);
        att2.setNightWorkMinutes(0);
        att2.setOvertimeMinutes(0);
        att2.setClockInMethod("BUTTON");
        att2.setAttendanceStatus("COMPLETED");
        attendanceMapper.insertAttendance(att2);
        testAttendanceId2 = att2.getAttendanceId();

        // 初期状態の再確認（計1件、総支給額10,900円）
        PayrollDto beforeCall = payrollMapper.findById(testPayrollId);
        List<PayrollDailyDetailDto> detailsBeforeCall = payrollMapper.findDailyDetailsByPayrollId(testPayrollId);
        assertEquals(10900, beforeCall.getGrossPay(), "初期総支給額は10900円であること");
        assertEquals(1, detailsBeforeCall.size(), "初期明細レコードは1件であること");

        // 店舗確認予想金額を意図的に不一致として送信（例: 999999円 != 再計算額21800円）
        // confirmPayroll 内部で calculatePayrollForStaff（upsert + delete + 2件 insert）が完了した後
        // 'expected != actual' 検証により IllegalStateException が発生
        PayrollConfirmRequestDto mismatchDto = new PayrollConfirmRequestDto();
        mismatchDto.setExpectedGrossPay(999999);

        // When: confirmPayroll 呼び出し
        IllegalStateException thrown = assertThrows(IllegalStateException.class, () -> {
            payrollService.confirmPayroll(testPayrollId, mismatchDto);
        });
        System.out.println("[F-08 検証例外捕捉]: " + thrown.getMessage());
        assertTrue(thrown.getMessage().contains("確認時と金額が一致しません"));

        // Then: サービストランザクション終了後、別クエリで実際の DB データを取得
        // 内部 calculatePayrollForStaff が DB に対して実行した upsert（21800円）および明細2件挿入が
        // サービスの @Transactional により完全にロールバックされ、初期10900円および明細1件に保持されたことを検証
        PayrollDto afterRollback = payrollMapper.findById(testPayrollId);
        List<PayrollDailyDetailDto> detailsAfterRollback = payrollMapper.findDailyDetailsByPayrollId(testPayrollId);

        System.out.println("==================================================================");
        System.out.println("[F-08 厳密実測検証結果（テストレベル @Transactional なし）]");
        System.out.println("1. ロールバック後 GrossPay      (期待値: 10900, 実測値: " + afterRollback.getGrossPay() + ")");
        System.out.println("2. ロールバック後 WorkDaysCount (期待値: 1,     実測値: " + afterRollback.getWorkDaysCount() + ")");
        System.out.println("3. ロールバック後 明細レコード数 (期待値: 1,     実測値: " + detailsAfterRollback.size() + ")");
        System.out.println("4. ロールバック後 ステータス   (期待値: DRAFT, 実測値: " + afterRollback.getPayrollStatus() + ")");
        System.out.println("==================================================================");

        assertEquals(10900, afterRollback.getGrossPay(), "[CRITICAL F-08] 内部 calculatePayrollForStaff の upsert(21800) が復元されること");
        assertEquals(1, afterRollback.getWorkDaysCount(), "[CRITICAL F-08] 出勤日数（2日）の変更が1日へロールバックされること");
        assertEquals(1, detailsAfterRollback.size(), "[CRITICAL F-08] 日別明細2件挿入がロールバックされ、元の1件のみ保持されること");
        assertEquals("DRAFT", afterRollback.getPayrollStatus(), "ステータスは DRAFT であること");
        assertNull(afterRollback.getConfirmedAt(), "confirmed_at フィールドは null であること");
    }
}
