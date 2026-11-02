# 📘 일본형 시프트·근태·급여 정산 시스템 마스터 기술 보고서 및 초상세 개발 명세서 (SRS)

> **시스템명**: 일본형 시프트·근태·급여 정산 시스템 (勤怠・シフト・給与管理システム / Kintai Management System)  
> **문서 성격**: 프로젝트 개론서 · 아키텍처 설계서 · DB 명세서 · AI 프롬프트용 초상세 구현 가이드 통합본  
> **기술 스택(초안)**: Java 21, Spring Boot 4.0.x, MyBatis Spring Boot Starter 4.0.x, MySQL 8.0, Spring Security(Boot BOM과 호환되는 버전), Thymeleaf, Bootstrap 5.3, FullCalendar 6.1.x, Apache POI(프로젝트 시작 시 호환되는 패치 버전 고정)  
> **문서 활용법**: 본 문서는 기획·요구사항·DB·API·화면·계산 규칙을 함께 관리하는 기준 문서입니다. 구현 전에 실제 프로젝트의 `pom.xml` 또는 `build.gradle`, 설정 파일, 기존 공통 코드를 확인하고, 본 문서와 실제 코드가 다르면 임의로 추측하지 말고 팀에서 기준을 확정합니다.

---

> **문서 상태 및 적용 원칙 (2인 정예 프로젝트 확정)**
> - **팀 규모 및 개발 기간 확정**: **2인 정예 팀, 약 3~4주 개발 스프린트 (2026.10 ~ 2026.11)**로 최종 확정되었습니다.
> - **2인 분업 핵심 원칙 (파트 분할 모델 - 킥오프 협의 배정)**:
>   - *(※ 담당자 배정은 팀 킥오프 미팅에서 2인의 강점과 희망에 따라 파트 A와 파트 B 중 상호 합의 하에 자유롭게 결정)*
>   - **파트 A (비즈니스 코어 & 노동법 도메인)**: 시프트 캘린더 엔진, 근태 4대 타각/다회 휴게 시간 계산기, 일본 노동법 37조 급여 산출 엔진, 급여 확정 잠금, POI 엑셀 출력, 트랜잭션/동시성 락(F01~F10 방어).
>   - **파트 B (인증·인가 & 점포/계약 마스터 & UI/UX)**: Spring Security 기반 회원가입/로그인 및 권한(점주/스태프) 분리, 점포(Store) 등록 및 점주-직원 계약 CRUD, Thymeleaf 모바일 반응형 UI 스타일링 및 폼 유효성 검사.
> - **범위 집중 (Phase 1 우선 원칙)**: 2인 3~4주 납기 준수 및 최고 품질 달성을 위해 **Phase 1 (1개 점포 기준 계약~시프트~근태~급여~엑셀 완결 슬라이스)**에 100% 집중하며, LINE 알림 연동·BI 대시보드·다점포 근로시간 합산 등은 **Phase 2 차기 확장 과제**로 명확히 분리합니다.
> - **법률 준수 범위**: 본 프로젝트는 학습·포트폴리오용입니다. 실제 급여 지급에 사용하기 전에는 적용 지역·기간·근로자 유형·법령 개정 사항을 확인하고, 노무·세무 전문가의 검토를 받아야 합니다.
> - **개발 협업 원칙**: 공통 DB 스키마·API DTO·보안 설정은 팀에서 합의한 뒤 통합합니다. 브랜치 전략(`feature/...` ➔ PR ➔ `develop`)을 준수하여 협업합니다.

## 제0장. 프로젝트 개론 및 아키텍처 철학 (Project Overview & Philosophy)

### 0.1 왜 이 시스템을 개발하는가? (배경 및 문제 정의)
일본의 음식점·소매점 아르바이트 현장에서는 여러 매장에 걸친 근로계약, 시프트 조율, 출퇴근 기록, 수당 계산이 서로 다른 방식으로 관리될 수 있습니다. 수기·메신저·스프레드시트에 의존하면 기록 누락, 계약 조건 혼동, 승인 이력 부족, 계산 오류가 발생하기 쉽습니다.

본 문서는 특정 업계 통계의 정확한 비율을 전제로 하지 않습니다. 외부 통계 수치를 발표 자료에 사용할 경우에는 조사기관·조사연도·대상·원문 URL을 별도로 확인해 기록합니다.

1. **다점포 근무와 계약 조건 관리**: 한 직원이 여러 매장에서 일할 수 있으며, 매장별 시급·통근수당·근무 조건이 다를 수 있습니다.
2. **시프트 조율과 이력 보존**: 직원 희망안과 관리자 확정안이 구분되어야 하고, 제출·확정·반려 이력을 확인할 수 있어야 합니다.
3. **근태 수정과 감사 추적**: 출근·퇴근 타각 누락 및 오류를 신청·승인 절차로 처리하고, 누가 언제 어떤 값을 승인했는지 보존해야 합니다.
4. **근로시간·수당 계산**: 야간근로(22:00부터 익일 05:00까지), 시간외근로, 휴게시간, 통근수당을 분리해 계산해야 합니다. 실제 법정 정산에는 일별·주별·월별 기준, 법정휴일, 월 60시간 초과 시간외근로, 통근수당의 과세 구분 등 추가 규칙이 영향을 줄 수 있습니다.

> **핵심 진단**: `근로계약 → 시프트 계획 → 근태 기록 → 급여 정산`의 연결을 단일 데이터 흐름으로 관리합니다. MVP는 입력 검증·승인 이력·정산 스냅샷·테스트 가능성을 우선하며, 모든 법정 급여 상황을 완전히 구현했다고 주장하지 않습니다.

---

### 0.2 문제와 핵심성공요인(CSF) 매핑

| 부문 | 해결할 문제 | 기술적 해결 방향 | 완료 기준(테스트 가능한 조건) |
| :--- | :--- | :--- | :--- |
| **1. 계약 마스터** | 매장별 계약 조건 혼동, 타 매장 접근, 계약 변경 이력 손실 | `member`, `store`, `store_staff` 및 계약 이력 관리; 서버 측 역할·매장 소속 검증 | 직원은 소속 매장만 접근 가능하고, 계약 변경 후 과거 계약 이력을 조회할 수 있음 |
| **2. 시프트 계획** | 희망안과 확정안 대조 불가, 중복 시간, 인력 부족 | FullCalendar; 상태 전이 검증; 시간 겹침 검사; 피크타임 경고는 설정된 경우 제공 | 희망 제출·확정·반려가 구별되고, 종료 경계일이 잘못 조회되지 않음 |
| **3. 근태 타각** | 중복 출근, 휴게 누적 오류, 임의 수정 이력 누락 | 상태 머신; 여러 휴게 구간 저장; 수정 신청·승인·반려 감사 이력 | 중복 타각이 차단되고, 수정 전·요청·적용 결과 및 승인자가 추적 가능함 |
| **4. 급여 정산** | 야간·시간외 중복 할증, 계약 변경, 확정 후 데이터 변조 | 분 단위 시간 산출; 수당별 계산; 계약 적용 기간; 급여 상세 스냅샷 | 명세된 범위의 경계값 테스트를 통과하고, 확정 급여는 일반 재정산으로 변경되지 않음 |
| **5. 확장 기능** | 알림 및 경영 지표 부족 | LINE·SSE/WebSocket·BI를 별도 확장 모듈로 설계 | 핵심 MVP 완료 후 팀 인원과 일정에 따라 별도 승인하여 구현함 |

**완료 기준 (Definition of Done)**: 매장·직원 소속 검증 100%, 승인된 변경 이력 추적 보존, 확정 급여의 일반 재계산 변경 0건(장부 불변성), 핵심 계산 단위 테스트 전체 통과.

---

### 0.3 [2인 정예 팀] 역할 분담(R&R) 및 3~4주 스프린트 로드맵 (WBS)

본 프로젝트는 **2인 정예 팀(약 3~4주 개발 스프린트)**으로 운영됩니다. 도메인 분리를 통해 Git 브랜치 충돌을 0%로 만들고, 3~4주 납기 내에 최고의 완성도와 결함 없는 비즈니스 로직을 구축하는 것을 목표로 합니다.

#### ① 2인 역할 분담 모델 (파트 A vs 파트 B 제안 - 킥오프 협의 배정)

| 구분 (제안 모델) | 담당 영역 | 구체적 구현 책임 및 마일스톤 |
| :--- | :--- | :--- |
| **파트 A<br>(비즈니스 코어 & 노동법 도메인)** | **코어 비즈니스 도메인 & 아키텍처** | • **시프트 도메인 (`domain.shift`)**: FullCalendar 캘린더 이벤트 연동, F07 16시간 초과 방어(`Duration.compareTo`), 겹침 방지, F04 비관적 락.<br>• **근태 도메인 (`domain.attendance`)**: 4대 타각 상태 머신, 다회 휴게 분리, `AttendanceTimeCalculator` 노동법 37조(야간 25%, 연장 25%) 정밀 분 단위 산출, 사후 근태 수정 신청 및 점주 승인 플로우.<br>• **급여 도메인 (`domain.payroll`)**: 마감일 자동 계산, F06 `HALF_UP` 1엔 오차 방지 반올림, F01 조기 확정 차단, F02 `expectedGrossPay` 409 Silent Update 방어, F09 최저시급(1,280엔) 검증, F10 통근수당 상한 배분, Apache POI 월별 급여대장 엑셀(.xlsx) 출력.<br>• **품질 보증**: 코어 계산기 및 비관적 락에 대한 JUnit 5 단위/통합 테스트 작성. |
| **파트 B<br>(인증·점포 마스터 & UI/UX)** | **인증·점포 관리 & 프론트엔드 UX** | • **인증 & 보안 (`domain.member`)**: Spring Security 6 기반 회원가입/로그인, 비밀번호 암호화(BCrypt), 세션 인가(`OWNER` vs `STAFF` 롤 분리), `@AuthenticationPrincipal` 세션 연동으로 가짜 ID 제거.<br>• **점포 & 계약 마스터 (`domain.store`)**: 점포 등록/수정/삭제 CRUD, 직원 초대 및 소속 승인, 시급/통근수당 계약 이력(`store_staff_contract_history`) 등록 및 갱신.<br>• **화면 UI & 퍼블리싱 (`templates`)**: Thymeleaf 템플릿 모바일 반응형 UI 고도화, 모바일 타각 인터페이스, FullCalendar 드래그 앤 드롭, Bootstrap 모달 스타일링 및 클라이언트 폼 유효성 검사. |

#### ② 3~4주 집중 범위(Phase 1 MVP)와 확장 과제(Phase 2)의 경계
- **✅ In-Scope (Phase 1 MVP - 3~4주 100% 완결)**:
  `단일 매장 기준: 시급 계약 ➔ 희망 시프트 등록/확정 ➔ 4대 타각/다회휴게 ➔ 근태 수정 승인 ➔ 노동법 37조 급여 일괄 산출 ➔ 확정 잠금 ➔ POI 엑셀 출력` 전 과정 완결.
- **⏸️ Out-of-Scope (Phase 2 차기 확장 과제)**:
  LINE 메시징 알림 연동, 실시간 WebSocket 알림, BI 경영 대시보드, 다점포 근로시간 통산, 유급휴가, 세무 공제/원천징수 등은 2인 일정상 Phase 2 과제로 공식 분리합니다.

#### ③ 2인 4주 스프린트 로드맵 (WBS)

| 주차 | 파트 A 담당자 (코어 비즈니스 & 정산) | 파트 B 담당자 (인증·점포 & UI/UX) | 공통 마일스톤 |
| :---: | :--- | :--- | :--- |
| **1주차** | • 기획/설계 및 DB 스키마 뼈대 공유<br>• 시프트/근태/급여 프로토타입 뼈대 세팅<br>• Hermes 7B 감사 파이프라인 정립 | • Spring Security 로그인/회원가입 기초 구현<br>• 점포(Store) 엔티티 및 기본 CRUD 화면 제작<br>• 공통 Thymeleaf 레이아웃 퍼블리싱 | DB 스키마 v1 합의 및 Git 브랜치 전략(`feature/...` ➔ PR) 수립 |
| **2주차** | • 코어 로직과 Security 실제 세션 주체 연동<br>• 사후 근태 수정 신청 및 점주 승인 로직 결합<br>• 동시성 비관적 락(`FOR UPDATE`) 실데이터 검증 | • 점주 vs 스태프 권한별 URL 접근 제어 완성<br>• 점주-스태프 시급 계약 이력 관리 CRUD 완성<br>• 모바일 타각 및 캘린더 반응형 UI 1차 완성 | 가짜 ID 완전 제거 및 단일 매장 1회 전체 순환 테스트 통과 |
| **3주차** | • Apache POI 급여대장 엑셀 내보내기 연동<br>• F01/F02 급여 확정 잠금 및 409 예외 처리<br>• 최저시급(1,280엔) 및 통근수당 상한 배분 검증 | • 모바일 타각 화면 UX 고도화 및 큰 버튼 UI<br>• 시프트 캘린더 상세 모달 및 프론트 검증<br>• 예외 발생 시 안내 모달 및 토스트 알림 연동 | Phase 1 전체 기능 통합(Integration) 완료 |
| **4주차** | • 일본 노동법 37조 계산기 JUnit 5 단위 테스트 작성<br>• 비관적 락 동시성 멀티스레드 통합 테스트 작성<br>• 아키텍처 및 도메인 기술 문서(README) 작성 | • 전체 화면 CSS/디자인 폴리싱 및 다듬기<br>• 사용자 시연 시나리오 리허설 및 버그 픽스<br>• 발표 PPT 제작 및 시연 영상/GIF 준비 | 최종 빌드 검증 및 프로젝트 발표 성공! |

---

## 제1장. 전사 공통 아키텍처 및 협업 표준 (Global Architecture & Conventions)

모든 팀원과 AI 코딩 도구는 아래 공통 규칙을 준수합니다. 기본 시간대는 일본 매장 현지 시간 `Asia/Tokyo`로 정의하고, 날짜·시간 파싱과 DB 세션 시간대가 일치하는지 확인합니다.

### 1.1 패키지 디렉토리 구조 (권장 기준)
```text
com.example.kintai
├── KintaiApplication.java
├── config
│   └── SecurityConfig.java
├── global
│   ├── common
│   │   ├── ApiResponse.java
│   │   └── TimeUtils.java
│   └── exception
│       └── GlobalExceptionHandler.java
└── domain
    ├── home
    ├── member
    ├── store
    ├── shift
    ├── attendance
    ├── payroll
    ├── notification   # 선택 확장
    └── analytics      # 선택 확장
```

- Java 패키지, Mapper XML, Thymeleaf 템플릿의 명칭을 동일한 도메인 기준으로 유지합니다.
- MyBatis XML: `src/main/resources/mapper/{도메인명}/{도메인}Mapper.xml`
- Thymeleaf: `src/main/resources/templates/{도메인명}/{화면명}.html`
- DB 초기화: `src/main/resources/db/schema.sql`, `src/main/resources/db/data.sql`를 사용합니다. 해당 파일 실행은 Spring 설정 또는 명시한 DB 초기화 절차로 보장합니다.
- 기존 파일이 이미 존재하면 AI에게 새로 만들도록 하기 전에 실제 내용을 확인합니다. `[생성완료]` 표시는 저장소에서 실제 파일을 확인한 경우에만 사용합니다.
- 개발용 데이터와 `permitAll`은 개발 프로필에서만 허용하고, 기본 실행 프로필에서는 인증·인가를 적용합니다.

---

