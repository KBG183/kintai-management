package com.example.kintai.domain.attendance.service;

import com.example.kintai.domain.attendance.dto.AttendanceDto;
import com.example.kintai.domain.attendance.dto.LaborDetailDto;
import com.example.kintai.domain.attendance.mapper.AttendanceMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Collections;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("LaborCalculationService 労働基準法詳細算定サービス単体テスト")
class LaborCalculationServiceTest {

    @Mock
    private AttendanceMapper attendanceMapper;

    @InjectMocks
    private LaborCalculationService laborCalculationService;

    @Test
    @DisplayName("H-2検証: 分割勤務（中抜け）の第2勤務時、先行勤務累計秒数が連動して8時間超過分の残業が正確に算定されること")
    void calculateDetailedLabor_WhenSplitShift_ShouldIncludePriorWorkSeconds() {
        ReflectionTestUtils.setField(laborCalculationService, "minHourlyWage", 1280);

        Long attendanceId = 200L;
        Long staffId = 10L;
        LocalDate workDate = LocalDate.of(2026, 11, 15);
        LocalDateTime clockIn = LocalDateTime.of(2026, 11, 15, 15, 0);
        LocalDateTime clockOut = LocalDateTime.of(2026, 11, 15, 21, 0); // 6時間拘束 (360分)

        AttendanceDto att = new AttendanceDto();
        att.setAttendanceId(attendanceId);
        att.setStoreStaffId(staffId);
        att.setWorkDate(workDate);
        att.setClockInAt(clockIn);
        att.setClockOutAt(clockOut);
        att.setTotalBreakMinutes(0);

        // 同日の先行勤務実績: 09:00~14:00 (5時間 = 300分 = 18,000秒)
        long priorWorkSeconds = 5L * 3600L;

        when(attendanceMapper.findById(attendanceId)).thenReturn(att);
        when(attendanceMapper.findBreaksByAttendanceId(attendanceId)).thenReturn(Collections.emptyList());
        when(attendanceMapper.sumPriorNetWorkSecondsOnDate(staffId, workDate, attendanceId, clockIn)).thenReturn(priorWorkSeconds);
        when(attendanceMapper.findHourlyWageForStaffOnDate(staffId, workDate)).thenReturn(1300);
        when(attendanceMapper.findStaffNameByStoreStaffId(staffId)).thenReturn("佐藤 花子");

        LaborDetailDto result = laborCalculationService.calculateDetailedLabor(attendanceId);

        assertThat(result).isNotNull();
        // 第2勤務: 6時間(360分)。先行5時間があるため、3時間(180分)で日通算8時間に達し、残り3時間(180分)は法定外残業となる
        assertThat(result.getNetWorkMinutes()).isEqualTo(360);
        assertThat(result.getTotalOvertimeMinutes()).isEqualTo(180); // 残業3時間 (H-2で0分になっていた欠陥の解消検証)
    }

    @Test
    @DisplayName("M-8検証: attendance_break個別レコードが無く合計休憩分(60分)のみ存在する場合でもフォールバックで実労働が正常算定されること")
    void calculateDetailedLabor_WhenOnlyTotalBreakMinutesExists_ShouldFallbackAndCalculateCorrectNetWork() {
        ReflectionTestUtils.setField(laborCalculationService, "minHourlyWage", 1280);

        Long attendanceId = 300L;
        Long staffId = 10L;
        LocalDate workDate = LocalDate.of(2026, 11, 16);
        LocalDateTime clockIn = LocalDateTime.of(2026, 11, 16, 9, 0);
        LocalDateTime clockOut = LocalDateTime.of(2026, 11, 16, 18, 0); // 9時間拘束 (540分)

        AttendanceDto att = new AttendanceDto();
        att.setAttendanceId(attendanceId);
        att.setStoreStaffId(staffId);
        att.setWorkDate(workDate);
        att.setClockInAt(clockIn);
        att.setClockOutAt(clockOut);
        att.setTotalBreakMinutes(60); // 休憩個別レコードは無く合計60分のみ保持

        when(attendanceMapper.findById(attendanceId)).thenReturn(att);
        when(attendanceMapper.findBreaksByAttendanceId(attendanceId)).thenReturn(Collections.emptyList());
        when(attendanceMapper.sumPriorNetWorkSecondsOnDate(staffId, workDate, attendanceId, clockIn)).thenReturn(0L);
        when(attendanceMapper.findHourlyWageForStaffOnDate(staffId, workDate)).thenReturn(1300);
        when(attendanceMapper.findStaffNameByStoreStaffId(staffId)).thenReturn("佐藤 花子");

        LaborDetailDto result = laborCalculationService.calculateDetailedLabor(attendanceId);

        assertThat(result).isNotNull();
        // 9時間拘束 - 休憩60分フォールバック = 実労働480分(8時間)、残業0分
        assertThat(result.getTotalBreakMinutes()).isEqualTo(60);
        assertThat(result.getNetWorkMinutes()).isEqualTo(480);
        assertThat(result.getTotalOvertimeMinutes()).isEqualTo(0);
    }
}
