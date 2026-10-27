# 📘 일본형 시프트·근태·급여 정산 시스템 마스터 기술 보고서 및 초상세 개발 명세서 (SRS)

> **시스템명**: 일본형 시프트·근태·급여 정산 시스템 (勤怠・シフト・給与管理システム / Kintai Management System)  
> **문서 성격**: 프로젝트 개론서 · 아키텍처 설계서 · DB 명세서 · AI 프롬프트용 초상세 구현 가이드 통합본  
> **기술 스택**: Spring Boot 4.0.8, Java 17 (또는 21), MyBatis 4.0.1, MySQL 8.0, Spring Security 6/7, Thymeleaf, Bootstrap 5.3, FullCalendar 6.1.15, Apache POI 5.2.5  
> **문서 활용법**: 팀원은 본 문서 하나만 읽으면 프로젝트의 기획 의도부터 DB 구조, 화면 라우팅, 수식 알고리즘까지 100% 이해할 수 있으며, 담당 장(Chapter)을 통째로 복사해 AI(ChatGPT, Claude, Cursor 등)에게 주면 즉시 동작하는 코드를 생성할 수 있습니다.

---

## 제0장. 프로젝트 개론 및 아키텍처 철학 (Project Overview & Philosophy)

### 0.1 왜 이 시스템을 개발하는가? (배경 및 문제 정의)
일본의 외식(이자카야, 카페, 패밀리 레스토랑)·유통(편의점, 드럭스토어) 아르바이트 시장은 전 세계에서 가장 독특하고 까다로운 노무 환경을 가지고 있습니다.

1. **다점포 겸업(掛け持ち)의 일상화 (38.5%)**: 알바생 1명이 낮에는 카페(시급 1,163엔), 밤에는 이자카야(시급 1,300엔) 등 여러 매장에서 각기 다른 시급과 교통비 조건으로 일합니다.
2. **메신저(LINE)와 수기 엑셀에 의존하는 시프트 조율 (82.4%)**: 알바생이 LINE 메시지로 희망 근무 시간을 보내면 점주가 이를 수기로 엑셀에 옮기며 매월 평균 **7.5일**을 허비하고, 오타나 누락으로 인해 금/토요일 피크타임에 인원 펑크(결원)가 발생합니다.
3. **종이 타임카드의 부정 타각(27.1%)과 사후 조작 분쟁**: 대리 출근이 빈번하고, 퇴근 타각을 깜빡했을 때(월평균 18.6건) 점주가 구두로 듣고 임의로 시간을 고쳐 적어 사후 임금 체불 분쟁의 원인이 됩니다.
4. **일본 노동기준법 복합 할증(150%) 수기 계산 오류 (63.8%)**: 일본 노동기준법 제37조에 따라 **야간 근로(22:00~익일 05:00, +25%)**와 **연장 근로(1일 8시간 초과, +25%)**가 겹치는 심야 잔업 구간은 **150%**를 지급해야 하며, 50전 단수 처리 규정과 월 교통비 비과세 상한선(통상 월 15,000엔)까지 얽혀 있어 엑셀 수식 오류가 끊이지 않습니다.

> **💡 핵심 진단**: *"근로 계약(마스터) $\rightarrow$ 시프트 계획 $\rightarrow$ 출퇴근 타각(실행) $\rightarrow$ 법정 급여 정산"*의 4대 도메인이 서로 단절되어 있기 때문에 발생하는 문제입니다. 본 프로젝트는 이 4단계 파이프라인을 **단일 데이터베이스와 무결성 정산 엔진으로 일원화**하여 해결합니다.

---

### 0.2 현장 12대 문제와 4대 핵심성공요인(CSF) 매핑

| 부문 | 문제 번호 및 현장의 병목 현상 | 기술적 해결 아키텍처 | 핵심성공요인 (CSF) 및 목표 지표 |
| :--- | :--- | :--- | :--- |
| **1. 계약 마스터** | • `계약-1`: 1인 다점포 시급/교통비 개별 매핑 불가<br>• `계약-2`: 점주-알바생 간 권한 및 매장 접근 통제 부재<br>• `계약-3`: 시급 인상 시 과거 급여명세서까지 변조됨 | • `member`와 `store` 사이 `store_staff` 매핑 정규화<br>• Spring Security Role 및 매장 소속 인가 검증<br>• `payroll` 정산 시점 `applied_hourly_wage` 스냅샷 보존 | **CSF 1. 정규화 계약 마스터**<br>*(다점포 매핑 무결성 100%, 과거 정산 왜곡 0건)* |
| **2. 시프트 계획** | • `시프트-1`: LINE/구두 취합으로 마감 및 이력 유실<br>• `시프트-2`: 알바생 희망안 vs 점주 확정안 대조 불가<br>• `시프트-3`: 주말 피크타임 최소 인원 결원 방치 | • 웹 캘린더 기반 희망 시프트 직접 제출<br>• `shift_schedule` 단일 테이블 내 `requested`/`confirmed` 공존<br>• 시간대별 확정 인원 집계 및 결원 경고 배지 | **CSF 2. 통합 시프트 스케줄러**<br>*(시프트 조율 소요 7.5일 $\rightarrow$ 1일 단축)* |
| **3. 근태 타각** | • `근태-1`: 매장 외부 원격 타각 및 동료 대리 출근<br>• `근태-2`: 타각 누락 시 점주 임의 수정(감사 이력 공백)<br>• `근태-3`: 6h/8h 초과 법정 휴게시간(45분/60분) 누락 | • 점포 고유 `qr_token` 검증 기반 1클릭 비동기 타각<br>• `attendance_mod_request` 승인 워크플로우(Audit Trail)<br>• 근무시간 대비 법정 휴게시간 자동 검증 및 차감 | **CSF 3. 감사추적 근태 타각**<br>*(타각 수정 이력 감사 추적 100%)* |
| **4. 법정 급여** | • `급여-1`: 야간(25%)+연장(25%) 중복 150% 수식 충돌<br>• `급여-2`: 시간(FLOAT) 연산으로 인한 부동소수점 오차<br>• `급여-3`: 교통비 월 상한액 수기 비교 누락 및 엑셀 오타 | • 기본급(100%)+야간할증(25%)+연장할증(25%) 분리 가산식<br>• 모든 근로시간을 **분(Minute, `INT`)** 정수로 저장 및 연산<br>• `MIN(출근일수×일교통비, 월상한)` 및 Apache POI 엑셀 출력 | **+1 CSF 4. 법정 급여 엔진**<br>*(일본 노동법 정산 오차 0건, 1클릭 엑셀 출력)* |

---

### 0.3 팀 규모별(2인 / 3인 / 4인) 수직분할 아키텍처 및 역할 분담

본 프로젝트는 인원수에 따라 **도메인 단위(Vertical Slice)로 완벽히 쪼개어 개발**할 수 있도록 설계되어 있습니다. 각 팀원은 본인이 맡은 도메인 폴더(`domain/{도메인명}`) 안에서 Controller, Service, Mapper, DTO, HTML을 풀스택으로 전담하므로 **Git 충돌(Merge Conflict)이 원천 차단**됩니다.

#### ① [기본형] 2인 팀 분담표
* **팀원 A (계획 레이어 전담)**:
  * **담당 테이블**: `member`, `store`, `store_staff`, `shift_schedule`
  * **담당 장(Chapter)**: **제3장(회원·점포 마스터)** + **제4장(통합 시프트 스케줄러 & FullCalendar)**
* **팀원 B (실행·정산 레이어 전담)**:
  * **담당 테이블**: `attendance`, `attendance_mod_request`, `payroll`
  * **담당 장(Chapter)**: **제5장(실시간 타각 & 근태 수정 감사)** + **제6장(법정 급여 정산 엔진 & POI 엑셀)**

#### ② [확장형] 3인 팀 분담표
* **팀원 A**: 제3장(회원·점포) + 제4장(시프트 스케줄러)
* **팀원 B**: 제5장(근태 타각·수정 감사) + 제6장(법정 급여 정산·POI 엑셀)
* **팀원 C (채널·알림 레이어 전담)**:
  * **담당 장(Chapter)**: **제7장(일본 특화 LINE 메신저 봇 & WebSocket/SSE 웹 실시간 알림 센터)** + 회원/점포 UI 고도화

