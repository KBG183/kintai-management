package com.example.kintai.domain.attendance.mapper;

import com.example.kintai.domain.attendance.dto.AttendanceBreakDto;
import com.example.kintai.domain.attendance.dto.AttendanceDto;
import com.example.kintai.domain.attendance.dto.AttendanceModRequestDto;
import com.example.kintai.domain.attendance.dto.AttendanceModResponseDto;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface AttendanceMapper {

    AttendanceDto findActiveAttendance(@Param("storeStaffId") Long storeStaffId);

    AttendanceDto findById(@Param("attendanceId") Long attendanceId);

    int insertAttendance(AttendanceDto attendance);

    int updateAttendance(AttendanceDto attendance);

    int insertBreak(AttendanceBreakDto breakDto);

    List<AttendanceBreakDto> findBreaksByAttendanceId(@Param("attendanceId") Long attendanceId);

    int deleteBreaksByAttendanceId(@Param("attendanceId") Long attendanceId);

    // [F01 방어]: 해당 근무일에 귀속된 급여가 이미 CONFIRMED 또는 PAID 상태인지 확인
    boolean isPayrollConfirmedForDate(
            @Param("storeStaffId") Long storeStaffId,
            @Param("workDate") LocalDate workDate
    );

    // [F04 방어]: 동시성 충돌 방지를 위한 비관적 락 (SELECT ... FOR UPDATE)
    void lockStoreStaffForUpdate(@Param("storeStaffId") Long storeStaffId);

    Long findConfirmedShiftIdForDate(
            @Param("storeStaffId") Long storeStaffId,
            @Param("workDate") LocalDate workDate
    );

    List<AttendanceDto> findCompletedAttendancesForStaffAndPeriod(
            @Param("storeStaffId") Long storeStaffId,
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate
    );

    // 근태 수정 신청 관련
    int insertModRequest(AttendanceModRequestDto modRequest);

    int insertModBreak(
            @Param("modRequestId") Long modRequestId,
            @Param("breakSetType") String breakSetType,
            @Param("startAt") LocalDateTime startAt,
            @Param("endAt") LocalDateTime endAt,
            @Param("duration") int duration
    );

    List<AttendanceModResponseDto> findModRequestsByStoreId(
            @Param("storeId") Long storeId,
            @Param("status") String status
    );

    AttendanceModResponseDto findModRequestById(@Param("modRequestId") Long modRequestId);

    List<AttendanceBreakDto> findModBreaksByRequestId(
            @Param("modRequestId") Long modRequestId,
            @Param("breakSetType") String breakSetType
    );

    int updateModRequestReview(
            @Param("modRequestId") Long modRequestId,
            @Param("status") String status,
            @Param("reviewerId") Long reviewerId,
            @Param("rejectReason") String rejectReason,
            @Param("reviewedAt") LocalDateTime reviewedAt
    );
}