### 1.2 전사 화면(Thymeleaf View) 라우팅 마스터 테이블
아래는 권장 화면 URL입니다. `GET /`에서 샘플 화면으로 **리다이렉트할지** 또는 뷰를 직접 반환할지는 한 가지로 정해 구현합니다. 현재 권장안은 `GET /`에서 `member/sample` 화면을 직접 반환하는 것입니다.

| 메뉴명 | HTTP Method & URL | 반환할 View 경로 | 권장 권한 | 화면 핵심 역할 |
| :--- | :--- | :--- | :--- | :--- |
| 홈 / 개발 샘플 | `GET /` | `member/sample` | 개발 프로필만 샘플 선택 허용 | 테스트 계정·매장 전환(운영에서는 노출하지 않음) |
| 로그인 / 가입 | `GET /login`, `GET /join` | `member/login`, `member/join` | 로그인/가입 정책에 따름 | 로그인·회원가입 |
| 점포 관리 | `GET /stores` | `store/manage` | OWNER | 점포, 직원, 계약 조건 관리 |
| 시프트 계획 | `GET /shifts` | `shift/calendar` | OWNER, STAFF | 희망 제출 및 확정 조율 |
| 출퇴근 타각 | `GET /attendance` | `attendance/punch` | STAFF(점주 테스트 허용은 별도 정책) | 출근·휴게·퇴근 및 월간 기록 |
| 근태 승인관리 | `GET /attendance/mod-requests` | `attendance/mod-list` | OWNER | 수정 요청 승인·반려 |
| 급여 정산 | `GET /payroll` | `payroll/list` | OWNER | 정산·확정·명세서·Excel |
| 경영 분석(확장) | `GET /analytics` | `analytics/dashboard` | OWNER, 확장 구현 시만 제공 | 참고용 인건비/연수입 대시보드 |

개발 PC의 절대 경로(`file:///c:/Users/...`)를 문서에 저장하지 않습니다. 저장소 기준 상대 경로만 사용합니다.

### 1.2.1 Spring Security 권한별 메뉴 분기 및 레이아웃 상속 규칙

`sec:authorize`는 메뉴 표시를 위한 UI 규칙일 뿐 보안 경계가 아닙니다. 모든 Controller/API에서도 로그인 사용자와 매장 소속을 검증합니다. 실제 운영 설정에서 미인증 사용자에게 OWNER/STAFF 메뉴를 표시하지 않습니다.

- **OWNER 메뉴**: `hasRole('OWNER')` — 점포/직원 계약 관리, 근태 승인, 급여 정산·확정, 확장 분석
- **STAFF 메뉴**: `hasRole('STAFF')` — 시프트 제출, 본인 근태 기록 및 수정 신청
- **공통 메뉴**: 로그인/로그아웃, 사용자의 접근 가능한 점포 선택
- **공통 화면 규칙**: 로그인 사용자의 `memberId`, `role`, 접근 가능한 `storeId`는 Spring Security 인증 컨텍스트에서 가져옵니다. 요청 JSON의 `ownerId`, `memberId`, `reviewerId`를 권한의 근거로 사용하지 않습니다.

레이아웃은 현재 실제 `layout.html` 구현과 대조한 뒤 확정합니다. 아래는 파라미터 프래그먼트 방식의 **예시**이며, 기존 프로젝트의 fragment 이름과 일치하는지 확인한 후 사용합니다.

```html
<!DOCTYPE html>
<html lang="ja" xmlns:th="http://www.thymeleaf.org"
      th:replace="~{common/layout :: layout(~{::title}, ~{::#content})}">
<head>
    <title>画面タイトル</title>
</head>
<body>
<div id="content">
    <!-- 페이지 본문 및 필요한 스크립트 -->
</div>
</body>
</html>
```

Spring Security 설정은 Boot가 관리하는 호환 버전을 사용합니다. 개발 편의를 위해 `permitAll`을 쓰더라도 `application-dev.yaml` 등 개발 프로필에서만 활성화하고, 기본/배포 프로필에는 인증 규칙을 적용합니다. 상태를 변경하는 요청에는 CSRF 정책도 확인합니다.

---

### 1.3 다점포(`storeId`, `storeStaffId`) 컨텍스트 처리 및 보안 인가 규칙 (매우 중요)

한 회원이 여러 매장에 소속될 수 있으므로, ID 자체가 아니라 **로그인한 회원이 해당 매장과 직원 매핑에 접근할 권한이 있는지**를 서버에서 검사합니다.

- **개발 프로필**: 테스트 데이터와 명시적인 테스트 전용 화면/API를 이용해 매장을 선택할 수 있습니다. 기본 ID를 쓰는 경우에도 `data.sql`에 실제로 존재하는 레코드를 사용합니다.
- **기본/배포 프로필**: `storeId`, `storeStaffId`, `memberId`, `ownerId`, `reviewerId`를 요청에서 받더라도 이를 인증 정보로 신뢰하지 않습니다. 로그인 사용자와 실제 `store_staff` 관계를 조회해 접근 권한을 검사합니다.
- **ID 구분**: `member_id`와 `store_staff_id`는 서로 다른 PK입니다. `member_id=2`라고 해서 `store_staff_id=2`인 것은 아닙니다.
- **매장 일치 검증**: 전달된 `storeId`와 `storeStaffId`가 같은 매장 소속 관계인지 검증합니다. DB의 복합 외래키와 서비스 계층 검증을 함께 사용합니다.
- **공통 트랜잭션 직렬화 및 동시성 락 (F04 대응)**:
  - 타각(`CLOCK_IN/OUT`), 근태 수정 승인(`review`), 사후 생성, 그리고 급여 계산/확정(`calculate/confirm`)은 동일 직원의 상태를 변경하는 상호 배타적 작업입니다.
  - "한 트랜잭션" 선언만으로는 서로 다른 트랜잭션의 경쟁(Race Condition)을 막을 수 없으므로, 해당 직원의 근태 수정 승인 및 급여 확정 시 동일 `store_staff` 행(또는 `member` 행)을 `SELECT ... FOR UPDATE`로 잠그거나, `payroll` 갱신 시 상태 조건부 갱신(`WHERE payroll_status = 'DRAFT'`)을 사용하여 확정 처리와 수정 승인이 동시에 발생했을 때의 장부 오염을 원천 차단합니다.

테스트 코드에서는 다음처럼 인증 컨텍스트와 분리된 테스트 픽스처를 사용합니다. 아래 예시는 테스트 데이터에 `store_id=1`, `store_staff_id=1`이 실제로 존재하는 경우에만 유효합니다.

```java
// 운영 컨트롤러에서 defaultValue="1"을 사용해 권한을 우회하지 않습니다.
// 테스트에서는 테스트 데이터/Mock 인증 사용자를 명시적으로 주입합니다.
@GetMapping("/shifts")
public String shiftCalendarView(Model model, @AuthenticationPrincipal KintaiUserPrincipal principal) {
    Long storeId = storeAccessService.requireDefaultAccessibleStoreId(principal.getMemberId());
    model.addAttribute("storeId", storeId);
    return "shift/calendar";
}
```

`KintaiUserPrincipal`과 서비스 이름은 구현 시 정하는 예시 이름입니다. 기존 프로젝트에 실제 타입이 있다면 그 타입을 기준으로 맞춥니다.

---

### 1.4 공통 JSON 응답 규격 (`ApiResponse<T>`)

JSON API는 가능하면 공통 `ApiResponse<T>` 형식을 사용합니다. 파일 다운로드처럼 바이너리를 반환하는 응답은 예외입니다.

- **성공 예시**:
  ```json
  { "success": true, "message": "処理が完了しました。", "data": {} }
  ```
- **검증 오류 예시**:
  ```json
  { "success": false, "message": "入力内容を確認してください。", "data": { "fieldErrors": [] } }
  ```
- **HTTP 상태 코드**: 인증 실패 `401`, 권한 부족 `403`, 리소스 없음 `404`, 상태 충돌/중복 처리 `409`, 입력값 검증 오류 `400`, 예상하지 못한 서버 오류 `500` 등 의미에 맞는 상태 코드를 사용합니다.
- `GlobalExceptionHandler`에서 `MethodArgumentNotValidException`, 업무 검증 예외, 인증/인가 예외 등을 의도에 맞게 구분합니다. `@Valid`를 선언하는 것만으로 모든 오류가 자동으로 동일한 JSON으로 변환된다고 가정하지 않습니다.
- 오류 응답에는 SQL, 스택 트레이스, 토큰, 비밀번호 등 내부 정보를 노출하지 않습니다.
- Excel 다운로드는 `Content-Type`, `Content-Disposition`, 파일명, 오류 응답을 별도로 명세합니다.

---

### 1.5 ⭐ [일본 현지 취업 필수] UI 일본어(日本語) 표준화 원칙 및 핵심 용어집 (用語集)
본 프로젝트는 **일본 IT 현지 기업 취업 및 포트폴리오 시연**을 주 목적으로 합니다.  
따라서 **모든 브라우저 화면(Thymeleaf HTML)의 메뉴명, 버튼, 테이블 헤더, 캘린더, 모달 안내문구, 알림 메시지, 그리고 엑셀 출력 서식은 자연스러운 비즈니스 일본어(日本語)**로 작성해야 합니다.

AI에게 화면 코드를 생성시킬 때 반드시 아래의 **공식 용어 대조표**를 엄격히 준수하도록 지시합니다.

| 분류 | 한국어 개념 | 일본어 표준 UI 표기 | 읽는 법 (히라가나/가타카나) | 실제 사용 화면 및 컴포넌트 |
| :--- | :--- | :--- | :--- | :--- |
| **근태 타각** | **출근** | **出勤** | しゅっきん | 타임카드 메인 초록색 버튼 |
| **근태 타각** | **퇴근** | **退勤** | たいきん | 타임카드 메인 빨간색 버튼 |
| **근태 타각** | **휴게 시작** | **休憩開始** | きゅうけいかいし | 타임카드 주황색 버튼 |
| **근태 타각** | **휴게 종료/복귀** | **休憩終了** | きゅうけいしゅうりょう | 타임카드 휴게 종료 버튼 |
| **근태 타각** | **타각 (출퇴근기록)** | **打刻** | だこく | 시스템 전체 명칭 (勤怠打刻) |
| **근태 타각** | **근무중 / 휴게중** | **勤務中 / 休憩中** | きんむちゅう / きゅうけいちゅう | 실시간 상태 배지 |
| **근태 감사** | **근태 수정 신청** | **勤怠修正申請** | しゅうせいしんせい | 알바생 타각 오류 수정 모달 |
| **근태 감사** | **승인 / 반려** | **承認 / 却下** | しょうにん / きゃっか | 점주 감사 검토 버튼 |
| **시프트** | **희망 시프트** | **希望シフト** | きぼうしふと | 알바생 제출 시프트 (주황색 배지) |
| **시프트** | **확정 시프트** | **確定シフト** | かくていしふと | 점주 확정 시프트 (파란색 배지) |
| **시프트** | **미배정** | **未割当** | みわりあて | 필요한 인원이 배정되지 않은 시간대/상태 |
| **시프트** | **결원 경고** | **人員不足警告** | じんいんぶそくけいこく | 피크타임 인원 미달 알림창 |
| **급여 정산** | **기본급** | **基本給** | きほんきゅう | 계산 범위 내 실근무시간에 적용되는 기본 시급분 |
| **급여 정산** | **야간 수당 (25%)** | **深夜手当** | しんやてあて | 22:00~05:00 야간 할증분 |
| **급여 정산** | **연장 수당 (25%)** | **残業手当** | ざんぎょうてあて | 1일 8시간 초과 연장 할증분 |
| **급여 정산** | **통근수당 지급액** | **通勤手当** | つうきんてあて | 계약에 따라 지급한 금액. 비과세 여부와 별도 관리 |
| **급여 정산** | **총 지급액** | **総支給額** | そうしきゅうがく | 세전 총 지급 합계 |
| **급여 정산** | **급여 대장 (엑셀)** | **給与台帳** | きゅうよだいちょう | Apache POI 엑셀 시트명 및 파일명 |
| **급여 정산** | **마감일** | **締め日** | しめび | 점포 급여 마감일 (말일=末日締め) |
| **마스터** | **점주 / 관리자** | **管理者 (オーナー)** | おーなー | 점주 계정 및 권한 (`ROLE_OWNER`) |
| **마스터** | **알바생 / 직원** | **スタッフ (アルバイト)** | すたっふ (あるばいと) | 알바생 계정 및 권한 (`ROLE_STAFF`) |
| **마스터** | **점포 / 매장** | **店舗** | てんぽ | 매장 관리 메뉴 (`店舗管理`) |
| **마스터** | **초대 코드** | **招待コード** | しょうたいこーど | 매장 합류용 8자리 코드 |

---

## 제2장. 데이터베이스 마스터 설계 및 전체 DDL (Database Schema)

AI에게 코드를 생성시킬 때 테이블명과 컬럼명이 단 한 글자도 틀리지 않도록 아래의 확정 DDL을 기준으로 삼습니다.
(`application.yaml`에 `map-underscore-to-camel-case: true`가 적용되어 있으므로 DB의 `store_staff_id`는 Java DTO의 `storeStaffId`와 1:1 자동 매핑됩니다.)

### 2.1 핵심 테이블 DDL (`src/main/resources/db/schema.sql`)

아래 DDL은 MVP의 기준 스키마입니다. 기존 핵심 테이블에 계약 이력, 다회 휴게, 수정 요청 휴게 스냅샷, 급여 일별 상세를 추가했습니다. Mapper와 DTO는 반드시 이 스키마에 맞추고, 스키마를 변경하면 본 문서도 함께 갱신합니다.

`map-underscore-to-camel-case: true`는 일반적인 컬럼명 변환을 지원하지만, 조인 별칭·중첩 DTO·특수 자료형은 명시적 별칭이나 `resultMap`이 필요할 수 있습니다.

