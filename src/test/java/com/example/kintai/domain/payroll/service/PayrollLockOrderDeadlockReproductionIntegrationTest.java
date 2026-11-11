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
@DisplayName("F-04: 락 순서 역전에 따른 실제 MySQL InnoDB 데드락(1213 / 40001) 재현 및 검증")
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
    @DisplayName("독립 JDBC 커넥션 2개에서 상호 역순 락 획득 시 MySQL 1213 데드락 감지 및 40001 SQLState 실측")
    void reproduceDeadlock_RawJdbcConnections() throws Exception {
        CountDownLatch conn1AcquiredStaff = new CountDownLatch(1);
        CountDownLatch conn2AcquiredPayroll = new CountDownLatch(1);
        CountDownLatch proceedToSecondLock = new CountDownLatch(1);

        AtomicReference<SQLException> conn1Exception = new AtomicReference<>();
        AtomicReference<SQLException> conn2Exception = new AtomicReference<>();

        ExecutorService executor = Executors.newFixedThreadPool(2);

        // Connection 1: calculatePayrollForStaff 경로 (store_staff -> payroll)
        Future<?> f1 = executor.submit(() -> {
            try (Connection c1 = dataSource.getConnection()) {
                c1.setAutoCommit(false);
                c1.setTransactionIsolation(Connection.TRANSACTION_READ_COMMITTED);

                // Step 1: store_staff 행 락
                try (PreparedStatement ps = c1.prepareStatement("SELECT store_staff_id FROM store_staff WHERE store_staff_id = ? FOR UPDATE")) {
                    ps.setLong(1, targetStaffId);
                    ps.executeQuery();
                }
                System.out.println("[Conn-1] Step 1: store_staff 행 락 획득 완료");
                conn1AcquiredStaff.countDown();

                // Conn-2가 payroll 락을 획득할 때까지 대기
                conn2AcquiredPayroll.await(5, TimeUnit.SECONDS);
                proceedToSecondLock.await(5, TimeUnit.SECONDS);
                Thread.sleep(100);

                // Step 2: payroll 행 락 요청 (Conn-2가 점유 중)
                System.out.println("[Conn-1] Step 2: payroll 행 락 요청 시도");
                try (PreparedStatement ps2 = c1.prepareStatement("SELECT payroll_id FROM payroll WHERE payroll_id = ? FOR UPDATE")) {
                    ps2.setLong(1, testPayrollId);
                    ps2.executeQuery();
                }

                System.out.println("[Conn-1] Step 2 성공 (생존자 트랜잭션)");
                c1.commit();
            } catch (SQLException e) {
                System.out.println("[Conn-1] SQLException 포착: ErrorCode=" + e.getErrorCode() + ", SQLState=" + e.getSQLState() + ", Msg=" + e.getMessage());
                conn1Exception.set(e);
            } catch (Exception e) {
                e.printStackTrace();
            }
        });

        // Connection 2: confirmPayroll 경로 (payroll -> store_staff)
        Future<?> f2 = executor.submit(() -> {
            try (Connection c2 = dataSource.getConnection()) {
                c2.setAutoCommit(false);
                c2.setTransactionIsolation(Connection.TRANSACTION_READ_COMMITTED);

                // Step 1: payroll 행 락
                try (PreparedStatement ps = c2.prepareStatement("SELECT payroll_id FROM payroll WHERE payroll_id = ? FOR UPDATE")) {
                    ps.setLong(1, testPayrollId);
                    ps.executeQuery();
                }
                System.out.println("[Conn-2] Step 1: payroll 행 락 획득 완료");
                conn2AcquiredPayroll.countDown();

                // Conn-1이 store_staff 락을 획득할 때까지 대기
                conn1AcquiredStaff.await(5, TimeUnit.SECONDS);
                proceedToSecondLock.countDown();
                Thread.sleep(100);

                // Step 2: store_staff 행 락 요청 (Conn-1이 점유 중 -> 상호 대기 데드락!)
                System.out.println("[Conn-2] Step 2: store_staff 행 락 요청 시도 (데드락 유발)");
                try (PreparedStatement ps2 = c2.prepareStatement("SELECT store_staff_id FROM store_staff WHERE store_staff_id = ? FOR UPDATE")) {
                    ps2.setLong(1, targetStaffId);
                    ps2.executeQuery();
                }

                System.out.println("[Conn-2] Step 2 성공 (생존자 트랜잭션)");
                c2.commit();
            } catch (SQLException e) {
                System.out.println("[Conn-2] SQLException 포착: ErrorCode=" + e.getErrorCode() + ", SQLState=" + e.getSQLState() + ", Msg=" + e.getMessage());
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
        System.out.println("[F-04 데드락 정밀 실측 결과]");
        System.out.println("Conn-1 Exception: " + (ex1 != null ? "ErrorCode=" + ex1.getErrorCode() + ", SQLState=" + ex1.getSQLState() + " (" + ex1.getMessage() + ")" : "SURVIVED & COMMITTED"));
        System.out.println("Conn-2 Exception: " + (ex2 != null ? "ErrorCode=" + ex2.getErrorCode() + ", SQLState=" + ex2.getSQLState() + " (" + ex2.getMessage() + ")" : "SURVIVED & COMMITTED"));
        System.out.println("==================================================================");

        boolean deadlockDetected = (ex1 != null && ex1.getErrorCode() == 1213) || (ex2 != null && ex2.getErrorCode() == 1213);
        boolean sqlState40001 = (ex1 != null && "40001".equals(ex1.getSQLState())) || (ex2 != null && "40001".equals(ex2.getSQLState()));

        assertTrue(deadlockDetected, "[CRITICAL F-04] MySQL 에러 코드 1213 (Deadlock found when trying to get lock)이 실측되어야 함!");
        assertTrue(sqlState40001, "[CRITICAL F-04] 표준 SQLState 40001 (Serialization Failure)이 실측되어야 함!");
        assertTrue((ex1 == null && ex2 != null) || (ex1 != null && ex2 == null), "[CRITICAL F-04] 정확히 한쪽만 희생자로 롤백되고 다른 한쪽은 정상 커밋되어야 함");
    }
}
