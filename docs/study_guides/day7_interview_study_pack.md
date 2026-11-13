# 【Day 7 技術面接対策パック】勤怠修正申請・店長承認ワークフロー及び重複防御（R-03）・N+1クエリ解消

本書は、日本型勤怠・給与管理システム（kintai）のDay 7スプリントで実装された「勤怠修正申請・電子決裁ワークフローおよび排他制御」のアーキテクチャ背景、DB排他制御、および全実装ロジックを1つに統合した面接対策学習パックです。

---

## 1. ビジネス背景とアーキテクチャ設計根拠（Why & What）

### なぜ単なる「打刻データの直接UPDATE」を禁止し、ワークフローを導入したのか？
* **従来の課題（不正打刻・労使トラブルリスク）**:
  従業員が自由に打刻時刻を変更できる仕様にすると、残業代の不正水増しや改ざんが横行します。また、既に締められた過去の給与計算と実績勤怠が乖離し、労務監査（労働基準監督署）で是正勧告を受ける決定的な要因となります。
* **採用した解決策（2段階 電子決裁ワークフロー）**:
  1. **申請フェーズ**: 打刻漏れ・修正が必要な従業員は、修正前後の打刻時刻および休憩区間（1:N）を理由とともに「修正申請（`AttendanceModRequest`）」として提出。
  2. **ステータス保護**: 申請中レコードはステータスを `MOD_REQUESTED` に遷移させ、二重修正や通常打刻の競合をロック。
  3. **店長審査フェーズ**: 店長（管理者）が差分（Before vs Requested）を画面上で確認し、承認（`APPROVED`）または却下（`REJECTED`）。
  4. **事後承認マーク**: 承認された勤怠レコードには `clock_in_method = 'MANUAL_APPROVED'` を明示付与し、通常のGPS/WEB打刻と監査ログ上明確に区別。

---

## 2. データベース排他制御と整合性防護（R-03 / 重複遮断）

### 時間帯の重複防御（Overlapping Detection）
修正後の勤務時間帯が、同一従業員の他の有効な勤務記録（実績勤怠）と1分でも重なっている場合、不正な多重計上となるためDBクエリレベルで厳密に遮断：
```sql
SELECT COUNT(*) 
FROM attendance
WHERE store_staff_id = #{storeStaffId}
  AND attendance_status != 'CANCELLED'
  AND id != #{attendanceId}
  AND clock_in_at < #{clockOutAt}
  AND clock_out_at > #{clockInAt}
```

### 給与確定衝突防御および悲観的ロック（Pessimistic Lock）
* **給与確定ロック**: 対象勤務日の属する年月ですでに給与計算が確定（`CONFIRMED`/`PAID`）している場合、申請・承認ともに `409 Conflict` 例外をスローして更新を即時拒絶。
* **行ロック（`SELECT ... FOR UPDATE`）**: 承認トランザクション開始時に `store_staff` 行を悲観的ロックし、裏で走る月末給与自動集計バッチとのレースコンディションを完全排除。

---

## 3. Hermes 27B 監査によるN+1クエリ検出と解決（Code Quality）

* **検出された欠陥**: 修正申請一覧画面（`getModRequests`）において、申請リスト（N件）を取得した後に各ループ内で休憩取得クエリ（`findModBreaksByRequestId`）を都度発行していたため、典型的な `N+1 Query Problem` が発生。
* **解決アーキテクチャ**:
  - `findModBreaksByRequestIds(List<Long> requestIds)` による一括 `IN (...)` 取得を実装。
  - Java側で `Map<Long, List<AttendanceBreakDto>>` へ O(1) インメモリグルーピングすることで、SQL発行回数を「N+1回」から「**定数2回**」へ劇的削減。

---

## 4. コアサービス実装: AttendanceService.java（抜粋）

```java
// 修正申請トランザクション
@Transactional
public Long requestModification(AttendanceModRequestDto dto, Long currentStaffId) {
    // 1. 給与確定チェック (409 Conflict)
    validatePayrollNotClosed(dto.getStoreStaffId(), dto.getWorkDate());
    
    // 2. 時間帯重複検証 (R-03)
    validateNoOverlap(dto.getStoreStaffId(), null, dto.getRequestedClockInAt(), dto.getRequestedClockOutAt());
    
    // 3. 申請登録及び元勤怠のステータス保留 (MOD_REQUESTED)
    ...
}

// 店長承認・却下トランザクション
@Transactional
public void reviewModification(AttendanceModReviewDto dto, Long reviewerStaffId) {
    // 1. store_staff 行の悲観的ロック獲得
    attendanceMapper.lockStoreStaffForUpdate(request.getStoreStaffId());
    
    if (dto.getAction() == ReviewAction.APPROVE) {
        // 2. 労働基準法第37条に基づく実働・深夜・残業の再計算
        LaborHours hours = AttendanceTimeCalculator.calculate(
            req.getRequestedClockInAt(), req.getRequestedClockOutAt(), breaks
        );
        // 3. MANUAL_APPROVED 付与・COMPLETED 確定更新
        ...
    } else {
        // 4. 却下時は元の COMPLETED へ安全復元
        ...
    }
}
```
