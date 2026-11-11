package com.example.kintai.domain.payroll.mapper;

import com.example.kintai.domain.attendance.dto.AttendanceDto;
import com.example.kintai.domain.attendance.mapper.AttendanceMapper;
import com.example.kintai.domain.payroll.dto.PayrollDailyDetailDto;
import com.example.kintai.domain.payroll.dto.PayrollDto;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@Transactional
@DisplayName("F-09: MySQL upsertPayroll useGeneratedKeys & LAST_INSERT_ID 세션 오염 분리 정밀 실측")
class PayrollMapperUpsertIntegrationTest {

    @Autowired
    private PayrollMapper payrollMapper;

    @Autowired
    private AttendanceMapper attendanceMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    @DisplayName("중간에 다른 AUTO_INCREMENT 발생으로 LAST_INSERT_ID 오염 시에도 DTO B에 원래 대상 PK가 정확히 전달되는지 검증")
    void upsertPayroll_WhenLastInsertIdPolluted_ShouldStillReceiveTargetPayrollId() {
        // 0. 초기 외래키용 Attendance 레코드 생성
        AttendanceDto attendance1 = new AttendanceDto();
        attendance1.setStoreId(1L);
        attendance1.setStoreStaffId(1L);
        attendance1.setWorkDate(LocalDate.of(2026, 11, 5));
        attendance1.setClockInAt(LocalDateTime.of(2026, 11, 5, 9, 0));
        attendance1.setClockOutAt(LocalDateTime.of(2026, 11, 5, 18, 0));
        attendance1.setTotalBreakMinutes(60);
        attendance1.setNetWorkMinutes(480);
        attendance1.setNightWorkMinutes(0);
        attendance1.setOvertimeMinutes(0);
        attendance1.setClockInMethod("BUTTON");
        attendance1.setAttendanceStatus("COMPLETED");
        attendanceMapper.insertAttendance(attendance1);
        Long validAttendanceId = attendance1.getAttendanceId();
        assertNotNull(validAttendanceId);

        // 1. DTO A (payrollId=null)로 신규 INSERT 실행
        PayrollDto dtoA = new PayrollDto();
        dtoA.setStoreId(1L);
        dtoA.setStoreStaffId(1L);
        dtoA.setTargetYearMonth("2026-11");
        dtoA.setRangeStartDate(LocalDate.of(2026, 11, 1));
        dtoA.setRangeEndDate(LocalDate.of(2026, 11, 30));
        dtoA.setWorkDaysCount(5);
        dtoA.setTotalWorkMinutes(2400);
        dtoA.setTotalNightMinutes(300);
        dtoA.setTotalOvertimeMinutes(120);
        dtoA.setAppliedHourlyWage(1300);
        dtoA.setAppliedTransportLimit(15000);
        dtoA.setBasePay(52000);
        dtoA.setNightAllowance(1625);
        dtoA.setOvertimeAllowance(650);
        dtoA.setTransportAllowance(3000);
        dtoA.setGrossPay(57275);
        dtoA.setPayrollStatus("DRAFT");

        payrollMapper.upsertPayroll(dtoA);
        Long originalPayrollId = dtoA.getPayrollId();
        assertNotNull(originalPayrollId, "DTO A에 payrollId가 채번되어야 함");

        Long sessionLastIdAfterA = jdbcTemplate.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
        System.out.println("[단계 1] DTO A INSERT 후 - 채번 ID: " + originalPayrollId + ", 세션 LAST_INSERT_ID(): " + sessionLastIdAfterA);
        assertEquals(originalPayrollId, sessionLastIdAfterA, "DTO A INSERT 직후 세션 LAST_INSERT_ID는 originalPayrollId와 일치함");

        // 2. 일별 상세 1건 등록 (originalPayrollId 참조)
        PayrollDailyDetailDto detail = new PayrollDailyDetailDto();
        detail.setPayrollId(originalPayrollId);
        detail.setAttendanceId(validAttendanceId);
        detail.setWorkDate(LocalDate.of(2026, 11, 5));
        detail.setAppliedHourlyWage(1300);
        detail.setTotalBreakMinutes(60);
        detail.setNetWorkMinutes(480);
        detail.setOvertimeMinutes(0);
        detail.setNightWorkMinutes(0);
        detail.setBasePay(10400);
        detail.setOvertimeAllowance(0);
        detail.setNightAllowance(0);
        detail.setTransportAmount(500);
        payrollMapper.insertPayrollDailyDetail(detail);

        // 3. 【오염 통제 단계】동일 세션에서 완전히 다른 테이블에 AUTO_INCREMENT 행을 다수 생성하여 세션 LAST_INSERT_ID를 다른 값으로 고의 변경
        AttendanceDto attendancePolluter = new AttendanceDto();
        attendancePolluter.setStoreId(1L);
        attendancePolluter.setStoreStaffId(1L);
        attendancePolluter.setWorkDate(LocalDate.of(2026, 11, 6));
        attendancePolluter.setClockInAt(LocalDateTime.of(2026, 11, 6, 9, 0));
        attendancePolluter.setClockOutAt(LocalDateTime.of(2026, 11, 6, 18, 0));
        attendancePolluter.setTotalBreakMinutes(60);
        attendancePolluter.setNetWorkMinutes(480);
        attendancePolluter.setNightWorkMinutes(0);
        attendancePolluter.setOvertimeMinutes(0);
        attendancePolluter.setClockInMethod("BUTTON");
        attendancePolluter.setAttendanceStatus("COMPLETED");
        attendanceMapper.insertAttendance(attendancePolluter);
        Long pollutedAttendanceId = attendancePolluter.getAttendanceId();

        Long sessionLastIdPolluted = jdbcTemplate.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
        System.out.println("[단계 2: 오염 유발] Attendance 삽입 후 - attendance_id: " + pollutedAttendanceId + ", 세션 LAST_INSERT_ID(): " + sessionLastIdPolluted);
        assertNotEquals(originalPayrollId, sessionLastIdPolluted, "세션 LAST_INSERT_ID가 급여 ID와 다른 값으로 오염되었음을 확인");
        assertEquals(pollutedAttendanceId, sessionLastIdPolluted, "세션 LAST_INSERT_ID는 최근 삽입된 attendanceId로 변경됨");

        // 4. 새로운 인스턴스 DTO B (payrollId=null)로 동일 유니크 키 중복 UPDATE 실행
        PayrollDto dtoB = new PayrollDto();
        dtoB.setStoreId(1L);
        dtoB.setStoreStaffId(1L);
        dtoB.setTargetYearMonth("2026-11");
        dtoB.setRangeStartDate(LocalDate.of(2026, 11, 1));
        dtoB.setRangeEndDate(LocalDate.of(2026, 11, 30));
        dtoB.setWorkDaysCount(6);
        dtoB.setTotalWorkMinutes(2880);
        dtoB.setTotalNightMinutes(300);
        dtoB.setTotalOvertimeMinutes(120);
        dtoB.setAppliedHourlyWage(1300);
        dtoB.setAppliedTransportLimit(15000);
        dtoB.setBasePay(62400);
        dtoB.setNightAllowance(1625);
        dtoB.setOvertimeAllowance(650);
        dtoB.setTransportAllowance(3000);
        dtoB.setGrossPay(67675);
        dtoB.setPayrollStatus("DRAFT");
        assertNull(dtoB.getPayrollId(), "UPDATE 실행 전 DTO B의 payrollId는 null");

        // When: DTO B로 중복 UPDATE (ON DUPLICATE KEY UPDATE) 실행
        payrollMapper.upsertPayroll(dtoB);
        Long idInDtoB = dtoB.getPayrollId();
        Long sessionLastIdAfterUpdate = jdbcTemplate.queryForObject("SELECT LAST_INSERT_ID()", Long.class);

        System.out.println("==================================================================");
        System.out.println("[F-09 세션 오염 분리 정밀 결과]");
        System.out.println("1. 원래 대상 급여 ID (Target)     : " + originalPayrollId);
        System.out.println("2. 중간 오염된 세션 ID (Polluted) : " + sessionLastIdPolluted);
        System.out.println("3. DTO B에 바인딩된 최종 ID        : " + idInDtoB);
        System.out.println("4. 중복 UPDATE 후 세션 LAST_INSERT_ID: " + sessionLastIdAfterUpdate);
        System.out.println("==================================================================");

        // Then: DTO B에 바인딩된 ID가 오염된 ID(pollutedAttendanceId)가 아니라 원래 급여 ID(originalPayrollId)인지 검증
        assertNotNull(idInDtoB, "DTO B의 payrollId는 null이 아니어야 함");
        assertNotEquals(sessionLastIdPolluted, idInDtoB, "[CRITICAL CHECK] DTO B에 오염된 세션 ID가 들어가지 않아야 함");
        assertEquals(originalPayrollId, idInDtoB, "[CRITICAL CHECK] DTO B에는 반드시 원래 대상 급여 레코드의 PK가 바인딩되어야 함");

        // 5. 바인딩된 ID로 후속 상세 삭제 정상 동작 확인
        payrollMapper.deleteDailyDetailsByPayrollId(dtoB.getPayrollId());
        List<PayrollDailyDetailDto> detailsAfter = payrollMapper.findDailyDetailsByPayrollId(originalPayrollId);
        assertEquals(0, detailsAfter.size(), "기존 상세 레코드가 정확히 삭제되어야 함");
    }
}
