package com.example.kintai.domain.attendance.service;

import com.example.kintai.domain.attendance.dto.AttendanceBreakDto;
import com.example.kintai.domain.attendance.dto.AttendanceDto;
import com.example.kintai.domain.attendance.dto.AttendanceModRequestDto;
import com.example.kintai.domain.attendance.dto.AttendanceModResponseDto;
import com.example.kintai.domain.attendance.dto.AttendanceModReviewDto;
import com.example.kintai.domain.attendance.mapper.AttendanceMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * AttendanceModWorkflowTest（勤怠修正申請・電子決裁ワークフロー単体テスト）
 * 悲観的ロック(排他制御)、勤務時間帯重複防御(R-03)、給与確定衝突防止、
 * 承認時の労働基準法第37条再計算および事後承認マーク付与を検証する。
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("AttendanceService 勤怠修正ワークフロー単体テスト")
class AttendanceModWorkflowTest {

    @Mock
    private AttendanceMapper attendanceMapper;

    @InjectMocks
    private AttendanceService attendanceService;

    @Nested
    @DisplayName("勤怠修正申請（スタッフ申請）の検証")
    class RequestModificationTest {

        @Test
        @DisplayName("勤務時間重複防御: 既存勤務記録と重複する場合はIllegalStateExceptionがスローされること")
        void requestModification_WhenOverlappingAttendanceExists_ShouldThrowException() {
            LocalDate workDate = LocalDate.of(2026, 11, 10);
            LocalDateTime clockIn = LocalDateTime.of(2026, 11, 10, 9, 0);
            LocalDateTime clockOut = LocalDateTime.of(2026, 11, 10, 18, 0);

            AttendanceModRequestDto dto = new AttendanceModRequestDto();
            dto.setStoreStaffId(10L);
            dto.setWorkDate(workDate);
            dto.setRequestedClockInAt(clockIn);
            dto.setRequestedClockOutAt(clockOut);
            dto.setReason("打刻忘れによる事後修正申請");

            when(attendanceMapper.isPayrollConfirmedForDate(10L, workDate)).thenReturn(false);
            // 重複件数 1件検知
            when(attendanceMapper.countOverlappingAttendances(10L, clockIn, clockOut, null)).thenReturn(1);

            assertThatThrownBy(() -> attendanceService.requestModification(dto))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("重複する既存の勤務記録が存在します");

            verify(attendanceMapper).lockStoreStaffForUpdate(10L);
            verify(attendanceMapper, never()).insertModRequest(any());
        }

        @Test
        @DisplayName("正常系: 既存勤怠に対する修正申請時、悲観的ロックが確保され状態がMOD_REQUESTEDに保留されること")
        void requestModification_WhenValid_ShouldLockAndSetModRequested() {
            LocalDate workDate = LocalDate.of(2026, 11, 10);
            LocalDateTime clockIn = LocalDateTime.of(2026, 11, 10, 9, 0);
            LocalDateTime clockOut = LocalDateTime.of(2026, 11, 10, 18, 0);

            AttendanceModRequestDto dto = new AttendanceModRequestDto();
            dto.setAttendanceId(100L);
            dto.setStoreStaffId(10L);
            dto.setWorkDate(workDate);
            dto.setRequestedClockInAt(clockIn);
            dto.setRequestedClockOutAt(clockOut);
            dto.setReason("打刻漏れの修正申請");

            AttendanceDto original = new AttendanceDto();
            original.setAttendanceId(100L);
            original.setStoreStaffId(10L);
            original.setStoreId(1L);
            original.setClockInAt(clockIn);
            original.setClockOutAt(clockOut);
            original.setTotalBreakMinutes(60);
            original.setAttendanceStatus("COMPLETED");

            when(attendanceMapper.isPayrollConfirmedForDate(10L, workDate)).thenReturn(false);
            when(attendanceMapper.countOverlappingAttendances(10L, clockIn, clockOut, 100L)).thenReturn(0);
            when(attendanceMapper.findById(100L)).thenReturn(original);
            when(attendanceMapper.findBreaksByAttendanceId(100L)).thenReturn(Collections.emptyList());

            attendanceService.requestModification(dto);

            // 排他制御・申請登録・状態保留(MOD_REQUESTED)の呼出検証
            verify(attendanceMapper).lockStoreStaffForUpdate(10L);
            verify(attendanceMapper).insertModRequest(dto);
            verify(attendanceMapper).updateAttendanceStatus(100L, "MOD_REQUESTED");
        }
    }

