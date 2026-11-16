package com.example.kintai.domain.payroll.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.concurrent.*;

import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
@DisplayName("F-04: performance_schema.data_locks 実測検証")
class PerformanceSchemaDataLocksIntegrationTest {

    @Autowired
    private DataSource dataSource;

    @Test
    @DisplayName("独立コネクションにおける lockStorePayrollsForUpdate 実行時の performance_schema.data_locks ロックモードおよびインデックス実測")
    void observeDataLocks() throws Exception {
        String targetSchema = currentDatabaseName();
        CountDownLatch lockAcquired = new CountDownLatch(1);
        CountDownLatch releaseLock = new CountDownLatch(1);

        ExecutorService executor = Executors.newFixedThreadPool(2);

        // Session 1: lockStorePayrollsForUpdate 実行後ロック保持
        Future<?> f1 = executor.submit(() -> {
            try (Connection c1 = dataSource.getConnection()) {
                c1.setAutoCommit(false);
                c1.setTransactionIsolation(Connection.TRANSACTION_READ_COMMITTED);

                try (Statement st = c1.createStatement()) {
                    st.executeQuery("SELECT payroll_id, store_id, store_staff_id, target_year_month FROM payroll WHERE store_id = 1 AND target_year_month = '2026-10' ORDER BY store_staff_id ASC, payroll_id ASC FOR UPDATE");
                }
                System.out.println("[Session 1] lockStorePayrollsForUpdate FOR UPDATE ロック取得完了");
                lockAcquired.countDown();

                releaseLock.await(5, TimeUnit.SECONDS);
                c1.rollback();
            } catch (Exception e) {
                e.printStackTrace();
            }
        });

        // Session 2: performance_schema.data_locks 検索
        Future<?> f2 = executor.submit(() -> {
            try {
                lockAcquired.await(5, TimeUnit.SECONDS);
                try (Connection c2 = dataSource.getConnection();
                     PreparedStatement st = c2.prepareStatement(
                             "SELECT ENGINE, ENGINE_TRANSACTION_ID, OBJECT_NAME, INDEX_NAME, " +
                                     "LOCK_TYPE, LOCK_MODE, LOCK_STATUS, LOCK_DATA " +
                                     "FROM performance_schema.data_locks WHERE OBJECT_SCHEMA = ?")) {
                    st.setString(1, targetSchema);
                    try (ResultSet rs = st.executeQuery()) {

                    System.out.println("==================== [performance_schema.data_locks 実測] ====================");
                    int count = 0;
                    while (rs.next()) {
                        count++;
                        System.out.printf("Table: %s | Index: %s | Type: %s | Mode: %s | Status: %s | Data: %s%n",
                                rs.getString("OBJECT_NAME"),
                                rs.getString("INDEX_NAME"),
                                rs.getString("LOCK_TYPE"),
                                rs.getString("LOCK_MODE"),
                                rs.getString("LOCK_STATUS"),
                                rs.getString("LOCK_DATA")
                        );
                    }
                    System.out.println("===============================================================================");
                    assertTrue(count > 0, "実際のアクティブなデータロックが performance_schema で検知されること");
                    }
                } finally {
                    releaseLock.countDown();
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        });

        f1.get(10, TimeUnit.SECONDS);
        f2.get(10, TimeUnit.SECONDS);
        executor.shutdown();
    }

    private String currentDatabaseName() throws SQLException {
        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery("SELECT DATABASE()")) {
            if (!resultSet.next() || resultSet.getString(1) == null) {
                throw new SQLException("The test datasource has no selected database.");
            }
            return resultSet.getString(1);
        }
    }
}
