package com.example.kintai.domain.payroll.service;

import com.example.kintai.domain.attendance.dto.AttendanceDto;
import com.example.kintai.domain.attendance.mapper.AttendanceMapper;
import com.example.kintai.domain.payroll.dto.*;
import com.example.kintai.domain.payroll.mapper.PayrollMapper;
import com.example.kintai.domain.payroll.util.PayrollCalculator;
import com.example.kintai.domain.payroll.util.PayrollExcelExporter;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Service
@Transactional(readOnly = true)
public class PayrollService {

    private final PayrollMapper payrollMapper;
    private final AttendanceMapper attendanceMapper;
    private final com.example.kintai.domain.store.service.StoreAuthorizationService storeAuthorizationService;

    public PayrollService(PayrollMapper payrollMapper, AttendanceMapper attendanceMapper, com.example.kintai.domain.store.service.StoreAuthorizationService storeAuthorizationService) {
        this.payrollMapper = payrollMapper;
        this.attendanceMapper = attendanceMapper;
        this.storeAuthorizationService = storeAuthorizationService;
    }

    public List<PayrollDto> getPayrollsByStoreAndMonth(Long storeId, String targetYearMonth) {
        return payrollMapper.findPayrollsByStoreAndMonth(storeId, targetYearMonth);
    }

    /**
     * 月別給与の一括集計および計算 (Upsert)
     */
    @Transactional
    public void calculateMonthlyPayroll(Long storeId, String targetYearMonth) {
        Integer cutoffDay = payrollMapper.getPayCutoffDayByStoreId(storeId);
        int payCutoffDay = (cutoffDay != null) ? cutoffDay : 99;

        PayrollCalculator.DateRange range = PayrollCalculator.resolveDateRange(targetYearMonth, payCutoffDay);
        LocalDate startDate = range.startDate();
        LocalDate endDate = range.endDate();

        List<Long> staffIds = payrollMapper.findActiveStaffIdsByStoreId(storeId);

        for (Long staffId : staffIds) {
            calculatePayrollForStaff(storeId, staffId, targetYearMonth, startDate, endDate);
        }
    }

    @Transactional
    public PayrollDto calculatePayrollForStaff(Long storeId, Long staffId, String targetYearMonth, LocalDate startDate, LocalDate endDate) {
        // 排他制御: 同時計算競合を防止する悲観的ロック (スタッフ単位)
        payrollMapper.lockStoreStaffForUpdate(staffId);

        // 確定済み給与の不変性保護 (CONFIRMED / PAID の場合は再計算・上書きを遮断, 行ロック付き照会)
        PayrollDto existingPayroll = payrollMapper.findByStaffAndMonthForUpdate(staffId, targetYearMonth);
        if (existingPayroll != null && !"DRAFT".equals(existingPayroll.getPayrollStatus())) {
            throw new IllegalStateException("指定年月 (" + targetYearMonth + ") の給与は既に確定済み(" +
                    existingPayroll.getPayrollStatus() + ") のため、再計算できません。(給与不変性保護)");
        }

        // 未処理勤怠の確認（打刻途中や未承認申請がある場合は計算中断）
        int unresolvedCount = payrollMapper.countUnresolvedAttendance(staffId, startDate, endDate);
        if (unresolvedCount > 0) {
            throw new IllegalStateException("未承認または打刻途中の勤怠が存在するため、給与計算を中断しました。(職員ID: " + staffId + ")");
        }

        List<AttendanceDto> attendances = attendanceMapper.findCompletedAttendancesForStaffAndPeriod(staffId, startDate, endDate);

        // 通勤手当上限額（1回の精算あたりの上限）の取得
        Integer transportLimitVal = payrollMapper.findTransportLimitForStaffOnDate(staffId, endDate);
        int transportLimit = (transportLimitVal != null) ? transportLimitVal : 15000;

        int totalWorkMinutes = 0;
        int totalNightMinutes = 0;
        int totalOvertimeMinutes = 0;
        int totalBasePay = 0;
        int totalNightAllowance = 0;
        int totalOvertimeAllowance = 0;
        int totalTransportAllowance = 0;

        Set<Integer> uniqueWages = new HashSet<>();
        Set<LocalDate> workedDates = new HashSet<>();

        List<PayrollDailyDetailDto> detailsToInsert = new ArrayList<>();

        for (AttendanceDto att : attendances) {
            // 勤怠データの負値・異常値バリデーション (F-07 堅牢性保証)
            if (att.getNetWorkMinutes() < 0 || att.getNightWorkMinutes() < 0 || att.getOvertimeMinutes() < 0 || att.getTotalBreakMinutes() < 0) {
                throw new IllegalStateException("勤務時間または休憩時間に負の値が含まれているため、給与計算を中断しました。(勤怠ID: " + att.getAttendanceId() + ")");
            }

            LocalDate workDate = att.getWorkDate();
            Integer hourlyWageVal = payrollMapper.findHourlyWageForStaffOnDate(staffId, workDate);
            if (hourlyWageVal == null || hourlyWageVal <= 0) {
                throw new IllegalStateException("勤務日 (" + workDate + ") に有効な契約時給データが存在しません。");
            }
            int hourlyWage = hourlyWageVal;
            uniqueWages.add(hourlyWage);

            // 2026年東京都最低賃金 (1,280円) 割れを防止
            if (hourlyWage < 1280) {
                throw new IllegalStateException("契約時給 (" + hourlyWage + "円) が法定最低賃金 (1,280円) を下回っています。");
            }

            // 日別計算（端数は HALF_UP で厳密に丸め）
            PayrollCalculator.DailyPayResult dailyPay = PayrollCalculator.calculateDailyPay(
                    hourlyWage,
                    att.getNetWorkMinutes(),
                    att.getNightWorkMinutes(),
                    att.getOvertimeMinutes()
            );

            // 1日1回通勤手当支給および月額上限枠内での配分
            int transportAmount = 0;
            if (!workedDates.contains(workDate)) {
                workedDates.add(workDate);
                Integer dailyTransportVal = payrollMapper.findDailyTransportFeeForStaffOnDate(staffId, workDate);
                int dailyTransport = (dailyTransportVal != null) ? dailyTransportVal : 0;
                int remainingLimit = Math.max(0, transportLimit - totalTransportAllowance);
                transportAmount = Math.min(dailyTransport, remainingLimit);
                totalTransportAllowance += transportAmount;
            }

            totalWorkMinutes += att.getNetWorkMinutes();
            totalNightMinutes += att.getNightWorkMinutes();
            totalOvertimeMinutes += att.getOvertimeMinutes();
            totalBasePay += dailyPay.basePay();
            totalNightAllowance += dailyPay.nightAllowance();
            totalOvertimeAllowance += dailyPay.overtimeAllowance();

            PayrollDailyDetailDto detail = new PayrollDailyDetailDto();
            detail.setAttendanceId(att.getAttendanceId());
            detail.setWorkDate(workDate);
            detail.setAppliedHourlyWage(hourlyWage);
            detail.setTotalBreakMinutes(att.getTotalBreakMinutes());
            detail.setNetWorkMinutes(att.getNetWorkMinutes());
            detail.setNightWorkMinutes(att.getNightWorkMinutes());
            detail.setOvertimeMinutes(att.getOvertimeMinutes());
            detail.setBasePay(dailyPay.basePay());
            detail.setNightAllowance(dailyPay.nightAllowance());
            detail.setOvertimeAllowance(dailyPay.overtimeAllowance());
            detail.setTransportAmount(transportAmount);

            detailsToInsert.add(detail);
        }

        int grossPay = totalBasePay + totalNightAllowance + totalOvertimeAllowance + totalTransportAllowance;
        Integer appliedHourlyWage = (uniqueWages.size() == 1) ? uniqueWages.iterator().next() : null;

        PayrollDto payroll = new PayrollDto();
        payroll.setStoreId(storeId);
        payroll.setStoreStaffId(staffId);
        payroll.setTargetYearMonth(targetYearMonth);
        payroll.setRangeStartDate(startDate);
        payroll.setRangeEndDate(endDate);
        payroll.setWorkDaysCount(workedDates.size());
        payroll.setTotalWorkMinutes(totalWorkMinutes);
        payroll.setTotalNightMinutes(totalNightMinutes);
        payroll.setTotalOvertimeMinutes(totalOvertimeMinutes);
        payroll.setAppliedHourlyWage(appliedHourlyWage);
        payroll.setAppliedTransportLimit(transportLimit);
        payroll.setBasePay(totalBasePay);
        payroll.setNightAllowance(totalNightAllowance);
        payroll.setOvertimeAllowance(totalOvertimeAllowance);
        payroll.setTransportAllowance(totalTransportAllowance);
        payroll.setGrossPay(grossPay);
        payroll.setPayrollStatus("DRAFT");

        // 【通達 昭和62年(63年) 基発第150号 法定月間端数処理の適用】
        // 単一契約時給の場合は、月間割増手当総額の法定端数処理（30分境界丸め＋手当別50銭四捨五入）を反映
        if (appliedHourlyWage != null) {
            StatutoryPayrollResult statutoryResult = applyStatutoryMonthlyRounding(payroll);
            // 法定端数処理結果を手当額および総支給額に反映
            payroll.setOvertimeAllowance(statutoryResult.statutoryOvertimeAllowance());
            payroll.setNightAllowance(statutoryResult.statutoryNightAllowance());
            payroll.setGrossPay(payroll.getBasePay() + payroll.getNightAllowance() + payroll.getOvertimeAllowance() + payroll.getTransportAllowance());
        }

        payrollMapper.upsertPayroll(payroll);

        // upsert 후 payrollId 보장 (ON DUPLICATE KEY UPDATE 시 기존 레코드 ID 보존)
        if (payroll.getPayrollId() == null) {
            PayrollDto persisted = payrollMapper.findByStaffAndMonth(staffId, targetYearMonth);
            if (persisted != null) {
                payroll.setPayrollId(persisted.getPayrollId());
            }
        }

        // 日別詳細スナップショットの再保存
        payrollMapper.deleteDailyDetailsByPayrollId(payroll.getPayrollId());
        for (PayrollDailyDetailDto detail : detailsToInsert) {
            detail.setPayrollId(payroll.getPayrollId());
            payrollMapper.insertPayrollDailyDetail(detail);
        }

        return payroll;
    }

