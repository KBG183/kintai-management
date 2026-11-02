package com.example.kintai.domain.payroll.dto;

public class PayrollCalculateRequestDto {
    private Long storeId;
    private String targetYearMonth; // "2026-10"

    public PayrollCalculateRequestDto() {}

    public Long getStoreId() { return storeId; }
    public void setStoreId(Long storeId) { this.storeId = storeId; }

    public String getTargetYearMonth() { return targetYearMonth; }
    public void setTargetYearMonth(String targetYearMonth) { this.targetYearMonth = targetYearMonth; }
}
