# 🔍 Claude Cross-Review Request Bundle: Day 8 Monthly Payroll Batch & Concurrency Locking

> **Reviewer**: Claude (Senior Full-Stack & Concurrency Architect)  
> **Target Project**: Spring Boot 4 + Java 21 + MyBatis + MySQL 8.4 Enterprise Kintai (Attendance & Payroll) System  
> **Sprint**: Day 8 (Monthly Payroll Batch Confirmation & Pessimistic Locking)  
> **Verification Pipeline**: 4-Tier Collaborative System (Local Qwen 27B -> Antigravity Developer -> Automated Tests -> User Approver -> Claude Cross-Review)

---

## 🎯 검수 요청 목적 및 비즈니스 배경
본 프로젝트는 일본 현지 기업용 근태/급여 관리 시스템입니다.  
Day 8에서는 **"점포 단위 월간 급여 자동 집계 배치 및 마감 동시성 락(`SELECT ... FOR UPDATE`)"**을 구현했습니다.

### 핵심 비즈니스 요구사항 및 구현 내용
1. **점포 단위 월간 일괄 마감 (`confirmMonthlyPayroll`)**:
   - 점주가 매월 말 수십 명의 아르바이트생 급여를 원클릭으로 일괄 확정(`CONFIRMED`)
   - 미퇴근 타각(`WORKING`)이나 미승인 근태 수정 신청(`MOD_REQUESTED`)이 1건이라도 존재하면 일괄 확정 차단 (All-or-Nothing 원자성)
   - 급여 대상 기간 종료일(`rangeEndDate`) 이전의 조기 확정 방어
2. **동시성 배타 제어 및 멱등성 보장**:
   - 복수의 점주/관리자가 동시에 마감 버튼을 누르거나 네트워크 재전송이 발생해도 중복 확정/덮어쓰기 방지
   - 행 락(`SELECT ... FOR UPDATE`) 및 데드락 방지를 위한 결정적 정렬 순서(`ORDER BY store_staff_id ASC, payroll_id ASC`)
   - 로컬 Qwen 27B가 적발했던 TOCTOU 레이스 컨디션을 방어하기 위해 SQL UPDATE 레벨에 `NOT EXISTS (SELECT 1 FROM attendance ... WHERE status IN ('WORKING', 'ON_BREAK', 'MOD_REQUESTED'))` 2중 서브쿼리 가드 주입
3. **일본 노동기준법 제37조 및 법정 단수처리 (행정통달 기발 제150호)**:
   - 심야(22:00~05:00) 25% 가산, 법정외 연장(1일 8시간 초과) 25% 가산, 중복 시 50% 가산
   - 월간 30분 단위 절사/절상 및 50전 단위 사사오입(HALF_UP) 반영

---

## 🤖 로컬 감사관(Qwen 3.8 27B)의 1차 적발 및 2차 조치 내역

| Finding ID | 점검 항목 | 심각도 | 1차 판정 | 조치 및 해결 내역 (Antigravity) | 2차 재검수 판정 |
|---|---|---|:---:|---|:---:|
| **F-01** | 데드락 방지 | P1 | WARN | `ORDER BY store_staff_id ASC, payroll_id ASC FOR UPDATE`로 명시적 락 순서 고정 | **PASS** |
| **F-02** | TOCTOU 레이스 컨디션 | P0 | **FAIL** | `updateAllPayrollStatusConfirm`에 `NOT EXISTS (SELECT 1 FROM attendance ...)` 서브쿼리 가드 주입하여 DB 레벨 2중 방어선 구축 | **PASS** |
| **F-07** | 음수 시간 경계값 예외 | P2 | WARN | `PayrollService.java`에 `netWorkMinutes < 0` 등 음수 시간 유입 시 `IllegalStateException` 차단 가드 추가 | **PASS** |
| **F-09** | 법정 단수처리 (기발 150호) | P1 | PASS | 30분 단위 월간 시간 올림/버림 및 HALF_UP 계산 검증 완료 | **PASS** |
| **F-10** | IDOR 취약점 방어 | P1 | PASS | 점포/급여 데이터 소유권 검증(`StoreAuthorizationService`) 확인 | **PASS** |
| **F-11** | 트랜잭션 격리 | P1 | PASS | `@Transactional` 및 행 단위 배타 락 확인 | **PASS** |

---

## 📂 전체 소스 코드 번들 (E2E Full-Stack Layers)

### 1. Database Schema DDL (`schema.sql` 발췌)
```sql
-- 7. 月次給与台帳 (Payroll)
CREATE TABLE IF NOT EXISTS payroll (
    payroll_id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '給与台帳ID',
    store_id BIGINT NOT NULL COMMENT '店舗ID',
    store_staff_id BIGINT NOT NULL COMMENT '店舗スタッフID',
    target_year_month CHAR(7) NOT NULL COMMENT '対象年月 (YYYY-MM)',
    range_start_date DATE NOT NULL COMMENT '集計期間開始日',
    range_end_date DATE NOT NULL COMMENT '集計期間終了日',
    work_days_count INT NOT NULL DEFAULT 0 COMMENT '出勤日数',
    total_work_minutes INT NOT NULL DEFAULT 0 COMMENT '実労働時間合計 (分)',
    total_night_minutes INT NOT NULL DEFAULT 0 COMMENT '深夜労働時間合計 (分)',
    total_overtime_minutes INT NOT NULL DEFAULT 0 COMMENT '時間外残業時間合計 (分)',
    applied_hourly_wage INT NOT NULL COMMENT '適用時給スナップショット (円)',
    applied_transport_limit INT NOT NULL DEFAULT 15000 COMMENT '適用月間交通費上限額スナップショット (円)',
    base_pay INT NOT NULL DEFAULT 0 COMMENT '基本給 (円)',
    night_allowance INT NOT NULL DEFAULT 0 COMMENT '深夜割増手当 (円)',
    overtime_allowance INT NOT NULL DEFAULT 0 COMMENT '時間外割増手当 (円)',
    transport_allowance INT NOT NULL DEFAULT 0 COMMENT '交通費支給額 (円)',
    gross_pay INT NOT NULL DEFAULT 0 COMMENT '総支給額 (円)',
    payroll_status VARCHAR(20) NOT NULL DEFAULT 'DRAFT' COMMENT '状態 (DRAFT, CONFIRMED, PAID)',
    confirmed_at DATETIME NULL COMMENT '確定日時',
    paid_at DATETIME NULL COMMENT '支給完了日時',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '作成日時',
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新日時',
    CONSTRAINT uq_payroll_staff_month UNIQUE (store_staff_id, target_year_month),
    CONSTRAINT fk_payroll_store FOREIGN KEY (store_id) REFERENCES store (store_id),
    CONSTRAINT fk_payroll_staff FOREIGN KEY (store_staff_id) REFERENCES store_staff (store_staff_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
```

### 2. MyBatis Mapper XML (`PayrollMapper.xml`)
``xml
<?xml version="1.0" encoding="UTF-8" ?>
<!DOCTYPE mapper PUBLIC "-//mybatis.org//DTD Mapper 3.0//EN"
        "http://mybatis.org/dtd/mybatis-3-mapper.dtd">

