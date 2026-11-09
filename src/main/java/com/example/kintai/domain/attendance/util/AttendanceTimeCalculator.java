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
 * 実労働時間、所定内深夜、法定外残業、および深夜残業（重畳割増: 150%）を分単位のタイムライン・スライシングにより厳密に分離算定する。
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
     * 従来の基本算定結果（互換性維持用）
     */
    public record CalculationResult(
            int totalStayMinutes,
            int totalBreakMinutes,
            int netWorkMinutes,
            int nightWorkMinutes,
            int overtimeMinutes
    ) {}

    /**
     * 労働基準法第37条に基づく4区分精密算定結果
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
            int overtimeMinutes
    ) {
        public DetailedCalculationResult {
            // 不変式の整合性検証
            if (netWorkMinutes != regularWorkMinutes + regularNightWorkMinutes + overtimeWorkMinutes + nightOvertimeWorkMinutes) {
                throw new IllegalStateException("4区分実労働時間の合計が総実労働時間と一致しません。");
            }
            if (nightWorkMinutes != regularNightWorkMinutes + nightOvertimeWorkMinutes) {
                throw new IllegalStateException("総深夜時間が所定内深夜と深夜残業の合計と一致しません。");
            }
            if (overtimeMinutes != overtimeWorkMinutes + nightOvertimeWorkMinutes) {
                throw new IllegalStateException("総残業時間が通常残業と深夜残業の合計と一致しません。");
            }
        }
    }

    /**
     * 出退勤時刻および休憩区間一覧に基づき、実労働、深夜労働、法定時間外残業を分単位で厳密に算出（基本互換版）
     */
    public static CalculationResult calculate(
            LocalDateTime clockInAt,
            LocalDateTime clockOutAt,
            List<BreakInterval> rawBreaks
    ) {
        DetailedCalculationResult detailed = calculateDetailed(clockInAt, clockOutAt, rawBreaks);
        return new CalculationResult(
                detailed.totalStayMinutes(),
                detailed.totalBreakMinutes(),
                detailed.netWorkMinutes(),
                detailed.nightWorkMinutes(),
                detailed.overtimeMinutes()
        );
    }

    /**
     * 労働基準法第37条に基づくタイムライン・インターバル・スライシング精密算定エンジン
     */
    public static DetailedCalculationResult calculateDetailed(
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

        // 4区分集計用アキュムレータ
        int regularWorkMinutes = 0;
        int regularNightWorkMinutes = 0;
        int overtimeWorkMinutes = 0;
        int nightOvertimeWorkMinutes = 0;

        int cumulativeNetMinutes = 0;
        final int STATUTORY_LIMIT_MINUTES = 480; // 1日法定労働時間 8時間 = 480分

        for (WorkInterval wi : workIntervals) {
            LocalDateTime segCursor = wi.startAt();
            LocalDateTime segEnd = wi.endAt();

            while (segCursor.isBefore(segEnd)) {
                LocalDateTime nextBound = findNextBoundary(segCursor, segEnd);
                int sliceMinutes = Math.toIntExact(Duration.between(segCursor, nextBound).toMinutes());
                boolean isNight = isNightTime(segCursor);

                if (cumulativeNetMinutes + sliceMinutes <= STATUTORY_LIMIT_MINUTES) {
                    // 全体が所定内（8時間以下）
                    if (isNight) {
                        regularNightWorkMinutes += sliceMinutes;
                    } else {
                        regularWorkMinutes += sliceMinutes;
                    }
                    cumulativeNetMinutes += sliceMinutes;
                } else if (cumulativeNetMinutes >= STATUTORY_LIMIT_MINUTES) {
                    // 全体が法定外（8時間超過）
                    if (isNight) {
                        nightOvertimeWorkMinutes += sliceMinutes;
                    } else {
                        overtimeWorkMinutes += sliceMinutes;
                    }
                    cumulativeNetMinutes += sliceMinutes;
                } else {
                    // スライス内で8時間の境界を跨ぐ場合
                    int regularPart = STATUTORY_LIMIT_MINUTES - cumulativeNetMinutes;
                    int overtimePart = sliceMinutes - regularPart;

                    if (isNight) {
                        regularNightWorkMinutes += regularPart;
                        nightOvertimeWorkMinutes += overtimePart;
                    } else {
                        regularWorkMinutes += regularPart;
                        overtimeWorkMinutes += overtimePart;
                    }
                    cumulativeNetMinutes += sliceMinutes;
                }

                segCursor = nextBound;
            }
        }

        int nightWorkMinutes = regularNightWorkMinutes + nightOvertimeWorkMinutes;
        int overtimeMinutes = overtimeWorkMinutes + nightOvertimeWorkMinutes;

        return new DetailedCalculationResult(
                totalStayMinutes,
                totalBreakMinutes,
                netWorkMinutes,
                regularWorkMinutes,
                regularNightWorkMinutes,
                overtimeWorkMinutes,
                nightOvertimeWorkMinutes,
                nightWorkMinutes,
                overtimeMinutes
        );
    }

    private record WorkInterval(LocalDateTime startAt, LocalDateTime endAt) {}

    /**
     * 指定時刻が法定深夜時間帯（22:00 ~ 翌05:00）に該当するか判定
     */
    public static boolean isNightTime(LocalDateTime time) {
        LocalTime lt = time.toLocalTime();
        return !lt.isBefore(LocalTime.of(22, 0)) || lt.isBefore(LocalTime.of(5, 0));
    }

    /**
     * 現在時刻から直近の深夜境界（22:00または05:00）または区間終端のうち、最も近い時刻を探索
     */
    private static LocalDateTime findNextBoundary(LocalDateTime current, LocalDateTime maxEnd) {
        LocalDate date = current.toLocalDate();
        LocalDateTime t05 = LocalDateTime.of(date, LocalTime.of(5, 0));
        LocalDateTime t22 = LocalDateTime.of(date, LocalTime.of(22, 0));
        LocalDateTime nextT05 = LocalDateTime.of(date.plusDays(1), LocalTime.of(5, 0));

        LocalDateTime boundary;
        if (current.isBefore(t05)) {
            boundary = t05;
        } else if (current.isBefore(t22)) {
            boundary = t22;
        } else {
            boundary = nextT05;
        }

        return boundary.isBefore(maxEnd) ? boundary : maxEnd;
    }
}
