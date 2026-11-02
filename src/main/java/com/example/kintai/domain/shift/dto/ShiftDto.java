package com.example.kintai.domain.shift.dto;

import java.time.LocalDate;
import java.time.LocalDateTime;

public class ShiftDto {
    private Long shiftId;
    private Long storeId;
    private Long storeStaffId;
    private String staffName;
    private LocalDate workDate;
    private LocalDateTime requestedStartAt;
    private LocalDateTime requestedEndAt;
    private LocalDateTime confirmedStartAt;
    private LocalDateTime confirmedEndAt;
    private Integer plannedBreakMinutes;
    private String status;
    private String staffMemo;
    private String ownerMemo;

    public ShiftDto() {}

    public Long getShiftId() { return shiftId; }
    public void setShiftId(Long shiftId) { this.shiftId = shiftId; }

    public Long getStoreId() { return storeId; }
    public void setStoreId(Long storeId) { this.storeId = storeId; }

    public Long getStoreStaffId() { return storeStaffId; }
    public void setStoreStaffId(Long storeStaffId) { this.storeStaffId = storeStaffId; }

    public String getStaffName() { return staffName; }
    public void setStaffName(String staffName) { this.staffName = staffName; }

    public LocalDate getWorkDate() { return workDate; }
    public void setWorkDate(LocalDate workDate) { this.workDate = workDate; }

    public LocalDateTime getRequestedStartAt() { return requestedStartAt; }
    public void setRequestedStartAt(LocalDateTime requestedStartAt) { this.requestedStartAt = requestedStartAt; }

    public LocalDateTime getRequestedEndAt() { return requestedEndAt; }
    public void setRequestedEndAt(LocalDateTime requestedEndAt) { this.requestedEndAt = requestedEndAt; }

    public LocalDateTime getConfirmedStartAt() { return confirmedStartAt; }
    public void setConfirmedStartAt(LocalDateTime confirmedStartAt) { this.confirmedStartAt = confirmedStartAt; }

    public LocalDateTime getConfirmedEndAt() { return confirmedEndAt; }
    public void setConfirmedEndAt(LocalDateTime confirmedEndAt) { this.confirmedEndAt = confirmedEndAt; }

    public Integer getPlannedBreakMinutes() { return plannedBreakMinutes; }
    public void setPlannedBreakMinutes(Integer plannedBreakMinutes) { this.plannedBreakMinutes = plannedBreakMinutes; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getStaffMemo() { return staffMemo; }
    public void setStaffMemo(String staffMemo) { this.staffMemo = staffMemo; }

    public String getOwnerMemo() { return ownerMemo; }
    public void setOwnerMemo(String ownerMemo) { this.ownerMemo = ownerMemo; }
}
