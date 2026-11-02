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

    // [F04 방어]: 동시성 충돌 방지를 위한 비관적 락 (SELECT ... FOR UPDATE)
    void lockStoreStaffForUpdate(@Param("storeStaffId") Long storeStaffId);
}
