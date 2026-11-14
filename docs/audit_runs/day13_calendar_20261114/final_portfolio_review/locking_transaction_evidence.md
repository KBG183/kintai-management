
===== src/main/java/com/example/kintai/domain/attendance/service/AttendanceService.java lines 17-47 =====
import java.util.Map;

@Service
@Transactional(readOnly = true)
public class AttendanceService {

    private final AttendanceMapper attendanceMapper;

    public AttendanceService(AttendanceMapper attendanceMapper) {
        this.attendanceMapper = attendanceMapper;
    }

    /**
     * リアルタイム打刻処理（出勤・休憩開始・休憩終了・退勤）
     */
    @Transactional
    public AttendanceDto punch(AttendancePunchDto punchDto) {
        Long storeStaffId = punchDto.getStoreStaffId();
        String punchType = punchDto.getPunchType();
        LocalDateTime now = LocalDateTime.now();
        LocalDate today = now.toLocalDate();

        // 排他制御: 同時打刻の競合を防止する悲観的ロック
        attendanceMapper.lockStoreStaffForUpdate(storeStaffId);

        AttendanceDto active = attendanceMapper.findActiveAttendance(storeStaffId);

        switch (punchType) {
            case "CLOCK_IN":
                return handleClockIn(punchDto, today, now, active);


===== src/main/resources/mapper/attendance/AttendanceMapper.xml lines 143-151 =====

    <select id="lockStoreStaffForUpdate" resultType="java.lang.Long">
        SELECT store_staff_id
        FROM store_staff
        WHERE store_staff_id = #{storeStaffId}
        FOR UPDATE
    </select>

    <select id="findStoreIdByStoreStaffId" resultType="java.lang.Long">

===== src/main/java/com/example/kintai/domain/payroll/service/PayrollService.java lines 18-43 =====
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


===== src/main/java/com/example/kintai/domain/payroll/service/PayrollService.java lines 279-303 =====
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

===== src/main/resources/mapper/payroll/PayrollMapper.xml lines 80-96 =====
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


