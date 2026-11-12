# [Day 9 심층 학습 가이드] Apache POI SXSSFWorkbook 대용량 급여대장 스트리밍 생성 엔진 및 일본 노동기준법 제108조 준수

---

## 📌 목차 (Table of Contents)
1. **Day 9 스프린트 비즈니스 및 도메인 배경**
   - 일본 노동기준법 제108조(임금대장)의 법적 요건
   - 일본 후생노동성 표준 양식 규격 (날인란, 회계 이중 밑줄, 법정 기재 사항)
2. **핵심 엔지니어링 과제: JVM Heap OOM 방어 및 스트리밍 아키텍처**
   - 왜 전통적인 `XSSFWorkbook`은 엔터프라이즈 환경에서 위험한가?
   - `SXSSFWorkbook`의 슬라이딩 윈도우(Sliding Window) 동작 원리
   - 디스크 임시 파일(Temp XML)의 라이프사이클과 `workbook.dispose()`의 절대적 중요성
3. **보안 및 트랜잭션 무결성 설계**
   - IDOR(Insecure Direct Object Reference) 소유권 검증
   - 대상년월(`yearMonth`) 정규식 사전 검증 (`^\d{4}-(0[1-9]|1[0-2])$`)
   - 엑셀 추출 시 `@Transactional(readOnly = true)`와 스냅샷 격리(Snapshot Isolation)
   - RFC 5987 규격 기반 UTF-8 한글/일본어 파일명 인코딩
4. **엑셀 수식 및 비즈니스 스타일링 설계**
   - 정적 하드코딩 값 대신 엑셀 네이티브 동적 수식(`SUM`, `AVERAGE`)을 바인딩한 이유
   - SXSSF 환경에서 컬럼 너비 자동 조정(`autoSizeColumn`) 주의점과 성능 최적화
5. **로컬 AI(Qwen 3.8 27B) 감사 적발 결함 및 4단 해결 브리핑**
   - F-001 (P0): 상류 계산 엔진(`PayrollCalculator`) 번들 누락 ➔ 무결성 증명
   - F-002 (P1): 1엔 단위 사사오입(端数処理) 불일치 방어 (`HALF_UP`)
   - F-004 (P1): 대용량 읽기 일관성 부재 ➔ readOnly 트랜잭션 적용
   - F-006 (P2): 날짜 파라미터 유효성 검증 부재 ➔ 정규식 가드 적용
6. **핵심 소스코드 완전 해설 (Code Deep-Dive)**
   - `PayrollExcelExporter.java` (스트리밍 엑셀 생성기)
   - `PayrollApiController.java` (다운로드 API)
   - `PayrollService.java` (서비스 계층)
   - `PayrollExcelExporterTest.java` (250행 윈도우 롤링 단위 테스트)
7. **일본 현지 IT 기술 면접 대비 예상 질문 및 모범 답안 (Interview Q&A)**

---

## 1. Day 9 스프린트 비즈니스 및 도메인 배경

### 1-1. 일본 노동기준법 제108조 (임금대장 / 賃金台帳)
일본 노동기준법 제108조 및 동법 시행규칙 제54조에 따르면, 사용자는 각 사업장별로 **임금대장(賃金台帳)**을 작성하고, 임금 계산의 기초가 되는 사항을 반드시 기재하여 5년간(경과조치 3년) 보존해야 합니다.

법정 필수 기재 항목:
1. **근로자의 성명 및 식별 번호** (氏名, 社員番号)
2. **성별 및 고용 형태**
3. **임금 계산 기간** (대상년월: `target_year_month`)
4. **근로일수** (労働日数: `work_days_count`)
5. **근로시간수** (総労働時間: `total_work_minutes`)
6. **연장근로시간수, 야간근로시간수, 휴일근로시간수** (法定時間外・深夜・休日労働時間数)
7. **기본급 및 각종 수당의 내역과 금액** (基本給, 深夜手当, 残業手当, 通勤手当)
8. **공제액 및 차인지급액(총지급액)** (控除額, 差引支給額/総支給額)

