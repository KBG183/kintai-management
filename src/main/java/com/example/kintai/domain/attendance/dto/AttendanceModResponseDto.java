package com.example.kintai.domain.attendance.dto;

import java.time.LocalDate;
import java.time.LocalDateTime;

public class AttendanceModResponseDto {
    private Long modRequestId;
    private Long attendanceId;
    private Long storeId;
    private Long storeStaffId;
    private String staffName;
    private LocalDate workDate;
    private LocalDateTime beforeClockInAt;
    private LocalDateTime beforeClockOutAt;
    private Integer beforeBreakMinutes;
    private LocalDateTime requestedClockInAt;
    private LocalDateTime requestedClockOutAt;
    private Integer requestedBreakMinutes;
    private String reason;
    private String approvalStatus;
    private String rejectReason;
    private LocalDateTime createdAt;

    public AttendanceModResponseDto() {}

    public Long getModRequestId() { return modRequestId; }
    public void setModRequestId(Long modRequestId) { this.modRequestId = modRequestId; }

    public Long getAttendanceId() { return attendanceId; }
    public void setAttendanceId(Long attendanceId) { this.attendanceId = attendanceId; }

    public Long getStoreId() { return storeId; }
    public void setStoreId(Long storeId) { this.storeId = storeId; }

    public Long getStoreStaffId() { return storeStaffId; }
    public void setStoreStaffId(Long storeStaffId) { this.storeStaffId = storeStaffId; }

    public String getStaffName() { return staffName; }
    public void setStaffName(String staffName) { this.staffName = staffName; }

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

    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }

    public String getApprovalStatus() { return approvalStatus; }
    public void setApprovalStatus(String approvalStatus) { this.approvalStatus = approvalStatus; }

    public String getRejectReason() { return rejectReason; }
    public void setRejectReason(String rejectReason) { this.rejectReason = rejectReason; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
