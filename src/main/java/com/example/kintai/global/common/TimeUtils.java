package com.example.kintai.global.common;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

/**
 * 共通時間・分（Minute）変換ユーティリティ
 */
public final class TimeUtils {

    private TimeUtils() {}

    /**
     * 分（Minute）を標準時間フォーマット（"04:45"）に変換
     */
    public static String formatMinutesToHHmm(int totalMinutes) {
        int safeMinutes = Math.max(0, totalMinutes);
        int hours = safeMinutes / 60;
        int mins = safeMinutes % 60;
        return String.format("%02d:%02d", hours, mins);
    }

    /**
     * 分（Minute）を日本語時間フォーマット（"4時間45分"）に変換
     */
    public static String formatMinutesToJapanese(int totalMinutes) {
        int safeMinutes = Math.max(0, totalMinutes);
        int hours = safeMinutes / 60;
        int mins = safeMinutes % 60;
        return String.format("%d時間%02d分", hours, mins);
    }

    /**
     * 勤務基準日と出退勤時刻文字列から、日跨ぎ（夜勤翌日退勤）を自動判定して退勤 LocalDateTime を生成
     */
    public static LocalDateTime resolveEndDateTime(LocalDate workDate, String startTimeStr, String endTimeStr) {
        LocalTime startTime = LocalTime.parse(startTimeStr);
        LocalTime endTime = LocalTime.parse(endTimeStr);
        LocalDate endDate = endTime.isAfter(startTime) ? workDate : workDate.plusDays(1);
        return LocalDateTime.of(endDate, endTime);
    }
}