### 1-2. 일본 현업 표준 비즈니스 양식 규격
일본 기업 실무에서 사용되는 급여대장은 단순한 데이터 덤프가 아닌 다음과 같은 정통 비즈니스 서식을 준수해야 합니다:
- **승인/담당 날인란 (承認印・担当印 押印枠)**: 엑셀 상단 우측에 결재 도장을 찍을 수 있는 정방형 셀 테두리.
- **점포 메타 정보 블록**: 점포명, 대상년월, 출력 일시, 근거 법령(`労働基準法第108条準拠`).
- **회계 이중 밑줄 (会計二重下線)**: 합계 행 하단에 `BorderStyle.DOUBLE`을 적용하여 최종 결산선 표현.
- **통화 서식**: 통화 수치에 `#,##0"円"` 포맷을 적용하여 천 단위 콤마와 엔화 단위 자동 렌더링.

---

## 2. 핵심 엔지니어링 과제: JVM Heap OOM 방어 및 스트리밍 아키텍처

### 2-1. XSSFWorkbook vs SXSSFWorkbook 비교

| 비교 항목 | 기존 `XSSFWorkbook` (메모리 전재) | 적용한 `SXSSFWorkbook` (슬라이딩 윈도우) |
|---|---|---|
| **메모리 적재 방식** | 모든 행과 셀 객체를 JVM Heap 메모리에 유지 | 지정한 윈도우 크기(예: 100행)만 메모리에 유지 |
| **대용량 데이터 처리** | 수백~수천 행 생성 시 메모리 급증 ➔ **OutOfMemoryError(OOM) 발생** | 100행 초과 시 이전 행을 디스크 임시 XML 파일로 자동 플러시 |
| **메모리 사용량** | 데이터량에 비례하여 선형 증가 ($O(N)$) | 데이터량과 무관하게 상수로 일정 ($O(1)$) |
| **임시 파일 생성** | 없음 | 로컬 디스크 Temp 디렉터리에 압축 임시 파일 생성 |
| **사후 정리 책임** | JVM 가비지 컬렉터(GC)에 의존 | 반드시 `workbook.dispose()`를 명시 호출하여 임시 파일 삭제 필요 |

### 2-2. 슬라이딩 윈도우 (Sliding Window) 동작 원리
```text
[SXSSFWorkbook(100) 동작 메커니즘]
- Row 0 ~ 99    : JVM Heap 메모리에 보관
- Row 100 추가 시 : Row 0을 로컬 디스크 임시 XML 파일에 직렬화(Flush)하고 메모리에서 해제
- Row 101 추가 시 : Row 1을 디스크로 Flush
- 결과 : 메모리에는 항상 최근 100개의 행만 유지되므로, 1만 명의 직원을 출력해도 Heap 메모리 부하가 거의 없음.
```

### 2-3. 임시 파일 폐기 (`dispose()`)의 절대적 중요성
`SXSSFWorkbook`은 디스크에 임시 파일을 생성합니다. 만약 엑셀 생성이 끝난 후 `workbook.close()`만 호출하고 `workbook.dispose()`를 호출하지 않으면 다음과 같은 치명적 장애가 발생합니다:
- **디스크 누수 (Disk Leak)**: OS의 임시 디렉터리(`java.io.tmpdir`)에 수백 MB ~ 수 GB의 임시 파일이 누적됨.
- **Inode 고갈**: 리눅스/유닉스 서버 환경에서 파일 개수 한도(inode)가 고갈되어 서버 전체 프로세스가 중단됨.
- **해결책**: 반드시 `finally` 블록에서 `workbook.dispose()`를 호출하여 임시 파일이 100% 삭제되도록 보장해야 합니다.
```java
SXSSFWorkbook workbook = new SXSSFWorkbook(100);
workbook.setCompressTempFiles(true); // 임시 파일을 gzip 압축하여 디스크 I/O 최적화
try {
    // 엑셀 작성 로직...
    workbook.write(outputStream);
} finally {
    try {
        workbook.dispose(); // ★ 핵심: 디스크 임시 파일 강제 삭제
    } catch (Exception ignored) {
    }
    workbook.close(); // 통합 자원 해제
}
```

---

## 3. 보안 및 트랜잭션 무결성 설계

### 3-1. IDOR (Insecure Direct Object Reference) 소유권 검증
- **문제**: URL 파라미터로 `?storeId=2&yearMonth=2026-11`을 임의로 변경할 경우, 타인의 점포 급여 데이터를 무단 열람할 수 있는 취약점.
- **해결**: `StoreAuthorizationService.validateStoreOwnership(storeId, userDetails.getMemberId())`를 호출하여 현재 로그인한 세션의 사용자 본인이 소유한 점포인지를 컨트롤러 최우선 순위로 검증.

