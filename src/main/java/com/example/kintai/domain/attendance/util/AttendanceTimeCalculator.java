package com.example.kintai.domain.attendance.util;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * 日本労働基準法第37条（時間外・深夜・休日割増）および第34条（休憩）に基づく勤怠労働時間精密算定ユーティリティ。
 * 秒単位の高精度タイムライン・スライシングにより、実労働時間、所定内深夜、法定外残業、および深夜残業（重畳割増: 150%）を
 * 1秒の誤差もなく厳密に分離算定し、DTOレベルで秒精度を完全保持する。
 */
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

    /**
     * 従来の基本算定結果（秒精度保持版）
     */
    public record CalculationResult(
            int totalStayMinutes,
            int totalBreakMinutes,
            int netWorkMinutes,
            int nightWorkMinutes,
            int overtimeMinutes,
            long netWorkSeconds
    ) {
        public CalculationResult(int totalStayMinutes, int totalBreakMinutes, int netWorkMinutes, int nightWorkMinutes, int overtimeMinutes) {
            this(totalStayMinutes, totalBreakMinutes, netWorkMinutes, nightWorkMinutes, overtimeMinutes, (long) netWorkMinutes * 60L);
        }
    }

    /**
     * 労働基準法第37条に基づく4区分精密算定結果（秒精度完全保持）
     *
     * @param totalStayMinutes 総拘束時間（分）
     * @param totalBreakMinutes 総休憩時間（分）
     * @param netWorkMinutes 総実労働時間（分: 総拘束 - 総休憩）
     * @param regularWorkMinutes 所定内通常労働時間（分: 100%支給、8時間以内かつ非深夜）
     * @param regularNightWorkMinutes 所定内深夜労働時間（分: 125%支給、8時間以内かつ深夜22:00~05:00）
     * @param overtimeWorkMinutes 法定外通常残業時間（分: 125%支給、8時間超過かつ非深夜）
     * @param nightOvertimeWorkMinutes 法定外深夜残業時間（分: 150%重畳割増支給、8時間超過かつ深夜22:00~05:00）
     * @param nightWorkMinutes 総深夜労働時間（分: regularNightWorkMinutes + nightOvertimeWorkMinutes）
     * @param overtimeMinutes 総法定外残業時間（分: overtimeWorkMinutes + nightOvertimeWorkMinutes）
     * @param netWorkSeconds 総実労働時間（秒）
     * @param regularWorkSeconds 所定内通常労働時間（秒）
     * @param regularNightWorkSeconds 所定内深夜労働時間（秒）
     * @param overtimeWorkSeconds 法定外通常残業時間（秒）
     * @param nightOvertimeWorkSeconds 法定外深夜残業時間（秒）
     * @param totalStaySeconds 総拘束時間（秒）
     * @param totalBreakSeconds 総休憩時間（秒）
     */
    public record DetailedCalculationResult(
            int totalStayMinutes,
            int totalBreakMinutes,
            int netWorkMinutes,
            int regularWorkMinutes,
            int regularNightWorkMinutes,
            int overtimeWorkMinutes,
            int nightOvertimeWorkMinutes,
            int nightWorkMinutes,
            int overtimeMinutes,
            long netWorkSeconds,
            long regularWorkSeconds,
            long regularNightWorkSeconds,
            long overtimeWorkSeconds,
            long nightOvertimeWorkSeconds,
            long totalStaySeconds,
            long totalBreakSeconds
    ) {}

    public static CalculationResult calculate(
            LocalDateTime clockInAt,
            LocalDateTime clockOutAt,
            List<BreakInterval> rawBreaks
    ) {
        return calculate(clockInAt, clockOutAt, rawBreaks, 0L);
    }

    public static CalculationResult calculate(
            LocalDateTime clockInAt,
            LocalDateTime clockOutAt,
            List<BreakInterval> rawBreaks,
            int priorNetWorkMinutes
    ) {
        return calculate(clockInAt, clockOutAt, rawBreaks, (long) priorNetWorkMinutes * 60L);
    }

    public static CalculationResult calculate(
            LocalDateTime clockInAt,
            LocalDateTime clockOutAt,
            List<BreakInterval> rawBreaks,
            long priorNetWorkSeconds
    ) {
        DetailedCalculationResult detailed = calculateDetailed(clockInAt, clockOutAt, rawBreaks, priorNetWorkSeconds);
        return new CalculationResult(
                detailed.totalStayMinutes(),
                detailed.totalBreakMinutes(),
                detailed.netWorkMinutes(),
                detailed.nightWorkMinutes(),
                detailed.overtimeMinutes(),
                detailed.netWorkSeconds()
        );
    }

    public static DetailedCalculationResult calculateDetailed(
            LocalDateTime clockInAt,
            LocalDateTime clockOutAt,
            List<BreakInterval> rawBreaks
    ) {
        return calculateDetailed(clockInAt, clockOutAt, rawBreaks, 0L);
    }

    public static DetailedCalculationResult calculateDetailed(
            LocalDateTime clockInAt,
            LocalDateTime clockOutAt,
            List<BreakInterval> rawBreaks,
            int priorNetWorkMinutes
    ) {
        return calculateDetailed(clockInAt, clockOutAt, rawBreaks, (long) priorNetWorkMinutes * 60L);
    }

    /**
     * 労働基準法第37条に基づく秒精度タイムライン・インターバル・スライシング精密算定エンジン（先行累計秒数連動版）
     */
    public static DetailedCalculationResult calculateDetailed(
            LocalDateTime clockInAt,
            LocalDateTime clockOutAt,
            List<BreakInterval> rawBreaks,
            long priorNetWorkSeconds
    ) {
        if (clockInAt == null || clockOutAt == null) {
            throw new IllegalArgumentException("出勤および退勤日時は必須です。");
        }
        if (!clockOutAt.isAfter(clockInAt)) {
            throw new IllegalArgumentException("退勤日時は出勤日時より後である必要があります。");
        }

        List<BreakInterval> breaks = (rawBreaks != null) ? new ArrayList<>(rawBreaks) : new ArrayList<>();

        // 休憩区間のソートおよび整合性検証（出退勤範囲内かつ重複禁止）
        breaks.sort(Comparator.comparing(BreakInterval::startAt));
        for (int i = 0; i < breaks.size(); i++) {
            BreakInterval b = breaks.get(i);
            if (b.startAt().isBefore(clockInAt) || b.endAt().isAfter(clockOutAt)) {
                throw new IllegalArgumentException("休憩時間は勤務時間内である必要があります。");
            }
            if (i > 0) {
                BreakInterval prev = breaks.get(i - 1);
                if (b.startAt().isBefore(prev.endAt())) {
                    throw new IllegalArgumentException("休憩時間帯同士が重複しています。");
                }
            }
        }

        long totalStaySeconds = Duration.between(clockInAt, clockOutAt).getSeconds();
        long totalBreakSeconds = 0;
        for (BreakInterval b : breaks) {
            totalBreakSeconds += Duration.between(b.startAt(), b.endAt()).getSeconds();
        }

        if (totalBreakSeconds >= totalStaySeconds) {
            throw new IllegalArgumentException("休憩時間が総滞在時間以上になることはできません。");
        }

        long netWorkSeconds = totalStaySeconds - totalBreakSeconds;

        // タイムラインから休憩区間を除外した「実働区間」の一覧を生成
        List<WorkInterval> workIntervals = new ArrayList<>();
        LocalDateTime cursor = clockInAt;

        for (BreakInterval b : breaks) {
            if (b.startAt().isAfter(cursor)) {
                workIntervals.add(new WorkInterval(cursor, b.startAt()));
            }
            cursor = b.endAt();
        }
        if (clockOutAt.isAfter(cursor)) {
            workIntervals.add(new WorkInterval(cursor, clockOutAt));
        }

        // 4区分集計用秒単位アキュムレータ
        long regularWorkSeconds = 0;
        long regularNightWorkSeconds = 0;
        long overtimeWorkSeconds = 0;
        long nightOvertimeWorkSeconds = 0;

        long cumulativeNetSeconds = Math.max(0L, priorNetWorkSeconds);
        final long STATUTORY_LIMIT_SECONDS = 8L * 3600L; // 1日法定労働時間 8時間 = 28,800秒

        for (WorkInterval wi : workIntervals) {
            LocalDateTime segCursor = wi.startAt();
            LocalDateTime segEnd = wi.endAt();

            while (segCursor.isBefore(segEnd)) {
                LocalDateTime nextBound = findNextBoundary(segCursor, segEnd);
                long sliceSeconds = Duration.between(segCursor, nextBound).getSeconds();
                boolean isNight = isNightTime(segCursor);

                if (cumulativeNetSeconds + sliceSeconds <= STATUTORY_LIMIT_SECONDS) {
                    // 全体が所定内（8時間以下）
                    if (isNight) {
                        regularNightWorkSeconds += sliceSeconds;
                    } else {
                        regularWorkSeconds += sliceSeconds;
                    }
                    cumulativeNetSeconds += sliceSeconds;
                } else if (cumulativeNetSeconds >= STATUTORY_LIMIT_SECONDS) {
                    // 全体が法定外（8時間超過）
                    if (isNight) {
                        nightOvertimeWorkSeconds += sliceSeconds;
                    } else {
                        overtimeWorkSeconds += sliceSeconds;
                    }
                    cumulativeNetSeconds += sliceSeconds;
                } else {
                    // スライス内で8時間の境界を跨ぐ場合
                    long regularPart = STATUTORY_LIMIT_SECONDS - cumulativeNetSeconds;
                    long overtimePart = sliceSeconds - regularPart;

                    if (isNight) {
                        regularNightWorkSeconds += regularPart;
                        nightOvertimeWorkSeconds += overtimePart;
                    } else {
                        regularWorkSeconds += regularPart;
                        overtimeWorkSeconds += overtimePart;
                    }
                    cumulativeNetSeconds += sliceSeconds;
                }
                segCursor = nextBound;
            }
        }

        int totalStayMinutes = (int) (totalStaySeconds / 60);
        int totalBreakMinutes = (int) (totalBreakSeconds / 60);
        int netWorkMinutes = (int) (netWorkSeconds / 60);
        int regMin = (int) (regularWorkSeconds / 60);
        int regNightMin = (int) (regularNightWorkSeconds / 60);
        int otMin = (int) (overtimeWorkSeconds / 60);
        int nightOtMin = (int) (nightOvertimeWorkSeconds / 60);

        int totalNightMinutes = regNightMin + nightOtMin;
        int totalOvertimeMinutes = otMin + nightOtMin;

        return new DetailedCalculationResult(
                totalStayMinutes,
                totalBreakMinutes,
                netWorkMinutes,
                regMin,
                regNightMin,
                otMin,
                nightOtMin,
                totalNightMinutes,
                totalOvertimeMinutes,
                netWorkSeconds,
                regularWorkSeconds,
                regularNightWorkSeconds,
                overtimeWorkSeconds,
                nightOvertimeWorkSeconds,
                totalStaySeconds,
                totalBreakSeconds
        );
    }

    private static LocalDateTime findNextBoundary(LocalDateTime current, LocalDateTime limit) {
        LocalDate date = current.toLocalDate();
        List<LocalDateTime> boundaries = new ArrayList<>();

        boundaries.add(date.atTime(LocalTime.of(5, 0)));
        boundaries.add(date.atTime(LocalTime.of(22, 0)));
        boundaries.add(date.plusDays(1).atTime(LocalTime.of(5, 0)));
        boundaries.add(date.plusDays(1).atTime(LocalTime.of(22, 0)));
        boundaries.add(date.plusDays(2).atTime(LocalTime.of(5, 0)));

        Collections.sort(boundaries);

        for (LocalDateTime b : boundaries) {
            if (b.isAfter(current)) {
                return b.isBefore(limit) ? b : limit;
            }
        }
        return limit;
    }

    public static boolean isNightTime(LocalDateTime dt) {
        LocalTime t = dt.toLocalTime();
        return !t.isBefore(LocalTime.of(22, 0)) || t.isBefore(LocalTime.of(5, 0));
    }

    private record WorkInterval(LocalDateTime startAt, LocalDateTime endAt) {}
}