```sql
-- 1. 회원 (점주 및 알바생 계정 마스터)
CREATE TABLE IF NOT EXISTS member (
    member_id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '회원 PK',
    email VARCHAR(100) NOT NULL UNIQUE COMMENT '로그인 이메일',
    password VARCHAR(255) NOT NULL COMMENT 'BCrypt 암호화 비밀번호',
    name VARCHAR(50) NOT NULL COMMENT '이름',
    phone VARCHAR(20) NOT NULL COMMENT '연락처',
    role VARCHAR(20) NOT NULL COMMENT '권한 (ROLE_OWNER, ROLE_STAFF)',
    is_active BOOLEAN NOT NULL DEFAULT TRUE COMMENT '계정 활성화 여부',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 2. 점포 (매장 정보 및 급여 마감일/타각 토큰 기준)
CREATE TABLE IF NOT EXISTS store (
    store_id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '점포 PK',
    owner_id BIGINT NOT NULL COMMENT '점주 회원 FK',
    store_name VARCHAR(100) NOT NULL COMMENT '매장명',
    prefecture_code CHAR(2) NOT NULL DEFAULT '13' COMMENT '일본 도도부현 코드(기본 13=도쿄도)',
    invite_code VARCHAR(20) NOT NULL UNIQUE COMMENT '직원 등록용 초대 코드',
    qr_token VARCHAR(100) NOT NULL UNIQUE COMMENT '매장별 QR 토큰(고유성만 보장하며 실제 위치를 증명하지는 않음)',
    open_time TIME NOT NULL DEFAULT '09:00:00' COMMENT '기본 오픈 시간',
    close_time TIME NOT NULL DEFAULT '23:00:00' COMMENT '기본 마감 시간',
    pay_cutoff_day INT NOT NULL DEFAULT 99 COMMENT '급여 마감일 (1~28 또는 99=말일)',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT fk_store_owner FOREIGN KEY (owner_id) REFERENCES member (member_id),
    CONSTRAINT chk_store_cutoff CHECK (pay_cutoff_day BETWEEN 1 AND 28 OR pay_cutoff_day = 99)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 3. 점포 소속 직원 (다점포 매핑 및 개별 시급/통근수당 계약)
CREATE TABLE IF NOT EXISTS store_staff (
    store_staff_id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '점포 직원 매핑 PK',
    store_id BIGINT NOT NULL COMMENT '점포 FK',
    member_id BIGINT NOT NULL COMMENT '직원 회원 FK',
    hourly_wage INT NOT NULL COMMENT '현재 표시용 시급(엔; 급여 계산 원본은 계약 이력)',
    daily_transport_fee INT NOT NULL DEFAULT 0 COMMENT '1일 왕복 교통비(예: 500)',
    monthly_transport_limit INT NOT NULL DEFAULT 15000 COMMENT '월 교통비 상한액(예: 15000)',
    employment_status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE' COMMENT '재직상태 (PENDING, ACTIVE, RESIGNED)',
    joined_date DATE NOT NULL COMMENT '입사일',
    resigned_date DATE NULL COMMENT '퇴사일',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT uq_store_member UNIQUE (store_id, member_id),
    CONSTRAINT uq_store_staff_id_store UNIQUE (store_staff_id, store_id),
    CONSTRAINT fk_staff_store FOREIGN KEY (store_id) REFERENCES store (store_id),
    CONSTRAINT fk_staff_member FOREIGN KEY (member_id) REFERENCES member (member_id),
    CONSTRAINT chk_staff_wage CHECK (hourly_wage > 0),
    CONSTRAINT chk_staff_transport CHECK (daily_transport_fee >= 0 AND monthly_transport_limit >= 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 3-A. 직원 계약 조건 이력(적용일 기준; 급여 계산 시 적용 시급/교통비 조회 원본)
CREATE TABLE IF NOT EXISTS store_staff_contract_history (
    contract_history_id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '계약 이력 PK',
    store_staff_id BIGINT NOT NULL COMMENT '점포 소속 직원 FK',
    effective_from DATE NOT NULL COMMENT '적용 시작일(포함)',
    effective_to DATE NULL COMMENT '적용 종료일(미포함; NULL이면 현재 유효)',
    hourly_wage INT NOT NULL COMMENT '적용 시급(엔)',
    daily_transport_fee INT NOT NULL DEFAULT 0 COMMENT '적용 1일 통근수당 지급액',
    monthly_transport_limit INT NOT NULL DEFAULT 15000 COMMENT '적용 월 통근수당 지급 상한(회사 계약 기준)',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_contract_effective_from UNIQUE (store_staff_id, effective_from),
    CONSTRAINT fk_contract_staff FOREIGN KEY (store_staff_id) REFERENCES store_staff (store_staff_id),
    CONSTRAINT chk_contract_dates CHECK (effective_to IS NULL OR effective_to > effective_from),
    CONSTRAINT chk_contract_wage CHECK (hourly_wage > 0),
    CONSTRAINT chk_contract_transport CHECK (daily_transport_fee >= 0 AND monthly_transport_limit >= 0),
    INDEX idx_contract_staff_period (store_staff_id, effective_from, effective_to)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 4. 시프트 스케줄 (희망 시프트 제출 및 점주 확정 통합 테이블)
CREATE TABLE IF NOT EXISTS shift_schedule (
    shift_id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '시프트 PK',
    store_id BIGINT NOT NULL COMMENT '점포 FK',
    store_staff_id BIGINT NOT NULL COMMENT '점포 직원 FK',
    work_date DATE NOT NULL COMMENT '근무 기준일',
    requested_start_at DATETIME NULL COMMENT '희망 출근 일시',
    requested_end_at DATETIME NULL COMMENT '희망 퇴근 일시',
    confirmed_start_at DATETIME NULL COMMENT '확정 출근 일시',
    confirmed_end_at DATETIME NULL COMMENT '확정 퇴근 일시',
    planned_break_minutes INT NOT NULL DEFAULT 0 COMMENT '계획 휴게시간(분)',
    status VARCHAR(20) NOT NULL DEFAULT 'SUBMITTED' COMMENT '상태 (SUBMITTED, CONFIRMED, REJECTED)',
    staff_memo VARCHAR(255) NULL COMMENT '알바생 메모',
    owner_memo VARCHAR(255) NULL COMMENT '점주 메모',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_shift_store_date (store_id, work_date),
    INDEX idx_shift_staff_date (store_staff_id, work_date),
    CONSTRAINT fk_shift_store FOREIGN KEY (store_id) REFERENCES store (store_id),
    CONSTRAINT fk_shift_staff_store FOREIGN KEY (store_staff_id, store_id) REFERENCES store_staff (store_staff_id, store_id),
    CONSTRAINT chk_shift_break CHECK (planned_break_minutes >= 0),
    CONSTRAINT chk_shift_status CHECK (status IN ('SUBMITTED', 'CONFIRMED', 'REJECTED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 5. 출퇴근 근태 기록 (타임카드 및 일별 실근무·야간·연장 분 단위 산출)
CREATE TABLE IF NOT EXISTS attendance (
    attendance_id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '근태 PK',
    store_id BIGINT NOT NULL COMMENT '점포 FK',
    store_staff_id BIGINT NOT NULL COMMENT '점포 직원 FK',
    shift_id BIGINT NULL COMMENT '연결된 확정 시프트 FK (미계획 출근 시 NULL)',
    work_date DATE NOT NULL COMMENT '출근 기준일',
    clock_in_at DATETIME NOT NULL COMMENT '출근 타각 일시',
    clock_out_at DATETIME NULL COMMENT '퇴근 타각 일시',
    break_start_at DATETIME NULL COMMENT '현재 진행 중 휴게의 시작 시각(ON_BREAK 상태에서만 사용)',
    break_end_at DATETIME NULL COMMENT '마지막 완료 휴게 종료 시각(표시용; 합산은 attendance_break 기준)',
    total_break_minutes INT NOT NULL DEFAULT 0 COMMENT '완료된 모든 휴게 구간 합계(분; 파생값)',
    net_work_minutes INT NOT NULL DEFAULT 0 COMMENT '실근무시간(분, 휴게 제외)',
    night_work_minutes INT NOT NULL DEFAULT 0 COMMENT '야간근로시간(분, 22:00~05:00)',
    overtime_minutes INT NOT NULL DEFAULT 0 COMMENT '연장잔업시간(분, 1일 8시간=480분 초과분)',
    clock_in_method VARCHAR(20) NOT NULL DEFAULT 'BUTTON' COMMENT '타각 방식 (BUTTON, QR, MANUAL_APPROVED)',
    attendance_status VARCHAR(20) NOT NULL DEFAULT 'WORKING' COMMENT '상태 (WORKING, ON_BREAK, COMPLETED, MOD_REQUESTED)',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_att_store_date (store_id, work_date),
    INDEX idx_att_staff_date (store_staff_id, work_date),
    CONSTRAINT fk_att_store FOREIGN KEY (store_id) REFERENCES store (store_id),
    CONSTRAINT fk_att_staff_store FOREIGN KEY (store_staff_id, store_id) REFERENCES store_staff (store_staff_id, store_id),
    CONSTRAINT fk_att_shift FOREIGN KEY (shift_id) REFERENCES shift_schedule (shift_id),
    CONSTRAINT chk_attendance_minutes CHECK (total_break_minutes >= 0 AND net_work_minutes >= 0 AND night_work_minutes >= 0 AND overtime_minutes >= 0),
    CONSTRAINT chk_attendance_status CHECK (attendance_status IN ('WORKING', 'ON_BREAK', 'COMPLETED', 'MOD_REQUESTED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 5-A. 근태별 휴게 구간(하루 여러 번의 휴게를 모두 보존)
CREATE TABLE IF NOT EXISTS attendance_break (
    attendance_break_id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '휴게 구간 PK',
    attendance_id BIGINT NOT NULL COMMENT '근태 FK',
    break_start_at DATETIME NOT NULL COMMENT '휴게 시작 일시',
    break_end_at DATETIME NOT NULL COMMENT '휴게 종료 일시',
    duration_minutes INT NOT NULL COMMENT '휴게 구간 길이(분)',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_break_attendance (attendance_id, break_start_at),
    CONSTRAINT fk_break_attendance FOREIGN KEY (attendance_id) REFERENCES attendance (attendance_id),
    CONSTRAINT chk_break_range CHECK (break_end_at > break_start_at),
    CONSTRAINT chk_break_duration CHECK (duration_minutes > 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 6. 근태 수정 요청 (타각 누락·오류 수정 및 점주 승인 감사 이력)
CREATE TABLE IF NOT EXISTS attendance_mod_request (
    mod_request_id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '근태 수정 요청 PK',
    attendance_id BIGINT NULL COMMENT '대상 근태 FK (타각 완전 누락 사후 생성 시 NULL 허용)',
    store_id BIGINT NOT NULL COMMENT '점포 FK (사후 신규 생성 시 필수)',
    store_staff_id BIGINT NOT NULL COMMENT '요청 직원 FK',
    work_date DATE NOT NULL COMMENT '근무 기준일 (사후 신규 생성 시 필수)',
    before_clock_in_at DATETIME NULL COMMENT '수정 전 출근 일시 (사후 신규 생성 시 NULL 허용)',
    before_clock_out_at DATETIME NULL COMMENT '수정 전 퇴근 일시',
    before_break_minutes INT NOT NULL DEFAULT 0 COMMENT '수정 전 휴게시간(분)',
    requested_clock_in_at DATETIME NOT NULL COMMENT '수정 요청 출근 일시',
    requested_clock_out_at DATETIME NOT NULL COMMENT '수정 요청 퇴근 일시',
    requested_break_minutes INT NOT NULL DEFAULT 0 COMMENT '수정 요청 휴게시간(분)',
    reason VARCHAR(500) NOT NULL COMMENT '수정 요청 사유',
    approval_status VARCHAR(20) NOT NULL DEFAULT 'PENDING' COMMENT '상태 (PENDING, APPROVED, REJECTED)',
    reviewer_id BIGINT NULL COMMENT '승인/반려 처리한 점주 회원 FK',
    reject_reason VARCHAR(500) NULL COMMENT '반려 사유',
    reviewed_at DATETIME NULL COMMENT '승인/반려 처리 일시',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_mod_attendance FOREIGN KEY (attendance_id) REFERENCES attendance (attendance_id),
    CONSTRAINT fk_mod_store FOREIGN KEY (store_id) REFERENCES store (store_id),
    CONSTRAINT fk_mod_staff_store FOREIGN KEY (store_staff_id, store_id) REFERENCES store_staff (store_staff_id, store_id),
    CONSTRAINT fk_mod_reviewer FOREIGN KEY (reviewer_id) REFERENCES member (member_id),
    CONSTRAINT chk_mod_status CHECK (approval_status IN ('PENDING', 'APPROVED', 'REJECTED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 6-A. 수정 요청의 휴게 구간 스냅샷(승인 전·요청값을 감사 목적으로 보존)
CREATE TABLE IF NOT EXISTS attendance_mod_break (
    mod_break_id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '수정 요청 휴게 구간 PK',
    mod_request_id BIGINT NOT NULL COMMENT '수정 요청 FK',
    break_set_type VARCHAR(10) NOT NULL COMMENT 'BEFORE 또는 REQUESTED',
    break_start_at DATETIME NOT NULL,
    break_end_at DATETIME NOT NULL,
    duration_minutes INT NOT NULL,
    CONSTRAINT fk_mod_break_request FOREIGN KEY (mod_request_id) REFERENCES attendance_mod_request (mod_request_id),
    CONSTRAINT chk_mod_break_type CHECK (break_set_type IN ('BEFORE', 'REQUESTED')),
    CONSTRAINT chk_mod_break_range CHECK (break_end_at > break_start_at),
    CONSTRAINT chk_mod_break_duration CHECK (duration_minutes > 0),
    INDEX idx_mod_break_request (mod_request_id, break_set_type)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 7. 월별 급여 정산 (적용 규칙에 따른 계산 스냅샷 및 엑셀 출력 원본)
CREATE TABLE IF NOT EXISTS payroll (
    payroll_id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '급여 정산 PK',
    store_id BIGINT NOT NULL COMMENT '점포 FK',
    store_staff_id BIGINT NOT NULL COMMENT '점포 직원 FK',
    target_year_month CHAR(7) NOT NULL COMMENT '정산 표시 연월 (YYYY-MM)',
    range_start_date DATE NOT NULL COMMENT '실제 정산 집계 시작일(포함)',
    range_end_date DATE NOT NULL COMMENT '실제 정산 집계 종료일(포함)',
    work_days_count INT NOT NULL DEFAULT 0 COMMENT '총 출근 일수',
    total_work_minutes INT NOT NULL DEFAULT 0 COMMENT '월 총 실근무시간(분)',
    total_night_minutes INT NOT NULL DEFAULT 0 COMMENT '월 총 야간근로시간(분)',
    total_overtime_minutes INT NOT NULL DEFAULT 0 COMMENT '월 총 연장근로시간(분)',
    applied_hourly_wage INT NULL COMMENT '정산 기간에 시급이 한 가지인 경우 표시용 스냅샷; 복수 시급이면 NULL',
    applied_transport_limit INT NOT NULL DEFAULT 0 COMMENT '정산 시 사용한 회사 통근수당 지급 상한 스냅샷',
    base_pay INT NOT NULL DEFAULT 0 COMMENT '기본급 (일별 적용 시급 기준 100%)',
    night_allowance INT NOT NULL DEFAULT 0 COMMENT '야간 근로 할증 수당 (25% 가산분)',
    overtime_allowance INT NOT NULL DEFAULT 0 COMMENT '연장 잔업 할증 수당 (25% 가산분)',
    transport_allowance INT NOT NULL DEFAULT 0 COMMENT '교통비 지급액 (월 상한액 적용 후)',
    gross_pay INT NOT NULL DEFAULT 0 COMMENT '총 지급액',
    payroll_status VARCHAR(20) NOT NULL DEFAULT 'DRAFT' COMMENT '상태 (DRAFT, CONFIRMED, PAID)',
    confirmed_at DATETIME NULL COMMENT '정산 확정 일시',
    paid_at DATETIME NULL COMMENT '지급 처리 일시(선택 기능)',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT uq_payroll_staff_month UNIQUE (store_staff_id, target_year_month),
    CONSTRAINT fk_payroll_store FOREIGN KEY (store_id) REFERENCES store (store_id),
    CONSTRAINT fk_payroll_staff_store FOREIGN KEY (store_staff_id, store_id) REFERENCES store_staff (store_staff_id, store_id),
    CONSTRAINT chk_payroll_range CHECK (range_end_date >= range_start_date),
    CONSTRAINT chk_payroll_status CHECK (payroll_status IN ('DRAFT', 'CONFIRMED', 'PAID'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 7-A. 급여 확정 시점의 일별 상세 스냅샷(확정 명세서와 근태 변경 후 값의 불일치 방지)
CREATE TABLE IF NOT EXISTS payroll_daily_detail (
    payroll_daily_detail_id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '급여 일별 상세 PK',
    payroll_id BIGINT NOT NULL COMMENT '급여 정산 FK',
    attendance_id BIGINT NOT NULL COMMENT '원본 근태 FK',
    work_date DATE NOT NULL COMMENT '근무일',
    applied_hourly_wage INT NOT NULL COMMENT '해당 근무일 적용 시급 스냅샷',
    total_break_minutes INT NOT NULL DEFAULT 0,
    net_work_minutes INT NOT NULL DEFAULT 0,
    night_work_minutes INT NOT NULL DEFAULT 0,
    overtime_minutes INT NOT NULL DEFAULT 0,
    base_pay INT NOT NULL DEFAULT 0,
    night_allowance INT NOT NULL DEFAULT 0,
    overtime_allowance INT NOT NULL DEFAULT 0,
    transport_amount INT NOT NULL DEFAULT 0 COMMENT '회사 지급 규칙으로 일자별 배분한 통근수당',
    CONSTRAINT fk_payroll_detail_payroll FOREIGN KEY (payroll_id) REFERENCES payroll (payroll_id),
    CONSTRAINT fk_payroll_detail_attendance FOREIGN KEY (attendance_id) REFERENCES attendance (attendance_id),
    CONSTRAINT uq_payroll_detail_attendance UNIQUE (payroll_id, attendance_id),
    INDEX idx_payroll_detail_date (payroll_id, work_date)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
```

