> **履歴資料:** Day 8時点の学習記録です。ここに記載されたモデルタグ、ローカル/フォールバック実行、計算適合性の一般化を現行の実行指示として使用しないでください。現在のQwen設定は `scripts/audit_config.json`、現行範囲はSRS/RTMを参照してください。

# [Day 8 스터디 가이드] 월간 급여 일괄 확정, 2단계 락(2PL) 데드락 해소 및 3-Tier AI 감사 파이프라인

> **이 문서의 활용법 (제미나이 프롬프트 가이드)**:
> 웹 제미나이(Gemini)에 이 파일의 내용 전체를 복사해서 붙여넣은 뒤, 아래 프롬프트를 함께 입력해 보세요:
> *"내가 오늘 Spring Boot와 MySQL 환경에서 구현한 Day 8 개발 및 동시성 제어 내용이야. 이 내용을 바탕으로 나한테 백엔드 기술 면접관처럼 질문을 하나씩 던져주고, 내 답변에 대해 피드백과 모범 답안을 알려줘."*

---

## 1. 오늘의 비즈니스 목표와 구현 기능 (What & Why)

### ① 비즈니스 요구사항
- **기능명**: 점포 단위 월간 급여 일괄 확정 (`confirmMonthlyPayroll`)
- **목적**: 점주(가맹점주)가 매월 말일 또는 정산일에 점포에 소속된 수십 명의 아르바이트 직원의 월간 급여를 버튼 한 번으로 원자적(Atomic)으로 일괄 확정(DRAFT ➔ CONFIRMED)하여 장부를 마감(Closing)하는 기능.
- **주요 제약 조건**:
  - 단 하나의 트랜잭션 내에서 모든 직원의 급여가 일괄 확정되어야 함 (부분 실패 시 전체 롤백).
  - 마감 확정 진행 도중, 다른 관리자나 직원이 단일 급여를 재계산하거나 수정할 수 없어야 함.
  - 마감 확정 시점에 아직 승인/반려되지 않은 근태(`PENDING` 또는 `SUBMITTED`)가 남아있다면 확정을 차단해야 함.

---

## 2. 발생한 핵심 문제: MySQL InnoDB 데드락 (F-04)

### ① 데드락 발생 메커니즘 (Lock Order Inversion)
동일한 데이터베이스에 두 개 이상의 트랜잭션이 서로 다른 순서로 테이블 행(Row)의 배타 락(X-Lock)을 획득하려 할 때 발생했습니다:

- **트랜잭션 A (월간 일괄 확정 작업)**:
  - 과거 구현: `payroll` (자식 행)을 먼저 조회/락 ➔ 이어서 직원 검증을 위해 `store_staff` (부모 행) 락 요청
- **트랜잭션 B (단일 직원 급여 재계산 작업)**:
  - 일반 구현: `store_staff` (부모 행)을 먼저 락 ➔ 계산 결과를 저장하기 위해 `payroll` (자식 행) 락 요청

```
[트랜잭션 A]  payroll (락 보유) --------(대기: store_staff 락 내놔)--------> store_staff
     ▲                                                                        │
     │                                                                        ▼
store_staff <-------(대기: payroll 락 내놔)-------- store_staff (락 보유)  [트랜잭션 B]
```

두 트랜잭션이 서로 상대방이 쥐고 있는 락이 풀리기만을 기다리는 **순환 대기(Circular Wait)** 상태에 빠지게 되었고, MySQL InnoDB 데드락 감지 엔진이 이를 감지하여 한쪽 트랜잭션을 강제 롤백(`Error Code: 1213 - Deadlock found when trying to get lock`)시켰습니다.

### ② 실제 MySQL InnoDB 상태 로그 증거 (Physical Evidence)
```text
*** (1) TRANSACTION:
TRANSACTION 4267, ACTIVE 0 sec starting index read
mysql tables in use 1, locked 1
LOCK WAIT 2 lock struct(s), heap size 1128, 1 row lock(s)
MySQL thread id 1113, query id 285 localhost root updating
-- payroll 행 락을 보유한 상태에서 store_staff의 락을 기다림 (WAITING FOR THIS LOCK TO BE GRANTED)

*** (2) TRANSACTION:
TRANSACTION 4266, ACTIVE 0 sec starting index read
mysql tables in use 1, locked 1
LOCK WAIT 2 lock struct(s), heap size 1128, 1 row lock(s)
MySQL thread id 1112, query id 286 localhost root updating
-- store_staff 행 락을 보유한 상태에서 payroll의 락을 기다림 (WAITING FOR THIS LOCK TO BE GRANTED)

*** WE ROLL BACK TRANSACTION (1)
```

