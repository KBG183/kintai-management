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
// ★핵심: 테스트 메서드 레벨의 @Transactional을 명시적으로 제거하여 테스트 프레임워크 롤백 착시를 100% 원천 배제
@DisplayName("F-08: PayrollService 내부 호출 시 실제 DB 변경 및 트랜잭션 전체 롤백 원자성 정밀 실측")
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

        // 테스트 시작 전: 실제 DB에 초기 DRAFT 급여와 1건의 근태/상세 데이터를 물리적으로 INSERT & COMMIT
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
        // 테스트 종료 후 DB 클린업
        if (testPayrollId != null) {
            payrollMapper.deleteDailyDetailsByPayrollId(testPayrollId);
        }
    }

    @Test
    @DisplayName("내부 재계산(calculatePayrollForStaff)으로 금액/상세 변경 도중 예외 발생 시, 실제 DB가 초기 상태로 100% 롤백되는지 실측")
    void confirmPayroll_WhenInnerValidationFails_ShouldRollbackActualDbMutations() {
        // Given: 중간에 근태 데이터가 1건 추가되어 재계산 시 금액과 상세 레코드가 실제로 대폭 변경되는 환경 조성
        // (재계산 시 workDaysCount=2, totalWorkMinutes=960, grossPay=21800으로 변경 대상이 됨)
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

        // 초기 상태 재확인 (총 1건, 총지급액 10,900엔)
        PayrollDto beforeCall = payrollMapper.findById(testPayrollId);
        List<PayrollDailyDetailDto> detailsBeforeCall = payrollMapper.findDailyDetailsByPayrollId(testPayrollId);
        assertEquals(10900, beforeCall.getGrossPay(), "초기 총지급액은 10900엔이어야 함");
        assertEquals(1, detailsBeforeCall.size(), "초기 상세 레코드는 1건이어야 함");

        // 점주 확인 예상 금액을 고의로 불일치하게 전송 (예: 999999엔 != 재계산액 21800엔)
        // confirmPayroll 내부에서 calculatePayrollForStaff(upsert + delete + 2건 insert)가 완전히 실행된 후
        // 'expected != actual' 검증에 걸려 IllegalStateException이 터짐
        PayrollConfirmRequestDto mismatchDto = new PayrollConfirmRequestDto();
        mismatchDto.setExpectedGrossPay(999999);

        // When: confirmPayroll 호출
        IllegalStateException thrown = assertThrows(IllegalStateException.class, () -> {
            payrollService.confirmPayroll(testPayrollId, mismatchDto);
        });
        System.out.println("[F-08 검증 예외 포착]: " + thrown.getMessage());
        assertTrue(thrown.getMessage().contains("確認時と金額が一致しません"));

        // Then: 서비스 트랜잭션 종료 후 별도 쿼리로 실제 DB 데이터 조회
        // 내부 calculatePayrollForStaff가 DB에 실행했던 upsert(21800엔) 및 상세 2건 삽입이
        // 서비스 @Transactional에 의해 완전히 롤백되어 초기 10900엔과 상세 1건으로 보존되었는지 검증!
        PayrollDto afterRollback = payrollMapper.findById(testPayrollId);
        List<PayrollDailyDetailDto> detailsAfterRollback = payrollMapper.findDailyDetailsByPayrollId(testPayrollId);

        System.out.println("==================================================================");
        System.out.println("[F-08 엄밀 실측 검증 결과 (테스트 레벨 @Transactional 없음)]");
        System.out.println("1. 롤백 후 GrossPay      (기대값: 10900, 실측값: " + afterRollback.getGrossPay() + ")");
        System.out.println("2. 롤백 후 WorkDaysCount (기대값: 1,     실측값: " + afterRollback.getWorkDaysCount() + ")");
        System.out.println("3. 롤백 후 상세 레코드 수 (기대값: 1,     실측값: " + detailsAfterRollback.size() + ")");
        System.out.println("4. 롤백 후 상태          (기대값: DRAFT, 실측값: " + afterRollback.getPayrollStatus() + ")");
        System.out.println("==================================================================");

        assertEquals(10900, afterRollback.getGrossPay(), "[CRITICAL F-08] 내부 calculatePayrollForStaff의 upsert(21800)가 원복되어야 함");
        assertEquals(1, afterRollback.getWorkDaysCount(), "[CRITICAL F-08] 출근일수(2일) 변경이 1일로 롤백되어야 함");
        assertEquals(1, detailsAfterRollback.size(), "[CRITICAL F-08] 일별 상세 2건 삽입이 롤백되어 원래 1건만 유지되어야 함");
        assertEquals("DRAFT", afterRollback.getPayrollStatus(), "상태는 DRAFT여야 함");
        assertNull(afterRollback.getConfirmedAt(), "confirmed_at 필드는 null이어야 함");
    }
}