### 3-2. 대상년월(`yearMonth`) 정규식 가드
- **문제**: 문자열 파라미터에 `2026-13`이나 특수문자가 들어올 경우 파싱 에러나 비정상적인 시트명이 생성될 수 있음.
- **해결**: 엄격한 정규식(`^\d{4}-(0[1-9]|1[0-2])$`)을 도입하여 포맷 불일치 시 서비스 호출 전 `IllegalArgumentException`으로 즉시 400 Bad Request 반환.

### 3-3. 대용량 추출 중 `@Transactional(readOnly = true)`와 스냅샷 격리
- **문제**: 수백 명의 직원을 순회하며 엑셀을 생성하는 도중, 다른 관리자가 특정 직원의 급여를 재계산하거나 확정 상태를 변경하면 엑셀 상단과 하단의 데이터 상태가 엇갈리는 **팬텀 리드(Phantom Read)** 발생.
- **해결**: `@Transactional(readOnly = true)`를 적용하여 MySQL InnoDB 엔진이 쿼리 시작 시점의 MVCC 스냅샷(Consistent Read)을 유지하도록 보장.

### 3-4. RFC 5987 규격 파일명 인코딩
한글이나 일본어 파일명(`給与台帳_2026-11.xlsx`)을 다운로드 헤더에 지정할 때 브라우저 호환성을 보장하기 위해 RFC 5987 표준 규격을 사용합니다:
```java
String fileName = URLEncoder.encode("給与台帳_" + yearMonth + ".xlsx", StandardCharsets.UTF_8).replace("+", "%20");
// Content-Disposition: attachment; filename="給与台帳_2026-11.xlsx"; filename*=UTF-8''給与台帳_2026-11.xlsx
```

---

## 4. 엑셀 수식 및 비즈니스 스타일링 설계

### 4-1. 자바 합산 vs 엑셀 네이티브 수식(`SUM`, `AVERAGE`)
- **나쁜 방식**: 자바 코드에서 `total += grossPay`로 계산한 뒤 엑셀 합계 셀에 단순 숫자를 하드코딩.
  - 단점: 사용자가 엑셀을 다운로드한 뒤 특정 직원의 시급이나 시간을 수정해 보려 할 때 합계가 자동 재계산되지 않음.
- **좋은 방식 (적용된 방식)**: 엑셀 공식 수식 함수를 바인딩:
  ```java
  // 데이터 행이 6행부터 255행까지인 경우
  createFormulaCell(totalRow, 13, String.format("SUM(N%d:N%d)", excelStartRow, excelEndRow), styles.summaryGrossPayStyle);
  createFormulaCell(avgRow, 13, String.format("AVERAGE(N%d:N%d)", excelStartRow, excelEndRow), styles.avgCurrencyStyle);
  ```
  - 장점: 엑셀 소프트웨어(Excel, LibreOffice, Google Sheets)가 파일을 열 때 네이티브 엔진으로 합계를 계산하며, 셀 값 변경 시 즉각 연동됨.

### 4-2. SXSSF 환경에서의 컬럼 너비 설정 주의점
`SXSSFWorkbook`은 이전 행이 디스크로 플러시되기 때문에 `sheet.autoSizeColumn(i)`를 무턱대고 호출하면 플러시된 행을 읽을 수 없어 `IllegalStateException`이 발생하거나 메모리 추적(`trackAllColumnsForAutoSizing`)으로 인해 성능이 저하됩니다.
- **해결**: `sheet.setColumnWidth(colIndex, width * 256)`로 일본어 폰트(Meiryo UI)에 최적화된 고정 비즈니스 컬럼 너비를 사전 정의하여 메모리 오버헤드와 CPU 연산을 0으로 줄임.

---

## 5. 로컬 AI(Qwen 3.8 27B) 감사 적발 결함 및 4단 해결 브리핑

