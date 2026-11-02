package com.example.kintai.domain.shift.dto;

public class ShiftConfirmDto {
    private String confirmedStartTime; // "17:00"
    private String confirmedEndTime;   // "23:30"
    private Integer plannedBreakMinutes; // null 가능 (자동 산정)
    private String status;              // "CONFIRMED" or "REJECTED"
    private String ownerMemo;

    public ShiftConfirmDto() {}

    public String getConfirmedStartTime() { return confirmedStartTime; }
    public void setConfirmedStartTime(String confirmedStartTime) { this.confirmedStartTime = confirmedStartTime; }

    public String getConfirmedEndTime() { return confirmedEndTime; }
    public void setConfirmedEndTime(String confirmedEndTime) { this.confirmedEndTime = confirmedEndTime; }

    public Integer getPlannedBreakMinutes() { return plannedBreakMinutes; }
    public void setPlannedBreakMinutes(Integer plannedBreakMinutes) { this.plannedBreakMinutes = plannedBreakMinutes; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getOwnerMemo() { return ownerMemo; }
    public void setOwnerMemo(String ownerMemo) { this.ownerMemo = ownerMemo; }
}