    /**
     * 【行政通達 昭和62年3月14日付 基発第150号 準拠】
     * 月間3大端数処理（時間外・深夜・休日の30分境界月間丸めおよび50銭境界手当集計）を適用した法定集計
     */
    public StatutoryPayrollResult applyStatutoryMonthlyRounding(PayrollDto payroll) {
        return applyStatutoryMonthlyRounding(payroll, 0);
    }

    /**
     * 【行政通達 昭和62年3月14日付 基発第150号 準拠】
     * 月間3大端数処理（時間外・深夜・休日の30分境界月間丸めおよび50銭境界手当集計、休日労働時間連動版）
     */
    public StatutoryPayrollResult applyStatutoryMonthlyRounding(PayrollDto payroll, int totalHolidayMinutes) {
        if (payroll == null) {
            throw new IllegalArgumentException("給与データは必須です。");
        }

        // ① 月間労働時間の端数処理（時間外・深夜・休日それぞれ30分未満切り捨て、30分以上1時間に切り上げ）
        int roundedOvertimeHours = PayrollCalculator.StatutoryRounding.roundMonthlyHours(payroll.getTotalOvertimeMinutes());
        int roundedNightHours = PayrollCalculator.StatutoryRounding.roundMonthlyHours(payroll.getTotalNightMinutes());
        int roundedHolidayHours = PayrollCalculator.StatutoryRounding.roundMonthlyHours(Math.max(0, totalHolidayMinutes));

        int hourlyWage = (payroll.getAppliedHourlyWage() != null) ? payroll.getAppliedHourlyWage() : 1280;

        // ② 1時間あたりの割増賃金額（50銭未満切り捨て、50銭以上1円切り上げ）
        // 時間外: 25%加算
        int overtimeHourlyPremium = PayrollCalculator.StatutoryRounding.roundHourlyRate(
                java.math.BigDecimal.valueOf(hourlyWage).multiply(java.math.BigDecimal.valueOf(0.25))
        );
        // 深夜: 25%加算
        int nightHourlyPremium = PayrollCalculator.StatutoryRounding.roundHourlyRate(
                java.math.BigDecimal.valueOf(hourlyWage).multiply(java.math.BigDecimal.valueOf(0.25))
        );
        // 休日: 35%加算 (労基法第37条第1項)
        int holidayHourlyPremium = PayrollCalculator.StatutoryRounding.roundHourlyRate(
                java.math.BigDecimal.valueOf(hourlyWage).multiply(java.math.BigDecimal.valueOf(0.35))
        );

        // ③ 月間割増手当総額の端数処理（項目別独立集計・50銭未満切り捨て、50銭以上1円切り上げ）
        int roundedOvertimeAllowance = PayrollCalculator.StatutoryRounding.roundMonthlyAllowanceTotal(
                java.math.BigDecimal.valueOf((long) overtimeHourlyPremium * roundedOvertimeHours)
        );
        int roundedNightAllowance = PayrollCalculator.StatutoryRounding.roundMonthlyAllowanceTotal(
                java.math.BigDecimal.valueOf((long) nightHourlyPremium * roundedNightHours)
        );
        int roundedHolidayAllowance = PayrollCalculator.StatutoryRounding.roundMonthlyAllowanceTotal(
                java.math.BigDecimal.valueOf((long) holidayHourlyPremium * roundedHolidayHours)
        );

        return new StatutoryPayrollResult(
                roundedOvertimeHours,
                roundedNightHours,
                roundedHolidayHours,
                overtimeHourlyPremium,
                nightHourlyPremium,
                holidayHourlyPremium,
                roundedOvertimeAllowance,
                roundedNightAllowance,
                roundedHolidayAllowance
        );
    }

