# Day 10 Comprehensive Audit Bundle: Attendance Concurrency & Traceability

## 1. Audit Scope & Context
This audit package provides the exact code, SQL mapping, test implementation, and execution evidence for Day 10: Attendance Punch Concurrency (`CLOCK_IN` and `CLOCK_OUT`).
The auditor must evaluate whether:
1. Concurrency isolation and pessimistic locking (`lockStoreStaffForUpdate`) prevent duplicate attendance rows and guarantee exactly one success and one business rejection under simultaneous requests.
2. The MyBatis XML mapper and method signatures are formally sound (specifically `lockStoreStaffForUpdate` returning `java.lang.Long`).
3. Test fixture management, determinism, timeout, transaction scope, and assertion logic are properly isolated.
4. Requirements traceability is correctly mapped to SRS 1.3 / F04 and distinguished from payroll (F04 R-01~R-07).
5. The environmental constraint (MySQL 26.7.0 vs Docker/MySQL 8.4 LTS) is honestly disclosed without unexecuted claims.

## 2. Governing Files & Rules
- Governing rules: `C:\dev\kintai\AGENTS.md`
- SRS: `C:\dev\kintai\kintai_requirements_specification_revised.md` (SRS 1.3, Section 8.1 F04)
- Traceability process: `C:\dev\kintai\docs\requirements_tracking_process.md`
- RTM ledger: `C:\dev\kintai\docs\rtm_ledger.md`
- Working draft: `C:\dev\kintai\docs\working\day10_traceability_working.md`

## 3. Implementation Under Review

### A. AttendanceService.java (`punch` method)
```java
@Transactional
public AttendanceDto punch(AttendancePunchDto punchDto) {
    Long storeStaffId = punchDto.getStoreStaffId();
    String punchType = punchDto.getPunchType();
    LocalDateTime now = LocalDateTime.now();
    LocalDate today = now.toLocalDate();

    // Pessimistic lock on store_staff row to serialize concurrent punches
    attendanceMapper.lockStoreStaffForUpdate(storeStaffId);

    AttendanceDto active = attendanceMapper.findActiveAttendance(storeStaffId);

    switch (punchType) {
        case "CLOCK_IN":
            return handleClockIn(punchDto, today, now, active);
        case "BREAK_START":
            return handleBreakStart(now, active);
        case "BREAK_END":
            return handleBreakEnd(now, active);
        case "CLOCK_OUT":
            return handleClockOut(now, active);
        default:
            throw new IllegalArgumentException("無効な打刻タイプです: " + punchType);
    }
}
```

### B. AttendanceMapper.xml (`lockStoreStaffForUpdate`)
```xml
<select id="lockStoreStaffForUpdate" resultType="java.lang.Long">
    SELECT store_staff_id
    FROM store_staff
    WHERE store_staff_id = #{storeStaffId}
    FOR UPDATE
</select>
```

### C. AttendanceServiceConcurrencyIntegrationTest.java
```java
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
```

## 4. Execution Evidence
- Target command: `./gradlew test --tests com.example.kintai.domain.attendance.service.AttendanceServiceConcurrencyIntegrationTest`
- Exit Code: 0 (BUILD SUCCESSFUL in 19s)
- JUnit XML: `build/test-results/test/TEST-com.example.kintai.domain.attendance.service.AttendanceServiceConcurrencyIntegrationTest.xml`
- Result summary: tests=2, failures=0, errors=0, skipped=0
- Entire regression suite: `./gradlew test` -> `BUILD SUCCESSFUL in 29s` (Exit Code 0)

## 5. Audit Questions & Protocol
Please provide an independent audit report strictly following the standard Markdown table format:
`| Finding ID | Audit Item | Severity (P0/P1/P2/Info) | Verdict (PASS / WARN / FAIL / N/A) | Code & Test Evidence | Detailed Analysis & Recommendation |`

Address:
1. Does `attendanceMapper.lockStoreStaffForUpdate` serialize concurrent punches effectively?
2. Is the `resultType="java.lang.Long"` addition in `AttendanceMapper.xml` complete and correct?
3. Does the test deterministically catch duplicate `CLOCK_IN` and `CLOCK_OUT`?
4. Are teardown and cleanup reliable against test pollution?
5. Is the separation between Day 10 Attendance Concurrency (R-08, R-09) and Day 9 Payroll Concurrency (R-01~R-07) clear and compliant with the RTM ledger?
