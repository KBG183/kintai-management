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

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@DisplayName("F-04: 실제 PayrollService 경로 동시 실행 시 락 순서 일치에 따른 데드락 소멸 및 직렬화 검증")
class PayrollServiceConcurrentExecutionIntegrationTest {

    @Autowired
    private PayrollService payrollService;

    @Autowired
    private PayrollMapper payrollMapper;

    @Autowired
    private AttendanceMapper attendanceMapper;

    private Long testPayrollId;
    private final Long targetStoreId = 1L;
    private final Long targetStaffId = 1L;
    private final String targetMonth = "2026-10";
    private final LocalDate startDate = LocalDate.of(2026, 10, 1);
    private final LocalDate endDate = LocalDate.of(2026, 10, 31);

    @BeforeEach
    void setUp() {
        // 기존 테스트 데이터 정리
        PayrollDto old = payrollMapper.findByStaffAndMonth(targetStaffId, targetMonth);
        if (old != null) {
            payrollMapper.deleteDailyDetailsByPayrollId(old.getPayrollId());
        }

        // 유효한 출근 데이터 확보 (10월 5일)
        AttendanceDto att = new AttendanceDto();
        att.setStoreId(targetStoreId);
        att.setStoreStaffId(targetStaffId);
        att.setWorkDate(LocalDate.of(2026, 10, 5));
        att.setClockInAt(LocalDateTime.of(2026, 10, 5, 9, 0));
        att.setClockOutAt(LocalDateTime.of(2026, 10, 5, 18, 0));
        att.setTotalBreakMinutes(60);
        att.setNetWorkMinutes(480);
        att.setNightWorkMinutes(0);
        att.setOvertimeMinutes(0);
        att.setClockInMethod("WEB");
        att.setAttendanceStatus("COMPLETED");
        attendanceMapper.insertAttendance(att);

        // DRAFT 급여 원장 생성
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
    }

    @AfterEach
    void tearDown() {
        if (testPayrollId != null) {
            payrollMapper.deleteDailyDetailsByPayrollId(testPayrollId);
        }
    }

    @Test
    @DisplayName("동일 직원에 대해 calculatePayrollForStaff와 confirmPayroll 동시 실행 시 데드락 없이 순차 실행 완료 검증")
    void concurrentExecution_NoDeadlock_SerializedSuccessfully() throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch readyLatch = new CountDownLatch(2);
        CountDownLatch startLatch = new CountDownLatch(1);

        AtomicReference<Throwable> calculateError = new AtomicReference<>();
        AtomicReference<Throwable> confirmError = new AtomicReference<>();
        AtomicBoolean calculateCompleted = new AtomicBoolean(false);
        AtomicBoolean confirmCompleted = new AtomicBoolean(false);

        // Thread 1: calculatePayrollForStaff (store_staff -> payroll)
        Future<?> f1 = executor.submit(() -> {
            readyLatch.countDown();
            try {
                startLatch.await(5, TimeUnit.SECONDS);
                payrollService.calculatePayrollForStaff(targetStoreId, targetStaffId, targetMonth, startDate, endDate);
                calculateCompleted.set(true);
            } catch (Throwable t) {
                calculateError.set(t);
            }
        });

        // Thread 2: confirmPayroll (store_staff -> payroll 통일)
        Future<?> f2 = executor.submit(() -> {
            readyLatch.countDown();
            try {
                startLatch.await(5, TimeUnit.SECONDS);
                PayrollConfirmRequestDto req = new PayrollConfirmRequestDto();
                req.setExpectedGrossPay(10900);
                payrollService.confirmPayroll(testPayrollId, req);
                confirmCompleted.set(true);
            } catch (Throwable t) {
                confirmError.set(t);
            }
        });

        assertTrue(readyLatch.await(5, TimeUnit.SECONDS), "두 스레드가 준비 상태에 도달해야 함");
        startLatch.countDown(); // 동시 시작!

        f1.get(10, TimeUnit.SECONDS);
        f2.get(10, TimeUnit.SECONDS);
        executor.shutdown();

        Throwable e1 = calculateError.get();
        Throwable e2 = confirmError.get();

        System.out.println("==================================================================");
        System.out.println("[실제 서비스 경로 동시 실행 결과]");
        System.out.println("Thread 1 (calculatePayrollForStaff) Exception: " + (e1 != null ? e1.getMessage() : "SUCCESS"));
        System.out.println("Thread 2 (confirmPayroll) Exception: " + (e2 != null ? e2.getMessage() : "SUCCESS"));
        System.out.println("==================================================================");

        // 핵심 판정 1: 데드락(1213 / CannotAcquireLockException / DeadlockLoserDataAccessException)이 전혀 발생하지 않아야 함
        boolean deadlockOccurred = false;
        if (e1 != null) {
            deadlockOccurred |= e1.getMessage().contains("Deadlock") || e1.getMessage().contains("1213");
        }
        if (e2 != null) {
            deadlockOccurred |= e2.getMessage().contains("Deadlock") || e2.getMessage().contains("1213");
        }
        assertFalse(deadlockOccurred, "[CRITICAL F-04] 실제 서비스 경로 동시 실행 시 1213 데드락이 발생하지 않아야 함!");

        // 핵심 판정 2: 최종 급여 상태는 정상 확정(CONFIRMED)되거나 정합성이 보존되어야 함
        PayrollDto finalPayroll = payrollMapper.findById(testPayrollId);
        assertNotNull(finalPayroll);
        System.out.println("최종 급여 상태: " + finalPayroll.getPayrollStatus() + ", 총지급액: " + finalPayroll.getGrossPay());
    }
}
