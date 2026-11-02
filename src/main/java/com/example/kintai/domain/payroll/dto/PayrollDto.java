package com.example.kintai.domain.payroll.dto;

import java.time.LocalDate;
import java.time.LocalDateTime;

public class PayrollDto {
    private Long payrollId;
    private Long storeId;
    private Long storeStaffId;
    private String staffName;
    private String targetYearMonth;
    private LocalDate rangeStartDate;
    private LocalDate rangeEndDate;
    private Integer workDaysCount;
    private Integer totalWorkMinutes;
    private Integer totalNightMinutes;
    private Integer totalOvertimeMinutes;
    private Integer appliedHourlyWage;
    private Integer appliedTransportLimit;
    private Integer basePay;
    private Integer nightAllowance;
    private Integer overtimeAllowance;
    private Integer transportAllowance;
    private Integer grossPay;
    private String payrollStatus;
    private LocalDateTime confirmedAt;
    private LocalDateTime paidAt;

    public PayrollDto() {}

    public Long getPayrollId() { return payrollId; }
    public void setPayrollId(Long payrollId) { this.payrollId = payrollId; }

    public Long getStoreId() { return storeId; }
    public void setStoreId(Long storeId) { this.storeId = storeId; }

    public Long getStoreStaffId() { return storeStaffId; }
    public void setStoreStaffId(Long storeStaffId) { this.storeStaffId = storeStaffId; }

    public String getStaffName() { return staffName; }
    public void setStaffName(String staffName) { this.staffName = staffName; }

    public String getTargetYearMonth() { return targetYearMonth; }
    public void setTargetYearMonth(String targetYearMonth) { this.targetYearMonth = targetYearMonth; }

    public LocalDate getRangeStartDate() { return rangeStartDate; }
    public void setRangeStartDate(LocalDate rangeStartDate) { this.rangeStartDate = rangeStartDate; }

    public LocalDate getRangeEndDate() { return rangeEndDate; }
    public void setRangeEndDate(LocalDate rangeEndDate) { this.rangeEndDate = rangeEndDate; }

    public Integer getWorkDaysCount() { return workDaysCount; }
    public void setWorkDaysCount(Integer workDaysCount) { this.workDaysCount = workDaysCount; }

    public Integer getTotalWorkMinutes() { return totalWorkMinutes; }
    public void setTotalWorkMinutes(Integer totalWorkMinutes) { this.totalWorkMinutes = totalWorkMinutes; }

    public Integer getTotalNightMinutes() { return totalNightMinutes; }
    public void setTotalNightMinutes(Integer totalNightMinutes) { this.totalNightMinutes = totalNightMinutes; }

    public Integer getTotalOvertimeMinutes() { return totalOvertimeMinutes; }
    public void setTotalOvertimeMinutes(Integer totalOvertimeMinutes) { this.totalOvertimeMinutes = totalOvertimeMinutes; }

    public Integer getAppliedHourlyWage() { return appliedHourlyWage; }
    public void setAppliedHourlyWage(Integer appliedHourlyWage) { this.appliedHourlyWage = appliedHourlyWage; }

    public Integer getAppliedTransportLimit() { return appliedTransportLimit; }
    public void setAppliedTransportLimit(Integer appliedTransportLimit) { this.appliedTransportLimit = appliedTransportLimit; }

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

    public LocalDateTime getConfirmedAt() { return confirmedAt; }
    public void setConfirmedAt(LocalDateTime confirmedAt) { this.confirmedAt = confirmedAt; }

    public LocalDateTime getPaidAt() { return paidAt; }
    public void setPaidAt(LocalDateTime paidAt) { this.paidAt = paidAt; }
}
