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
     * 月間カレンダー用シフトイベント一覧取得 (店舗全体・プライバシーマスク連動)
     */
    public List<CalendarEventDto> getCalendarEvents(Long storeId, LocalDate startDate, LocalDate endDate, Long currentStaffId, boolean isManager) {
        List<ShiftDto> shifts = shiftMapper.findShiftEvents(storeId, startDate, endDate);
        return shifts.stream()
                .map(s -> CalendarEventDto.from(s, currentStaffId, isManager))
                .toList();
    }

    public List<CalendarEventDto> getCalendarEvents(Long storeId, LocalDate startDate, LocalDate endDate) {
        return getCalendarEvents(storeId, startDate, endDate, null, true);
    }

    /**
     * スタッフ本人用シフト一覧取得 (IDOR防止: セッション認証済みのstoreStaffIdに基づく取得)
     */
    public List<CalendarEventDto> getStaffShiftEvents(Long storeStaffId, LocalDate startDate, LocalDate endDate) {
        if (storeStaffId == null) {
            throw new IllegalArgumentException("店舗スタッフIDが指定されていません。");
        }
        List<ShiftDto> shifts = shiftMapper.findStaffShiftList(storeStaffId, startDate, endDate);
        return shifts.stream()
                .map(CalendarEventDto::from)
                .toList();
    }

    /**
     * スタッフによる希望シフト提出 (過去日付拒否・時間逆転防止・排他ロック)
     */
    @Transactional
    public Long requestShift(ShiftRequestDto requestDto) {
        if (requestDto == null) {
            throw new IllegalArgumentException("リクエストデータが空です。");
        }
        if (requestDto.getStoreStaffId() == null) {
            throw new IllegalArgumentException("店舗スタッフIDは必須です。");
        }
        if (requestDto.getWorkDate() == null) {
            throw new IllegalArgumentException("勤務日は必須です。");
        }
        if (requestDto.getWorkDate().isBefore(LocalDate.now())) {
            throw new IllegalArgumentException("過去の日付に対する希望シフトは申請できません。");
        }
        if (requestDto.getRequestedStartTime() == null || requestDto.getRequestedEndTime() == null) {
            throw new IllegalArgumentException("開始時刻および終了時刻は必須です。");
        }

        // 排他制御: 悲観的ロックによる直列化 (同一スタッフの多重登録レースコンディション防止)
        shiftMapper.lockStoreStaffForUpdate(requestDto.getStoreStaffId());

        LocalDate workDate = requestDto.getWorkDate();
        LocalTime startLocalTime;
        LocalTime endLocalTime;
        try {
            startLocalTime = LocalTime.parse(requestDto.getRequestedStartTime());
            endLocalTime = LocalTime.parse(requestDto.getRequestedEndTime());
        } catch (Exception e) {
            throw new IllegalArgumentException("時刻のフォーマットが不正です。(HH:mm形式で入力してください)", e);
        }

        LocalDateTime startAt = LocalDateTime.of(workDate, startLocalTime);
        LocalDateTime endAt = TimeUtils.resolveEndDateTime(workDate, requestDto.getRequestedStartTime(), requestDto.getRequestedEndTime());

        if (!endAt.isAfter(startAt)) {
            throw new IllegalArgumentException("終了日時は開始日時より後である必要があります。");
        }

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
     * スタッフ本人による希望シフト申請の取消 (IDOR防御: 本人かつSUBMITTED状態のみ削除可能)
     */
    @Transactional
    public void deleteShiftRequest(Long shiftId, Long storeStaffId) {
        if (shiftId == null || storeStaffId == null) {
            throw new IllegalArgumentException("シフトIDおよび店舗スタッフIDは必須です。");
        }

        // 排他ロック
        shiftMapper.lockStoreStaffForUpdate(storeStaffId);

        ShiftDto existing = shiftMapper.findById(shiftId);
        if (existing == null) {
            throw new IllegalArgumentException("指定されたシフトが存在しません。(ID: " + shiftId + ")");
        }

        // IDOR検証: 自身のシフトであるかの確認
        if (!existing.getStoreStaffId().equals(storeStaffId)) {
            throw new SecurityException("他のスタッフのシフトを削除する権限がありません。(IDOR遮断)");
        }

        // 状態検証: すでに確定されたシフトはスタッフ側からは削除不可（店長へ要連絡）
        if (!"SUBMITTED".equals(existing.getStatus())) {
            throw new IllegalStateException("確定済または却下済のシフトは申請取消できません。店長へ直接ご相談ください。");
        }

        int deletedCount = shiftMapper.deleteShiftRequest(shiftId, storeStaffId);
        if (deletedCount == 0) {
            throw new IllegalStateException("シフトの取消に失敗しました。");
        }
    }

    /**
     * 店長によるシフト確定および法定休憩時間の検証 (自店舗データ検証によるクロス店舗IDOR防止)
     */
    @Transactional
    public void confirmShift(Long shiftId, Long storeId, ShiftConfirmDto confirmDto) {
        if (shiftId == null || storeId == null || confirmDto == null) {
            throw new IllegalArgumentException("シフト確定パラメータが不足しています。");
        }

        ShiftDto existing = shiftMapper.findById(shiftId);
        if (existing == null) {
            throw new IllegalArgumentException("存在しないシフトです。(ID: " + shiftId + ")");
        }

        // 店舗間IDOR検証: 自身の管理店舗のシフトであるかの確認
        if (!existing.getStoreId().equals(storeId)) {
            throw new SecurityException("他店舗のシフトを確定・変更する権限がありません。(店舗間IDOR遮断)");
        }

        // 排他ロック (スタッフIDに対して悲観的ロック)
        shiftMapper.lockStoreStaffForUpdate(existing.getStoreStaffId());

        if (confirmDto.getConfirmedStartTime() == null || confirmDto.getConfirmedEndTime() == null) {
            throw new IllegalArgumentException("確定開始時刻および終了時刻は必須です。");
        }

        LocalDate workDate = existing.getWorkDate();
        LocalTime startLocalTime;
        LocalTime endLocalTime;
        try {
            startLocalTime = LocalTime.parse(confirmDto.getConfirmedStartTime());
            endLocalTime = LocalTime.parse(confirmDto.getConfirmedEndTime());
        } catch (Exception e) {
            throw new IllegalArgumentException("確定時刻のフォーマットが不正です。(HH:mm形式)", e);
        }

        LocalDateTime confirmedStartAt = LocalDateTime.of(workDate, startLocalTime);
        LocalDateTime confirmedEndAt = TimeUtils.resolveEndDateTime(workDate, confirmDto.getConfirmedStartTime(), confirmDto.getConfirmedEndTime());

        if (!confirmedEndAt.isAfter(confirmedStartAt)) {
            throw new IllegalArgumentException("終了日時は開始日時より後である必要があります。");
        }

        long grossMinutes = Duration.between(confirmedStartAt, confirmedEndAt).toMinutes();

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

        if (plannedBreak < 0 || plannedBreak >= grossMinutes) {
            throw new IllegalArgumentException("計画休憩時間は0分以上かつ総拘束時間未満である必要があります。");
        }

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
