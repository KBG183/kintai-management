# Final Full Audit Bundle: bundle_3b_calendar_ui
Generated: 2026-11-14T12:51:27+09:00

## FILE: src\main\java\com\example\kintai\domain\shift\dto\CalendarEventDto.java
```
package com.example.kintai.domain.shift.dto;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public class CalendarEventDto {
    private String id;
    private String title;
    private String start;
    private String end;
    private String color;
    private ExtendedProps extendedProps;

    public CalendarEventDto() {}

    public static class ExtendedProps {
        private Long shiftId;
        private Long storeStaffId;
        private String staffName;
        private String status;
        private Integer plannedBreakMinutes;
        private String staffMemo;
        private String ownerMemo;

        public ExtendedProps() {}

        public Long getShiftId() { return shiftId; }
        public void setShiftId(Long shiftId) { this.shiftId = shiftId; }
        public Long getStoreStaffId() { return storeStaffId; }
        public void setStoreStaffId(Long storeStaffId) { this.storeStaffId = storeStaffId; }
        public String getStaffName() { return staffName; }
        public void setStaffName(String staffName) { this.staffName = staffName; }
        public String getStatus() { return status; }
        public void setStatus(String status) { this.status = status; }
        public Integer getPlannedBreakMinutes() { return plannedBreakMinutes; }
        public void setPlannedBreakMinutes(Integer plannedBreakMinutes) { this.plannedBreakMinutes = plannedBreakMinutes; }
        public String getStaffMemo() { return staffMemo; }
        public void setStaffMemo(String staffMemo) { this.staffMemo = staffMemo; }
        public String getOwnerMemo() { return ownerMemo; }
        public void setOwnerMemo(String ownerMemo) { this.ownerMemo = ownerMemo; }
    }

    public static CalendarEventDto from(ShiftDto shift) {
        return from(shift, null, true);
    }

    /**
     * プライバシー保護・サーバーサイドマスキング対応
     * 他スタッフの個人メモは管理者(ROLE_OWNER/ROLE_MANAGER)または本人のみに限定して返却
     */
    public static CalendarEventDto from(ShiftDto shift, Long currentStaffId, boolean isManager) {
        CalendarEventDto dto = new CalendarEventDto();
        dto.setId(String.valueOf(shift.getShiftId()));

        DateTimeFormatter isoFormatter = DateTimeFormatter.ISO_LOCAL_DATE_TIME;
        DateTimeFormatter timeFormatter = DateTimeFormatter.ofPattern("HH:mm");

        String status = shift.getStatus();
        LocalDateTime eventStart;
        LocalDateTime eventEnd;

        // オーナー直接割り当てシフト（requested_*がNULL）の場合でもイベント区間がNULLにならないよう防衛
        if ("CONFIRMED".equalsIgnoreCase(status)) {
            eventStart = shift.getConfirmedStartAt() != null ? shift.getConfirmedStartAt() : shift.getRequestedStartAt();
            eventEnd = shift.getConfirmedEndAt() != null ? shift.getConfirmedEndAt() : shift.getRequestedEndAt();
            dto.setColor("#0d6efd"); // 青色（確定）
            String timeRange = (eventStart != null && eventEnd != null) 
                    ? " (" + eventStart.format(timeFormatter) + " - " + eventEnd.format(timeFormatter) + ")" 
                    : "";
            dto.setTitle("[確定] " + shift.getStaffName() + timeRange);
        } else if ("SUBMITTED".equalsIgnoreCase(status)) {
            eventStart = shift.getRequestedStartAt();
            eventEnd = shift.getRequestedEndAt();
            dto.setColor("#fd7e14"); // オレンジ色（希望）
            String timeRange = (eventStart != null && eventEnd != null) 
                    ? " (" + eventStart.format(timeFormatter) + " - " + eventEnd.format(timeFormatter) + ")" 
                    : "";
            dto.setTitle("[希望] " + shift.getStaffName() + timeRange);
        } else { // REJECTED など
            eventStart = shift.getRequestedStartAt() != null ? shift.getRequestedStartAt() : shift.getConfirmedStartAt();
            eventEnd = shift.getRequestedEndAt() != null ? shift.getRequestedEndAt() : shift.getConfirmedEndAt();
            dto.setColor("#6c757d"); // グレー
            dto.setTitle("[未割当] " + shift.getStaffName());
        }

        if (eventStart != null) dto.setStart(eventStart.format(isoFormatter));
        if (eventEnd != null) dto.setEnd(eventEnd.format(isoFormatter));

        ExtendedProps props = new ExtendedProps();
        props.setShiftId(shift.getShiftId());
        props.setStoreStaffId(shift.getStoreStaffId());
        props.setStaffName(shift.getStaffName());
        props.setStatus(shift.getStatus());
        props.setPlannedBreakMinutes(shift.getPlannedBreakMinutes());

        // サーバーサイド・プライバシー保護マスク
        boolean isSelf = (currentStaffId != null && currentStaffId.equals(shift.getStoreStaffId()));
        if (isManager || isSelf) {
            props.setStaffMemo(shift.getStaffMemo());
            props.setOwnerMemo(shift.getOwnerMemo());
        } else {
            props.setStaffMemo(null); // 他スタッフのメモはAPIペイロードから完全除外
            props.setOwnerMemo(null);
        }

        dto.setExtendedProps(props);
        return dto;
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getStart() { return start; }
    public void setStart(String start) { this.start = start; }
    public String getEnd() { return end; }
    public void setEnd(String end) { this.end = end; }
    public String getColor() { return color; }
    public void setColor(String color) { this.color = color; }
    public ExtendedProps getExtendedProps() { return extendedProps; }
    public void setExtendedProps(ExtendedProps extendedProps) { this.extendedProps = extendedProps; }
}
```

