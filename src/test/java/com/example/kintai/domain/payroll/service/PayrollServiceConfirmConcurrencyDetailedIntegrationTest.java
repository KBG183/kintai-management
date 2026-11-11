package com.example.kintai.domain.payroll.service;

import com.example.kintai.domain.attendance.dto.AttendanceDto;
import com.example.kintai.domain.attendance.mapper.AttendanceMapper;
import com.example.kintai.domain.payroll.dto.PayrollConfirmRequestDto;
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
import java.sql.ResultSet;
import java.sql.Statement;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@DisplayName("F-04: confirmPayroll 동시성 정밀 경합 및 동시 중복 확정(CAS) 검증")
class PayrollServiceConfirmConcurrencyDetailedIntegrationTest {

    @Autowired
    private PayrollService payrollService;

    @Autowired
    private PayrollMapper payrollMapper;

    @Autowired
    private AttendanceMapper attendanceMapper;

    @Autowired
    private DataSource dataSource;

    private Long testPayrollId;
    private final Long targetStoreId = 1L;
    private final Long targetStaffId = 2L;
    private final String targetMonth = "2026-10";
    private final LocalDate startDate = LocalDate.of(2026, 10, 1);
    private final LocalDate endDate = LocalDate.of(2026, 10, 31);
    private Long testAttendanceId;

    @BeforeEach
    void setUp() {
        try (Connection conn = dataSource.getConnection();
             Statement st = conn.createStatement()) {
            st.executeUpdate("DELETE FROM payroll WHERE store_staff_id = " + targetStaffId + " AND target_year_month = '" + targetMonth + "'");
            st.executeUpdate("DELETE FROM attendance WHERE store_staff_id = " + targetStaffId);
        } catch (Exception ignored) {}

        AttendanceDto att = new AttendanceDto();
        att.setStoreId(targetStoreId);
        att.setStoreStaffId(targetStaffId);
        att.setWorkDate(LocalDate.of(2026, 10, 20));
        att.setClockInAt(LocalDateTime.of(2026, 10, 20, 9, 0));
        att.setClockOutAt(LocalDateTime.of(2026, 10, 20, 18, 0));
        att.setTotalBreakMinutes(60);
        att.setNetWorkMinutes(480);
        att.setNightWorkMinutes(0);
        att.setOvertimeMinutes(0);
        att.setClockInMethod("WEB");
        att.setAttendanceStatus("COMPLETED");
        attendanceMapper.insertAttendance(att);
        testAttendanceId = att.getAttendanceId();

        PayrollDto draft = new PayrollDto();
        draft.setStoreId(targetStoreId);
        draft.setStoreStaffId(targetStaffId);
        draft.setTargetYearMonth(targetMonth);
        draft.setRangeStartDate(startDate);
        draft.setRangeEndDate(endDate);
        draft.setWorkDaysCount(1);
        draft.setTotalWorkMinutes(480);
        draft.setTotalNightMinutes(0);
        draft.setTotalOvertimeMinutes(0);
        draft.setAppliedHourlyWage(1350);
        draft.setAppliedTransportLimit(15000);
        draft.setBasePay(10800);
        draft.setNightAllowance(0);
        draft.setOvertimeAllowance(0);
        draft.setTransportAllowance(600);
        draft.setGrossPay(11400);
        draft.setPayrollStatus("DRAFT");
        payrollMapper.upsertPayroll(draft);
        testPayrollId = draft.getPayrollId();
    }

    @AfterEach
    void tearDown() {
        if (testPayrollId != null) {
            payrollMapper.deleteDailyDetailsByPayrollId(testPayrollId);
        }
        if (testAttendanceId != null) {
            try (Connection conn = dataSource.getConnection();
                 Statement st = conn.createStatement()) {
                st.executeUpdate("DELETE FROM attendance WHERE attendance_id = " + testAttendanceId);
            } catch (Exception ignored) {}
        }
    }

    private Long getConnectionId(Connection conn) {
        try (Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery("SELECT CONNECTION_ID()")) {
            if (rs.next()) {
                return rs.getLong(1);
            }
        } catch (Exception ignored) {}
        return -1L;
    }

    @Test
    @DisplayName("동일 급여에 대한 2개 관리자 세션 동시 중복 확정 시도 시 정확히 1건만 성공하고 1건은 상태 차단 검증")
    void concurrentDuplicateConfirm_ExactlyOneSucceeds() throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch readyLatch = new CountDownLatch(2);
        CountDownLatch startLatch = new CountDownLatch(1);

        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger alreadyConfirmedCount = new AtomicInteger(0);
        AtomicReference<Throwable> otherError = new AtomicReference<>();

        Runnable task = () -> {
            readyLatch.countDown();
            try {
                startLatch.await(5, TimeUnit.SECONDS);
                long startMs = System.currentTimeMillis();
                try (Connection conn = dataSource.getConnection()) {
                    Long connId = getConnectionId(conn);
                    System.out.println("[" + Thread.currentThread().getName() + "] ConnectionID=" + connId + " 확정 시도 시작 at " + startMs);
                }

                PayrollConfirmRequestDto req = new PayrollConfirmRequestDto();
                // 10/5 하루 8시간 1350원 = 기본급 10800 + 교통비 600 = 11400 (계산 엔진 일치)
                req.setExpectedGrossPay(11400);
                payrollService.confirmPayroll(testPayrollId, req);

                long endMs = System.currentTimeMillis();
                System.out.println("[" + Thread.currentThread().getName() + "] 확정 성공 완료 at " + endMs);
                successCount.incrementAndGet();
            } catch (IllegalStateException e) {
                if (e.getMessage() != null && e.getMessage().contains("既に確定済みの給与データです")) {
                    System.out.println("[" + Thread.currentThread().getName() + "] 2중 확정 안전 차단 포착: " + e.getMessage());
                    alreadyConfirmedCount.incrementAndGet();
                } else {
                    otherError.set(e);
                }
            } catch (Throwable t) {
                otherError.set(t);
            }
        };

        Future<?> f1 = executor.submit(task);
        Future<?> f2 = executor.submit(task);

        assertTrue(readyLatch.await(5, TimeUnit.SECONDS), "두 스레드가 준비되어야 함");
        startLatch.countDown();

        f1.get(10, TimeUnit.SECONDS);
        f2.get(10, TimeUnit.SECONDS);
        executor.shutdown();

        assertNull(otherError.get(), "예상치 못한 기타 예외가 없어야 함: " + (otherError.get() != null ? otherError.get().getMessage() : ""));
        assertEquals(1, successCount.get(), "동시 확정 시도 중 정확히 1건만 성공해야 함");
        assertEquals(1, alreadyConfirmedCount.get(), "나머지 1건은 이미 확정됨(상태 전이 차단) 예외가 발생해야 함");

        PayrollDto finalPayroll = payrollMapper.findById(testPayrollId);
        assertNotNull(finalPayroll);
        assertEquals("CONFIRMED", finalPayroll.getPayrollStatus(), "최종 상태는 CONFIRMED여야 함");
    }
}
