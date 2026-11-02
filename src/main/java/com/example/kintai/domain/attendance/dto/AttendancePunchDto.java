package com.example.kintai.domain.attendance.dto;

public class AttendancePunchDto {
    private Long storeId;
    private Long storeStaffId;
    private String qrToken;
    private String punchType; // CLOCK_IN, CLOCK_OUT, BREAK_START, BREAK_END

    public AttendancePunchDto() {}

    public Long getStoreId() { return storeId; }
    public void setStoreId(Long storeId) { this.storeId = storeId; }

    public Long getStoreStaffId() { return storeStaffId; }
    public void setStoreStaffId(Long storeStaffId) { this.storeStaffId = storeStaffId; }

    public String getQrToken() { return qrToken; }
    public void setQrToken(String qrToken) { this.qrToken = qrToken; }

    public String getPunchType() { return punchType; }
    public void setPunchType(String punchType) { this.punchType = punchType; }
}
