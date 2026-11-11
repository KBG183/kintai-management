/**
 * [FULL EVIDENCE BUNDLE FOR LOCAL QWEN 27B AUDIT]
 * Feature: Day 8 - Pessimistic Concurrency & Two-Phase Lock (F-04 Verification)
 * Files: PayrollService.java, PayrollMapper.xml, PayrollMapper.java
 */

package com.example.kintai.domain.payroll;

import com.example.kintai.domain.payroll.dto.PayrollConfirmRequestDto;
import com.example.kintai.domain.payroll.dto.PayrollDto;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public class PayrollAuditBundle {

    // 1. confirmPayroll (Single Confirmation with TOCTOU double check)
    @Transactional
    public void confirmPayroll(Long payrollId, PayrollConfirmRequestDto confirmDto) {
        // Step 1: Initial non-locking read
        PayrollDto initial = payrollMapper.findById(payrollId);
        if (initial == null) throw new IllegalArgumentException("Not found: " + payrollId);
        Long initialStaffId = initial.getStoreStaffId();
        Long initialStoreId = initial.getStoreId();

        // Step 2: Global Level 1 Parent Lock (store_staff)
        payrollMapper.lockStoreStaffForUpdate(initialStaffId);

        // Step 3: Global Level 2 Child Lock (payroll)
        PayrollDto existing = payrollMapper.findByIdForUpdate(payrollId);
        if (existing == null) throw new IllegalArgumentException("Not found: " + payrollId);

        // Step 3-1: TOCTOU Verification
        if (!initialStaffId.equals(existing.getStoreStaffId())) {
            throw new IllegalStateException("StoreStaff transfer detected. Aborting.");
        }
        if (!initialStoreId.equals(existing.getStoreId())) {
            throw new IllegalStateException("Store transfer detected. Aborting.");
        }
        if (!"DRAFT".equals(existing.getPayrollStatus())) {
            throw new IllegalStateException("Already confirmed: " + existing.getPayrollStatus());
        }

        // Step 4 & 5: Period validation & unresolved attendance guard
        if (!LocalDate.now().isAfter(existing.getRangeEndDate())) throw new IllegalStateException("Period not ended.");
        if (payrollMapper.countUnresolvedAttendance(existing.getStoreStaffId(), existing.getRangeStartDate(), existing.getRangeEndDate()) > 0) {
            throw new IllegalStateException("Unresolved attendance pending.");
        }

        // Step 6: Recalculate under lock
        PayrollDto recalculated = calculatePayrollForStaff(existing.getStoreId(), existing.getStoreStaffId(), existing.getTargetYearMonth(), existing.getRangeStartDate(), existing.getRangeEndDate());
        if (confirmDto != null && confirmDto.getExpectedGrossPay() != null && confirmDto.getExpectedGrossPay() != recalculated.getGrossPay()) {
            throw new IllegalStateException("Amount mismatch.");
        }

        payrollMapper.updatePayrollStatusConfirm(payrollId, LocalDateTime.now());
    }

    // 2. confirmMonthlyPayroll (Store-wide Monthly Confirmation with Two-Phase Locking)
    @Transactional
    public int confirmMonthlyPayroll(Long storeId, String targetYearMonth) {
        // [Step 1] Determine target DRAFT staff IDs in deterministic ASC order
        List<Long> draftStaffIds = payrollMapper.findDraftStaffIdsByStoreAndMonth(storeId, targetYearMonth);
        if (draftStaffIds == null || draftStaffIds.isEmpty()) {
            List<PayrollDto> allPayrolls = payrollMapper.findPayrollsByStoreAndMonth(storeId, targetYearMonth);
            if (allPayrolls == null || allPayrolls.isEmpty()) {
                throw new IllegalStateException("No payroll data for " + targetYearMonth);
            } else {
                throw new IllegalStateException("All payrolls already confirmed for " + targetYearMonth);
            }
        }

        // [Step 2] Global Level 1 Parent Lock (store_staff) in ASC order
        for (Long staffId : draftStaffIds) {
            payrollMapper.lockStoreStaffForUpdate(staffId);
        }

        // [Step 3] Global Level 2 Child Lock (payroll) re-query
        List<PayrollDto> payrolls = payrollMapper.lockStorePayrollsForUpdate(storeId, targetYearMonth);
        if (payrolls == null || payrolls.isEmpty()) {
            throw new IllegalStateException("No payroll data for " + targetYearMonth);
        }

        List<PayrollDto> draftPayrolls = payrolls.stream()
                .filter(p -> "DRAFT".equals(p.getPayrollStatus()))
                .toList();

        if (draftPayrolls.isEmpty()) {
            throw new IllegalStateException("All payrolls already confirmed.");
        }

        // [Step 3-1] Concurrency boundary check (unlocked new staff DRAFT detection)
        for (PayrollDto p : draftPayrolls) {
            if (!draftStaffIds.contains(p.getStoreStaffId())) {
                throw new IllegalStateException("New staff DRAFT created during lock acquisition: " + p.getStoreStaffId());
            }
        }

        LocalDate today = LocalDate.now();

        // [Step 4] Period validation & unresolved attendance guard for all
        for (PayrollDto p : draftPayrolls) {
            if (!today.isAfter(p.getRangeEndDate())) {
                throw new IllegalStateException("Period not ended: " + p.getRangeEndDate());
            }

            int unresolved = payrollMapper.countUnresolvedAttendance(p.getStoreStaffId(), p.getRangeStartDate(), p.getRangeEndDate());
            if (unresolved > 0) {
                throw new IllegalStateException("Unresolved attendance pending for staff: " + p.getStoreStaffId());
            }
        }

        // [Step 5] Atomic CAS status update
        return payrollMapper.updateAllPayrollStatusConfirm(storeId, targetYearMonth, LocalDateTime.now());
    }
}