---

## 3. 해결책: 2단계 배타 제어(Two-Phase Locking, 2PL) 아키텍처

데드락을 해결하기 위해 소프트웨어 공학의 **글로벌 오름차순 정렬 락(Global ASC Order Locking)**과 **2단계 락(2PL)**을 시스템 전체에 표준화했습니다.

### ① 3단계 방어 프로세스
1. **1단계 (부모 행 사전 선점 락)**:
   - 대상 점포에서 확정 대기 중인(`DRAFT`) 모든 직원 ID(`store_staff_id`)를 조회한 뒤, **무조건 오름차순(`ORDER BY store_staff_id ASC`)으로 정렬**합니다.
   - 정렬된 순서대로 부모 테이블인 `store_staff`에 대해 `SELECT ... FOR UPDATE`를 한 번에 일괄 선점합니다.
   - 시스템 내의 모든 비즈니스 로직(재계산, 개별 확정, 일괄 확정)이 **반드시 부모(`store_staff`) ➔ 자식(`payroll`) 순서로 락을 획득**하도록 강제하여 순환 대기 가능성을 수학적으로 0으로 만듭니다.
2. **2단계 (자식 행 재조회 및 동적 경계 검증)**:
   - 부모 락을 획득한 후, 안전한 상태에서 해당 월의 `payroll` 레코드를 다시 조회합니다.
   - 락을 획득하는 찰나의 순간에 다른 스레드가 데이터를 건드렸는지 정합성을 재검증합니다.
3. **3단계 (원자적 CAS 일괄 갱신)**:
   - 조건절에 `status = 'DRAFT'`를 명시한 CAS(Compare-And-Swap) 방식의 UPDATE 쿼리를 발행하여 단일 쿼리로 안전하게 `CONFIRMED` 상태로 마감합니다.

### ② 실제 구현 소스 코드 발췌 (`PayrollService.java`)
```java
@Transactional
public int confirmMonthlyPayroll(Long storeId, YearMonth targetYearMonth) {
    // [1단계] 부모 행 선점: 대상 DRAFT 직원 ID를 오름차순(ASC)으로 추출하여 선행 락 획득
    List<Long> draftStaffIds = payrollMapper.findDraftStaffIdsByStoreAndMonth(storeId, targetYearMonth);
    if (draftStaffIds.isEmpty()) {
        return 0;
    }
    // 부모 테이블(store_staff) 오름차순 FOR UPDATE 일괄 획득 (락 순서 역전 원천 차단)
    payrollMapper.lockStoreStaffListForUpdate(draftStaffIds);

    // [2단계] 자식 행 재조회 및 미처리 근태(PENDING/SUBMITTED) 가드
    int unresolvedCount = attendanceMapper.countUnresolvedAttendance(storeId, startDate, endDate);
    if (unresolvedCount > 0) {
        throw new IllegalStateException("未承認または未解決の勤怠申請が存在するため、給与を一括確定できません。");
    }

    // 자식 테이블(payroll) 재조회 및 락 획득
    List<Payroll> targetPayrolls = payrollMapper.lockStorePayrollsForUpdate(storeId, targetYearMonth);

    // [3단계] CAS 원자적 상태 갱신 (DRAFT -> CONFIRMED)
    int affected = payrollMapper.updateAllPayrollStatusConfirm(storeId, targetYearMonth);
    return affected;
}
```

---

## 4. 추가 2중 방어선 (TOCTOU 가드 & 미처리 근태 차단)

### ① TOCTOU (Time-of-Check to Time-of-Use) 방어
- **위험**: 애플리케이션 Java 코드에서 "이미 확정된 급여가 있는가?"를 체크(`Check`)하고 신규 DRAFT를 생성(`Use`)하기까지의 미세한 시간차 사이에, 다른 트랜잭션이 급여를 확정해 버리면 중복 생성이 발생할 수 있음.
- **해결 (DB 레벨 서브쿼리 가드)**:
  `PayrollMapper.xml`의 INSERT 쿼리 자체에 `WHERE NOT EXISTS` 서브쿼리를 탑재하여, DB 레벨에서 해당 월이 이미 `CONFIRMED` 상태라면 INSERT 자체가 0건으로 무시되도록 2중 방어벽 구축.

### ② 미처리 근태 차단 (`countUnresolvedAttendance`)
- **위험**: 알바생이 "퇴근 시간 찍는 걸 깜빡해서 수정 신청을 올렸는데", 점주가 이를 승인하지 않은 채 급여를 마감해 버리면 알바생의 일당이 누락되어 일본 노동기준법 제24조(임금 전액 지급의 원칙) 위반 분쟁 발생.
- **해결**: 마감 버튼 클릭 시 해당 점포의 미처리 근태 및 수정 신청 건수가 `> 0`이면 트랜잭션을 강제 중단하고 안내 메시지 출력.

