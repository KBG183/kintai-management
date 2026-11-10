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

    // 該当勤務日の給与が確定済（CONFIRMED / PAID）か確認
    boolean isPayrollConfirmedForDate(
            @Param("storeStaffId") Long storeStaffId,
            @Param("workDate") LocalDate workDate
    );

    // 同時更新防止のための悲観的ロック (SELECT ... FOR UPDATE)
    void lockStoreStaffForUpdate(@Param("storeStaffId") Long storeStaffId);

    // 店舗スタッフ所属情報から店舗IDを取得
    Long findStoreIdByStoreStaffId(@Param("storeStaffId") Long storeStaffId);

    Long findConfirmedShiftIdForDate(
            @Param("storeStaffId") Long storeStaffId,
            @Param("workDate") LocalDate workDate
    );

    List<AttendanceDto> findCompletedAttendancesForStaffAndPeriod(
            @Param("storeStaffId") Long storeStaffId,
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate
    );

    // 勤怠修正申請関連
    /**
     * 店舗および指定勤務日の全スタッフ勤怠実績取得（シフト・勤怠リアルタイム対照用）
     */
    List<AttendanceDto> findAttendancesByStoreAndDate(
            @Param("storeId") Long storeId,
            @Param("workDate") LocalDate workDate
    );

    /**
     * 店舗スタッフIDに基づくスタッフ氏名取得（労働基準法明細表示用）
     */
    String findStaffNameByStoreStaffId(@Param("storeStaffId") Long storeStaffId);

    /**
     * 勤務日時点の契約時給取得
     */
    Integer findHourlyWageForStaffOnDate(
            @Param("storeStaffId") Long storeStaffId,
            @Param("workDate") LocalDate workDate
    );

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

    /**
     * 複数申請IDに対する休憩区間一括取得（N+1問題の解消）
     */
    List<AttendanceBreakDto> findModBreaksByRequestIds(@Param("requestIds") List<Long> requestIds);

    int updateModRequestReview(
            @Param("modRequestId") Long modRequestId,
            @Param("status") String status,
            @Param("reviewerId") Long reviewerId,
            @Param("rejectReason") String rejectReason,
            @Param("reviewedAt") LocalDateTime reviewedAt
    );

    /**
     * 勤怠ステータスの単体更新
     */
    int updateAttendanceStatus(
            @Param("attendanceId") Long attendanceId,
            @Param("attendanceStatus") String attendanceStatus
    );

    /**
     * 同一スタッフにおける勤務時間の重複件数をカウント（勤務時間重複防止）
     */
    int countOverlappingAttendances(
            @Param("storeStaffId") Long storeStaffId,
            @Param("clockInAt") LocalDateTime clockInAt,
            @Param("clockOutAt") LocalDateTime clockOutAt,
            @Param("excludeAttendanceId") Long excludeAttendanceId
    );

    /**
     * 事後新規登録された勤怠IDを修正申請レコードに紐付け
     */
    int updateModRequestAttendanceId(
            @Param("modRequestId") Long modRequestId,
            @Param("attendanceId") Long attendanceId
    );

    /**
     * 同一スタッフ・同一勤務日の先行完了勤務の実労働時間合計（分）を取得（分割勤務中抜け対応）
     */
    int sumPriorNetWorkMinutesOnDate(
            @Param("storeStaffId") Long storeStaffId,
            @Param("workDate") LocalDate workDate,
            @Param("excludeAttendanceId") Long excludeAttendanceId,
            @Param("beforeTime") LocalDateTime beforeTime
    );

    /**
     * 同一スタッフ・同一勤務日の先行完了勤務の実労働時間合計（秒）を取得（秒精度・中抜け対応）
     */
    long sumPriorNetWorkSecondsOnDate(
            @Param("storeStaffId") Long storeStaffId,
            @Param("workDate") LocalDate workDate,
            @Param("excludeAttendanceId") Long excludeAttendanceId,
            @Param("beforeTime") LocalDateTime beforeTime
    );
}
