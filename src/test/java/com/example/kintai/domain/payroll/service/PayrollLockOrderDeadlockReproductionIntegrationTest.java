package com.example.kintai.domain.payroll.service;

import com.example.kintai.domain.payroll.dto.PayrollDto;
import com.example.kintai.domain.payroll.mapper.PayrollMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DeadlockLoserDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@DisplayName("F-04: ロック順序逆転によるMySQL InnoDBデッドロック(1213 / 40001)再現検証")
class PayrollLockOrderDeadlockReproductionIntegrationTest {

    @Autowired
    private DataSource dataSource;

    @Autowired
    private PayrollMapper payrollMapper;

    private Long testPayrollId;
    private final Long targetStaffId = 1L;

    @BeforeEach
    void setUp() {
        PayrollDto draft = new PayrollDto();
        draft.setStoreId(1L);
        draft.setStoreStaffId(targetStaffId);
        draft.setTargetYearMonth("2026-10");
        draft.setRangeStartDate(LocalDate.of(2026, 10, 1));
        draft.setRangeEndDate(LocalDate.of(2026, 10, 31));
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
    @DisplayName("独立した2つのJDBCコネクションにおける相互逆順ロック取得時のMySQL 1213デッドロック検知および40001 SQLState実測")
    void reproduceDeadlock_RawJdbcConnections() throws Exception {
        CountDownLatch conn1AcquiredStaff = new CountDownLatch(1);
        CountDownLatch conn2AcquiredPayroll = new CountDownLatch(1);
        CountDownLatch proceedToSecondLock = new CountDownLatch(1);

        AtomicReference<SQLException> conn1Exception = new AtomicReference<>();
        AtomicReference<SQLException> conn2Exception = new AtomicReference<>();

        ExecutorService executor = Executors.newFixedThreadPool(2);

        // Connection 1: calculatePayrollForStaff 経路（store_staff -> payroll）
        Future<?> f1 = executor.submit(() -> {
            try (Connection c1 = dataSource.getConnection()) {
                c1.setAutoCommit(false);
                c1.setTransactionIsolation(Connection.TRANSACTION_READ_COMMITTED);

                // Step 1: store_staff 行ロック
                try (PreparedStatement ps = c1.prepareStatement("SELECT store_staff_id FROM store_staff WHERE store_staff_id = ? FOR UPDATE")) {
                    ps.setLong(1, targetStaffId);
                    ps.executeQuery();
                }
                System.out.println("[Conn-1] Step 1: store_staff 行ロック取得完了");
                conn1AcquiredStaff.countDown();

                // Conn-2 が payroll ロックを取得するまで待機
                conn2AcquiredPayroll.await(5, TimeUnit.SECONDS);
                proceedToSecondLock.await(5, TimeUnit.SECONDS);
                Thread.sleep(100);

                // Step 2: payroll 行ロック要求（Conn-2 が占有中）
                System.out.println("[Conn-1] Step 2: payroll 行ロック要求試行");
                try (PreparedStatement ps2 = c1.prepareStatement("SELECT payroll_id FROM payroll WHERE payroll_id = ? FOR UPDATE")) {
                    ps2.setLong(1, testPayrollId);
                    ps2.executeQuery();
                }

                System.out.println("[Conn-1] Step 2 成功（生存トランザクション）");
                c1.commit();
            } catch (SQLException e) {
                System.out.println("[Conn-1] SQLException 捕捉: ErrorCode=" + e.getErrorCode() + ", SQLState=" + e.getSQLState() + ", Msg=" + e.getMessage());
                conn1Exception.set(e);
            } catch (Exception e) {
                e.printStackTrace();
            }
        });

        // Connection 2: confirmPayroll 経路（payroll -> store_staff）
        Future<?> f2 = executor.submit(() -> {
            try (Connection c2 = dataSource.getConnection()) {
                c2.setAutoCommit(false);
                c2.setTransactionIsolation(Connection.TRANSACTION_READ_COMMITTED);

                // Step 1: payroll 行ロック
                try (PreparedStatement ps = c2.prepareStatement("SELECT payroll_id FROM payroll WHERE payroll_id = ? FOR UPDATE")) {
                    ps.setLong(1, testPayrollId);
                    ps.executeQuery();
                }
                System.out.println("[Conn-2] Step 1: payroll 行ロック取得完了");
                conn2AcquiredPayroll.countDown();

                // Conn-1 が store_staff ロックを取得するまで待機
                conn1AcquiredStaff.await(5, TimeUnit.SECONDS);
                proceedToSecondLock.countDown();
                Thread.sleep(100);

                // Step 2: store_staff 行ロック要求（Conn-1 が占有中 -> 相互待機デッドロック）
                System.out.println("[Conn-2] Step 2: store_staff 行ロック要求試行（デッドロック発生）");
                try (PreparedStatement ps2 = c2.prepareStatement("SELECT store_staff_id FROM store_staff WHERE store_staff_id = ? FOR UPDATE")) {
                    ps2.setLong(1, targetStaffId);
                    ps2.executeQuery();
                }

                System.out.println("[Conn-2] Step 2 成功（生存トランザクション）");
                c2.commit();
            } catch (SQLException e) {
                System.out.println("[Conn-2] SQLException 捕捉: ErrorCode=" + e.getErrorCode() + ", SQLState=" + e.getSQLState() + ", Msg=" + e.getMessage());
                conn2Exception.set(e);
            } catch (Exception e) {
                e.printStackTrace();
            }
        });

        f1.get(10, TimeUnit.SECONDS);
        f2.get(10, TimeUnit.SECONDS);
        executor.shutdown();

        SQLException ex1 = conn1Exception.get();
        SQLException ex2 = conn2Exception.get();

        System.out.println("==================================================================");
        System.out.println("[F-04 デッドロック精密実測結果]");
        System.out.println("Conn-1 Exception: " + (ex1 != null ? "ErrorCode=" + ex1.getErrorCode() + ", SQLState=" + ex1.getSQLState() + " (" + ex1.getMessage() + ")" : "SURVIVED & COMMITTED"));
        System.out.println("Conn-2 Exception: " + (ex2 != null ? "ErrorCode=" + ex2.getErrorCode() + ", SQLState=" + ex2.getSQLState() + " (" + ex2.getMessage() + ")" : "SURVIVED & COMMITTED"));
        System.out.println("==================================================================");

        boolean deadlockDetected = (ex1 != null && ex1.getErrorCode() == 1213) || (ex2 != null && ex2.getErrorCode() == 1213);
        boolean sqlState40001 = (ex1 != null && "40001".equals(ex1.getSQLState())) || (ex2 != null && "40001".equals(ex2.getSQLState()));

        assertTrue(deadlockDetected, "[CRITICAL F-04] MySQL エラーコード 1213 (Deadlock found when trying to get lock) が実測されること");
        assertTrue(sqlState40001, "[CRITICAL F-04] 標準 SQLState 40001 (Serialization Failure) が実測されること");
        assertTrue((ex1 == null && ex2 != null) || (ex1 != null && ex2 == null), "[CRITICAL F-04] 正確に一方のみが犠牲者としてロールバックされ、他方は正常コミットされること");
    }
}
