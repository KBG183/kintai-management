# Day 9 E2E Audit Bundle: Monthly Payroll Ledger Excel Streaming Export & Statutory Compliance

## 1. Domain Background & Regulatory Compliance
- **Domain**: 月次給与台帳 (Monthly Payroll Ledger) Excel エクスポート
- **Governing Law**: 労働基準法第108条（賃金台帳）、第37条（割増賃金: 時間外25%, 深夜25%, 深夜残業50%重畳）および昭和62年基発第150号（端数処理通達）
- **Architecture**:
  - Apache POI `SXSSFWorkbook(rowAccessWindowSize = 100)` によるディスクスライディングウィンドウ・ストリーミング生成
  - `setCompressTempFiles(true)` によるディスクI/Oおよび容量の最適化
  - `finally { workbook.dispose(); workbook.close(); }` による一時ファイル（temp XML files）の完全破棄保証
  - 承認印・担当印押印枠、二重下線（会計標準）および Excel 数式（SUM / AVERAGE）の正確な動的バインド
  - 年月パラメータの正規表現厳格バリデーション (`^\d{4}-(0[1-9]|1[0-2])$`)

---

## 2. Source Code Components

### 2.1 Upstream Statutory Calculation & Rounding Engine: `PayrollCalculator.java`
*(F-001 & F-002 Verification Evidence)*
```java
public final class PayrollCalculator {
    /**
     * 労働基準法第37条に基づく4区分支給額詳細結果
     * regularPay: 所定内100%
     * regularNightPay: 所定内深夜125% (基本100% + 深夜割増25%)
     * overtimePay: 法定外残業125% (基本100% + 時間外割増25%)
     * nightOvertimePay: 法定外深夜残業150% (基本100% + 時間外25% + 深夜25% 重畳割増)
     */
    public record DetailedDailyPayResult(
            int regularPay, int regularNightPay, int overtimePay, int nightOvertimePay,
            int basePay, int nightAllowance, int overtimeAllowance, int totalPay
    ) {}

    public static DetailedDailyPayResult calculateDetailedDailyPay(
            int regularMinutes, int regularNightMinutes, int overtimeMinutes, int nightOvertimeMinutes,
            int hourlyWage
    ) {
        BigDecimal wage = BigDecimal.valueOf(hourlyWage);
        BigDecimal min60 = BigDecimal.valueOf(60);

        // 1. 各区分の支給額（1円未満四捨五入）
        BigDecimal regPay = wage.multiply(BigDecimal.valueOf(regularMinutes)).divide(min60, 0, RoundingMode.HALF_UP);
        BigDecimal regNightPay = wage.multiply(BigDecimal.valueOf(1.25)).multiply(BigDecimal.valueOf(regularNightMinutes)).divide(min60, 0, RoundingMode.HALF_UP);
        BigDecimal otPay = wage.multiply(BigDecimal.valueOf(1.25)).multiply(BigDecimal.valueOf(overtimeMinutes)).divide(min60, 0, RoundingMode.HALF_UP);
        BigDecimal nightOtPay = wage.multiply(BigDecimal.valueOf(1.50)).multiply(BigDecimal.valueOf(nightOvertimeMinutes)).divide(min60, 0, RoundingMode.HALF_UP);

        int totalPay = regPay.add(regNightPay).add(otPay).add(nightOtPay).intValue();

        // 2. 基本給（実労働時間全体の100%分）
        int totalMinutes = regularMinutes + regularNightMinutes + overtimeMinutes + nightOvertimeMinutes;
        BigDecimal basePayBd = wage.multiply(BigDecimal.valueOf(totalMinutes)).divide(min60, 0, RoundingMode.HALF_UP);

        // 3. 割増手当（深夜25%加算、残業25%加算）
        int totalNightMinutes = regularNightMinutes + nightOvertimeMinutes;
        int totalOtMinutes = overtimeMinutes + nightOvertimeMinutes;
        BigDecimal nightAllowBd = wage.multiply(BigDecimal.valueOf(0.25)).multiply(BigDecimal.valueOf(totalNightMinutes)).divide(min60, 0, RoundingMode.HALF_UP);
        BigDecimal otAllowBd = wage.multiply(BigDecimal.valueOf(0.25)).multiply(BigDecimal.valueOf(totalOtMinutes)).divide(min60, 0, RoundingMode.HALF_UP);

        return new DetailedDailyPayResult(
                regPay.intValue(), regNightPay.intValue(), otPay.intValue(), nightOtPay.intValue(),
                basePayBd.intValue(), nightAllowBd.intValue(), otAllowBd.intValue(), totalPay
        );
    }
}
```

### 2.2 Controller Layer: `PayrollApiController.java`
*(F-003 IDOR Defense & F-006 Date Validation Evidence)*
```java
    @GetMapping("/excel")
    public ResponseEntity<byte[]> downloadExcel(
            @RequestParam("storeId") Long storeId,
            @RequestParam("yearMonth") String yearMonth,
            @AuthenticationPrincipal CustomUserDetails userDetails
    ) throws IOException {
        // 対象年月フォーマットの厳格な検証 (F-006 対策: YYYY-MM 形式)
        if (yearMonth == null || !yearMonth.matches("^\\d{4}-(0[1-9]|1[0-2])$")) {
            throw new IllegalArgumentException("無効な対象年月フォーマットです (形式: YYYY-MM): " + yearMonth);
        }

        storeAuthorizationService.validateStoreOwnership(storeId, userDetails.getMemberId());
        byte[] excelBytes = payrollService.exportPayrollToExcel(storeId, yearMonth);

        String fileName = URLEncoder.encode("給与台帳_" + yearMonth + ".xlsx", StandardCharsets.UTF_8).replace("+", "%20");

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + fileName + "\"; filename*=UTF-8''" + fileName)
                .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .body(excelBytes);
    }
```

