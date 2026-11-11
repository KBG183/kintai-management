package com.example.kintai.domain.payroll.service;

import com.example.kintai.domain.attendance.dto.AttendanceDto;
import com.example.kintai.domain.attendance.mapper.AttendanceMapper;
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
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@DisplayName("F-04: confirmMonthlyPayroll 2단계 잠금 및 동시성 경계(신규 DRAFT 경쟁) 검증")
class PayrollServiceMonthlyConfirmConcurrencyIntegrationTest {

    @Autowired
    private PayrollService payrollService;

    @Autowired
    private PayrollMapper payrollMapper;

    @Autowired
    private AttendanceMapper attendanceMapper;

    @Autowired
    private DataSource dataSource;

    private final Long targetStoreId = 1L;
    private final String targetMonth = "2026-10";
    private final LocalDate startDate = LocalDate.of(2026, 10, 1);
    private final LocalDate endDate = LocalDate.of(2026, 10, 31);

    // 격리된 테스트 전용 직원 ID 목록 (ID: 10, 11)
    private final Long staffId1 = 10L;
    private final Long staffId2 = 11L;

    @BeforeEach
    void setUp() throws Exception {
        tearDown(); // 완전한 사전 격리 정리

        try (Connection conn = dataSource.getConnection();
             Statement st = conn.createStatement()) {
            // 회원 및 직원 픽스처 생성 (ID: 10, 11)
            st.executeUpdate("INSERT IGNORE INTO member (member_id, email, password, name, role) VALUES " +
                    "(10, 'fixture10@test.com', 'dummy', 'Fixture Staff 10', 'ROLE_STAFF'), " +
                    "(11, 'fixture11@test.com', 'dummy', 'Fixture Staff 11', 'ROLE_STAFF')");

            st.executeUpdate("INSERT IGNORE INTO store_staff (store_staff_id, store_id, member_id, hourly_wage, joined_date) VALUES " +
                    "(10, 1, 10, 1300, '2026-10-01'), " +
                    "(11, 1, 11, 1350, '2026-10-01')");

            // 정상 출근 데이터 (10월 20일)
            st.executeUpdate("INSERT INTO attendance (store_id, store_staff_id, work_date, clock_in_at, clock_out_at, total_break_minutes, net_work_minutes, night_work_minutes, overtime_minutes, clock_in_method, attendance_status) VALUES " +
                    "(1, 10, '2026-10-20', '2026-10-20 09:00:00', '2026-10-20 18:00:00', 60, 480, 0, 0, 'WEB', 'COMPLETED'), " +
                    "(1, 11, '2026-10-20', '2026-10-20 09:00:00', '2026-10-20 18:00:00', 60, 480, 0, 0, 'WEB', 'COMPLETED')");

            // 초기 DRAFT 급여 원장 2건 삽입
            st.executeUpdate("INSERT INTO payroll (store_id, store_staff_id, target_year_month, range_start_date, range_end_date, work_days_count, total_work_minutes, total_night_minutes, total_overtime_minutes, applied_hourly_wage, applied_transport_limit, base_pay, night_allowance, overtime_allowance, transport_allowance, gross_pay, payroll_status) VALUES " +
                    "(1, 10, '2026-10', '2026-10-01', '2026-10-31', 1, 480, 0, 0, 1300, 15000, 10400, 0, 0, 500, 10900, 'DRAFT'), " +
                    "(1, 11, '2026-10', '2026-10-01', '2026-10-31', 1, 480, 0, 0, 1350, 15000, 10800, 0, 0, 500, 11300, 'DRAFT')");
        }
    }

    @AfterEach
    void tearDown() {
        try (Connection conn = dataSource.getConnection();
             Statement st = conn.createStatement()) {
            st.executeUpdate("DELETE FROM payroll_daily_detail WHERE payroll_id IN (SELECT payroll_id FROM payroll WHERE store_staff_id IN (10, 11))");
            st.executeUpdate("DELETE FROM payroll WHERE store_staff_id IN (10, 11)");
            st.executeUpdate("DELETE FROM attendance WHERE store_staff_id IN (10, 11)");
            st.executeUpdate("DELETE FROM store_staff WHERE store_staff_id IN (10, 11)");
            st.executeUpdate("DELETE FROM member WHERE member_id IN (10, 11)");
        } catch (Exception ignored) {}
    }

