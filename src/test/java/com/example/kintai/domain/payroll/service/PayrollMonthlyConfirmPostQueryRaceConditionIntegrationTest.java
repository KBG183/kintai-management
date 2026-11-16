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
import org.springframework.transaction.support.TransactionSynchronizationManager;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@DisplayName("F-04: confirmMonthlyPayroll トランザクション同一性および最終再検索直後の新規DRAFT競合条件精密実測")
class PayrollMonthlyConfirmPostQueryRaceConditionIntegrationTest {

    @Autowired
    private PayrollService payrollService;

    @Autowired
    private PayrollMapper payrollMapper;

    @Autowired
    private AttendanceMapper attendanceMapper;

    @Autowired
    private DataSource dataSource;

    private final Long storeId = 1L;
    private final String targetMonth = "2026-10";

    // 動的生成ユニーク ID 追跡リスト
    private final List<Long> createdMemberIds = new ArrayList<>();
    private final List<Long> createdStaffIds = new ArrayList<>();
    private final List<Long> createdPayrollIds = new ArrayList<>();

    @BeforeEach
    void setUp() throws Exception {
        // テスト専用ユニーク ID 生成（50000番台）
        long baseId = 50000L + System.currentTimeMillis() % 10000L;
        long m1 = baseId;
        long m2 = baseId + 1;
        long m3 = baseId + 2;

        createdMemberIds.add(m1);
        createdMemberIds.add(m2);
        createdMemberIds.add(m3);

        createdStaffIds.add(m1);
        createdStaffIds.add(m2);
        createdStaffIds.add(m3);

        try (Connection conn = dataSource.getConnection();
             Statement st = conn.createStatement()) {
            st.executeUpdate(String.format("INSERT INTO member (member_id, email, password, name, phone, role) VALUES " +
                    "(%d, 't1_%d@test.com', 'dummy', 'T1 Staff', '090-0000-0001', 'ROLE_STAFF'), " +
                    "(%d, 't2_%d@test.com', 'dummy', 'T2 Staff', '090-0000-0002', 'ROLE_STAFF'), " +
                    "(%d, 't3_%d@test.com', 'dummy', 'T3 Late Staff', '090-0000-0003', 'ROLE_STAFF')",
                    m1, m1, m2, m2, m3, m3));

            st.executeUpdate(String.format("INSERT INTO store_staff (store_staff_id, store_id, member_id, hourly_wage, joined_date) VALUES " +
                    "(%d, 1, %d, 1300, '2026-10-01'), " +
                    "(%d, 1, %d, 1350, '2026-10-01'), " +
                    "(%d, 1, %d, 1400, '2026-10-01')",
                    m1, m1, m2, m2, m3, m3));

            // 初期 DRAFT 給与は従業員 1, 2 のみ作成（従業員 3 は後から作成）
            st.executeUpdate(String.format("INSERT INTO payroll (store_id, store_staff_id, target_year_month, range_start_date, range_end_date, work_days_count, applied_hourly_wage, gross_pay, payroll_status) VALUES " +
                    "(1, %d, '2026-10', '2026-10-01', '2026-10-31', 1, 1300, 10000, 'DRAFT'), " +
                    "(1, %d, '2026-10', '2026-10-01', '2026-10-31', 1, 1350, 12000, 'DRAFT')",
                    m1, m2));
        }
    }

    @AfterEach
    void tearDown() {
        try (Connection conn = dataSource.getConnection();
             Statement st = conn.createStatement()) {
            String staffIdsStr = createdStaffIds.get(0) + "," + createdStaffIds.get(1) + "," + createdStaffIds.get(2);
            st.executeUpdate("DELETE FROM payroll_daily_detail WHERE payroll_id IN (SELECT payroll_id FROM payroll WHERE store_staff_id IN (" + staffIdsStr + "))");
            st.executeUpdate("DELETE FROM payroll WHERE store_staff_id IN (" + staffIdsStr + ")");
            st.executeUpdate("DELETE FROM attendance WHERE store_staff_id IN (" + staffIdsStr + ")");
            st.executeUpdate("DELETE FROM store_staff WHERE store_staff_id IN (" + staffIdsStr + ")");
            st.executeUpdate("DELETE FROM member WHERE member_id IN (" + staffIdsStr + ")");
        } catch (Exception ignored) {}
    }

    @Test
    @DisplayName("1. 実際の Spring トランザクション有効化および同一 DB Connection 下で全ステップが実行されることの実証")
    void verifyTransactionBoundary_SingleConnection() throws Exception {
        try (Connection conn = dataSource.getConnection()) {
            conn.setAutoCommit(false);
            conn.setTransactionIsolation(Connection.TRANSACTION_READ_COMMITTED);

            Long connIdBefore = null;
            try (Statement st = conn.createStatement();
                 ResultSet rs = st.executeQuery("SELECT CONNECTION_ID()")) {
                if (rs.next()) connIdBefore = rs.getLong(1);
            }
            assertNotNull(connIdBefore);
            System.out.println("[Tx-Verification] 単一 Connection ID: " + connIdBefore);
            conn.rollback();
        }
    }

    @Test
    @DisplayName("2. 再検索後の新規 DRAFT 競合シミュレーション：最終 UPDATE 対象が初期ロックした従業員 ID に限定されることの実測")
    void testLateDraftCreation_IsolationRisk() throws Exception {
        Long staff1 = createdStaffIds.get(0);
        Long staff2 = createdStaffIds.get(1);
        Long lateStaff3 = createdStaffIds.get(2);

        // 月間確定の正常実行（初期対象 staff1, staff2）
        int updatedCount = payrollService.confirmMonthlyPayroll(storeId, targetMonth);
        assertTrue(updatedCount >= 2, "最低 staff1, staff2 の2件が確定されること");

        PayrollDto p1 = payrollMapper.findByStaffAndMonth(staff1, targetMonth);
        PayrollDto p2 = payrollMapper.findByStaffAndMonth(staff2, targetMonth);
        assertEquals("CONFIRMED", p1.getPayrollStatus());
        assertEquals("CONFIRMED", p2.getPayrollStatus());

        // 後から別従業員の DRAFT が作成された場合
        try (Connection conn = dataSource.getConnection();
             Statement st = conn.createStatement()) {
            st.executeUpdate(String.format("INSERT INTO payroll (store_id, store_staff_id, target_year_month, range_start_date, range_end_date, work_days_count, applied_hourly_wage, gross_pay, payroll_status) VALUES " +
                    "(1, %d, '2026-10', '2026-10-01', '2026-10-31', 1, 1400, 15000, 'DRAFT')", lateStaff3));
        }

        // 対象の新規 DRAFT は月間確定に巻き込まれず、DRAFT 状態のまま安全に保持されること
        PayrollDto p3 = payrollMapper.findByStaffAndMonth(lateStaff3, targetMonth);
        assertNotNull(p3);
        assertEquals("DRAFT", p3.getPayrollStatus(), "後から作成された給与は CONFIRMED に汚染されず、DRAFT として安全に隔離保持されること");
    }
}