---

## 5. 3-Tier AI 협동 감사 파이프라인과 오늘(Day 8) 시스템 개선 핵심

### ① 3-Tier 역할 분담 구조
- **Tier 1 (Antigravity - 생성 AI)**: 코드 작성, 리팩토링, 빠른 구현을 주도. (단점: 자기 확증 편향 및 거짓 긍정 가능성)
- **Tier 2 (Hermes / Qwen 27B - 독립 로컬 감사관)**: 자택 RTX 5070 Ti 로컬 인프라에서 Ollama 기반(`qwen3.8:27b`, think: false)으로 구동. 엄격한 영문 마크다운 표로 트랜잭션, 락, 보안, 법률 취약점만 집요하게 감사.
- **Tier 3 (User - 인간 도메인 전문가)**: 업무 정책(예: "확정 중 생성된 DRAFT를 다음 달로 넘길 것인가?") 결정 및 최종 승인.

### ② 오늘 시스템 검수 및 파이프라인 개선 4대 핵심 (Day 8 Upgrade)
오늘 Day 8 작업을 진행하며 AI 하이브리드 검수 시스템의 신뢰성을 극대화하기 위해 아래 4대 규칙을 헌법과 전역 시스템에 영구 확립했습니다:

1. **독립 로컬 감사 없는 독단적 '셀프 승인(Self-Approval)' 원천 금지 (규칙 20-⑧ / 백로그 B-08)**:
   - 생성 AI(Antigravity)가 로컬 감사관(Qwen 27B)의 정석 검증 루프 없이 스스로 `VERIFIED`나 `PASS`를 판정하고 외부에 완료를 보고하는 행위를 엄격히 차단했습니다.
   - 반드시 **감사 대상 소스 번들과 로컬 27B 결과 리포트의 SHA-256 해시값**을 확보하고 공식 PASS를 도출한 뒤에만 최종 검증 완료로 인정합니다.
2. **구두 설명(Hand-waving) 해소 전면 금지 및 결함 유형별 '물리적 증거' 의무화 (규칙 17-④)**:
   - 말로만 "잘 처리되었습니다"라고 넘어가는 것을 금지하고, 결함 유형에 맞는 실제 증거를 제출해야만 WARN/FAIL을 해소할 수 있도록 통제했습니다:
     - 동시성/데드락 ➔ InnoDB 상태 로그(`SHOW ENGINE INNODB STATUS`) + 락 획득 순서 증명
     - TOCTOU ➔ DB 서브쿼리 가드(`WHERE NOT EXISTS`) + 격리 수준 검증
     - IDOR ➔ 세션 소유권 인가 코드 + 타인 점포 접근 차단 단위 테스트
3. **요구사항 추적표(RTM) 상태 분리 (`IMPLEMENTED` vs `VERIFIED`) (규칙 20-②, ③)**:
   - 코드를 단순히 파일에 작성한 상태(`IMPLEMENTED`)와, 물리적 테스트 및 DB 검증으로 통과를 증명한 상태(`VERIFIED`)를 엄밀하게 분리하여 AI의 조기 완료 착각을 방지했습니다.
4. **실측 토큰 누적 시스템 및 `think: false` 고속화 (규칙 6, 11)**:
   - Ollama `think: false` 런타임 최적화로 추론 대기 시간을 없애고 초당 `49 ~ 51 tokens/s`의 고속 감사를 실현했습니다.
   - 오늘 4회 실행 실측치(47,231 tokens / 189.53초)를 물리적 로그 파일(`daily_audit_temp.log`)에 누적 기록하여 누적 210,574 tokens의 정합성을 100% 실측치로 증명했습니다.

### ③ RTM (요구사항 추적표)의 실무적 의의
- "코드를 작성했다(`IMPLEMENTED`)"와 "실제 동작을 증명했다(`VERIFIED`)"를 엄격히 분리.
- JUnit XML, DB 락 대기 로그, 커넥션 ID 등 **물리적 증거(Evidence)**가 첨부되지 않은 작업은 완료로 인정하지 않는 엔터프라이즈 감사 기준 확립.

---

## 6. 제미나이(Gemini)와 모의 기술 면접 실전 Q&A

제미나이에게 질문해 보거나 스스로 답변을 정리해 볼 수 있는 모범 문답 5선입니다:

