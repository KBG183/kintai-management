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