    public record StatutoryPayrollResult(
            int roundedOvertimeHours,
            int roundedNightHours,
            int roundedHolidayHours,
            int overtimeHourlyPremium,
            int nightHourlyPremium,
            int holidayHourlyPremium,
            int statutoryOvertimeAllowance,
            int statutoryNightAllowance,
            int statutoryHolidayAllowance
    ) {}

    /**
     * 給与確定ロック処理（単一レコードの帳簿不変性を保証、排他悲観적ロック適用）
     * 락 순서 역전(데드락) 방지를 위해 전역 락 계층(store_staff -> payroll)을 엄격 준수하고
     * 무잠금 초기 조회와 잠금 후 재조회 간 storeStaffId 일치 여부를 2중 검증(Double-Checked Lock)하여 TOCTOU를 방어합니다.
     */
    @Transactional
    public void confirmPayroll(Long payrollId, PayrollConfirmRequestDto confirmDto) {
        // [Step 1] storeStaffId 및 storeId 파악을 위한 초기 무잠금 조회 (Non-locking Read)
        PayrollDto initial = payrollMapper.findById(payrollId);
        if (initial == null) {
            throw new IllegalArgumentException("存在しない給与データです。(ID: " + payrollId + ")");
        }
        Long initialStaffId = initial.getStoreStaffId();
        Long initialStoreId = initial.getStoreId();

        // [Step 2] 전역 락 계층 1단계: store_staff 부모 행 배타적 락 선점 (Level 1 Lock)
        payrollMapper.lockStoreStaffForUpdate(initialStaffId);

        // [Step 3] 전역 락 계층 2단계: payroll 자식 행 배타적 락 획득 (Level 2 Lock)
        PayrollDto existing = payrollMapper.findByIdForUpdate(payrollId);
        if (existing == null) {
            throw new IllegalArgumentException("存在しない給与データです。(ID: " + payrollId + ")");
        }

        // [Step 3-1] TOCTOU 방어: 잠금 후 재조회한 storeStaffId 및 storeId가 초기 조회값과 일치하는지 엄밀 검증
        if (!initialStaffId.equals(existing.getStoreStaffId())) {
            throw new IllegalStateException("給与レコードの職員所属情報が変更されたため、確定処理を中断しました。(初期: " +
                    initialStaffId + ", ロック後: " + existing.getStoreStaffId() + ")");
        }
        if (!initialStoreId.equals(existing.getStoreId())) {
            throw new IllegalStateException("給与レコードの店舗情報が変更されたため、確定処理を中断しました。(初期店舗: " +
                    initialStoreId + ", ロック後店舗: " + existing.getStoreId() + ")");
        }

        // [Step 3-2] 급여 상태 재검증 (이미 다른 관리자에 의해 확정되었는지 여부)
        if (!"DRAFT".equals(existing.getPayrollStatus())) {
            throw new IllegalStateException("既に確定済みの給与データです。(現在の状態: " + existing.getPayrollStatus() + ")");
        }

        // [Step 4] 정산 대상 기간 종료의 검증 (마감 기간 종료 전 조기 확정 방지)
        if (!LocalDate.now().isAfter(existing.getRangeEndDate())) {
            throw new IllegalStateException("精算対象期間 (" + existing.getRangeEndDate() + ") が終了する前に給与を確定することはできません。");
        }

        // [Step 5] 미처리 근태 확인 (수정 신청 대기 또는 타각 진행 중 차단)
        int unresolved = payrollMapper.countUnresolvedAttendance(existing.getStoreStaffId(), existing.getRangeStartDate(), existing.getRangeEndDate());
        if (unresolved > 0) {
            throw new IllegalStateException("未処理の勤怠修正申請または勤務中打刻が存在するため、確定できません。");
        }

        // [Step 6] 최신 데이터 기반 재계산 (동일 트랜잭션 내 store_staff 락 재진입 안전)
        PayrollDto recalculated = calculatePayrollForStaff(
                existing.getStoreId(),
                existing.getStoreStaffId(),
                existing.getTargetYearMonth(),
                existing.getRangeStartDate(),
                existing.getRangeEndDate()
        );

        // [Step 7] 확인 금액과 재계산 금액 불일치 검증
        if (confirmDto != null && confirmDto.getExpectedGrossPay() != null) {
            int expected = confirmDto.getExpectedGrossPay();
            int actual = recalculated.getGrossPay();
            if (expected != actual) {
                throw new IllegalStateException("確認時と金額が一致しません。(確認額: " + expected + "円, 再計算額: " + actual + "円)。再確認が必要です。");
            }
        }

        payrollMapper.updatePayrollStatusConfirm(payrollId, LocalDateTime.now());
    }

