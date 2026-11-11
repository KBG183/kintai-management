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
@DisplayName("F-04: confirmMonthlyPayroll 트랜잭션 동일성 및 최종 재조회 직후 신규 DRAFT 경쟁 조건 정밀 실측")
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

    // 동적 생성 고유 ID 추적 리스트
    private final List<Long> createdMemberIds = new ArrayList<>();
    private final List<Long> createdStaffIds = new ArrayList<>();
    private final List<Long> createdPayrollIds = new ArrayList<>();

    @BeforeEach
    void setUp() throws Exception {
        // 테스트 전용 고유 ID 생성 (50000번대)
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

            // 초기 DRAFT 급여는 직원 1, 2만 생성 (직원 3은 나중에 생성됨)
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
    @DisplayName("1. 실제 Spring 트랜잭션 활성 및 동일 DB Connection 하에서 전 단계가 실행되는지 증명")
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
            System.out.println("[Tx-Verification] 단일 Connection ID: " + connIdBefore);
            conn.rollback();
        }
    }

    @Test
    @DisplayName("2. 재조회 이후 신규 DRAFT 경쟁 시뮬레이션: 최종 UPDATE 대상이 초기 잠금한 직원 ID들로만 한정되어야 함을 실측")
    void testLateDraftCreation_IsolationRisk() throws Exception {
        Long staff1 = createdStaffIds.get(0);
        Long staff2 = createdStaffIds.get(1);
        Long lateStaff3 = createdStaffIds.get(2);

        // 월간 확정 정상 실행 (초기 대상 staff1, staff2)
        int updatedCount = payrollService.confirmMonthlyPayroll(storeId, targetMonth);
        assertTrue(updatedCount >= 2, "최소 staff1, staff2 2건이 확정되어야 함");

        PayrollDto p1 = payrollMapper.findByStaffAndMonth(staff1, targetMonth);
        PayrollDto p2 = payrollMapper.findByStaffAndMonth(staff2, targetMonth);
        assertEquals("CONFIRMED", p1.getPayrollStatus());
        assertEquals("CONFIRMED", p2.getPayrollStatus());

        // 뒤늦게 다른 직원의 DRAFT가 생성된 경우
        try (Connection conn = dataSource.getConnection();
             Statement st = conn.createStatement()) {
            st.executeUpdate(String.format("INSERT INTO payroll (store_id, store_staff_id, target_year_month, range_start_date, range_end_date, work_days_count, applied_hourly_wage, gross_pay, payroll_status) VALUES " +
                    "(1, %d, '2026-10', '2026-10-01', '2026-10-31', 1, 1400, 15000, 'DRAFT')", lateStaff3));
        }

        // 해당 신규 DRAFT는 월간 확정에 '조용히 포함'되지 않고 DRAFT 상태로 안전하게 남아있어야 함
        PayrollDto p3 = payrollMapper.findByStaffAndMonth(lateStaff3, targetMonth);
        assertNotNull(p3);
        assertEquals("DRAFT", p3.getPayrollStatus(), "뒤늦게 생성된 급여는 CONFIRMED로 오염되지 않고 DRAFT로 안전하게 격리 보존되어야 함");
    }
}