#### ③ [확장형] 4인 팀 분담표
* **팀원 A (인증·마스터 전담)**: 제3장(회원가입, Spring Security 인가, 다점포 계약 관리, 점포 QR/초대코드 관리)
* **팀원 B (시프트·캘린더 전담)**: 제4장(FullCalendar 6.x 희망/확정 대조 뷰, 드래그앤드롭 조율, 피크타임 결원 경고)
* **팀원 C (근태·급여 코어 전담)**: 제5장(실시간 타각, 수정 요청 승인 워크플로우) + 제6장(150% 할증 급여 엔진, POI 엑셀)
* **팀원 D (외부연동·경영분석 BI 전담)**: **제7장(LINE 봇 연동)** + **제8장(점주용 인건비 시뮬레이터 & 일본 세법 '103만엔/130만엔의 벽' 대시보드)**

---

## 제1장. 전사 공통 아키텍처 및 협업 표준 (Global Architecture & Conventions)

모든 팀원과 AI 코딩 도구는 코드를 작성할 때 반드시 아래의 공통 규칙을 준수해야 합니다.

### 1.1 패키지 디렉토리 구조 (고정)
```text
com.example.kintai
├── KintaiApplication.java
├── config
│   └── SecurityConfig.java                # Spring Security 설정 (초기 개발 시 permitAll)
├── global
│   ├── common
│   │   ├── ApiResponse.java               # [생성완료] 전사 표준 JSON 응답 래퍼
│   │   └── TimeUtils.java                 # [생성완료] 분(Minute) <-> 시간 포맷 및 익일 퇴근 변환 유틸
│   └── exception
│       └── GlobalExceptionHandler.java    # [생성완료] 전역 예외 처리기 (@RestControllerAdvice)
└── domain
    ├── home
    │   └── HomeController.java            # [생성완료] 루트('/') 접속 라우팅
    ├── member                             # [제3장] 회원 도메인 (샘플 1세트 생성완료)
    │   ├── controller
    │   ├── service
    │   ├── mapper
    │   └── dto
    ├── store                              # [제3장] 점포 및 소속 직원(store_staff) 도메인
    ├── shift                              # [제4장] 시프트 스케줄(shift_schedule) 도메인
    ├── attendance                         # [제5장] 출퇴근 타각 및 수정요청 도메인
    ├── payroll                            # [제6장] 월별 급여 정산 및 POI 엑셀 도메인
    ├── notification                       # [제7장 - 3~4인 확장] LINE 봇 및 실시간 알림 도메인
    └── analytics                          # [제8장 - 4인 확장] 경영 분석 및 103만엔 세법 도메인
```

* **리소스 디렉토리 구조**:
  * MyBatis XML 매퍼: `src/main/resources/mapper/{도메인명}/{도메인}Mapper.xml`
  * Thymeleaf 뷰 파일: `src/main/resources/templates/{도메인명}/{화면명}.html`

---

