package com.example.kintai.domain.payroll.dto;

import java.util.List;

public class PayrollDetailResponseDto {
    private Long payrollId;
    private String staffName;
    private String targetYearMonth;
    private Integer appliedHourlyWage;
    private PayrollSummary summary;
    private List<DailyAttendanceDetail> dailyAttendances;

    public PayrollDetailResponseDto() {}

    public static class PayrollSummary {
        private Integer workDaysCount;
        private Integer totalWorkMinutes;
        private Integer totalNightMinutes;
        private Integer totalOvertimeMinutes;
        private Integer basePay;
        private Integer nightAllowance;
        private Integer overtimeAllowance;
        private Integer transportAllowance;
        private Integer grossPay;
        private String payrollStatus;

        public PayrollSummary() {}

        public Integer getWorkDaysCount() { return workDaysCount; }
        public void setWorkDaysCount(Integer workDaysCount) { this.workDaysCount = workDaysCount; }
        public Integer getTotalWorkMinutes() { return totalWorkMinutes; }
        public void setTotalWorkMinutes(Integer totalWorkMinutes) { this.totalWorkMinutes = totalWorkMinutes; }
        public Integer getTotalNightMinutes() { return totalNightMinutes; }
        public void setTotalNightMinutes(Integer totalNightMinutes) { this.totalNightMinutes = totalNightMinutes; }
        public Integer getTotalOvertimeMinutes() { return totalOvertimeMinutes; }
        public void setTotalOvertimeMinutes(Integer totalOvertimeMinutes) { this.totalOvertimeMinutes = totalOvertimeMinutes; }
        public Integer getBasePay() { return basePay; }
        public void setBasePay(Integer basePay) { this.basePay = basePay; }
        public Integer getNightAllowance() { return nightAllowance; }
        public void setNightAllowance(Integer nightAllowance) { this.nightAllowance = nightAllowance; }
        public Integer getOvertimeAllowance() { return overtimeAllowance; }
        public void setOvertimeAllowance(Integer overtimeAllowance) { this.overtimeAllowance = overtimeAllowance; }
        public Integer getTransportAllowance() { return transportAllowance; }
        public void setTransportAllowance(Integer transportAllowance) { this.transportAllowance = transportAllowance; }
        public Integer getGrossPay() { return grossPay; }
        public void setGrossPay(Integer grossPay) { this.grossPay = grossPay; }
        public String getPayrollStatus() { return payrollStatus; }
        public void setPayrollStatus(String payrollStatus) { this.payrollStatus = payrollStatus; }
    }

    public static class DailyAttendanceDetail {
        private String workDate;
        private String clockInAt;
        private String clockOutAt;
        private Integer breakMinutes;
        private Integer netWorkMinutes;
        private Integer nightMinutes;
        private Integer overtimeMinutes;
        private Integer appliedHourlyWage;
        private Integer basePay;
        private Integer nightAllowance;
        private Integer overtimeAllowance;
        private Integer transportAmount;

        public DailyAttendanceDetail() {}

        public String getWorkDate() { return workDate; }
        public void setWorkDate(String workDate) { this.workDate = workDate; }
        public String getClockInAt() { return clockInAt; }
        public void setClockInAt(String clockInAt) { this.clockInAt = clockInAt; }
        public String getClockOutAt() { return clockOutAt; }
        public void setClockOutAt(String clockOutAt) { this.clockOutAt = clockOutAt; }
        public Integer getBreakMinutes() { return breakMinutes; }
        public void setBreakMinutes(Integer breakMinutes) { this.breakMinutes = breakMinutes; }
        public Integer getNetWorkMinutes() { return netWorkMinutes; }
        public void setNetWorkMinutes(Integer netWorkMinutes) { this.netWorkMinutes = netWorkMinutes; }
        public Integer getNightMinutes() { return nightMinutes; }
        public void setNightMinutes(Integer nightMinutes) { this.nightMinutes = nightMinutes; }
        public Integer getOvertimeMinutes() { return overtimeMinutes; }
        public void setOvertimeMinutes(Integer overtimeMinutes) { this.overtimeMinutes = overtimeMinutes; }
        public Integer getAppliedHourlyWage() { return appliedHourlyWage; }
        public void setAppliedHourlyWage(Integer appliedHourlyWage) { this.appliedHourlyWage = appliedHourlyWage; }
        public Integer getBasePay() { return basePay; }
        public void setBasePay(Integer basePay) { this.basePay = basePay; }
        public Integer getNightAllowance() { return nightAllowance; }
        public void setNightAllowance(Integer nightAllowance) { this.nightAllowance = nightAllowance; }
        public Integer getOvertimeAllowance() { return overtimeAllowance; }
        public void setOvertimeAllowance(Integer overtimeAllowance) { this.overtimeAllowance = overtimeAllowance; }
        public Integer getTransportAmount() { return transportAmount; }
        public void setTransportAmount(Integer transportAmount) { this.transportAmount = transportAmount; }
    }

    public Long getPayrollId() { return payrollId; }
    public void setPayrollId(Long payrollId) { this.payrollId = payrollId; }
    public String getStaffName() { return staffName; }
    public void setStaffName(String staffName) { this.staffName = staffName; }
    public String getTargetYearMonth() { return targetYearMonth; }
    public void setTargetYearMonth(String targetYearMonth) { this.targetYearMonth = targetYearMonth; }
    public Integer getAppliedHourlyWage() { return appliedHourlyWage; }
    public void setAppliedHourlyWage(Integer appliedHourlyWage) { this.appliedHourlyWage = appliedHourlyWage; }
    public PayrollSummary getSummary() { return summary; }
    public void setSummary(PayrollSummary summary) { this.summary = summary; }
    public List<DailyAttendanceDetail> getDailyAttendances() { return dailyAttendances; }
    public void setDailyAttendances(List<DailyAttendanceDetail> dailyAttendances) { this.dailyAttendances = dailyAttendances; }
}
