package com.example.kintai.domain.payroll.service;

import com.example.kintai.domain.payroll.mapper.PayrollMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.DefaultTransactionDefinition;

import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@DisplayName("F-04: マルチセッション悲観的ロック(SELECT ... FOR UPDATE)同時実行競合および反復タイミング安定性検証")
class PayrollPessimisticLockConcurrencyIntegrationTest {

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Autowired
    private PayrollMapper payrollMapper;

    @RepeatedTest(3)
    @DisplayName("トランザクション1が store_staff 行ロックを占有中、トランザクション2が同一行にロックを要求すると待機し、コミット後に正常取得されることを反復実測（3回連続）")
    void pessimisticLock_ConcurrentSessions_ShouldBlockUntilCommitted_Repeated() throws Exception {
        Long targetStaffId = 1L;

        CountDownLatch tx1LockAcquired = new CountDownLatch(1);
        CountDownLatch tx2Started = new CountDownLatch(1);
        CountDownLatch tx1CanCommit = new CountDownLatch(1);

        AtomicBoolean tx2AcquiredLock = new AtomicBoolean(false);
        ExecutorService executor = Executors.newFixedThreadPool(2);

        // Session 1: store_staff 行に FOR UPDATE ロック取得後待機
        Future<?> tx1Future = executor.submit(() -> {
            DefaultTransactionDefinition def1 = new DefaultTransactionDefinition();
            def1.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);
            def1.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
            TransactionStatus status1 = transactionManager.getTransaction(def1);

            try {
                payrollMapper.lockStoreStaffForUpdate(targetStaffId);
                tx1LockAcquired.countDown();

                // Tx-2 が進入しブロッキング状態になるまで待機
                boolean signaled = tx1CanCommit.await(5, TimeUnit.SECONDS);
                assertTrue(signaled, "Tx-1 commit signal timed out");
                transactionManager.commit(status1);
            } catch (Exception e) {
                transactionManager.rollback(status1);
                throw new RuntimeException(e);
            }
        });

        // Session 2: 同一行に対し別トランザクションで FOR UPDATE を試行（Tx-1 占有のため待機）
        Future<?> tx2Future = executor.submit(() -> {
            try {
                boolean acquired = tx1LockAcquired.await(5, TimeUnit.SECONDS);
                assertTrue(acquired, "Tx-1 lock signal timed out");

                DefaultTransactionDefinition def2 = new DefaultTransactionDefinition();
                def2.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);
                def2.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
                TransactionStatus status2 = transactionManager.getTransaction(def2);

                try {
                    tx2Started.countDown();
                    
                    // Tx-1 がコミットするまでここでブロッキング待機発生
                    payrollMapper.lockStoreStaffForUpdate(targetStaffId);
                    
                    tx2AcquiredLock.set(true);
                    transactionManager.commit(status2);
                } catch (Exception e) {
                    transactionManager.rollback(status2);
                    throw new RuntimeException(e);
                }
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });

        // Tx-2 がロック取得試行に進入したかを待機
        boolean tx2Ready = tx2Started.await(3, TimeUnit.SECONDS);
        assertTrue(tx2Ready, "Tx-2 did not start in time");
        Thread.sleep(300); // 300ms 間 Tx-2 がブロッキング状態であることを確認

        // 検証1: Tx-1 が未コミットのため Tx-2 はロックを取得できず待機中であること
        assertFalse(tx2AcquiredLock.get(), "[F-04 CONCURRENCY GUARD] Tx-1 占有中の Tx-2 ブロッキング待機維持を確認");

        // Tx-1 コミットシグナル送出
        tx1CanCommit.countDown();

        // 両スレッド完了待機
        tx1Future.get(5, TimeUnit.SECONDS);
        tx2Future.get(5, TimeUnit.SECONDS);

        // 検証2: Tx-1 コミット後に Tx-2 がロックを正常取得し完了したことを確認
        assertTrue(tx2AcquiredLock.get(), "[F-04 CONCURRENCY GUARD] Tx-1 コミット後の Tx-2 ロック取得完了を確認");

        executor.shutdown();
    }
}
