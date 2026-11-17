package com.example.kintai.domain.attendance.service;

import com.example.kintai.domain.attendance.dto.AttendanceBreakDto;
import com.example.kintai.domain.attendance.dto.AttendanceDto;
import com.example.kintai.domain.attendance.dto.AttendancePunchDto;
import com.example.kintai.domain.attendance.mapper.AttendanceMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

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

    @Test
    @DisplayName("H-1検証: 1分未満(例: 45秒)の休憩終了時でもdurationMinutesが1以上に補正され、DB制約(CHECK > 0)違反および状態固着が防止されること")
    void punch_WhenBreakDurationUnderOneMinute_ShouldClampDurationToAtLeastOneMinute() {
        request.setPunchType("BREAK_END");
        request.setStoreStaffId(10L);

        LocalDateTime now = LocalDateTime.now();
        LocalDateTime breakStart = now.minusSeconds(45); // 45秒休憩 (1分未満)

        AttendanceDto active = new AttendanceDto();
        active.setAttendanceId(100L);
        active.setStoreStaffId(10L);
        active.setAttendanceStatus("ON_BREAK");
        active.setBreakStartAt(breakStart);
        active.setTotalBreakMinutes(0);

        when(attendanceMapper.findActiveAttendance(10L)).thenReturn(active);

        attendanceService.punch(request);

        ArgumentCaptor<AttendanceBreakDto> breakCaptor = ArgumentCaptor.forClass(AttendanceBreakDto.class);
        verify(attendanceMapper).insertBreak(breakCaptor.capture());

        AttendanceBreakDto recordedBreak = breakCaptor.getValue();
        // 0分ではなく1分以上に補正されたことを検証
        assertThat(recordedBreak.getDurationMinutes()).isGreaterThanOrEqualTo(1);
        assertThat(recordedBreak.getAttendanceId()).isEqualTo(100L);

        // ON_BREAK固着解除およびWORKING復帰を検証
        assertThat(active.getAttendanceStatus()).isEqualTo("WORKING");
        assertThat(active.getBreakStartAt()).isNull();
        assertThat(active.getTotalBreakMinutes()).isGreaterThanOrEqualTo(1);
    }
}