### 2.2 선택 확장 DDL (실제 확장 기능을 구현할 때만 실행)
```sql
-- LINE 알림 기능을 선택했을 때만 추가
ALTER TABLE member ADD COLUMN line_user_id VARCHAR(100) NULL UNIQUE COMMENT 'LINE Messaging API User ID';

-- 피크타임 최소 인원 경고를 구현할 때만 추가
ALTER TABLE store ADD COLUMN min_peak_staff INT NOT NULL DEFAULT 3 COMMENT '피크타임(18~22시) 최소 권장 인원';
```

**초기화 주의:** `CREATE TABLE IF NOT EXISTS`는 기존 테이블을 새 구조로 자동 변경하지 않습니다. 스키마를 바꾼 뒤에는 개발 DB를 재생성하거나 별도 마이그레이션 SQL을 실행해야 합니다. 팀 공용 DB에서는 데이터를 보존해야 하는지 먼저 확인하고 임의로 DROP하지 않습니다. `data.sql`에 `ACTIVE` 직원의 `store_staff`를 넣는 경우에는 해당 `joined_date`부터 유효한 `store_staff_contract_history` 레코드도 함께 넣어야 급여 테스트가 가능합니다. 테스트용 계약 이력이 빠진 직원은 급여 산정에서 임의의 기본 시급으로 대체하지 말고 데이터 오류로 처리합니다.

---

## 제3장. [도메인 1] 회원 인증 및 점포·계약 마스터 (`member`, `store`, `store_staff`)

> **담당**: 공통 업무 스트림 A(팀 구성에 따라 담당자 지정)  
> **패키지**: `com.example.kintai.domain.member`, `com.example.kintai.domain.store`

### 3.1 도메인 개론 및 설계 의도
* **왜 회원과 점포 소속을 분리했는가?**: `member`는 로그인 계정이며, `store_staff`는 특정 매장과의 소속 관계입니다. 시급·통근수당 계약 조건은 `store_staff_contract_history`에서 적용 기간별로 보존하고, `store_staff`의 현재 표시용 필드는 동기화합니다.
* **초대 코드(`invite_code`)와 QR 토큰(`qr_token`)**: 점포 생성 시 예측하기 어려운 코드를 생성하고 DB에서 고유성을 보장합니다. 코드는 소속 초대 및 데모 인증의 보조 수단이며, QR 토큰 하나만으로 실제 매장 위치나 대리 타각을 완전히 방지한다고 주장하지 않습니다.

### 3.2 화면(View) 라우팅 및 UI 명세
1. **`GET /login` → `templates/member/login.html` & `GET /join` → `templates/member/join.html`**
   * 이메일/비밀번호 로그인 화면 및 회원가입. 운영 정책상 점주 가입과 직원 가입 권한을 구분하며, 클라이언트가 보낸 `role`을 무조건 신뢰하지 않습니다.
2. **`GET /stores` → `templates/store/manage.html`**
   * **Model 바인딩**:
     * `store`: 현재 선택된 점포 상세 정보 (`StoreDto`)
     * `staffList`: 해당 점포에 소속된 알바생 계약 목록 (`List<StoreStaffDetailDto>` - `member.name`, `member.email`, `member.phone` 조인)
   * **주요 UI 구성**:
     * 상단 카드: 매장명, 영업시간(`openTime ~ closeTime`), 급여 마감일, **초대 코드 복사 버튼**, **출퇴근 QR 코드(또는 토큰) 모달 보기 버튼**, **[신규 점포 개설] 모달 버튼**.
     * 하단 테이블: 소속 알바생 목록 (이름, 이메일, 연락처, **기본 시급**, **1일 왕복 교통비**, **월 교통비 상한액**, 재직상태, 입사일, **[계약 조건 수정] 버튼**).
     * 우측 상단 보조 버튼: **[초대 코드로 매장 합류하기(알바생 합류 신청)] 모달 버튼**.

### 3.3 REST API 상세 명세

#### ① 회원가입 (`POST /api/members/join`) 및 로그인 (`POST /api/members/login`)
  * **회원가입 Request JSON (`MemberJoinRequestDto`)**:
  ```json
  {
    "email": "staff3@kintai.com",
    "password": "Password123!",
    "name": "高橋 健太",
    "phone": "080-9999-8888"
  }
  ```
  * 가입 시 권한은 서버의 가입 정책에 따라 결정합니다. 직원은 초대·소속 흐름으로 연결하며, 일반 요청의 `role` 값을 그대로 권한으로 저장하지 않습니다.
  * `PasswordEncoder.encode(password)`로 해시를 저장합니다. 비밀번호 평문은 로그·응답에 포함하지 않습니다.
* **로그인 Request JSON (`MemberLoginRequestDto`)**:
  ```json
  {
    "email": "owner@kintai.com",
    "password": "테스트용 비밀번호"
  }
  ```
  * Spring Security 인증 흐름을 사용하고, 인증 성공 시 인증 컨텍스트 및 안전한 세션을 사용합니다. 자체 `LOGIN_MEMBER` 세션만 저장하고 Spring Security 로그인까지 완료되었다고 간주하지 않습니다.

#### ② 신규 점포 개설 (`POST /api/stores`)
* **Request JSON (`StoreCreateRequestDto`)**:
  ```json
  {
    "storeName": "東京 渋谷2号店",
    "prefectureCode": "13",
    "openTime": "10:00",
    "closeTime": "23:30",
    "payCutoffDay": 25
  }
  ```
* **서버 처리 로직**:
  1. `inviteCode`: `ST` 접두사와 안전한 난수 기반 코드를 생성하고, 길이·문자 집합을 문서 내 예시와 동일하게 유지합니다. DB UNIQUE 충돌이 발생하면 새 코드를 생성해 재시도합니다.
  2. `qrToken`: `"TOKEN_QR_" + UUID.randomUUID().toString()` 생성.
  3. `openTime`, `closeTime`을 `LocalTime`으로 변환하여 `store` 테이블에 INSERT (점주 ID는 요청값이 아니라 로그인된 사용자의 인증 정보에서 가져옴).

#### ③ 알바생 초대 코드로 매장 합류 (`POST /api/stores/join-staff`)
* **Request JSON (`StaffJoinRequestDto`)**:
  ```json
  {
    "inviteCode": "ST7K4P2Q"
  }
  ```
  *(보안 규칙: 합류 요청에는 초대 코드만 받으며 회원 ID는 인증 정보에서 가져옵니다. 새 소속은 `PENDING`으로 생성하고 점주가 계약 조건을 승인한 뒤 `ACTIVE`로 변경합니다. 테스트 편의를 이유로 실제 API에서 `ACTIVE`와 `PENDING`을 임의 선택하지 않습니다. 시급은 매장 지역과 적용일 기준의 최저임금 설정을 검증합니다.)*
* **서버 처리 로직**:
  1. `StoreMapper.findByInviteCode(inviteCode)`로 점포 조회 (없으면 `IllegalArgumentException("無効な招待コードです。")`).
  2. `StoreMapper.existsStoreStaff(storeId, memberId)`로 이미 등록된 직원인지 중복 체크.
  3. `joined_date = LocalDate.now()`로 `store_staff`를 `PENDING` 상태로 INSERT합니다. 이 단계에서 기본 시급 값은 승인 전 표시·검증용 값이며 급여 정산 계약으로 취급하지 않습니다. 점주가 ④ API로 계약을 승인할 때 `store_staff_contract_history`에 최초 유효 계약을 INSERT하고 `ACTIVE`로 전환합니다. 실제 API의 초기 시급은 매장 지역·적용일 기준 최저임금 검증을 통과해야 합니다.

#### ④ 알바생 시급·교통비 계약 조건 수정 및 승인 (`PUT /api/stores/staff/{storeStaffId}`)
* **Request JSON (`StaffContractUpdateDto`)**:
  ```json
  {
    "effectiveFrom": "2026-11-01",
    "hourlyWage": 1350,
    "dailyTransportFee": 600,
    "monthlyTransportLimit": 15000,
    "employmentStatus": "ACTIVE"
  }
  ```
* **서버 처리 로직**:
  * 매장 `prefecture_code`와 계약 적용일 기준으로 최소임금을 검증합니다. 이번 MVP에서 도쿄도만 지원한다면 도쿄도 2026년 10월 1일 이후 시간급 1,280엔을 예시 설정값으로 두고, 그 이전 기간 및 다른 지역까지 자동 지원한다고 설명하지 않습니다. 공식 출처: https://jsite.mhlw.go.jp/tokyo-roudoukyoku/news_topics/houdou/20260930chinginka_00001.html
  * `effectiveFrom`은 계약 적용 시작일입니다. 최초 승인 시 계약 이력을 INSERT합니다. 변경 시에는 기존 적용 기간을 덮어쓰지 않고, 기존 이력의 `effective_to`를 새 `effectiveFrom`으로 닫은 뒤 새 이력을 추가합니다. 날짜가 기존 이력과 겹치거나 중간 구간의 계약 이력이 모호해지면 요청을 거절합니다. 이력 갱신과 `store_staff`의 현재 표시용 필드 동기화는 한 트랜잭션에서 처리합니다.
  * 확정 급여는 `payroll` 및 `payroll_daily_detail` 스냅샷을 유지합니다. 확정 급여 대상 근태 수정은 기본 흐름에서 차단하며, 별도 조정 급여 기능이 필요하면 확장 기능으로 설계합니다.

#### ⑤ 알바생 퇴사 처리 및 효력일 무결성 (`PUT /api/stores/staff/{storeStaffId}/resign`) (F03 대응)
* **Request JSON (`StaffResignRequestDto`)**:
  ```json
  {
    "resignedDate": "2026-10-31"
  }
  ```
* **서버 처리 로직 (효력일과 재직 상태의 충돌 방지)**:
  * **선행 검증**: 대상 직원은 현재 `ACTIVE` 상태여야 하며, `resignedDate >= joined_date`여야 합니다. 또한 퇴사일 이후(`work_date > resignedDate`)에 등록된 근태나 미래 시프트가 존재하면 `409 Conflict`로 거절하고 먼저 정리하도록 안내합니다.
  * **미래 퇴사 등록 시 상태 전이 원칙 (F03 방어)**:
    - 퇴사일을 미래 날짜로 지정할 경우, 즉시 `employment_status = 'RESIGNED'`로 바꾸면 남은 재직 기간 동안 출퇴근 타각 및 시프트 제출이 차단되는 치명적 모순이 발생합니다.
    - 따라서 **MVP 규칙**: `resignedDate`는 당일(`LocalDate.now()`) 또는 과거일만 입력 가능하도록 검증하여 즉시 정상 퇴사 처리합니다(`resignedDate <= LocalDate.now()`). 만약 미래 퇴사 예약을 지원할 경우에는 상태를 즉시 `RESIGNED`로 바꾸지 않고 `resigned_date`만 예약 저장하며, 실제 타각 차단은 서버 시각이 `today > resigned_date`가 되는 시점에 판정하도록 상태 판정 로직을 분리합니다.
  * **계약 종료 처리**: 열려 있는 최신 계약 이력의 `effective_to`를 `resignedDate + 1일`로 안전하게 닫아 DDL 제약조건(`effective_to > effective_from`) 충돌을 방지합니다.

---

## 제4장. [도메인 2] 통합 시프트 스케줄러 (`shift_schedule` & FullCalendar)

> **담당**: 업무 스트림 B(팀 구성에 따라 담당자 지정)  
> **패키지**: `com.example.kintai.domain.shift`

### 4.1 도메인 개론 및 단일 테이블 대조 뷰 설계
* 기존 시스템들은 '희망 시프트 테이블'과 '확정 시프트 테이블'을 따로 만들어 조인 쿼리가 복잡해지고 화면에서 비교하기 어려웠습니다.
* 본 프로젝트는 `shift_schedule` 한 행(Row)에 **알바생 희망 시간(`requested_start_at`, `requested_end_at`)**과 **점주 확정 시간(`confirmed_start_at`, `confirmed_end_at`)**을 나란히 배치했습니다.
  * 알바생이 처음 제출하면: `status = 'SUBMITTED'`, `requested_*`만 값이 있고 `confirmed_*`는 `NULL`.
  * 점주가 클릭해서 확정하면: `status = 'CONFIRMED'`, `confirmed_*`에 확정 시간이 채워짐 (초기값은 `requested_*`와 동일하게 자동 세팅되어 점주가 1클릭으로 확정하거나 시간만 살짝 바꿀 수 있음).

### 4.2 화면(View) 라우팅 및 FullCalendar UI 명세
* **`GET /shifts` $\rightarrow$ `templates/shift/calendar.html`**
  * **화면 상단 컨트롤 바**:
    * 매장 선택 드롭다운, 직원 선택(알바생 테스트용), 상태 필터(`전체`, `희망제출(주황)`, `확정됨(파랑)`, `미배정(회색)`).
    * **피크타임 결원 경고 알림창**: 해당 월에 18:00~22:00 사이 확정 인원이 부족한 날짜가 있으면 상단에 `"⚠️ 10월 30일(금) 피크타임 확정 인원이 1명입니다 (권장 3명 이상)"` 배지 표시.
  * **중앙 FullCalendar 6.x 영역**:
    * **일본어 로케일 설정 필수**:
      ```html
      <!-- FullCalendar 6.x 일본어 로케일 CDN 추가 -->
      <script src="https://cdn.jsdelivr.net/npm/fullcalendar@6.1.15/locales/ja.global.min.js"></script>
      ```
      ```javascript
      // FullCalendar 초기화 옵션 필수 지정
      const calendar = new FullCalendar.Calendar(calendarEl, {
          initialView: 'dayGridMonth',
          locale: 'ja', // 100% 일본어 요일 및 달력 표기
          buttonText: { today: '今日', month: '月', week: '週' },
          headerToolbar: {
              left: 'prev,next today',
              center: 'title',
              right: 'dayGridMonth,timeGridWeek'
          },
          events: '/api/shifts/events?storeId=' + storeId,
          dateClick: function(info) { openShiftRequestModal(info.dateStr); },
          eventClick: function(info) { openShiftConfirmModal(info.event); }
      });
      ```
    * 날짜 빈 칸 클릭(`dateClick`): **[알바생 희망 시프트 제출 모달]** 오픈 (날짜 자동 입력, 출근 시각, 퇴근 시각, 알바생 메모 입력 후 `POST /api/shifts/request` 호출).
    * 등록된 일정 바 클릭(`eventClick`): **[점주 시프트 조율·확정 모달]** 오픈.
      * 모달 내부에 **"알바생 원본 희망 시간(예: 17:00 ~ 23:00)"**이 읽기 전용으로 상단에 표시되고, 바로 아래에 **"점주 확정 시간 입력칸"**과 **"계획 휴게시간(분)"**, **"점주 메모"**가 표시되어 직관적인 대조 조율이 가능함.
      * `[확정(CONFIRMED)]` 버튼과 `[미배정 반려(REJECTED)]` 버튼 배치.

