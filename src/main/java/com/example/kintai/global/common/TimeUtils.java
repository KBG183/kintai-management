package com.example.kintai.global.common;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

/**
 * 전사 공통 시간·분(Minute) 변환 유틸리티
 */
public final class TimeUtils {

    private TimeUtils() {}

    /**
     * 분(Minute)을 일본어/표준 시간 포맷("04:45" 또는 "4時間45分")으로 변환
     */
    public static String formatMinutesToHHmm(int totalMinutes) {
        int safeMinutes = Math.max(0, totalMinutes);
        int hours = safeMinutes / 60;
        int mins = safeMinutes % 60;
        return String.format("%02d:%02d", hours, mins);
    }

    public static String formatMinutesToJapanese(int totalMinutes) {
        int safeMinutes = Math.max(0, totalMinutes);
        int hours = safeMinutes / 60;
        int mins = safeMinutes % 60;
        return String.format("%d時間%02d分", hours, mins);
    }

    /**
     * 기준일(workDate)과 출퇴근 시각 문자열("18:00", "02:00")을 받아
     * 자정 넘김(철야 익일 퇴근)을 자동 판별하여 퇴근 LocalDateTime을 생성한다.
     */
    public static LocalDateTime resolveEndDateTime(LocalDate workDate, String startTimeStr, String endTimeStr) {
        LocalTime startTime = LocalTime.parse(startTimeStr);
        LocalTime endTime = LocalTime.parse(endTimeStr);
        LocalDate endDate = endTime.isAfter(startTime) ? workDate : workDate.plusDays(1);
        return LocalDateTime.of(endDate, endTime);
    }
}
