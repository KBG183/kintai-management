We need answer user's request. Need produce final with concise table Audit Item, Verdict, Finding, Required Evidence or Fix, then overall verdict. Need audit supplied bundle only. Need identify compile-level risks, MySQL fixture/cleanup correctness, contract history boundaries, labor time calculation and payroll oracle, rounding assumptions, whether test proves scope. State findings severity, exact evidence, fixes. Do not assume execution. Do not expand scope.

Need analyze code carefully.

We have Java test source. Need inspect compile-level risks. Let's parse.

Package com.example.kintai.domain.payroll.service;
Imports:
com.example.kintai.domain.attendance.dto.LaborDetailDto;
com.example.kintai.domain.attendance.service.LaborCalculationService;
com.example.kintai.domain.payroll.dto.PayrollDto;
org.junit.jupiter.api.AfterEach;
org.junit.jupiter.api.BeforeEach;
org.junit.jupiter.api.DisplayName;
org.junit.jupiter.api.Test;
org.springframework.beans.factory.annotation.Autowired;
org.springframework.boot.test.context.SpringBootTest;

javax.sql.DataSource;
java.sql.Connection;
java.sql.Date;
java.sql.PreparedStatement;
java.sql.ResultSet;
java.sql.Statement;
java.time.LocalDate;
java.time.LocalDateTime;
java.util.ArrayList;
java.util.List;
java.util.UUID;

static assertEquals, assertNotNull.

Class AnnualPayrollScenarioIntegrationTest.

Fields:
@Autowired DataSource dataSource;
@Autowired LaborCalculationService laborCalculationService;
@Autowired PayrollService payrollService;

private long memberId;
private long staffMemberId;
private long storeId;
private long staffId;
private long staffMemberId; // duplicate field? Yes: private long staffMemberId; appears twice? Let's inspect:
    private long memberId;
    private long staffMemberId;
    private long storeId;
    private long staffId;
    private long staffMemberId;
    private final List<Long> attendanceIds = new ArrayList<>();
Indeed duplicate field staffMemberId. Java compile error: variable staffMemberId is already defined in class. Severity blocker.

Also PayrollService not imported? It is in same package com.example.kintai.domain.payroll.service, so no import needed if class exists. OK.

ShiftCase record: private record ShiftCase(int month, int kind, int wage, int netMinutes, int nightMinutes, int overtimeMinutes, int breakMinutes, int breakCount) {}
Java 16+ records. OK if Java 16+.

CASES static final array. OK.

@BeforeEach seedAnnualScenario throws Exception.
Inside try (Connection connection = dataSource.getConnection()) { ... }
memberId = insertAndGetId(connection, "INSERT INTO member(email,password,name,phone,role) VALUES(?,?,?,?,'ROLE_OWNER')", "day11-" + UUID.randomUUID() + "@example.invalid", "test-only", "Day11 Owner", "0000000000");
storeId = insertAndGetId(connection, "INSERT INTO store(owner_id,store_name,invite_code,qr_token,pay_cutoff_day) VALUES(?,?,?,?,99)", memberId, "Day11 Annual Scenario", "d11-" + UUID.randomUUID(), "d11-" + UUID.randomUUID());
staffMemberId = insertAndGetId(connection, "INSERT INTO member(email,password,name,phone,role) VALUES(?,?,?,?,'ROLE_STAFF')", "day11-staff-" + UUID.randomUUID() + "@example.invalid", "test-only", "Day11 Staff", "0000000001");
staffId = insertAndGetId(connection, "INSERT INTO store_staff(store_id,member_id,hourly_wage,daily_transport_fee,monthly_transport_limit,joined_date) VALUES(?,?,1300,1200,1000,'2026-01-01')", storeId, staffMemberId);

Potential compile: insertAndGetId signature (Connection, String, Object... values). Passing long memberId autobox to Object. OK.

execute(connection, "INSERT INTO store_staff_contract_history(...)", staffId); OK.

Loop:
for (ShiftCase shift : CASES) {
    LocalDate date = LocalDate.of(2026, shift.month(), 15);
    LocalDateTime clockIn = date.atTime(shift.kind() == 2 ? 20 : 9, 0);
    LocalDateTime clockOut = shift.kind() == 2 ? date.plusDays(1).atTime(4, 0)
            : date.atTime(shift.kind() == 1 ? 19 : 18, 0);
    long attendanceId = insertAndGetId(connection,
            "INSERT INTO attendance(store_id,store_staff_id,work_date,clock_in_at,clock_out_at,total_break_minutes,net_work_minutes,night_work_minutes,overtime_minutes,clock_in_method,attendance_status) VALUES(?,?,?,?,?,?,?,?,?,'BUTTON','COMPLETED')",
            storeId, staffId, Date.valueOf(date), java.sql.Timestamp.valueOf(clockIn),
            java.sql.Timestamp.valueOf(clockOut), shift.breakMinutes(), shift.netMinutes(),
            shift.nightMinutes(), shift.overtimeMinutes());
    attendanceIds.add(attendanceId);
    insertBreaks(connection, attendanceId, date, shift);
}

