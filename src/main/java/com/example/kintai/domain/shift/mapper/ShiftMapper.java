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

    /**
     * 店舗内の申請中（未確定）シフト一覧取得
     */
    List<ShiftDto> findPendingShifts(
            @Param("storeId") Long storeId,
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate
    );

    /**
     * 店舗内の未確定シフト件数カウント
     */
    int countPendingShifts(@Param("storeId") Long storeId);

    /**
     * 特定勤務日の確定シフト一覧取得（実績打刻照合エンジン用）
     */
    List<ShiftDto> findConfirmedShiftsByDate(
            @Param("storeId") Long storeId,
            @Param("workDate") LocalDate workDate
    );

    /**
     * シフト状態の単件更新（店舗IDOR防御付き却下・承認処理）
     */
    int updateShiftStatus(
            @Param("shiftId") Long shiftId,
            @Param("storeId") Long storeId,
            @Param("status") String status,
            @Param("ownerMemo") String ownerMemo
    );

    /**
     * 複数シフトの一括確定処理 (申請時間を確定時間として一括昇格)
     */
    int batchConfirmShifts(
            @Param("storeId") Long storeId,
            @Param("shiftIds") List<Long> shiftIds,
            @Param("ownerMemo") String ownerMemo
    );
}