    /**
     * 店舗全体の月次給与一括確定バッチ処理（同月全レコード悲観的ロックおよび一括確定）
     *
     * @param storeId 対象店舗ID
     * @param targetYearMonth 対象年月 (YYYY-MM)
     * @return 確定された給与件数
     */
    @Transactional
    public int confirmMonthlyPayroll(Long storeId, String targetYearMonth) {
        // [Step 1] 확정 대상 DRAFT 급여를 보유한 직원 ID 목록을 결정론적 오름차순으로 1차 선별
        List<Long> draftStaffIds = payrollMapper.findDraftStaffIdsByStoreAndMonth(storeId, targetYearMonth);
        if (draftStaffIds == null || draftStaffIds.isEmpty()) {
            List<PayrollDto> allPayrolls = payrollMapper.findPayrollsByStoreAndMonth(storeId, targetYearMonth);
            if (allPayrolls == null || allPayrolls.isEmpty()) {
                throw new IllegalStateException("指定年月 (" + targetYearMonth + ") の給与データが存在しません。先に一括計算を実行してください。");
            } else {
                throw new IllegalStateException("指定年月 (" + targetYearMonth + ") の給与は全件確定済みです。");
            }
        }

        // [Step 2] 전역 락 계층 1단계: 선별된 직원 부모 행(store_staff)들을 ID 오름차순으로 순차 선점 잠금 (Level 1 Lock)
        for (Long staffId : draftStaffIds) {
            payrollMapper.lockStoreStaffForUpdate(staffId);
        }

        // [Step 3] 전역 락 계층 2단계: 부모 락이 확보된 안전 상태에서 점포의 당월 급여 행들을 배타 락(Level 2 Lock)으로 재조회
        List<PayrollDto> payrolls = payrollMapper.lockStorePayrollsForUpdate(storeId, targetYearMonth);
        if (payrolls == null || payrolls.isEmpty()) {
            throw new IllegalStateException("指定年月 (" + targetYearMonth + ") の給与データが存在しません。");
        }

        List<PayrollDto> draftPayrolls = payrolls.stream()
                .filter(p -> "DRAFT".equals(p.getPayrollStatus()))
                .toList();

        if (draftPayrolls.isEmpty()) {
            throw new IllegalStateException("指定年月 (" + targetYearMonth + ") の給与は全件確定済みです。");
        }

        // [Step 3-1] 동시성 경계 검증: 부모 락을 잡지 않은 새로운 직원의 DRAFT가 그 사이에 추가되었는지 감지
        for (PayrollDto p : draftPayrolls) {
            if (!draftStaffIds.contains(p.getStoreStaffId())) {
                throw new IllegalStateException("月次確定処理中に新たな職員の給与が生成されました。(職員ID: " +
                        p.getStoreStaffId() + ")。データの整合性を保つため再実行してください。");
            }
        }

        LocalDate today = LocalDate.now();

        // 締め期間終了および未処理勤怠の全件バリデーション
        for (PayrollDto p : draftPayrolls) {
            if (!today.isAfter(p.getRangeEndDate())) {
                throw new IllegalStateException("精算対象期間 (" + p.getRangeEndDate() + ") が終了する前に月次給与を一括確定することはできません。(対象スタッフID: " + p.getStoreStaffId() + ")");
            }

            int unresolved = payrollMapper.countUnresolvedAttendance(p.getStoreStaffId(), p.getRangeStartDate(), p.getRangeEndDate());
            if (unresolved > 0) {
                throw new IllegalStateException("未処理の勤怠修正申請または打刻が存在するため、月次給与を一括確定できません。(対象スタッフID: " + p.getStoreStaffId() + ")");
            }
        }

        // 店舗・対象年月の一括CONFIRMED更新 (CAS)
        int updatedCount = payrollMapper.updateAllPayrollStatusConfirm(storeId, targetYearMonth, LocalDateTime.now());
        return updatedCount;
    }

    /**
     * スタッフ個人別給与明細詳細モーダル用データ取得（認可検証付き）
     */
    public PayrollDetailResponseDto getPayrollDetail(Long payrollId, Long currentMemberId) {
        if (currentMemberId != null) {
            storeAuthorizationService.validatePayrollOwnership(payrollId, currentMemberId);
        }
        return getPayrollDetail(payrollId);
    }

    /**
     * スタッフ個人別給与明細詳細モーダル用データ取得
     */
    public PayrollDetailResponseDto getPayrollDetail(Long payrollId) {
        PayrollDto payroll = payrollMapper.findById(payrollId);
        if (payroll == null) {
            throw new IllegalArgumentException("存在しない給与データです。(ID: " + payrollId + ")");
        }

        List<PayrollDailyDetailDto> details = payrollMapper.findDailyDetailsByPayrollId(payrollId);

        PayrollDetailResponseDto response = new PayrollDetailResponseDto();
        response.setPayrollId(payroll.getPayrollId());
        response.setStaffName(payroll.getStaffName());
        response.setTargetYearMonth(payroll.getTargetYearMonth());
        response.setAppliedHourlyWage(payroll.getAppliedHourlyWage());

        PayrollDetailResponseDto.PayrollSummary summary = new PayrollDetailResponseDto.PayrollSummary();
        summary.setWorkDaysCount(payroll.getWorkDaysCount());
        summary.setTotalWorkMinutes(payroll.getTotalWorkMinutes());
        summary.setTotalNightMinutes(payroll.getTotalNightMinutes());
        summary.setTotalOvertimeMinutes(payroll.getTotalOvertimeMinutes());
        summary.setBasePay(payroll.getBasePay());
        summary.setNightAllowance(payroll.getNightAllowance());
        summary.setOvertimeAllowance(payroll.getOvertimeAllowance());
        summary.setTransportAllowance(payroll.getTransportAllowance());
        summary.setGrossPay(payroll.getGrossPay());
        summary.setPayrollStatus(payroll.getPayrollStatus());
        response.setSummary(summary);

        DateTimeFormatter timeFormatter = DateTimeFormatter.ofPattern("HH:mm");
        List<PayrollDetailResponseDto.DailyAttendanceDetail> dailyList = new ArrayList<>();

        for (PayrollDailyDetailDto d : details) {
            PayrollDetailResponseDto.DailyAttendanceDetail daily = new PayrollDetailResponseDto.DailyAttendanceDetail();
            daily.setWorkDate(d.getWorkDate().toString());
            daily.setBreakMinutes(d.getTotalBreakMinutes());
            daily.setNetWorkMinutes(d.getNetWorkMinutes());
            daily.setNightMinutes(d.getNightWorkMinutes());
            daily.setOvertimeMinutes(d.getOvertimeMinutes());
            daily.setAppliedHourlyWage(d.getAppliedHourlyWage());
            daily.setBasePay(d.getBasePay());
            daily.setNightAllowance(d.getNightAllowance());
            daily.setOvertimeAllowance(d.getOvertimeAllowance());
            daily.setTransportAmount(d.getTransportAmount());

            // 元勤怠データから打刻時刻情報を補完
            AttendanceDto originalAtt = attendanceMapper.findById(d.getAttendanceId());
            if (originalAtt != null) {
                daily.setClockInAt(originalAtt.getClockInAt() != null ? originalAtt.getClockInAt().format(timeFormatter) : "-");
                daily.setClockOutAt(originalAtt.getClockOutAt() != null ? originalAtt.getClockOutAt().format(timeFormatter) : "-");
            } else {
                daily.setClockInAt("-");
                daily.setClockOutAt("-");
            }

            dailyList.add(daily);
        }

        response.setDailyAttendances(dailyList);
        return response;
    }

    /**
     * 給与台帳 Excel エクスポート（労働基準法第108条準拠・SXSSFストリーミング）
     * 読取一貫性(Snapshot Isolation)担保のため、readOnlyトランザクションを適用
     */
    @Transactional(readOnly = true)
    public byte[] exportPayrollToExcel(Long storeId, String targetYearMonth) throws IOException {
        List<PayrollDto> payrolls = getPayrollsByStoreAndMonth(storeId, targetYearMonth);
        String storeName = payrollMapper.findStoreNameById(storeId);
        return PayrollExcelExporter.exportToExcel(targetYearMonth, storeName, payrolls);
    }
}
package com.example.kintai.domain.payroll.controller;