### 4.3 REST API 및 핵심 검증 로직 명세

#### ① 월간 캘린더 이벤트 목록 조회 (`GET /api/shifts/events?storeId=1&start=2026-10-01&end=2026-11-01`)
* **파라미터 주의사항**: FullCalendar의 `start`와 `end`는 ISO 문자열이며 종료일 `end`는 **미포함**입니다. `substring(0, 10)`에만 의존하지 말고 ISO-8601 파싱을 사용해 날짜·시간대 오프셋을 검증합니다. 일별 `work_date` 조회를 유지한다면 `startDate <= work_date < endDate` 조건을 사용합니다. 예: `2026-10-01`부터 `2026-11-01`까지 조회하면 10월 1일부터 10월 31일까지만 포함합니다. 공식 문서: https://fullcalendar.io/docs/events-function
* **SQL 쿼리 (`ShiftMapper.xml`)**:
  ```sql
  SELECT
      s.shift_id,
      s.store_id,
      s.store_staff_id,
      m.name AS staff_name,
      s.work_date,
      s.requested_start_at,
      s.requested_end_at,
      s.confirmed_start_at,
      s.confirmed_end_at,
      s.planned_break_minutes,
      s.status,
      s.staff_memo,
      s.owner_memo
  FROM shift_schedule s
  JOIN store_staff ss ON s.store_staff_id = ss.store_staff_id
  JOIN member m ON ss.member_id = m.member_id
  WHERE s.store_id = #{storeId}
    AND s.work_date >= #{startDate}
    AND s.work_date < #{endDate} -- FullCalendar end는 미포함 경계
  ORDER BY s.work_date ASC, COALESCE(s.confirmed_start_at, s.requested_start_at) ASC
  ```
* **응답 변환 로직 (Service $\rightarrow$ FullCalendar Event DTO)**:
  * `status == 'CONFIRMED'`인 경우:
    * `start` = `confirmedStartAt`, `end` = `confirmedEndAt`
    * `color` = `"#0d6efd"` (파란색)
    * `title` = `"[확정] " + staffName + " (" + HH:mm + "~" + HH:mm + ")"`
  * `status == 'SUBMITTED'`인 경우:
    * `start` = `requestedStartAt`, `end` = `requestedEndAt`
    * `color` = `"#fd7e14"` (주황색)
    * `title` = `"[희망] " + staffName + " (" + HH:mm + "~" + HH:mm + ")"`
  * `status == 'REJECTED'`인 경우 (F08 방어):
    * 점주 직접 배정 시프트는 `requested_*`가 NULL이므로, 취소 시에도 캘린더 구간이 유실되지 않도록 `COALESCE` 방식으로 매핑합니다:
    * `start` = `(requestedStartAt != null) ? requestedStartAt : confirmedStartAt`
    * `end` = `(requestedEndAt != null) ? requestedEndAt : confirmedEndAt`
    * `color` = `"#6c757d"` (회색)
    * `title` = `"[미배정] " + staffName`

#### ② 희망 시프트 제출 (`POST /api/shifts/request`)
* **Request JSON (`ShiftRequestDto`)**:
  ```json
  {
    "storeId": 1,
    "storeStaffId": 1,
    "workDate": "2026-10-28",
    "requestedStartTime": "17:00",
    "requestedEndTime": "23:30",
    "staffMemo": "수업 끝나고 바로 출근합니다."
  }
  ```
* **시간 변환 주의사항 (야간 익일 퇴근 처리)**:
  * `requestedEndTime`이 `requestedStartTime`보다 이르면 다음 날 퇴근으로 해석합니다. 시작 시각과 종료 시각이 같으면 24시간 근무로 오인할 수 있으므로 MVP에서는 기본적으로 입력 오류로 거절합니다. 예외적으로 24시간 근무를 지원할 필요가 있다면 별도 필드/규칙을 추가합니다. `TimeUtils.resolveEndDateTime()` 사용 여부는 기존 유틸 구현을 확인하고 결정합니다.
  * 코드 공식:
    ```java
    // [F04 방어: 시프트 동시 신청 충돌 방지를 위한 비관적 락 직렬화]
    shiftMapper.lockStoreStaffForUpdate(dto.getStoreStaffId());

    LocalDate workDate = LocalDate.parse(dto.getWorkDate());
    LocalTime startLocalTime = LocalTime.parse(dto.getRequestedStartTime());
    LocalTime endLocalTime = LocalTime.parse(dto.getRequestedEndTime());
    LocalDateTime startAt = LocalDateTime.of(workDate, startLocalTime);
    LocalDate endDate = endLocalTime.isAfter(startLocalTime) ? workDate : workDate.plusDays(1);
    LocalDateTime endAt = LocalDateTime.of(endDate, endLocalTime);

    // [F07 방어: 16시간 초과 검증 정확도 보장]
    // Duration.toHours() > 16은 소수점을 절사하여 16시간 59분(1019분)을 16으로 계산해 통과시키는 치명적 버그가 발생합니다.
    // 따라서 반드시 정밀 Duration 비교 또는 분(Minutes) 단위 비교를 사용해야 합니다:
    if (Duration.between(startAt, endAt).compareTo(Duration.ofHours(16)) > 0) {
        throw new IllegalArgumentException("1回のシフト時間は最大16時間以内である必要があります。");
    }
    ```

#### ③ 점주 시프트 확정 및 일본 법정 휴게시간 자동 산정 (`PUT /api/shifts/{shiftId}/confirm`)
* **Request JSON (`ShiftConfirmDto`)**:
  ```json
  {
    "confirmedStartTime": "17:00",
    "confirmedEndTime": "23:30",
    "plannedBreakMinutes": null,
    "status": "CONFIRMED",
    "ownerMemo": "確認完了"
  }
  ```
* **일본 노동기준법 제34조 기반 휴게시간 자동 계산 및 검증 공식**:
  * 일본 노동기준법 제34조의 기본 기준은 실제 근로시간이 6시간을 초과하면 최소 45분, 8시간을 초과하면 최소 60분의 휴게를 근로 도중 부여하는 것입니다. 휴게시간은 단순히 시스템이 자동 공제했다고 해서 실제로 부여된 것으로 간주할 수 없습니다.
  * 사용자가 휴게시간을 입력한 경우: `netMinutes = grossMinutes - plannedBreakMinutes`를 계산하고 법정 최소 기준 미달 여부를 검증합니다. 기준 미달이면 임의로 조용히 보정하지 말고 관리자 확인 또는 입력 수정 오류를 반환합니다.
  * 자동 산정 정책(계획용 기본값)은 다음과 같습니다. 이는 **근태 기록에서 실제 휴게를 대신하는 값이 아니라, 시프트 계획용 추천값**입니다.
    * 총 체류시간 `grossMinutes <= 360`: 0분
    * `360 < grossMinutes <= 525`: 45분
    * `grossMinutes > 525`: 60분
  * 경계 예시: 6시간 1분 근무 예정이면 최소 45분을 안내하고, 총 체류시간 8시간 50분에 휴게 45분을 빼면 실근무가 8시간 5분이므로 60분이 필요합니다. 점주가 실제 휴게 계획을 확인하고 저장하도록 합니다.

---

## 제5장. [도메인 3] 감사추적 출퇴근 타각 및 수정 승인 워크플로우 (`attendance`, `attendance_mod_request`)

> **담당**: 업무 스트림 C(근태; 팀 구성에 따라 분담)  
> **패키지**: `com.example.kintai.domain.attendance`

### 5.1 도메인 개론 및 상태 머신(State Machine)
알바생의 하루 근태(`attendance_status`)는 아래의 엄격한 상태 전이를 따릅니다:
```text
[미출근] ──(출근 타각: CLOCK_IN)──▶ [WORKING (근무중)]
                                       │         ▲
                   (휴게 시작: BREAK_START)     (휴게 종료: BREAK_END)
                                       ▼         │
                                  [ON_BREAK (휴게중)]
                                       
[WORKING] ──(퇴근 타각: CLOCK_OUT)──▶ [COMPLETED (퇴근완료 / 일별 시간 3종 자동 산출)]
                                       │
                     (알바생 수정 신청) │
                                       ▼
                              [MOD_REQUESTED (수정대기)] ──(점주 승인/반려)──▶ [COMPLETED (재계산 반영)]
```

### 5.2 화면(View) 라우팅 및 UI 명세
1. **`GET /attendance` → `templates/attendance/punch.html`**
   * **상단 [실시간 타임카드 타각 패널]**:
     * 현재 시각 실시간 디지털 시계 (`1초 단위 JS 갱신`).
     * 현재 선택된 직원 및 오늘의 근태 상태 배지 (`未出勤` / `勤務中` / `休憩中` / `退勤済`).
     * 개발 프로필에서는 테스트용 QR 토큰을 사용할 수 있으나 기본/운영 화면에 비밀 토큰을 HTML로 미리 출력하지 않습니다. QR 인증은 시연용 보조 검증이며, 토큰만으로 실제 위치나 대리 타각을 완전히 방지한다고 표시하지 않습니다.
     * **대형 타각 버튼 4개 (1.5절 일본어 표준 용어 준수)**: `[🟢 出勤]`, `[☕ 休憩開始]`, `[☕ 休憩終了]`, `[🔴 退勤]` (현재 상태에 따라 누를 수 있는 버튼만 활성화).
   * **하단 [내 월별 출퇴근 기록표 & 수정 신청 (`勤怠修正申請`)]**:
     * 일자별 출근시각, 퇴근시각, 휴게(분), **실근무(분)**, **야간(분)**, **연장(분)**, 상태, **`[勤怠修正申請]` 버튼**.
     * `[勤怠修正申請]` 클릭 시 모달이 열리며 수정할 출퇴근 시각, 휴게 분, **수정 사유(필수)**를 입력해 제출.
2. **`GET /attendance/mod-requests` → `templates/attendance/mod-list.html` (점주용 감사 화면)**
   * 알바생들이 올린 근태 수정 요청 목록을 표로 보여줌.
   * **핵심 UI**: `[修正前(Before) 出退勤時刻]` → `[修正後(Requested) 出退勤時刻]`이 빨간색/파란색 비교 뷰로 나란히 표시되고, 알바생의 사유를 확인한 뒤 점주가 **`[承認]`** 또는 **`[却下]`** 버튼을 누름.

### 5.3 ⭐ [핵심 알고리즘 명세] 일별 근로시간 3종(실근무·야간·연장) 계산

계산 입력은 출퇴근 시각과 **완료된 모든 휴게 구간 목록**입니다. `attendance.break_start_at`은 현재 진행 중 휴게를 표시하는 데만 사용하고, 실제 휴게 합계 및 야간 휴게 차감은 `attendance_break`에 저장된 전체 구간을 기준으로 계산합니다.

잘못된 데이터는 0분으로 조용히 변환하지 않고 검증 오류로 처리합니다. 퇴근 타각 전의 `WORKING`/`ON_BREAK` 레코드는 최종 급여 계산 대상이 아닙니다.

```java
package com.example.kintai.domain.attendance.util;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public final class AttendanceTimeCalculator {
    private AttendanceTimeCalculator() {}

    public record BreakInterval(LocalDateTime startAt, LocalDateTime endAt) {}

    public record WorkTimeResult(
            int grossMinutes,
            int totalBreakMinutes,
            int netWorkMinutes,
            int nightWorkMinutes,
            int overtimeMinutes) {}

    public static WorkTimeResult calculate(
            LocalDateTime clockIn,
            LocalDateTime clockOut,
            List<BreakInterval> breakIntervals) {
        if (clockIn == null || clockOut == null || !clockOut.isAfter(clockIn)) {
            throw new IllegalArgumentException("出退勤時刻を正しく入力してください。");
        }
        if (breakIntervals == null) {
            throw new IllegalArgumentException("休憩記録がありません。空リストを指定してください。");
        }

        int grossMinutes = Math.toIntExact(Duration.between(clockIn, clockOut).toMinutes());
        List<BreakInterval> sorted = new ArrayList<>(breakIntervals);
        for (BreakInterval interval : sorted) {
            if (interval == null || interval.startAt() == null || interval.endAt() == null) {
                throw new IllegalArgumentException("休憩時間帯を正しく入力してください。");
            }
        }
        sorted.sort(Comparator.comparing(BreakInterval::startAt));

        int totalBreakMinutes = 0;
        int nightBreakMinutes = 0;
        LocalDateTime previousEnd = null;
        for (BreakInterval interval : sorted) {
            if (interval == null || interval.startAt() == null || interval.endAt() == null
                    || !interval.endAt().isAfter(interval.startAt())) {
                throw new IllegalArgumentException("休憩時間帯を正しく入力してください。");
            }
            if (interval.startAt().isBefore(clockIn) || interval.endAt().isAfter(clockOut)) {
                throw new IllegalArgumentException("休憩時間は出勤から退勤までの範囲内にしてください。");
            }
            if (previousEnd != null && interval.startAt().isBefore(previousEnd)) {
                throw new IllegalArgumentException("休憩時間帯が重複しています。");
            }
            int duration = Math.toIntExact(Duration.between(interval.startAt(), interval.endAt()).toMinutes());
            totalBreakMinutes = Math.addExact(totalBreakMinutes, duration);
            nightBreakMinutes = Math.addExact(nightBreakMinutes,
                    overlapNightMinutes(interval.startAt(), interval.endAt()));
            previousEnd = interval.endAt();
        }

        int netWorkMinutes = grossMinutes - totalBreakMinutes;
        if (netWorkMinutes < 0) {
            throw new IllegalArgumentException("休憩時間が総滞在時間を超えています。");
        }
        int grossNightMinutes = overlapNightMinutes(clockIn, clockOut);
        int nightWorkMinutes = Math.max(0, grossNightMinutes - nightBreakMinutes);
        int overtimeMinutes = Math.max(0, netWorkMinutes - 480);

        return new WorkTimeResult(grossMinutes, totalBreakMinutes, netWorkMinutes,
                nightWorkMinutes, overtimeMinutes);
    }

    // [start, end)와 각 날짜의 22:00~다음 날 05:00 구간의 교집합을 분 단위로 합산
    private static int overlapNightMinutes(LocalDateTime start, LocalDateTime end) {
        int total = 0;
        LocalDate first = start.toLocalDate().minusDays(1);
        LocalDate last = end.toLocalDate();
        for (LocalDate date = first; !date.isAfter(last); date = date.plusDays(1)) {
            LocalDateTime windowStart = LocalDateTime.of(date, LocalTime.of(22, 0));
            LocalDateTime windowEnd = LocalDateTime.of(date.plusDays(1), LocalTime.of(5, 0));
            LocalDateTime overlapStart = start.isAfter(windowStart) ? start : windowStart;
            LocalDateTime overlapEnd = end.isBefore(windowEnd) ? end : windowEnd;
            if (overlapEnd.isAfter(overlapStart)) {
                total = Math.addExact(total,
                        Math.toIntExact(Duration.between(overlapStart, overlapEnd).toMinutes()));
            }
        }
        return total;
    }
}
```

