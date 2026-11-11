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
@DisplayName("F-04: 멀티 세션 비관적 락(SELECT ... FOR UPDATE) 동시성 경합 및 반복 타이밍 안정성 검증")
class PayrollPessimisticLockConcurrencyIntegrationTest {

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Autowired
    private PayrollMapper payrollMapper;

    @RepeatedTest(3)
    @DisplayName("트랜잭션 1이 store_staff 행 락을 점유 중일 때, 트랜잭션 2가 동일 행에 락을 요청하면 대기하며 커밋 후 정상 획득되는지 반복 실측 (3회 연속)")
    void pessimisticLock_ConcurrentSessions_ShouldBlockUntilCommitted_Repeated() throws Exception {
        Long targetStaffId = 1L;

        CountDownLatch tx1LockAcquired = new CountDownLatch(1);
        CountDownLatch tx2Started = new CountDownLatch(1);
        CountDownLatch tx1CanCommit = new CountDownLatch(1);

        AtomicBoolean tx2AcquiredLock = new AtomicBoolean(false);
        ExecutorService executor = Executors.newFixedThreadPool(2);

        // Session 1: store_staff 행에 FOR UPDATE 락 획득 후 대기
        Future<?> tx1Future = executor.submit(() -> {
            DefaultTransactionDefinition def1 = new DefaultTransactionDefinition();
            def1.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);
            def1.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
            TransactionStatus status1 = transactionManager.getTransaction(def1);

            try {
                payrollMapper.lockStoreStaffForUpdate(targetStaffId);
                tx1LockAcquired.countDown();

                // Tx-2가 진입하여 블로킹 상태가 될 때까지 대기
                boolean signaled = tx1CanCommit.await(5, TimeUnit.SECONDS);
                assertTrue(signaled, "Tx-1 commit signal timed out");
                transactionManager.commit(status1);
            } catch (Exception e) {
                transactionManager.rollback(status1);
                throw new RuntimeException(e);
            }
        });

        // Session 2: 동일 행에 대해 별도 트랜잭션으로 FOR UPDATE 시도 (Tx-1 점유로 인해 블로킹 대기해야 함)
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
                    
                    // Tx-1이 커밋하기 전까지 여기서 블로킹 대기 발생
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

        // Tx-2가 락 획득 시도에 진입했는지 대기
        boolean tx2Ready = tx2Started.await(3, TimeUnit.SECONDS);
        assertTrue(tx2Ready, "Tx-2 did not start in time");
        Thread.sleep(300); // 300ms 동안 Tx-2가 블로킹 상태인지 확인

        // 검증 1: Tx-1이 아직 커밋하지 않았으므로 Tx-2는 락을 획득하지 못하고 대기 중이어야 함
        assertFalse(tx2AcquiredLock.get(), "[F-04 CONCURRENCY GUARD] Tx-1 점유 중 Tx-2 블로킹 대기 유지 확인");

        // Tx-1 커밋 신호 방출
        tx1CanCommit.countDown();

        // 양측 스레드 완료 대기
        tx1Future.get(5, TimeUnit.SECONDS);
        tx2Future.get(5, TimeUnit.SECONDS);

        // 검증 2: Tx-1 커밋 후 Tx-2가 락을 정상 획득하고 완료되었는지 확인
        assertTrue(tx2AcquiredLock.get(), "[F-04 CONCURRENCY GUARD] Tx-1 커밋 후 Tx-2 락 획득 완료 확인");

        executor.shutdown();
    }
}