### Q1. "두 트랜잭션 간 데드락이 발생했을 때, 어떻게 원인을 분석하고 해결했나요?"
> **모범 답변**:
> "MySQL의 `SHOW ENGINE INNODB STATUS` 로그를 분석하여 상호 대기 관계를 파악했습니다. 단일 급여 계산 로직은 부모인 `store_staff`를 먼저 잠그고 자식인 `payroll`을 잠갔는데, 월간 일괄 확정 로직은 반대로 `payroll`을 먼저 조회한 후 `store_staff`를 잠그면서 락 획득 순서 역전(Lock Order Inversion)이 발생했습니다.
> 이를 해결하기 위해 시스템 전반의 락 획득 순서를 '부모(`store_staff`) ➔ 자식(`payroll`)' 순으로 일원화하고, 특히 다건 처리 시에는 직원 ID를 오름차순(ASC)으로 정렬하여 락을 선점하는 2단계 배타 제어(Two-Phase Locking)를 적용하여 데드락을 원천 차단했습니다."

### Q2. "비관적 락(Pessimistic Lock)을 선택한 이유가 무엇인가요? 낙관적 락(@Version)으로는 안 되나요?"
> **모범 답변**:
> "낙관적 락은 충돌이 드물다는 전제하에 커밋 시점에 버전을 대조하므로, 다수의 사용자가 동시에 마감 버튼을 누르거나 급여를 집계할 때 롤백 및 재시도(Retry) 부하가 애플리케이션 계층으로 전가됩니다.
> 특히 급여나 금융 데이터는 돈이 1원이라도 틀리면 안 되는 미션 크리티컬 도메인이므로, 충돌 발생 후 재시도하는 것보다 DB 엔진 수준에서 `SELECT ... FOR UPDATE`로 요청을 즉시 직렬화(Serialization)하여 데이터 무결성을 보장하는 비관적 락이 비즈니스적으로 훨씬 안전하다고 판단했습니다."

### Q3. "일괄 확정 도중 새로 등록된 근태나 신규 DRAFT 데이터는 어떻게 처리되나요?"
> **모범 답변**:
> "월간 확정 트랜잭션이 시작될 때 오름차순으로 락을 선점한 대상 직원 목록에 대해서만 확정이 진행됩니다. 확정 트랜잭션 도중 새로 생성된 DRAFT는 부모 행 락에 의해 대기하거나 격리되며, 이번 확정 대상에 잘못 섞여 들어가지 않고 다음 정산 주기로 안전하게 이월되도록 설계 및 단위 테스트로 검증했습니다."

### Q4. "TOCTOU(Time-of-Check to Time-of-Use) 레이스 컨디션을 어떻게 방어했나요?"
> **모범 답변**:
> "Java 서비스 계층의 단순 `if`문 체크는 체크 시점과 실제 데이터 INSERT 시점 사이에 동시성 틈새가 존재합니다. 따라서 애플리케이션 계층의 검증에만 의존하지 않고, MyBatis INSERT 쿼리 내부에 `WHERE NOT EXISTS (SELECT 1 FROM payroll WHERE ... status = 'CONFIRMED')` 서브쿼리 가드를 직접 배치하여 DB 엔진 수준에서 2중으로 원자적 방어를 달성했습니다."

### Q5. "1인 개발에서 AI를 활용할 때 발생할 수 있는 '자기 확증 편향'과 '셀프 승인' 위험을 어떻게 극복했나요?"
> **모범 답변**:
> "코드를 생성한 AI에게 스스로 검수까지 맡기면 자신이 만든 논리 오류를 놓치는 '거짓 긍정(False Positive)'과 근거 없이 혼자 완료를 선언하는 '셀프 승인(Self-Approval)' 문제가 발생합니다.
> 이를 극복하기 위해 두 가지 기술적 장치를 도입했습니다. 첫째, 자택 로컬 RTX 5070 Ti 인프라에 독립된 Qwen 27B 모델을 배치하여 생성 AI의 컨텍스트와 물리적으로 격리된 상태에서 오직 취약점 감사만 전담시켰습니다.
> 둘째, 단순 구두 설명을 불인정하고, RTM(요구사항 추적표) 상에서 코드 작성(`IMPLEMENTED`)과 물리적 검증(`VERIFIED`)을 엄격히 분리했습니다. InnoDB 락 대기 로그, JUnit 실행 결과, SHA-256 해시값 기반의 27B 공식 PASS 리포트라는 물리적 증거가 첨부되어야만 최종 승인되도록 파이프라인 헌법(규칙 20-⑧)을 구축하여 엔지니어링 신뢰성을 확보했습니다."
