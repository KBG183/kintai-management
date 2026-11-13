package com.example.kintai.domain.attendance.service;

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
@DisplayName("Day 10: AttendanceService 동시 출퇴근 타각 검증")
class AttendanceServiceConcurrencyIntegrationTest {

    private static final long TARGET_STORE_ID = 1L;
    private static final long TARGET_STAFF_ID = 2L;

    @Autowired
    private AttendanceService attendanceService;

    @Autowired
    private AttendanceMapper attendanceMapper;

    @Autowired
    private DataSource dataSource;

    private final List<Long> createdAttendanceIds = new ArrayList<>();

    @BeforeEach
    void setUp() throws Exception {
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
    @DisplayName("동시 CLOCK_IN 요청은 하나만 성공하고 중복 근무 행을 만들지 않는다")
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
                    synchronized (createdAttendanceIds) {
                        createdAttendanceIds.add(result.getAttendanceId());
                    }
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

        assertNull(unexpectedError.get(), "예상하지 않은 예외가 발생하면 안 됩니다");
        assertEquals(1, successCount.get(), "동시 CLOCK_IN은 정확히 하나만 성공해야 합니다");
        assertEquals(1, rejectedCount.get(), "나머지 요청은 중복 타각으로 거부되어야 합니다");
        assertNotNull(attendanceMapper.findActiveAttendance(TARGET_STAFF_ID));
    }

    @Test
    @DisplayName("동시 CLOCK_OUT 요청은 하나만 성공하고 완료 상태를 보존한다")
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

        assertNull(unexpectedError.get(), "예상하지 않은 예외가 발생하면 안 됩니다");
        assertEquals(1, successCount.get(), "동시 CLOCK_OUT은 정확히 하나만 성공해야 합니다");
        assertEquals(1, rejectedCount.get(), "나머지 요청은 이미 종료된 근무로 거부되어야 합니다");

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