### 1.2 전사 화면(Thymeleaf View) 라우팅 마스터 테이블
현재 [`templates/common/layout.html`](file:///c:/Users/klad3/OneDrive/바탕 화면/IT/프로젝트/팀플(스프링부트) 2026.10.26~2026/kintai/src/main/resources/templates/common/layout.html) 상단 네비게이션 바에 연결된 표준 URL입니다. 각 도메인 담당자는 화면 컨트롤러를 만들 때 반드시 아래 경로와 일치시켜야 합니다.

| 메뉴명 | HTTP Method & URL | 반환할 View 경로 (`return "..."`) | 담당 | 화면 핵심 역할 |
| :--- | :--- | :--- | :---: | :--- |
| **홈 / 샘플** | `GET /` $\rightarrow$ `/members/sample` | `"member/sample"` | 공통 | 초기 연동 확인 및 테스트용 계정/매장 전환 화면 |
| **로그인 / 가입** | `GET /login`, `GET /join` | `"member/login"`, `"member/join"` | A | 로그인 폼 및 점주/알바생 회원가입 화면 |
| **점포 관리** | `GET /stores` | `"store/manage"` | A | 점포 개설, 초대코드/QR토큰 확인, 소속 알바생 시급/교통비 계약 관리 |
| **시프트 계획** | `GET /shifts` | `"shift/calendar"` | A | FullCalendar 기반 알바생 희망 시프트 제출 및 점주 확정 조율 화면 |
| **출퇴근 타각** | `GET /attendance` | `"attendance/punch"` | B | 실시간 디지털 시계, 1클릭 출근/휴게/퇴근 타각, 월간 타임카드 및 수정 신청 모달 |
| **근태 승인관리** | `GET /attendance/mod-requests` | `"attendance/mod-list"` | B | 점주용 근태 수정 요청(`PENDING`) 목록 조회 및 승인/반려 처리 화면 |
| **급여 정산** | `GET /payroll` | `"payroll/list"` | B | 귀속연월 선택, 150% 할증 급여 일괄 계산 실행, 명세서 상세 모달, 엑셀 다운로드 |
| **경영 분석(확장)** | `GET /analytics` | `"analytics/dashboard"` | D | 인건비 통계 차트 및 알바생별 103만 엔 소득의 벽 프로그레스 대시보드 |

### 1.2.1 Spring Security 권한별 메뉴 분기 (Thymeleaf `sec:authorize`) 규칙
점주와 알바생의 보안 인가 분리를 위해 상단 네비게이션 바(`layout.html`)는 아래 규칙에 따라 동적으로 메뉴를 제어합니다:
* **점주 권한 (`ROLE_OWNER`) 전용 메뉴**:
  * `<li sec:authorize="hasRole('ROLE_OWNER')"><a href="/stores">店舗管理</a></li>`
  * `<li sec:authorize="hasRole('ROLE_OWNER')"><a href="/attendance/mod-requests">勤怠承認</a></li>`
  * `<li sec:authorize="hasRole('ROLE_OWNER')"><a href="/payroll">給与計算</a></li>`
  * `<li sec:authorize="hasRole('ROLE_OWNER')"><a href="/analytics">経営分析</a></li>`
* **알바생 권한 (`ROLE_STAFF`) 전용 메뉴**:
  * `<li sec:authorize="hasRole('ROLE_STAFF')"><a href="/shifts">希望シフト提出</a></li>`
  * `<li sec:authorize="hasRole('ROLE_STAFF')"><a href="/attendance">勤怠打刻</a></li>`
* **공통 메뉴**:
  * 로그인/로그아웃, 점포 선택 드롭다운, 현재 상태 표시

* **Thymeleaf 레이아웃 상속 문법 (모든 HTML 파일 상단 고정)**:
  ```html
  <!DOCTYPE html>
  <html lang="ko" xmlns:th="http://www.thymeleaf.org"
        th:replace="~{common/layout :: html}">
  <head>
      <title>화면 제목</title>
  </head>
  <body>
  <div th:fragment="content">
      <!-- 여기에 각 도메인 본문 HTML 및 <script> 작성 -->
  </div>
  </body>
  </html>
  ```

---

### 1.3 다점포(`storeId`, `storeStaffId`) 컨텍스트 처리 규칙 (매우 중요)
이 시스템은 1명의 회원이 여러 매장에 소속될 수 있습니다. 개발 초기에는 Spring Security 로그인 없이도 팀원 A와 B가 독립적으로 개발·테스트할 수 있도록 **쿼리 파라미터 + 세션 폴백(Fallback) 규칙**을 사용합니다.

* **규칙**: 모든 화면 컨트롤러나 조회 API에서 `storeId`, `storeStaffId`, `memberId`가 필요할 때, 요청 파라미터로 넘어오지 않으면 **`data.sql`에 등록된 기본 테스트 데이터(`storeId = 1`, `owner memberId = 1`, `staff memberId = 2`, `storeStaffId = 1`)를 기본값(`defaultValue = "1"`)으로 사용**합니다.
* 예시 코드:
  ```java
  @GetMapping("/shifts")
  public String shiftCalendarView(
          @RequestParam(value = "storeId", defaultValue = "1") Long storeId,
          @RequestParam(value = "storeStaffId", defaultValue = "1") Long storeStaffId,
          Model model) {
      model.addAttribute("storeId", storeId);
      model.addAttribute("storeStaffId", storeStaffId);
      return "shift/calendar";
  }
  ```
* 이렇게 작성해 두면 로그인 기능이 완성되기 전에도 브라우저에서 `/shifts`만 치면 1번 매장(도쿄 신주쿠 본점) 기준으로 즉시 화면이 동작하고, `/shifts?storeId=1&storeStaffId=2`처럼 파라미터만 바꿔서 다른 알바생 테스트도 즉시 가능합니다.

---

### 1.4 공통 JSON 응답 규격 (`ApiResponse<T>`)
모든 `@ResponseBody` 또는 `@RestController` API는 [`ApiResponse<T>`](file:///c:/Users/klad3/OneDrive/바탕 화면/IT/프로젝트/팀플(스프링부트) 2026.10.26~2026/kintai/src/main/java/com/example/kintai/global/common/ApiResponse.java)를 반환합니다.
* 성공 시: `ApiResponse.ok(data)` 또는 `ApiResponse.ok("処理が完了しました。", data)`
  ```json
  { "success": true, "message": "SUCCESS", "data": { ... } }
  ```
* 실패 시 (`IllegalArgumentException` 또는 `@Valid` 에러 발생 시 `GlobalExceptionHandler`가 자동 변환):
  ```json
  { "success": false, "message": "エラーが発生しました。", "data": null }
  ```

---

### 1.5 ⭐ [일본 현지 취업 필수] UI 일본어(日本語) 표준화 원칙 및 핵심 용어집 (用語集)
본 프로젝트는 **일본 IT 현지 기업 취업 및 포트폴리오 시연**을 주 목적으로 합니다.  
따라서 **모든 브라우저 화면(Thymeleaf HTML)의 메뉴명, 버튼, 테이블 헤더, 캘린더, 모달 안내문구, 알림 메시지, 그리고 엑셀 출력 서식은 100% 자연스러운 비즈니스 일본어(日本語)**로 작성해야 합니다.

AI에게 화면 코드를 생성시킬 때 반드시 아래의 **공식 용어 대조표**를 엄격히 준수하도록 지시합니다.

| 분류 | 한국어 개념 | 일본어 표준 UI 표기 | 읽는 법 (히라가나/가타카나) | 실제 사용 화면 및 컴포넌트 |
| :--- | :--- | :--- | :--- | :--- |
| **근태 타각** | **출근** | **出勤** | しゅっきん | 타임카드 메인 초록색 버튼 |
| **근태 타각** | **퇴근** | **退勤** | たいきん | 타임카드 메인 빨간색 버튼 |
| **근태 타각** | **휴게 시작** | **休憩開始** | きゅうけいかいし | 타임카드 주황색 버튼 |
| **근태 타각** | **휴게 복귀** | **休憩戻り** | きゅうけいもどり | 타임카드 파란색 버튼 |
| **근태 타각** | **타각 (출퇴근기록)** | **打刻** | だこく | 시스템 전체 명칭 (勤怠打刻) |
| **근태 타각** | **근무중 / 휴게중** | **勤務中 / 休憩中** | きんむちゅう / きゅうけいちゅう | 실시간 상태 배지 |
| **근태 감사** | **근태 수정 신청** | **勤怠修正申請** | しゅうせいしんせい | 알바생 타각 오류 수정 모달 |
| **근태 감사** | **승인 / 반려** | **承認 / 却下** | しょうにん / きゃっか | 점주 감사 검토 버튼 |
| **시프트** | **희망 시프트** | **希望シフト** | きぼうしふと | 알바생 제출 시프트 (주황색 배지) |
| **시프트** | **확정 시프트** | **確定シフト** | かくていしふと | 점주 확정 시프트 (파란색 배지) |
| **시프트** | **미배정** | **未割当** | みわりあて | 반려/미배정 시프트 (회색 배지) |
| **시프트** | **결원 경고** | **人員不足警告** | じんいんぶそくけいこく | 피크타임 인원 미달 알림창 |
| **급여 정산** | **기본급** | **基本給** | きほんきゅう | 100% 실근무 수당 |
| **급여 정산** | **야간 수당 (25%)** | **深夜手当** | しんやてあて | 22:00~05:00 야간 할증분 |
| **급여 정산** | **연장 수당 (25%)** | **残業手当** | ざんぎょうてあて | 1일 8시간 초과 연장 할증분 |
| **급여 정산** | **교통비 (통근수당)** | **通勤手当 (交通費)** | つうきんてあて (こうつうひ) | 비과세 교통비 지급액 |
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

### 2.1 기본 7개 테이블 확정 DDL (`src/main/resources/db/schema.sql`)

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
    invite_code VARCHAR(20) NOT NULL UNIQUE COMMENT '직원 등록용 초대 코드',
    qr_token VARCHAR(100) NOT NULL COMMENT 'QR 출퇴근 인증 토큰',
    open_time TIME NOT NULL DEFAULT '09:00:00' COMMENT '기본 오픈 시간',
    close_time TIME NOT NULL DEFAULT '23:00:00' COMMENT '기본 마감 시간',
    pay_cutoff_day INT NOT NULL DEFAULT 99 COMMENT '급여 마감일 (1~28 또는 99=말일)',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT fk_store_owner FOREIGN KEY (owner_id) REFERENCES member (member_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 3. 점포 소속 직원 (다점포 매핑 및 개별 시급/교통비 근로계약)
CREATE TABLE IF NOT EXISTS store_staff (
    store_staff_id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '점포 직원 매핑 PK',
    store_id BIGINT NOT NULL COMMENT '점포 FK',
    member_id BIGINT NOT NULL COMMENT '직원 회원 FK',
    hourly_wage INT NOT NULL COMMENT '기본 시급(엔/원, 예: 1163)',
    daily_transport_fee INT NOT NULL DEFAULT 0 COMMENT '1일 왕복 교통비(예: 500)',
    monthly_transport_limit INT NOT NULL DEFAULT 15000 COMMENT '월 교통비 상한액(예: 15000)',
    employment_status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE' COMMENT '재직상태 (PENDING, ACTIVE, RESIGNED)',
    joined_date DATE NOT NULL COMMENT '입사일',
    resigned_date DATE NULL COMMENT '퇴사일',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT uq_store_member UNIQUE (store_id, member_id),
    CONSTRAINT fk_staff_store FOREIGN KEY (store_id) REFERENCES store (store_id),
    CONSTRAINT fk_staff_member FOREIGN KEY (member_id) REFERENCES member (member_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 4. 시프트 스케줄 (팀원 A: 희망 시프트 제출 및 점주 확정 통합 테이블)
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
    CONSTRAINT fk_shift_staff FOREIGN KEY (store_staff_id) REFERENCES store_staff (store_staff_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 5. 출퇴근 근태 기록 (팀원 B: 타임카드 및 일별 실근무/야간/연장 분 단위 산출)
CREATE TABLE IF NOT EXISTS attendance (
    attendance_id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '근태 PK',
    store_id BIGINT NOT NULL COMMENT '점포 FK',
    store_staff_id BIGINT NOT NULL COMMENT '점포 직원 FK',
    shift_id BIGINT NULL COMMENT '연결된 확정 시프트 FK (미계획 출근 시 NULL)',
    work_date DATE NOT NULL COMMENT '출근 기준일',
    clock_in_at DATETIME NOT NULL COMMENT '출근 타각 일시',
    clock_out_at DATETIME NULL COMMENT '퇴근 타각 일시',
    break_start_at DATETIME NULL COMMENT '휴게 시작 일시',
    break_end_at DATETIME NULL COMMENT '휴게 종료 일시',
    total_break_minutes INT NOT NULL DEFAULT 0 COMMENT '총 휴게시간(분)',
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
    CONSTRAINT fk_att_staff FOREIGN KEY (store_staff_id) REFERENCES store_staff (store_staff_id),
    CONSTRAINT fk_att_shift FOREIGN KEY (shift_id) REFERENCES shift_schedule (shift_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 6. 근태 수정 요청 (팀원 B: 타각 누락/오류 수정 및 점주 승인 감사 원장)
CREATE TABLE IF NOT EXISTS attendance_mod_request (
    mod_request_id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '근태 수정 요청 PK',
    attendance_id BIGINT NULL COMMENT '대상 근태 FK (타각 완전 누락 사후 생성 시 NULL 허용)',
    store_staff_id BIGINT NOT NULL COMMENT '요청 직원 FK',
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
    CONSTRAINT fk_mod_staff FOREIGN KEY (store_staff_id) REFERENCES store_staff (store_staff_id),
    CONSTRAINT fk_mod_reviewer FOREIGN KEY (reviewer_id) REFERENCES member (member_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 7. 월별 급여 정산 (팀원 B: 일본 노동기준법 150% 계산 스냅샷 및 엑셀 출력 원본)
CREATE TABLE IF NOT EXISTS payroll (
    payroll_id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '급여 정산 PK',
    store_id BIGINT NOT NULL COMMENT '점포 FK',
    store_staff_id BIGINT NOT NULL COMMENT '점포 직원 FK',
    target_year_month CHAR(7) NOT NULL COMMENT '귀속 연월 (YYYY-MM)',
    work_days_count INT NOT NULL DEFAULT 0 COMMENT '총 출근 일수',
    total_work_minutes INT NOT NULL DEFAULT 0 COMMENT '월 총 실근무시간(분)',
    total_night_minutes INT NOT NULL DEFAULT 0 COMMENT '월 총 야간근로시간(분)',
    total_overtime_minutes INT NOT NULL DEFAULT 0 COMMENT '월 총 연장근로시간(분)',
    applied_hourly_wage INT NOT NULL COMMENT '정산 시점 기본 시급 스냅샷',
    base_pay INT NOT NULL DEFAULT 0 COMMENT '기본급 (실근무시간 기준 100%)',
    night_allowance INT NOT NULL DEFAULT 0 COMMENT '야간 근로 할증 수당 (25% 가산분)',
    overtime_allowance INT NOT NULL DEFAULT 0 COMMENT '연장 잔업 할증 수당 (25% 가산분)',
    transport_allowance INT NOT NULL DEFAULT 0 COMMENT '교통비 지급액 (월 상한액 적용 후)',
    gross_pay INT NOT NULL DEFAULT 0 COMMENT '총 지급액',
    payroll_status VARCHAR(20) NOT NULL DEFAULT 'DRAFT' COMMENT '상태 (DRAFT, CONFIRMED, PAID)',
    confirmed_at DATETIME NULL COMMENT '정산 확정 일시',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT uq_payroll_staff_month UNIQUE (store_staff_id, target_year_month),
    CONSTRAINT fk_payroll_store FOREIGN KEY (store_id) REFERENCES store (store_id),
    CONSTRAINT fk_payroll_staff FOREIGN KEY (store_staff_id) REFERENCES store_staff (store_staff_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
```

### 2.2 [3~4인 확장 시] 추가 실행 DDL (선택 사항)
3인 이상으로 LINE 봇이나 피크타임 인원 설정 기능을 확장할 경우 아래 SQL만 추가로 실행합니다.
```sql
-- 회원 테이블에 LINE 봇 연동용 고유 ID 컬럼 추가
ALTER TABLE member ADD COLUMN line_user_id VARCHAR(100) NULL COMMENT 'LINE Messaging API User ID';

-- 점포 테이블에 피크타임 최소 필요 인원 기준 컬럼 추가
ALTER TABLE store ADD COLUMN min_peak_staff INT NOT NULL DEFAULT 3 COMMENT '피크타임(18~22시) 최소 권장 인원';
```

---

## 제3장. [도메인 1] 회원 인증 및 점포·계약 마스터 (`member`, `store`, `store_staff`)

> **담당**: 팀원 A (공통 마스터)  
> **패키지**: `com.example.kintai.domain.member`, `com.example.kintai.domain.store`

### 3.1 도메인 개론 및 설계 의도
* **왜 `member`와 `store_staff`를 분리했는가?**: 알바생 1명(`member_id = 2`)이 신주쿠점(`store_id = 1`, 시급 1,163엔)과 시부야점(`store_id = 2`, 시급 1,300엔) 두 곳에서 일할 수 있도록 **회원 인증 정보(`member`)와 근로 계약 조건(`store_staff`)을 제3정규화로 분리**했습니다.
* **초대 코드(`invite_code`)와 QR 토큰(`qr_token`)**: 점주가 매장을 만들면 8자리 초대 코드(알바생 합류용)와 고유 QR 토큰(매장 현장 타각 검증용)이 자동 발급됩니다.

### 3.2 화면(View) 라우팅 및 UI 명세
1. **`GET /login` $\rightarrow$ `templates/member/login.html` & `GET /join` $\rightarrow$ `templates/member/join.html`**
   * 이메일/비밀번호 로그인 화면 및 점주(`ROLE_OWNER`) / 알바생(`ROLE_STAFF`) 권한 선택 회원가입 폼.
2. **`GET /stores` $\rightarrow$ `templates/store/manage.html`**
   * **Model 바인딩**:
     * `store`: 현재 선택된 점포 상세 정보 (`StoreDto`)
     * `staffList`: 해당 점포에 소속된 알바생 계약 목록 (`List<StoreStaffDetailDto>` - `member.name`, `member.email`, `member.phone` 조인)
   * **주요 UI 구성**:
     * 상단 카드: 매장명, 영업시간(`openTime ~ closeTime`), 급여 마감일, **초대 코드 복사 버튼**, **출퇴근 QR 코드(또는 토큰) 모달 보기 버튼**, **[신규 점포 개설] 모달 버튼**.
     * 하단 테이블: 소속 알바생 목록 (이름, 이메일, 연락처, **기본 시급**, **1일 왕복 교통비**, **월 교통비 상한액**, 재직상태, 입사일, **[계약 조건 수정] 버튼**).
     * 우측 상단 보조 버튼: **[초대 코드로 매장 합류하기(알바생 테스트용)] 모달 버튼**.

### 3.3 REST API 상세 명세

#### ① 회원가입 (`POST /api/members/join`) 및 로그인 (`POST /api/members/login`)
* **회원가입 Request JSON (`MemberJoinRequestDto`)**:
  ```json
  {
    "email": "staff3@kintai.com",
    "password": "Password123!",
    "name": "高橋 健太",
    "phone": "080-9999-8888",
    "role": "ROLE_STAFF"
  }
  ```
  * 이메일 중복 검증 후 `PasswordEncoder.encode(password)`로 BCrypt 암호화하여 `member` 테이블에 INSERT.
* **로그인 Request JSON (`MemberLoginRequestDto`)**:
  ```json
  {
    "email": "owner@kintai.com",
    "password": "1234"
  }
  ```
  * `passwordEncoder.matches(rawPassword, encodedPassword)` 검증 후 `HttpSession`에 `LOGIN_MEMBER` (`SessionMemberDto`: `memberId`, `name`, `role`, `defaultStoreId`, `defaultStoreStaffId`) 저장.

#### ② 신규 점포 개설 (`POST /api/stores`)
* **Request JSON (`StoreCreateRequestDto`)**:
  ```json
  {
    "ownerId": 1,
    "storeName": "東京 渋谷2号店",
    "openTime": "10:00",
    "closeTime": "23:30",
    "payCutoffDay": 25
  }
  ```
* **서버 처리 로직**:
  1. `inviteCode`: `"ST" + UUID.randomUUID().toString().substring(0, 6).toUpperCase()` 생성.
  2. `qrToken`: `"TOKEN_QR_" + UUID.randomUUID().toString()` 생성.
  3. `openTime`, `closeTime`을 `LocalTime`으로 변환하여 `store` 테이블에 INSERT.

#### ③ 알바생 초대 코드로 매장 합류 (`POST /api/stores/join-staff`)
* **Request JSON (`StaffJoinRequestDto`)**:
  ```json
  {
    "memberId": 2,
    "inviteCode": "SHINJUKU01",
    "hourlyWage": 1163,
    "dailyTransportFee": 500,
    "monthlyTransportLimit": 15000
  }
  ```
* **서버 처리 로직**:
  1. `StoreMapper.findByInviteCode(inviteCode)`로 점포 조회 (없으면 `IllegalArgumentException("유효하지 않은 초대 코드입니다.")`).
  2. `StoreMapper.existsStoreStaff(storeId, memberId)`로 이미 등록된 직원인지 중복 체크.
  3. `joined_date = LocalDate.now()`, `employment_status = 'ACTIVE'`로 `store_staff` 테이블에 INSERT.

#### ④ 알바생 시급·교통비 계약 조건 수정 (`PUT /api/stores/staff/{storeStaffId}`)
* **Request JSON (`StaffContractUpdateDto`)**:
  ```json
  {
    "hourlyWage": 1250,
    "dailyTransportFee": 600,
    "monthlyTransportLimit": 15000,
    "employmentStatus": "ACTIVE"
  }
  ```
* **서버 처리 로직**:
  * 도쿄도 최저임금 검증(`hourlyWage < 1163`일 경우 경고 또는 예외 처리 가능).
  * `store_staff` 테이블의 `hourly_wage`, `daily_transport_fee`, `monthly_transport_limit`, `employment_status` 업데이트.
  * *(주의: 여기서 시급을 올려도 이미 정산된 과거 `payroll` 테이블의 `applied_hourly_wage`는 스냅샷이므로 절대 변하지 않음!)*

---

## 제4장. [도메인 2] 통합 시프트 스케줄러 (`shift_schedule` & FullCalendar)

> **담당**: 팀원 A  
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
* **파라미터 주의사항**: FullCalendar는 `start`, `end` 파라미터를 `"2026-09-27T00:00:00+09:00"` 형태의 ISO 문자열로 전송하므로, 컨트롤러에서는 `@RequestParam("start") String start`로 받아 `LocalDate.parse(start.substring(0, 10))`으로 안전하게 날짜를 추출해 Mapper의 `#{startDate}`, `#{endDate}`에 전달합니다.
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
    AND s.work_date BETWEEN #{startDate} AND #{endDate}
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
  * `status == 'REJECTED'`인 경우:
    * `start` = `requestedStartAt`, `end` = `requestedEndAt`
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
  * 만약 `requestedEndTime`("02:00")이 `requestedStartTime`("18:00")보다 작거나 같으면(자정 넘김 근무), **`requested_end_at`의 날짜는 `workDate.plusDays(1)`로 자동 변환**하여 `DATETIME`을 생성해야 합니다! (`TimeUtils.resolveEndDateTime()` 사용 가능)
  * 코드 공식:
    ```java
    LocalDate workDate = LocalDate.parse(dto.getWorkDate());
    LocalTime startLocalTime = LocalTime.parse(dto.getRequestedStartTime());
    LocalTime endLocalTime = LocalTime.parse(dto.getRequestedEndTime());
    LocalDateTime startAt = LocalDateTime.of(workDate, startLocalTime);
    LocalDate endDate = endLocalTime.isAfter(startLocalTime) ? workDate : workDate.plusDays(1);
    LocalDateTime endAt = LocalDateTime.of(endDate, endLocalTime);
    ```

#### ③ 점주 시프트 확정 및 일본 법정 휴게시간 자동 산정 (`PUT /api/shifts/{shiftId}/confirm`)
* **Request JSON (`ShiftConfirmDto`)**:
  ```json
  {
    "confirmedStartTime": "17:00",
    "confirmedEndTime": "23:30",
    "plannedBreakMinutes": null,
    "status": "CONFIRMED",
    "ownerMemo": "확인 완료"
  }
  ```
* **일본 노동기준법 제34조 기반 휴게시간 자동 계산 공식**:
  * 만약 점주가 `plannedBreakMinutes`를 비워두거나 기본값으로 넘기면, 총 체류 시간(`totalMinutes = Duration.between(startAt, endAt).toMinutes()`)을 기준으로 자동 부여하거나 미달 시 검증합니다:
    * 총 구속시간이 **8시간(480분) 초과**: 최소 **60분** 휴게 필수
    * 총 구속시간이 **6시간(360분) 초과 ~ 8시간 이하**: 최소 **45분** 휴게 필수
    * 6시간 이하: 0분

---

## 제5장. [도메인 3] 감사추적 출퇴근 타각 및 수정 승인 워크플로우 (`attendance`, `attendance_mod_request`)

> **담당**: 팀원 B  
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
1. **`GET /attendance` $\rightarrow$ `templates/attendance/punch.html`**
   * **상단 [실시간 타임카드 타각 패널]**:
     * 현재 시각 실시간 디지털 시계 (`1초 단위 JS 갱신`).
     * 현재 선택된 직원 및 오늘의 근태 상태 배지 (`미출근` / `근무중` / `휴게중` / `퇴근완료`).
     * 점포 인증 토큰 입력칸 (기본값으로 현재 점포의 `qr_token`이 미리 채워져 있어 1클릭 테스트가 가능하며, 일부러 틀린 토큰을 넣으면 "매장 현장이 아닙니다(토큰 불일치)" 에러 시연 가능).
     * **대형 타각 버튼 4개**: `[🟢 출근하기]`, `[☕ 휴게 시작]`, `[🔥 휴게 복귀]`, `[🔴 퇴근하기]` (현재 상태에 따라 누를 수 있는 버튼만 활성화).
   * **하단 [내 월별 출퇴근 기록표 & 수정 신청]**:
     * 일자별 출근시각, 퇴근시각, 휴게(분), **실근무(분)**, **야간(분)**, **연장(분)**, 상태, **`[수정 신청]` 버튼**.
     * `[수정 신청]` 클릭 시 모달이 열리며 수정할 출퇴근 시각, 휴게 분, **수정 사유(필수)**를 입력해 제출.
2. **`GET /attendance/mod-requests` $\rightarrow$ `templates/attendance/mod-list.html` (점주용 감사 화면)**
   * 알바생들이 올린 근태 수정 요청 목록을 표로 보여줌.
   * **핵심 UI**: `[변경 전(Before) 출퇴근 시각]` $\rightarrow$ `[수정 요청(Requested) 출퇴근 시각]`이 빨간색/파란색 비교 뷰로 나란히 표시되고, 알바생의 사유를 확인한 뒤 점주가 **`[승인]`** 또는 **`[반려]`** 버튼을 누름.

### 5.3 ⭐ [핵심 알고리즘 명세] 일별 근로시간 3종(실근무·야간·연장) 무결성 계산기

AI가 코드를 작성할 때 가장 실수하기 쉬운 부분이 **야간 시간대(22:00 ~ 익일 05:00) 교집합 산출과 휴게시간 차감**입니다. `AttendanceTimeCalculator` 유틸 또는 `AttendanceService` 내부에 아래의 **검증된 Java 알고리즘을 그대로 사용**하십시오.

```java
package com.example.kintai.domain.attendance.util;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

public class AttendanceTimeCalculator {

    public record WorkTimeResult(
            int netWorkMinutes,    // 실근무시간(분)
            int nightWorkMinutes,  // 야간근로시간(분, 22:00~05:00)
            int overtimeMinutes    // 법정외 연장시간(분, 1일 8시간=480분 초과분)
    ) {}

    /**
     * 출퇴근 일시와 휴게 정보를 바탕으로 일본 노동기준법 기준 일별 근로시간 3종을 분(Minute) 단위로 산출한다.
     */
    public static WorkTimeResult calculate(
            LocalDateTime clockIn,
            LocalDateTime clockOut,
            LocalDateTime breakStart,
            LocalDateTime breakEnd,
            int totalBreakMinutes) {

        if (clockIn == null || clockOut == null || !clockOut.isAfter(clockIn)) {
            return new WorkTimeResult(0, 0, 0);
        }

        // 1. 총 체류 시간(분) 및 실근무시간(분)
        int grossMinutes = (int) Duration.between(clockIn, clockOut).toMinutes();
        int netWorkMinutes = Math.max(0, grossMinutes - totalBreakMinutes);

        // 2. 총 체류 시간 중 야간 구간(22:00 ~ 익일 05:00)과 겹치는 분(Minute) 합산
        int grossNightMinutes = getOverlapNightMinutes(clockIn, clockOut);

        // 3. 야간 구간에서 차감할 휴게시간 산출
        int nightBreakMinutes = 0;
        if (breakStart != null && breakEnd != null && breakEnd.isAfter(breakStart)) {
            // 실제 휴게 타각 시각이 있으면 야간 구간과 겹치는 휴게시간만 정확히 차감
            nightBreakMinutes = getOverlapNightMinutes(breakStart, breakEnd);
        } else if (totalBreakMinutes > 0) {
            // 사후 수정 등으로 휴게 '분(Minute)'만 입력된 경우:
            // 근로자 유리 원칙에 따라 주간 근무시간에서 우선 차감하고, 주간 시간을 초과하는 휴게만 야간에서 차감
            int dayGrossMinutes = grossMinutes - grossNightMinutes;
            nightBreakMinutes = Math.max(0, totalBreakMinutes - dayGrossMinutes);
        }

        int nightWorkMinutes = Math.max(0, grossNightMinutes - nightBreakMinutes);

        // 4. 연장근로시간 (일본 노동기준법 제32조: 1일 실근무 8시간 = 480분 초과분)
        int overtimeMinutes = Math.max(0, netWorkMinutes - 480);

        return new WorkTimeResult(netWorkMinutes, nightWorkMinutes, overtimeMinutes);
    }

    /**
     * 특정 시간 구간(start ~ end)이 야간 시간대(22:00 ~ 익일 05:00)와 겹치는 분(Minute)을 구한다.
     * 새벽 출근(00:00~05:00)과 심야 퇴근(22:00~익일 05:00)을 모두 완벽히 처리한다.
     */
    private static int getOverlapNightMinutes(LocalDateTime start, LocalDateTime end) {
        int totalNight = 0;
        LocalDate baseDate = start.toLocalDate().minusDays(1);
        LocalDate endDate = end.toLocalDate().plusDays(1);

        // 출근 전날 밤 22시 ~ 퇴근 다음날 새벽 5시까지 모든 [22:00 ~ 익일 05:00] 윈도우와 교집합 검사
        for (LocalDate d = baseDate; !d.isAfter(endDate); d = d.plusDays(1)) {
            LocalDateTime nightWindowStart = LocalDateTime.of(d, LocalTime.of(22, 0));
            LocalDateTime nightWindowEnd = LocalDateTime.of(d.plusDays(1), LocalTime.of(5, 0));

            LocalDateTime overlapStart = start.isAfter(nightWindowStart) ? start : nightWindowStart;
            LocalDateTime overlapEnd = end.isBefore(nightWindowEnd) ? end : nightWindowEnd;

            if (overlapEnd.isAfter(overlapStart)) {
                totalNight += (int) Duration.between(overlapStart, overlapEnd).toMinutes();
            }
        }
        return totalNight;
    }
}
```

### 5.4 REST API 상세 명세

1. **실시간 타각 (`POST /api/attendance/punch`)**:
   * Request: `{ "storeId": 1, "storeStaffId": 1, "qrToken": "TOKEN_QR_SHINJUKU_001", "punchType": "CLOCK_IN" }`
   * **[핵심 로직 1: 시프트 자동 매핑]**: `punchType == 'CLOCK_IN'` 시, 당일(`work_date = today`) 해당 직원의 `shift_schedule` 중 `status = 'CONFIRMED'`인 시프트를 조회하여 존재하면 `attendance.shift_id`에 자동으로 외래키를 연결합니다 (미배정 출근 시 `NULL`).
   * **[핵심 로직 2: 퇴근 계산]**: `punchType == 'CLOCK_OUT'` 시, 위 `AttendanceTimeCalculator.calculate()`를 호출하여 `net_work_minutes`, `night_work_minutes`, `overtime_minutes`를 산출해 DB에 저장하고 `attendance_status = 'COMPLETED'`로 변경.
2. **근태 수정 요청 제출 (`POST /api/attendance/mod-requests`)**:
   * Request:
     ```json
     {
       "attendanceId": 1,                           // 타각 누락으로 행 자체가 없는 경우 null 허용
       "storeStaffId": 1,
       "workDate": "2026-10-27",                    // 근무 기준일
       "requestedClockInAt": "2026-10-27T18:00:00",
       "requestedClockOutAt": "2026-10-28T01:00:00",
       "requestedBreakMinutes": 45,
       "reason": "마감 후 청소로 늦게 퇴근했으나 버튼을 누르지 못했습니다."
     }
     ```
   * **[핵심 로직 3: 타각 완전 누락 시 사후 생성 처리]**:
     * `attendanceId != null`: 기존 근태의 `clock_in_at`, `clock_out_at`, `total_break_minutes`를 `before_*`에 복사하고 `attendance_status = 'MOD_REQUESTED'`로 변경.
     * `attendanceId == null` (출퇴근 둘 다 까먹어서 Row가 없는 날): `before_*`는 NULL/0으로 두고 `PENDING` 요청 생성.
3. **점주 승인/반려 처리 (`PUT /api/attendance/mod-requests/{modRequestId}/review`)**:
   * Request: `{ "reviewerId": 1, "action": "APPROVE", "rejectReason": null }`
   * 승인(`APPROVE`) 시:
     * 기존 `attendanceId`가 있는 경우: 수정 요청 시간으로 3종 근로시간 재계산 후 UPDATE (`clock_in_method = 'MANUAL_APPROVED'`).
     * `attendanceId`가 없던 신규 생성 요청인 경우: `attendance` 테이블에 신규 INSERT를 수행하고 `attendance_status = 'COMPLETED'`, `clock_in_method = 'MANUAL_APPROVED'`로 적재.

---

## 제6장. [도메인 4] 일본 노동기준법 급여 정산 엔진 및 Apache POI 엑셀 대장 (`payroll`)

> **담당**: 팀원 B  
> **패키지**: `com.example.kintai.domain.payroll`

### 6.1 도메인 개론 및 법리적 설계 포인트
1. **왜 기본급(100%) + 야간(25%) + 연장(25%)으로 분리 가산하는가?**
   * 만약 주간 시간(100%), 야간 시간(125%), 야간+연장 시간(150%)을 구간별로 쪼개서 계산하면 수식이 극도로 복잡해집니다.
   * 반면, **① 전체 실근무시간에 대해 기본 시급(100%)을 먼저 주고, ② 야간 근로분에 대해 할증분(+25%)만 추가로 더하고, ③ 8시간 초과 연장 근로분에 대해 할증분(+25%)만 추가로 더하면**, 야간이면서 동시에 연장인 구간은 자동으로 `100% + 25% + 25% = 150%`가 되어 **일본 노동기준법 제37조의 심야 잔업 중복 할증 규정을 단 한 줄의 수식 충돌도 없이 100% 완벽하게 만족**합니다.
2. **점포별 급여 마감일(`pay_cutoff_day`) 대응**:
   * 일본 매장은 `말일 마감(99)`, `15일 마감(15)`, `25일 마감(25)` 등 매장마다 급여 산정 기간이 다릅니다.

### 6.2 ⭐ [핵심 알고리즘 명세] 마감일 산출 공식 & 오차 0건 급여 정산 코드

AI에게 급여 정산 서비스를 만들게 할 때 아래 코드를 그대로 적용하십시오. **중간 나눗셈 반올림 오차를 원천 배제한 무결성 코드**입니다.

```java
package com.example.kintai.domain.payroll.util;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.YearMonth;

public class PayrollCalculator {

    public record DateRange(LocalDate startDate, LocalDate endDate) {}

    public record PayrollCalcResult(
            int basePay,             // 기본급 (100%)
            int nightAllowance,      // 야간 할증 수당 (25%)
            int overtimeAllowance,   // 연장 할증 수당 (25%)
            int transportAllowance,  // 교통비 (상한 적용)
            int grossPay             // 총 지급액
    ) {}

    /**
     * 1. 점포의 급여 마감일(payCutoffDay)에 따른 귀속연월(YYYY-MM)의 실제 근태 집계 시작일~종료일 산출
     * 예: targetYearMonth = "2026-10"
     * - payCutoffDay가 99(말일) 또는 28 이상이면 -> 2026-10-01 ~ 2026-10-31
     * - payCutoffDay가 15(15일 마감)이면 -> 2026-09-16 ~ 2026-10-15
     * - payCutoffDay가 25(25일 마감)이면 -> 2026-09-26 ~ 2026-10-25
     */
    public static DateRange getCutoffDateRange(String targetYearMonth, int payCutoffDay) {
        YearMonth ym = YearMonth.parse(targetYearMonth);
        if (payCutoffDay >= 28) {
            return new DateRange(ym.atDay(1), ym.atEndOfMonth());
        }
        LocalDate endDate = ym.atDay(payCutoffDay);
        LocalDate startDate = ym.minusMonths(1).atDay(payCutoffDay).plusDays(1);
        return new DateRange(startDate, endDate);
    }

    /**
     * 2. 일본 노동기준법 제37조 및 행정통달(기발 제150호: 50전 미만 절사, 50전 이상 반올림 HALF_UP) 무결성 연산
     * 핵심: 나눗셈을 먼저 하지 않고, 분자(분 * 시급 * 할증률)를 정수로 먼저 곱한 뒤 마지막에 단 1회만 나눈다.
     */
    public static PayrollCalcResult calculateMonthlyPay(
            int appliedHourlyWage,
            int workDaysCount,
            int totalWorkMinutes,
            int totalNightMinutes,
            int totalOvertimeMinutes,
            int dailyTransportFee,
            int monthlyTransportLimit) {

        // 기본급 = (총 실근무 분 * 시급) / 60 -> 소수점 첫째자리 반올림(HALF_UP)
        int basePay = BigDecimal.valueOf((long) totalWorkMinutes * appliedHourlyWage)
                .divide(BigDecimal.valueOf(60), 0, RoundingMode.HALF_UP)
                .intValue();

        // 야간 할증 수당(25%) = (총 야간 분 * 시급 * 25) / (60 * 100) -> 단 1회 반올림(HALF_UP)
        int nightAllowance = BigDecimal.valueOf((long) totalNightMinutes * appliedHourlyWage * 25)
                .divide(BigDecimal.valueOf(6000), 0, RoundingMode.HALF_UP)
                .intValue();

        // 연장 할증 수당(25%) = (총 연장 분 * 시급 * 25) / (60 * 100) -> 단 1회 반올림(HALF_UP)
        int overtimeAllowance = BigDecimal.valueOf((long) totalOvertimeMinutes * appliedHourlyWage * 25)
                .divide(BigDecimal.valueOf(6000), 0, RoundingMode.HALF_UP)
                .intValue();

        // 비과세 통근수당(교통비) = MIN(출근일수 * 1일 왕복교통비, 월 상한액)
        int transportAllowance = Math.min(workDaysCount * dailyTransportFee, monthlyTransportLimit);

        // 총 지급액
        int grossPay = basePay + nightAllowance + overtimeAllowance + transportAllowance;

        return new PayrollCalcResult(basePay, nightAllowance, overtimeAllowance, transportAllowance, grossPay);
    }
}
```

### 6.3 화면(View) 및 REST API 명세
1. **`GET /payroll` $\rightarrow$ `templates/payroll/list.html`**
   * 상단: 귀속연월 선택기(기본값 `"2026-10"`), **`[⚡ 당월 급여 일괄 정산 실행]`** 버튼, **`[📊 세무용 급여대장 엑셀(.xlsx) 다운로드]`** 버튼.
   * 중앙 요약 카드: 당월 총 지급 인건비 합계, 총 근무시간 합계, 야간/연장 할증 수당 합계, 교통비 합계.
   * 하단 테이블: 직원별 정산 내역 (`직원명`, `적용시급(스냅샷)`, `출근일수`, `실근무(시간:분)`, `기본급`, `야간할증(25%)`, `연장할증(25%)`, `교통비`, **`총 지급액`**, `상태(DRAFT/CONFIRMED)`, **`[급여명세서 상세]`**, **`[확정]`** 버튼).
2. **`POST /api/payroll/calculate` (급여 일괄 계산 및 Upsert)**
   * Request: `{ "storeId": 1, "targetYearMonth": "2026-10" }`
   * 점포의 `pay_cutoff_day`를 조회해 `startDate ~ endDate`를 구한 뒤, 해당 기간 내 `attendance_status = 'COMPLETED'`인 기록을 직원별로 합산하여 `PayrollCalculator.calculateMonthlyPay()` 실행 후 `payroll` 테이블에 저장(`ON DUPLICATE KEY UPDATE`).
   * **SQL 집계 주의사항**: 출근 일수가 0일인 직원의 `SUM()` 결과가 `NULL`이 되어 NPE가 발생하지 않도록 반드시 `COALESCE(SUM(net_work_minutes), 0)`과 같이 NULL 방어 함수를 사용합니다.
3. **`GET /api/payroll/excel?storeId=1&targetYearMonth=2026-10` (Apache POI 엑셀 다운로드)**
   * `XSSFWorkbook`을 생성하여 세무사 제출용 포맷의 엑셀 시트를 생성하고 `HttpServletResponse` 스트림으로 출력.
4. **`GET /api/payroll/{payrollId}/detail` (개인별 급여명세서 상세 모달 조회)**
   * **응답 JSON (`PayrollDetailResponseDto`)**:
     ```json
     {
       "success": true,
       "data": {
         "payrollId": 1,
         "staffName": "佐藤 花子",
         "targetYearMonth": "2026-10",
         "appliedHourlyWage": 1163,
         "summary": {
           "workDaysCount": 15,
           "totalWorkMinutes": 5400,
           "totalNightMinutes": 900,
           "totalOvertimeMinutes": 360,
           "basePay": 104670,
           "nightAllowance": 4361,
           "overtimeAllowance": 1745,
           "transportAllowance": 7500,
           "grossPay": 118276,
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
             "overtimeMinutes": 0
           }
         ]
       }
     }
     ```
   * 화면에서 직원의 `[급여명세서 상세]`를 클릭하면 Fetch API로 위 JSON을 호출해 모달 창에 일자별 근무 기록과 계산 내역을 깔끔한 일본어 표로 띄워줍니다.

---

## 제7장. [확장 도메인 5 - 3~4인용] 일본 현지 특화 LINE 메신저 봇 & 실시간 알림 센터

> **담당**: 3인 팀의 팀원 C (또는 4인 팀의 팀원 D)  
> **패키지**: `com.example.kintai.domain.notification`

### 7.1 왜 이 기능이 일본 취업에서 강력한 무기인가?
* 일본 아르바이트 현장의 82.4%가 LINE을 씁니다.
* **사업자등록증이 필요한 카카오톡 알림톡과 달리, LINE Messaging API는 개인 개발자 계정으로 무료 개설 및 월 수백 건 무료 Push 전송이 가능**합니다.
* 스프링 부트의 `RestClient` 또는 `RestTemplate`으로 `https://api.line.me/v2/bot/message/push`에 HTTP POST 요청만 보내면 실제 스마트폰 LINE 앱으로 즉시 알림이 옵니다.

### 7.2 구현 스펙 (DB 스키마 변경 없이도 즉시 동작 가능)
1. **`application.yaml` 설정**:
   ```yaml
   line:
     bot:
       enabled: true # 키가 없을 때는 콘솔 로그(Mock)로 출력하도록 안전장치 설계
       channel-token: "발급받은_채널_토큰"
   ```
2. **핵심 발송 메서드 (`LineNotificationService.java`)**:
   * **시프트 확정 알림**: 점주가 시프트를 확정(`PUT /api/shifts/{id}/confirm`)하면 `[신주쿠점] 사토 님, 10/28(수) 18:00~23:00 시프트가 확정되었습니다.` 발송.
   * **근태 수정 요청 알림**: 알바생이 타각 수정을 신청(`POST /api/attendance/mod-requests`)하면 점주에게 `[근태 수정 요청] 사토 알바생이 10/27 퇴근 시각 수정을 요청했습니다. 사유: 마감 청소` 발송.
   * **급여 명세서 발행 알림**: 점주가 급여를 확정하면 `[급여 명세서 도착] 2026년 10월분 급여(총 85,400엔) 정산이 완료되었습니다.` 발송.
   * *(꿀팁: 실제 LINE 토큰을 넣기 전에는 화면 우측 하단에 **"LINE 발송 미리보기 토스트(연두색 말풍선 UI)"**가 뜨도록 함께 구현해 두면, 발표장에서 스마트폰 화면과 웹 화면 양쪽으로 시각적 효과를 극대화할 수 있습니다!)*

---

## 제8장. [확장 도메인 6 - 4인용] 경영 분석(BI) 대시보드 & 일본 세법 "103만 엔의 벽" 시뮬레이터

> **담당**: 4인 팀의 팀원 D  
> **패키지**: `com.example.kintai.domain.analytics`

### 8.1 도메인 개론: 일본의 "103만 엔 / 130만 엔 소득의 벽(年収の壁)"이란?
* 일본에서 대학생이나 주부 아르바이트생은 **연간 총 급여 소득(1월~12월 합계, 비과세 교통비 제외)이 103만 엔을 초과하면 본인에게 소득세가 부과되고 부모/배우자의 세금 부양공제(38만~63만 엔) 대상에서 탈락**합니다. 또한 **130만 엔을 넘으면 사회보험료(연금·건강보험)가 강제 부과**되어 오히려 실수령액이 줄어듭니다.
* 따라서 일본 알바생들은 연말(10~12월)이 되면 *"점장님, 저 올해 103만 엔 넘을 것 같아서 다음 달 시프트 못 들어가요"*라며 대량 결원을 일으킵니다.
* 이를 시스템에서 미리 추적하여 **연간 누적 기본급+수당 합계가 103만 엔의 80%(82.4만 엔)를 넘으면 점주에게 미리 경고해 주는 기능**은 일본 현지 면접관이 무릎을 치는 킬러 기능입니다.

### 8.2 상세 API 및 화면 명세 (`GET /analytics` $\rightarrow$ `templates/analytics/dashboard.html`)
1. **시프트 편성 기준 당월 예상 인건비 시뮬레이션 (`GET /api/analytics/projected-cost?storeId=1&yearMonth=2026-10`)**:
   * 아직 월말이 오지 않았더라도, 현재 `shift_schedule`에 `CONFIRMED`된 시프트 시간과 직원별 시급을 곱하여 **"이번 달 예상 총 인건비"**를 실시간 시뮬레이션.
2. **알바생별 '103만 엔 / 130만 엔의 벽' 모니터링 (`GET /api/analytics/tax-barrier?storeId=1&year=2026`)**:
   * 해당 연도(`target_year_month LIKE '2026-%'`)의 직원별 누적 과세 급여(`SUM(base_pay + night_allowance + overtime_allowance)` — *비과세인 `transport_allowance`는 세법상 제외하는 디테일!*)를 집계.
   * `1,030,000엔`에서 누적 과세 급여를 빼서 **"올해 남은 근무 가능 금액(잔여 한도)"**과 **"현재 시급 기준 올해 앞으로 더 일할 수 있는 최대 시간"**을 계산하여 프로그레스 바로 시각화.

---

## 제9장. 팀원별 AI 코딩 프롬프트 실전 가이드 (복사해서 바로 사용)

팀원들은 각자 맡은 파트를 개발할 때, **본 문서(`kintai_requirements_specification.md`) 파일을 AI(ChatGPT, Claude, Cursor)에 첨부**한 뒤 아래 프롬프트를 그대로 복사해서 입력하면 단번에 완벽하게 호환되는 코드를 얻을 수 있습니다.

### 💬 [팀원 A용 프롬프트 1] 점포 및 알바생 계약 관리 구현
```text
첨부한 kintai_requirements_specification.md 문서를 기준으로 '제3장. 회원 인증 및 점포·계약 마스터'의 점포 관리 기능을 구현해 줘.
현재 프로젝트에는 Member 샘플과 공통 layout.html, ApiResponse가 이미 만들어져 있어.
★ 중요: 일본 IT 현지 취업 포트폴리오이므로 모든 HTML 화면(UI)의 버튼명, 라벨, 테이블 헤더, 모달 안내 문구는 명세서 1.5절의 [일본어 표준 용어집]을 준수하여 100% 자연스러운 비즈니스 일본어(日本語)로 작성해 줘.
아래 파일들을 Spring Boot 4.0.8 + MyBatis 4.0.1 규격에 맞춰 전체 코드로 작성해 줘:
1. com.example.kintai.domain.store.dto (StoreDto, StoreStaffDetailDto, StoreCreateRequestDto, StaffJoinRequestDto, StaffContractUpdateDto)
2. com.example.kintai.domain.store.mapper.StoreMapper.java & src/main/resources/mapper/store/StoreMapper.xml
3. com.example.kintai.domain.store.service.StoreService.java
4. com.example.kintai.domain.store.controller.StoreController.java (GET /stores 화면 라우팅 및 /api/stores/** REST API 포함)
5. src/main/resources/templates/store/manage.html (common/layout.html 상속, Bootstrap 5 모달 및 Fetch API 연동 완료된 일본어 화면)
```

### 💬 [팀원 A용 프롬프트 2] FullCalendar 통합 시프트 스케줄러 구현
```text
첨부한 kintai_requirements_specification.md 문서를 기준으로 '제4장. 통합 시프트 스케줄러 (shift_schedule & FullCalendar)'를 구현해 줘.
야간 익일 퇴근 시 날짜 +1일 처리 로직과 일본 노동기준법 제34조(6시간 초과 45분, 8시간 초과 60분 휴게) 자동 계산 로직을 반드시 포함해 줘.
★ 중요: 일본 IT 현지 취업 포트폴리오이므로 FullCalendar의 월/요일 및 모든 버튼, 상태 배지(希望シフト, 確定シフト, 未割当 등), 모달 텍스트는 100% 자연스러운 일본어(日本語)로 작성해 줘.
아래 파일들을 전체 코드로 작성해 줘:
1. com.example.kintai.domain.shift.dto (ShiftDto, CalendarEventDto, ShiftRequestDto, ShiftConfirmDto)
2. com.example.kintai.domain.shift.mapper.ShiftMapper.java & src/main/resources/mapper/shift/ShiftMapper.xml
3. com.example.kintai.domain.shift.service.ShiftService.java
4. com.example.kintai.domain.shift.controller.ShiftController.java (GET /shifts 및 /api/shifts/** 포함)
5. src/main/resources/templates/shift/calendar.html (FullCalendar 6.1 렌더링, 날짜 클릭 시 희망 제출 모달, 일정 클릭 시 희망 vs 확정 대조 조율 모달 포함 일본어 화면)
```

### 💬 [팀원 B용 프롬프트 1] 실시간 출퇴근 타각 및 근태 수정 승인제 구현
```text
첨부한 kintai_requirements_specification.md 문서를 기준으로 '제5장. 감사추적 출퇴근 타각 및 수정 승인 워크플로우'를 구현해 줘.
명세서 5.3절에 있는 AttendanceTimeCalculator 클래스(야간 22:00~05:00 교집합 산출 및 휴게 차감 공식)를 그대로 포함해서 일별 실근무/야간/연장(분)이 오차 없이 계산되게 해 줘.
★ 중요: 일본 IT 현지 취업 포트폴리오이므로 타각 메인 버튼(出勤, 退勤, 休憩開始, 休憩戻り) 및 상태 배지(勤務中, 休憩中), 수정 신청/승인 화면 텍스트는 100% 자연스러운 일본어(日本語)로 작성해 줘.
아래 파일들을 전체 코드로 작성해 줘:
1. com.example.kintai.domain.attendance.util.AttendanceTimeCalculator.java
2. com.example.kintai.domain.attendance.dto (AttendanceDto, PunchRequestDto, AttendanceModRequestDto, ModReviewDto)
3. com.example.kintai.domain.attendance.mapper.AttendanceMapper.java & src/main/resources/mapper/attendance/AttendanceMapper.xml
4. com.example.kintai.domain.attendance.service.AttendanceService.java
5. com.example.kintai.domain.attendance.controller.AttendanceController.java (GET /attendance, GET /attendance/mod-requests 및 REST API 포함)
6. src/main/resources/templates/attendance/punch.html 및 mod-list.html (일본어 UI)
```

### 💬 [팀원 B용 프롬프트 2] 일본 노동법 150% 급여 정산 엔진 및 POI 엑셀 출력 구현
```text
첨부한 kintai_requirements_specification.md 문서를 기준으로 '제6장. 일본 노동기준법 급여 정산 엔진 및 Apache POI 엑셀 대장'을 구현해 줘.
명세서 6.2절의 PayrollCalculator(pay_cutoff_day 마감일 기간 산출 공식 및 오차 0건 BigDecimal 곱셈 우선 단 1회 HALF_UP 공식)를 반드시 그대로 사용해 줘.
★ 중요: 일본 IT 현지 취업 포트폴리오이므로 급여 정산 화면의 모든 수당 항목(基本給, 深夜手当, 残業手当, 通勤手当, 総支給額) 및 Apache POI 엑셀 시트명(給与台帳), 엑셀 헤더는 100% 일본 노동법 표준 일본어(日本語) 서식으로 작성해 줘.
아래 파일들을 전체 코드로 작성해 줘:
1. com.example.kintai.domain.payroll.util.PayrollCalculator.java
2. com.example.kintai.domain.payroll.dto (PayrollDto, PayrollCalculateRequestDto)
3. com.example.kintai.domain.payroll.mapper.PayrollMapper.java & src/main/resources/mapper/payroll/PayrollMapper.xml
4. com.example.kintai.domain.payroll.service.PayrollService.java (Apache POI .xlsx 엑셀 워크북 생성 로직 포함)
5. com.example.kintai.domain.payroll.controller.PayrollController.java (GET /payroll 및 /api/payroll/** 포함)
6. src/main/resources/templates/payroll/list.html (일본어 UI)
```

### 💬 [3~4인 확장: 팀원 C/D용 프롬프트] LINE 메신저 봇 & 경영 분석(103만엔의 벽) 대시보드 구현
```text
첨부한 kintai_requirements_specification.md 문서를 기준으로 '제7장. LINE 메신저 봇 & 실시간 알림 센터'와 '제8장. 경영 분석(BI) 대시보드 & 일본 세법 103만 엔의 벽 시뮬레이터'를 구현해 줘.
LINE 채널 토큰이 설정되지 않은 로컬 환경에서도 발표 시연이 가능하도록 웹 화면 우측 하단에 연두색 LINE 말풍선 미리보기 토스트가 뜨는 하이브리드 기능과, 비과세 교통비를 제외한 연간 누적 과세 급여 기반 103만 엔/130만 엔 도달 위험도 프로그레스 바를 포함해 줘.
★ 중요: 모든 화면 텍스트와 알림 문구는 명세서 1.5절 용어집을 준수하여 100% 자연스러운 일본어(日本語)로 작성해 줘.
아래 파일들을 전체 코드로 작성해 줘:
1. com.example.kintai.domain.notification.service.LineNotificationService.java
2. com.example.kintai.domain.analytics.dto (TaxBarrierStatusDto, ProjectedCostDto)
3. com.example.kintai.domain.analytics.mapper.AnalyticsMapper.java & src/main/resources/mapper/analytics/AnalyticsMapper.xml
4. com.example.kintai.domain.analytics.service.AnalyticsService.java
5. com.example.kintai.domain.analytics.controller.AnalyticsController.java (GET /analytics 및 /api/analytics/** 포함)
6. src/main/resources/templates/analytics/dashboard.html (Chart.js 통계 차트 및 103만 엔 소득의 벽 프로그레스 바 일본어 UI)
```
