package com.example.kintai.domain.shift.dto;

import java.time.LocalDate;

public class ShiftRequestDto {
    private Long storeId;
    private Long storeStaffId;
    private LocalDate workDate;
    private String requestedStartTime; // "17:00"
    private String requestedEndTime;   // "23:30"
    private String staffMemo;

    public ShiftRequestDto() {}

    public Long getStoreId() { return storeId; }
    public void setStoreId(Long storeId) { this.storeId = storeId; }

    public Long getStoreStaffId() { return storeStaffId; }
    public void setStoreStaffId(Long storeStaffId) { this.storeStaffId = storeStaffId; }

    public LocalDate getWorkDate() { return workDate; }
    public void setWorkDate(LocalDate workDate) { this.workDate = workDate; }

    public String getRequestedStartTime() { return requestedStartTime; }
    public void setRequestedStartTime(String requestedStartTime) { this.requestedStartTime = requestedStartTime; }

    public String getRequestedEndTime() { return requestedEndTime; }
    public void setRequestedEndTime(String requestedEndTime) { this.requestedEndTime = requestedEndTime; }

    public String getStaffMemo() { return staffMemo; }
    public void setStaffMemo(String staffMemo) { this.staffMemo = staffMemo; }
}