**필수 단위 테스트**(예상값은 완결된 휴게 구간이 입력된 경우):
- 18:00부터 23:00까지, 휴게 없음: 총 300분, 야간 60분, 연장 0분
- 18:00부터 다음 날 06:00까지, 21:00부터 21:30까지와 23:00부터 23:30까지 휴게: 총 체류 720분, 휴게 60분, 실근무 660분, 야간 실근로 390분, 일별 8시간 초과분 180분
- 휴게가 출퇴근 범위를 벗어나거나 서로 겹침: 계산 결과 대신 검증 오류
- 퇴근 시각이 출근 시각보다 빠름: 검증 오류

이 계산기는 일별 실제 근무 분류의 기초입니다. 주 40시간 기준, 법정휴일, 월 60시간 초과 시간외근로 및 법률상 예외는 별도 급여 규칙이 필요하며 이 메서드 하나로 전부 계산했다고 간주하지 않습니다.

### 5.4 REST API 상세 명세

1. **실시간 타각 (`POST /api/attendance/punch`)**:
   * Request: `{ "storeId": 1, "storeStaffId": 1, "qrToken": "TOKEN_QR_SHINJUKU_001", "punchType": "CLOCK_IN" }`
   * **[핵심 로직 1: 중복 출근 방지, 확정 급여 기간 보호, 시프트 자동 매핑]**:
     * `punchType == 'CLOCK_IN'` 시, 해당 직원에게 아직 퇴근 처리되지 않은 진행 중 근태(`attendance_status IN ('WORKING', 'ON_BREAK')`)가 존재하면 중복 출근을 차단(`IllegalStateException("既に出勤打刻が存在します。")`)합니다.
     * **[F01 방어: 확정 급여 기간 내 신규 출근 차단]**: 당일(`work_date = today`)이 포함된 해당 직원의 급여 정산 데이터가 이미 `CONFIRMED` 또는 `PAID` 상태로 잠겨 있는 경우, 출근 타각을 즉시 차단(`409 Conflict`, `"対象期間の給与は既に確定済みです。"`)하여 확정된 장부의 불변성을 보장합니다.
     * 당일(`work_date = today`) 해당 직원의 `shift_schedule` 중 `status = 'CONFIRMED'`인 시프트를 조회하여 존재하면 `attendance.shift_id`에 자동으로 외래키를 연결합니다 (미배정 출근 시 `NULL`).
   * **[핵심 로직 2: 다회 휴게 누적 및 퇴근 계산]**:
     * `BREAK_START`는 `WORKING` 상태에서만 허용하고, `BREAK_END`는 `ON_BREAK` 상태에서만 허용합니다. `BREAK_END` 시 `attendance_break`에 완료된 휴게 구간을 INSERT하고 `total_break_minutes`를 전체 구간의 합계로 갱신합니다. 한 쌍의 컬럼만으로 여러 휴게를 덮어쓰지 않습니다.
     * `CLOCK_OUT`은 진행 중 휴게가 없는 `WORKING` 상태에서만 허용합니다. `ON_BREAK` 상태에서 퇴근을 누르면 휴게 종료를 먼저 처리하도록 안내하고, 서버는 임의로 휴게 종료 시각을 만들어내지 않습니다. 완료 후 전체 `attendance_break` 구간을 불러 `AttendanceTimeCalculator.calculate()`를 호출해 분 단위 결과를 저장하고 `attendance_status = 'COMPLETED'`로 변경합니다.
     * 동시에 여러 출근 요청이 들어오는 경우에도 중복 근태가 생성되지 않도록 트랜잭션과 필요한 잠금/동시성 제어를 적용합니다.
2. **근태 수정 요청 제출 (`POST /api/attendance/mod-requests`)**:
   * Request (`AttendanceModRequestDto`) 예시(실제 구현에서는 인증된 회원 ID를 요청 본문에 넣지 않음):
     ```json
     {
       "attendanceId": 1,
       "workDate": "2026-10-27",
       "requestedClockInAt": "2026-10-27T18:00:00",
       "requestedClockOutAt": "2026-10-28T01:00:00",
       "requestedBreaks": [
         {"startAt": "2026-10-27T21:00:00", "endAt": "2026-10-27T21:30:00"},
         {"startAt": "2026-10-27T23:00:00", "endAt": "2026-10-27T23:15:00"}
       ],
       "reason": "閉店後の清掃で退勤打刻を忘れました。"
     }
     ```
     `requestedBreaks`는 실제 휴게 시작·종료 구간 목록입니다. 총 분만 전달하고 야간 휴게 구간을 추정하는 방식은 정식 급여 계산에서 사용하지 않습니다.
   * **[핵심 로직 3: 타각 완전 누락 시 사후 생성 처리]**:
     * `attendanceId != null`: 기존 근태의 출퇴근 값과 모든 휴게 구간을 `BEFORE` 스냅샷으로 `attendance_mod_break`에 보관하고 요청 휴게 구간은 `REQUESTED`로 저장합니다. 대기 중에는 해당 근태를 급여 대상에서 제외합니다.
     * `attendanceId == null`(기록 자체가 없는 날): 요청 DTO에 출퇴근 시각과 `REQUESTED` 휴게 구간 목록을 받아 `PENDING` 요청을 만듭니다. 직원은 본인 기록만 신청할 수 있고 매장·직원 ID는 인증 정보와 매핑을 검증합니다.
3. **점주 승인/반려 처리 (`PUT /api/attendance/mod-requests/{modRequestId}/review`)**:
   * Request: `{ "action": "APPROVE", "rejectReason": null }`. 승인자 ID는 로그인된 점주 인증 정보에서 가져옵니다.
   * 승인(`APPROVE`) 시:
      * **[F01 & F04 & F05 방어: 확정 급여 충돌 검증, 직렬화 락, 날짜 이동 규칙]**:
        * 트랜잭션 시작 시 동일 `store_staff` 행을 비관적 락(`SELECT ... FOR UPDATE`)으로 잠가 급여 확정과의 경쟁(Race Condition)을 원천 차단합니다.
        * 대상 근태의 근무일(`work_date`)이 속한 급여가 이미 `CONFIRMED` 또는 `PAID`이면 승인을 즉시 거절(`409 Conflict`)합니다.
        * **[F05 출근일 변경 규칙]**: 출근 시각 변경으로 인해 출근일(`work_date`) 자체가 달라지는 경우(예: 11월 1일 00:05 출근을 10월 31일 23:55로 정정), MVP 정책상 출근일 자체를 변경하는 수정 신청은 거절합니다. 만약 날짜 이동을 허용할 경우 원래 날짜(`before.work_date`)와 새 날짜(`requested.work_date`) 양쪽 모두 확정 급여가 존재하지 않아야 하며, 양쪽 일자의 일별 연장근로시간을 모두 재계산합니다.
     * 기존 `attendanceId`가 있으면 수정 전 값은 요청 이력에 보존하고 요청한 출퇴근·휴게 구간을 적용하여 시간을 재계산합니다. 기존 `attendance_break`는 승인된 요청 구간으로 교체하되, BEFORE/REQUESTED 이력은 `attendance_mod_break`에 남깁니다.
     * `attendanceId`가 없는 신규 생성 요청이면 `attendance` INSERT 후 `attendance_id`를 요청 레코드에 연결합니다.
     * 승인 시 `clock_in_method = 'MANUAL_APPROVED'`, 근태 상태는 `COMPLETED`가 됩니다. 반려 시 기존 근태는 `COMPLETED`로 복구하고, 신규 생성 요청이었다면 근태를 만들지 않고 요청 기록을 `REJECTED`로 보존합니다. 모든 변경은 한 트랜잭션에서 처리합니다.

---

## 제6장. [도메인 4] 일본 노동기준법 급여 정산 엔진 및 Apache POI 엑셀 대장 (`payroll`)

> **담당**: 업무 스트림 D(급여·출력; 팀 구성에 따라 분담)  
> **패키지**: `com.example.kintai.domain.payroll`

### 6.1 도메인 개론 및 법리적 설계 포인트
1. **왜 기본급(100%) + 야간(25%) + 연장(25%)으로 분리 가산하는가?**
   * 만약 주간 시간(100%), 야간 시간(125%), 야간+연장 시간(150%)을 구간별로 쪼개서 계산하면 수식이 극도로 복잡해집니다.
   * 반면, **① 전체 실근무시간에 대해 기본 시급(100%)을 먼저 주고, ② 야간 근로분에 대해 할증분(+25%)만 추가로 더하고, ③ 1일 8시간 초과 연장 근로분에 대해 할증분(+25%)만 추가로 더하면**, 야간이면서 동시에 연장인 구간은 자동으로 `100% + 25% + 25% = 150%`가 되어 야간과 일별 시간외가 겹치는 구간을 이 MVP의 두 할증 항목으로 분리해 계산할 수 있습니다. 다만 이 방식만으로 다른 주별·휴일·월별 할증 규칙까지 모두 처리하는 것은 아닙니다.
   * **MVP 범위**: 일별 8시간 초과 시간외근로의 기본 할증과 22:00~05:00 야간 할증을 별도 구성 요소로 계산합니다. 주 40시간 초과, 법정휴일근로, 월 60시간 초과 시간외근로, 적용 예외 및 각종 세무 처리는 별도 규칙이므로 명확히 구현하지 않았다면 지원한다고 주장하지 않습니다.
   * 계약 조건은 `store_staff_contract_history`의 적용일을 기준으로 조회합니다. 월중에 시급이나 통근수당 조건이 바뀌면 각 근무일에 유효한 계약을 적용하고 `payroll_daily_detail`에 일별 스냅샷을 보관합니다. `payroll.applied_hourly_wage`는 정산 기간 전체에 시급이 하나일 때만 표시하며 복수 시급이면 `NULL`로 두고 UI에는 `複数`로 표시합니다.
2. **점포별 급여 마감일(`pay_cutoff_day`) 대응**:
   * 일본 매장은 `말일 마감(99)`, `15일 마감(15)`, `25일 마감(25)`, `28일 마감(28)` 등 매장마다 급여 산정 기간이 다릅니다.

### 6.2 ⭐ [핵심 알고리즘 명세] 급여 마감일 및 일별 시급 적용 계산

급여 계산은 기간 전체에 단일 시급을 곱하는 방식으로 구현하지 않습니다. `COMPLETED` 근태별로 근무일에 유효한 `store_staff_contract_history`를 조회하고, 해당 일자의 시급·근로시간을 계산한 뒤 합산합니다. 월별 통근수당은 회사 지급 규칙에 따라 별도로 계산합니다.

아래 예시 코드는 **일별 기본급과 MVP 범위의 야간·일별 시간외 할증**을 계산합니다. 주 40시간 초과, 법정휴일, 월 60시간 초과 시간외근로 등은 이 코드에 포함되지 않으며, 모든 일본 법정 급여 상황을 충족한다는 보장은 없습니다. `HALF_UP` 정수 엔 반올림은 테스트 가능한 프로젝트 정책의 예시일 뿐, 모든 법률 상황에 적용되는 보편 규칙이라는 뜻이 아닙니다.

서비스 구현 규칙:
1. `pay_cutoff_day`가 99이면 표시 연월의 1일부터 말일까지, 1~28이면 전월의 마감일 다음 날부터 해당 월의 마감일까지를 포함해 조회합니다.
2. 각 근태의 `work_date`에 대해 `effective_from <= work_date AND (effective_to IS NULL OR work_date < effective_to)`인 계약 이력 한 건을 조회합니다. 계약이 없거나 두 건 이상이면 정산을 중단하고 데이터 오류를 표시합니다.
3. **[F06 & F09 & F10 단수 처리 및 검증 무결성]**:
   - **일별 합산 후 반올림 (F06 방어)**: 하루에 여러 건의 근태가 발생할 수 있으므로, 근태 건별로 금액을 반올림한 뒤 합산하면 단수 오차가 발생합니다. 따라서 동일 `work_date`의 모든 정상 실근무 분, 야간 근로 분, 일 8시간 초과 연장 분을 먼저 일별로 각각 합산한 후, 일별 금액을 계산하여 `HALF_UP` 반올림하고 `payroll_daily_detail`에 저장한 뒤 최종 합계를 `payroll`에 저장합니다.
   - **최저임금 재검증 (F09 방어)**: 계약 체결 시점뿐만 아니라 급여 계산 시점에도 해당 근무일 기준 유효 법정 최저시급(도쿄 1,280엔 등) 미달 여부를 재검증하여, 장기 계약 중 최저임금이 인상되었을 때의 법률 위반을 사전에 방어하고 경고합니다.
   - **통근수당 상한 기간 명확화 (F10 방어)**: 통근수당 월 상한은 '급여 정산 기간(1회)당 상한'으로 명확히 규정하며 계약서 및 명세서에 고지합니다.
4. 각 근무일에 적용된 `daily_transport_fee`를 합산한 뒤 계약 이력에서 사용한 월 상한을 적용합니다. 월 상한이 정산 기간 중 달라진 경우 임의로 고르지 말고 정산을 중단해 관리자 확인을 받도록 합니다.
5. 해당 기간에 대기 중 근태 수정 요청이 있으면 정산을 중단합니다. 이미 `CONFIRMED` 또는 `PAID`인 급여는 일반 재계산 API로 변경하지 않습니다.

```java
package com.example.kintai.domain.payroll.util;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.YearMonth;

public final class PayrollCalculator {
    private PayrollCalculator() {}

    public record DateRange(LocalDate startDate, LocalDate endDate) {}

    public record DailyPayResult(
            int basePay,
            int nightAllowance,
            int overtimeAllowance,
            int grossWage) {}

    /** 마감일(1~28 또는 99=말일)에 따른 실제 집계 기간. 양 끝 날짜를 모두 포함한다. */
    public static DateRange getCutoffDateRange(String targetYearMonth, int payCutoffDay) {
        YearMonth ym = YearMonth.parse(targetYearMonth);
        if (payCutoffDay == 99) {
            return new DateRange(ym.atDay(1), ym.atEndOfMonth());
        }
        if (payCutoffDay < 1 || payCutoffDay > 28) {
            throw new IllegalArgumentException("締め日は1～28または99（末日）で指定してください。");
        }
        LocalDate endDate = ym.atDay(payCutoffDay);
        LocalDate startDate = ym.minusMonths(1).atDay(payCutoffDay).plusDays(1);
        return new DateRange(startDate, endDate);
    }

    /**
     * 근태 한 건에 적용되는 시급으로 계산한다. 적용 시급은 서비스가 근무일 기준 계약 이력에서 조회한다.
     * 이 메서드는 야간 25%와 일별 8시간 초과분의 25%만 추가 계산한다.
     * 주 40시간 초과, 법정휴일, 월 60시간 초과 시간외근로 등은 별도 구현 대상이다.
     */
    public static DailyPayResult calculateDailyPay(
            int appliedHourlyWage,
            int netWorkMinutes,
            int nightWorkMinutes,
            int dailyOvertimeMinutes) {

        if (appliedHourlyWage <= 0) {
            throw new IllegalArgumentException("適用時給は0より大きい値を指定してください。");
        }
        if (netWorkMinutes < 0 || nightWorkMinutes < 0 || dailyOvertimeMinutes < 0
                || nightWorkMinutes > netWorkMinutes || dailyOvertimeMinutes > netWorkMinutes) {
            throw new IllegalArgumentException("勤務時間の値が不正です。");
        }

        int basePay = yenRound(BigDecimal.valueOf(netWorkMinutes)
                .multiply(BigDecimal.valueOf(appliedHourlyWage)), 60);
        int nightAllowance = yenRound(BigDecimal.valueOf(nightWorkMinutes)
                .multiply(BigDecimal.valueOf(appliedHourlyWage)).multiply(BigDecimal.valueOf(25)), 6000);
        int overtimeAllowance = yenRound(BigDecimal.valueOf(dailyOvertimeMinutes)
                .multiply(BigDecimal.valueOf(appliedHourlyWage)).multiply(BigDecimal.valueOf(25)), 6000);
        int grossWage = Math.addExact(Math.addExact(basePay, nightAllowance), overtimeAllowance);

        return new DailyPayResult(basePay, nightAllowance, overtimeAllowance, grossWage);
    }

    /** 이 프로젝트의 예시 정책: 금액을 마지막에 HALF_UP 방식으로 정수 엔 반올림한다. */
    private static int yenRound(BigDecimal numerator, long denominator) {
        return numerator.divide(BigDecimal.valueOf(denominator), 0, RoundingMode.HALF_UP)
                .intValueExact();
    }
}
```

