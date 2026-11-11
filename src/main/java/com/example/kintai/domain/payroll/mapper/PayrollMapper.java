package com.example.kintai.domain.payroll.mapper;

import com.example.kintai.domain.payroll.dto.PayrollDailyDetailDto;
import com.example.kintai.domain.payroll.dto.PayrollDto;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface PayrollMapper {

    List<PayrollDto> findPayrollsByStoreAndMonth(
            @Param("storeId") Long storeId,
            @Param("targetYearMonth") String targetYearMonth
    );

    PayrollDto findById(@Param("payrollId") Long payrollId);

    // 排他制御: 単一給与確定時の同時更新防止用悲観的ロック (SELECT ... FOR UPDATE)
    PayrollDto findByIdForUpdate(@Param("payrollId") Long payrollId);

    PayrollDto findByStaffAndMonth(
            @Param("storeStaffId") Long storeStaffId,
            @Param("targetYearMonth") String targetYearMonth
    );

    // 排他制御: 給与再計算・更新時のレコード競合防止用悲観的ロック (SELECT ... FOR UPDATE)
    PayrollDto findByStaffAndMonthForUpdate(
            @Param("storeStaffId") Long storeStaffId,
            @Param("targetYearMonth") String targetYearMonth
    );

    int upsertPayroll(PayrollDto payroll);

    int insertPayrollDailyDetail(PayrollDailyDetailDto detail);

    List<PayrollDailyDetailDto> findDailyDetailsByPayrollId(@Param("payrollId") Long payrollId);

    int deleteDailyDetailsByPayrollId(@Param("payrollId") Long payrollId);

    int updatePayrollStatusConfirm(
            @Param("payrollId") Long payrollId,
            @Param("confirmedAt") LocalDateTime confirmedAt
    );

    // 店舗全体の月次給与一括確定 (DRAFT -> CONFIRMED)
    int updateAllPayrollStatusConfirm(
            @Param("storeId") Long storeId,
            @Param("targetYearMonth") String targetYearMonth,
            @Param("confirmedAt") LocalDateTime confirmedAt
    );

    int countPayrollsByStoreAndMonthAndStatus(
            @Param("storeId") Long storeId,
            @Param("targetYearMonth") String targetYearMonth,
            @Param("payrollStatus") String payrollStatus
    );

    int countUnresolvedAttendance(
            @Param("storeStaffId") Long storeStaffId,
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate
    );

    Integer getPayCutoffDayByStoreId(@Param("storeId") Long storeId);

    Integer findHourlyWageForStaffOnDate(
            @Param("storeStaffId") Long storeStaffId,
            @Param("workDate") LocalDate workDate
    );

    Integer findTransportLimitForStaffOnDate(
            @Param("storeStaffId") Long storeStaffId,
            @Param("date") LocalDate date
    );

    Integer findDailyTransportFeeForStaffOnDate(
            @Param("storeStaffId") Long storeStaffId,
            @Param("date") LocalDate date
    );

    // 排他制御: 同時更新防止のための悲観的ロック (SELECT ... FOR UPDATE)
    void lockStoreStaffForUpdate(@Param("storeStaffId") Long storeStaffId);

    // 店舗全体の月次給与一括ロック (SELECT ... FOR UPDATE)
    List<PayrollDto> lockStorePayrollsForUpdate(
            @Param("storeId") Long storeId,
            @Param("targetYearMonth") String targetYearMonth
    );

    List<Long> findActiveStaffIdsByStoreId(@Param("storeId") Long storeId);

    List<Long> findDraftStaffIdsByStoreAndMonth(
            @Param("storeId") Long storeId,
            @Param("targetYearMonth") String targetYearMonth
    );
}
