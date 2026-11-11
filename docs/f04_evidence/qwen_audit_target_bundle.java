/**
 * [EVIDENCE BUNDLE FOR INDEPENDENT AUDITOR (QWEN 27B)]
 * Target: F-04 Concurrency & Lock Hierarchy Verification
 * Scope: PayrollService.java, PayrollMapper.xml, Concurrency Test Suite, InnoDB & Performance Schema Logs
 */

// 1. SERVICE IMPLEMENTATION (PayrollService.java)
public class PayrollServiceEvidence {

    // [Method 1: confirmPayroll with TOCTOU double-check]
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

        // Step 3-1: TOCTOU Verification (staff & store immutability check)
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

    // [Method 2: confirmMonthlyPayroll with Two-Phase Locking]
    @Transactional
    public int confirmMonthlyPayroll(Long storeId, String targetYearMonth) {
        // Phase 1: Determine target DRAFT staff IDs in deterministic ASC order
        List<Long> draftStaffIds = payrollMapper.findDraftStaffIdsByStoreAndMonth(storeId, targetYearMonth);
        if (draftStaffIds == null || draftStaffIds.isEmpty()) throw new IllegalStateException("No draft payrolls.");

        // Phase 1 Lock: Sequentially acquire Level 1 store_staff locks in ASC order
        for (Long staffId : draftStaffIds) {
            payrollMapper.lockStoreStaffForUpdate(staffId);
        }

        // Phase 2 Lock: Re-query child payroll records with Level 2 lock
        List<PayrollDto> payrolls = payrollMapper.lockStorePayrollsForUpdate(storeId, targetYearMonth);
        List<PayrollDto> draftPayrolls = payrolls.stream().filter(p -> "DRAFT".equals(p.getPayrollStatus())).toList();
        if (draftPayrolls.isEmpty()) throw new IllegalStateException("All confirmed.");

        // Boundary Check: Detect any new DRAFT created between Phase 1 and Phase 2
        for (PayrollDto p : draftPayrolls) {
            if (!draftStaffIds.contains(p.getStoreStaffId())) {
                throw new IllegalStateException("New staff DRAFT created during lock acquisition. Aborting for consistency.");
            }
        }

        // Period & attendance validation for all
        for (PayrollDto p : draftPayrolls) {
            if (!LocalDate.now().isAfter(p.getRangeEndDate())) throw new IllegalStateException("Period not ended.");
            if (payrollMapper.countUnresolvedAttendance(p.getStoreStaffId(), p.getRangeStartDate(), p.getRangeEndDate()) > 0) {
                throw new IllegalStateException("Unresolved attendance exists.");
            }
        }

        // Final atomic CAS update
        return payrollMapper.updateAllPayrollStatusConfirm(storeId, targetYearMonth, LocalDateTime.now());
    }
}

// 2. MAPPER SQL EXCERPTS (PayrollMapper.xml)
/*
<select id="lockStoreStaffForUpdate" resultType="java.lang.Long">
    SELECT store_staff_id FROM store_staff WHERE store_staff_id = #{storeStaffId} FOR UPDATE
</select>

<select id="findDraftStaffIdsByStoreAndMonth" resultType="java.lang.Long">
    SELECT DISTINCT store_staff_id FROM payroll
    WHERE store_id = #{storeId} AND target_year_month = #{targetYearMonth} AND payroll_status = 'DRAFT'
    ORDER BY store_staff_id ASC
</select>

<select id="lockStorePayrollsForUpdate" resultType="com.example.kintai.domain.payroll.dto.PayrollDto">
    SELECT payroll_id, store_id, store_staff_id, target_year_month, range_start_date, range_end_date, gross_pay, payroll_status
    FROM payroll
    WHERE store_id = #{storeId} AND target_year_month = #{targetYearMonth}
    ORDER BY store_staff_id ASC, payroll_id ASC
    FOR UPDATE
</select>

<update id="updateAllPayrollStatusConfirm">
    UPDATE payroll p
    SET p.payroll_status = 'CONFIRMED', p.confirmed_at = #{confirmedAt}, p.updated_at = CURRENT_TIMESTAMP
    WHERE p.store_id = #{storeId} AND p.target_year_month = #{targetYearMonth} AND p.payroll_status = 'DRAFT'
</update>
*/

// 3. PHYSICAL EVIDENCE SUMMARY
/*
- Deadlock 1213 reproduction confirmed in Run-2 (Thread 1112/1113, Trx 4266/4267, show_engine_innodb_status_deadlock.txt).
- Service path concurrency test passed: zero 1213 deadlocks across concurrent executions.
- Two-Phase Locking implemented: store_staff pre-locking (ASC) eliminates inter-table deadlocks with calculatePayrollForStaff.
- Unhandled race risk: What if a new DRAFT is created after Step 3 query but before Step 5 update?
*/
