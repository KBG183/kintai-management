package com.example.kintai.domain.shift.dto;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public class CalendarEventDto {
    private String id;
    private String title;
    private String start;
    private String end;
    private String color;
    private ExtendedProps extendedProps;

    public CalendarEventDto() {}

    public static class ExtendedProps {
        private Long shiftId;
        private Long storeStaffId;
        private String staffName;
        private String status;
        private Integer plannedBreakMinutes;
        private String staffMemo;
        private String ownerMemo;

        public ExtendedProps() {}

        public Long getShiftId() { return shiftId; }
        public void setShiftId(Long shiftId) { this.shiftId = shiftId; }
        public Long getStoreStaffId() { return storeStaffId; }
        public void setStoreStaffId(Long storeStaffId) { this.storeStaffId = storeStaffId; }
        public String getStaffName() { return staffName; }
        public void setStaffName(String staffName) { this.staffName = staffName; }
        public String getStatus() { return status; }
        public void setStatus(String status) { this.status = status; }
        public Integer getPlannedBreakMinutes() { return plannedBreakMinutes; }
        public void setPlannedBreakMinutes(Integer plannedBreakMinutes) { this.plannedBreakMinutes = plannedBreakMinutes; }
        public String getStaffMemo() { return staffMemo; }
        public void setStaffMemo(String staffMemo) { this.staffMemo = staffMemo; }
        public String getOwnerMemo() { return ownerMemo; }
        public void setOwnerMemo(String ownerMemo) { this.ownerMemo = ownerMemo; }
    }

    public static CalendarEventDto from(ShiftDto shift) {
        return from(shift, null, true);
    }

    /**
     * プライバシー保護・サーバーサイドマスキング対応
     * 他スタッフの個人メモは管理者(ROLE_OWNER/ROLE_MANAGER)または本人のみに限定して返却
     */
    public static CalendarEventDto from(ShiftDto shift, Long currentStaffId, boolean isManager) {
        CalendarEventDto dto = new CalendarEventDto();
        dto.setId(String.valueOf(shift.getShiftId()));

        DateTimeFormatter isoFormatter = DateTimeFormatter.ISO_LOCAL_DATE_TIME;
        DateTimeFormatter timeFormatter = DateTimeFormatter.ofPattern("HH:mm");

        String status = shift.getStatus();
        LocalDateTime eventStart;
        LocalDateTime eventEnd;

        // オーナー直接割り当てシフト（requested_*がNULL）の場合でもイベント区間がNULLにならないよう防衛
        if ("CONFIRMED".equalsIgnoreCase(status)) {
            eventStart = shift.getConfirmedStartAt() != null ? shift.getConfirmedStartAt() : shift.getRequestedStartAt();
            eventEnd = shift.getConfirmedEndAt() != null ? shift.getConfirmedEndAt() : shift.getRequestedEndAt();
            dto.setColor("#0d6efd"); // 青色（確定）
            String timeRange = (eventStart != null && eventEnd != null) 
                    ? " (" + eventStart.format(timeFormatter) + " - " + eventEnd.format(timeFormatter) + ")" 
                    : "";
            dto.setTitle("[確定] " + shift.getStaffName() + timeRange);
        } else if ("SUBMITTED".equalsIgnoreCase(status)) {
            eventStart = shift.getRequestedStartAt();
            eventEnd = shift.getRequestedEndAt();
            dto.setColor("#fd7e14"); // オレンジ色（希望）
            String timeRange = (eventStart != null && eventEnd != null) 
                    ? " (" + eventStart.format(timeFormatter) + " - " + eventEnd.format(timeFormatter) + ")" 
                    : "";
            dto.setTitle("[希望] " + shift.getStaffName() + timeRange);
        } else { // REJECTED など
            eventStart = shift.getRequestedStartAt() != null ? shift.getRequestedStartAt() : shift.getConfirmedStartAt();
            eventEnd = shift.getRequestedEndAt() != null ? shift.getRequestedEndAt() : shift.getConfirmedEndAt();
            dto.setColor("#6c757d"); // グレー
            dto.setTitle("[未割当] " + shift.getStaffName());
        }

        if (eventStart != null) dto.setStart(eventStart.format(isoFormatter));
        if (eventEnd != null) dto.setEnd(eventEnd.format(isoFormatter));

        ExtendedProps props = new ExtendedProps();
        props.setShiftId(shift.getShiftId());
        props.setStoreStaffId(shift.getStoreStaffId());
        props.setStaffName(shift.getStaffName());
        props.setStatus(shift.getStatus());
        props.setPlannedBreakMinutes(shift.getPlannedBreakMinutes());

        // サーバーサイド・プライバシー保護マスク
        boolean isSelf = (currentStaffId != null && currentStaffId.equals(shift.getStoreStaffId()));
        if (isManager || isSelf) {
            props.setStaffMemo(shift.getStaffMemo());
            props.setOwnerMemo(shift.getOwnerMemo());
        } else {
            props.setStaffMemo(null); // 他スタッフのメモはAPIペイロードから完全除外
            props.setOwnerMemo(null);
        }

        dto.setExtendedProps(props);
        return dto;
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getStart() { return start; }
    public void setStart(String start) { this.start = start; }
    public String getEnd() { return end; }
    public void setEnd(String end) { this.end = end; }
    public String getColor() { return color; }
    public void setColor(String color) { this.color = color; }
    public ExtendedProps getExtendedProps() { return extendedProps; }
    public void setExtendedProps(ExtendedProps extendedProps) { this.extendedProps = extendedProps; }
}
