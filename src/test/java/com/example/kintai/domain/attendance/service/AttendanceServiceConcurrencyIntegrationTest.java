package com.example.kintai.domain.attendance.service;

import com.example.kintai.database.IsolatedDatabaseGuard;
import com.example.kintai.domain.attendance.dto.AttendanceDto;
import com.example.kintai.domain.attendance.dto.AttendancePunchDto;
import com.example.kintai.domain.attendance.mapper.AttendanceMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

@SpringBootTest
@DisplayName("Day 10: AttendanceService 同時出退勤打刻の排他検証")
class AttendanceServiceConcurrencyIntegrationTest {

    private static final long TARGET_STORE_ID = 1L;
    private static final long TARGET_STAFF_ID = 2L;

    @Autowired
    private AttendanceService attendanceService;

    @Autowired
    private AttendanceMapper attendanceMapper;

    @Autowired
    private DataSource dataSource;

    private final Queue<Long> createdAttendanceIds = new ConcurrentLinkedQueue<>();

    @BeforeEach
    void setUp() throws Exception {
        IsolatedDatabaseGuard.requireAllowedDatabase(dataSource, "kintai_(day10_test|full_test)_[0-9a-f]{12}");
        deleteActiveAttendances();
    }

    @AfterEach
    void tearDown() throws Exception {
        for (Long attendanceId : createdAttendanceIds) {
            try (Connection connection = dataSource.getConnection();
                 PreparedStatement statement = connection.prepareStatement(
                         "DELETE FROM attendance WHERE attendance_id = ?")) {
                statement.setLong(1, attendanceId);
                statement.executeUpdate();
            }
        }
        createdAttendanceIds.clear();
    }

    @Test
    @DisplayName("同時CLOCK_INリクエストは1件のみ成功し重複勤務レコードを作成しない")
    void concurrentClockIn_ExactlyOneSucceeds() throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        AtomicInteger successCount = new AtomicInteger();
        AtomicInteger rejectedCount = new AtomicInteger();
        AtomicReference<Throwable> unexpectedError = new AtomicReference<>();
        List<Future<?>> futures = new ArrayList<>();

        for (int i = 0; i < 2; i++) {
            futures.add(executor.submit(() -> {
                ready.countDown();
                try {
                    start.await(5, TimeUnit.SECONDS);
                    AttendanceDto result = attendanceService.punch(punch("CLOCK_IN"));
                    createdAttendanceIds.add(result.getAttendanceId());
                    successCount.incrementAndGet();
                } catch (IllegalStateException expected) {
                    rejectedCount.incrementAndGet();
                } catch (Throwable unexpected) {
                    unexpectedError.compareAndSet(null, unexpected);
                }
            }));
        }

        assertEquals(true, ready.await(5, TimeUnit.SECONDS));
        start.countDown();
        for (Future<?> future : futures) {
            future.get(10, TimeUnit.SECONDS);
        }
        executor.shutdownNow();

        assertNull(unexpectedError.get(), "予期せぬ例外が発生してはならない");
        assertEquals(1, successCount.get(), "同時CLOCK_INは正確に1件のみ成功しなければならない");
        assertEquals(1, rejectedCount.get(), "残りのリクエストは重複打刻として拒否されなければならない");
        assertNotNull(attendanceMapper.findActiveAttendance(TARGET_STAFF_ID));
    }

    @Test
    @DisplayName("同時CLOCK_OUTリクエストは1件のみ成功し完了状態を保持する")
    void concurrentClockOut_ExactlyOneSucceeds() throws Exception {
        AttendanceDto working = createWorkingAttendance();

        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        AtomicInteger successCount = new AtomicInteger();
        AtomicInteger rejectedCount = new AtomicInteger();
        AtomicReference<Throwable> unexpectedError = new AtomicReference<>();
        List<Future<?>> futures = new ArrayList<>();

        for (int i = 0; i < 2; i++) {
            futures.add(executor.submit(() -> {
                ready.countDown();
                try {
                    start.await(5, TimeUnit.SECONDS);
                    attendanceService.punch(punch("CLOCK_OUT"));
                    successCount.incrementAndGet();
                } catch (IllegalStateException expected) {
                    rejectedCount.incrementAndGet();
                } catch (Throwable unexpected) {
                    unexpectedError.compareAndSet(null, unexpected);
                }
            }));
        }

        assertEquals(true, ready.await(5, TimeUnit.SECONDS));
        start.countDown();
        for (Future<?> future : futures) {
            future.get(10, TimeUnit.SECONDS);
        }
        executor.shutdownNow();

        assertNull(unexpectedError.get(), "予期せぬ例外が発生してはならない");
        assertEquals(1, successCount.get(), "同時CLOCK_OUTは正確に1件のみ成功しなければならない");
        assertEquals(1, rejectedCount.get(), "残りのリクエストは既に終了した勤務として拒否されなければならない");

        AttendanceDto completed = attendanceMapper.findById(working.getAttendanceId());
        assertNotNull(completed);
        assertEquals("COMPLETED", completed.getAttendanceStatus());
    }

    private AttendancePunchDto punch(String punchType) {
        AttendancePunchDto request = new AttendancePunchDto();
        request.setStoreId(TARGET_STORE_ID);
        request.setStoreStaffId(TARGET_STAFF_ID);
        request.setPunchType(punchType);
        return request;
    }

    private AttendanceDto createWorkingAttendance() {
        AttendanceDto attendance = new AttendanceDto();
        attendance.setStoreId(TARGET_STORE_ID);
        attendance.setStoreStaffId(TARGET_STAFF_ID);
        attendance.setWorkDate(LocalDate.now());
        attendance.setClockInAt(LocalDateTime.now().minusHours(1));
        attendance.setTotalBreakMinutes(0);
        attendance.setNetWorkMinutes(0);
        attendance.setNightWorkMinutes(0);
        attendance.setOvertimeMinutes(0);
        attendance.setClockInMethod("BUTTON");
        attendance.setAttendanceStatus("WORKING");
        attendanceMapper.insertAttendance(attendance);
        createdAttendanceIds.add(attendance.getAttendanceId());
        return attendance;
    }

    private void deleteActiveAttendances() throws Exception {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "DELETE FROM attendance WHERE store_staff_id = ? " +
                             "AND attendance_status IN ('WORKING', 'ON_BREAK')")) {
            statement.setLong(1, TARGET_STAFF_ID);
            statement.executeUpdate();
        }
    }
}
