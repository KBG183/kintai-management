# Day 1 ~ Day 9 오판 사례 기반 골든 테스트 명세서 (Golden Test Specifications)

## 1. 개요
본 명세서는 Hermes v1 운영 과정(Day 1부터 Day 9까지)에서 실제로 관찰된 모델의 환각, 법률 오해, 기술적 오류, 범위 혼동 등 **10대 핵심 오판 사례**를 테스트 케이스로 정형화한 검증 세트다.
향후 프롬프트 개정이나 검증기 도입 시 로컬 모델 및 검증기가 본 10개 테스트를 통과해야만 프로덕션에 배포될 수 있다.

---

## 2. 10대 골든 테스트 케이스 상세 명세

### 검증 범위 분리
GT-01, GT-02, GT-08, GT-09, GT-10은 결정론적 검증기의 구조·증적·정합성 fixture다. GT-03~GT-07은 동일 envelope의 구조 검증만 오프라인으로 수행하며, 노동법·MySQL·IDOR의 사실 판정은 Qwen 감사 추론 및 Kintai 애플리케이션 회귀 테스트에서 별도로 검증한다.

### GT-01: 감사 번들 범위 밖 코드 누락 시 결함 오판 방어
- **테스트 ID**: `GT-01-MISSING-BUNDLE-BOUNDARY`
- **입력 시나리오**: `PayrollExcelExporter.java` 단독 파일만 감사 번들로 제공됨. 해당 클래스는 `PayrollDto`와 POI 라이브러리만 사용함.
- **v1 안티패턴**: "급여 계산 비즈니스 로직 및 세금 공제 로직이 구현되어 있지 않으므로 임금체불 위험이 있어 P0 FAIL"이라고 오판.
- **기대 Verdict / Severity**: `Verdict: N/A` 또는 `Verdict: NOT_VERIFIABLE` / `Severity: INFO`
- **합격 기준**: 클래스의 책임이 엑셀 변환(Export)임을 인지하고, 계산 로직 부재를 이유로 FAIL을 발행하지 않아야 함.

---

### GT-02: 피호출 의존성 클래스 누락 시 정상/결함 단정 금지
- **테스트 ID**: `GT-02-MISSING-DEPENDENCY-HANDLING`
- **입력 시나리오**: `PayrollApiController.java`가 번들에 포함되어 있으나 주입받는 `PayrollService.java`는 번들에 누락됨.
- **v1 안티패턴**: "컨트롤러에서 서비스 호출 시 권한 검증이나 예외 처리가 실제로 일어나는지 확인할 수 없으므로 P1 FAIL" 선언.
- **기대 Verdict / Severity**: `Verdict: NOT_VERIFIABLE` / `Severity: P2` (또는 N/A)
- **합격 기준**: `missing_dependencies: ["PayrollService"]`를 기록하고, 증거 부재를 이유로 `FAIL`을 선언하지 않고 `NOT_VERIFIABLE`로 판정할 것.

---

### GT-03: 심야근로 및 시간외근로 동시 중첩 가산율 (+50%) 판정
- **테스트 ID**: `GT-03-NIGHT-OVERTIME-OVERLAP`
- **입력 시나리오**: 근태 데이터 모델에 `overtimeMinutes`와 `nightMinutes`가 각각 120분으로 저장되고, 급여 계산기에서 `(overtimeMinutes * 0.25) + (nightMinutes * 0.25)`로 각각 독립 가산하여 기본급의 150%를 산출함.
- **v1 안티패턴**: "동시 중첩 가산 50%를 위한 별도의 `nightOvertimeMinutes` 컬럼이나 메서드가 없으므로 일본 노동기준법 37조 위반 P0 FAIL"이라고 오판.
- **기대 Verdict / Severity**: `Verdict: PASS` / `Severity: INFO`
- **합격 기준**: 독립된 25% 가산의 합이 수학적으로 50% 중복 가산과 정확히 일치함을 인식하고 PASS로 승인할 것.

---

### GT-04: 총 체류시간(Gross)과 실제 노동시간(Net) 휴게 차감 경계값
- **테스트 ID**: `GT-04-GROSS-NET-BREAK-BOUNDARY`
- **입력 시나리오**: 출근 09:00, 퇴근 17:45 (체류 8시간 45분 = 525분). 점심 휴게시간 45분 차감. 순 실노동시간은 정확히 480분(8시간 0분).
- **v1 안티패턴**: "525분이므로 법정근로 8시간(480분)을 45분 초과하여 연장근로수당이 지급되어야 하는데 0분으로 계산되어 P1 FAIL"이라고 오판.
- **기대 Verdict / Severity**: `Verdict: PASS` / `Severity: INFO`
- **합격 기준**: 체류시간 525분에서 휴게 45분을 차감한 순 노동시간(480분)이 법정근로 한도 내이므로 연장근로가 0분인 것이 합법임을 정확히 판정할 것.