<mapper namespace="com.example.kintai.domain.payroll.mapper.PayrollMapper">

    <select id="findPayrollsByStoreAndMonth" resultType="com.example.kintai.domain.payroll.dto.PayrollDto">
        SELECT
            p.payroll_id,
            p.store_id,
            p.store_staff_id,
            m.name AS staff_name,
            p.target_year_month,
            p.range_start_date,
            p.range_end_date,
            p.work_days_count,
            p.total_work_minutes,
            p.total_night_minutes,
            p.total_overtime_minutes,
            p.applied_hourly_wage,
            p.applied_transport_limit,
            p.base_pay,
            p.night_allowance,
            p.overtime_allowance,
            p.transport_allowance,
            p.gross_pay,
            p.payroll_status,
            p.confirmed_at,
            p.paid_at
        FROM payroll p
        JOIN store_staff ss ON p.store_staff_id = ss.store_staff_id
        JOIN member m ON ss.member_id = m.member_id
        WHERE p.store_id = #{storeId}
          AND p.target_year_month = #{targetYearMonth}
        ORDER BY p.store_staff_id ASC
    </select>

    <select id="findById" resultType="com.example.kintai.domain.payroll.dto.PayrollDto">
        SELECT
            p.payroll_id,
            p.store_id,
            p.store_staff_id,
            m.name AS staff_name,
            p.target_year_month,
            p.range_start_date,
            p.range_end_date,
            p.work_days_count,
            p.total_work_minutes,
            p.total_night_minutes,
            p.total_overtime_minutes,
            p.applied_hourly_wage,
            p.applied_transport_limit,
            p.base_pay,
            p.night_allowance,
            p.overtime_allowance,
            p.transport_allowance,
            p.gross_pay,
            p.payroll_status,
            p.confirmed_at,
            p.paid_at
        FROM payroll p
        JOIN store_staff ss ON p.store_staff_id = ss.store_staff_id
        JOIN member m ON ss.member_id = m.member_id
        WHERE p.payroll_id = #{payrollId}
    </select>

    <select id="findByIdForUpdate" resultType="com.example.kintai.domain.payroll.dto.PayrollDto">
        SELECT
            p.payroll_id,
            p.store_id,
            p.store_staff_id,
            m.name AS staff_name,
            p.target_year_month,
            p.range_start_date,
            p.range_end_date,
            p.work_days_count,
            p.total_work_minutes,
            p.total_night_minutes,
            p.total_overtime_minutes,
            p.applied_hourly_wage,
            p.applied_transport_limit,
            p.base_pay,
            p.night_allowance,
            p.overtime_allowance,
            p.transport_allowance,
            p.gross_pay,
            p.payroll_status,
            p.confirmed_at,
            p.paid_at
        FROM payroll p
        JOIN store_staff ss ON p.store_staff_id = ss.store_staff_id
        JOIN member m ON ss.member_id = m.member_id
        WHERE p.payroll_id = #{payrollId}
        FOR UPDATE
    </select>

    <select id="findByStaffAndMonth" resultType="com.example.kintai.domain.payroll.dto.PayrollDto">
        SELECT
            p.payroll_id,
            p.store_id,
            p.store_staff_id,
            m.name AS staff_name,
            p.target_year_month,
            p.range_start_date,
            p.range_end_date,
            p.work_days_count,
            p.total_work_minutes,
            p.total_night_minutes,
            p.total_overtime_minutes,
            p.applied_hourly_wage,
            p.applied_transport_limit,
            p.base_pay,
            p.night_allowance,
            p.overtime_allowance,
            p.transport_allowance,
            p.gross_pay,
            p.payroll_status,
            p.confirmed_at,
            p.paid_at
        FROM payroll p
        JOIN store_staff ss ON p.store_staff_id = ss.store_staff_id
        JOIN member m ON ss.member_id = m.member_id
        WHERE p.store_staff_id = #{storeStaffId}
          AND p.target_year_month = #{targetYearMonth}
        LIMIT 1
    </select>

    <select id="findByStaffAndMonthForUpdate" resultType="com.example.kintai.domain.payroll.dto.PayrollDto">
        SELECT
            p.payroll_id,
            p.store_id,
            p.store_staff_id,
            m.name AS staff_name,
            p.target_year_month,
            p.range_start_date,
            p.range_end_date,
            p.work_days_count,
            p.total_work_minutes,
            p.total_night_minutes,
            p.total_overtime_minutes,
            p.applied_hourly_wage,
            p.applied_transport_limit,
            p.base_pay,
            p.night_allowance,
            p.overtime_allowance,
            p.transport_allowance,
            p.gross_pay,
            p.payroll_status,
            p.confirmed_at,
            p.paid_at
        FROM payroll p
        JOIN store_staff ss ON p.store_staff_id = ss.store_staff_id
        JOIN member m ON ss.member_id = m.member_id
        WHERE p.store_staff_id = #{storeStaffId}
          AND p.target_year_month = #{targetYearMonth}
        LIMIT 1
        FOR UPDATE
    </select>

    <insert id="upsertPayroll" useGeneratedKeys="true" keyProperty="payrollId">
        INSERT INTO payroll (
            store_id,
            store_staff_id,
            target_year_month,
            range_start_date,
            range_end_date,
            work_days_count,
            total_work_minutes,
            total_night_minutes,
            total_overtime_minutes,
            applied_hourly_wage,
            applied_transport_limit,
            base_pay,
            night_allowance,
            overtime_allowance,
            transport_allowance,
            gross_pay,
            payroll_status
        ) VALUES (
            #{storeId},
            #{storeStaffId},
            #{targetYearMonth},
            #{rangeStartDate},
            #{rangeEndDate},
            #{workDaysCount},
            #{totalWorkMinutes},
            #{totalNightMinutes},
            #{totalOvertimeMinutes},
            #{appliedHourlyWage},
            #{appliedTransportLimit},
            #{basePay},
            #{nightAllowance},
            #{overtimeAllowance},
            #{transportAllowance},
            #{grossPay},
            #{payrollStatus}
        )
        ON DUPLICATE KEY UPDATE
            work_days_count = VALUES(work_days_count),
            total_work_minutes = VALUES(total_work_minutes),
            total_night_minutes = VALUES(total_night_minutes),
            total_overtime_minutes = VALUES(total_overtime_minutes),
            applied_hourly_wage = VALUES(applied_hourly_wage),
            applied_transport_limit = VALUES(applied_transport_limit),
            base_pay = VALUES(base_pay),
            night_allowance = VALUES(night_allowance),
            overtime_allowance = VALUES(overtime_allowance),
            transport_allowance = VALUES(transport_allowance),
            gross_pay = VALUES(gross_pay),
            updated_at = CURRENT_TIMESTAMP
    </insert>

    <insert id="insertPayrollDailyDetail" useGeneratedKeys="true" keyProperty="payrollDailyDetailId">
        INSERT INTO payroll_daily_detail (
            payroll_id,
            attendance_id,
            work_date,
            applied_hourly_wage,
            total_break_minutes,
            net_work_minutes,
            night_work_minutes,
            overtime_minutes,
            base_pay,
            night_allowance,
            overtime_allowance,
            transport_amount
        ) VALUES (
            #{payrollId},
            #{attendanceId},
            #{workDate},
            #{appliedHourlyWage},
            #{totalBreakMinutes},
            #{netWorkMinutes},
            #{nightWorkMinutes},
            #{overtimeMinutes},
            #{basePay},
            #{nightAllowance},
            #{overtimeAllowance},
            #{transportAmount}
        )
    </insert>

    <select id="findDailyDetailsByPayrollId" resultType="com.example.kintai.domain.payroll.dto.PayrollDailyDetailDto">
        SELECT
            payroll_daily_detail_id,
            payroll_id,
            attendance_id,
            work_date,
            applied_hourly_wage,
            total_break_minutes,
            net_work_minutes,
            night_work_minutes,
            overtime_minutes,
            base_pay,
            night_allowance,
            overtime_allowance,
            transport_amount
        FROM payroll_daily_detail
        WHERE payroll_id = #{payrollId}
        ORDER BY work_date ASC
    </select>

    <delete id="deleteDailyDetailsByPayrollId">
        DELETE FROM payroll_daily_detail
        WHERE payroll_id = #{payrollId}
    </delete>

    <update id="updatePayrollStatusConfirm">
        UPDATE payroll
        SET payroll_status = 'CONFIRMED',
            confirmed_at = #{confirmedAt},
            updated_at = CURRENT_TIMESTAMP
        WHERE payroll_id = #{payrollId}
          AND payroll_status = 'DRAFT'
    </update>

    <update id="updateAllPayrollStatusConfirm">
        UPDATE payroll p
        SET p.payroll_status = 'CONFIRMED',
            p.confirmed_at = #{confirmedAt},
            p.updated_at = CURRENT_TIMESTAMP
        WHERE p.store_id = #{storeId}
          AND p.target_year_month = #{targetYearMonth}
          AND p.payroll_status = 'DRAFT'
          AND NOT EXISTS (
              SELECT 1
              FROM attendance a
              WHERE a.store_staff_id = p.store_staff_id
                AND a.work_date &gt;= p.range_start_date
                AND a.work_date &lt;= p.range_end_date
                AND a.attendance_status IN ('WORKING', 'ON_BREAK', 'MOD_REQUESTED')
          )
    </update>

    <select id="countPayrollsByStoreAndMonthAndStatus" resultType="int">
        SELECT COUNT(*)
        FROM payroll
        WHERE store_id = #{storeId}
          AND target_year_month = #{targetYearMonth}
          AND payroll_status = #{payrollStatus}
    </select>

    <select id="countUnresolvedAttendance" resultType="int">
        SELECT COUNT(*)
        FROM attendance
        WHERE store_staff_id = #{storeStaffId}
          AND work_date &gt;= #{startDate}
          AND work_date &lt;= #{endDate}
          AND attendance_status IN ('WORKING', 'ON_BREAK', 'MOD_REQUESTED')
    </select>

    <select id="getPayCutoffDayByStoreId" resultType="java.lang.Integer">
        SELECT pay_cutoff_day
        FROM store
        WHERE store_id = #{storeId}
    </select>

    <select id="findHourlyWageForStaffOnDate" resultType="java.lang.Integer">
        SELECT hourly_wage
        FROM store_staff_contract_history
        WHERE store_staff_id = #{storeStaffId}
          AND effective_from &lt;= #{workDate}
          AND (effective_to IS NULL OR effective_to &gt; #{workDate})
        ORDER BY effective_from DESC
        LIMIT 1
    </select>

    <select id="findTransportLimitForStaffOnDate" resultType="java.lang.Integer">
        SELECT monthly_transport_limit
        FROM store_staff_contract_history
        WHERE store_staff_id = #{storeStaffId}
          AND effective_from &lt;= #{date}
          AND (effective_to IS NULL OR effective_to &gt; #{date})
        ORDER BY effective_from DESC
        LIMIT 1
    </select>

    <select id="findDailyTransportFeeForStaffOnDate" resultType="java.lang.Integer">
        SELECT daily_transport_fee
        FROM store_staff_contract_history
        WHERE store_staff_id = #{storeStaffId}
          AND effective_from &lt;= #{date}
          AND (effective_to IS NULL OR effective_to &gt; #{date})
        ORDER BY effective_from DESC
        LIMIT 1
    </select>

    <select id="lockStoreStaffForUpdate">
        SELECT store_staff_id
        FROM store_staff
        WHERE store_staff_id = #{storeStaffId}
        FOR UPDATE
    </select>

    <select id="lockStorePayrollsForUpdate" resultType="com.example.kintai.domain.payroll.dto.PayrollDto">
        SELECT
            payroll_id,
            store_id,
            store_staff_id,
            target_year_month,
            range_start_date,
            range_end_date,
            work_days_count,
            total_work_minutes,
            total_night_minutes,
            total_overtime_minutes,
            applied_hourly_wage,
            applied_transport_limit,
            base_pay,
            night_allowance,
            overtime_allowance,
            transport_allowance,
            gross_pay,
            payroll_status,
            confirmed_at,
            paid_at
        FROM payroll
        WHERE store_id = #{storeId}
          AND target_year_month = #{targetYearMonth}
        ORDER BY store_staff_id ASC, payroll_id ASC
        FOR UPDATE
    </select>

    <select id="findActiveStaffIdsByStoreId" resultType="java.lang.Long">
        SELECT store_staff_id
        FROM store_staff
        WHERE store_id = #{storeId}
          AND employment_status = 'ACTIVE'
    </select>

</mapper>

``

### 3. MyBatis Mapper Interface (`PayrollMapper.java`)
``java
package com.example.kintai.domain.payroll.mapper;

import com.example.kintai.domain.payroll.dto.PayrollDailyDetailDto;
import com.example.kintai.domain.payroll.dto.PayrollDto;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface PayrollMapper {

    List<PayrollDto> findPayrollsByStoreAndMonth(
            @Param("storeId") Long storeId,
            @Param("targetYearMonth") String targetYearMonth
    );

    PayrollDto findById(@Param("payrollId") Long payrollId);

    // ?믢퍟?뜹쐴: ?섆?永╊툗閻뷴츣?귙겗?뚧셽?닸뼭?꿩??ⓩ궟誤녕쉪??긿??(SELECT ... FOR UPDATE)
    PayrollDto findByIdForUpdate(@Param("payrollId") Long payrollId);

    PayrollDto findByStaffAndMonth(
            @Param("storeStaffId") Long storeStaffId,
            @Param("targetYearMonth") String targetYearMonth
    );

    // ?믢퍟?뜹쐴: 永╊툗?띹쮫嶸쀣꺕?닸뼭?귙겗?с궠?쇈깋塋뜹릦?꿩??ⓩ궟誤녕쉪??긿??(SELECT ... FOR UPDATE)
    PayrollDto findByStaffAndMonthForUpdate(
            @Param("storeStaffId") Long storeStaffId,
            @Param("targetYearMonth") String targetYearMonth
    );

    int upsertPayroll(PayrollDto payroll);

    int insertPayrollDailyDetail(PayrollDailyDetailDto detail);

    List<PayrollDailyDetailDto> findDailyDetailsByPayrollId(@Param("payrollId") Long payrollId);

    int deleteDailyDetailsByPayrollId(@Param("payrollId") Long payrollId);

    int updatePayrollStatusConfirm(
            @Param("payrollId") Long payrollId,
            @Param("confirmedAt") LocalDateTime confirmedAt
    );

    // 佯쀨닓?ⓧ퐪??쐢轝←덱訝롣??х▶若?(DRAFT -> CONFIRMED)
    int updateAllPayrollStatusConfirm(
            @Param("storeId") Long storeId,
            @Param("targetYearMonth") String targetYearMonth,
            @Param("confirmedAt") LocalDateTime confirmedAt
    );

    int countPayrollsByStoreAndMonthAndStatus(
            @Param("storeId") Long storeId,
            @Param("targetYearMonth") String targetYearMonth,
            @Param("payrollStatus") String payrollStatus
    );

    int countUnresolvedAttendance(
            @Param("storeStaffId") Long storeStaffId,
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate
    );

    Integer getPayCutoffDayByStoreId(@Param("storeId") Long storeId);

    Integer findHourlyWageForStaffOnDate(
            @Param("storeStaffId") Long storeStaffId,
            @Param("workDate") LocalDate workDate
    );

    Integer findTransportLimitForStaffOnDate(
            @Param("storeStaffId") Long storeStaffId,
            @Param("date") LocalDate date
    );

    Integer findDailyTransportFeeForStaffOnDate(
            @Param("storeStaffId") Long storeStaffId,
            @Param("date") LocalDate date
    );

    // ?믢퍟?뜹쐴: ?뚧셽?닸뼭?꿩???걼?곥겗?꿱┳?꾠꺆?껁궚 (SELECT ... FOR UPDATE)
    void lockStoreStaffForUpdate(@Param("storeStaffId") Long storeStaffId);

    // 佯쀨닓?ⓧ퐪??쐢轝←덱訝롣??с꺆?껁궚 (SELECT ... FOR UPDATE)
    List<PayrollDto> lockStorePayrollsForUpdate(
            @Param("storeId") Long storeId,
            @Param("targetYearMonth") String targetYearMonth
    );

    List<Long> findActiveStaffIdsByStoreId(@Param("storeId") Long storeId);
}

``

### 4. Core Business Service (`PayrollService.java`)
``java
package com.example.kintai.domain.payroll.service;

import com.example.kintai.domain.attendance.dto.AttendanceDto;
import com.example.kintai.domain.attendance.mapper.AttendanceMapper;
import com.example.kintai.domain.payroll.dto.*;
import com.example.kintai.domain.payroll.mapper.PayrollMapper;
import com.example.kintai.domain.payroll.util.PayrollCalculator;
import com.example.kintai.domain.payroll.util.PayrollExcelExporter;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Service
@Transactional(readOnly = true)
public class PayrollService {

    private final PayrollMapper payrollMapper;
    private final AttendanceMapper attendanceMapper;
    private final com.example.kintai.domain.store.service.StoreAuthorizationService storeAuthorizationService;

    public PayrollService(PayrollMapper payrollMapper, AttendanceMapper attendanceMapper, com.example.kintai.domain.store.service.StoreAuthorizationService storeAuthorizationService) {
        this.payrollMapper = payrollMapper;
        this.attendanceMapper = attendanceMapper;
        this.storeAuthorizationService = storeAuthorizationService;
    }

    public List<PayrollDto> getPayrollsByStoreAndMonth(Long storeId, String targetYearMonth) {
        return payrollMapper.findPayrollsByStoreAndMonth(storeId, targetYearMonth);
    }

    /**
     * ?덂닪永╊툗????ч썓鼇덀걡?덀겞鼇덄츞 (Upsert)
     */
    @Transactional
    public void calculateMonthlyPayroll(Long storeId, String targetYearMonth) {
        Integer cutoffDay = payrollMapper.getPayCutoffDayByStoreId(storeId);
        int payCutoffDay = (cutoffDay != null) ? cutoffDay : 99;

        PayrollCalculator.DateRange range = PayrollCalculator.resolveDateRange(targetYearMonth, payCutoffDay);
        LocalDate startDate = range.startDate();
        LocalDate endDate = range.endDate();

        List<Long> staffIds = payrollMapper.findActiveStaffIdsByStoreId(storeId);

        for (Long staffId : staffIds) {
            calculatePayrollForStaff(storeId, staffId, targetYearMonth, startDate, endDate);
        }
    }

    @Transactional
    public PayrollDto calculatePayrollForStaff(Long storeId, Long staffId, String targetYearMonth, LocalDate startDate, LocalDate endDate) {
        // ?믢퍟?뜹쐴: ?뚧셽鼇덄츞塋뜹릦?믧삻閭㏂걲?뗦궟誤녕쉪??긿??(?밤궭?껁깢?섆퐤)
        payrollMapper.lockStoreStaffForUpdate(staffId);

        // 閻뷴츣歷덀겳永╊툗??툖鸚됪㏛퓷鈺?(CONFIRMED / PAID ??졃?덀겘?띹쮫嶸쀣꺕訝딀쎑?띲굮??뼪, 烏뚣꺆?껁궚餓섅걤?㏛폏)
        PayrollDto existingPayroll = payrollMapper.findByStaffAndMonthForUpdate(staffId, targetYearMonth);
        if (existingPayroll != null && !"DRAFT".equals(existingPayroll.getPayrollStatus())) {
            throw new IllegalStateException("?뉐츣亮닸쐢 (" + targetYearMonth + ") ??덱訝롢겘?㏂겓閻뷴츣歷덀겳(" +
                    existingPayroll.getPayrollStatus() + ") ??걼?곥곩냽鼇덄츞?㎯걤?얇걵?볝?永╊툗訝띶쨯?㏛퓷鈺?");
        }

        // ?ゅ눇?녶떎?졼겗閻븃첀竊덃돀?삯붶릎?꾣쑋?욤첀?녘쳦?뚣걗?뗥졃?덀겘鼇덄츞訝?뼪竊?
        int unresolvedCount = payrollMapper.countUnresolvedAttendance(staffId, startDate, endDate);
        if (unresolvedCount > 0) {
            throw new IllegalStateException("?ゆ돽沃띲겲?잆겘?볟댗?붶릎??떎?졼걣耶섇쑉?쇻굥?잆굙?곭덱訝롨쮫嶸쀣굮訝?뼪?쀣겲?쀣걼???룟뱻ID: " + staffId + ")");
        }

        List<AttendanceDto> attendances = attendanceMapper.findCompletedAttendancesForStaffAndPeriod(staffId, startDate, endDate);

        // ?싧떎?뗥퐪訝딃솏窈랃펷1?욁겗暎양츞?귙걼?듽겗訝딃솏竊됥겗?뽩풓
        Integer transportLimitVal = payrollMapper.findTransportLimitForStaffOnDate(staffId, endDate);
        int transportLimit = (transportLimitVal != null) ? transportLimitVal : 15000;

        int totalWorkMinutes = 0;
        int totalNightMinutes = 0;
        int totalOvertimeMinutes = 0;
        int totalBasePay = 0;
        int totalNightAllowance = 0;
        int totalOvertimeAllowance = 0;
        int totalTransportAllowance = 0;

        Set<Integer> uniqueWages = new HashSet<>();
        Set<LocalDate> workedDates = new HashSet<>();

        List<PayrollDailyDetailDto> detailsToInsert = new ArrayList<>();

        for (AttendanceDto att : attendances) {
            // ?ㅶ졼깈?쇈궭??쿋?ㅳ꺕?겼만?ㅳ깘?ゃ깈?쇈궥?㎯꺍 (F-07 ?끿돚?㏛퓷鼇?
            if (att.getNetWorkMinutes() < 0 || att.getNightWorkMinutes() < 0 || att.getOvertimeMinutes() < 0 || att.getTotalBreakMinutes() < 0) {
                throw new IllegalStateException("?ㅵ떃?귡뼋?얇걼??폂?⒵셽?볝겓縕졼겗?ㅳ걣?ャ겲?뚣겍?꾠굥?잆굙?곭덱訝롨쮫嶸쀣굮訝?뼪?쀣겲?쀣걼???ㅶ쟅D: " + att.getAttendanceId() + ")");
            }

            LocalDate workDate = att.getWorkDate();
            Integer hourlyWageVal = payrollMapper.findHourlyWageForStaffOnDate(staffId, workDate);
            if (hourlyWageVal == null || hourlyWageVal <= 0) {
                throw new IllegalStateException("?ㅵ떃??(" + workDate + ") ?ユ쐣?밤겒也묊큵?귞덱?뉎꺖?욍걣耶섇쑉?쀣겲?쎼굯??);
            }
            int hourlyWage = hourlyWageVal;
            uniqueWages.add(hourlyWage);

            // 2026亮닸씝雅ч꺗?鵝롨퀍??(1,280?? ?꿔굦?믧삻閭?
            if (hourlyWage < 1280) {
                throw new IllegalStateException("也묊큵?귞덱 (" + hourlyWage + "?? ?뚧퀡若싨?鵝롨퀍??(1,280?? ?믢툔?욁겂?╉걚?얇걲??);
            }

            // ?ε닪鼇덄츞竊덄ク?겹겘 HALF_UP ?㎩렢野녴겓訝멥굙竊?
            PayrollCalculator.DailyPayResult dailyPay = PayrollCalculator.calculateDailyPay(
                    hourlyWage,
                    att.getNetWorkMinutes(),
                    att.getNightWorkMinutes(),
                    att.getOvertimeMinutes()
            );

            // 1???욇싧떎?뗥퐪??덱?듽굠?녔쐢窈띴툓?먩옞?끹겎??뀓??
            int transportAmount = 0;
            if (!workedDates.contains(workDate)) {
                workedDates.add(workDate);
                Integer dailyTransportVal = payrollMapper.findDailyTransportFeeForStaffOnDate(staffId, workDate);
                int dailyTransport = (dailyTransportVal != null) ? dailyTransportVal : 0;
                int remainingLimit = Math.max(0, transportLimit - totalTransportAllowance);
                transportAmount = Math.min(dailyTransport, remainingLimit);
                totalTransportAllowance += transportAmount;
            }

            totalWorkMinutes += att.getNetWorkMinutes();
            totalNightMinutes += att.getNightWorkMinutes();
            totalOvertimeMinutes += att.getOvertimeMinutes();
            totalBasePay += dailyPay.basePay();
            totalNightAllowance += dailyPay.nightAllowance();
            totalOvertimeAllowance += dailyPay.overtimeAllowance();

            PayrollDailyDetailDto detail = new PayrollDailyDetailDto();
            detail.setAttendanceId(att.getAttendanceId());
            detail.setWorkDate(workDate);
            detail.setAppliedHourlyWage(hourlyWage);
            detail.setTotalBreakMinutes(att.getTotalBreakMinutes());
            detail.setNetWorkMinutes(att.getNetWorkMinutes());
            detail.setNightWorkMinutes(att.getNightWorkMinutes());
            detail.setOvertimeMinutes(att.getOvertimeMinutes());
            detail.setBasePay(dailyPay.basePay());
            detail.setNightAllowance(dailyPay.nightAllowance());
            detail.setOvertimeAllowance(dailyPay.overtimeAllowance());
            detail.setTransportAmount(transportAmount);

            detailsToInsert.add(detail);
        }

        int grossPay = totalBasePay + totalNightAllowance + totalOvertimeAllowance + totalTransportAllowance;
        Integer appliedHourlyWage = (uniqueWages.size() == 1) ? uniqueWages.iterator().next() : null;

        PayrollDto payroll = new PayrollDto();
        payroll.setStoreId(storeId);
        payroll.setStoreStaffId(staffId);
        payroll.setTargetYearMonth(targetYearMonth);
        payroll.setRangeStartDate(startDate);
        payroll.setRangeEndDate(endDate);
        payroll.setWorkDaysCount(workedDates.size());
        payroll.setTotalWorkMinutes(totalWorkMinutes);
        payroll.setTotalNightMinutes(totalNightMinutes);
        payroll.setTotalOvertimeMinutes(totalOvertimeMinutes);
        payroll.setAppliedHourlyWage(appliedHourlyWage);
        payroll.setAppliedTransportLimit(transportLimit);
        payroll.setBasePay(totalBasePay);
        payroll.setNightAllowance(totalNightAllowance);
        payroll.setOvertimeAllowance(totalOvertimeAllowance);
        payroll.setTransportAllowance(totalTransportAllowance);
        payroll.setGrossPay(grossPay);
        payroll.setPayrollStatus("DRAFT");

        // ?먬싮걫 ??뭽62亮?63亮? ?븀쇇寧?50??力뺝츣?덆뼋塋?빊??릤??겑?ⓦ?
        // ?섆?也묊큵?귞덱??졃?덀겘?곫쐢?볟돯罌쀦뎸壤볡톽窈띲겗力뺝츣塋?빊??릤竊?0?녶쥊?뚥만?곻펻?뗥퐪??0??썪?ⓧ틪?ο펹?믣룏??
        if (appliedHourlyWage != null) {
            StatutoryPayrollResult statutoryResult = applyStatutoryMonthlyRounding(payroll);
            // 力뺝츣塋?빊??릤永먩옖?믤뎸壤볣죲?듽굠?녕톽??덱窈띲겓?띷삝
            payroll.setOvertimeAllowance(statutoryResult.statutoryOvertimeAllowance());
            payroll.setNightAllowance(statutoryResult.statutoryNightAllowance());
            payroll.setGrossPay(payroll.getBasePay() + payroll.getNightAllowance() + payroll.getOvertimeAllowance() + payroll.getTransportAllowance());
        }

        payrollMapper.upsertPayroll(payroll);

        // ?ε닪屋녕눗?밤깏?껁깤?룔깾?껁깉??냽岳앭춼
        payrollMapper.deleteDailyDetailsByPayrollId(payroll.getPayrollId());
        for (PayrollDailyDetailDto detail : detailsToInsert) {
            detail.setPayrollId(payroll.getPayrollId());
            payrollMapper.insertPayrollDailyDetail(detail);
        }

        return payroll;
    }

    /**
     * ?먫죱?욥싮걫 ??뭽62亮???4?δ퍡 ?븀쇇寧?50??繹뽪떊??
     * ?덆뼋3鸚㎫ク?겼눇?놅펷?귡뼋鸚뽧꺕曆긷쩂?삡폂?γ겗30?녶쥊?뚧쐢?볞만?곥걡?덀겞50??쥊?뚧뎸壤볣썓鼇덌펹?믧겑?ⓦ걮?잍퀡若싮썓鼇?
     */
    public StatutoryPayrollResult applyStatutoryMonthlyRounding(PayrollDto payroll) {
        return applyStatutoryMonthlyRounding(payroll, 0);
    }

    /**
     * ?먫죱?욥싮걫 ??뭽62亮???4?δ퍡 ?븀쇇寧?50??繹뽪떊??
     * ?덆뼋3鸚㎫ク?겼눇?놅펷?귡뼋鸚뽧꺕曆긷쩂?삡폂?γ겗30?녶쥊?뚧쐢?볞만?곥걡?덀겞50??쥊?뚧뎸壤볣썓鼇덀곦폂?ε뒾?띷셽?볣ｅ땿?덌펹
     */
    public StatutoryPayrollResult applyStatutoryMonthlyRounding(PayrollDto payroll, int totalHolidayMinutes) {
        if (payroll == null) {
            throw new IllegalArgumentException("永╊툗?뉎꺖?욍겘恙낂젅?㎯걲??);
        }

        // ???덆뼋?닷깓?귡뼋??ク?겼눇?놅펷?귡뼋鸚뽧꺕曆긷쩂?삡폂?γ걹?뚣걻??0?녷쑋繹?뉎굤?ⓦ겍??0?녵빳訝??귡뼋?ュ늾?듾툓?믭펹
        int roundedOvertimeHours = PayrollCalculator.StatutoryRounding.roundMonthlyHours(payroll.getTotalOvertimeMinutes());
        int roundedNightHours = PayrollCalculator.StatutoryRounding.roundMonthlyHours(payroll.getTotalNightMinutes());
        int roundedHolidayHours = PayrollCalculator.StatutoryRounding.roundMonthlyHours(Math.max(0, totalHolidayMinutes));

        int hourlyWage = (payroll.getAppliedHourlyWage() != null) ? payroll.getAppliedHourlyWage() : 1280;

        // ??1?귡뼋?귙걼?듽겗?꿨쥥蘊껈뇫窈랃펷50??쑋繹?뉎굤?ⓦ겍??0??빳訝??녶늾?듾툓?믭펹
        // ?귡뼋鸚? 25%?좂츞
        int overtimeHourlyPremium = PayrollCalculator.StatutoryRounding.roundHourlyRate(
                java.math.BigDecimal.valueOf(hourlyWage).multiply(java.math.BigDecimal.valueOf(0.25))
        );
        // 曆긷쩂: 25%?좂츞
        int nightHourlyPremium = PayrollCalculator.StatutoryRounding.roundHourlyRate(
                java.math.BigDecimal.valueOf(hourlyWage).multiply(java.math.BigDecimal.valueOf(0.25))
        );
        // 鴉묉뿥: 35%?좂츞 (?닷읃力뺟К37?←К1??
        int holidayHourlyPremium = PayrollCalculator.StatutoryRounding.roundHourlyRate(
                java.math.BigDecimal.valueOf(hourlyWage).multiply(java.math.BigDecimal.valueOf(0.35))
        );

        // ???덆뼋?꿨쥥?뗥퐪渶뤻죲??ク?겼눇?놅펷?끿쎅?η떖塋뗩썓鼇덀꺕50??쑋繹?뉎굤?ⓦ겍??0??빳訝??녶늾?듾툓?믭펹
        int roundedOvertimeAllowance = PayrollCalculator.StatutoryRounding.roundMonthlyAllowanceTotal(
                java.math.BigDecimal.valueOf((long) overtimeHourlyPremium * roundedOvertimeHours)
        );
        int roundedNightAllowance = PayrollCalculator.StatutoryRounding.roundMonthlyAllowanceTotal(
                java.math.BigDecimal.valueOf((long) nightHourlyPremium * roundedNightHours)
        );
        int roundedHolidayAllowance = PayrollCalculator.StatutoryRounding.roundMonthlyAllowanceTotal(
                java.math.BigDecimal.valueOf((long) holidayHourlyPremium * roundedHolidayHours)
        );

        return new StatutoryPayrollResult(
                roundedOvertimeHours,
                roundedNightHours,
                roundedHolidayHours,
                overtimeHourlyPremium,
                nightHourlyPremium,
                holidayHourlyPremium,
                roundedOvertimeAllowance,
                roundedNightAllowance,
                roundedHolidayAllowance
        );
    }

    public record StatutoryPayrollResult(
            int roundedOvertimeHours,
            int roundedNightHours,
            int roundedHolidayHours,
            int overtimeHourlyPremium,
            int nightHourlyPremium,
            int holidayHourlyPremium,
            int statutoryOvertimeAllowance,
            int statutoryNightAllowance,
            int statutoryHolidayAllowance
    ) {}

    /**
     * 永╊툗閻뷴츣??긿??눇?놅펷?섆??с궠?쇈깋??립怜요툖鸚됪㎯굮岳앲㉫?곫럲餓뽪궟誤녕쉪??긿??겑?⑨펹
     */
    @Transactional
    public void confirmPayroll(Long payrollId, PayrollConfirmRequestDto confirmDto) {
        // ?믢퍟?뜹쐴: 閻뷴츣野얕괌??덱訝롢꺃?녈꺖?됭눎鵝볝굮烏뚣꺆?껁궚 (SELECT ... FOR UPDATE)
        PayrollDto existing = payrollMapper.findByIdForUpdate(payrollId);
        if (existing == null) {
            throw new IllegalArgumentException("耶섇쑉?쀣겒?꾤덱訝롢깈?쇈궭?㎯걲??ID: " + payrollId + ")");
        }

        if (!"DRAFT".equals(existing.getPayrollStatus())) {
            throw new IllegalStateException("?㏂겓閻뷴츣歷덀겳??덱訝롢깈?쇈궭?㎯걲??);
        }

        // ?밤궭?껁깢??럲餓뽧꺆?껁궚
        payrollMapper.lockStoreStaffForUpdate(existing.getStoreStaffId());

        // 暎양츞野얕괌?잓뼋永귚틙??쩂鼇쇽펷渶졼굙?잓뼋永귚틙?띲겗?⒵쐿閻뷴츣?믧삻閭?펹
        if (!LocalDate.now().isAfter(existing.getRangeEndDate())) {
            throw new IllegalStateException("暎양츞野얕괌?잓뼋 (" + existing.getRangeEndDate() + ") ?뚨탞雅녴걲?뗥뎺?ョ덱訝롢굮閻뷴츣?쇻굥?볝겏??겎?띲겲?쎼굯??);
        }

        // ?ゅ눇?녶떎?졼겗閻븃첀
        int unresolved = payrollMapper.countUnresolvedAttendance(existing.getStoreStaffId(), existing.getRangeStartDate(), existing.getRangeEndDate());
        if (unresolved > 0) {
            throw new IllegalStateException("?ゅ눇?녴겗?ㅶ졽엶閭ｇ뵵獄뗣겲?잆겘?ㅵ떃訝?돀?삠걣耶섇쑉?쇻굥?잆굙?곭▶若싥겎?띲겲?쎼굯??);
        }

        // ??겹깈?쇈궭?ャ굠?뗥냽鼇덄츞
        PayrollDto recalculated = calculatePayrollForStaff(
                existing.getStoreId(),
                existing.getStoreStaffId(),
                existing.getTargetYearMonth(),
                existing.getRangeStartDate(),
                existing.getRangeEndDate()
        );

        // 佯쀩빓?뚨▶沃띲걮?잓뇫窈띲겏?띹쮫嶸쀩뇫窈띲겗訝띴??담긽?㎯긿??
        if (confirmDto != null && confirmDto.getExpectedGrossPay() != null) {
            int expected = confirmDto.getExpectedGrossPay();
            int actual = recalculated.getGrossPay();
            if (expected != actual) {
                throw new IllegalStateException("閻븃첀?귙겏?묌죲?뚥??담걮?얇걵?볝?閻븃첀窈? " + expected + "?? ?띹쮫嶸쀩죲: " + actual + "???귛냽閻븃첀?뚦퓚誤곥겎?쇻?);
            }
        }

        payrollMapper.updatePayrollStatusConfirm(payrollId, LocalDateTime.now());
    }

    /**
     * 佯쀨닓?ⓧ퐪??쐢轝←덱訝롣??х▶若싥깘?껁긽??릤竊덂릪?덂뀲?с궠?쇈깋?꿱┳?꾠꺆?껁궚?듽굠?념??х▶若싷펹
     *
     * @param storeId 野얕괌佯쀨닓ID
     * @param targetYearMonth 野얕괌亮닸쐢 (YYYY-MM)
     * @return 閻뷴츣?뺛굦?잏덱訝롣뻑??
     */
    @Transactional
    public int confirmMonthlyPayroll(Long storeId, String targetYearMonth) {
        // ?믢퍟?뜹쐴: 佯쀨닓??㈁壤볟뭅?덀겓?듽걨?뗥뀲永╊툗?с궠?쇈깋?믢??ф궟誤녕쉪??긿??(FOR UPDATE)
        List<PayrollDto> payrolls = payrollMapper.lockStorePayrollsForUpdate(storeId, targetYearMonth);
        if (payrolls == null || payrolls.isEmpty()) {
            throw new IllegalStateException("?뉐츣亮닸쐢 (" + targetYearMonth + ") ??덱訝롢깈?쇈궭?뚦춼?ⓦ걮?얇걵?볝귛뀍?ヤ??ц쮫嶸쀣굮若잒죱?쀣겍?뤵걽?뺛걚??);
        }

        List<PayrollDto> draftPayrolls = payrolls.stream()
                .filter(p -> "DRAFT".equals(p.getPayrollStatus()))
                .toList();

        if (draftPayrolls.isEmpty()) {
            throw new IllegalStateException("?뉐츣亮닸쐢 (" + targetYearMonth + ") ??덱訝롢겘?ⓧ뻑閻뷴츣歷덀겳?㎯걲??);
        }

        LocalDate today = LocalDate.now();

        // 渶졼굙?잓뼋永귚틙?듽굠?녔쑋??릤?ㅶ졼겗?ⓧ뻑?먦꺁?뉎꺖?룔깾??
        for (PayrollDto p : draftPayrolls) {
            if (!today.isAfter(p.getRangeEndDate())) {
                throw new IllegalStateException("暎양츞野얕괌?잓뼋 (" + p.getRangeEndDate() + ") ?뚨탞雅녴걲?뗥뎺?ユ쐢轝←덱訝롢굮訝?х▶若싥걲?뗣걪?ⓦ겘?㎯걤?얇걵?볝?野얕괌?밤궭?껁깢ID: " + p.getStoreStaffId() + ")");
            }

            int unresolved = payrollMapper.countUnresolvedAttendance(p.getStoreStaffId(), p.getRangeStartDate(), p.getRangeEndDate());
            if (unresolved > 0) {
                throw new IllegalStateException("?ゅ눇?녴겗?ㅶ졽엶閭ｇ뵵獄뗣겲?잆겘?볟댗?뚦춼?ⓦ걲?뗣걼?곥곫쐢轝←덱訝롢굮訝?х▶若싥겎?띲겲?쎼굯??野얕괌?밤궭?껁깢ID: " + p.getStoreStaffId() + ")");
            }
        }

        // 佯쀨닓?삣?穩▼뭅?덀겗訝?촀ONFIRMED?닸뼭 (CAS)
        int updatedCount = payrollMapper.updateAllPayrollStatusConfirm(storeId, targetYearMonth, LocalDateTime.now());
        return updatedCount;
    }

    /**
     * ?밤궭?껁깢?뗤볶?η덱訝롦삇榮계㈂榮겹깴?쇈??ョ뵪?뉎꺖?욕룚孃쀯펷沃띶룾濾쒑㉫餓섅걤竊?
     */
    public PayrollDetailResponseDto getPayrollDetail(Long payrollId, Long currentMemberId) {
        if (currentMemberId != null) {
            storeAuthorizationService.validatePayrollOwnership(payrollId, currentMemberId);
        }
        return getPayrollDetail(payrollId);
    }

    /**
     * ?밤궭?껁깢?뗤볶?η덱訝롦삇榮계㈂榮겹깴?쇈??ョ뵪?뉎꺖?욕룚孃?
     */
    public PayrollDetailResponseDto getPayrollDetail(Long payrollId) {
        PayrollDto payroll = payrollMapper.findById(payrollId);
        if (payroll == null) {
            throw new IllegalArgumentException("耶섇쑉?쀣겒?꾤덱訝롢깈?쇈궭?㎯걲??ID: " + payrollId + ")");
        }

        List<PayrollDailyDetailDto> details = payrollMapper.findDailyDetailsByPayrollId(payrollId);

        PayrollDetailResponseDto response = new PayrollDetailResponseDto();
        response.setPayrollId(payroll.getPayrollId());
        response.setStaffName(payroll.getStaffName());
        response.setTargetYearMonth(payroll.getTargetYearMonth());
        response.setAppliedHourlyWage(payroll.getAppliedHourlyWage());

        PayrollDetailResponseDto.PayrollSummary summary = new PayrollDetailResponseDto.PayrollSummary();
        summary.setWorkDaysCount(payroll.getWorkDaysCount());
        summary.setTotalWorkMinutes(payroll.getTotalWorkMinutes());
        summary.setTotalNightMinutes(payroll.getTotalNightMinutes());
        summary.setTotalOvertimeMinutes(payroll.getTotalOvertimeMinutes());
        summary.setBasePay(payroll.getBasePay());
        summary.setNightAllowance(payroll.getNightAllowance());
        summary.setOvertimeAllowance(payroll.getOvertimeAllowance());
        summary.setTransportAllowance(payroll.getTransportAllowance());
        summary.setGrossPay(payroll.getGrossPay());
        summary.setPayrollStatus(payroll.getPayrollStatus());
        response.setSummary(summary);

        DateTimeFormatter timeFormatter = DateTimeFormatter.ofPattern("HH:mm");
        List<PayrollDetailResponseDto.DailyAttendanceDetail> dailyList = new ArrayList<>();

        for (PayrollDailyDetailDto d : details) {
            PayrollDetailResponseDto.DailyAttendanceDetail daily = new PayrollDetailResponseDto.DailyAttendanceDetail();
            daily.setWorkDate(d.getWorkDate().toString());
            daily.setBreakMinutes(d.getTotalBreakMinutes());
            daily.setNetWorkMinutes(d.getNetWorkMinutes());
            daily.setNightMinutes(d.getNightWorkMinutes());
            daily.setOvertimeMinutes(d.getOvertimeMinutes());
            daily.setAppliedHourlyWage(d.getAppliedHourlyWage());
            daily.setBasePay(d.getBasePay());
            daily.setNightAllowance(d.getNightAllowance());
            daily.setOvertimeAllowance(d.getOvertimeAllowance());
            daily.setTransportAmount(d.getTransportAmount());

            // ?껃떎?졼깈?쇈궭?뗣굢?볟댗?귛댗?끻젿?믦짒若?
            AttendanceDto originalAtt = attendanceMapper.findById(d.getAttendanceId());
            if (originalAtt != null) {
                daily.setClockInAt(originalAtt.getClockInAt() != null ? originalAtt.getClockInAt().format(timeFormatter) : "-");
                daily.setClockOutAt(originalAtt.getClockOutAt() != null ? originalAtt.getClockOutAt().format(timeFormatter) : "-");
            } else {
                daily.setClockInAt("-");
                daily.setClockOutAt("-");
            }

            dailyList.add(daily);
        }

        response.setDailyAttendances(dailyList);
        return response;
    }

    /**
     * 永╊툗?겼립 Excel ?ⓦ궚?밤깮?쇈깉
     */
    public byte[] exportPayrollToExcel(Long storeId, String targetYearMonth) throws IOException {
        List<PayrollDto> payrolls = getPayrollsByStoreAndMonth(storeId, targetYearMonth);
        return PayrollExcelExporter.exportToExcel(targetYearMonth, payrolls);
    }
}

``

### 5. API Controller (`PayrollApiController.java`)
``java
package com.example.kintai.domain.payroll.controller;

import com.example.kintai.domain.payroll.dto.PayrollCalculateRequestDto;
import com.example.kintai.domain.payroll.dto.PayrollConfirmRequestDto;
import com.example.kintai.domain.payroll.dto.PayrollDetailResponseDto;
import com.example.kintai.domain.payroll.dto.PayrollDto;
import com.example.kintai.domain.payroll.service.PayrollService;
import com.example.kintai.domain.store.service.StoreAuthorizationService;
import com.example.kintai.global.common.ApiResponse;
import com.example.kintai.global.security.CustomUserDetails;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;

@RestController
@RequestMapping("/api/payroll")
@RequiredArgsConstructor
public class PayrollApiController {

    private final PayrollService payrollService;
    private final StoreAuthorizationService storeAuthorizationService;

    /**
     * ?덂닪永╊툗訝誤㎩룚孃?(?ゃ꺖?듽꺖佯쀨닓??됪Ł濾쒑㉫恙낂젅)
     */
    @GetMapping("/list")
    public ResponseEntity<ApiResponse<List<PayrollDto>>> getPayrollList(
            @RequestParam("storeId") Long storeId,
            @RequestParam("yearMonth") String yearMonth,
            @AuthenticationPrincipal CustomUserDetails userDetails
    ) {
        storeAuthorizationService.validateStoreOwnership(storeId, userDetails.getMemberId());
        List<PayrollDto> list = payrollService.getPayrollsByStoreAndMonth(storeId, yearMonth);
        return ResponseEntity.ok(ApiResponse.ok("永╊툗訝誤㎯굮?뽩풓?쀣겲?쀣걼??, list));
    }

    /**
     * ?덂닪永╊툗訝?ц쮫嶸?(Upsert, ?ゃ꺖?듽꺖佯쀨닓??됪Ł濾쒑㉫恙낂젅)
     */
    @PostMapping("/calculate")
    public ResponseEntity<ApiResponse<Void>> calculateMonthlyPayroll(
            @RequestBody PayrollCalculateRequestDto requestDto,
            @AuthenticationPrincipal CustomUserDetails userDetails
    ) {
        storeAuthorizationService.validateStoreOwnership(requestDto.getStoreId(), userDetails.getMemberId());
        payrollService.calculateMonthlyPayroll(requestDto.getStoreId(), requestDto.getTargetYearMonth());
        return ResponseEntity.ok(ApiResponse.ok("永╊툗鼇덄츞?뚦츑雅녴걮?얇걮?잆?, null));
    }

    /**
     * 佯쀨닓?ⓧ퐪??쐢轝←덱訝롣??х▶若?(?믢퍟?꿱┳?꾠꺆?껁궚?⑴뵪?삣립怜욍꺆?껁궚, ?ゃ꺖?듽꺖佯쀨닓??됪Ł濾쒑㉫恙낂젅)
     */
    @PostMapping("/confirm-month")
    public ResponseEntity<ApiResponse<Integer>> confirmMonthlyPayroll(
            @RequestBody PayrollCalculateRequestDto requestDto,
            @AuthenticationPrincipal CustomUserDetails userDetails
    ) {
        storeAuthorizationService.validateStoreOwnership(requestDto.getStoreId(), userDetails.getMemberId());
        int confirmedCount = payrollService.confirmMonthlyPayroll(requestDto.getStoreId(), requestDto.getTargetYearMonth());
        return ResponseEntity.ok(ApiResponse.ok("?덃А永╊툗????х▶若싥걣若뚥틙?쀣겲?쀣걼??閻뷴츣餓뜻빊: " + confirmedCount + "餓?", confirmedCount));
    }

    /**
     * 永╊툗閻뷴츣??릤 (躍녕걀??긿??꺕?믢퍟?뜹쐴?⑴뵪, 永╊툗?뉎꺖?욘??됪Ł濾쒑㉫恙낂젅)
     */
    @PostMapping("/{payrollId}/confirm")
    public ResponseEntity<ApiResponse<Void>> confirmPayroll(
            @PathVariable("payrollId") Long payrollId,
            @RequestBody(required = false) PayrollConfirmRequestDto confirmDto,
            @AuthenticationPrincipal CustomUserDetails userDetails
    ) {
        storeAuthorizationService.validatePayrollOwnership(payrollId, userDetails.getMemberId());
        payrollService.confirmPayroll(payrollId, confirmDto);
        return ResponseEntity.ok(ApiResponse.ok("永╊툗?뚨▶若싥걬?뚣겲?쀣걼??, null));
    }

    /**
     * ?밤궭?껁깢?뗤볶?η덱訝롦삇榮계㈂榮겼룚孃?(永╊툗?뉎꺖?욘??됪Ł濾쒑㉫恙낂젅)
     */
    @GetMapping("/{payrollId}/detail")
    public ResponseEntity<ApiResponse<PayrollDetailResponseDto>> getPayrollDetail(
            @PathVariable("payrollId") Long payrollId,
            @AuthenticationPrincipal CustomUserDetails userDetails
    ) {
        storeAuthorizationService.validatePayrollOwnership(payrollId, userDetails.getMemberId());
        PayrollDetailResponseDto detail = payrollService.getPayrollDetail(payrollId, userDetails.getMemberId());
        return ResponseEntity.ok(ApiResponse.ok("永╊툗?롧눗屋녕눗?믣룚孃쀣걮?얇걮?잆?, detail));
    }

    /**
     * 永╊툗?겼립 Excel ?ⓦ궚?밤깮?쇈깉 (?ゃ꺖?듽꺖佯쀨닓??됪Ł濾쒑㉫恙낂젅)
     */
    @GetMapping("/excel")
    public ResponseEntity<byte[]> downloadExcel(
            @RequestParam("storeId") Long storeId,
            @RequestParam("yearMonth") String yearMonth,
            @AuthenticationPrincipal CustomUserDetails userDetails
    ) throws IOException {
        storeAuthorizationService.validateStoreOwnership(storeId, userDetails.getMemberId());
        byte[] excelBytes = payrollService.exportPayrollToExcel(storeId, yearMonth);

        String fileName = URLEncoder.encode("永╊툗?겼립_" + yearMonth + ".xlsx", StandardCharsets.UTF_8).replace("+", "%20");

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + fileName + "\"; filename*=UTF-8''" + fileName)
                .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .body(excelBytes);
    }
}

``

### 6. Automated JUnit 5 Tests (`PayrollServiceTest.java`)
``java
package com.example.kintai.domain.payroll.service;

import com.example.kintai.domain.attendance.mapper.AttendanceMapper;
import com.example.kintai.domain.payroll.dto.PayrollDto;
import com.example.kintai.domain.payroll.mapper.PayrollMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("PayrollService 力뺝츣塋?빊??릤?ｆ맳?듽굠?녕덱訝롣툖鸚됪㏛퓷鈺룟뜕鵝볝깇?밤깉")
class PayrollServiceTest {

    @Mock
    private PayrollMapper payrollMapper;

    @Mock
    private AttendanceMapper attendanceMapper;

    @InjectMocks
    private PayrollService payrollService;

    @Test
    @DisplayName("閻뷴츣歷덀겳永╊툗(CONFIRMED/PAID)??냽鼇덄츞?츸llegalStateException?㏝겗??걬?뚥툖鸚됪㎯걣岳앲??뺛굦?뗣걪??)
    void calculatePayroll_WhenAlreadyConfirmed_ShouldThrowIllegalStateException() {
        Long storeId = 1L;
        Long staffId = 10L;
        String targetMonth = "2026-11";
        LocalDate start = LocalDate.of(2026, 11, 1);
        LocalDate end = LocalDate.of(2026, 11, 30);

        PayrollDto confirmedPayroll = new PayrollDto();
        confirmedPayroll.setPayrollId(100L);
        confirmedPayroll.setPayrollStatus("CONFIRMED");

        when(payrollMapper.findByStaffAndMonthForUpdate(staffId, targetMonth)).thenReturn(confirmedPayroll);

        IllegalStateException ex = assertThrows(IllegalStateException.class, () ->
                payrollService.calculatePayrollForStaff(storeId, staffId, targetMonth, start, end)
        );

        assertEquals("?뉐츣亮닸쐢 (2026-11) ??덱訝롢겘?㏂겓閻뷴츣歷덀겳(CONFIRMED) ??걼?곥곩냽鼇덄츞?㎯걤?얇걵?볝?永╊툗訝띶쨯?㏛퓷鈺?", ex.getMessage());
    }

    @Test
    @DisplayName("烏뚧붃?싮걫 ??뭽62亮???4?δ퍡 ?븀쇇寧?50?룡틬?졼겗?덆뼋3鸚㎫ク?겼눇?녽ｆ맳濾쒑㉫")
    void applyStatutoryMonthlyRounding_ShouldApplyMhlwNoticeRulesCorrectly() {
        PayrollDto payroll = new PayrollDto();
        payroll.setAppliedHourlyWage(1300); // ?귞덱1,300??-> 25%?꿨쥥?섆쐴: 325??(50??쥊?뚣궦?ｃ궧?덃빐??
        payroll.setTotalOvertimeMinutes(449); // 7?귡뼋29??-> 30?녷쑋繹?뉎굤?ⓦ겍???귡뼋
        payroll.setTotalNightMinutes(450);    // 7?귡뼋30??-> 30?녵빳訝듿늾?듾툓?믡겎8?귡뼋

        PayrollService.StatutoryPayrollResult result = payrollService.applyStatutoryMonthlyRounding(payroll, 329);

        // ???덆뼋?귡뼋??ク?겼눇?놅펷?귡뼋鸚뽧꺕曆긷쩂?삡폂???앫굦?욁굦?х쳦??0?녶쥊?뚦눇?놅펹
        assertEquals(7, result.roundedOvertimeHours(), "7?귡뼋29?녴겘30?녷쑋繹?뉎굤?ⓦ겍???귡뼋?ⓦ겒?뗣걪??);
        assertEquals(8, result.roundedNightHours(), "7?귡뼋30?녴겘30?녵빳訝듿늾?듾툓?믡겎8?귡뼋?ⓦ겒?뗣걪??);
        assertEquals(5, result.roundedHolidayHours(), "5?귡뼋29?녴겘30?녷쑋繹?뉎굤?ⓦ겍???귡뼋?ⓦ겒?뗣걪??);

        // ??1?귡뼋?귙걼?듽겗?꿨쥥?섆쐴竊?,300 * 0.25 = 325?녴?,300 * 0.35 = 455?놅펹
        assertEquals(325, result.overtimeHourlyPremium());
        assertEquals(325, result.nightHourlyPremium());
        assertEquals(455, result.holidayHourlyPremium());

        // ???덆뼋?뗥퐪渶뤻죲??ク?겼눇?놅펷?끿쎅?η떖塋뗩썓鼇덌펹
        // ?귡뼋鸚? 325??* 7?귡뼋 = 2,275??
        assertEquals(2275, result.statutoryOvertimeAllowance());
        // 曆긷쩂: 325??* 8?귡뼋 = 2,600??
        assertEquals(2600, result.statutoryNightAllowance());
        // 鴉묉뿥: 455??* 5?귡뼋 = 2,275??
        assertEquals(2275, result.statutoryHolidayAllowance());
    }

    @Test
    @DisplayName("?섆?永╊툗閻뷴츣?귙곫뿢?ョ▶若싨툑??CONFIRMED)??졃?덀겘?꿱┳?꾠꺆?껁궚?㏛폏孃뚣겓IllegalStateException?믤뒘?믡굥?볝겏")
    void confirmPayroll_WhenAlreadyConfirmed_ShouldThrowIllegalStateException() {
        Long payrollId = 200L;
        PayrollDto existing = new PayrollDto();
        existing.setPayrollId(payrollId);
        existing.setPayrollStatus("CONFIRMED");

        when(payrollMapper.findByIdForUpdate(payrollId)).thenReturn(existing);

        IllegalStateException ex = assertThrows(IllegalStateException.class, () ->
                payrollService.confirmPayroll(payrollId, null)
        );

        assertEquals("?㏂겓閻뷴츣歷덀겳??덱訝롢깈?쇈궭?㎯걲??, ex.getMessage());
    }

    @Test
    @DisplayName("?섆?永╊툗閻뷴츣?귙곭퇏?곫쐿?볡탞雅녶뎺(?⒵쐿閻뷴츣)?츸llegalStateException?㏝겗??걬?뚣굥?볝겏")
    void confirmPayroll_WhenPeriodNotEnded_ShouldThrowIllegalStateException() {
        Long payrollId = 201L;
        PayrollDto existing = new PayrollDto();
        existing.setPayrollId(payrollId);
        existing.setStoreStaffId(5L);
        existing.setPayrollStatus("DRAFT");
        existing.setRangeEndDate(LocalDate.now().plusDays(5)); // ?ゆ씎??뿥餓?

        when(payrollMapper.findByIdForUpdate(payrollId)).thenReturn(existing);

        IllegalStateException ex = assertThrows(IllegalStateException.class, () ->
                payrollService.confirmPayroll(payrollId, null)
        );

        assertEquals("暎양츞野얕괌?잓뼋 (" + existing.getRangeEndDate() + ") ?뚨탞雅녴걲?뗥뎺?ョ덱訝롢굮閻뷴츣?쇻굥?볝겏??겎?띲겲?쎼굯??, ex.getMessage());
    }

    @Test
    @DisplayName("佯쀨닓?ⓧ퐪??쐢轝←덱訝롣??х▶若싥걣閭ｅ만?ュ츪烏뚣걬?뚦뀲餓띄▶若싥걬?뚣굥?볝겏")
    void confirmMonthlyPayroll_Success() {
        Long storeId = 1L;
        String targetMonth = "2026-10";

        PayrollDto p1 = new PayrollDto();
        p1.setPayrollId(1L);
        p1.setStoreStaffId(101L);
        p1.setPayrollStatus("DRAFT");
        p1.setRangeStartDate(LocalDate.of(2026, 10, 1));
        p1.setRangeEndDate(LocalDate.of(2026, 10, 31));

        PayrollDto p2 = new PayrollDto();
        p2.setPayrollId(2L);
        p2.setStoreStaffId(102L);
        p2.setPayrollStatus("DRAFT");
        p2.setRangeStartDate(LocalDate.of(2026, 10, 1));
        p2.setRangeEndDate(LocalDate.of(2026, 10, 31));

        when(payrollMapper.lockStorePayrollsForUpdate(storeId, targetMonth)).thenReturn(java.util.List.of(p1, p2));
        when(payrollMapper.countUnresolvedAttendance(101L, p1.getRangeStartDate(), p1.getRangeEndDate())).thenReturn(0);
        when(payrollMapper.countUnresolvedAttendance(102L, p2.getRangeStartDate(), p2.getRangeEndDate())).thenReturn(0);
        when(payrollMapper.updateAllPayrollStatusConfirm(org.mockito.ArgumentMatchers.eq(storeId), org.mockito.ArgumentMatchers.eq(targetMonth), org.mockito.ArgumentMatchers.any())).thenReturn(2);

        int confirmedCount = payrollService.confirmMonthlyPayroll(storeId, targetMonth);

        assertEquals(2, confirmedCount);
    }

    @Test
    @DisplayName("佯쀨닓?ⓧ퐪??쐢轝←덱訝롣??х▶若싨셽?곫쑋??릤?ㅶ졼걣耶섇쑉?쇻굥?밤궭?껁깢?뚣걚?뗥졃?덀겘訝?х▶若싥굮??뼪?쇻굥?볝겏")
    void confirmMonthlyPayroll_WhenUnresolvedAttendance_ShouldThrowIllegalStateException() {
        Long storeId = 1L;
        String targetMonth = "2026-10";

        PayrollDto p1 = new PayrollDto();
        p1.setPayrollId(1L);
        p1.setStoreStaffId(101L);
        p1.setPayrollStatus("DRAFT");
        p1.setRangeStartDate(LocalDate.of(2026, 10, 1));
        p1.setRangeEndDate(LocalDate.of(2026, 10, 31));

        when(payrollMapper.lockStorePayrollsForUpdate(storeId, targetMonth)).thenReturn(java.util.List.of(p1));
        when(payrollMapper.countUnresolvedAttendance(101L, p1.getRangeStartDate(), p1.getRangeEndDate())).thenReturn(1);

        IllegalStateException ex = assertThrows(IllegalStateException.class, () ->
                payrollService.confirmMonthlyPayroll(storeId, targetMonth)
        );

        assertEquals("?ゅ눇?녴겗?ㅶ졽엶閭ｇ뵵獄뗣겲?잆겘?볟댗?뚦춼?ⓦ걲?뗣걼?곥곫쐢轝←덱訝롢굮訝?х▶若싥겎?띲겲?쎼굯??野얕괌?밤궭?껁깢ID: 101)", ex.getMessage());
    }
}

``
---

## 📋 Claude에게 요청하는 심층 검수 질문 (Review Questions)

1. **동시성 및 락 전략 (Concurrency & Locking)**:
   - `PayrollService.confirmMonthlyPayroll`에서 수행하는 점포 전체 락(`lockStorePayrollsForUpdate`)과 개별 확정(`confirmPayroll`) 간에 교차 실행 시 데드락 위험이 여전히 존재하는지?
   - `ORDER BY store_staff_id ASC, payroll_id ASC` 정렬 순서가 데드락을 완전히 방어하는지?
2. **TOCTOU 방어선 정합성**:
   - `updateAllPayrollStatusConfirm`의 `NOT EXISTS (SELECT 1 FROM attendance ...)` 서브쿼리 가드가 동시성 레이스 컨디션을 DB 레벨에서 완벽히 봉쇄하는지, 추가적인 엣지 케이스가 있는지?
3. **트랜잭션 격리 및 성능**:
   - 점포 직원 수가 많을 때(예: 50~100명), 일괄 마감 트랜잭션의 락 점유 시간과 타임아웃 위험을 완화할 수 있는 추가적인 아키텍처 개선점이 있는지?
4. **비즈니스 예외 및 정합성**:
   - 일본 노동기준법 제37조 및 실무 급여 마감 프로세스 관점에서 놓치고 있는 예외 상황이나 개선 가능한 리팩토링 포인트는 무엇인지?