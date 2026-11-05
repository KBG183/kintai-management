package com.example.kintai.domain.shift.mapper;

import com.example.kintai.domain.shift.dto.ShiftDto;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface ShiftMapper {

    List<ShiftDto> findShiftEvents(
            @Param("storeId") Long storeId,
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate
    );

    ShiftDto findById(@Param("shiftId") Long shiftId);

    int insertShift(ShiftDto shift);

    int updateShiftConfirm(ShiftDto shift);

    boolean existsOverlapShift(
            @Param("storeStaffId") Long storeStaffId,
            @Param("workDate") LocalDate workDate,
            @Param("startTime") LocalDateTime startTime,
            @Param("endTime") LocalDateTime endTime,
            @Param("excludeShiftId") Long excludeShiftId
    );

    // 排他制御: 同時更新防止のための悲観的ロック (SELECT ... FOR UPDATE)
    void lockStoreStaffForUpdate(@Param("storeStaffId") Long storeStaffId);

    /**
     * スタッフ本人による希望シフト申請の取消・削除 (IDOR防止および確定済削除禁止)
     */
    int deleteShiftRequest(
            @Param("shiftId") Long shiftId,
            @Param("storeStaffId") Long storeStaffId
    );

    /**
     * 特定スタッフのシフト一覧取得 (自店舗・自身のシフト確認用)
     */
    List<ShiftDto> findStaffShiftList(
            @Param("storeStaffId") Long storeStaffId,
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate
    );
}
