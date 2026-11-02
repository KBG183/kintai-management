package com.example.kintai.domain.attendance.dto;

import java.time.LocalDate;
import java.time.LocalDateTime;

public class AttendanceDto {
    private Long attendanceId;
    private Long storeId;
    private Long storeStaffId;
    private String staffName;
    private Long shiftId;
    private LocalDate workDate;
    private LocalDateTime clockInAt;
    private LocalDateTime clockOutAt;
    private LocalDateTime breakStartAt;
    private LocalDateTime breakEndAt;
    private Integer totalBreakMinutes;
    private Integer netWorkMinutes;
    private Integer nightWorkMinutes;
    private Integer overtimeMinutes;
    private String clockInMethod;
    private String attendanceStatus;

    public AttendanceDto() {}

    public Long getAttendanceId() { return attendanceId; }
    public void setAttendanceId(Long attendanceId) { this.attendanceId = attendanceId; }

    public Long getStoreId() { return storeId; }
    public void setStoreId(Long storeId) { this.storeId = storeId; }

    public Long getStoreStaffId() { return storeStaffId; }
    public void setStoreStaffId(Long storeStaffId) { this.storeStaffId = storeStaffId; }

    public String getStaffName() { return staffName; }
    public void setStaffName(String staffName) { this.staffName = staffName; }

    public Long getShiftId() { return shiftId; }
    public void setShiftId(Long shiftId) { this.shiftId = shiftId; }

    public LocalDate getWorkDate() { return workDate; }
    public void setWorkDate(LocalDate workDate) { this.workDate = workDate; }

    public LocalDateTime getClockInAt() { return clockInAt; }
    public void setClockInAt(LocalDateTime clockInAt) { this.clockInAt = clockInAt; }

    public LocalDateTime getClockOutAt() { return clockOutAt; }
    public void setClockOutAt(LocalDateTime clockOutAt) { this.clockOutAt = clockOutAt; }

    public LocalDateTime getBreakStartAt() { return breakStartAt; }
    public void setBreakStartAt(LocalDateTime breakStartAt) { this.breakStartAt = breakStartAt; }

    public LocalDateTime getBreakEndAt() { return breakEndAt; }
    public void setBreakEndAt(LocalDateTime breakEndAt) { this.breakEndAt = breakEndAt; }

    public Integer getTotalBreakMinutes() { return totalBreakMinutes; }
    public void setTotalBreakMinutes(Integer totalBreakMinutes) { this.totalBreakMinutes = totalBreakMinutes; }

    public Integer getNetWorkMinutes() { return netWorkMinutes; }
    public void setNetWorkMinutes(Integer netWorkMinutes) { this.netWorkMinutes = netWorkMinutes; }

    public Integer getNightWorkMinutes() { return nightWorkMinutes; }
    public void setNightWorkMinutes(Integer nightWorkMinutes) { this.nightWorkMinutes = nightWorkMinutes; }

    public Integer getOvertimeMinutes() { return overtimeMinutes; }
    public void setOvertimeMinutes(Integer overtimeMinutes) { this.overtimeMinutes = overtimeMinutes; }

    public String getClockInMethod() { return clockInMethod; }
    public void setClockInMethod(String clockInMethod) { this.clockInMethod = clockInMethod; }

    public String getAttendanceStatus() { return attendanceStatus; }
    public void setAttendanceStatus(String attendanceStatus) { this.attendanceStatus = attendanceStatus; }
}