Potential compile: Date.valueOf(date) returns java.sql.Date. OK.
Potential issue: attendance insert has 9 placeholders? Let's count columns: store_id, store_staff_id, work_date, clock_in_at, clock_out_at, total_break_minutes, net_work_minutes, night_work_minutes, overtime_minutes, clock_in_method, attendance_status = 11 columns. Values: ?,?,?,?,?,?,?,?,?,?,'BUTTON','COMPLETED' = 9 ? + 2 literals = 11. Values passed: storeId, staffId, Date, Timestamp, Timestamp, breakMinutes, netMinutes, nightMinutes, overtimeMinutes = 9. OK.

insertBreaks static void (Connection, long, LocalDate, ShiftCase) throws Exception. OK.

insertBreak static void (Connection, long, LocalDateTime, LocalDateTime) throws Exception. OK.

insertAndGetId static long (Connection, String, Object... values) throws Exception. OK.
execute static void (Connection, String, Object... values) throws Exception. OK.

Test method:
int annualGrossPay = 0;
int actualAnnualGrossPay = 0;
int annualWorkMinutes = 0;
int annualNightMinutes = 0;
int annualOvertimeMinutes = 0;

for (int i = 0; i < CASES.length; i++) {
    ShiftCase shift = CASES[i];
    LaborDetailDto labor = laborCalculationService.calculateDetailedLabor(attendanceIds.get(i));
    assertEquals(shift.netMinutes(), labor.getNetWorkMinutes(), "net minutes for month " + shift.month());
...
    LocalDate start = LocalDate.of(2026, shift.month(), 1);
    LocalDate end = start.withDayOfMonth(start.lengthOfMonth());
    String yearMonth = String.format("2026-%02d", shift.month());
    PayrollDto payroll = payrollService.calculatePayrollForStaff(storeId, staffId, yearMonth, start, end);

    int expectedBase = shift.wage() * shift.netMinutes() / 60;
...
    assertNotNull(payroll.getPayrollId(), "persisted payroll for month " + shift.month());
    assertEquals(1, payroll.getWorkDaysCount(), "work days for month " + shift.month());
...
}

Potential compile: assertEquals(int, int?) Need know DTO getter return types. Not supplied. Could be Integer or int. JUnit assertEquals has overloads for (int, int) and (Object, Object) etc. If getter returns Integer, assertEquals(int, Integer) ambiguous? Let's examine. JUnit 5 Assertions has assertEquals(int expected, int actual), assertEquals(Object expected, Object actual), etc. If actual is Integer, expected int, Java chooses? There is no assertEquals(int, Integer) exact. It may choose assertEquals(Object, Object) after boxing expected to Integer? Or assertEquals(int, int) if unboxing actual? Method invocation: applicable by subtyping? For assertEquals(int, int), actual Integer can unbox, expected int exact. For assertEquals(Object, Object), expected int boxes, actual Integer. Both applicable? Java overload resolution: phase 1 no boxing/unboxing: expected int not subtype Object? int primitive not subtype Object, so not applicable. Phase 2 allows boxing/unboxing: both applicable. Most specific? Need compare. Could be ambiguous? JUnit has many overloads. But common code assertEquals(1, payroll.getWorkDaysCount()) compiles if getWorkDaysCount returns Integer? I think yes? Let's check: Assertions.assertEquals(int expected, int actual) and assertEquals(Object expected, Object actual). For args (int, Integer): phase 2: (int,int) applicable via unboxing actual; (Object,Object) applicable via boxing expected. Which more specific? Method m1(int,int) vs m2(Object,Object). For parameter types, int vs Object: int is not subtype of Object? In method specificity with boxing? Might be ambiguous? Actually Java allows autounboxing. I think it compiles to assertEquals(int,int) because primitive is more specific? Need not overstate. But compile-level risk due to unknown DTO getter types. But we can state if getters return Integer/int as expected. Since not supplied, cannot verify. But audit should inspect supplied bundle only. Missing DTO definitions means cannot prove compile. But likely expected to identify obvious duplicate field. Also PayrollService class not in bundle but same package; cannot verify method signature calculatePayrollForStaff. LaborCalculationService method calculateDetailedLabor. Need note compile-level blockers: duplicate field definite. Missing imports? PayrollService same package OK. But if PayrollService not in same package? It is in payroll.service, test package same. OK.

Potential compile: record accessor shift.kind() etc OK.

Potential compile: assertEquals(3 * ((8 * 1300 + 1000) + ...), annualGrossPay); int multiplication may overflow? Let's compute: 8*1300=10400 +1000=11400; 9*1300=11700 + 1300/4=325 +1000=13025? Wait 11700+325+1000=13025; 8*1300=10400 + 3*1300/2? 3*1300=3900/2=1950 +1000=13350; sum first three = 37775. Then 8*1400=11200+1000=12200; 9*1400=12600+350+1000=13950; 8*1400=11200 + 3*1400/2=2100 +1000=14300; sum second=40450; total per quarter? 37775+40450=78225; *3=234675. Fits int. OK.

