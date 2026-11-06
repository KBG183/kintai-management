package com.example.kintai.domain.shift.dto;

import java.util.List;

/**
 * シフト一括確定リクエストDTO
 * 店長が対象シフトを一括で承認・確定する際に使用
 */
public class ShiftBatchConfirmDto {

    /** 確定対象のシフトIDリスト */
    private List<Long> shiftIds;

    /** デフォルト付与の管理者メモ（任意） */
    private String ownerMemo;

    public ShiftBatchConfirmDto() {}

    public ShiftBatchConfirmDto(List<Long> shiftIds, String ownerMemo) {
        this.shiftIds = shiftIds;
        this.ownerMemo = ownerMemo;
    }

    public List<Long> getShiftIds() {
        return shiftIds;
    }

    public void setShiftIds(List<Long> shiftIds) {
        this.shiftIds = shiftIds;
    }

    public String getOwnerMemo() {
        return ownerMemo;
    }

    public void setOwnerMemo(String ownerMemo) {
        this.ownerMemo = ownerMemo;
    }
}
