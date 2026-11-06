package com.example.kintai.domain.shift.dto;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * シフト予定・実績対照DTO
 * 確定シフト（計画）と打刻記録（実績）のリアルタイム照合結果を保持する
 */
public class ShiftComparisonDto {

    private Long shiftId;
    private Long storeStaffId;
    private String staffName;
    private LocalDate workDate;

    // --- シフト予定（計画） ---
    private LocalDateTime plannedStartAt;
    private LocalDateTime plannedEndAt;
    private Integer plannedBreakMinutes;
    private Long plannedWorkMinutes; // 実働予定（拘束 - 休憩）

    // --- 打刻記録（実績） ---
    private Long attendanceId;
    private LocalDateTime actualClockInAt;
    private LocalDateTime actualClockOutAt;
    private Integer actualBreakMinutes;
    private Long actualWorkMinutes;  // 実労働時間

    // --- 照合判定 ---
    /**
     * 対照判定ステータス:
     * - NORMAL: 正常（予定通り出退勤）
     * - LATE: 遅刻（予定開始時刻より遅れて出勤）
     * - EARLY_LEAVE: 早退（予定終了時刻より前に退勤）
     * - LATE_AND_EARLY: 遅刻かつ早退
     * - ABSENT: 欠勤（予定時刻経過後も未出勤）
     * - WORKING: 正常出勤し現在勤務中
     * - UNSCHEDULED: シフト予定外の打刻出勤
     */
    private String comparisonStatus;

    /** 遅刻分数（0以上の値） */
    private Long lateMinutes;

    /** 早退分数（0以上の値） */
    private Long earlyLeaveMinutes;

    /** 予定と実績の実労働時間差異（分単位、実績 - 予定） */
    private Long diffWorkMinutes;

    /** 状態説明ラベル（日本語表示用） */
    private String statusLabel;

    public ShiftComparisonDto() {}

    public Long getShiftId() {
        return shiftId;
    }

    public void setShiftId(Long shiftId) {
        this.shiftId = shiftId;
    }

    public Long getStoreStaffId() {
        return storeStaffId;
    }

    public void setStoreStaffId(Long storeStaffId) {
        this.storeStaffId = storeStaffId;
    }

    public String getStaffName() {
        return staffName;
    }

    public void setStaffName(String staffName) {
        this.staffName = staffName;
    }

    public LocalDate getWorkDate() {
        return workDate;
    }

    public void setWorkDate(LocalDate workDate) {
        this.workDate = workDate;
    }

    public LocalDateTime getPlannedStartAt() {
        return plannedStartAt;
    }

    public void setPlannedStartAt(LocalDateTime plannedStartAt) {
        this.plannedStartAt = plannedStartAt;
    }

    public LocalDateTime getPlannedEndAt() {
        return plannedEndAt;
    }

    public void setPlannedEndAt(LocalDateTime plannedEndAt) {
        this.plannedEndAt = plannedEndAt;
    }

    public Integer getPlannedBreakMinutes() {
        return plannedBreakMinutes;
    }

    public void setPlannedBreakMinutes(Integer plannedBreakMinutes) {
        this.plannedBreakMinutes = plannedBreakMinutes;
    }

    public Long getPlannedWorkMinutes() {
        return plannedWorkMinutes;
    }

    public void setPlannedWorkMinutes(Long plannedWorkMinutes) {
        this.plannedWorkMinutes = plannedWorkMinutes;
    }

    public Long getAttendanceId() {
        return attendanceId;
    }

    public void setAttendanceId(Long attendanceId) {
        this.attendanceId = attendanceId;
    }

    public LocalDateTime getActualClockInAt() {
        return actualClockInAt;
    }

    public void setActualClockInAt(LocalDateTime actualClockInAt) {
        this.actualClockInAt = actualClockInAt;
    }

    public LocalDateTime getActualClockOutAt() {
        return actualClockOutAt;
    }

    public void setActualClockOutAt(LocalDateTime actualClockOutAt) {
        this.actualClockOutAt = actualClockOutAt;
    }

    public Integer getActualBreakMinutes() {
        return actualBreakMinutes;
    }

    public void setActualBreakMinutes(Integer actualBreakMinutes) {
        this.actualBreakMinutes = actualBreakMinutes;
    }

    public Long getActualWorkMinutes() {
        return actualWorkMinutes;
    }

    public void setActualWorkMinutes(Long actualWorkMinutes) {
        this.actualWorkMinutes = actualWorkMinutes;
    }

    public String getComparisonStatus() {
        return comparisonStatus;
    }

    public void setComparisonStatus(String comparisonStatus) {
        this.comparisonStatus = comparisonStatus;
    }

    public Long getLateMinutes() {
        return lateMinutes;
    }

    public void setLateMinutes(Long lateMinutes) {
        this.lateMinutes = lateMinutes;
    }

    public Long getEarlyLeaveMinutes() {
        return earlyLeaveMinutes;
    }

    public void setEarlyLeaveMinutes(Long earlyLeaveMinutes) {
        this.earlyLeaveMinutes = earlyLeaveMinutes;
    }

    public Long getDiffWorkMinutes() {
        return diffWorkMinutes;
    }

    public void setDiffWorkMinutes(Long diffWorkMinutes) {
        this.diffWorkMinutes = diffWorkMinutes;
    }

    public String getStatusLabel() {
        return statusLabel;
    }

    public void setStatusLabel(String statusLabel) {
        this.statusLabel = statusLabel;
    }
}
