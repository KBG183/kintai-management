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
     * 月間カレンダー用シフトイベント一覧取得
     */
    public List<CalendarEventDto> getCalendarEvents(Long storeId, LocalDate startDate, LocalDate endDate) {
        List<ShiftDto> shifts = shiftMapper.findShiftEvents(storeId, startDate, endDate);
        return shifts.stream()
                .map(CalendarEventDto::from)
                .toList();
    }

    /**
     * スタッフによる希望シフト提出
     */
    @Transactional
    public Long requestShift(ShiftRequestDto requestDto) {
        // 排他制御: 悲観的ロックによる直列化
        shiftMapper.lockStoreStaffForUpdate(requestDto.getStoreStaffId());

        LocalDate workDate = requestDto.getWorkDate();
        LocalTime startLocalTime = LocalTime.parse(requestDto.getRequestedStartTime());
        LocalTime endLocalTime = LocalTime.parse(requestDto.getRequestedEndTime());

        LocalDateTime startAt = LocalDateTime.of(workDate, startLocalTime);
        LocalDateTime endAt = TimeUtils.resolveEndDateTime(workDate, requestDto.getRequestedStartTime(), requestDto.getRequestedEndTime());

        // 16時間超過検証の精度担保（Duration.compareToによる正確な比較）
        if (Duration.between(startAt, endAt).compareTo(Duration.ofHours(16)) > 0) {
            throw new IllegalArgumentException("1回のシフト時間は最大16時間以内である必要があります。");
        }

        // 重複シフト検証
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
     * 店長によるシフト確定および法定休憩時間の検証
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

        // 16時間超過検証
        if (Duration.between(confirmedStartAt, confirmedEndAt).compareTo(Duration.ofHours(16)) > 0) {
            throw new IllegalArgumentException("1回のシフト時間は最大16時間以内である必要があります。");
        }

        // 重複シフト検証（自身を除く）
        if (shiftMapper.existsOverlapShift(existing.getStoreStaffId(), workDate, confirmedStartAt, confirmedEndAt, shiftId)) {
            throw new IllegalStateException("指定された確定時間帯に他のシフトと重複が発生します。");
        }

        // 日本労働基準法第34条に基づく休憩時間計算 (6時間超45分、8時間超60分)
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
     * 日本労働基準法第34条 休憩時間推奨算定
     */
    public int calculateRecommendedBreakMinutes(LocalDateTime startAt, LocalDateTime endAt) {
        long grossMinutes = Duration.between(startAt, endAt).toMinutes();
        if (grossMinutes <= 360) {
            return 0; // 6時間以下
        } else if (grossMinutes <= 525) {
            return 45; // 6時間超 8時間以下 (滞在 525分 - 45分 = 実働 480分=8時間)
        } else {
            return 60; // 8時間超
        }
    }
}
