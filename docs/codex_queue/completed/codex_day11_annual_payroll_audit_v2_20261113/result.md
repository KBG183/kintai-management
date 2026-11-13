We need answer user's request. Need produce final only table and verdict, max 700 words. Need audit code/scenario. Need identify compile blockers, fixture/cleanup risks, contract-period boundary, labor calculations, monthly/annual oracle, rounding, claimed E2E scope. Do not say tests ran. Need use only supplied bundle. Need at most 700 words. Need table columns Severity, Finding, Exact Evidence, Required Fix. Then short overall verdict.

Need analyze deeply.

We have scenario oracle: Twelve shifts dated 15th each month 2026; wages 1300 Jan-Jun, 1400 Jul-Dec; effective ranges [2026-01-01, 2026-07-01) and [2026-07-01, infinity). Shift kinds repeat 0,1,2,3: (0) 480 daytime net minutes with one 60-minute break; (1) 540 net minutes with 60 overtime and a 60-minute break; (2) 20:00-next-day 04:00, 480 net and 360 night minutes; (3) 480 net daytime minutes with two 30-minute breaks. Daily transport 1200 yen, monthly cap 1000 yen. Oracle formulas: base=wage*net/60; night=wage*night/240; overtime=wage*overtime/240; gross=base+night+overtime+1000. Actual count per wage: at 1300, 3 ordinary-equivalent, 2 overtime, 1 night; at 1400, 3 ordinary-equivalent, 1 overtime, 2 night.

Mapper query:
SELECT hourly_wage FROM store_staff_contract_history WHERE store_staff_id = ? AND effective_from <= ? AND (effective_to IS NULL OR effective_to > ?) ORDER BY effective_from DESC LIMIT 1

Test source: SpringBootTest, seeds member, store, staff, contract history, attendance for 12 months. Cleanup deletes payroll details, payroll, attendance_break, attendance, contract history, store_staff, store, members. Test calculates labor and payroll for each month, asserts.