But expected annual formula: 3 * ((8*1300+1000) + (9*1300+1300/4+1000) + (8*1300+3*1300/2+1000) + (8*1400+1000) + (9*1400+1400/4+1000) + (8*1400+3*1400/2+1000)). This assumes each case repeated 3 times? Cases: months 1-12: kinds 0,1,2,3,0,1,2,3,0,1,2,3. Wages: Jan-Jun 1300, Jul-Dec 1400. For each wage half, each kind appears 3 times? Kind 0 months 1,5,9: wages 1300,1300,1400? Wait kind 0 appears months 1 (1300), 5 (1300), 9 (1400). Kind 1 months 2 (1300),6 (1300),10 (1400). Kind 2 months 3 (1300),7 (1400),11 (1400). Kind 3 months 4 (1300),8 (1400),12 (1400). The annual formula groups by kind and wage? It has 3 * ((8*1300+1000) + (9*1300+1300/4+1000) + (8*1300+3*1300/2+1000) + (8*1400+1000) + (9*1400+1400/4+1000) + (8*1400+3*1400/2+1000)). That is 3 times sum of six terms: for 1300 kind0, kind1, kind2, and 1400 kind0, kind1, kind2? But missing 1300 kind3 and 1400 kind3? Wait terms: (8*1300+1000) = kind0 480 net no extras. (9*1300+1300/4+1000) = kind1 540 net + 60 overtime? 9*1300 = 540/60*1300; 1300/4 = 60/240*1300. (8*1300+3*1300/2+1000) = kind2 480 net + 360 night? 360/240=1.5 => 3*1300/2. Then (8*1400+1000), (9*1400+1400/4+1000), (8*1400+3*1400/2+1000). That's only 6 terms, multiplied by 3. But actual 12 months include kind3 (two breaks, no night/overtime, net 480) for months 4,8,12. Kind3 gross = 8*wage+1000, same as kind0. The formula has kind0 terms for 1300 and 1400, but kind3 also same gross. Multiplying by 3? Let's see: If each of six terms repeated 3 times, that would be 18 months? But maybe they consider cases repeat quarterly: 4 cases? Wait expected scenario: Cases repeat quarterly: 480 net ordinary; 540 net with 60 overtime; 480 net night; 480 net with two breaks. That's 4 cases. But CASES has 12 entries: months 1-12: kind 0,1,2,3,0,1,2,3,0,1,2,3. So each kind appears 3 times. Wages: Jan-Jun 1300, Jul-Dec 1400. So kind0 appears months 1,5,9: wages 1300,1300,1400. kind1 months 2,6,10: 1300,1300,1400. kind2 months 3,7,11: 1300,1400,1400. kind3 months 4,8,12: 1300,1400,1400.

Annual expected by actual cases:
For 1300: kind0 months 1,5 = 2*(8*1300+1000); kind1 months 2,6 = 2*(9*1300+1300/4+1000); kind2 month3 = 1*(8*1300+3*1300/2+1000); kind3 month4 = 1*(8*1300+1000). Total 1300 half = 3*(8*1300+1000) + 2*(9*1300+1300/4+1000) + 1*(8*1300+3*1300/2+1000).
For 1400: kind0 month9 =1*(8*1400+1000); kind1 month10=1*(9*1400+1400/4+1000); kind2 months7,11=2*(8*1400+3*1400/2+1000); kind3 months8,12=2*(8*1400+1000). Total 1400 half = 3*(8*1400+1000)+1*(9*1400+1400/4+1000)+2*(8*1400+3*1400/2+1000).
Total = 3*(8*1300+1000)+2*(9*1300+1300/4+1000)+1*(8*1300+3*1300/2+1000)+3*(8*1400+1000)+1*(9*1400+1400/4+1000)+2*(8*1400+3*1400/2+1000).
The test's annual formula = 3*[(8*1300+1000)+(9*1300+1300/4+1000)+(8*1300+3*1300/2+1000)+(8*1400+1000)+(9*1400+1400/4+1000)+(8*1400+3*1400/2+1000)] = 3*1300kind0 +3*1300kind1 +3*1300kind2 +3*1400kind0 +3*1400kind1 +3*1400kind2. It omits kind3 entirely and overcounts kind1/kind2 for 1300? Let's compare to actual:
Actual coefficients:
1300 kind0: 3 (months 1,5,4? kind3 same as kind0 but formula separate? If kind3 gross same as kind0, actual gross coefficient for 8*1300+1000 is kind0 months 1,5 + kind3 month4 =3. OK formula has 3*1300kind0. Good if kind3 same as kind0.
1300 kind1: actual 2, formula 3. Over by 1.
1300 kind2: actual 1, formula 3. Over by 2.
1400 kind0+kind3: actual kind0 month9 + kind3 months8,12 =3, formula 3*1400kind0. OK.
1400 kind1: actual 1, formula 3. Over by 2.
1400 kind2: actual 2, formula 3. Over by 1.
So annual formula is wrong relative to CASES and expected scenario? Wait maybe they intended cases repeat quarterly: 4 cases per quarter, but wages change Jan-Jun/Jul