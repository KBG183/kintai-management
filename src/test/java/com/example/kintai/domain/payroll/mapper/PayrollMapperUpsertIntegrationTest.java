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
@DisplayName("F-09: MySQL upsertPayroll useGeneratedKeys & LAST_INSERT_ID のセッション汚染分離に関する精密実測")
class PayrollMapperUpsertIntegrationTest {

    @Autowired
    private PayrollMapper payrollMapper;

    @Autowired
    private AttendanceMapper attendanceMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    @DisplayName("途中で別の AUTO_INCREMENT が発生して LAST_INSERT_ID が汚染された場合でも、DTO B に元の対象 PK が正確に渡されることを検証")
    void upsertPayroll_WhenLastInsertIdPolluted_ShouldStillReceiveTargetPayrollId() {
        // 0. 初期外部キー用 Attendance レコードを作成
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

        // 1. DTO A（payrollId=null）で新規 INSERT を実行
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
        assertNotNull(originalPayrollId, "DTO A に payrollId が採番されなければならない");

        Long sessionLastIdAfterA = jdbcTemplate.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
        System.out.println("[ステップ1] DTO A INSERT 後 - 採番 ID: " + originalPayrollId + ", セッション LAST_INSERT_ID(): " + sessionLastIdAfterA);
        assertEquals(originalPayrollId, sessionLastIdAfterA, "DTO A の INSERT 直後のセッション LAST_INSERT_ID は originalPayrollId と一致する");

        // 2. 日別明細を1件登録（originalPayrollId を参照）
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

        // 3. 【汚染制御ステップ】同一セッションでまったく別のテーブルに AUTO_INCREMENT 行を複数作成し、セッション LAST_INSERT_ID を意図的に別の値へ変更
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
        System.out.println("[ステップ2：汚染発生] Attendance 挿入後 - attendance_id: " + pollutedAttendanceId + ", セッション LAST_INSERT_ID(): " + sessionLastIdPolluted);
        assertNotEquals(originalPayrollId, sessionLastIdPolluted, "セッション LAST_INSERT_ID が給与 ID と異なる値に汚染されたことを確認");
        assertEquals(pollutedAttendanceId, sessionLastIdPolluted, "セッション LAST_INSERT_ID が直近に挿入された attendanceId に変更された");

        // 4. 新しいインスタンスの DTO B（payrollId=null）で同一ユニークキーに対する重複 UPDATE を実行
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
        assertNull(dtoB.getPayrollId(), "UPDATE 実行前の DTO B の payrollId は null である");

        // When: DTO B で重複 UPDATE（ON DUPLICATE KEY UPDATE）を実行
        payrollMapper.upsertPayroll(dtoB);
        Long idInDtoB = dtoB.getPayrollId();
        Long sessionLastIdAfterUpdate = jdbcTemplate.queryForObject("SELECT LAST_INSERT_ID()", Long.class);

        System.out.println("==================================================================");
        System.out.println("[F-09 セッション汚染分離の精密実測結果]");
        System.out.println("1. 元の対象給与 ID（Target）     : " + originalPayrollId);
        System.out.println("2. 途中で汚染されたセッション ID（Polluted）: " + sessionLastIdPolluted);
        System.out.println("3. DTO B にバインドされた最終 ID        : " + idInDtoB);
        System.out.println("4. 重複 UPDATE 後のセッション LAST_INSERT_ID: " + sessionLastIdAfterUpdate);
        System.out.println("==================================================================");

        // Then: DTO B にバインドされた ID が汚染された ID（pollutedAttendanceId）ではなく、元の給与 ID（originalPayrollId）であることを検証
        assertNotNull(idInDtoB, "DTO B の payrollId は null であってはならない");
        assertNotEquals(sessionLastIdPolluted, idInDtoB, "[CRITICAL CHECK] DTO B に汚染されたセッション ID が設定されてはならない");
        assertEquals(originalPayrollId, idInDtoB, "[CRITICAL CHECK] DTO B には必ず元の対象給与レコードの PK がバインドされなければならない");

        // 5. バインドされた ID による後続明細削除が正常に動作することを確認
        payrollMapper.deleteDailyDetailsByPayrollId(dtoB.getPayrollId());
        List<PayrollDailyDetailDto> detailsAfter = payrollMapper.findDailyDetailsByPayrollId(originalPayrollId);
        assertEquals(0, detailsAfter.size(), "既存の明細レコードが正確に削除されなければならない");
    }
}
