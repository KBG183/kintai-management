package com.example.kintai.domain.attendance.service;

import com.example.kintai.domain.attendance.dto.AttendanceBreakDto;
import com.example.kintai.domain.attendance.dto.AttendanceDto;
import com.example.kintai.domain.attendance.dto.LaborDetailDto;
import com.example.kintai.domain.attendance.dto.LaborSimulationRequestDto;
import com.example.kintai.domain.attendance.mapper.AttendanceMapper;
import com.example.kintai.domain.attendance.util.AttendanceTimeCalculator;
import com.example.kintai.domain.payroll.util.PayrollCalculator;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 日本労働基準法第37条に基づく時間外・深夜・重畳割増（150%）および手当精密算定サービス。
 */
@Service
@Transactional(readOnly = true)
public class LaborCalculationService {

    private final AttendanceMapper attendanceMapper;

    @Value("${kintai.legal.min-hourly-wage:1280}")
    private int minHourlyWage = 1280;

    public LaborCalculationService(AttendanceMapper attendanceMapper) {
        this.attendanceMapper = attendanceMapper;
    }

    /**
     * 登録済み勤怠実績IDに基づく労働基準法第37条精密内訳の算定
     */
    public LaborDetailDto calculateDetailedLabor(Long attendanceId) {
        if (attendanceId == null) {
            throw new IllegalArgumentException("勤怠IDは必須です。");
        }

        AttendanceDto attendance = attendanceMapper.findById(attendanceId);
        if (attendance == null) {
            throw new IllegalArgumentException("指定された勤怠実績が存在しません。(ID: " + attendanceId + ")");
        }

        if (attendance.getClockOutAt() == null) {
            throw new IllegalStateException("退勤打刻が完了していない勤怠データは労働法集計できません。");
        }

        List<AttendanceBreakDto> breakEntities = attendanceMapper.findBreaksByAttendanceId(attendanceId);
        List<AttendanceTimeCalculator.BreakInterval> breakIntervals = new ArrayList<>();
        if (breakEntities != null) {
            for (AttendanceBreakDto b : breakEntities) {
                breakIntervals.add(new AttendanceTimeCalculator.BreakInterval(b.getBreakStartAt(), b.getBreakEndAt()));
            }
        }
        // デモデータや事後修正等で休憩個別レコードが無く合計休憩分のみ存在する場合のフォールバック
        if (breakIntervals.isEmpty() && attendance.getTotalBreakMinutes() != null && attendance.getTotalBreakMinutes() > 0) {
            int breakMin = attendance.getTotalBreakMinutes();
            long totalSpan = java.time.Duration.between(attendance.getClockInAt(), attendance.getClockOutAt()).toMinutes();
            if (totalSpan > breakMin) {
                long offset = (totalSpan - breakMin) / 2;
                LocalDateTime bStart = attendance.getClockInAt().plusMinutes(offset);
                LocalDateTime bEnd = bStart.plusMinutes(breakMin);
                breakIntervals.add(new AttendanceTimeCalculator.BreakInterval(bStart, bEnd));
            }
        }

        // 分割勤務（中抜け）対応: 同一スタッフ・同日の先行完了実労働時間の秒精度累計を取得し給与計算と整合
        long priorWorkSeconds = attendanceMapper.sumPriorNetWorkSecondsOnDate(
                attendance.getStoreStaffId(),
                attendance.getWorkDate(),
                attendance.getAttendanceId(),
                attendance.getClockInAt()
        );

        AttendanceTimeCalculator.DetailedCalculationResult calc = AttendanceTimeCalculator.calculateDetailed(
                attendance.getClockInAt(),
                attendance.getClockOutAt(),
                breakIntervals,
                priorWorkSeconds
        );

        Integer hourlyWageVal = attendanceMapper.findHourlyWageForStaffOnDate(
                attendance.getStoreStaffId(),
                attendance.getWorkDate()
        );
        int hourlyWage = (hourlyWageVal != null) ? hourlyWageVal : minHourlyWage;

        PayrollCalculator.DetailedDailyPayResult pay = PayrollCalculator.calculateDetailedDailyPay(hourlyWage, calc);
        String staffName = attendanceMapper.findStaffNameByStoreStaffId(attendance.getStoreStaffId());

        return mapToDto(attendance, staffName, hourlyWage, calc, pay);
    }

