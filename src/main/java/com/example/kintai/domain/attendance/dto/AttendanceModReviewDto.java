package com.example.kintai.domain.attendance.dto;

public class AttendanceModReviewDto {
    private String action; // "APPROVE" or "REJECT"
    private String rejectReason;

    public AttendanceModReviewDto() {}

    public String getAction() { return action; }
    public void setAction(String action) { this.action = action; }

    public String getRejectReason() { return rejectReason; }
    public void setRejectReason(String rejectReason) { this.rejectReason = rejectReason; }
}