import com.example.kintai.domain.payroll.dto.PayrollCalculateRequestDto;
import com.example.kintai.domain.payroll.dto.PayrollConfirmRequestDto;
import com.example.kintai.domain.payroll.dto.PayrollDetailResponseDto;
import com.example.kintai.domain.payroll.dto.PayrollDto;
import com.example.kintai.domain.payroll.service.PayrollService;
import com.example.kintai.domain.store.service.StoreAuthorizationService;
import com.example.kintai.global.common.ApiResponse;
import com.example.kintai.global.security.CustomUserDetails;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;

@RestController
@RequestMapping("/api/payroll")
@RequiredArgsConstructor
public class PayrollApiController {

    private final PayrollService payrollService;
    private final StoreAuthorizationService storeAuthorizationService;

    /**
     * 月別給与一覧取得 (オーナー店舗所有権検証必須)
     */
    @GetMapping("/list")
    public ResponseEntity<ApiResponse<List<PayrollDto>>> getPayrollList(
            @RequestParam("storeId") Long storeId,
            @RequestParam("yearMonth") String yearMonth,
            @AuthenticationPrincipal CustomUserDetails userDetails
    ) {
        storeAuthorizationService.validateStoreOwnership(storeId, userDetails.getMemberId());
        List<PayrollDto> list = payrollService.getPayrollsByStoreAndMonth(storeId, yearMonth);
        return ResponseEntity.ok(ApiResponse.ok("給与一覧を取得しました。", list));
    }

    /**
     * 月別給与一括計算 (Upsert, オーナー店舗所有権検証必須)
     */
    @PostMapping("/calculate")
    public ResponseEntity<ApiResponse<Void>> calculateMonthlyPayroll(
            @RequestBody PayrollCalculateRequestDto requestDto,
            @AuthenticationPrincipal CustomUserDetails userDetails
    ) {
        storeAuthorizationService.validateStoreOwnership(requestDto.getStoreId(), userDetails.getMemberId());
        payrollService.calculateMonthlyPayroll(requestDto.getStoreId(), requestDto.getTargetYearMonth());
        return ResponseEntity.ok(ApiResponse.ok("給与計算が完了しました。", null));
    }

    /**
     * 店舗全体の月次給与一括確定 (排他悲観的ロック適用・帳簿ロック, オーナー店舗所有権検証必須)
     */
    @PostMapping("/confirm-month")
    public ResponseEntity<ApiResponse<Integer>> confirmMonthlyPayroll(
            @RequestBody PayrollCalculateRequestDto requestDto,
            @AuthenticationPrincipal CustomUserDetails userDetails
    ) {
        storeAuthorizationService.validateStoreOwnership(requestDto.getStoreId(), userDetails.getMemberId());
        int confirmedCount = payrollService.confirmMonthlyPayroll(requestDto.getStoreId(), requestDto.getTargetYearMonth());
        return ResponseEntity.ok(ApiResponse.ok("月次給与の一括確定が完了しました。(確定件数: " + confirmedCount + "件)", confirmedCount));
    }

    /**
     * 給与確定処理 (帳簿ロック・排他制御適用, 給与データ所有権検証必須)
     */
    @PostMapping("/{payrollId}/confirm")
    public ResponseEntity<ApiResponse<Void>> confirmPayroll(
            @PathVariable("payrollId") Long payrollId,
            @RequestBody(required = false) PayrollConfirmRequestDto confirmDto,
            @AuthenticationPrincipal CustomUserDetails userDetails
    ) {
        storeAuthorizationService.validatePayrollOwnership(payrollId, userDetails.getMemberId());
        payrollService.confirmPayroll(payrollId, confirmDto);
        return ResponseEntity.ok(ApiResponse.ok("給与が確定されました。", null));
    }

    /**
     * スタッフ個人別給与明細詳細取得 (給与データ所有権検証必須)
     */
    @GetMapping("/{payrollId}/detail")
    public ResponseEntity<ApiResponse<PayrollDetailResponseDto>> getPayrollDetail(
            @PathVariable("payrollId") Long payrollId,
            @AuthenticationPrincipal CustomUserDetails userDetails
    ) {
        storeAuthorizationService.validatePayrollOwnership(payrollId, userDetails.getMemberId());
        PayrollDetailResponseDto detail = payrollService.getPayrollDetail(payrollId, userDetails.getMemberId());
        return ResponseEntity.ok(ApiResponse.ok("給与明細詳細を取得しました。", detail));
    }

    /**
     * 給与台帳 Excel エクスポート (オーナー店舗所有権検証必須)
     */
    @GetMapping("/excel")
    public ResponseEntity<byte[]> downloadExcel(
            @RequestParam("storeId") Long storeId,
            @RequestParam("yearMonth") String yearMonth,
            @AuthenticationPrincipal CustomUserDetails userDetails
    ) throws IOException {
        // 対象年月フォーマットの厳格な検証 (F-006 対策: YYYY-MM 形式)
        if (yearMonth == null || !yearMonth.matches("^\\d{4}-(0[1-9]|1[0-2])$")) {
            throw new IllegalArgumentException("無効な対象年月フォーマットです (形式: YYYY-MM): " + yearMonth);
        }

        storeAuthorizationService.validateStoreOwnership(storeId, userDetails.getMemberId());
        byte[] excelBytes = payrollService.exportPayrollToExcel(storeId, yearMonth);

        String fileName = URLEncoder.encode("給与台帳_" + yearMonth + ".xlsx", StandardCharsets.UTF_8).replace("+", "%20");

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + fileName + "\"; filename*=UTF-8''" + fileName)
                .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .body(excelBytes);
    }
}
<?xml version="1.0" encoding="UTF-8" ?>
<!DOCTYPE mapper PUBLIC "-//mybatis.org//DTD Mapper 3.0//EN"
        "http://mybatis.org/dtd/mybatis-3-mapper.dtd">