### 6.3 화면(View) 및 REST API 명세
1. **`GET /payroll` → `templates/payroll/list.html`**
   * 상단: 귀속연월 선택기(기본값: `Asia/Tokyo` 기준 현재 연월 또는 사용자가 마지막으로 조회한 연월), **`[⚡ 선택 기간 급여 계산]`** 버튼, **`[📊 세무용 급여대장 엑셀(.xlsx) 다운로드]`** 버튼.
   * 중앙 요약 카드: 당월 총 지급 인건비 합계, 총 근무시간 합계, 야간/연장 할증 수당 합계, 교통비 합계.
   * 하단 테이블: 직원별 정산 내역 (`직원명`, `적용시급(스냅샷)`, `출근일수`, `실근무(시간:분)`, `기본급`, `야간할증(25%)`, `연장할증(25%)`, `교통비`, **`총 지급액`**, `상태(DRAFT/CONFIRMED/PAID)`, **`[급여명세서 상세]`**, **`[확정]`** 버튼).
2. **`POST /api/payroll/calculate` (급여 일괄 계산 및 Upsert)**
   * Request: `{ "storeId": 1, "targetYearMonth": "2026-10" }`
   * **[유효성 검증]**: `targetYearMonth` 파싱 실패 시 `DateTimeParseException`을 포착하여 `400 Bad Request` 에러를 반환합니다.
   * 점포의 `pay_cutoff_day`를 조회해 실제 집계 기간을 계산하고, 해당 기간의 `COMPLETED` 근태만 대상으로 적용일 기준 계약 이력을 조회합니다. 일별 계산 결과와 계약/근태 스냅샷을 `payroll_daily_detail`에 저장하고 합계를 `payroll`에 저장합니다.
   * `ON DUPLICATE KEY UPDATE`만으로 확정 행을 보호한다고 가정하지 않습니다. 정산 Service에서 상태를 확인하고 트랜잭션 안에서 `DRAFT`만 갱신하며 `CONFIRMED`/`PAID`는 일반 재정산으로 변경할 수 없도록 합니다. 동시 요청도 고려합니다.
   * `SUM()`이 `NULL`인 경우 `COALESCE`를 사용합니다.
   * 기간 안에 `PENDING` 수정 요청 또는 `MOD_REQUESTED` 근태가 하나라도 있으면 단순 경고만 표시하고 계산을 진행하지 않습니다. 기본 정책은 정산 실행을 중단하고 요청 처리를 먼저 완료하도록 하는 것입니다.
   * 정산/확정 시 `range_start_date`, `range_end_date`를 저장합니다. 확정 후에는 `payroll_daily_detail`을 급여명세서 원본으로 사용하고 일반 API에서 수정하지 않습니다.
3. **`GET /api/payroll/excel?storeId=1&targetYearMonth=2026-10` (Apache POI 엑셀 다운로드)**
   * `XSSFWorkbook`을 생성해 급여 집계용 엑셀 시트(`給与台帳`)를 만들고 `HttpServletResponse` 스트림으로 출력합니다. 특정 세무기관의 공식 제출 양식과 일치한다고 주장하지 않으며, 필요한 열·서식은 실제 제출 요건을 확인한 뒤 조정합니다.
4. **`GET /api/payroll/{payrollId}/detail` (개인별 급여명세서 상세 모달 조회)**
   * **응답 JSON (`PayrollDetailResponseDto`)**:
     ```json
     {
       "success": true,
       "data": {
         "payrollId": 1,
         "staffName": "佐藤 花子",
         "targetYearMonth": "2026-10",
         "appliedHourlyWage": 1300,
         "summary": {
           "workDaysCount": 15,
           "totalWorkMinutes": 5400,
           "totalNightMinutes": 900,
           "totalOvertimeMinutes": 360,
           "basePay": 117000,
           "nightAllowance": 4875,
           "overtimeAllowance": 1950,
           "transportAllowance": 7500,
           "grossPay": 131325,
           "payrollStatus": "CONFIRMED"
         },
         "dailyAttendances": [
           {
             "workDate": "2026-10-01",
             "clockInAt": "18:00",
             "clockOutAt": "23:30",
             "breakMinutes": 45,
             "netWorkMinutes": 285,
             "nightMinutes": 90,
             "overtimeMinutes": 0,
             "appliedHourlyWage": 1300,
             "basePay": 6175,
             "nightAllowance": 488,
             "overtimeAllowance": 0
           }
         ]
       }
     }
     ```
   * 화면에서 직원의 `[급여명세서 상세]`를 클릭하면 Fetch API로 위 JSON을 호출해 모달 창에 일자별 근무 기록과 계산 내역을 깔끔한 일본어 표로 띄워줍니다.

5. **`POST /api/payroll/{payrollId}/confirm` (급여 확정 잠금 및 장부 불변성 보장) (F01, F02, F04 대응)**
   * **Request JSON (`PayrollConfirmRequestDto`)**:
     ```json
     {
       "expectedGrossPay": 131325,
       "expectedTotalPay": 131325
     }
     ```
   * **선행 필수 조건 (F01 방어)**:
     1. 대상 급여 상태가 `DRAFT`일 것.
     2. **정산 대상 기간 종료 필수**: 오늘 날짜가 정산 기간 종료일보다 뒤여야 합니다 (`LocalDate.now() > payroll.range_end_date`). 아직 마감되지 않은 당월 기간의 급여를 조기 확정하여 남은 일자의 정상 출근이 차단되는 사태를 원천 차단합니다.
     3. 해당 직원의 이전 대상 월 급여가 모두 `CONFIRMED` 또는 `PAID`일 것 (시간순 확정).
     4. 해당 정산 기간 내에 처리 대기 중인 `PENDING` 근태 수정 요청 또는 `MOD_REQUESTED` 근태가 없을 것.
   * **서버 트랜잭션 직렬화 및 금액 재확인 (F02 & F04 방어)**:
     1. 트랜잭션 시작 시 동일 `store_staff` 행을 `SELECT ... FOR UPDATE`로 잠가 다른 관리자의 근태 수정 승인이나 사후 등록과의 경쟁(Race Condition)을 차단합니다.
     2. 확정 직전에 최신 근태와 계약을 바탕으로 재계산을 수행합니다.
     3. **점주 검토 금액 불일치 방어 (F02)**: 재계산된 총 지급액이 요청 본문의 `expectedGrossPay`와 1엔이라도 다를 경우, 백그라운드에서 임의로 갱신하여 잠그지 않고 즉시 `409 Conflict`와 함께 변경 내역(`changedDiff`)을 반환하여 점주에게 명시적 재확인을 요구합니다.
     4. 금액이 일치하면 `payroll_status = 'CONFIRMED'`, `confirmed_at = LocalDateTime.now()`, `confirmed_by = reviewerId`를 기록하고 트랜잭션을 커밋합니다. 확정 이후에는 일반 API를 통한 재계산 및 근태 수정이 영구 차단됩니다.
---

## 제7장. [선택 확장] LINE 메신저 봇 및 웹 알림 센터

> **구현 조건**: 팀 규모와 일정이 확인된 뒤 선택합니다. 2인 팀이면 핵심 MVP 완료 전에는 착수하지 않습니다.
> **패키지**: `com.example.kintai.domain.notification`

### 7.1 기능 목적 및 제한

LINE 메시지로 시프트 확정, 근태 수정 요청, 급여 확정 알림을 전달하는 확장 기능입니다. LINE Messaging API의 계정·채널 설정, 수신자 연결, 이용량/요금 정책은 구현 시 공식 문서에서 다시 확인합니다. 개인 개발 계정에서 모든 기능을 무조건 무료로 쓸 수 있다고 가정하지 않습니다.

공식 시작 문서: https://developers.line.biz/en/docs/messaging-api/overview/

### 7.2 구현 기준

1. **환경 설정**: 채널 토큰은 환경변수 또는 로컬 비밀 설정에서 읽고 저장소에 커밋하지 않습니다. 토큰이 없으면 `enabled=false`로 Mock 발송 로그만 출력합니다.
2. **사용자 연결**: `member.line_user_id`는 LINE 연동을 선택했을 때만 추가합니다. 사용자가 본인의 LINE 계정을 시스템 계정과 연결하는 절차와 연결 해제 방법을 정의합니다.
3. **웹훅**: 웹훅을 사용한다면 공식 서명 검증과 중복 이벤트 처리를 구현합니다. 단방향 푸시만 필요한 MVP라면 웹훅을 구현하지 않아도 됩니다.
4. **발송 이벤트**: 시프트 확정, 근태 수정 요청, 급여 확정 이벤트를 발송합니다. 급여 금액 등 민감 정보는 수신자 권한과 노출 수준을 검토합니다.
5. **재시도와 실패 기록**: 발송 성공/실패를 로그로 남기되 토큰과 민감한 본문을 로그에 노출하지 않습니다.
6. **웹 알림**: SSE 또는 WebSocket을 추가하려면 인증된 사용자별 수신 범위, 재연결, 미수신 알림 보관 여부를 별도로 명세합니다. 단순한 UI 토스트는 실제 실시간 푸시와 구분합니다.

### 7.3 시연 모드

- `line.enabled=false`: 실제 LINE 전송 대신 UI에 `送信プレビュー`를 표시하고 콘솔에는 민감 정보를 제외한 요약만 남깁니다.
- 실제 토큰 설정 시에만 LINE 전송 기능을 활성화합니다.
- 기능 설명에서 Mock 미리보기를 실제 발송 성공으로 표현하지 않습니다.

---

## 제8장. [선택 확장] 경영 분석(BI) 및 연수입 참고 대시보드

> **구현 조건**: 팀 규모와 무관하게 핵심 MVP가 통합된 뒤 선택합니다. 실제 세금·사회보험 자격을 판정하는 기능이 아니라 누적 급여와 기준 금액을 비교하는 참고용 화면으로 제한합니다.
> **패키지**: `com.example.kintai.domain.analytics`

### 8.1 일본 연수입 기준을 다루는 원칙

일본에서 흔히 말하는 `年収の壁`은 하나의 금액을 의미하지 않습니다. 본인 소득세의 과세 최저한, 가족의 부양공제, 특정친족특별공제, 사회보험 피부양자 기준, 근무처의 사회보험 가입 조건은 서로 다른 제도이며 대상 연령·가족관계·소득종류·적용일이 다릅니다.

**2026년 기준을 문서에 고정값으로 박아 두지 않습니다.** 2026년 10월 28일에 확인한 공식 자료에 따르면 다음 사항이 관련됩니다.

- 2026·2027년분 소득세는 2026년 12월 1일 시행되는 세제 개정이 2026년분에 적용되며, 소득세의 과세 최저한은 일정 조건에서 178만 엔 수준으로 상향됩니다. 2026년 11월까지의 원천징수와 12월 이후 연말정산 적용 시점도 구분해야 합니다. 공식 안내: https://www.nta.go.jp/users/gensen/2026kiso/
- 2026년분 급여소득공제는 법령상 구간별 계산이 적용되며, 급여소득만 있는 일반 부양친족의 소득 요건과 19세 이상 23세 미만 특정친족 특별공제는 서로 다른 기준입니다. 특정친족 특별공제는 공제액이 소득 구간별로 달라지며, 해당 친족의 급여수입이 197만 엔 이하인 경우까지 요건이 관련될 수 있습니다. 공식 안내: https://www.nta.go.jp/taxes/shiraberu/taxanswer/shotoku/1177.htm
- 사회보험 피부양자 기준은 세금 기준과 별도입니다. 피보험자의 배우자가 아닌 19세 이상 23세 미만 대상자는 일정 조건에서 2025년 10월 1일부터 연간 수입 150만 엔 미만 기준이 적용됩니다. 공식 안내: https://www.mhlw.go.jp/web/t_doc?dataId=00tc9249&dataType=1&pageNo=1
- 대중교통 통근수당의 비과세 한도는 합리적인 통근 경로·운임 등을 기준으로 하며 월 15만 엔 상한이 적용됩니다. 자동차·자전거 통근에는 거리 기준이 따로 있습니다. 2026년 개정이 있으므로 통근수당 지급액 전체를 무조건 비과세로 취급하지 않습니다. 공식 안내: https://www.nta.go.jp/taxes/shiraberu/taxanswer/gensen/2582.htm 및 https://www.nta.go.jp/taxes/shiraberu/taxanswer/gensen/2585.htm

위 기준은 기능을 설계하기 위한 참고이며, 자동 세무 판정이나 실제 세무 조언을 대체하지 않습니다. 공식 문서는 개발 착수와 발표 직전에 다시 확인합니다. 각 기준은 `기준명`, `제도 유형`, `적용 대상`, `적용 시작일`, `적용 종료일`, `금액`, `공식 출처 URL`을 갖는 설정 데이터로 관리하고 UI 문구에도 기준 연도를 표시합니다.

### 8.2 기능 범위 및 한계

1. **예상 인건비** (`GET /api/analytics/projected-cost`): 확정 시프트와 적용 계약 이력을 기반으로 예상 급여를 계산합니다. 법정 할증 범위를 모두 지원하지 않는 동안에는 `概算`으로 표시합니다.
2. **연간 누적 급여 참고** (`GET /api/analytics/annual-pay?storeId=1&year=2026`): 해당 시스템의 확정/지급 급여 데이터만 집계합니다. 다른 직장의 소득은 포함되지 않으므로 개인의 전체 연수입이라고 단정하지 않습니다.
3. **기준 금액 비교**: 사용자가 기준 유형과 적용 연도를 선택할 수 있도록 하되, 기준이 없거나 대상 조건을 판단할 수 없으면 자동으로 적격/부적격 판정을 내리지 않습니다.
4. **통근수당 처리**: 현재 DB에는 통근 수단과 통근거리 정보가 없으므로 `transport_allowance` 전체를 비과세 급여로 확정하지 않습니다. 정확한 과세 급여가 아니라는 안내를 화면에 표시합니다.
5. **필수 UI 안내**: `参考値です。税金・社会保険の資格判定を保証するものではありません。`와 같이 참고용이며 자격 판정을 보장하지 않는다는 문구를 표시합니다.

MVP에서는 연수입 기준을 하드코딩하지 않고, `DRAFT`를 제외한 급여 데이터의 누적 합계를 참고 그래프로 보여주는 기능까지만 구현할 수 있습니다. 법령 기준별 판정은 별도 확장 기능입니다.

---

## 부록 A. 통합 검증 체크리스트 및 미결정 사항

### A. 필수 통합 테스트

