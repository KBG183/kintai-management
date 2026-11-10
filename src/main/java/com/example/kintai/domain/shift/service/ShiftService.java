package com.example.kintai.domain.shift.service;

import com.example.kintai.domain.attendance.dto.AttendanceDto;
import com.example.kintai.domain.attendance.mapper.AttendanceMapper;
import com.example.kintai.domain.shift.dto.CalendarEventDto;
import com.example.kintai.domain.shift.dto.ShiftBatchConfirmDto;
import com.example.kintai.domain.shift.dto.ShiftComparisonDto;
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
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

@Service
@Transactional(readOnly = true)
public class ShiftService {

    private final ShiftMapper shiftMapper;
    private final AttendanceMapper attendanceMapper;

    public ShiftService(ShiftMapper shiftMapper, AttendanceMapper attendanceMapper) {
        this.shiftMapper = shiftMapper;
        this.attendanceMapper = attendanceMapper;
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
        } catch (DateTimeParseException e) {
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
        } catch (DateTimeParseException e) {
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

        int affected = shiftMapper.updateShiftConfirm(existing);
        if (affected != 1) {
            throw new IllegalStateException("他の管理者によって既にシフトが確定されたか、状態が変更されています。(CAS競合防止)");
        }
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

    /**
     * 店舗内の未確定（SUBMITTED）シフト一覧取得
     */
    public List<ShiftDto> getPendingShifts(Long storeId, LocalDate startDate, LocalDate endDate) {
        if (storeId == null) {
            throw new IllegalArgumentException("店舗IDは必須です。");
        }
        return shiftMapper.findPendingShifts(storeId, startDate, endDate);
    }

    /**
     * 店舗内の未確定シフト件数取得（通知バッジ用）
     */
    public int getPendingShiftCount(Long storeId) {
        if (storeId == null) {
            return 0;
        }
        return shiftMapper.countPendingShifts(storeId);
    }

    /**
     * 店長によるシフト申請の却下（REJECTED）
     */
    @Transactional
    public void rejectShift(Long shiftId, Long storeId, String ownerMemo) {
        if (shiftId == null || storeId == null) {
            throw new IllegalArgumentException("シフトIDおよび店舗IDは必須です。");
        }

        ShiftDto existing = shiftMapper.findById(shiftId);
        if (existing == null) {
            throw new IllegalArgumentException("指定されたシフトが存在しません。(ID: " + shiftId + ")");
        }

        if (!existing.getStoreId().equals(storeId)) {
            throw new SecurityException("他店舗のシフトを操作する権限がありません。(IDOR防御)");
        }

        shiftMapper.updateShiftStatus(shiftId, storeId, "REJECTED", ownerMemo);
    }

    /**
     * 店長による未確定シフトの複数一括確定処理
     */
    @Transactional
    public int batchConfirmShifts(Long storeId, ShiftBatchConfirmDto batchDto) {
        if (storeId == null || batchDto == null || batchDto.getShiftIds() == null || batchDto.getShiftIds().isEmpty()) {
            throw new IllegalArgumentException("一括確定の対象シフトが指定されていません。");
        }

        int confirmedCount = 0;
        for (Long shiftId : batchDto.getShiftIds()) {
            ShiftDto existing = shiftMapper.findById(shiftId);
            if (existing == null || !existing.getStoreId().equals(storeId)) {
                continue; // 存在しないまたは他店舗のデータはスキップ
            }

            if (!"SUBMITTED".equals(existing.getStatus())) {
                continue; // 既に確定または却下されたシフトは対象外
            }

            // 同時更新防止ロック
            shiftMapper.lockStoreStaffForUpdate(existing.getStoreStaffId());

            LocalDateTime startAt = existing.getRequestedStartAt();
            LocalDateTime endAt = existing.getRequestedEndAt();

            // 重複チェック
            if (shiftMapper.existsOverlapShift(existing.getStoreStaffId(), existing.getWorkDate(), startAt, endAt, shiftId)) {
                continue; // 重複がある場合は安全のためスキップ
            }

            // 法定休憩時間の自動算定
            int plannedBreak = calculateRecommendedBreakMinutes(startAt, endAt);

            existing.setConfirmedStartAt(startAt);
            existing.setConfirmedEndAt(endAt);
            existing.setPlannedBreakMinutes(plannedBreak);
            existing.setStatus("CONFIRMED");
            if (batchDto.getOwnerMemo() != null && !batchDto.getOwnerMemo().isBlank()) {
                existing.setOwnerMemo(batchDto.getOwnerMemo());
            }

            shiftMapper.updateShiftConfirm(existing);
            confirmedCount++;
        }

        return confirmedCount;
    }

    /**
     * シフト予定 vs 実績打刻 リアルタイム対照エンジン
     * 指定店舗・指定日の確定シフトと実際の打刻実績を照合し、遅刻・早退・欠勤・正常を判定する
     */
    public List<ShiftComparisonDto> compareShiftWithAttendance(Long storeId, LocalDate workDate) {
        if (storeId == null) {
            throw new IllegalArgumentException("店舗IDは必須です。");
        }
        LocalDate targetDate = (workDate != null) ? workDate : LocalDate.now();

        // 1. 指定日の確定シフト一覧取得
        List<ShiftDto> confirmedShifts = shiftMapper.findConfirmedShiftsByDate(storeId, targetDate);

        // 2. 指定日の打刻実績一覧取得
        List<AttendanceDto> actualAttendances = attendanceMapper.findAttendancesByStoreAndDate(storeId, targetDate);

        // スタッフIDをキーとする打刻マップ作成
        Map<Long, AttendanceDto> attendanceMap = actualAttendances.stream()
                .collect(Collectors.toMap(AttendanceDto::getStoreStaffId, a -> a, (a1, a2) -> a1));

        List<ShiftComparisonDto> comparisonList = new ArrayList<>();
        LocalDateTime now = LocalDateTime.now();

        // 3. 確定シフトを基準に対照判定
        for (ShiftDto shift : confirmedShifts) {
            if (shift.getConfirmedStartAt() == null || shift.getConfirmedEndAt() == null) {
                continue; // 確定時間が未設定のデータは防御的にスキップ
            }
            ShiftComparisonDto dto = new ShiftComparisonDto();
            dto.setShiftId(shift.getShiftId());
            dto.setStoreStaffId(shift.getStoreStaffId());
            dto.setStaffName(shift.getStaffName());
            dto.setWorkDate(shift.getWorkDate());
            dto.setPlannedStartAt(shift.getConfirmedStartAt());
            dto.setPlannedEndAt(shift.getConfirmedEndAt());
            dto.setPlannedBreakMinutes(shift.getPlannedBreakMinutes() != null ? shift.getPlannedBreakMinutes() : 0);

            // 実働予定時間（分）
            long grossPlannedMinutes = Duration.between(shift.getConfirmedStartAt(), shift.getConfirmedEndAt()).toMinutes();
            dto.setPlannedWorkMinutes(Math.max(0, grossPlannedMinutes - dto.getPlannedBreakMinutes()));

            AttendanceDto attendance = attendanceMap.remove(shift.getStoreStaffId());

            if (attendance != null) {
                dto.setAttendanceId(attendance.getAttendanceId());
                dto.setActualClockInAt(attendance.getClockInAt());
                dto.setActualClockOutAt(attendance.getClockOutAt());
                dto.setActualBreakMinutes(attendance.getTotalBreakMinutes() != null ? attendance.getTotalBreakMinutes() : 0);
                dto.setActualWorkMinutes(attendance.getNetWorkMinutes() != null ? (long) attendance.getNetWorkMinutes() : 0L);

                // 実働差異（実績 - 予定）
                dto.setDiffWorkMinutes(dto.getActualWorkMinutes() - dto.getPlannedWorkMinutes());

                // 遅刻判定（予定時刻より5分以上超過で出勤）
                long lateMinutes = 0;
                boolean isLate = false;
                if (attendance.getClockInAt() != null && attendance.getClockInAt().isAfter(shift.getConfirmedStartAt().plusMinutes(5))) {
                    lateMinutes = Duration.between(shift.getConfirmedStartAt(), attendance.getClockInAt()).toMinutes();
                    isLate = true;
                }
                dto.setLateMinutes(lateMinutes);

                // 早退判定（退勤済かつ予定終了時刻より5分以上前）
                long earlyLeaveMinutes = 0;
                boolean isEarlyLeave = false;
                if (attendance.getClockOutAt() != null && attendance.getClockOutAt().isBefore(shift.getConfirmedEndAt().minusMinutes(5))) {
                    earlyLeaveMinutes = Duration.between(attendance.getClockOutAt(), shift.getConfirmedEndAt()).toMinutes();
                    isEarlyLeave = true;
                }
                dto.setEarlyLeaveMinutes(earlyLeaveMinutes);

                // 総合ステータス判定
                if (attendance.getClockOutAt() == null) {
                    dto.setComparisonStatus("WORKING");
                    dto.setStatusLabel(isLate ? "勤務中 (遅刻 " + lateMinutes + "分)" : "勤務中 (正常)");
                } else if (isLate && isEarlyLeave) {
                    dto.setComparisonStatus("LATE_AND_EARLY");
                    dto.setStatusLabel("遅刻・早退 (遅刻 " + lateMinutes + "分 / 早退 " + earlyLeaveMinutes + "分)");
                } else if (isLate) {
                    dto.setComparisonStatus("LATE");
                    dto.setStatusLabel("遅刻 (" + lateMinutes + "分)");
                } else if (isEarlyLeave) {
                    dto.setComparisonStatus("EARLY_LEAVE");
                    dto.setStatusLabel("早退 (" + earlyLeaveMinutes + "分)");
                } else {
                    dto.setComparisonStatus("NORMAL");
                    dto.setStatusLabel("正常 (予定通り)");
                }

            } else {
                // 打刻なしのケース（欠勤判定）
                dto.setActualBreakMinutes(0);
                dto.setActualWorkMinutes(0L);
                dto.setLateMinutes(0L);
                dto.setEarlyLeaveMinutes(0L);
                dto.setDiffWorkMinutes(-dto.getPlannedWorkMinutes());

                if (now.isAfter(shift.getConfirmedStartAt().plusMinutes(30))) {
                    dto.setComparisonStatus("ABSENT");
                    dto.setStatusLabel("欠勤 (未出勤)");
                } else {
                    dto.setComparisonStatus("PENDING");
                    dto.setStatusLabel("出勤待ち");
                }
            }

            comparisonList.add(dto);
        }

        // 4. シフト予定がないにもかかわらず打刻があるスタッフ（予定外勤務）
        for (AttendanceDto extra : attendanceMap.values()) {
            ShiftComparisonDto dto = new ShiftComparisonDto();
            dto.setStoreStaffId(extra.getStoreStaffId());
            dto.setWorkDate(extra.getWorkDate());
            dto.setAttendanceId(extra.getAttendanceId());
            dto.setActualClockInAt(extra.getClockInAt());
            dto.setActualClockOutAt(extra.getClockOutAt());
            dto.setActualBreakMinutes(extra.getTotalBreakMinutes() != null ? extra.getTotalBreakMinutes() : 0);
            dto.setActualWorkMinutes(extra.getNetWorkMinutes() != null ? (long) extra.getNetWorkMinutes() : 0L);
            dto.setPlannedBreakMinutes(0);
            dto.setPlannedWorkMinutes(0L);
            dto.setDiffWorkMinutes(dto.getActualWorkMinutes());
            dto.setLateMinutes(0L);
            dto.setEarlyLeaveMinutes(0L);
            dto.setComparisonStatus("UNSCHEDULED");
            dto.setStatusLabel("予定外勤務 (シフト未登録)");
            comparisonList.add(dto);
        }

        return comparisonList;
    }
}