---

### GT-05: 통상임금 시간급 환산 시 1엔 단위 사사오입(HALF_UP) 단수처리
- **테스트 ID**: `GT-05-STATUTORY-ROUNDING-HALF-UP`
- **입력 시나리오**: 월 소정근로시간으로 월급을 나누어 시간급 산출 시 소수점이 발생하여 `BigDecimal.setScale(0, RoundingMode.HALF_UP)`(50전 이상 절상, 50전 미만 절사) 적용.
- **v1 안티패턴**: "노동자에게 불리한 반올림은 전액 지급 원칙 위반이므로 올림(CEILING)만 허용되어 P1 FAIL"이라고 잘못된 행정해석 제시.
- **기대 Verdict / Severity**: `Verdict: PASS` / `Severity: INFO`
- **합격 기준**: 일본 통달(昭和63年3月14日 基発150号)에 따라 50전 이상 절상/절사 처리가 법적으로 허용된 합법적 단수처리임을 인정할 것.

---

### GT-06: MySQL 8.4 기본 트랜잭션 격리수준 팩트체크
- **테스트 ID**: `GT-06-MYSQL-REPEATABLE-READ-DEFAULT`
- **입력 시나리오**: Spring Boot `application.yaml`에 격리수준이 별도 지정되지 않고 표준 MySQL 8.4 InnoDB 방언을 사용함.
- **v1 안티패턴**: "기본 격리수준이 READ COMMITTED이므로 Phantom Read 및 Non-Repeatable Read가 발생할 수 있어 P1 WARN/FAIL"이라고 오판.
- **기대 Verdict / Severity**: `Verdict: PASS` / `Severity: INFO`
- **합격 기준**: MySQL InnoDB의 공식 기본 격리수준이 `REPEATABLE READ`임을 정확히 적시하고 오판하지 않을 것.

---

### GT-07: IDOR 보안 선언부 vs 실제 인가 강제 검증
- **테스트 ID**: `GT-07-IDOR-ENFORCEMENT-VERIFICATION`
- **입력 시나리오**: Controller 메서드에 `@PreAuthorize("isAuthenticated()")`만 붙어 있고 타인의 급여명세서 ID(`payrollId=99`)를 URL 파라미터로 넘겼을 때 소유권 검사가 Service 및 Controller에 전무함.
- **v1 안티패턴**: "인증 어노테이션이 붙어 있으므로 IDOR 보안 PASS"라고 맹목적 통과 처리.
- **기대 Verdict / Severity**: `Verdict: FAIL` / `Severity: P1`
- **합격 기준**: 단순 로그인 여부만 검사하고 리소스 소유권(Ownership) 검증이 누락되었음을 정확히 지적하여 FAIL을 발행할 것.

---

### GT-08: 본문 결론과 마크다운 요약 표 간 Verdict 불일치 탐지
- **테스트 ID**: `GT-08-VERDICT-CONSISTENCY-CHECK`
- **입력 시나리오**: 모델의 상세 분석 텍스트에는 "치명적인 SQL Injection 및 IDOR 결함이 발견됨"이라고 적고 표에는 `Verdict: PASS`로 기재한 환각 케이스.
- **기대 검증기 동작**: `REJECT_INCONSISTENCY` (Exit Code 3)
- **합격 기준**: 파서 및 검증기가 본문 내용과 표의 판정이 상충함을 감지하여 리포트 채택을 거부할 것.

---

### GT-09: 중복 Finding ID 생성 방지 및 고유성 검증
- **테스트 ID**: `GT-09-DUPLICATE-FINDING-ID`
- **입력 시나리오**: 단일 감사 리포트 내에 `F-001`이 두 개의 서로 다른 결함에 중복으로 할당됨.
- **기대 검증기 동작**: `REJECT_DUPLICATE_ID`
- **합격 기준**: 검증기가 스키마 위반으로 판정하고 리포트 승인을 거부할 것.

---

### GT-10: 유효하지 않은 Severity 및 Verdict Enum 거절
- **테스트 ID**: `GT-10-INVALID-ENUM-REJECTION`
- **입력 시나리오**: 표에 `Severity: CRITICAL_ERROR` 또는 `Verdict: MAYBE`와 같이 비표준 문자열이 들어옴.
- **기대 검증기 동작**: `SCHEMA_VALIDATION_FAILURE`
- **합격 기준**: 허용된 5대 Severity(`P0, P1, P2, P3, INFO`) 및 5대 Verdict(`PASS, FAIL, WARN, N/A, NOT_VERIFIABLE`) 외의 값은 즉시 에러로 탈락시킬 것.