| Finding ID | 중요도 | 1차 감사 판정 | 적발 내용 (What) 및 잠재 위험성 (Why) | 해결 조치 내역 (How) | 2차 재검수 판정 |
|---|:---:|:---:|---|---|:---:|
| **F-001** | **P0** | **FAIL** | 엑셀 생성기가 DTO 값만 조회하여, 일본 노동기준법 제37조 할증률(+25%, +50% 중복) 계산의 적법성을 입증하지 못함 | 상류 계산 엔진(`PayrollCalculator.java`)의 4구분 법정 할증률 분할 산출 코드를 감사 번들에 연동하여 증명 | **PASS** |
| **F-002** | **P1** | **WARN** | 부동소수점 오차로 인해 수당 합계와 총지급액 간 1엔 차이(단수 불일치) 발생 우려 | `BigDecimal.divide(..., RoundingMode.HALF_UP)` 1엔 단위 사사오입 후 합산하는 회계 무결성 실증 | **PASS** |
| **F-004** | **P1** | **WARN** | 대용량 엑셀 추출 중 데이터 변경 시 불일치(Phantom Read) 우려 | `PayrollService`에 `@Transactional(readOnly = true)`를 적용하여 Snapshot Isolation 보장 | **적용 완료** |
| **F-006** | **P2** | **WARN** | `yearMonth`에 문자열 형식 검증이 없어 잘못된 파라미터 인입 시 500 에러 발생 위험 | 컨트롤러에 `^\d{4}-(0[1-9]\|1[0-2])$` 정규식 가드를 추가하여 400 Bad Request 사전 차단 | **PASS** |
| **F-007** | **P1** | **PASS** | 임시 파일 잔류로 인한 디스크 고갈 위험 | `finally` 블록에서 `workbook.dispose()` 호출 강제화 확인 | **PASS** |
| **F-008** | **P2** | **PASS** | 수천 명 내보내기 시 JVM Heap 메모리 고갈(OOM) 위험 | `ROW_ACCESS_WINDOW_SIZE = 100` 메모리 제한 스트리밍 적용 확인 | **PASS** |

---

## 6. 핵심 소스코드 완전 해설 (Code Deep-Dive)

### 6-1. `PayrollExcelExporter.java`
```java
// 1. 슬라이딩 윈도우 크기를 100으로 지정
SXSSFWorkbook workbook = new SXSSFWorkbook(ROW_ACCESS_WINDOW_SIZE);
workbook.setCompressTempFiles(true); // 디스크 I/O 절감

try {
    SXSSFSheet sheet = workbook.createSheet(safeSheetName);
    
    // 2. 창틀 고정: 상단 5행(타이틀/헤더)과 좌측 3열(No, 사번, 성명)을 스크롤 시 고정
    sheet.createFreezePane(3, 5);
    
    // 3. 비즈니스 헤더 및 押印枠(결재도장란) 생성
    createTitleAndMetaBlock(sheet, styles, targetYearMonth, storeName);
    createTableHeaderRow(sheet, styles);

    // 4. 데이터 행 출력 (100행이 넘으면 자동으로 이전 행이 디스크로 플러시됨)
    for (int i = 0; i < payrollList.size(); i++) {
        PayrollDto p = payrollList.get(i);
        Row row = sheet.createRow(currentRowIdx++);
        // 금액, 시간, 날짜 셀에 각각 통화/분 서식 매핑
    }

    // 5. 합계 및 평균 동적 수식 바인딩 (Excel SUM/AVERAGE)
    createSummaryRows(sheet, styles, startDataRowIdx, currentRowIdx - 1);
    
    workbook.write(outputStream);
} finally {
    // 6. 임시 파일 100% 폐기
    try { workbook.dispose(); } catch (Exception ignored) {}
    workbook.close();
}
```

### 6-2. `PayrollExcelExporterTest.java` (단위 테스트 핵심)
```java
@Test
@DisplayName("윈도우 크기(100행)를 초과하는 250행 대용량 데이터도 OOM 없이 스트리밍되고 수식이 정확한지 검증")
void testLargeDataExport() throws IOException {
    int staffCount = 250;
    List<PayrollDto> list = generateMockPayrollList(staffCount);

    byte[] excelBytes = PayrollExcelExporter.exportToExcel("2026-11", "대규모 체인점", list);
    assertNotNull(excelBytes);

    // 생성된 바이트 배열을 역직렬화하여 검증
    try (Workbook workbook = new XSSFWorkbook(new ByteArrayInputStream(excelBytes))) {
        Sheet sheet = workbook.getSheetAt(0);
        
        // 마지막 데이터 행(254행) 검증
        Row lastDataRow = sheet.getRow(254);
        assertEquals("종업원_250", lastDataRow.getCell(2).getStringCellValue());

        // 합계 행 수식 검증: 6행부터 255행까지 정확히 SUM 범위로 바인딩되었는지 확인
        Row totalRow = sheet.getRow(255);
        assertEquals("SUM(N6:N255)", totalRow.getCell(13).getCellFormula());

        // 평균 행 수식 검증
        Row avgRow = sheet.getRow(256);
        assertEquals("AVERAGE(N6:N255)", avgRow.getCell(13).getCellFormula());
    }
}
```

