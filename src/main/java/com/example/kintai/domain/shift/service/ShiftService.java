package com.example.kintai.domain.shift.service;

import com.example.kintai.domain.shift.dto.CalendarEventDto;
import com.example.kintai.domain.shift.dto.ShiftConfirmDto;
import com.example.kintai.domain.shift.dto.ShiftDto;
import com.example.kintai.domain.shift.dto.ShiftRequestDto;
import com.example.kintai.domain.shift.mapper.ShiftMapper;
import com.example.kintai.global.common.TimeUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

@Service
@Transactional(readOnly = true)
public class ShiftService {

    private final ShiftMapper shiftMapper;

    public ShiftService(ShiftMapper shiftMapper) {
        this.shiftMapper = shiftMapper;
    }

    /**
     * 월간 캘린더 이벤트 목록 조회
     */
    public List<CalendarEventDto> getCalendarEvents(Long storeId, LocalDate startDate, LocalDate endDate) {
        List<ShiftDto> shifts = shiftMapper.findShiftEvents(storeId, startDate, endDate);
        return shifts.stream()
                .map(CalendarEventDto::from)
                .toList();
    }

    /**
     * 알바생 희망 시프트 제출
     */
    @Transactional
    public Long requestShift(ShiftRequestDto requestDto) {
        // [F04 방어]: 동시성 충돌 방지를 위한 비관적 락 직렬화
        shiftMapper.lockStoreStaffForUpdate(requestDto.getStoreStaffId());

        LocalDate workDate = requestDto.getWorkDate();
        LocalTime startLocalTime = LocalTime.parse(requestDto.getRequestedStartTime());
        LocalTime endLocalTime = LocalTime.parse(requestDto.getRequestedEndTime());

        LocalDateTime startAt = LocalDateTime.of(workDate, startLocalTime);
        LocalDateTime endAt = TimeUtils.resolveEndDateTime(workDate, requestDto.getRequestedStartTime(), requestDto.getRequestedEndTime());

        // [F07 방어]: 16시간 초과 검증 정확도 보장
        // Duration.toHours() > 16은 소수점을 절사하여 16시간 59분(1019분)을 16으로 계산해 통과시키는 버그가 발생하므로 compareTo 사용
        if (Duration.between(startAt, endAt).compareTo(Duration.ofHours(16)) > 0) {
            throw new IllegalArgumentException("1回のシフト時間は最大16時間以内である必要があります。");
        }

        // 중복 시프트 검사
        if (shiftMapper.existsOverlapShift(requestDto.getStoreStaffId(), workDate, startAt, endAt, null)) {
            throw new IllegalStateException("指定された時間帯に既に別のシフトが存在します。");
        }

        ShiftDto shift = new ShiftDto();
        shift.setStoreId(requestDto.getStoreId());
        shift.setStoreStaffId(requestDto.getStoreStaffId());
        shift.setWorkDate(workDate);
        shift.setRequestedStartAt(startAt);
        shift.setRequestedEndAt(endAt);
        shift.setPlannedBreakMinutes(0);
        shift.setStatus("SUBMITTED");
        shift.setStaffMemo(requestDto.getStaffMemo());

        shiftMapper.insertShift(shift);
        return shift.getShiftId();
    }

    /**
     * 점주 시프트 확정 및 휴게시간 법정 기준 검증
     */
    @Transactional
    public void confirmShift(Long shiftId, ShiftConfirmDto confirmDto) {
        ShiftDto existing = shiftMapper.findById(shiftId);
        if (existing == null) {
            throw new IllegalArgumentException("存在しないシフトです。(ID: " + shiftId + ")");
        }

        LocalDate workDate = existing.getWorkDate();
        LocalDateTime confirmedStartAt = LocalDateTime.of(workDate, LocalTime.parse(confirmDto.getConfirmedStartTime()));
        LocalDateTime confirmedEndAt = TimeUtils.resolveEndDateTime(workDate, confirmDto.getConfirmedStartTime(), confirmDto.getConfirmedEndTime());

        // [F07 방어]: 16시간 초과 정밀 검사
        if (Duration.between(confirmedStartAt, confirmedEndAt).compareTo(Duration.ofHours(16)) > 0) {
            throw new IllegalArgumentException("1回のシフト時間は最大16時間以内である必要があります。");
        }

        // 중복 시프트 검사 (본인 제외)
        if (shiftMapper.existsOverlapShift(existing.getStoreStaffId(), workDate, confirmedStartAt, confirmedEndAt, shiftId)) {
            throw new IllegalStateException("指定された確定時間帯に他のシフトと重複が発生します。");
        }

        // 일본 노동기준법 제34조 기반 휴게시간 계산 (6시간 초과 45분, 8시간 초과 60분)
        int plannedBreak = (confirmDto.getPlannedBreakMinutes() != null)
                ? confirmDto.getPlannedBreakMinutes()
                : calculateRecommendedBreakMinutes(confirmedStartAt, confirmedEndAt);

        existing.setConfirmedStartAt(confirmedStartAt);
        existing.setConfirmedEndAt(confirmedEndAt);
        existing.setPlannedBreakMinutes(plannedBreak);
        existing.setStatus(confirmDto.getStatus() != null ? confirmDto.getStatus() : "CONFIRMED");
        existing.setOwnerMemo(confirmDto.getOwnerMemo());

        shiftMapper.updateShiftConfirm(existing);
    }

    /**
     * 일본 노동기준법 제34조 휴게시간 추천 산정
     */
    public int calculateRecommendedBreakMinutes(LocalDateTime startAt, LocalDateTime endAt) {
        long grossMinutes = Duration.between(startAt, endAt).toMinutes();
        if (grossMinutes <= 360) {
            return 0; // 6시간 이하
        } else if (grossMinutes <= 525) {
            return 45; // 6시간 초과 8시간 이하 (체류 525분 - 45분 = 실근무 480분=8시간)
        } else {
            return 60; // 8시간 초과
        }
    }
}
