package com.example.kintai.domain.attendance.dto;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 日本労働基準法第37条に基づく勤怠時間・割増賃金精密内訳DTO。
 * 所定内通常、所定内深夜（125%）、法定外通常残業（125%）、法定外深夜残業（150%重畳割増）を分単位および円単位で保持する。
 */
public class LaborDetailDto {

    private Long attendanceId;
    private Long storeStaffId;
    private String staffName;
    private LocalDate workDate;
    private LocalDateTime clockInAt;
    private LocalDateTime clockOutAt;

    // 時間区分（分単位）
    private int totalStayMinutes;
    private int totalBreakMinutes;
    private int netWorkMinutes;
    private int regularWorkMinutes;
    private int regularNightWorkMinutes;
    private int overtimeWorkMinutes;
    private int nightOvertimeWorkMinutes;
    private int totalNightMinutes;
    private int totalOvertimeMinutes;

    // 金額区分（円単位、1円未満四捨五入）
    private int appliedHourlyWage;
    private int regularPay;
    private int regularNightPay;
    private int overtimePay;
    private int nightOvertimePay;
    private int basePay;
    private int nightAllowance;
    private int overtimeAllowance;
    private int totalPay;

    public LaborDetailDto() {}

    public Long getAttendanceId() { return attendanceId; }
    public void setAttendanceId(Long attendanceId) { this.attendanceId = attendanceId; }

    public Long getStoreStaffId() { return storeStaffId; }
    public void setStoreStaffId(Long storeStaffId) { this.storeStaffId = storeStaffId; }

    public String getStaffName() { return staffName; }
    public void setStaffName(String staffName) { this.staffName = staffName; }

    public LocalDate getWorkDate() { return workDate; }
    public void setWorkDate(LocalDate workDate) { this.workDate = workDate; }

    public LocalDateTime getClockInAt() { return clockInAt; }
    public void setClockInAt(LocalDateTime clockInAt) { this.clockInAt = clockInAt; }

    public LocalDateTime getClockOutAt() { return clockOutAt; }
    public void setClockOutAt(LocalDateTime clockOutAt) { this.clockOutAt = clockOutAt; }

    public int getTotalStayMinutes() { return totalStayMinutes; }
    public void setTotalStayMinutes(int totalStayMinutes) { this.totalStayMinutes = totalStayMinutes; }

    public int getTotalBreakMinutes() { return totalBreakMinutes; }
    public void setTotalBreakMinutes(int totalBreakMinutes) { this.totalBreakMinutes = totalBreakMinutes; }

    public int getNetWorkMinutes() { return netWorkMinutes; }
    public void setNetWorkMinutes(int netWorkMinutes) { this.netWorkMinutes = netWorkMinutes; }

    public int getRegularWorkMinutes() { return regularWorkMinutes; }
    public void setRegularWorkMinutes(int regularWorkMinutes) { this.regularWorkMinutes = regularWorkMinutes; }

    public int getRegularNightWorkMinutes() { return regularNightWorkMinutes; }
    public void setRegularNightWorkMinutes(int regularNightWorkMinutes) { this.regularNightWorkMinutes = regularNightWorkMinutes; }

    public int getOvertimeWorkMinutes() { return overtimeWorkMinutes; }
    public void setOvertimeWorkMinutes(int overtimeWorkMinutes) { this.overtimeWorkMinutes = overtimeWorkMinutes; }

    public int getNightOvertimeWorkMinutes() { return nightOvertimeWorkMinutes; }
    public void setNightOvertimeWorkMinutes(int nightOvertimeWorkMinutes) { this.nightOvertimeWorkMinutes = nightOvertimeWorkMinutes; }

    public int getTotalNightMinutes() { return totalNightMinutes; }
    public void setTotalNightMinutes(int totalNightMinutes) { this.totalNightMinutes = totalNightMinutes; }

    public int getTotalOvertimeMinutes() { return totalOvertimeMinutes; }
    public void setTotalOvertimeMinutes(int totalOvertimeMinutes) { this.totalOvertimeMinutes = totalOvertimeMinutes; }

    public int getAppliedHourlyWage() { return appliedHourlyWage; }
    public void setAppliedHourlyWage(int appliedHourlyWage) { this.appliedHourlyWage = appliedHourlyWage; }

    public int getRegularPay() { return regularPay; }
    public void setRegularPay(int regularPay) { this.regularPay = regularPay; }

    public int getRegularNightPay() { return regularNightPay; }
    public void setRegularNightPay(int regularNightPay) { this.regularNightPay = regularNightPay; }

    public int getOvertimePay() { return overtimePay; }
    public void setOvertimePay(int overtimePay) { this.overtimePay = overtimePay; }

    public int getNightOvertimePay() { return nightOvertimePay; }
    public void setNightOvertimePay(int nightOvertimePay) { this.nightOvertimePay = nightOvertimePay; }

    public int getBasePay() { return basePay; }
    public void setBasePay(int basePay) { this.basePay = basePay; }

    public int getNightAllowance() { return nightAllowance; }
    public void setNightAllowance(int nightAllowance) { this.nightAllowance = nightAllowance; }

    public int getOvertimeAllowance() { return overtimeAllowance; }
    public void setOvertimeAllowance(int overtimeAllowance) { this.overtimeAllowance = overtimeAllowance; }

    public int getTotalPay() { return totalPay; }
    public void setTotalPay(int totalPay) { this.totalPay = totalPay; }
}