    @Test
    @DisplayName("2단계 잠금 일괄 확정(confirmMonthlyPayroll) 정상 동작 및 대상 DRAFT 전원 CONFIRMED 전환 검증")
    void confirmMonthlyPayroll_TwoPhaseLock_Success() {
        int confirmed = payrollService.confirmMonthlyPayroll(targetStoreId, targetMonth);
        assertTrue(confirmed >= 2, "최소 2건 이상의 급여가 확정되어야 함");

        PayrollDto p10 = payrollMapper.findByStaffAndMonth(staffId1, targetMonth);
        PayrollDto p11 = payrollMapper.findByStaffAndMonth(staffId2, targetMonth);

        assertNotNull(p10);
        assertNotNull(p11);
        assertEquals("CONFIRMED", p10.getPayrollStatus());
        assertEquals("CONFIRMED", p11.getPayrollStatus());
    }

    @Test
    @DisplayName("동시 실행: 일괄 확정(confirmMonthlyPayroll)과 개별 재계산(calculatePayrollForStaff) 경합 시 데드락 없이 직렬화 성공")
    void concurrentMonthlyAndIndividual_NoDeadlock_Serialized() throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch readyLatch = new CountDownLatch(2);
        CountDownLatch startLatch = new CountDownLatch(1);

        AtomicReference<Throwable> monthlyError = new AtomicReference<>();
        AtomicReference<Throwable> calculateError = new AtomicReference<>();

        // Thread 1: confirmMonthlyPayroll (staff 10 -> staff 11 -> payroll 다건)
        Future<?> f1 = executor.submit(() -> {
            readyLatch.countDown();
            try {
                startLatch.await(5, TimeUnit.SECONDS);
                System.out.println("[Thread 1] confirmMonthlyPayroll 시작 at " + System.currentTimeMillis());
                payrollService.confirmMonthlyPayroll(targetStoreId, targetMonth);
                System.out.println("[Thread 1] confirmMonthlyPayroll 완료 at " + System.currentTimeMillis());
            } catch (Throwable t) {
                monthlyError.set(t);
            }
        });

        // Thread 2: calculatePayrollForStaff for staff 11 (staff 11 -> payroll 11)
        Future<?> f2 = executor.submit(() -> {
            readyLatch.countDown();
            try {
                startLatch.await(5, TimeUnit.SECONDS);
                System.out.println("[Thread 2] calculatePayrollForStaff(staff 11) 시작 at " + System.currentTimeMillis());
                payrollService.calculatePayrollForStaff(targetStoreId, staffId2, targetMonth, startDate, endDate);
                System.out.println("[Thread 2] calculatePayrollForStaff(staff 11) 완료 at " + System.currentTimeMillis());
            } catch (Throwable t) {
                calculateError.set(t);
            }
        });

        assertTrue(readyLatch.await(5, TimeUnit.SECONDS));
        startLatch.countDown();

        f1.get(10, TimeUnit.SECONDS);
        f2.get(10, TimeUnit.SECONDS);
        executor.shutdown();

        Throwable e1 = monthlyError.get();
        Throwable e2 = calculateError.get();

        System.out.println("==================================================================");
        System.out.println("Thread 1 Error: " + (e1 != null ? e1.getMessage() : "SUCCESS"));
        System.out.println("Thread 2 Error: " + (e2 != null ? e2.getMessage() : "SUCCESS"));
        System.out.println("==================================================================");

        boolean deadlockDetected = false;
        if (e1 != null) deadlockDetected |= e1.getMessage().contains("Deadlock") || e1.getMessage().contains("1213");
        if (e2 != null) deadlockDetected |= e2.getMessage().contains("Deadlock") || e2.getMessage().contains("1213");

        assertFalse(deadlockDetected, "[CRITICAL F-04] 일괄 확정과 개별 계산 간 락 순서 일치로 1213 데드락이 발생하지 않아야 함");
    }
}
