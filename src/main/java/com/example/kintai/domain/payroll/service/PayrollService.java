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
     * 給与台帳 Excel エクスポート
     */
    public byte[] exportPayrollToExcel(Long storeId, String targetYearMonth) throws IOException {
        List<PayrollDto> payrolls = getPayrollsByStoreAndMonth(storeId, targetYearMonth);
        return PayrollExcelExporter.exportToExcel(targetYearMonth, payrolls);
    }
}
