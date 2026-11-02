package com.example.kintai.domain.attendance.dto;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public class AttendanceModRequestDto {
    private Long modRequestId;
    private Long attendanceId; // null이면 타각 완전 누락 사후 생성
    private Long storeId;
    private Long storeStaffId;
    private LocalDate workDate;
    private LocalDateTime beforeClockInAt;
    private LocalDateTime beforeClockOutAt;
    private Integer beforeBreakMinutes;
    private LocalDateTime requestedClockInAt;
    private LocalDateTime requestedClockOutAt;
    private Integer requestedBreakMinutes;
    private List<BreakTimeDto> requestedBreaks;
    private String reason;

    public AttendanceModRequestDto() {}

    public static class BreakTimeDto {
        private LocalDateTime startAt;
        private LocalDateTime endAt;

        public BreakTimeDto() {}
        public BreakTimeDto(LocalDateTime startAt, LocalDateTime endAt) {
            this.startAt = startAt;
            this.endAt = endAt;
        }

        public LocalDateTime getStartAt() { return startAt; }
        public void setStartAt(LocalDateTime startAt) { this.startAt = startAt; }
        public LocalDateTime getEndAt() { return endAt; }
        public void setEndAt(LocalDateTime endAt) { this.endAt = endAt; }
    }

    public Long getModRequestId() { return modRequestId; }
    public void setModRequestId(Long modRequestId) { this.modRequestId = modRequestId; }

    public Long getAttendanceId() { return attendanceId; }
    public void setAttendanceId(Long attendanceId) { this.attendanceId = attendanceId; }

    public Long getStoreId() { return storeId; }
    public void setStoreId(Long storeId) { this.storeId = storeId; }

    public Long getStoreStaffId() { return storeStaffId; }
    public void setStoreStaffId(Long storeStaffId) { this.storeStaffId = storeStaffId; }

    public LocalDate getWorkDate() { return workDate; }
    public void setWorkDate(LocalDate workDate) { this.workDate = workDate; }

    public LocalDateTime getBeforeClockInAt() { return beforeClockInAt; }
    public void setBeforeClockInAt(LocalDateTime beforeClockInAt) { this.beforeClockInAt = beforeClockInAt; }

    public LocalDateTime getBeforeClockOutAt() { return beforeClockOutAt; }
    public void setBeforeClockOutAt(LocalDateTime beforeClockOutAt) { this.beforeClockOutAt = beforeClockOutAt; }

    public Integer getBeforeBreakMinutes() { return beforeBreakMinutes; }
    public void setBeforeBreakMinutes(Integer beforeBreakMinutes) { this.beforeBreakMinutes = beforeBreakMinutes; }

    public LocalDateTime getRequestedClockInAt() { return requestedClockInAt; }
    public void setRequestedClockInAt(LocalDateTime requestedClockInAt) { this.requestedClockInAt = requestedClockInAt; }

    public LocalDateTime getRequestedClockOutAt() { return requestedClockOutAt; }
    public void setRequestedClockOutAt(LocalDateTime requestedClockOutAt) { this.requestedClockOutAt = requestedClockOutAt; }

    public Integer getRequestedBreakMinutes() { return requestedBreakMinutes; }
    public void setRequestedBreakMinutes(Integer requestedBreakMinutes) { this.requestedBreakMinutes = requestedBreakMinutes; }

    public List<BreakTimeDto> getRequestedBreaks() { return requestedBreaks; }
    public void setRequestedBreaks(List<BreakTimeDto> requestedBreaks) { this.requestedBreaks = requestedBreaks; }

    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }
}