<mapper namespace="com.example.kintai.domain.payroll.mapper.PayrollMapper">

    <select id="findPayrollsByStoreAndMonth" resultType="com.example.kintai.domain.payroll.dto.PayrollDto">
        SELECT
            p.payroll_id,
            p.store_id,
            p.store_staff_id,
            m.name AS staff_name,
            p.target_year_month,
            p.range_start_date,
            p.range_end_date,
            p.work_days_count,
            p.total_work_minutes,
            p.total_night_minutes,
            p.total_overtime_minutes,
            p.applied_hourly_wage,
            p.applied_transport_limit,
            p.base_pay,
            p.night_allowance,
            p.overtime_allowance,
            p.transport_allowance,
            p.gross_pay,
            p.payroll_status,
            p.confirmed_at,
            p.paid_at
        FROM payroll p
        JOIN store_staff ss ON p.store_staff_id = ss.store_staff_id
        JOIN member m ON ss.member_id = m.member_id
        WHERE p.store_id = #{storeId}
          AND p.target_year_month = #{targetYearMonth}
        ORDER BY p.store_staff_id ASC
    </select>

    <select id="findById" resultType="com.example.kintai.domain.payroll.dto.PayrollDto">
        SELECT
            p.payroll_id,
            p.store_id,
            p.store_staff_id,
            m.name AS staff_name,
            p.target_year_month,
            p.range_start_date,
            p.range_end_date,
            p.work_days_count,
            p.total_work_minutes,
            p.total_night_minutes,
            p.total_overtime_minutes,
            p.applied_hourly_wage,
            p.applied_transport_limit,
            p.base_pay,
            p.night_allowance,
            p.overtime_allowance,
            p.transport_allowance,
            p.gross_pay,
            p.payroll_status,
            p.confirmed_at,
            p.paid_at
        FROM payroll p
        JOIN store_staff ss ON p.store_staff_id = ss.store_staff_id
        JOIN member m ON ss.member_id = m.member_id
        WHERE p.payroll_id = #{payrollId}
    </select>

    <select id="findByIdForUpdate" resultType="com.example.kintai.domain.payroll.dto.PayrollDto">
        SELECT
            p.payroll_id,
            p.store_id,
            p.store_staff_id,
            m.name AS staff_name,
            p.target_year_month,
            p.range_start_date,
            p.range_end_date,
            p.work_days_count,
            p.total_work_minutes,
            p.total_night_minutes,
            p.total_overtime_minutes,
            p.applied_hourly_wage,
            p.applied_transport_limit,
            p.base_pay,
            p.night_allowance,
            p.overtime_allowance,
            p.transport_allowance,
            p.gross_pay,
            p.payroll_status,
            p.confirmed_at,
            p.paid_at
        FROM payroll p
        JOIN store_staff ss ON p.store_staff_id = ss.store_staff_id
        JOIN member m ON ss.member_id = m.member_id
        WHERE p.payroll_id = #{payrollId}
        FOR UPDATE
    </select>

    <select id="findByStaffAndMonth" resultType="com.example.kintai.domain.payroll.dto.PayrollDto">
        SELECT
            p.payroll_id,
            p.store_id,
            p.store_staff_id,
            m.name AS staff_name,
            p.target_year_month,
            p.range_start_date,
            p.range_end_date,
            p.work_days_count,
            p.total_work_minutes,
            p.total_night_minutes,
            p.total_overtime_minutes,
            p.applied_hourly_wage,
            p.applied_transport_limit,
            p.base_pay,
            p.night_allowance,
            p.overtime_allowance,
            p.transport_allowance,
            p.gross_pay,
            p.payroll_status,
            p.confirmed_at,
            p.paid_at
        FROM payroll p
        JOIN store_staff ss ON p.store_staff_id = ss.store_staff_id
        JOIN member m ON ss.member_id = m.member_id
        WHERE p.store_staff_id = #{storeStaffId}
          AND p.target_year_month = #{targetYearMonth}
        LIMIT 1
    </select>

    <select id="findByStaffAndMonthForUpdate" resultType="com.example.kintai.domain.payroll.dto.PayrollDto">
        SELECT
            p.payroll_id,
            p.store_id,
            p.store_staff_id,
            m.name AS staff_name,
            p.target_year_month,
            p.range_start_date,
            p.range_end_date,
            p.work_days_count,
            p.total_work_minutes,
            p.total_night_minutes,
            p.total_overtime_minutes,
            p.applied_hourly_wage,
            p.applied_transport_limit,
            p.base_pay,
            p.night_allowance,
            p.overtime_allowance,
            p.transport_allowance,
            p.gross_pay,
            p.payroll_status,
            p.confirmed_at,
            p.paid_at
        FROM payroll p
        JOIN store_staff ss ON p.store_staff_id = ss.store_staff_id
        JOIN member m ON ss.member_id = m.member_id
        WHERE p.store_staff_id = #{storeStaffId}
          AND p.target_year_month = #{targetYearMonth}
        LIMIT 1
        FOR UPDATE
    </select>

    <insert id="upsertPayroll" useGeneratedKeys="true" keyProperty="payrollId">
        INSERT INTO payroll (
            store_id,
            store_staff_id,
            target_year_month,
            range_start_date,
            range_end_date,
            work_days_count,
            total_work_minutes,
            total_night_minutes,
            total_overtime_minutes,
            applied_hourly_wage,
            applied_transport_limit,
            base_pay,
            night_allowance,
            overtime_allowance,
            transport_allowance,
            gross_pay,
            payroll_status
        ) VALUES (
            #{storeId},
            #{storeStaffId},
            #{targetYearMonth},
            #{rangeStartDate},
            #{rangeEndDate},
            #{workDaysCount},
            #{totalWorkMinutes},
            #{totalNightMinutes},
            #{totalOvertimeMinutes},
            #{appliedHourlyWage},
            #{appliedTransportLimit},
            #{basePay},
            #{nightAllowance},
            #{overtimeAllowance},
            #{transportAllowance},
            #{grossPay},
            #{payrollStatus}
        )
        ON DUPLICATE KEY UPDATE
            work_days_count = VALUES(work_days_count),
            total_work_minutes = VALUES(total_work_minutes),
            total_night_minutes = VALUES(total_night_minutes),
            total_overtime_minutes = VALUES(total_overtime_minutes),
            applied_hourly_wage = VALUES(applied_hourly_wage),
            applied_transport_limit = VALUES(applied_transport_limit),
            base_pay = VALUES(base_pay),
            night_allowance = VALUES(night_allowance),
            overtime_allowance = VALUES(overtime_allowance),
            transport_allowance = VALUES(transport_allowance),
            gross_pay = VALUES(gross_pay),
            updated_at = CURRENT_TIMESTAMP
    </insert>

    <insert id="insertPayrollDailyDetail" useGeneratedKeys="true" keyProperty="payrollDailyDetailId">
        INSERT INTO payroll_daily_detail (
            payroll_id,
            attendance_id,
            work_date,
            applied_hourly_wage,
            total_break_minutes,
            net_work_minutes,
            night_work_minutes,
            overtime_minutes,
            base_pay,
            night_allowance,
            overtime_allowance,
            transport_amount
        ) VALUES (
            #{payrollId},
            #{attendanceId},
            #{workDate},
            #{appliedHourlyWage},
            #{totalBreakMinutes},
            #{netWorkMinutes},
            #{nightWorkMinutes},
            #{overtimeMinutes},
            #{basePay},
            #{nightAllowance},
            #{overtimeAllowance},
            #{transportAmount}
        )
    </insert>

    <select id="findDailyDetailsByPayrollId" resultType="com.example.kintai.domain.payroll.dto.PayrollDailyDetailDto">
        SELECT
            payroll_daily_detail_id,
            payroll_id,
            attendance_id,
            work_date,
            applied_hourly_wage,
            total_break_minutes,
            net_work_minutes,
            night_work_minutes,
            overtime_minutes,
            base_pay,
            night_allowance,
            overtime_allowance,
            transport_amount
        FROM payroll_daily_detail
        WHERE payroll_id = #{payrollId}
        ORDER BY work_date ASC
    </select>

    <delete id="deleteDailyDetailsByPayrollId">
        DELETE FROM payroll_daily_detail
        WHERE payroll_id = #{payrollId}
    </delete>

    <update id="updatePayrollStatusConfirm">
        UPDATE payroll
        SET payroll_status = 'CONFIRMED',
            confirmed_at = #{confirmedAt},
            updated_at = CURRENT_TIMESTAMP
        WHERE payroll_id = #{payrollId}
          AND payroll_status = 'DRAFT'
    </update>

    <update id="updateAllPayrollStatusConfirm">
        UPDATE payroll p
        SET p.payroll_status = 'CONFIRMED',
            p.confirmed_at = #{confirmedAt},
            p.updated_at = CURRENT_TIMESTAMP
        WHERE p.store_id = #{storeId}
          AND p.target_year_month = #{targetYearMonth}
          AND p.payroll_status = 'DRAFT'
          AND NOT EXISTS (
              SELECT 1
              FROM attendance a
              WHERE a.store_staff_id = p.store_staff_id
                AND a.work_date &gt;= p.range_start_date
                AND a.work_date &lt;= p.range_end_date
                AND a.attendance_status IN ('WORKING', 'ON_BREAK', 'MOD_REQUESTED')
          )
    </update>

    <select id="countPayrollsByStoreAndMonthAndStatus" resultType="int">
        SELECT COUNT(*)
        FROM payroll
        WHERE store_id = #{storeId}
          AND target_year_month = #{targetYearMonth}
          AND payroll_status = #{payrollStatus}
    </select>

    <select id="countUnresolvedAttendance" resultType="int">
        SELECT COUNT(*)
        FROM attendance
        WHERE store_staff_id = #{storeStaffId}
          AND work_date &gt;= #{startDate}
          AND work_date &lt;= #{endDate}
          AND attendance_status IN ('WORKING', 'ON_BREAK', 'MOD_REQUESTED')
    </select>

    <select id="getPayCutoffDayByStoreId" resultType="java.lang.Integer">
        SELECT pay_cutoff_day
        FROM store
        WHERE store_id = #{storeId}
    </select>

    <select id="findHourlyWageForStaffOnDate" resultType="java.lang.Integer">
        SELECT hourly_wage
        FROM store_staff_contract_history
        WHERE store_staff_id = #{storeStaffId}
          AND effective_from &lt;= #{workDate}
          AND (effective_to IS NULL OR effective_to &gt; #{workDate})
        ORDER BY effective_from DESC
        LIMIT 1
    </select>

    <select id="findTransportLimitForStaffOnDate" resultType="java.lang.Integer">
        SELECT monthly_transport_limit
        FROM store_staff_contract_history
        WHERE store_staff_id = #{storeStaffId}
          AND effective_from &lt;= #{date}
          AND (effective_to IS NULL OR effective_to &gt; #{date})
        ORDER BY effective_from DESC
        LIMIT 1
    </select>

    <select id="findDailyTransportFeeForStaffOnDate" resultType="java.lang.Integer">
        SELECT daily_transport_fee
        FROM store_staff_contract_history
        WHERE store_staff_id = #{storeStaffId}
          AND effective_from &lt;= #{date}
          AND (effective_to IS NULL OR effective_to &gt; #{date})
        ORDER BY effective_from DESC
        LIMIT 1
    </select>

    <select id="lockStoreStaffForUpdate" resultType="java.lang.Long">
        SELECT store_staff_id
        FROM store_staff
        WHERE store_staff_id = #{storeStaffId}
        FOR UPDATE
    </select>

    <select id="lockStorePayrollsForUpdate" resultType="com.example.kintai.domain.payroll.dto.PayrollDto">
        SELECT
            payroll_id,
            store_id,
            store_staff_id,
            target_year_month,
            range_start_date,
            range_end_date,
            work_days_count,
            total_work_minutes,
            total_night_minutes,
            total_overtime_minutes,
            applied_hourly_wage,
            applied_transport_limit,
            base_pay,
            night_allowance,
            overtime_allowance,
            transport_allowance,
            gross_pay,
            payroll_status,
            confirmed_at,
            paid_at
        FROM payroll
        WHERE store_id = #{storeId}
          AND target_year_month = #{targetYearMonth}
        ORDER BY store_staff_id ASC, payroll_id ASC
        FOR UPDATE
    </select>

    <select id="findActiveStaffIdsByStoreId" resultType="java.lang.Long">
        SELECT store_staff_id
        FROM store_staff
        WHERE store_id = #{storeId}
          AND employment_status = 'ACTIVE'
        ORDER BY store_staff_id ASC
    </select>

    <select id="findDraftStaffIdsByStoreAndMonth" resultType="java.lang.Long">
        SELECT DISTINCT store_staff_id
        FROM payroll
        WHERE store_id = #{storeId}
          AND target_year_month = #{targetYearMonth}
          AND payroll_status = 'DRAFT'
        ORDER BY store_staff_id ASC
    </select>

    <select id="findStoreNameById" resultType="java.lang.String">
        SELECT store_name
        FROM store
        WHERE store_id = #{storeId}
    </select>

</mapper>
package com.example.kintai.domain.payroll.service;

import com.example.kintai.domain.payroll.dto.PayrollDto;
import com.example.kintai.domain.payroll.mapper.PayrollMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DeadlockLoserDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@DisplayName("F-04: 락 순서 역전에 따른 실제 MySQL InnoDB 데드락(1213 / 40001) 재현 및 검증")
class PayrollLockOrderDeadlockReproductionIntegrationTest {

    @Autowired
    private DataSource dataSource;

    @Autowired
    private PayrollMapper payrollMapper;

    private Long testPayrollId;
    private final Long targetStaffId = 1L;

    @BeforeEach
    void setUp() {
        PayrollDto draft = new PayrollDto();
        draft.setStoreId(1L);
        draft.setStoreStaffId(targetStaffId);
        draft.setTargetYearMonth("2026-10");
        draft.setRangeStartDate(LocalDate.of(2026, 10, 1));
        draft.setRangeEndDate(LocalDate.of(2026, 10, 31));
        draft.setWorkDaysCount(1);
        draft.setTotalWorkMinutes(480);
        draft.setTotalNightMinutes(0);
        draft.setTotalOvertimeMinutes(0);
        draft.setAppliedHourlyWage(1300);
        draft.setAppliedTransportLimit(15000);
        draft.setBasePay(10400);
        draft.setNightAllowance(0);
        draft.setOvertimeAllowance(0);
        draft.setTransportAllowance(500);
        draft.setGrossPay(10900);
        draft.setPayrollStatus("DRAFT");
        payrollMapper.upsertPayroll(draft);
        testPayrollId = draft.getPayrollId();
    }

    @AfterEach
    void tearDown() {
        if (testPayrollId != null) {
            payrollMapper.deleteDailyDetailsByPayrollId(testPayrollId);
        }
    }

    @Test
    @DisplayName("독립 JDBC 커넥션 2개에서 상호 역순 락 획득 시 MySQL 1213 데드락 감지 및 40001 SQLState 실측")
    void reproduceDeadlock_RawJdbcConnections() throws Exception {
        CountDownLatch conn1AcquiredStaff = new CountDownLatch(1);
        CountDownLatch conn2AcquiredPayroll = new CountDownLatch(1);
        CountDownLatch proceedToSecondLock = new CountDownLatch(1);

        AtomicReference<SQLException> conn1Exception = new AtomicReference<>();
        AtomicReference<SQLException> conn2Exception = new AtomicReference<>();

        ExecutorService executor = Executors.newFixedThreadPool(2);

        // Connection 1: calculatePayrollForStaff 경로 (store_staff -> payroll)
        Future<?> f1 = executor.submit(() -> {
            try (Connection c1 = dataSource.getConnection()) {
                c1.setAutoCommit(false);
                c1.setTransactionIsolation(Connection.TRANSACTION_READ_COMMITTED);

                // Step 1: store_staff 행 락
                try (PreparedStatement ps = c1.prepareStatement("SELECT store_staff_id FROM store_staff WHERE store_staff_id = ? FOR UPDATE")) {
                    ps.setLong(1, targetStaffId);
                    ps.executeQuery();
                }
                System.out.println("[Conn-1] Step 1: store_staff 행 락 획득 완료");
                conn1AcquiredStaff.countDown();

                // Conn-2가 payroll 락을 획득할 때까지 대기
                conn2AcquiredPayroll.await(5, TimeUnit.SECONDS);
                proceedToSecondLock.await(5, TimeUnit.SECONDS);
                Thread.sleep(100);

                // Step 2: payroll 행 락 요청 (Conn-2가 점유 중)
                System.out.println("[Conn-1] Step 2: payroll 행 락 요청 시도");
                try (PreparedStatement ps2 = c1.prepareStatement("SELECT payroll_id FROM payroll WHERE payroll_id = ? FOR UPDATE")) {
                    ps2.setLong(1, testPayrollId);
                    ps2.executeQuery();
                }

                System.out.println("[Conn-1] Step 2 성공 (생존자 트랜잭션)");
                c1.commit();
            } catch (SQLException e) {
                System.out.println("[Conn-1] SQLException 포착: ErrorCode=" + e.getErrorCode() + ", SQLState=" + e.getSQLState() + ", Msg=" + e.getMessage());
                conn1Exception.set(e);
            } catch (Exception e) {
                e.printStackTrace();
            }
        });

        // Connection 2: confirmPayroll 경로 (payroll -> store_staff)
        Future<?> f2 = executor.submit(() -> {
            try (Connection c2 = dataSource.getConnection()) {
                c2.setAutoCommit(false);
                c2.setTransactionIsolation(Connection.TRANSACTION_READ_COMMITTED);

                // Step 1: payroll 행 락
                try (PreparedStatement ps = c2.prepareStatement("SELECT payroll_id FROM payroll WHERE payroll_id = ? FOR UPDATE")) {
                    ps.setLong(1, testPayrollId);
                    ps.executeQuery();
                }
                System.out.println("[Conn-2] Step 1: payroll 행 락 획득 완료");
                conn2AcquiredPayroll.countDown();

                // Conn-1이 store_staff 락을 획득할 때까지 대기
                conn1AcquiredStaff.await(5, TimeUnit.SECONDS);
                proceedToSecondLock.countDown();
                Thread.sleep(100);

                // Step 2: store_staff 행 락 요청 (Conn-1이 점유 중 -> 상호 대기 데드락!)
                System.out.println("[Conn-2] Step 2: store_staff 행 락 요청 시도 (데드락 유발)");
                try (PreparedStatement ps2 = c2.prepareStatement("SELECT store_staff_id FROM store_staff WHERE store_staff_id = ? FOR UPDATE")) {
                    ps2.setLong(1, targetStaffId);
                    ps2.executeQuery();
                }

                System.out.println("[Conn-2] Step 2 성공 (생존자 트랜잭션)");
                c2.commit();
            } catch (SQLException e) {
                System.out.println("[Conn-2] SQLException 포착: ErrorCode=" + e.getErrorCode() + ", SQLState=" + e.getSQLState() + ", Msg=" + e.getMessage());
                conn2Exception.set(e);
            } catch (Exception e) {
                e.printStackTrace();
            }
        });

        f1.get(10, TimeUnit.SECONDS);
        f2.get(10, TimeUnit.SECONDS);
        executor.shutdown();

        SQLException ex1 = conn1Exception.get();
        SQLException ex2 = conn2Exception.get();

        System.out.println("==================================================================");
        System.out.println("[F-04 데드락 정밀 실측 결과]");
        System.out.println("Conn-1 Exception: " + (ex1 != null ? "ErrorCode=" + ex1.getErrorCode() + ", SQLState=" + ex1.getSQLState() + " (" + ex1.getMessage() + ")" : "SURVIVED & COMMITTED"));
        System.out.println("Conn-2 Exception: " + (ex2 != null ? "ErrorCode=" + ex2.getErrorCode() + ", SQLState=" + ex2.getSQLState() + " (" + ex2.getMessage() + ")" : "SURVIVED & COMMITTED"));
        System.out.println("==================================================================");

        boolean deadlockDetected = (ex1 != null && ex1.getErrorCode() == 1213) || (ex2 != null && ex2.getErrorCode() == 1213);
        boolean sqlState40001 = (ex1 != null && "40001".equals(ex1.getSQLState())) || (ex2 != null && "40001".equals(ex2.getSQLState()));

        assertTrue(deadlockDetected, "[CRITICAL F-04] MySQL 에러 코드 1213 (Deadlock found when trying to get lock)이 실측되어야 함!");
        assertTrue(sqlState40001, "[CRITICAL F-04] 표준 SQLState 40001 (Serialization Failure)이 실측되어야 함!");
        assertTrue((ex1 == null && ex2 != null) || (ex1 != null && ex2 == null), "[CRITICAL F-04] 정확히 한쪽만 희생자로 롤백되고 다른 한쪽은 정상 커밋되어야 함");
    }
}
