package com.example.kintai.domain.attendance.util;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Collections;
import java.util.List;

public final class AttendanceTimeCalculator {

    private AttendanceTimeCalculator() {}

    public record BreakInterval(LocalDateTime startAt, LocalDateTime endAt) {
        public BreakInterval {
            if (startAt == null || endAt == null) {
                throw new IllegalArgumentException("休憩の開始・終了日時は必須です。");
            }
            if (!endAt.isAfter(startAt)) {
                throw new IllegalArgumentException("休憩終了日時は開始日時より後である必要があります。");
            }
        }
    }

    public record CalculationResult(
            int totalStayMinutes,
            int totalBreakMinutes,
            int netWorkMinutes,
            int nightWorkMinutes,
            int overtimeMinutes
    ) {}

    /**
     * 出退勤時刻および休憩区間一覧に基づき、実労働、深夜労働、法定時間外残業を分単位で厳密に算出
     */
    public static CalculationResult calculate(
            LocalDateTime clockInAt,
            LocalDateTime clockOutAt,
            List<BreakInterval> rawBreaks
    ) {
        if (clockInAt == null || clockOutAt == null) {
            throw new IllegalArgumentException("出勤および退勤日時は必須です。");
        }
        if (!clockOutAt.isAfter(clockInAt)) {
            throw new IllegalArgumentException("退勤日時は出勤日時より後である必要があります。");
        }

        List<BreakInterval> breaks = (rawBreaks != null) ? rawBreaks : Collections.emptyList();

        // 休憩区間の整合性検証（出退勤範囲内かつ重複禁止）
        for (int i = 0; i < breaks.size(); i++) {
            BreakInterval b = breaks.get(i);
            if (b.startAt().isBefore(clockInAt) || b.endAt().isAfter(clockOutAt)) {
                throw new IllegalArgumentException("休憩時間は勤務時間内である必要があります。");
            }
            for (int j = i + 1; j < breaks.size(); j++) {
                BreakInterval other = breaks.get(j);
                if (b.startAt().isBefore(other.endAt()) && b.endAt().isAfter(other.startAt())) {
                    throw new IllegalArgumentException("休憩時間帯同士が重複しています。");
                }
            }
        }

        int totalStayMinutes = Math.toIntExact(Duration.between(clockInAt, clockOutAt).toMinutes());
        int totalBreakMinutes = 0;
        for (BreakInterval b : breaks) {
            totalBreakMinutes = Math.addExact(totalBreakMinutes,
                    Math.toIntExact(Duration.between(b.startAt(), b.endAt()).toMinutes()));
        }

        if (totalBreakMinutes >= totalStayMinutes) {
            throw new IllegalArgumentException("休憩時間が総滞在時間以上になることはできません。");
        }

        int netWorkMinutes = totalStayMinutes - totalBreakMinutes;
        int overtimeMinutes = Math.max(0, netWorkMinutes - 480); // 1日8時間（480分）超過分

        // 深夜時間帯（22:00 ~ 翌05:00）実労働時間計算（深夜帯と重なった休憩時間のみ控除）
        int grossNightStayMinutes = calculateNightMinutesInRange(clockInAt, clockOutAt);
        int nightBreakMinutes = 0;
        for (BreakInterval b : breaks) {
            nightBreakMinutes = Math.addExact(nightBreakMinutes,
                    calculateNightMinutesInRange(b.startAt(), b.endAt()));
        }
        int netNightWorkMinutes = Math.max(0, grossNightStayMinutes - nightBreakMinutes);

        return new CalculationResult(
                totalStayMinutes,
                totalBreakMinutes,
                netWorkMinutes,
                netNightWorkMinutes,
                overtimeMinutes
        );
    }

    /**
     * 指定区間と法定深夜時間帯（22:00 ~ 翌05:00）の重複分数を算出
     */
    private static int calculateNightMinutesInRange(LocalDateTime start, LocalDateTime end) {
        LocalDate startDate = start.toLocalDate();
        LocalDate endDate = end.toLocalDate();
        int total = 0;

        for (LocalDate d = startDate.minusDays(1); !d.isAfter(endDate.plusDays(1)); d = d.plusDays(1)) {
            LocalDateTime nightStart = LocalDateTime.of(d, LocalTime.of(22, 0));
            LocalDateTime nightEnd = LocalDateTime.of(d.plusDays(1), LocalTime.of(5, 0));

            LocalDateTime overlapStart = start.isAfter(nightStart) ? start : nightStart;
            LocalDateTime overlapEnd = end.isBefore(nightEnd) ? end : nightEnd;

            if (overlapEnd.isAfter(overlapStart)) {
                total = Math.addExact(total,
                        Math.toIntExact(Duration.between(overlapStart, overlapEnd).toMinutes()));
            }
        }
        return total;
    }
}
