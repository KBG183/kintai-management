package com.example.kintai.domain.payroll.dto;

public class PayrollConfirmRequestDto {
    private Integer expectedGrossPay; // 점주가 화면에서 확인한 총 지급액 스냅샷
    private Integer expectedTotalPay;

    public PayrollConfirmRequestDto() {}

    public Integer getExpectedGrossPay() { return expectedGrossPay; }
    public void setExpectedGrossPay(Integer expectedGrossPay) { this.expectedGrossPay = expectedGrossPay; }

    public Integer getExpectedTotalPay() { return expectedTotalPay; }
    public void setExpectedTotalPay(Integer expectedTotalPay) { this.expectedTotalPay = expectedTotalPay; }
}
