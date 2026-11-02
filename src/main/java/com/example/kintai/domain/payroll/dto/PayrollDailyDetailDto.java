package com.example.kintai.domain.payroll.dto;

import java.time.LocalDate;

public class PayrollDailyDetailDto {
    private Long payrollDailyDetailId;
    private Long payrollId;
    private Long attendanceId;
    private LocalDate workDate;
    private Integer appliedHourlyWage;
    private Integer totalBreakMinutes;
    private Integer netWorkMinutes;
    private Integer nightWorkMinutes;
    private Integer overtimeMinutes;
    private Integer basePay;
    private Integer nightAllowance;
    private Integer overtimeAllowance;
    private Integer transportAmount;

    public PayrollDailyDetailDto() {}

    public Long getPayrollDailyDetailId() { return payrollDailyDetailId; }
    public void setPayrollDailyDetailId(Long payrollDailyDetailId) { this.payrollDailyDetailId = payrollDailyDetailId; }

    public Long getPayrollId() { return payrollId; }
    public void setPayrollId(Long payrollId) { this.payrollId = payrollId; }

    public Long getAttendanceId() { return attendanceId; }
    public void setAttendanceId(Long attendanceId) { this.attendanceId = attendanceId; }

    public LocalDate getWorkDate() { return workDate; }
    public void setWorkDate(LocalDate workDate) { this.workDate = workDate; }

    public Integer getAppliedHourlyWage() { return appliedHourlyWage; }
    public void setAppliedHourlyWage(Integer appliedHourlyWage) { this.appliedHourlyWage = appliedHourlyWage; }

    public Integer getTotalBreakMinutes() { return totalBreakMinutes; }
    public void setTotalBreakMinutes(Integer totalBreakMinutes) { this.totalBreakMinutes = totalBreakMinutes; }

    public Integer getNetWorkMinutes() { return netWorkMinutes; }
    public void setNetWorkMinutes(Integer netWorkMinutes) { this.netWorkMinutes = netWorkMinutes; }

    public Integer getNightWorkMinutes() { return nightWorkMinutes; }
    public void setNightWorkMinutes(Integer nightWorkMinutes) { this.nightWorkMinutes = nightWorkMinutes; }

    public Integer getOvertimeMinutes() { return overtimeMinutes; }
    public void setOvertimeMinutes(Integer overtimeMinutes) { this.overtimeMinutes = overtimeMinutes; }

    public Integer getBasePay() { return basePay; }
    public void setBasePay(Integer basePay) { this.basePay = basePay; }

    public Integer getNightAllowance() { return nightAllowance; }
    public void setNightAllowance(Integer nightAllowance) { this.nightAllowance = nightAllowance; }

    public Integer getOvertimeAllowance() { return overtimeAllowance; }
    public void setOvertimeAllowance(Integer overtimeAllowance) { this.overtimeAllowance = overtimeAllowance; }

    public Integer getTransportAmount() { return transportAmount; }
    public void setTransportAmount(Integer transportAmount) { this.transportAmount = transportAmount; }
}
