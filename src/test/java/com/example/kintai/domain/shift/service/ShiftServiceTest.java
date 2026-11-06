package com.example.kintai.domain.shift.service;

import com.example.kintai.domain.attendance.dto.AttendanceDto;
import com.example.kintai.domain.attendance.mapper.AttendanceMapper;
import com.example.kintai.domain.shift.dto.ShiftComparisonDto;
import com.example.kintai.domain.shift.dto.ShiftDto;
import com.example.kintai.domain.shift.mapper.ShiftMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

/**
 * ShiftService 単体テスト（Unit Test）
 * 日本労働基準法第34条に基づく法定休憩時間推奨算定ロジックおよび
 * シフト予定 vs 打刻実績のリアルタイム対照エンジンの正確性を検証する。
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("ShiftService 単体テスト")
class ShiftServiceTest {

    @Mock
    private ShiftMapper shiftMapper;

    @Mock
    private AttendanceMapper attendanceMapper;

    @InjectMocks
    private ShiftService shiftService;

    @Nested
    @DisplayName("労働基準法第34条 推奨休憩時間算定テスト")
    class BreakTimeCalculationTest {

        @Test
        @DisplayName("拘束時間が6時間以下の場合は休憩時間が0分であること")
        void calculateRecommendedBreakMinutes_Under6Hours_ShouldReturnZero() {
            LocalDateTime start = LocalDateTime.of(2026, 11, 6, 9, 0);
            LocalDateTime end = LocalDateTime.of(2026, 11, 6, 15, 0); // 6時間（360分）

            int breakMinutes = shiftService.calculateRecommendedBreakMinutes(start, end);

            assertThat(breakMinutes).isZero();
        }

        @Test
        @DisplayName("境界値検証: 拘束時間が6時間を1分でも超える（361分）と休憩時間が45分となること")
        void calculateRecommendedBreakMinutes_Over6HoursBoundary_ShouldReturn45() {
            LocalDateTime start = LocalDateTime.of(2026, 11, 6, 9, 0);
            LocalDateTime end = LocalDateTime.of(2026, 11, 6, 15, 1); // 6時間1分（361分）

            int breakMinutes = shiftService.calculateRecommendedBreakMinutes(start, end);

            assertThat(breakMinutes).isEqualTo(45);
        }

        @Test
        @DisplayName("拘束時間が6時間超・8時間以下の場合は休憩時間が45分であること")
        void calculateRecommendedBreakMinutes_Between6And8Hours_ShouldReturn45() {
            LocalDateTime start = LocalDateTime.of(2026, 11, 6, 9, 0);
            LocalDateTime end = LocalDateTime.of(2026, 11, 6, 16, 0); // 7時間（420分）

            int breakMinutes = shiftService.calculateRecommendedBreakMinutes(start, end);

            assertThat(breakMinutes).isEqualTo(45);
        }

        @Test
        @DisplayName("境界値検証: 拘束時間が8時間45分超（実働8時間超）の場合は休憩時間が60分となること")
        void calculateRecommendedBreakMinutes_Over8HoursBoundary_ShouldReturn60() {
            LocalDateTime start = LocalDateTime.of(2026, 11, 6, 9, 0);
            LocalDateTime end = LocalDateTime.of(2026, 11, 6, 17, 46); // 8時間46分（526分）

            int breakMinutes = shiftService.calculateRecommendedBreakMinutes(start, end);

            assertThat(breakMinutes).isEqualTo(60);
        }

        @Test
        @DisplayName("拘束時間が8時間超の場合は休憩時間が60分であること")
        void calculateRecommendedBreakMinutes_Over8Hours_ShouldReturn60() {
            LocalDateTime start = LocalDateTime.of(2026, 11, 6, 9, 0);
            LocalDateTime end = LocalDateTime.of(2026, 11, 6, 18, 0); // 9時間（540分）

            int breakMinutes = shiftService.calculateRecommendedBreakMinutes(start, end);

            assertThat(breakMinutes).isEqualTo(60);
        }
    }

    @Nested
    @DisplayName("シフト予定 vs 打刻実績 リアルタイム対照エンジン検証")
    class ShiftComparisonEngineTest {

        private final Long storeId = 1L;
        private final LocalDate targetDate = LocalDate.of(2026, 11, 6);

        @Test
        @DisplayName("境界値・例外検証: 店舗IDがnullの場合はIllegalArgumentExceptionをスローすること")
        void compareShiftWithAttendance_NullStoreId_ShouldThrowIllegalArgumentException() {
            org.assertj.core.api.Assertions.assertThatThrownBy(() ->
                    shiftService.compareShiftWithAttendance(null, targetDate)
            ).isInstanceOf(IllegalArgumentException.class)
             .hasMessageContaining("店舗IDは必須です");
        }

        @Test
        @DisplayName("予定通りの出勤・退勤（許容誤差5分以内）の場合はNORMALと判定されること")
        void compareShiftWithAttendance_NormalShift_ShouldReturnNormalStatus() {
            ShiftDto shift = new ShiftDto();
            shift.setShiftId(101L);
            shift.setStoreStaffId(1L);
            shift.setStaffName("山田 太郎");
            shift.setWorkDate(targetDate);
            shift.setConfirmedStartAt(targetDate.atTime(9, 0));
            shift.setConfirmedEndAt(targetDate.atTime(18, 0));
            shift.setPlannedBreakMinutes(60);

            AttendanceDto attendance = new AttendanceDto();
            attendance.setAttendanceId(201L);
            attendance.setStoreStaffId(1L);
            attendance.setWorkDate(targetDate);
            attendance.setClockInAt(targetDate.atTime(8, 58)); // 2分前出勤（正常）
            attendance.setClockOutAt(targetDate.atTime(18, 2)); // 2分後退勤（正常）
            attendance.setTotalBreakMinutes(60);
            attendance.setNetWorkMinutes(484);

            when(shiftMapper.findConfirmedShiftsByDate(storeId, targetDate)).thenReturn(List.of(shift));
            when(attendanceMapper.findAttendancesByStoreAndDate(storeId, targetDate)).thenReturn(List.of(attendance));

            List<ShiftComparisonDto> result = shiftService.compareShiftWithAttendance(storeId, targetDate);

            assertThat(result).hasSize(1);
            ShiftComparisonDto dto = result.get(0);
            assertThat(dto.getComparisonStatus()).isEqualTo("NORMAL");
            assertThat(dto.getLateMinutes()).isZero();
            assertThat(dto.getEarlyLeaveMinutes()).isZero();
        }

        @Test
        @DisplayName("予定開始時刻から5分を超過して出勤した場合はLATEと判定されること")
        void compareShiftWithAttendance_LateClockIn_ShouldReturnLateStatus() {
            ShiftDto shift = new ShiftDto();
            shift.setShiftId(102L);
            shift.setStoreStaffId(2L);
            shift.setStaffName("佐藤 次郎");
            shift.setWorkDate(targetDate);
            shift.setConfirmedStartAt(targetDate.atTime(9, 0));
            shift.setConfirmedEndAt(targetDate.atTime(18, 0));
            shift.setPlannedBreakMinutes(60);

            AttendanceDto attendance = new AttendanceDto();
            attendance.setAttendanceId(202L);
            attendance.setStoreStaffId(2L);
            attendance.setWorkDate(targetDate);
            attendance.setClockInAt(targetDate.atTime(9, 20)); // 20分遅刻
            attendance.setClockOutAt(targetDate.atTime(18, 0));
            attendance.setTotalBreakMinutes(60);
            attendance.setNetWorkMinutes(460);

            when(shiftMapper.findConfirmedShiftsByDate(storeId, targetDate)).thenReturn(List.of(shift));
            when(attendanceMapper.findAttendancesByStoreAndDate(storeId, targetDate)).thenReturn(List.of(attendance));

            List<ShiftComparisonDto> result = shiftService.compareShiftWithAttendance(storeId, targetDate);

            assertThat(result).hasSize(1);
            ShiftComparisonDto dto = result.get(0);
            assertThat(dto.getComparisonStatus()).isEqualTo("LATE");
            assertThat(dto.getLateMinutes()).isEqualTo(20);
            assertThat(dto.getEarlyLeaveMinutes()).isZero();
        }

        @Test
        @DisplayName("予定終了時刻より5分以上早く退勤した場合はEARLY_LEAVEと判定されること")
        void compareShiftWithAttendance_EarlyLeave_ShouldReturnEarlyLeaveStatus() {
            ShiftDto shift = new ShiftDto();
            shift.setShiftId(103L);
            shift.setStoreStaffId(3L);
            shift.setStaffName("田中 三郎");
            shift.setWorkDate(targetDate);
            shift.setConfirmedStartAt(targetDate.atTime(9, 0));
            shift.setConfirmedEndAt(targetDate.atTime(18, 0));
            shift.setPlannedBreakMinutes(60);

            AttendanceDto attendance = new AttendanceDto();
            attendance.setAttendanceId(203L);
            attendance.setStoreStaffId(3L);
            attendance.setWorkDate(targetDate);
            attendance.setClockInAt(targetDate.atTime(9, 0));
            attendance.setClockOutAt(targetDate.atTime(17, 30)); // 30分早退
            attendance.setTotalBreakMinutes(60);
            attendance.setNetWorkMinutes(450);

            when(shiftMapper.findConfirmedShiftsByDate(storeId, targetDate)).thenReturn(List.of(shift));
            when(attendanceMapper.findAttendancesByStoreAndDate(storeId, targetDate)).thenReturn(List.of(attendance));

            List<ShiftComparisonDto> result = shiftService.compareShiftWithAttendance(storeId, targetDate);

            assertThat(result).hasSize(1);
            ShiftComparisonDto dto = result.get(0);
            assertThat(dto.getComparisonStatus()).isEqualTo("EARLY_LEAVE");
            assertThat(dto.getEarlyLeaveMinutes()).isEqualTo(30);
            assertThat(dto.getLateMinutes()).isZero();
        }

        @Test
        @DisplayName("確定シフトが存在しないスタッフの打刻実績はUNSCHEDULEDと判定されること")
        void compareShiftWithAttendance_UnscheduledAttendance_ShouldReturnUnscheduledStatus() {
            AttendanceDto attendance = new AttendanceDto();
            attendance.setAttendanceId(204L);
            attendance.setStoreStaffId(4L);
            attendance.setWorkDate(targetDate);
            attendance.setClockInAt(targetDate.atTime(13, 0));
            attendance.setClockOutAt(targetDate.atTime(17, 0));
            attendance.setTotalBreakMinutes(0);
            attendance.setNetWorkMinutes(240);

            when(shiftMapper.findConfirmedShiftsByDate(storeId, targetDate)).thenReturn(Collections.emptyList());
            when(attendanceMapper.findAttendancesByStoreAndDate(storeId, targetDate)).thenReturn(List.of(attendance));

            List<ShiftComparisonDto> result = shiftService.compareShiftWithAttendance(storeId, targetDate);

            assertThat(result).hasSize(1);
            ShiftComparisonDto dto = result.get(0);
            assertThat(dto.getComparisonStatus()).isEqualTo("UNSCHEDULED");
            assertThat(dto.getActualWorkMinutes()).isEqualTo(240L);
        }
    }
}
