===== src/main/java/com/example/kintai/domain/attendance/service/AttendanceService.java (20-130) =====
@Transactional(readOnly = true)
public class AttendanceService {

    private final AttendanceMapper attendanceMapper;

    public AttendanceService(AttendanceMapper attendanceMapper) {
        this.attendanceMapper = attendanceMapper;
    }

    /**
     * リアルタイム打刻処理（出勤・休憩開始・休憩終了・退勤）
     */
    @Transactional
    public AttendanceDto punch(AttendancePunchDto punchDto) {
        if (punchDto == null) {
            throw new IllegalArgumentException("打刻データは必須です。");
        }
        Long storeStaffId = punchDto.getStoreStaffId();
        String punchType = punchDto.getPunchType();
        if (storeStaffId == null || storeStaffId <= 0) {
            throw new IllegalArgumentException("有効なスタッフIDは必須です。");
        }
        if (punchType == null || !List.of("CLOCK_IN", "CLOCK_OUT", "BREAK_START", "BREAK_END").contains(punchType)) {
            throw new IllegalArgumentException("有効な打刻タイプは必須です。");
        }
        LocalDateTime now = LocalDateTime.now();
        LocalDate today = now.toLocalDate();

        // 排他制御: 同時打刻の競合を防止する悲観的ロック
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

    private AttendanceDto handleClockIn(AttendancePunchDto punchDto, LocalDate today, LocalDateTime now, AttendanceDto active) {
        if (active != null) {
            throw new IllegalStateException("既に出勤中の打刻が存在します。");
        }

        // 該当日の給与が既に確定済（CONFIRMED / PAID）の場合は新規打刻を遮断
        if (attendanceMapper.isPayrollConfirmedForDate(punchDto.getStoreStaffId(), today)) {
            throw new IllegalStateException("対象期間の給与は既に確定済みのため、新規打刻はできません。");
        }

        Long shiftId = attendanceMapper.findConfirmedShiftIdForDate(punchDto.getStoreStaffId(), today);

        // 店舗ID補正 (DTO未指定時は店舗所属マスタから補完)
        Long storeId = punchDto.getStoreId();
        if (storeId == null) {
            storeId = attendanceMapper.findStoreIdByStoreStaffId(punchDto.getStoreStaffId());
            if (storeId == null) {
                throw new IllegalStateException("所属店舗情報が存在しないため打刻できません。(storeStaffId: " + punchDto.getStoreStaffId() + ")");
            }
        }

        AttendanceDto attendance = new AttendanceDto();
        attendance.setStoreId(storeId);
        attendance.setStoreStaffId(punchDto.getStoreStaffId());
        attendance.setShiftId(shiftId);
        attendance.setWorkDate(today);
        attendance.setClockInAt(now);
        attendance.setTotalBreakMinutes(0);
        attendance.setNetWorkMinutes(0);
        attendance.setNightWorkMinutes(0);
        attendance.setOvertimeMinutes(0);
        attendance.setClockInMethod("BUTTON");
        attendance.setAttendanceStatus("WORKING");

        attendanceMapper.insertAttendance(attendance);
        return attendance;
    }

    private AttendanceDto handleBreakStart(LocalDateTime now, AttendanceDto active) {
        if (active == null || !"WORKING".equals(active.getAttendanceStatus())) {
            throw new IllegalStateException("勤務中のみ休憩開始が可能です。");
        }

        active.setBreakStartAt(now);
        active.setAttendanceStatus("ON_BREAK");
        attendanceMapper.updateAttendance(active);
        return active;
    }

    private AttendanceDto handleBreakEnd(LocalDateTime now, AttendanceDto active) {
        if (active == null || !"ON_BREAK".equals(active.getAttendanceStatus())) {
            throw new IllegalStateException("休憩中のみ休憩終了が可能です。");
        }

        LocalDateTime breakStart = active.getBreakStartAt();
        int breakDuration = Math.toIntExact(Duration.between(breakStart, now).toMinutes());

        AttendanceBreakDto breakDto = new AttendanceBreakDto(breakStart, now, breakDuration);
        breakDto.setAttendanceId(active.getAttendanceId());
        attendanceMapper.insertBreak(breakDto);

===== src/main/java/com/example/kintai/domain/attendance/controller/AttendanceApiController.java (20-85) =====
public class AttendanceApiController {

    private final AttendanceService attendanceService;
    private final StoreAuthorizationService storeAuthorizationService;

    public AttendanceApiController(AttendanceService attendanceService, StoreAuthorizationService storeAuthorizationService) {
        this.attendanceService = attendanceService;
        this.storeAuthorizationService = storeAuthorizationService;
    }

    /**
     * リアルタイム出退勤・休憩打刻 API
     * 認証済みスタッフの storeStaffId を優先適用し IDOR（なりすまし打刻）を防止。
     * オーナー（OWNER）の代理打刻時は、対象スタッフが自身の所有店舗に所属しているかを検証。
     */
    @PostMapping("/punch")
    public ResponseEntity<ApiResponse<AttendanceDto>> punch(
            @RequestBody AttendancePunchDto punchDto,
            @AuthenticationPrincipal CustomUserDetails userDetails
    ) {
        if (userDetails != null) {
            if (userDetails.getStoreStaffId() != null) {
                // 一般スタッフ本人打刻：セッションの storeStaffId で強制上書き
                punchDto.setStoreStaffId(userDetails.getStoreStaffId());
                if (userDetails.getStoreId() != null) {
                    punchDto.setStoreId(userDetails.getStoreId());
                }
            } else if (punchDto.getStoreStaffId() != null) {
                // オーナー（OWNER）代理打刻：対象スタッフがオーナー店舗の所属員であるかを厳格検証し、実店舗IDを強制バインド
                Long validatedStoreId = storeAuthorizationService.validateStaffStoreOwnership(punchDto.getStoreStaffId(), userDetails.getMemberId());
                punchDto.setStoreId(validatedStoreId);
            }
        }

        AttendanceDto result = attendanceService.punch(punchDto);
        return ResponseEntity.ok(ApiResponse.ok("打刻が完了しました。", result));
    }

    /**
     * 現在進行中の勤務実績取得 API
     */
    @GetMapping("/active")
    public ResponseEntity<ApiResponse<AttendanceDto>> getActiveAttendance(
            @RequestParam(name = "storeStaffId", required = false) Long storeStaffId,
            @AuthenticationPrincipal CustomUserDetails userDetails
    ) {
        if (userDetails == null) {
            throw new AccessDeniedException("認証が必要です。");
        }

        Long targetStaffId;
        if (userDetails.getStoreStaffId() != null) {
            targetStaffId = userDetails.getStoreStaffId();
        } else {
            if (storeStaffId == null) {
                throw new IllegalArgumentException("スタッフIDが指定されていません。");
            }
            storeAuthorizationService.validateStaffStoreOwnership(storeStaffId, userDetails.getMemberId());
            targetStaffId = storeStaffId;
        }

        AttendanceDto active = attendanceService.getActiveAttendance(targetStaffId);
        return ResponseEntity.ok(ApiResponse.ok(active));
    }

    /**

===== src/main/java/com/example/kintai/config/SecurityConfig.java (35-92) =====
@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final CustomUserDetailsService userDetailsService;
    private final CustomAuthenticationSuccessHandler successHandler;

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        CookieCsrfTokenRepository tokenRepository = CookieCsrfTokenRepository.withHttpOnlyFalse();
        SpaCsrfTokenRequestHandler requestHandler = new SpaCsrfTokenRequestHandler();

        http
                // BREACH攻撃防御とSPA/Fetch（Cookie/Header）の互換性を両立するCSRF多層防御設定
                .csrf(csrf -> csrf
                        .csrfTokenRepository(tokenRepository)
                        .csrfTokenRequestHandler(requestHandler)
                )
                .addFilterAfter(new CsrfCookieFilter(), BasicAuthenticationFilter.class)
                .authorizeHttpRequests(auth -> auth
                        // 静的リソースおよび認証関連エンドポイントの開放
                        .requestMatchers(
                                "/css/**",
                                "/js/**",
                                "/images/**",
                                "/favicon.ico",
                                "/login",
                                "/login-process"
                        ).permitAll()
                        // オーナー専用エンドポイント（給与計算、勤怠修正承認等）
                        .requestMatchers(
                                "/payroll/**",
                                "/api/payroll/**",
                                "/attendance/mod-requests/**",
                                "/api/attendance/mod-requests/**"
                        ).hasRole("OWNER")
                        // 勤怠打刻およびシフト管理（スタッフ・オーナー共通）
                        .requestMatchers(
                                "/attendance",
                                "/attendance/**",
                                "/shift/**",
                                "/shifts",
                                "/shifts/**",
                                "/api/shifts/**",
                                "/api/attendance/**"
                        ).hasAnyRole("STAFF", "OWNER", "ADMIN")
                        // サンプル確認用画面
                        .requestMatchers(
                                "/members/sample",
                                "/api/members/**"
                        ).hasRole("ADMIN")
                        // その他のリクエストは認証必須
                        .anyRequest().authenticated()
                )
                .formLogin(login -> login
                        .loginPage("/login")
                        .loginProcessingUrl("/login-process")

===== src/main/resources/mapper/attendance/AttendanceMapper.xml (140-170) =====
          AND #{workDate} &lt;= range_end_date
          AND payroll_status IN ('CONFIRMED', 'PAID')
    </select>

    <select id="lockStoreStaffForUpdate" resultType="java.lang.Long">
        SELECT store_staff_id
        FROM store_staff
        WHERE store_staff_id = #{storeStaffId}
        FOR UPDATE
    </select>

    <select id="findStoreIdByStoreStaffId" resultType="java.lang.Long">
        SELECT store_id
        FROM store_staff
        WHERE store_staff_id = #{storeStaffId}
        LIMIT 1
    </select>

    <select id="findConfirmedShiftIdForDate" resultType="java.lang.Long">
        SELECT shift_id
        FROM shift_schedule
        WHERE store_staff_id = #{storeStaffId}
          AND work_date = #{workDate}
          AND status = 'CONFIRMED'
        LIMIT 1
    </select>

    <select id="findCompletedAttendancesForStaffAndPeriod" resultType="com.example.kintai.domain.attendance.dto.AttendanceDto">
        SELECT
            attendance_id,
            store_id,

===== src/test/java/com/example/kintai/domain/attendance/service/AttendanceServiceConcurrencyIntegrationTest.java (1-190) =====
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

===== src/test/java/com/example/kintai/domain/attendance/service/AttendanceServicePunchValidationTest.java (1-100) =====
package com.example.kintai.domain.attendance.service;

import com.example.kintai.domain.attendance.dto.AttendancePunchDto;
import com.example.kintai.domain.attendance.mapper.AttendanceMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
class AttendanceServicePunchValidationTest {

    @Mock
    private AttendanceMapper attendanceMapper;

    @InjectMocks
    private AttendanceService attendanceService;

    private AttendancePunchDto request;

    @BeforeEach
    void setUp() {
        request = new AttendancePunchDto();
        request.setStoreStaffId(1L);
        request.setPunchType("CLOCK_IN");
    }

    @Test
    void nullRequestIsRejectedBeforeDatabaseAccess() {
        assertThrows(IllegalArgumentException.class, () -> attendanceService.punch(null));
        verifyNoInteractions(attendanceMapper);
    }

    @Test
    void missingOrNonPositiveStaffIdIsRejectedBeforeDatabaseAccess() {
        request.setStoreStaffId(null);
        assertThrows(IllegalArgumentException.class, () -> attendanceService.punch(request));
        request.setStoreStaffId(0L);
        assertThrows(IllegalArgumentException.class, () -> attendanceService.punch(request));
        verifyNoInteractions(attendanceMapper);
    }

    @Test
    void missingOrUnsupportedPunchTypeIsRejectedBeforeDatabaseAccess() {
        request.setPunchType(null);
        assertThrows(IllegalArgumentException.class, () -> attendanceService.punch(request));
        request.setPunchType("INVALID");
        assertThrows(IllegalArgumentException.class, () -> attendanceService.punch(request));
        verifyNoInteractions(attendanceMapper);
    }
}

===== src/test/java/com/example/kintai/domain/attendance/controller/AttendancePunchApiIntegrationTest.java (1-190) =====
package com.example.kintai.domain.attendance.controller;

import com.example.kintai.database.IsolatedDatabaseGuard;
import com.example.kintai.domain.member.dto.MemberDto;
import com.example.kintai.global.security.CustomUserDetails;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.time.LocalDate;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class AttendancePunchApiIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private DataSource dataSource;

    private long ownerId;
    private long staffMemberId;
    private long storeId;
    private long storeStaffId;
    private Long createdAttendanceId;

    @BeforeEach
    void createIsolatedApiFixture() throws Exception {
        IsolatedDatabaseGuard.requireAllowedDatabase(dataSource, "kintai_(day11_test|full_test)_[0-9a-f]{12}");
        try (Connection connection = dataSource.getConnection()) {
            ownerId = insertAndGetId(connection,
                    "INSERT INTO member(email,password,name,phone,role) VALUES(?,?,?,?,'ROLE_OWNER')",
                    "punch-owner-" + UUID.randomUUID() + "@example.invalid", "test-only", "Punch Owner", "0000000000");
            staffMemberId = insertAndGetId(connection,
                    "INSERT INTO member(email,password,name,phone,role) VALUES(?,?,?,?,'ROLE_STAFF')",
                    "punch-staff-" + UUID.randomUUID() + "@example.invalid", "test-only", "Punch Staff", "0000000001");
            storeId = insertAndGetId(connection,
                    "INSERT INTO store(owner_id,store_name,invite_code,qr_token,pay_cutoff_day) VALUES(?,?,?,?,99)",
                    ownerId, "Punch API Integration", token("invite"), token("qr"));
            storeStaffId = insertAndGetId(connection,
                    "INSERT INTO store_staff(store_id,member_id,hourly_wage,joined_date) VALUES(?,?,1300,?)",
                    storeId, staffMemberId, LocalDate.now());
        }
    }

    @AfterEach
    void cleanIsolatedApiFixture() throws Exception {
        try (Connection connection = dataSource.getConnection()) {
            if (createdAttendanceId != null) {
                try (PreparedStatement statement = connection.prepareStatement("DELETE FROM attendance WHERE attendance_id=?")) {
                    statement.setLong(1, createdAttendanceId);
                    statement.executeUpdate();
                }
            }
            if (storeStaffId > 0) delete(connection, "DELETE FROM store_staff WHERE store_staff_id=?", storeStaffId);
            if (storeId > 0) delete(connection, "DELETE FROM store WHERE store_id=?", storeId);
            if (staffMemberId > 0) delete(connection, "DELETE FROM member WHERE member_id=?", staffMemberId);
            if (ownerId > 0) delete(connection, "DELETE FROM member WHERE member_id=?", ownerId);
        }
    }

    @Test
    void authenticatedStaffPunchUsesSessionIdentityInsteadOfSpoofedBodyIds() throws Exception {
        MemberDto member = new MemberDto();
        member.setMemberId(staffMemberId);
        member.setEmail("punch-staff@example.invalid");
        member.setName("Punch Staff");
        member.setRole("ROLE_STAFF");
        member.setIsActive(true);
        CustomUserDetails principal = new CustomUserDetails(member, storeStaffId, storeId);

        String response = mockMvc.perform(post("/api/attendance/punch")
                        .with(user(principal))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"storeId\":999999,\"storeStaffId\":999999,\"punchType\":\"CLOCK_IN\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.storeId").value(storeId))
                .andExpect(jsonPath("$.data.storeStaffId").value(storeStaffId))
                .andExpect(jsonPath("$.data.attendanceStatus").value("WORKING"))
                .andReturn().getResponse().getContentAsString();

        Matcher idMatcher = Pattern.compile("\"attendanceId\"\\s*:\\s*(\\d+)").matcher(response);
        if (!idMatcher.find()) throw new AssertionError("Response did not contain the generated attendance ID.");
        long attendanceId = Long.parseLong(idMatcher.group(1));
        createdAttendanceId = attendanceId;
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "SELECT store_id,store_staff_id FROM attendance WHERE attendance_id=?")) {
            statement.setLong(1, attendanceId);
            try (ResultSet result = statement.executeQuery()) {
                if (!result.next()) throw new AssertionError("API-created attendance was not persisted.");
                assertEquals(storeId, result.getLong("store_id"));
                assertEquals(storeStaffId, result.getLong("store_staff_id"));
            }
        }
    }

    private static long insertAndGetId(Connection connection, String sql, Object... values) throws Exception {
        try (PreparedStatement statement = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            for (int i = 0; i < values.length; i++) statement.setObject(i + 1, values[i]);
            statement.executeUpdate();
            try (ResultSet keys = statement.getGeneratedKeys()) {
                if (!keys.next()) throw new IllegalStateException("Missing generated key.");
                return keys.getLong(1);
            }
        }
    }

    private static void delete(Connection connection, String sql, long id) throws Exception {
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, id);
            statement.executeUpdate();
        }
    }

    private static String token(String prefix) {
        return (prefix + UUID.randomUUID().toString().replace("-", "")).substring(0, 19);
    }
}

===== src/test/java/com/example/kintai/domain/attendance/controller/AttendanceApiSecurityTest.java (108-198) =====
    @DisplayName("リアルタイム打刻（POST /api/attendance/punch）の店舗ID強制バインドおよびIDOR防御")
    class PunchSecurityTest {

        @Test
        @DisplayName("オーナーが自己店舗スタッフの打刻時に誤ったstoreIdを指定しても、サーバー側で正規店舗IDに強制バインドされること")
        void punch_OwnerForOwnStaff_ShouldForceBindCorrectStoreId() throws Exception {
            Long ownerMemberId = 2L;
            Long targetStaffId = 10L;
            Long spoofedStoreId = 999L; // クライアントが改ざんまたは誤指定した店舗ID
            Long actualStoreId = 1L;   // DBに登録された正規店舗ID

            String jsonPayload = """
                {
                    "storeStaffId": 10,
                    "storeId": 999,
                    "punchType": "CLOCK_IN"
                }
                """;

            when(storeAuthorizationService.validateStaffStoreOwnership(targetStaffId, ownerMemberId)).thenReturn(actualStoreId);
            currentPrincipal = createOwnerDetails(ownerMemberId);

            mockMvc.perform(post("/api/attendance/punch")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(jsonPayload))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true));

            // 検証：サービス層へ渡されたDTOの storeId が正規店舗ID(1L)に強制置換されていること
            ArgumentCaptor<com.example.kintai.domain.attendance.dto.AttendancePunchDto> captor =
                    ArgumentCaptor.forClass(com.example.kintai.domain.attendance.dto.AttendancePunchDto.class);
            verify(attendanceService).punch(captor.capture());
            assertThat(captor.getValue().getStoreId()).isEqualTo(actualStoreId);
            assertThat(captor.getValue().getStoreStaffId()).isEqualTo(targetStaffId);
        }

        @Test
        @DisplayName("オーナーが他店舗スタッフの打刻を試みた場合は HTTP 403 Forbidden で遮断されること")
        void punch_OwnerForOtherStoreStaff_ShouldReturn403Forbidden() throws Exception {
            Long ownerMemberId = 2L;
            Long attackerTargetStaffId = 777L;

            String jsonPayload = """
                {
                    "storeStaffId": 777,
                    "punchType": "CLOCK_IN"
                }
                """;

            doThrow(new AccessDeniedException("対象スタッフに対する管理権限がありません。"))
                    .when(storeAuthorizationService).validateStaffStoreOwnership(attackerTargetStaffId, ownerMemberId);

            currentPrincipal = createOwnerDetails(ownerMemberId);

            mockMvc.perform(post("/api/attendance/punch")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(jsonPayload))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.success").value(false))
                    .andExpect(jsonPath("$.message").value("対象スタッフに対する管理権限がありません。"));

            verify(attendanceService, never()).punch(any());
        }

        @Test
        @DisplayName("スタッフが打刻時に他人IDと他店IDを偽造しても、セッションの本人情報に強制バインドされること")
        void punch_StaffAttemptsSpoofing_ShouldForceBindSessionInfo() throws Exception {
            Long myStaffId = 10L;
            Long myStoreId = 1L;

            String jsonPayload = """
                {
                    "storeStaffId": 888,
                    "storeId": 999,
                    "punchType": "CLOCK_IN"
                }
                """;

            currentPrincipal = createStaffDetails(1L, myStaffId, myStoreId);

            mockMvc.perform(post("/api/attendance/punch")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(jsonPayload))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true));

            ArgumentCaptor<com.example.kintai.domain.attendance.dto.AttendancePunchDto> captor =
                    ArgumentCaptor.forClass(com.example.kintai.domain.attendance.dto.AttendancePunchDto.class);
            verify(attendanceService).punch(captor.capture());
            assertThat(captor.getValue().getStoreStaffId()).isEqualTo(myStaffId);
            assertThat(captor.getValue().getStoreId()).isEqualTo(myStoreId);

===== src/test/java/com/example/kintai/config/CsrfSecurityIntegrationTest.java (1-85) =====
package com.example.kintai.config;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@DisplayName("CSRF 多層防御セキュリティ統合テスト")
class CsrfSecurityIntegrationTest {

    @Autowired
    private WebApplicationContext context;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders
                .webAppContextSetup(context)
                .apply(springSecurity())
                .build();
    }

    @Test
    @DisplayName("CSRFトークンなしのPOSTリクエストは403 Forbiddenで拒否されること")
    @WithMockUser(username = "staff1", roles = {"STAFF"})
    void postWithoutCsrf_ShouldReturn403Forbidden() throws Exception {
        mockMvc.perform(post("/api/attendance/punch")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"storeStaffId\":1,\"punchType\":\"CLOCK_IN\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("CSRFトークンが有効でも未認証ユーザーの打刻APIアクセスはログインへ拒否されること")
    void anonymousPunchWithValidCsrf_ShouldBeRejectedBySecurityFilter() throws Exception {
        mockMvc.perform(post("/api/attendance/punch")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"storeStaffId\":1,\"punchType\":\"CLOCK_IN\"}"))
                .andExpect(status().is3xxRedirection())
                .andExpect(result -> {
                    String redirectedUrl = result.getResponse().getRedirectedUrl();
                    if (redirectedUrl == null || !redirectedUrl.endsWith("/login")) {
                        throw new AssertionError("未認証打刻はログインへ誘導される必要があります。actual=" + redirectedUrl);
                    }
                });
    }

    @Test
    @DisplayName("不正なCSRFトークンを含むPOSTリクエストは403 Forbiddenで拒否されること")
    @WithMockUser(username = "staff1", roles = {"STAFF"})
    void postWithInvalidCsrf_ShouldReturn403Forbidden() throws Exception {
        mockMvc.perform(post("/api/attendance/punch")
                        .header("X-XSRF-TOKEN", "invalid-csrf-token-sample")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"storeStaffId\":1,\"punchType\":\"CLOCK_IN\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("オーナーおよびスタッフのアカウントで正常にログインできること")
    void testFormLoginSuccess() throws Exception {
        // オーナー (owner@kintai.com) -> /attendance/mod-requests
        mockMvc.perform(post("/login-process")
                        .with(csrf())
                        .param("email", "owner@kintai.com")
                        .param("password", "1234"))
                .andExpect(status().is3xxRedirection())
                .andExpect(result -> {
                    String url = result.getResponse().getRedirectedUrl();
                    if (!"/attendance/mod-requests".equals(url)) {
                        throw new AssertionError("オーナーログイン後のリダイレクト先が不正です: " + url);

===== src/test/java/com/example/kintai/database/IsolatedDatabaseGuard.java (1-100) =====
package com.example.kintai.database;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.regex.Pattern;

/** Fail closed before integration tests mutate database state. */
public final class IsolatedDatabaseGuard {

    private IsolatedDatabaseGuard() {}

    public static String requireAllowedDatabase(DataSource dataSource, String allowedNamePattern) throws Exception {
        Pattern allowed = Pattern.compile(allowedNamePattern);
        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement();
             ResultSet result = statement.executeQuery("SELECT DATABASE()")) {
            if (!result.next()) {
                throw new IllegalStateException("Unable to determine the integration-test database; refusing to continue.");
            }
            String database = result.getString(1);
            if (database == null || !allowed.matcher(database).matches()) {
                throw new IllegalStateException("Integration test requires an allowlisted isolated database; refusing database: " + database);
            }
            return database;
        }
    }
}

===== scripts/run_isolated_regression.ps1 (1-80) =====
[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)]
    [ValidateSet('Day10', 'Day11', 'All')]
    [string]$Scope,
    [Parameter(Mandatory = $true)]
    [string]$DatabaseName,
    [string]$TestClass
)

$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path -Parent $PSScriptRoot
$mysqlExe = 'C:\Program Files\MySQL\MySQL Server 26.7\bin\mysql.exe'
$settingsFile = Join-Path $projectRoot 'src\main\resources\application.yaml'
$runId = Get-Date -Format 'yyyyMMdd_HHmmss'
$outputDir = Join-Path $projectRoot ("docs\audit_runs\isolated_{0}_{1}_{2}" -f $Scope.ToLowerInvariant(), $runId, $DatabaseName)
$consoleLog = Join-Path $outputDir 'gradle_console.log'

if (-not $TestClass) {
    $TestClass = if ($Scope -eq 'Day11') {
        'com.example.kintai.domain.payroll.service.AnnualPayrollScenarioIntegrationTest'
    } else {
        'com.example.kintai.domain.attendance.service.AttendanceServiceConcurrencyIntegrationTest'
    }
}

if (-not (Test-Path -LiteralPath $mysqlExe -PathType Leaf)) { throw 'MySQL client not found.' }
if (-not (Test-Path -LiteralPath $settingsFile -PathType Leaf)) { throw 'application.yaml not found.' }

if ($Scope -eq 'Day10' -and $DatabaseName -notmatch '^kintai_day10_test_[0-9a-f]{12}$') {
    throw "Day10 database name is outside the generated allowlist: $DatabaseName"
}
if ($Scope -eq 'Day11' -and $DatabaseName -notmatch '^kintai_day11_test_[0-9a-f]{12}$') {
    throw "Day11 database name is outside the generated allowlist: $DatabaseName"
}
if ($Scope -eq 'All' -and $DatabaseName -notmatch '^kintai_full_test_[0-9a-f]{12}$') {
    throw "Full-suite database name is outside the generated allowlist: $DatabaseName"
}

function Get-DatabasePassword {
    if ($env:KINTAI_DB_PASSWORD) { return $env:KINTAI_DB_PASSWORD }
    $passwordLine = Get-Content -LiteralPath $settingsFile -Encoding UTF8 |
        Where-Object { $_ -match '^\s*password:' } | Select-Object -First 1
    if (-not $passwordLine) { throw 'Datasource password setting not found. Set KINTAI_DB_PASSWORD.' }
    return (($passwordLine -split ':', 2)[1] -split '#', 2)[0].Trim()
}

New-Item -ItemType Directory -Path $outputDir -Force | Out-Null
$resultDir = Join-Path $projectRoot 'build\test-results\test'
if (Test-Path -LiteralPath $resultDir -PathType Container) {
    $preexistingResults = @(Get-ChildItem -LiteralPath $resultDir -Filter 'TEST-*.xml' -File -ErrorAction SilentlyContinue)
    if ($preexistingResults.Count -gt 0) {
        $preservedResultDir = Join-Path $outputDir 'preexisting_test_results'
        New-Item -ItemType Directory -Path $preservedResultDir -Force | Out-Null
        foreach ($result in $preexistingResults) {
            Copy-Item -LiteralPath $result.FullName -Destination (Join-Path $preservedResultDir $result.Name) -Force
            Remove-Item -LiteralPath $result.FullName -Force
        }
        Write-Output "PRESERVED_PREEXISTING_JUNIT_COUNT=$($preexistingResults.Count)"
    }
}
$oldSpringUrl = $env:SPRING_DATASOURCE_URL
$oldMysqlPwd = $env:MYSQL_PWD
$env:MYSQL_PWD = Get-DatabasePassword
$env:SPRING_DATASOURCE_URL = "jdbc:mysql://127.0.0.1:3306/$DatabaseName"

try {
    $selected = & $mysqlExe --host=127.0.0.1 --port=3306 --user=root --database=$DatabaseName --batch --skip-column-names `
        --execute='SELECT DATABASE();'
    if ($LASTEXITCODE -ne 0) { throw "Target database preflight failed: exit $LASTEXITCODE" }
    if (($selected | Out-String).Trim() -ne $DatabaseName) {
        throw "Target database mismatch. Expected '$DatabaseName', got '$selected'."
    }

    $metadata = [ordered]@{
        database = $DatabaseName
        scope = $Scope
        test_class = if ($Scope -ne 'All') { $TestClass } else { $null }
        run_id = $runId
        output_dir = $outputDir

===== Prior Qwen findings and remediation =====
Prior Qwen A-05 warned that the audit excerpt did not establish identity authorization for punch. The current controller overwrites staff IDs from CustomUserDetails and owners use validateStaffStoreOwnership; SecurityConfig requires STAFF/OWNER/ADMIN for /api/attendance/**. A new actual-filter test sends valid CSRF without a principal and expects redirect to /login. The API integration test uses CustomUserDetails, sends spoofed IDs, and checks response plus persisted IDs. Prior A-06 warned about null/invalid request data. Service now rejects null DTO, null/nonpositive staff ID, null/unsupported type before mapper interaction; unit tests verify no mapper calls.

===== 3 isolated concurrency repetitions =====
[
  {
    "iteration": 1,
    "database": "kintai_day10_test_f21d3216d0d4",
    "status": "SUCCESS",
    "exit_code": 0,
    "tests": 2,
    "failures": 0,
    "errors": 0,
    "prepare_log": "C:\\dev\\kintai\\docs\\audit_runs\\day10_isolated_regression_20261114_093404\\iteration_01\\prepare.log",
    "runner_log": "C:\\dev\\kintai\\docs\\audit_runs\\day10_isolated_regression_20261114_093404\\iteration_01\\runner.log",
    "junit_xml": "C:\\dev\\kintai\\docs\\audit_runs\\day10_isolated_regression_20261114_093404\\iteration_01\\TEST-AttendanceServiceConcurrencyIntegrationTest.xml",
    "runner_log_sha256": "a2ca89262076d2dbce755b35c927f797982b256d66054af10e999871938a0ef6",
    "junit_sha256": "ee4df71ce6823ee24754af101741130e781e4a7d0d0ef25677599f6439123b47"
  },
  {
    "iteration": 2,
    "database": "kintai_day10_test_556e7ea160c0",
    "status": "SUCCESS",
    "exit_code": 0,
    "tests": 2,
    "failures": 0,
    "errors": 0,
    "prepare_log": "C:\\dev\\kintai\\docs\\audit_runs\\day10_isolated_regression_20261114_093404\\iteration_02\\prepare.log",
    "runner_log": "C:\\dev\\kintai\\docs\\audit_runs\\day10_isolated_regression_20261114_093404\\iteration_02\\runner.log",
    "junit_xml": "C:\\dev\\kintai\\docs\\audit_runs\\day10_isolated_regression_20261114_093404\\iteration_02\\TEST-AttendanceServiceConcurrencyIntegrationTest.xml",
    "runner_log_sha256": "b42a0055c1d22adef4efe2d809874af1a5db46d8fbb2738e129e8b8c726d2205",
    "junit_sha256": "beb5492f7fea1498bcb4ff57cfee27e2e92fb164ba85b5ea88761fdbe737eac5"
  },
  {
    "iteration": 3,
    "database": "kintai_day10_test_b4c6dec96694",
    "status": "SUCCESS",
    "exit_code": 0,
    "tests": 2,
    "failures": 0,
    "errors": 0,
    "prepare_log": "C:\\dev\\kintai\\docs\\audit_runs\\day10_isolated_regression_20261114_093404\\iteration_03\\prepare.log",
    "runner_log": "C:\\dev\\kintai\\docs\\audit_runs\\day10_isolated_regression_20261114_093404\\iteration_03\\runner.log",
    "junit_xml": "C:\\dev\\kintai\\docs\\audit_runs\\day10_isolated_regression_20261114_093404\\iteration_03\\TEST-AttendanceServiceConcurrencyIntegrationTest.xml",
    "runner_log_sha256": "86e2423316f2f8bda81b3092e01235af1a9762542a66730ae601c81cc929167c",
    "junit_sha256": "9f5fbf6e96cbb8121d2c2962aa5af4c66b8a3b96fc57616cfc46b26a2a35be57"
  }
]


===== Full isolated regression summary =====
{
  "gradle_exit_code": 0,
  "junit_xml_count": 37,
  "tests": 91,
  "failures": 0,
  "errors": 0,
  "skipped": 0,
  "junit_parse_errors": [],
  "result": "PASS"
}


===== JUnit summary docs/audit_runs/isolated_all_20261114_093302_kintai_full_test_964bf98d5088/TEST-com.example.kintai.config.CsrfSecurityIntegrationTest.xml =====
{
  "tests": 5,
  "failures": 0,
  "errors": 0,
  "skipped": 0,
  "cases": [
    {
      "name": "CSRFトークンなしのPOSTリクエストは403 Forbiddenで拒否されること",
      "verdict": "PASS"
    },
    {
      "name": "不正なCSRFトークンを含むPOSTリクエストは403 Forbiddenで拒否されること",
      "verdict": "PASS"
    },
    {
      "name": "CSRFトークンが有効でも未認証ユーザーの打刻APIアクセスはログインへ拒否されること",
      "verdict": "PASS"
    },
    {
      "name": "オーナーおよびスタッフのアカウントで正常にログインできること",
      "verdict": "PASS"
    },
    {
      "name": "正常なCSRFトークンを付与したPOSTリクエストは403にならず正常に受け付けられること",
      "verdict": "PASS"
    }
  ]
}

===== JUnit summary docs/audit_runs/isolated_all_20261114_093302_kintai_full_test_964bf98d5088/TEST-com.example.kintai.domain.attendance.controller.AttendancePunchApiIntegrationTest.xml =====
{
  "tests": 1,
  "failures": 0,
  "errors": 0,
  "skipped": 0,
  "cases": [
    {
      "name": "authenticatedStaffPunchUsesSessionIdentityInsteadOfSpoofedBodyIds()",
      "verdict": "PASS"
    }
  ]
}

===== JUnit summary docs/audit_runs/isolated_all_20261114_093302_kintai_full_test_964bf98d5088/TEST-com.example.kintai.domain.attendance.service.AttendanceServiceConcurrencyIntegrationTest.xml =====
{
  "tests": 2,
  "failures": 0,
  "errors": 0,
  "skipped": 0,
  "cases": [
    {
      "name": "동시 CLOCK_IN 요청은 하나만 성공하고 중복 근무 행을 만들지 않는다",
      "verdict": "PASS"
    },
    {
      "name": "동시 CLOCK_OUT 요청은 하나만 성공하고 완료 상태를 보존한다",
      "verdict": "PASS"
    }
  ]
}
