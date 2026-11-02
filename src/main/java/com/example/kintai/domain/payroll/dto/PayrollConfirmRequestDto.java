package com.example.kintai.domain.payroll.dto;

public class PayrollConfirmRequestDto {
    private Integer expectedGrossPay; // 店長が画面上で確認した総支給額のスナップショット
    private Integer expectedTotalPay;

    public PayrollConfirmRequestDto() {}

    public Integer getExpectedGrossPay() { return expectedGrossPay; }
    public void setExpectedGrossPay(Integer expectedGrossPay) { this.expectedGrossPay = expectedGrossPay; }

    public Integer getExpectedTotalPay() { return expectedTotalPay; }
    public void setExpectedTotalPay(Integer expectedTotalPay) { this.expectedTotalPay = expectedTotalPay; }
}