    @Nested
    @DisplayName("店長による審査（承認・却下）の検証")
    class ReviewModificationTest {

        @Test
        @DisplayName("承認処理: 承認時に労働法再計算が行われ、MANUAL_APPROVEDおよびCOMPLETEDが付与されること")
        void reviewModification_WhenApproved_ShouldUpdateAttendanceWithManualApproved() {
            Long modRequestId = 1L;
            Long reviewerId = 999L;
            LocalDate workDate = LocalDate.of(2026, 11, 10);
            LocalDateTime reqIn = LocalDateTime.of(2026, 11, 10, 13, 0);
            LocalDateTime reqOut = LocalDateTime.of(2026, 11, 10, 23, 0); // 10時間拘束

            AttendanceModResponseDto req = new AttendanceModResponseDto();
            req.setModRequestId(modRequestId);
            req.setAttendanceId(100L);
            req.setStoreStaffId(10L);
            req.setStoreId(1L);
            req.setWorkDate(workDate);
            req.setRequestedClockInAt(reqIn);
            req.setRequestedClockOutAt(reqOut);
            req.setRequestedBreakMinutes(60);
            req.setApprovalStatus("PENDING");

            AttendanceDto existing = new AttendanceDto();
            existing.setAttendanceId(100L);
            existing.setStoreStaffId(10L);
            existing.setAttendanceStatus("MOD_REQUESTED");

            AttendanceBreakDto breakDto = new AttendanceBreakDto();
            breakDto.setBreakStartAt(LocalDateTime.of(2026, 11, 10, 17, 0));
            breakDto.setBreakEndAt(LocalDateTime.of(2026, 11, 10, 18, 0));
            breakDto.setDurationMinutes(60);

            when(attendanceMapper.findModRequestById(modRequestId)).thenReturn(req);
            when(attendanceMapper.isPayrollConfirmedForDate(10L, workDate)).thenReturn(false);
            when(attendanceMapper.countOverlappingAttendances(10L, reqIn, reqOut, 100L)).thenReturn(0);
            when(attendanceMapper.findModBreaksByRequestId(modRequestId, "REQUESTED")).thenReturn(List.of(breakDto));
            when(attendanceMapper.findById(100L)).thenReturn(existing);
            when(attendanceMapper.updateModRequestReview(eq(modRequestId), eq("APPROVED"), eq(reviewerId), isNull(), any())).thenReturn(1);

            AttendanceModReviewDto reviewDto = new AttendanceModReviewDto();
            reviewDto.setAction("APPROVE");

            attendanceService.reviewModification(modRequestId, reviewerId, reviewDto);

            // 悲観的ロックおよび更新キャプチャ検証
            verify(attendanceMapper).lockStoreStaffForUpdate(10L);

            ArgumentCaptor<AttendanceDto> captor = ArgumentCaptor.forClass(AttendanceDto.class);
            verify(attendanceMapper).updateAttendance(captor.capture());

            AttendanceDto updated = captor.getValue();
            assertThat(updated.getClockInMethod()).isEqualTo("MANUAL_APPROVED");
            assertThat(updated.getAttendanceStatus()).isEqualTo("COMPLETED");
            assertThat(updated.getNetWorkMinutes()).isEqualTo(540); // 9時間 = 540分
            assertThat(updated.getNightWorkMinutes()).isEqualTo(60); // 22:00~23:00 = 60分
            assertThat(updated.getOvertimeMinutes()).isEqualTo(60); // 8時間超過 = 60分

            verify(attendanceMapper).updateModRequestReview(eq(modRequestId), eq("APPROVED"), eq(reviewerId), isNull(), any());
        }

