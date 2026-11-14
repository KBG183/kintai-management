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