Need audit only code and scenario. Need likely findings:
- Compile blockers? Need inspect imports and types. Test uses PayrollService but no import? It imports LaborCalculationService, PayrollDto, etc. It uses PayrollService without import? In same package? Test package com.example.kintai.domain.payroll.service; PayrollService likely same package, so no import needed. OK.
- Uses java.sql.Date, Timestamp, etc. OK.
- Uses record ShiftCase inside class. Java 16+? OK maybe.
- Uses @SpringBootTest without context configuration. OK.
- Potential compile blocker: `payrollService.calculatePayrollForStaff(storeId, staffId, yearMonth, start, end)` signature unknown. Not compile blocker from supplied? We can't assume. But audit only supplied bundle; if method not shown, cannot verify. But maybe no compile blocker.
- `labor.getAppliedHourlyWage()` returns? assertEquals(int, Integer?) OK if Integer. `payroll.getWorkDaysCount()` maybe int. OK.
- `payroll.getPayrollId()` maybe Long; assertNotNull OK.
- `payroll.getBasePay()` etc maybe int. OK.
- `payroll.getGrossPay()` int. OK.
- `laborCalculationService.calculateDetailedLabor(attendanceIds.get(i))` expects Long? OK.
- `insertAndGetId` with `Object... values` and `statement.setObject(i+1, values[i])`. For LocalDate? It passes Date.valueOf(date) for work_date, Timestamp for clock. OK.
- `execute(connection, "INSERT INTO store_staff_contract_history...", staffId);` OK.
- `cleanAnnualScenario`: if staffId == 0 return. But if seed fails after staffId set? cleanup partial. If seed fails before staffId set, no cleanup. But fixture risk.
- Cleanup order: deletes payroll_daily_detail where payroll_id in (select payroll_id from payroll where store_staff_id=?). But if payroll has FK to store_staff? OK. Deletes attendance_break before attendance. Deletes store_staff before store. Deletes members. But does not delete invite/qr? store deleted. OK.
- Potential cleanup risk: `DELETE FROM payroll_daily_detail WHERE payroll_id IN (SELECT payroll_id FROM payroll WHERE store_staff_id=?)` if no payroll rows, OK. But if payroll_daily_detail has FK to attendance? Not relevant.
- Potential fixture risk: `pay_cutoff_day` 99 maybe invalid? Could violate check constraint? Unknown.
- Potential fixture risk: `store_staff` insert includes hourly_wage 1300 but contract history also. If payroll uses store_staff.hourly_wage fallback? But mapper uses contract history. OK.
- Contract-period boundary: Mapper uses `effective_from <= workDate AND (effective_to IS NULL OR effective_to > workDate)`. Scenario says ranges [2026-01-01, 2026-07-01) and [2026-07-01, infinity). For workDate 2026-07-01, first has effective_to = 2026-07-01, condition effective_to > workDate false, so excluded; second effective_from <= true, effective_to null true. Good. For 2026-06-30, first included. For 2026-01-01 included. For before 2026-01-01 none. But test dates 15th. OK. However if workDate is date only? Mapper parameter `#{workDate}` maybe LocalDate. SQL compares date. OK.
- But mapper query `ORDER BY effective_from DESC LIMIT 1` with overlapping histories could pick latest effective_from even if not effective? It filters effective. OK.
- Potential boundary issue: `effective_to` is exclusive? Query uses > workDate, so yes. Scenario says [from,to). Good.
- But if effective_to is date 2026-07-01 and workDate is 2026-07-01 00:00? If workDate is LocalDate, OK. If LocalDateTime, effective_to date cast? Could be issue. Not enough.
- Labor calculations: Test seeds attendance with net, night, overtime, breaks. But clock in/out times may not align with net minutes and breaks? Need check.
Case kind 0: clockIn 9:00, clockOut 18:00, break 60, net 480. 9h -1h =8h=480. OK.
Kind 1: clockIn 9:00, clockOut 19:00, break 60, net 540, overtime 60. 10h-1h=9h=540. Overtime 60? If standard 8h, yes. OK.
Kind 2: clockIn 20:00, clockOut next 04:00, break 0, net 480, night 360. 8h total. Night minutes? 20:00-04:00 maybe night 22:00-05:00? 360? If night defined 22-5, from 22 to 4 = 6h=360. OK. But net 480 includes 20-24 (4h) + 0-4 (4h) =8h. OK.
Kind 3: clockIn 9:00, clockOut 18:00, two 30 breaks, net 480. OK.
But `insertBreaks` for kind 2 none. OK.
Potential issue: For kind 2, clockOut next day 04:00, but work_date is date. If labor calculation uses work_date and clock times, OK. But attendance_break none.
- Monthly/annual oracle: Test expectedBase = wage * net / 60 integer division. Oracle formula same. But independentAnnualOracle uses expressions:
3 * (8 * 1300 + 1000) + 2 * (9 * 1300 + 1300 / 4 + 1000) + (8 * 1300 + 3 * 1300 / 2 + 1000) + 3 * (8 * 1400 + 1000) + (9 * 1400 + 1400 / 4 + 1000) + 2 * (8 * 1400 + 3 * 1400 / 2 + 1000)
Need compare to scenario counts. At 1300: 3 ordinary-equivalent (kind0 and kind3? Actually ordinary-equivalent maybe net 480 no OT/night: months 1,4,5? Wait CASES: 1 kind0, 2 kind1, 3 kind2, 4 kind3, 5 kind0, 6 kind1. At 1300: kind0 months 1,5 =2; kind3 month4=1 => 3 ordinary-equivalent. Overtime kind1 months2,6=2. Night kind3? kind2 month3=1. OK.
At 1400: months7 kind2,8 kind3,9 kind0,10 kind1,11 kind2,12 kind3. Ordinary-equivalent: kind3 month8, kind0 month9, kind3 month12 =3. Overtime: month10=1. Night: months7,11=2. OK.
Independent oracle: 3*(8*1300+1000) for ordinary 1300. 2*(9*1300 + 1300/4 +1000) for overtime: 9h base + 15h? Wait overtime 60 min allowance = wage*60/240 = wage/4. Yes. Night: (8*1300 + 3*1300/2 +1000) for 360 night = 360/240=1.5 wage? 3*1300/2 integer? 3*1300=3900/2=1950. wage*360/240 = 1300*1.5=1950. OK.
At 1400: 3 ordinary, 1 overtime, 2 night. OK.
But monthly expected uses integer division per component. Independent oracle uses integer division per component too? For 1300/4 = 325 exact. 3*1300/2 = 1950 exact. For 1400/4=350, 3*1400/2=2100. OK. No rounding issue for these values. But if wage not divisible, integer division truncates. Oracle formula says base=wage*net/60; night=wage*night/240; overtime=wage*overtime/240. In Java int division truncates. OK.
- Rounding: Test uses int division. If service uses BigDecimal/rounding, mismatch. But scenario oracle uses integer formulas. Need finding? Maybe required fix: specify rounding/truncation and use same. But for given values no fractional. However base for 540*1300/60 = 11700 exact; 480*1300/60=10400 exact. Night 360*1300/240=1950 exact. Overtime 60*1300/240=325 exact. All exact. So no rounding risk for fixture. But audit should note integer division truncation not specified; if service rounds, could fail. Required fix: align rounding or use exact values.
- Claimed E2E scope: Test is @SpringBootTest, seeds DB, calls services, asserts. But does it exercise full E2E? It bypasses API/controller, auth, UI, clock-in method? It inserts attendance directly with BUTTON and COMPLETED. It does not test attendance creation/clock-in/out, break capture, payroll trigger, persistence via API. It also directly calls payrollService.calculatePayrollForStaff, not end-to-end from attendance to payroll via scheduled job? It claims Day 11 annual payroll scenario maybe E2E? Need finding: claimed E2E scope overstated; it's service-level integration, not full E2E. Required fix: rename/scope or add API/controller tests.
- Compile blockers: Need identify actual compile blockers in supplied code. Let's scrutinize.
Imports:
import com.example.kintai.domain.attendance.dto.LaborDetailDto;
import com.example.kintai.domain.attendance.service.LaborCalculationService;
import com.example.kintai.domain.payroll.dto.PayrollDto;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

Class uses `PayrollService` no import. If PayrollService in same package `com.example.kintai.domain.payroll.service`, OK. Test package is that. Good.
Uses `LaborDetailDto` methods: getNetWorkMinutes, getTotalNightMinutes, getTotalOvertimeMinutes, getAppliedHourlyWage. Not shown but assumed.
Uses `PayrollDto` methods: getPayrollId, getWorkDaysCount, getTotalWorkMinutes, getTotalNightMinutes, getTotalOvertimeMinutes, getBasePay, getNightAllowance, getOvertimeAllowance, getTransportAllowance, getGrossPay. Not shown.
Potential compile blocker: `assertEquals(shift.wage(), labor.getAppliedHourlyWage(), "contract wage for month " + shift.month());` If getAppliedHourlyWage returns Integer, assertEquals(int, Integer) ambiguous? JUnit 5 has assertEquals(int expected, int actual) and assertEquals(Object, Object). If actual is Integer, expected int, Java may choose assertEquals(int, int) unboxing? There is also assertEquals(Object, Object). Overload resolution: expected int can widen to Integer? No boxing. Candidate methods: assertEquals(int, int), assertEquals(Object, Object), maybe assertEquals(Integer, Integer)? JUnit 5 Assertions has assertEquals(int expected, int actual, String message). It also has assertEquals(Object expected, Object actual, String message). For args (int, Integer, String), applicable by subtyping? For (int,int): second arg Integer can unbox to int, applicable by method invocation conversion (unboxing). For (Object,Object): first int boxes to Object, second Integer to Object. Both applicable. More specific? JLS: most specific method. (int,int) vs (Object,Object). int is not subtype of Object? For primitive vs reference, neither? There may be ambiguity? Actually Java overload resolution with primitive and reference can be tricky. But common code `assertEquals(1, someInteger)` compiles? I think yes, chooses assertEquals(int, int) if unboxing? Let's recall JUnit 5 has `assertEquals(int expected, int actual, String message)`. If actual is Integer, it unboxes. It compiles. OK.
`assertEquals(1, payroll.getWorkDaysCount(), ...)` if getWorkDaysCount returns Integer, OK.
`assertEquals(1000, payroll.getTransportAllowance(), ...)` OK.
`assertEquals(expectedBase, payroll.getBasePay(), ...)` if getBasePay returns Integer, OK.
`assertEquals(independentAnnualOracle, annualGrossPay);` both int.
`assertEquals(12 * 480 + 3 * 60, annualWorkMinutes);` int.
Potential compile blocker: `payrollService.calculatePayrollForStaff(storeId, staffId, yearMonth, start, end)` returns PayrollDto. If method not exists, compile blocker but not in bundle. We can only audit supplied code; maybe no.
Potential compile blocker: `insertAndGetId(connection, "INSERT INTO member(email,password,name,phone,role) VALUES(?,?,?,?,'ROLE_OWNER')", "day11-" + UUID.randomUUID() + "@example.invalid", "test-only", "Day11 Owner", "0000000000");` The SQL has 4 placeholders plus literal role. OK.
`insertAndGetId(connection, "INSERT INTO store(owner_id,store_name,invite_code,qr_token,pay_cutoff_day) VALUES(?,?,?,?,99)", memberId, "Day11 Annual Scenario", "d11-" + UUID.randomUUID(), "d11-" + UUID.randomUUID());` OK.
`insertAndGetId(connection, "INSERT INTO store_staff(store_id,member_id,hourly_wage,daily_transport_fee,monthly_transport_limit,joined_date) VALUES(?,?,1300,1200,1000,'2026-01-01')", storeId, staffMemberId);` OK.
`execute(connection, "INSERT INTO store_staff_contract_history(store_staff_id,effective_from,effective_to,hourly_wage,daily_transport_fee,monthly_transport_limit) VALUES(?, '2026-01-01','2026-07-01',1300,1200,1000)", staffId);` OK.
`insertAndGetId(connection, "INSERT INTO attendance(...) VALUES(?,?,?,?,?,?,?,?,?,'BUTTON','COMPLETED')", storeId, staffId, Date.valueOf(date), java.sql.Timestamp.valueOf(clockIn), java.sql.Timestamp.valueOf(clockOut), shift.breakMinutes(), shift.netMinutes(), shift.nightMinutes(), shift.overtimeMinutes());` Count placeholders: attendance columns: store_id, store_staff_id, work_date, clock_in_at, clock_out_at, total_break_minutes, net_work_minutes, night_work_minutes, overtime_minutes, clock_in_method, attendance_status = 11. Placeholders 9 + 2 literals = 11. Values: storeId, staffId, Date, Timestamp, Timestamp, breakMinutes, netMinutes, nightMinutes, overtimeMinutes =9. OK.
`insertBreak` uses duration_minutes int. OK.
Potential compile blocker: `private record ShiftCase(int month, int kind, int wage, int netMinutes, int nightMinutes, int overtimeMinutes, int breakMinutes, int breakCount) {}` inside class. OK Java 16.
Potential compile blocker: `@SpringBootTest` requires Spring Boot test dependency. Not in code.
Potential compile blocker: `PayrollService` may be interface/class not imported. Same package.
Potential compile blocker: `laborCalculationService.calculateDetailedLabor(attendanceIds.get(i));` attendanceIds is List<Long>, get returns Long. OK.
Potential compile blocker: `payrollService.calculatePayrollForStaff(storeId, staffId, yearMonth, start, end);` start/end LocalDate. OK if signature.
Potential compile blocker: `payroll.getPayrollId()` maybe returns Long; assertNotNull OK.
Potential compile blocker: `payroll.getWorkDaysCount()` maybe returns Integer; assertEquals(1, ...) OK.
Potential compile blocker: `payroll.getTotalWorkMinutes()` etc.
No definite compile blocker from code alone. But user asks review compile blockers. Maybe there is a compile blocker: `PayrollService` is not imported and not in same package? Wait test package is `com.example.kintai.domain.payroll.service`. PayrollService