        @Test
        @DisplayName("却下処理: 却下時に既存勤怠の保留状態がCOMPLETEDに復帰し却下理由が記録されること")
        void reviewModification_WhenRejected_ShouldRestoreCompletedStatus() {
            Long modRequestId = 1L;
            Long reviewerId = 999L;
            LocalDate workDate = LocalDate.of(2026, 11, 10);

            AttendanceModResponseDto req = new AttendanceModResponseDto();
            req.setModRequestId(modRequestId);
            req.setAttendanceId(100L);
            req.setStoreStaffId(10L);
            req.setWorkDate(workDate);
            req.setApprovalStatus("PENDING");

            when(attendanceMapper.findModRequestById(modRequestId)).thenReturn(req);
            when(attendanceMapper.isPayrollConfirmedForDate(10L, workDate)).thenReturn(false);
            when(attendanceMapper.updateModRequestReview(eq(modRequestId), eq("REJECTED"), eq(reviewerId), eq("防犯カメラ記録と勤務時間が一致しないため"), any())).thenReturn(1);

            AttendanceModReviewDto reviewDto = new AttendanceModReviewDto();
            reviewDto.setAction("REJECT");
            reviewDto.setRejectReason("防犯カメラ記録と勤務時間が一致しないため");

            attendanceService.reviewModification(modRequestId, reviewerId, reviewDto);

            // COMPLETED状態復帰およびREJECTED更新の検証
            verify(attendanceMapper).updateAttendanceStatus(100L, "COMPLETED");
            verify(attendanceMapper).updateModRequestReview(eq(modRequestId), eq("REJECTED"), eq(reviewerId), eq("防犯カメラ記録と勤務時間が一致しないため"), any());
        }
    }

    @Nested
    @DisplayName("勤怠修正申請一覧取得（バッチ取得・N+1解消）の検証")
    class GetModRequestsTest {

        @Test
        @DisplayName("バッチ取得: 全申請の休憩スナップショットが一括取得され、N+1クエリが発生しないこと")
        void getModRequests_ShouldBatchLoadBreaksAndPreventNPlusOne() {
            Long storeId = 1L;
            AttendanceModResponseDto item1 = new AttendanceModResponseDto();
            item1.setModRequestId(101L);
            AttendanceModResponseDto item2 = new AttendanceModResponseDto();
            item2.setModRequestId(102L);

            when(attendanceMapper.findModRequestsByStoreId(storeId, "PENDING")).thenReturn(List.of(item1, item2));

            AttendanceBreakDto break1 = new AttendanceBreakDto();
            break1.setAttendanceId(101L);
            break1.setBreakSetType("BEFORE");

            AttendanceBreakDto break2 = new AttendanceBreakDto();
            break2.setAttendanceId(102L);
            break2.setBreakSetType("REQUESTED");

            when(attendanceMapper.findModBreaksByRequestIds(List.of(101L, 102L))).thenReturn(List.of(break1, break2));

            List<AttendanceModResponseDto> result = attendanceService.getModRequests(storeId, "PENDING");

            assertThat(result).hasSize(2);
            assertThat(result.get(0).getBeforeBreaks()).containsExactly(break1);
            assertThat(result.get(0).getRequestedBreaks()).isEmpty();
            assertThat(result.get(1).getBeforeBreaks()).isEmpty();
            assertThat(result.get(1).getRequestedBreaks()).containsExactly(break2);

            // N+1防止: findModBreaksByRequestId(単件)は一切呼ばれず、一括メソッドが1回のみ呼ばれたことを検証
            verify(attendanceMapper, times(1)).findModBreaksByRequestIds(List.of(101L, 102L));
            verify(attendanceMapper, never()).findModBreaksByRequestId(anyLong(), anyString());
        }
    }
}