### 2.3 Service Layer: `PayrollService.java`
```java
    /**
     * 給与台帳 Excel エクスポート（労働基準法第108条準拠・SXSSFストリーミング）
     */
    public byte[] exportPayrollToExcel(Long storeId, String targetYearMonth) throws IOException {
        List<PayrollDto> payrolls = getPayrollsByStoreAndMonth(storeId, targetYearMonth);
        String storeName = payrollMapper.findStoreNameById(storeId);
        return PayrollExcelExporter.exportToExcel(targetYearMonth, storeName, payrolls);
    }
```

### 2.4 Exporter Utility: `PayrollExcelExporter.java`
*(F-005 Null-Safety, F-007 Cleanup, F-008 OOM Streaming, F-009 Dynamic Formulas)*
```java
public final class PayrollExcelExporter {
    private static final int ROW_ACCESS_WINDOW_SIZE = 100;

    public static byte[] exportToExcel(String targetYearMonth, String storeName, List<PayrollDto> payrollList) throws IOException {
        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            exportToStream(targetYearMonth, storeName, payrollList, out);
            return out.toByteArray();
        }
    }

    public static void exportToStream(
            String targetYearMonth,
            String storeName,
            List<PayrollDto> payrollList,
            OutputStream outputStream
    ) throws IOException {
        SXSSFWorkbook workbook = new SXSSFWorkbook(ROW_ACCESS_WINDOW_SIZE);
        workbook.setCompressTempFiles(true);

        try {
            String safeSheetName = "給与台帳_" + (targetYearMonth != null ? targetYearMonth.replace("-", "") : "一覧");
            SXSSFSheet sheet = workbook.createSheet(safeSheetName);

            sheet.createFreezePane(3, 5);
            configureColumnWidths(sheet);
            ExcelStyles styles = new ExcelStyles(workbook);

            createTitleAndMetaBlock(sheet, styles, targetYearMonth, storeName);
            createTableHeaderRow(sheet, styles);

            int startDataRowIdx = 5;
            int currentRowIdx = startDataRowIdx;

            if (payrollList == null || payrollList.isEmpty()) {
                createEmptyDataRow(sheet, styles, currentRowIdx);
            } else {
                for (int i = 0; i < payrollList.size(); i++) {
                    PayrollDto p = payrollList.get(i);
                    Row row = sheet.createRow(currentRowIdx++);
                    row.setHeightInPoints(20);

                    createNumericCell(row, 0, i + 1, styles.centerNumberStyle);
                    createNumericCell(row, 1, p.getStoreStaffId() != null ? p.getStoreStaffId() : 0, styles.centerNumberStyle);
                    createTextCell(row, 2, p.getStaffName() != null ? p.getStaffName() : "-", styles.leftTextStyle);
                    boolean isConfirmed = "CONFIRMED".equals(p.getPayrollStatus());
                    createTextCell(row, 3, isConfirmed ? "確定" : "未確定", isConfirmed ? styles.confirmedStyle : styles.draftStyle);
                    createNumericCell(row, 4, p.getWorkDaysCount() != null ? p.getWorkDaysCount() : 0, styles.daysStyle);
                    createNumericCell(row, 5, p.getTotalWorkMinutes() != null ? p.getTotalWorkMinutes() : 0, styles.minutesStyle);
                    createNumericCell(row, 6, p.getTotalNightMinutes() != null ? p.getTotalNightMinutes() : 0, styles.minutesStyle);
                    createNumericCell(row, 7, p.getTotalOvertimeMinutes() != null ? p.getTotalOvertimeMinutes() : 0, styles.minutesStyle);
                    createNumericCell(row, 8, p.getAppliedHourlyWage() != null ? p.getAppliedHourlyWage() : 0, styles.currencyStyle);
                    createNumericCell(row, 9, p.getBasePay() != null ? p.getBasePay() : 0, styles.currencyStyle);
                    createNumericCell(row, 10, p.getNightAllowance() != null ? p.getNightAllowance() : 0, styles.currencyStyle);
                    createNumericCell(row, 11, p.getOvertimeAllowance() != null ? p.getOvertimeAllowance() : 0, styles.currencyStyle);
                    createNumericCell(row, 12, p.getTransportAllowance() != null ? p.getTransportAllowance() : 0, styles.currencyStyle);
                    createNumericCell(row, 13, p.getGrossPay() != null ? p.getGrossPay() : 0, styles.grossPayStyle);
                }

                createSummaryRows(sheet, styles, startDataRowIdx, currentRowIdx - 1);
            }

            workbook.write(outputStream);
        } finally {
            try {
                workbook.dispose();
            } catch (Exception ignored) {
            }
            workbook.close();
        }
    }
}
```

### 2.5 Verification Test: `PayrollExcelExporterTest.java`
- `testExportToExcelWithData`: 複数件データ、ヘッダー、社員行、合計数式(`SUM(N6:N7)`)、平均数式(`AVERAGE(N6:N7)`)の整合性検証
- `testExportToExcelEmptyData`: 0件時案内メッセージおよび数式行スキップによる例外完全防止
- `testExportToStream`: OutputStreamへの直接ストリーミング検証
- `testLargeDataExport`: SXSSFウィンドウ(100行)を超える250件のストリーミング書き込み、ディスクフラッシュ、`SUM(N6:N255)` 数式整合性検証
