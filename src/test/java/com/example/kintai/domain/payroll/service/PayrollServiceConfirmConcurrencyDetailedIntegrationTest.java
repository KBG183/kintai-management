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
@DisplayName("F-04: confirmPayroll 同時実行競合および同時重複確定(CAS)検証")
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
    @DisplayName("同一給与に対する2つの管理者セッションによる同時重複確定試行時、正確に1件のみ成功し1件はステータス遮断されることの検証")
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
                    System.out.println("[" + Thread.currentThread().getName() + "] ConnectionID=" + connId + " 確定試行開始 at " + startMs);
                }

                PayrollConfirmRequestDto req = new PayrollConfirmRequestDto();
                // 10/5 8時間 1350円 = 基本給 10800 + 交通費 600 = 11400（計算エンジン準拠）
                req.setExpectedGrossPay(11400);
                payrollService.confirmPayroll(testPayrollId, req);

                long endMs = System.currentTimeMillis();
                System.out.println("[" + Thread.currentThread().getName() + "] 確定成功完了 at " + endMs);
                successCount.incrementAndGet();
            } catch (IllegalStateException e) {
                if (e.getMessage() != null && e.getMessage().contains("既に確定済みの給与データです")) {
                    System.out.println("[" + Thread.currentThread().getName() + "] 二重確定の安全遮断を捕捉: " + e.getMessage());
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

        assertTrue(readyLatch.await(5, TimeUnit.SECONDS), "両スレッドが準備されること");
        startLatch.countDown();

        f1.get(10, TimeUnit.SECONDS);
        f2.get(10, TimeUnit.SECONDS);
        executor.shutdown();

        assertNull(otherError.get(), "予期しないその他例外が発生しないこと: " + (otherError.get() != null ? otherError.get().getMessage() : ""));
        assertEquals(1, successCount.get(), "同時確定試行のうち正確に1件のみ成功すること");
        assertEquals(1, alreadyConfirmedCount.get(), "残りの1件はすでに確定済み（状態遷移遮断）例外が発生すること");

        PayrollDto finalPayroll = payrollMapper.findById(testPayrollId);
        assertNotNull(finalPayroll);
        assertEquals("CONFIRMED", finalPayroll.getPayrollStatus(), "最終状態は CONFIRMED であること");
    }
}