## FILE: src\main\resources\templates\shift\calendar.html
```
<!DOCTYPE html>
<html lang="ja" xmlns:th="http://www.thymeleaf.org"
      xmlns:sec="http://www.thymeleaf.org/extras/spring-security"
      th:replace="~{common/layout :: layout(~{::title}, ~{::#content})}">
<head>
    <title>シフト管理・希望提出 | KINTAI SYSTEM</title>
</head>
<body>
<div id="content">
    <!-- CSRF トークン保持用 -->
    <meta name="_csrf" th:content="${_csrf?.token}"/>
    <meta name="_csrf_header" th:content="${_csrf?.headerName}"/>

    <div class="d-flex justify-content-between align-items-center mb-3">
        <div>
            <h3 class="fw-bold mb-1"><i class="fa-regular fa-calendar-days text-primary me-2"></i>シフト管理・希望提出</h3>
            <p class="text-muted small mb-0">カレンダーの日付をクリックして希望シフトを登録、または登録済みシフトをクリックして詳細・確定を行います。</p>
        </div>
        <div class="d-flex align-items-center gap-2">
            <span class="badge bg-success"><i class="fa-solid fa-check me-1"></i>確定 (CONFIRMED)</span>
            <span class="badge bg-warning text-dark"><i class="fa-solid fa-clock me-1"></i>申請中 (SUBMITTED)</span>
            <span class="badge bg-secondary"><i class="fa-solid fa-ban me-1"></i>却下 (REJECTED)</span>
        </div>
    </div>

    <!-- カレンダー本体カード -->
    <div class="card shadow-sm border-0 mb-4">
        <div class="card-body p-3">
            <div id="calendar"></div>
        </div>
    </div>

    <!-- 1. アルバイト用: 希望シフト提出モーダル -->
    <div class="modal fade" id="shiftRequestModal" tabindex="-1" aria-labelledby="shiftRequestModalLabel" aria-hidden="true">
        <div class="modal-dialog">
            <div class="modal-content">
                <div class="modal-header bg-primary text-white">
                    <h5 class="modal-title fw-bold" id="shiftRequestModalLabel"><i class="fa-solid fa-calendar-plus me-2"></i>希望シフト登録</h5>
                    <button type="button" class="btn-close btn-close-white" data-bs-dismiss="modal" aria-label="閉じる"></button>
                </div>
                <div class="modal-body">
                    <form id="shiftRequestForm">
                        <div class="mb-3">
                            <label class="form-label fw-bold">勤務予定日</label>
                            <input type="date" class="form-control" id="reqWorkDate" readonly required>
                        </div>
                        <div class="row">
                            <div class="col-6 mb-3">
                                <label class="form-label fw-bold">希望出勤時刻</label>
                                <input type="time" class="form-control" id="reqStartTime" value="09:00" required>
                            </div>
                            <div class="col-6 mb-3">
                                <label class="form-label fw-bold">希望退勤時刻</label>
                                <input type="time" class="form-control" id="reqEndTime" value="18:00" required>
                            </div>
                        </div>
                        <div class="mb-3">
                            <label class="form-label fw-bold">スタッフ備考・連絡事項 (任意)</label>
                            <textarea class="form-control" id="reqStaffMemo" rows="2" placeholder="例: 授業のため15分前後する可能性があります"></textarea>
                        </div>
                        <div class="alert alert-info py-2 small mb-0">
                            <i class="fa-solid fa-circle-info me-1"></i>提出された希望シフトは店長による確認・確定を経て確定シフトとなります。
                        </div>
                    </form>
                </div>
                <div class="modal-footer">
                    <button type="button" class="btn btn-secondary" data-bs-dismiss="modal">キャンセル</button>
                    <button type="button" class="btn btn-primary" id="btnSubmitShiftRequest">
                        <i class="fa-solid fa-paper-plane me-1"></i>希望提出
                    </button>
                </div>
            </div>
        </div>
    </div>

    <!-- 2. スタッフ用: 自身の希望シフト詳細・取消モーダル -->
    <div class="modal fade" id="shiftDetailModal" tabindex="-1" aria-labelledby="shiftDetailModalLabel" aria-hidden="true">
        <div class="modal-dialog">
            <div class="modal-content">
                <div class="modal-header">
                    <h5 class="modal-title fw-bold" id="shiftDetailModalLabel"><i class="fa-solid fa-calendar-check me-2"></i>シフト詳細</h5>
                    <button type="button" class="btn-close" data-bs-dismiss="modal" aria-label="閉じる"></button>
                </div>
                <div class="modal-body">
                    <input type="hidden" id="detailShiftId">
                    <table class="table table-bordered mb-3">
                        <tbody>
                            <tr>
                                <th class="table-light w-25">スタッフ</th>
                                <td id="detailStaffName">-</td>
                            </tr>
                            <tr>
                                <th class="table-light">勤務日</th>
                                <td id="detailWorkDate">-</td>
                            </tr>
                            <tr>
                                <th class="table-light">時間帯</th>
                                <td id="detailTimeRange">-</td>
                            </tr>
                            <tr>
                                <th class="table-light">ステータス</th>
                                <td id="detailStatus">-</td>
                            </tr>
                            <tr>
                                <th class="table-light">備考</th>
                                <td id="detailMemo">-</td>
                            </tr>
                        </tbody>
                    </table>
                    <div id="detailAlertMessage" class="alert alert-warning py-2 small mb-0 d-none"></div>
                </div>
                <div class="modal-footer d-flex justify-content-between">
                    <div>
                        <button type="button" class="btn btn-outline-danger d-none" id="btnDeleteShiftRequest">
                            <i class="fa-solid fa-trash me-1"></i>申請取消 (削除)
                        </button>
                    </div>
                    <button type="button" class="btn btn-secondary" data-bs-dismiss="modal">閉じる</button>
                </div>
            </div>
        </div>
    </div>

    <!-- 3. 店長用: シフト確定・調整モーダル -->
    <div class="modal fade" id="shiftConfirmModal" tabindex="-1" aria-labelledby="shiftConfirmModalLabel" aria-hidden="true">
        <div class="modal-dialog">
            <div class="modal-content">
                <div class="modal-header bg-success text-white">
                    <h5 class="modal-title fw-bold" id="shiftConfirmModalLabel"><i class="fa-solid fa-clipboard-check me-2"></i>シフト確定 / 調整 (店長管理)</h5>
                    <button type="button" class="btn-close btn-close-white" data-bs-dismiss="modal" aria-label="閉じる"></button>
                </div>
                <div class="modal-body">
                    <form id="shiftConfirmForm">
                        <input type="hidden" id="confShiftId">
                        <div class="mb-3">
                            <label class="form-label fw-bold">スタッフ名</label>
                            <input type="text" class="form-control-plaintext fw-bold" id="confStaffName" readonly>
                        </div>
                        <div class="mb-3">
                            <label class="form-label fw-bold">勤務日</label>
                            <input type="date" class="form-control" id="confWorkDate" readonly>
                        </div>
                        <div class="row">
                            <div class="col-6 mb-3">
                                <label class="form-label fw-bold">確定出勤時刻</label>
                                <input type="time" class="form-control" id="confStartTime" required>
                            </div>
                            <div class="col-6 mb-3">
                                <label class="form-label fw-bold">確定退勤時刻</label>
                                <input type="time" class="form-control" id="confEndTime" required>
                            </div>
                        </div>
                        <div class="mb-3">
                            <label class="form-label fw-bold">計画休憩時間 (分)</label>
                            <input type="number" class="form-control" id="confBreakMinutes" min="0" step="15" required>
                            <div class="form-text">6時間超45分以上、8時間超60分以上の法定休憩が推奨されます。</div>
                        </div>
                        <div class="mb-3">
                            <label class="form-label fw-bold">ステータス</label>
                            <select class="form-select" id="confStatus">
                                <option value="CONFIRMED">確定 (CONFIRMED)</option>
                                <option value="REJECTED">却下 (REJECTED)</option>
                            </select>
                        </div>
                        <div class="mb-3">
                            <label class="form-label fw-bold">店長メモ</label>
                            <textarea class="form-control" id="confOwnerMemo" rows="2" placeholder="調整理由などを記入"></textarea>
                        </div>
                    </form>
                </div>
                <div class="modal-footer">
                    <button type="button" class="btn btn-secondary" data-bs-dismiss="modal">閉じる</button>
                    <button type="button" class="btn btn-success" id="btnSubmitShiftConfirm">
                        <i class="fa-solid fa-check me-1"></i>確定保存
                    </button>
                </div>
            </div>
        </div>
    </div>

    <!-- FullCalendar初期化 & イベント処理スクリプト -->
    <script th:inline="javascript">
        document.addEventListener('DOMContentLoaded', function () {
            const storeId = /*[[${storeId}]]*/ 1;
            const isManager = /*[[${#authorization.expression('hasAnyRole(''OWNER'', ''MANAGER'')')}]]*/ false;
            const currentStaffId = /*[[${#authentication?.principal?.storeStaffId}]]*/ null;

            const calendarEl = document.getElementById('calendar');
            const reqModal = new bootstrap.Modal(document.getElementById('shiftRequestModal'));
            const detailModal = new bootstrap.Modal(document.getElementById('shiftDetailModal'));
            const confModal = new bootstrap.Modal(document.getElementById('shiftConfirmModal'));

            // CSRF トークン取得
            const csrfTokenMeta = document.querySelector('meta[name="_csrf"]');
            const csrfHeaderMeta = document.querySelector('meta[name="_csrf_header"]');
            const csrfToken = csrfTokenMeta ? csrfTokenMeta.getAttribute('content') : '';
            const csrfHeader = csrfHeaderMeta ? csrfHeaderMeta.getAttribute('content') : 'X-CSRF-TOKEN';

            const headers = {
                'Content-Type': 'application/json'
            };
            if (csrfToken) {
                headers[csrfHeader] = csrfToken;
            }

            const calendar = new FullCalendar.Calendar(calendarEl, {
                initialView: 'dayGridMonth',
                locale: 'ja',
                headerToolbar: {
                    left: 'prev,next today',
                    center: 'title',
                    right: 'dayGridMonth,timeGridWeek,timeGridDay'
                },
                navLinks: true,
                editable: false,
                selectable: true,
                events: function (info, successCallback, failureCallback) {
                    const startStr = info.startStr.substring(0, 10);
                    const endStr = info.endStr.substring(0, 10);
                    fetch(`/api/shifts/events?storeId=${storeId}&start=${startStr}&end=${endStr}`)
                        .then(res => {
                            if (!res.ok) throw new Error('シフトデータの取得に失敗しました。');
                            return res.json();
                        })
                        .then(data => {
                            const events = data.map(ev => {
                                let bg = '#ffc107'; // SUBMITTED
                                let border = '#e0a800';
                                let text = '#000000';
                                const status = ev.extendedProps.status;
                                if (status === 'CONFIRMED') {
                                    bg = '#198754';
                                    border = '#157347';
                                    text = '#ffffff';
                                } else if (status === 'REJECTED' || status === 'CANCELED') {
                                    bg = '#6c757d';
                                    border = '#5a6268';
                                    text = '#ffffff';
                                }
                                return {
                                    ...ev,
                                    backgroundColor: bg,
                                    borderColor: border,
                                    textColor: text
                                };
                            });
                            successCallback(events);
                        })
                        .catch(err => {
                            console.error(err);
                            failureCallback(err);
                        });
                },
                dateClick: function (info) {
                    // JST(日本標準時)基準での過去日チェック (UTCズレ防止)
                    const now = new Date();
                    const year = now.getFullYear();
                    const month = String(now.getMonth() + 1).padStart(2, '0');
                    const day = String(now.getDate()).padStart(2, '0');
                    const todayStr = `${year}-${month}-${day}`;

                    if (info.dateStr < todayStr) {
                        alert('過去の日付に対する希望シフトは申請できません。');
                        return;
                    }
                    document.getElementById('reqWorkDate').value = info.dateStr;
                    document.getElementById('reqStartTime').value = '09:00';
                    document.getElementById('reqEndTime').value = '18:00';
                    document.getElementById('reqStaffMemo').value = '';
                    reqModal.show();
                },
                eventClick: function (info) {
                    const ev = info.event.extendedProps;
                    const isOwnShift = currentStaffId && (String(currentStaffId) === String(ev.storeStaffId));
                    const workDate = info.event.startStr.substring(0, 10);
                    const startTime = info.event.startStr.substring(11, 16);
                    const endTime = info.event.endStr.substring(11, 16);

                    if (isManager) {
                        // 店長: 確定・調整モーダル
                        document.getElementById('confShiftId').value = ev.shiftId;
                        document.getElementById('confStaffName').value = ev.staffName;
                        document.getElementById('confWorkDate').value = workDate;
                        document.getElementById('confStartTime').value = startTime;
                        document.getElementById('confEndTime').value = endTime;
                        document.getElementById('confBreakMinutes').value = ev.plannedBreakMinutes || 0;
                        document.getElementById('confStatus').value = (ev.status === 'REJECTED') ? 'REJECTED' : 'CONFIRMED';
                        document.getElementById('confOwnerMemo').value = ev.ownerMemo || '';
                        confModal.show();
                    } else {
                        // スタッフ: 詳細・取消モーダル (他スタッフのプライベートメモ非表示)
                        document.getElementById('detailShiftId').value = ev.shiftId;
                        document.getElementById('detailStaffName').innerText = ev.staffName;
                        document.getElementById('detailWorkDate').innerText = workDate;
                        document.getElementById('detailTimeRange').innerText = `${startTime} 〜 ${endTime}`;
                        
                        const statusBadge = (ev.status === 'CONFIRMED')
                            ? '<span class="badge bg-success">確定 (CONFIRMED)</span>'
                            : (ev.status === 'SUBMITTED')
                                ? '<span class="badge bg-warning text-dark">申請中 (SUBMITTED)</span>'
                                : '<span class="badge bg-secondary">却下 (REJECTED)</span>';
                        document.getElementById('detailStatus').innerHTML = statusBadge;

                        // プライバシー保護: 自身のリクエストのみメモ表示
                        if (isOwnShift) {
                            document.getElementById('detailMemo').innerText = ev.staffMemo || '特になし';
                        } else {
                            document.getElementById('detailMemo').innerText = '非公開 (他スタッフの希望)';
                        }

                        const btnDelete = document.getElementById('btnDeleteShiftRequest');
                        const alertBox = document.getElementById('detailAlertMessage');

                        // 自身の申請かつSUBMITTED状態のみ取消ボタンを表示
                        if (isOwnShift && ev.status === 'SUBMITTED') {
                            btnDelete.classList.remove('d-none');
                            alertBox.classList.add('d-none');
                        } else {
                            btnDelete.classList.add('d-none');
                            if (ev.status === 'CONFIRMED') {
                                alertBox.innerText = '確定済のシフトはスタッフ側からは取消できません。変更が必要な場合は店長へご連絡ください。';
                                alertBox.classList.remove('d-none');
                            } else {
                                alertBox.classList.add('d-none');
                            }
                        }
                        detailModal.show();
                    }
                }
            });

            calendar.render();

            // 1. 希望シフト提出
            document.getElementById('btnSubmitShiftRequest').addEventListener('click', function () {
                const startTime = document.getElementById('reqStartTime').value;
                const endTime = document.getElementById('reqEndTime').value;
                if (!startTime || !endTime) {
                    alert('希望時刻を入力してください。');
                    return;
                }
                if (startTime === endTime) {
                    alert('希望開始時刻と終了時刻を同一にすることはできません。');
                    return;
                }

                const payload = {
                    workDate: document.getElementById('reqWorkDate').value,
                    requestedStartTime: startTime + ':00',
                    requestedEndTime: endTime + ':00',
                    staffMemo: document.getElementById('reqStaffMemo').value
                };

                fetch('/api/shifts/request', {
                    method: 'POST',
                    headers: headers,
                    body: JSON.stringify(payload)
                })
                .then(async res => {
                    const data = await res.json();
                    if (res.ok) {
                        alert(data.message || '希望シフトを登録しました。');
                        reqModal.hide();
                        calendar.refetchEvents();
                    } else {
                        alert('登録エラー: ' + (data.message || 'シフトの登録に失敗しました。'));
                    }
                })
                .catch(err => alert('通信エラーが発生しました: ' + err));
            });

            // 2. スタッフによる希望シフト申請取消
            document.getElementById('btnDeleteShiftRequest').addEventListener('click', function () {
                const shiftId = document.getElementById('detailShiftId').value;
                if (!confirm('この希望シフトの申請を取り消しますか？')) {
                    return;
                }

                fetch(`/api/shifts/${shiftId}`, {
                    method: 'DELETE',
                    headers: headers
                })
                .then(async res => {
                    const data = await res.json();
                    if (res.ok) {
                        alert(data.message || '希望シフトの申請を取り消しました。');
                        detailModal.hide();
                        calendar.refetchEvents();
                    } else {
                        alert('取消エラー: ' + (data.message || '申請の取消に失敗しました。'));
                    }
                })
                .catch(err => alert('通信エラーが発生しました: ' + err));
            });

            // 3. 店長によるシフト確定
            document.getElementById('btnSubmitShiftConfirm').addEventListener('click', function () {
                const shiftId = document.getElementById('confShiftId').value;
                const startTime = document.getElementById('confStartTime').value;
                const endTime = document.getElementById('confEndTime').value;
                if (!startTime || !endTime) {
                    alert('確定時刻を入力してください。');
                    return;
                }
                if (startTime === endTime) {
                    alert('確定開始時刻と終了時刻を同一にすることはできません。');
                    return;
                }

                const breakMinRaw = document.getElementById('confBreakMinutes').value;
                const breakMin = parseInt(breakMinRaw, 10);
                if (isNaN(breakMin) || breakMin < 0) {
                    alert('計画休憩時間は0分以上の整数を入力してください。');
                    return;
                }

                const payload = {
                    confirmedStartTime: startTime + ':00',
                    confirmedEndTime: endTime + ':00',
                    plannedBreakMinutes: breakMin,
                    status: document.getElementById('confStatus').value,
                    ownerMemo: document.getElementById('confOwnerMemo').value
                };

                fetch(`/api/shifts/${shiftId}/confirm`, {
                    method: 'PUT',
                    headers: headers,
                    body: JSON.stringify(payload)
                })
                .then(async res => {
                    const data = await res.json();
                    if (res.ok) {
                        alert(data.message || 'シフトを確定保存しました。');
                        confModal.hide();
                        calendar.refetchEvents();
                    } else {
                        alert('保存エラー: ' + (data.message || 'シフトの保存に失敗しました。'));
                    }
                })
                .catch(err => alert('通信エラーが発生しました: ' + err));
            });
        });
    </script>
</div>
</body>
</html>
```