    /**
     * 任意の日時および時給に基づく労働基準法第37条シミュレーション算定
     */
    public LaborDetailDto simulateLabor(LaborSimulationRequestDto request) {
        if (request == null || request.getClockInAt() == null || request.getClockOutAt() == null) {
            throw new IllegalArgumentException("シミュレーションの出退勤日時は必須です。");
        }

        int hourlyWage = (request.getHourlyWage() != null && request.getHourlyWage() > 0)
                ? request.getHourlyWage()
                : minHourlyWage;

        List<AttendanceTimeCalculator.BreakInterval> breakIntervals = new ArrayList<>();
        if (request.getBreaks() != null) {
            for (LaborSimulationRequestDto.SimulationBreakDto b : request.getBreaks()) {
                if (b.getStartAt() != null && b.getEndAt() != null) {
                    breakIntervals.add(new AttendanceTimeCalculator.BreakInterval(b.getStartAt(), b.getEndAt()));
                }
            }
        }

        AttendanceTimeCalculator.DetailedCalculationResult calc = AttendanceTimeCalculator.calculateDetailed(
                request.getClockInAt(),
                request.getClockOutAt(),
                breakIntervals
        );

        PayrollCalculator.DetailedDailyPayResult pay = PayrollCalculator.calculateDetailedDailyPay(hourlyWage, calc);

        AttendanceDto dummyAtt = new AttendanceDto();
        dummyAtt.setWorkDate(request.getClockInAt().toLocalDate());
        dummyAtt.setClockInAt(request.getClockInAt());
        dummyAtt.setClockOutAt(request.getClockOutAt());

        return mapToDto(dummyAtt, "シミュレーション試算", hourlyWage, calc, pay);
    }

    private LaborDetailDto mapToDto(
            AttendanceDto attendance,
            String staffName,
            int hourlyWage,
            AttendanceTimeCalculator.DetailedCalculationResult calc,
            PayrollCalculator.DetailedDailyPayResult pay
    ) {
        LaborDetailDto dto = new LaborDetailDto();
        dto.setAttendanceId(attendance.getAttendanceId());
        dto.setStoreStaffId(attendance.getStoreStaffId());
        dto.setStaffName(staffName);
        dto.setWorkDate(attendance.getWorkDate());
        dto.setClockInAt(attendance.getClockInAt());
        dto.setClockOutAt(attendance.getClockOutAt());

        // 時間区分（分）
        dto.setTotalStayMinutes(calc.totalStayMinutes());
        dto.setTotalBreakMinutes(calc.totalBreakMinutes());
        dto.setNetWorkMinutes(calc.netWorkMinutes());
        dto.setRegularWorkMinutes(calc.regularWorkMinutes());
        dto.setRegularNightWorkMinutes(calc.regularNightWorkMinutes());
        dto.setOvertimeWorkMinutes(calc.overtimeWorkMinutes());
        dto.setNightOvertimeWorkMinutes(calc.nightOvertimeWorkMinutes());
        dto.setTotalNightMinutes(calc.nightWorkMinutes());
        dto.setTotalOvertimeMinutes(calc.overtimeMinutes());

        // 金額区分（円）
        dto.setAppliedHourlyWage(hourlyWage);
        dto.setRegularPay(pay.regularPay());
        dto.setRegularNightPay(pay.regularNightPay());
        dto.setOvertimePay(pay.overtimePay());
        dto.setNightOvertimePay(pay.nightOvertimePay());
        dto.setBasePay(pay.basePay());
        dto.setNightAllowance(pay.nightAllowance());
        dto.setOvertimeAllowance(pay.overtimeAllowance());
        dto.setTotalPay(pay.totalPay());

        return dto;
    }
}