- **권한**: STAFF가 다른 직원·다른 매장의 근태/급여를 조회하거나 수정할 수 없어야 합니다. 메뉴를 직접 숨기는 것만으로는 통과가 아닙니다.
- **매장 무결성**: `store_id`와 `store_staff_id`가 다른 매장 관계를 가리키는 INSERT/UPDATE가 거절되어야 합니다.
- **시프트 날짜 범위**: `start=2026-10-01`, `end=2026-11-01`일 때 2026-11-01 근무가 응답에 포함되지 않아야 합니다.
- **시간 경계값**: 6시간, 6시간 초과, 8시간, 8시간 초과 및 자정 통과를 별도 테스트합니다.
- **다회 휴게**: 휴게 구간 전체의 합계와 야간 구간 교집합을 각각 계산하고, 겹치는 휴게를 거절합니다.
- **타각 상태**: 중복 출근, 근무 중 휴게 시작, 휴게 중 재휴게 시작, 휴게 중 퇴근 요청 등을 검증합니다.
- **계약 이력**: 시급 변경 이전 날짜의 근태에는 이전 계약, 이후 날짜에는 새 계약이 적용되는지 확인합니다.
- **급여 재정산**: `DRAFT`는 재계산 가능하지만 `CONFIRMED`/`PAID`는 일반 계산 API가 변경할 수 없어야 합니다.
- **수정 승인**: 승인 시 기존/요청 휴게 이력이 보존되고, 반려 시 기존 근태가 유지되며, 신규 생성 요청이 반려되면 근태 행이 생성되지 않아야 합니다.
- **급여 상세**: 확정 후 원본 근태를 바꾸려는 요청은 기본 정책에서 차단하고, 확정 급여 상세가 `payroll_daily_detail` 스냅샷을 기반으로 표시되어야 합니다.
- **마감일**: 1~28, 99는 정상 처리하고 0, 29~98은 검증 오류가 되어야 합니다.
- **계산 오류**: 잘못된 시간 순서·음수 시간·범위를 벗어난 휴게를 0분으로 통과시키지 않아야 합니다.

### B. 팀에서 최종 결정할 항목(아직 미결정이어도 개발은 진행 가능)

1. **팀 인원과 담당자**: **2인 정예 팀 (개발자 A: 도메인/아키텍처, 개발자 B: 인증/점포/UI)**으로 최종 확정되었습니다.
2. **Java/라이브러리 패치 버전**: Java 21을 기준으로 하되 실제 빌드 파일을 확인하고, Spring Boot와 MyBatis Starter는 호환되는 버전을 고정합니다.
3. **배포·데모 방식**: 로컬 실행만 할지, 클라우드 배포까지 할지 정합니다.
4. **MVP 지역 범위**: 기본 설정은 도쿄 예시입니다. 전국 최저임금의 유효일자별 판정은 별도 기준 데이터가 준비되지 않는 한 지원한다고 주장하지 않습니다.
5. **법정 급여 범위**: 주 40시간, 법정휴일, 월 60시간 초과, 추가 할증 및 세무 기능은 MVP에서 완전 지원하지 않습니다. 지원 범위를 넓힐 경우 각각 별도 규칙과 테스트를 추가합니다.
6. **연수입 대시보드**: 구현할 경우 참고용으로 제한하고, 세무·사회보험 자격을 자동 확정 판정하지 않습니다.
7. **알림 확장**: LINE API 토큰, 실제 발송, 웹훅, SSE/WebSocket 중 구현할 항목을 일정과 인원에 맞춰 선택합니다.

### C. 공식 기준 확인 링크

- 도쿄도 최저임금(2026년 10월 1일부터 시간급 1,280엔): https://jsite.mhlw.go.jp/tokyo-roudoukyoku/news_topics/houdou/20260930chinginka_00001.html
- FullCalendar 이벤트 조회의 종료 경계 미포함 규칙: https://fullcalendar.io/docs/events-function
- 일본 국세청의 2026년분 소득세 개정 안내: https://www.nta.go.jp/users/gensen/2026kiso/
- 일본 국세청의 특정친족특별공제: https://www.nta.go.jp/taxes/shiraberu/taxanswer/shotoku/1177.htm
- 일본 국세청의 대중교통 통근수당 비과세 기준: https://www.nta.go.jp/taxes/shiraberu/taxanswer/gensen/2582.htm
- 일본 국세청의 자동차·자전거 통근수당 비과세 기준: https://www.nta.go.jp/taxes/shiraberu/taxanswer/gensen/2585.htm
- 일본 후생노동성의 19세 이상 23세 미만 사회보험 피부양자 기준: https://www.mhlw.go.jp/web/t_doc?dataId=00tc9249&dataType=1&pageNo=1

공식 기준 링크는 구현 착수·발표 시 다시 확인하며, 문서에 기재한 기준 날짜를 현재 법령의 영구 보장으로 간주하지 않습니다.

---

## 제9장. 업무 스트림별 AI 코딩 프롬프트 실전 가이드 (복사해서 바로 사용)

본 프로젝트는 **2인 정예 팀(3~4주 스프린트)**으로 확정되었으므로, 아래 업무 스트림을 2인이 상호 협의하여 [파트 A]와 [파트 B]로 명확히 분담하여 진행합니다. 선택 확장 기능(LINE/대시보드)은 Phase 1 MVP 완결 후로 공식 분리합니다.

### 2인 업무 스트림 분할 모델 (킥오프 협의 배정)

- **파트 A 담당자 (코어 비즈니스):** 업무 스트림 B(시프트) + C(근태) + D(급여·POI 엑셀) 및 통합 테스트/비관적 락 관리
- **파트 B 담당자 (인증·점포 & UI):** 업무 스트림 A(인증·점포 마스터) 및 공통 UI/UX 반응형 스타일링
- **공통 규칙:** `schema.sql`, 공통 보안 설정, API 계약, `layout.html` 같은 공유 파일은 PR 및 상호 코드 리뷰를 통해 변경합니다.

### 💬 [업무 스트림 A] 점포 및 알바생 계약 관리 구현 프롬프트
```text
첨부한 이 요구사항 명세서와 실제 프로젝트 파일을 확인한 뒤 '제3장. 회원 인증 및 점포·계약 마스터'의 점포 관리 기능을 구현해 줘.
먼저 저장소에서 Member 샘플, 공통 layout.html, ApiResponse가 실제로 존재하는지와 클래스/메서드 시그니처를 확인하고, 존재하지 않거나 문서와 다르면 임의로 가정하지 말고 먼저 보고해 줘.
★ 중요: 일본 IT 현지 취업 포트폴리오이므로 모든 HTML 화면(UI)의 버튼명, 라벨, 테이블 헤더, 모달 안내 문구는 명세서 1.5절의 [일본어 표준 용어집]을 준수하여 자연스러운 비즈니스 일본어(日本語)로 작성해 줘.
현재 프로젝트의 실제 의존성 버전과 호환성에 맞춰 작은 단계로 작성해 줘. 먼저 DTO/API 계약 및 변경 계획을 제시하고, 합의한 단계를 구현해 줘:
1. com.example.kintai.domain.store.dto (StoreDto, StoreStaffDetailDto, StoreCreateRequestDto, StaffJoinRequestDto, StaffContractUpdateDto)
2. com.example.kintai.domain.store.mapper.StoreMapper.java & src/main/resources/mapper/store/StoreMapper.xml
3. com.example.kintai.domain.store.service.StoreService.java
4. com.example.kintai.domain.store.controller.StoreController.java (GET /stores 화면 라우팅 및 /api/stores/** REST API 포함)
5. src/main/resources/templates/store/manage.html (common/layout.html 상속, Bootstrap 5 모달 및 Fetch API 연동 완료된 일본어 화면)
```

### 💬 [업무 스트림 B] FullCalendar 통합 시프트 스케줄러 구현 프롬프트
```text
첨부한 이 요구사항 명세서 문서를 기준으로 '제4장. 통합 시프트 스케줄러 (shift_schedule & FullCalendar)'를 구현해 줘.
자정을 넘는 시각 변환, FullCalendar 종료일 미포함 규칙, 상태 전이 검증을 포함해 줘. 휴게시간 자동값은 시프트 계획용 추천으로만 사용하고 실제 휴게 기록을 임의로 대체하지 말아 줘.
★ 중요: 일본 IT 현지 취업 포트폴리오이므로 FullCalendar의 월/요일 및 모든 버튼, 상태 배지(希望シフト, 確定シフト, 未割当 등), 모달 텍스트는 자연스러운 비즈니스 일본어(日本語)로 작성해 줘.
먼저 각 파일의 책임, DTO/API 계약, 필요한 DB 변경을 계획으로 제시해 줘. 계획을 검토한 뒤 한 단계씩 구현하고, 단계마다 컴파일 가능한 코드와 테스트를 제공해 줘. 필요한 경우 아래 파일을 순서대로 작성해 줘:
1. com.example.kintai.domain.shift.dto (ShiftDto, CalendarEventDto, ShiftRequestDto, ShiftConfirmDto)
2. com.example.kintai.domain.shift.mapper.ShiftMapper.java & src/main/resources/mapper/shift/ShiftMapper.xml
3. com.example.kintai.domain.shift.service.ShiftService.java
4. com.example.kintai.domain.shift.controller.ShiftController.java (GET /shifts 및 /api/shifts/** 포함)
5. src/main/resources/templates/shift/calendar.html (FullCalendar 6.1 렌더링, 날짜 클릭 시 희망 제출 모달, 일정 클릭 시 희망 vs 확정 대조 조율 모달 포함 일본어 화면)
```

### 💬 [업무 스트림 C] 출퇴근 타각 및 근태 수정 승인제 구현 프롬프트
```text
첨부한 이 요구사항 명세서 문서를 기준으로 '제5장. 감사추적 출퇴근 타각 및 수정 승인 워크플로우'를 구현해 줘.
명세서 5.3절의 `AttendanceTimeCalculator`를 검토해 적용하고, 다회 휴게 구간을 모두 반영해 일별 실근무/야간/일별 8시간 초과분을 계산해 줘. 잘못된 입력을 0분으로 조용히 바꾸지 말고 검증 오류로 처리해 줘. 다음 케이스의 JUnit 단위 테스트도 작성해 줘: 야간 근무, 여러 휴게, 겹치는 휴게, 출퇴근 범위 밖 휴게, 잘못된 출퇴근 순서.
★ 중요: 일본 IT 현지 취업 포트폴리오이므로 타각 메인 버튼(出勤, 退勤, 休憩開始, 休憩終了) 및 상태 배지(勤務中, 休憩中), 수정 신청/승인 화면 텍스트는 자연스러운 비즈니스 일본어(日本語)로 작성해 줘.
먼저 각 파일의 책임, DTO/API 계약, 필요한 DB 변경을 계획으로 제시해 줘. 계획을 검토한 뒤 한 단계씩 구현하고, 단계마다 컴파일 가능한 코드와 테스트를 제공해 줘. 필요한 경우 아래 파일을 순서대로 작성해 줘:
1. com.example.kintai.domain.attendance.util.AttendanceTimeCalculator.java
2. com.example.kintai.domain.attendance.dto (AttendanceDto, PunchRequestDto, AttendanceModRequestDto, ModReviewDto)
3. com.example.kintai.domain.attendance.mapper.AttendanceMapper.java & src/main/resources/mapper/attendance/AttendanceMapper.xml
4. com.example.kintai.domain.attendance.service.AttendanceService.java
5. com.example.kintai.domain.attendance.controller.AttendanceController.java (GET /attendance, GET /attendance/mod-requests 및 REST API 포함)
6. src/main/resources/templates/attendance/punch.html 및 mod-list.html (일본어 UI)
```

### 💬 [업무 스트림 D] 급여 정산 및 POI 엑셀 출력 구현 프롬프트
```text
첨부한 이 요구사항 명세서 문서를 기준으로 '제6장. 일본 노동기준법 급여 정산 엔진 및 Apache POI 엑셀 대장'을 구현해 줘.
명세서 6.2절의 마감일 계산 규칙을 검증하고 구현해 줘. `pay_cutoff_day`는 1~28 또는 99만 허용하고 나머지는 검증 오류로 처리해 줘. 정산 기간 중 계약 이력이 여러 개일 수 있으므로 일별 유효 시급을 적용하고 `payroll_daily_detail`에 스냅샷을 저장해 줘. 반올림 정책은 코드에서 명시하고 테스트 결과를 보고해 줘.
★ 중요: 일본 IT 현지 취업 포트폴리오이므로 급여 정산 화면의 모든 수당 항목(基本給, 深夜手当, 残業手当, 通勤手当, 総支給額) 및 Apache POI 엑셀 시트명(給与台帳), 엑셀 헤더는 일본 업무 현장에서 자연스러운 비즈니스 일본어(日本語) 서식으로 작성해 줘.
먼저 각 파일의 책임, DTO/API 계약, 필요한 DB 변경을 계획으로 제시해 줘. 계획을 검토한 뒤 한 단계씩 구현하고, 단계마다 컴파일 가능한 코드와 테스트를 제공해 줘. 필요한 경우 아래 파일을 순서대로 작성해 줘:
1. com.example.kintai.domain.payroll.util.PayrollCalculator.java
2. com.example.kintai.domain.payroll.dto (PayrollDto, PayrollCalculateRequestDto)
3. com.example.kintai.domain.payroll.mapper.PayrollMapper.java & src/main/resources/mapper/payroll/PayrollMapper.xml
4. com.example.kintai.domain.payroll.service.PayrollService.java (Apache POI .xlsx 엑셀 워크북 생성 로직 포함)
5. com.example.kintai.domain.payroll.controller.PayrollController.java (GET /payroll 및 /api/payroll/** 포함)
6. src/main/resources/templates/payroll/list.html (일본어 UI)
```

### 💬 [선택 확장 스트림] LINE 알림 및 경영 분석 대시보드 구현 프롬프트
```text
핵심 MVP가 통합 테스트를 통과하고 팀이 확장 기능 구현에 합의한 경우에만 첨부한 이 요구사항 명세서의 제7장/제8장을 구현해 줘. 팀 규모만으로 확장 기능 담당자를 미리 고정하지 말아 줘. 먼저 범위를 선택하고 공식 API/법령 기준 적용일을 확인해 줘.
LINE 토큰이 없는 환경에서는 실제 발송으로 오인되지 않는 Mock 미리보기만 제공해 줘. 연수입 대시보드는 적용 연도·제도 유형·대상 조건이 명시된 참고용 누적 급여 그래프로 제한하고, 통근수당을 근거 없이 전액 비과세로 간주하거나 세금/사회보험 적격 여부를 확정 판정하지 말아 줘.
★ 중요: 모든 화면 텍스트와 알림 문구는 명세서 1.5절 용어집을 준수하여 자연스러운 비즈니스 일본어(日本語)로 작성해 줘.
먼저 각 파일의 책임, DTO/API 계약, 필요한 DB 변경을 계획으로 제시해 줘. 계획을 검토한 뒤 한 단계씩 구현하고, 단계마다 컴파일 가능한 코드와 테스트를 제공해 줘. 필요한 경우 아래 파일을 순서대로 작성해 줘:
1. com.example.kintai.domain.notification.service.LineNotificationService.java
2. com.example.kintai.domain.analytics.dto (TaxBarrierStatusDto, ProjectedCostDto)
3. com.example.kintai.domain.analytics.mapper.AnalyticsMapper.java & src/main/resources/mapper/analytics/AnalyticsMapper.xml
4. com.example.kintai.domain.analytics.service.AnalyticsService.java
5. com.example.kintai.domain.analytics.controller.AnalyticsController.java (GET /analytics 및 /api/analytics/** 포함)
6. src/main/resources/templates/analytics/dashboard.html (Chart.js 통계 차트 및 연수입의 벽 프로그레스 바 일본어 UI)
```
