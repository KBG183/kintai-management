package com.example.kintai.domain.attendance.dto;

import java.time.LocalDateTime;

public class AttendanceBreakDto {
    private Long attendanceBreakId;
    private Long attendanceId;
    private LocalDateTime breakStartAt;
    private LocalDateTime breakEndAt;
    private Integer durationMinutes;

    public AttendanceBreakDto() {}

    public AttendanceBreakDto(LocalDateTime breakStartAt, LocalDateTime breakEndAt, Integer durationMinutes) {
        this.breakStartAt = breakStartAt;
        this.breakEndAt = breakEndAt;
        this.durationMinutes = durationMinutes;
    }

    public Long getAttendanceBreakId() { return attendanceBreakId; }
    public void setAttendanceBreakId(Long attendanceBreakId) { this.attendanceBreakId = attendanceBreakId; }

    public Long getAttendanceId() { return attendanceId; }
    public void setAttendanceId(Long attendanceId) { this.attendanceId = attendanceId; }

    public LocalDateTime getBreakStartAt() { return breakStartAt; }
    public void setBreakStartAt(LocalDateTime breakStartAt) { this.breakStartAt = breakStartAt; }

    public LocalDateTime getBreakEndAt() { return breakEndAt; }
    public void setBreakEndAt(LocalDateTime breakEndAt) { this.breakEndAt = breakEndAt; }

    public Integer getDurationMinutes() { return durationMinutes; }
    public void setDurationMinutes(Integer durationMinutes) { this.durationMinutes = durationMinutes; }
}