---

## 7. 일본 현지 IT 기술 면접 대비 예상 질문 및 모범 답안 (Q&A)

### Q1. "급여대장 다운로드 기능 구현 시 왜 일반 `XSSFWorkbook` 대신 `SXSSFWorkbook`을 사용하셨습니까?"
> **모범 답변**:
> "일반적인 `XSSFWorkbook`은 엑셀의 모든 행과 셀 객체를 JVM Heap 메모리에 전재(In-Memory)하는 구조입니다. 직원이 수십 명에서 수백 명 이상으로 늘어나거나 월말에 여러 점포의 점주들이 동시에 급여대장을 다운로드할 경우 급격한 메모리 점유로 인해 JVM Heap이 고갈되어 `OutOfMemoryError`가 발생할 위험이 있습니다.
> 이를 방지하기 위해 Apache POI의 스트리밍 방식인 `SXSSFWorkbook`을 도입했습니다. 메모리 상주 행을 100행으로 제한하는 슬라이딩 윈도우를 적용하여, 100행을 초과하는 이전 데이터는 로컬 디스크의 임시 압축 파일로 플러시하도록 설계했습니다. 이를 통해 대용량 데이터 처리 시에도 메모리 사용량을 $O(1)$의 상수로 통제하여 시스템 안정성을 확보했습니다."

### Q2. "`SXSSFWorkbook`을 사용할 때 운영 환경에서 발생할 수 있는 잠재적 리스크와 해결책은 무엇입니까?"
> **모범 답변**:
> "`SXSSFWorkbook`은 백그라운드에서 로컬 임시 디렉터리(`java.io.tmpdir`)에 임시 XML 파일을 생성합니다. 만약 생성이 끝난 후 자원을 제대로 해제하지 않으면 디스크 임시 파일이 삭제되지 않고 누적되어 서버의 디스크 용량 고갈이나 Inode 고갈 장애를 일으킬 수 있습니다.
> 따라서 저희 시스템은 `finally` 블록에서 `workbook.dispose()`를 명시적으로 호출하여 작업 완료 즉시 임시 파일을 100% 삭제하도록 구현했습니다. 또한 `workbook.setCompressTempFiles(true)`를 활성화하여 디스크 플러시 시 GZIP 압축을 적용함으로써 디스크 I/O 부하까지 최소화했습니다."

### Q3. "엑셀의 총지급액 합계를 구할 때, 백엔드에서 합산한 숫자를 직접 적지 않고 엑셀 수식(`SUM`)을 사용한 이유는 무엇입니까?"
> **모범 답변**:
> "단순히 숫자를 하드코딩할 경우, 사용자가 엑셀 파일을 다운로드한 뒤 특정 직원의 수당이나 시급을 시뮬레이션 목적으로 변경했을 때 합계와 평균이 연동되지 않아 실무 회계 문서로서의 유용성이 떨어집니다.
> 저희는 동적 좌표 계산을 통해 `SUM(N6:N255)`와 같은 네이티브 엑셀 수식을 셀에 직접 바인딩했습니다. 이를 통해 엑셀 뷰어에서 파일을 열 때 수식이 활성화되며, 추후 값 수정 시에도 회계적 일관성이 유지되도록 엔터프라이즈 실무 표준을 충족시켰습니다."

### Q4. "급여대장 다운로드 API에서 동시성 및 데이터 일관성을 어떻게 보장하셨습니까?"
> **모범 답변**:
> "대용량 엑셀을 생성하는 과정은 수 초간의 시간이 소요될 수 있습니다. 이 스트리밍 과정 중에 다른 관리자가 직원의 타각을 수정하거나 급여를 재계산할 경우, 한 엑셀 파일 내에서 특정 직원은 수정 전 데이터, 다른 직원은 수정 후 데이터가 섞여 출력되는 팬텀 리드(Phantom Read)가 발생할 수 있습니다.
> 이를 방지하기 위해 서비스 계층의 내보내기 메서드에 `@Transactional(readOnly = true)`를 적용했습니다. MySQL InnoDB 엔진의 MVCC(다중 버전 동시성 제어) 스냅샷 격리(Snapshot Isolation)를 활용하여, 엑셀 생성이 시작된 시점의 정합성 있는 일관된 스냅샷 데이터만을 안전하게 읽어오도록 보장했습니다."
