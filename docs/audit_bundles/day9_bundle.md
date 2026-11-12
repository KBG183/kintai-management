package com.example.kintai.domain.payroll.util;

import com.example.kintai.domain.payroll.dto.PayrollDto;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.streaming.SXSSFSheet;
import org.apache.poi.xssf.streaming.SXSSFWorkbook;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * 労働基準法第108条（賃金台帳）準拠 月次給与台帳 Excel エクスポートユーティリティ
 * <p>
 * 大量データ処理時のJVMヒープ枯渇（Out Of Memory）を防止するため、
 * Apache POI SXSSFWorkbook（ローリングウィンドウ100行）によるストリーミング書き込みを実施し、
 * 出力完了後は一時ファイル（temp files）の確実な破棄（dispose）を保証する。
 * </p>
 */
public final class PayrollExcelExporter {

    private static final int ROW_ACCESS_WINDOW_SIZE = 100;
    private static final DateTimeFormatter TIMESTAMP_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private PayrollExcelExporter() {
    }

    /**
     * 給与台帳をバイト配列として生成（後方互換性用）
     */
    public static byte[] exportToExcel(String targetYearMonth, List<PayrollDto> payrollList) throws IOException {
        return exportToExcel(targetYearMonth, null, payrollList);
    }

    /**
     * 店舗名を含む給与台帳をバイト配列として生成
     */
    public static byte[] exportToExcel(String targetYearMonth, String storeName, List<PayrollDto> payrollList) throws IOException {
        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            exportToStream(targetYearMonth, storeName, payrollList, out);
            return out.toByteArray();
        }
    }

    /**
     * 給与台帳を指定の出力ストリームへ直接ストリーミング書き込み
     */
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

            // ウィンドウ枠の固定（タイトル・ヘッダー行および社員識別列を固定）
            sheet.createFreezePane(3, 5);

            // カラム幅の明示的設定（SXSSFSheetでのメモリ消費・CPU負荷を抑制）
            configureColumnWidths(sheet);

            // スタイルの初期化
            ExcelStyles styles = new ExcelStyles(workbook);

            // 1. タイトル及びメタ情報ブロックの作成
            createTitleAndMetaBlock(sheet, styles, targetYearMonth, storeName);

            // 2. テーブルヘッダー行の作成 (Row 4)
            createTableHeaderRow(sheet, styles);

            // 3. データ行の書き込み (Row 5〜)
            int startDataRowIdx = 5;
            int currentRowIdx = startDataRowIdx;

            if (payrollList == null || payrollList.isEmpty()) {
                createEmptyDataRow(sheet, styles, currentRowIdx);
            } else {
                for (int i = 0; i < payrollList.size(); i++) {
                    PayrollDto p = payrollList.get(i);
                    Row row = sheet.createRow(currentRowIdx++);
                    row.setHeightInPoints(20);

                    // No. (連番)
                    createNumericCell(row, 0, i + 1, styles.centerNumberStyle);

                    // スタッフID
                    createNumericCell(row, 1, p.getStoreStaffId() != null ? p.getStoreStaffId() : 0, styles.centerNumberStyle);

                    // 氏名
                    createTextCell(row, 2, p.getStaffName() != null ? p.getStaffName() : "-", styles.leftTextStyle);

                    // 状態 (CONFIRMED / DRAFT)
                    boolean isConfirmed = "CONFIRMED".equals(p.getPayrollStatus());
                    createTextCell(row, 3, isConfirmed ? "確定" : "未確定", isConfirmed ? styles.confirmedStyle : styles.draftStyle);

                    // 労働日数
                    createNumericCell(row, 4, p.getWorkDaysCount() != null ? p.getWorkDaysCount() : 0, styles.daysStyle);

                    // 総労働時間(分)
                    createNumericCell(row, 5, p.getTotalWorkMinutes() != null ? p.getTotalWorkMinutes() : 0, styles.minutesStyle);

                    // 深夜労働(分)
                    createNumericCell(row, 6, p.getTotalNightMinutes() != null ? p.getTotalNightMinutes() : 0, styles.minutesStyle);

                    // 残業(分)
                    createNumericCell(row, 7, p.getTotalOvertimeMinutes() != null ? p.getTotalOvertimeMinutes() : 0, styles.minutesStyle);

                    // 適用時給
                    createNumericCell(row, 8, p.getAppliedHourlyWage() != null ? p.getAppliedHourlyWage() : 0, styles.currencyStyle);

                    // 基本給
                    createNumericCell(row, 9, p.getBasePay() != null ? p.getBasePay() : 0, styles.currencyStyle);

                    // 深夜手当
                    createNumericCell(row, 10, p.getNightAllowance() != null ? p.getNightAllowance() : 0, styles.currencyStyle);

                    // 残業手当
                    createNumericCell(row, 11, p.getOvertimeAllowance() != null ? p.getOvertimeAllowance() : 0, styles.currencyStyle);

                    // 通勤手当
                    createNumericCell(row, 12, p.getTransportAllowance() != null ? p.getTransportAllowance() : 0, styles.currencyStyle);

                    // 総支給額
                    createNumericCell(row, 13, p.getGrossPay() != null ? p.getGrossPay() : 0, styles.grossPayStyle);
                }

                // 4. 合計行および平均行の作成 (Excel数式連携)
                createSummaryRows(sheet, styles, startDataRowIdx, currentRowIdx - 1);
            }

            workbook.write(outputStream);
        } finally {
            // ディスク一時ファイルの確実な消去
            try {
                workbook.dispose();
            } catch (Exception ignored) {
            }
            workbook.close();
        }
    }

    private static void configureColumnWidths(SXSSFSheet sheet) {
        sheet.setColumnWidth(0, 6 * 256);   // No.
        sheet.setColumnWidth(1, 11 * 256);  // スタッフID
        sheet.setColumnWidth(2, 18 * 256);  // 氏名
        sheet.setColumnWidth(3, 12 * 256);  // 状態
        sheet.setColumnWidth(4, 12 * 256);  // 労働日数
        sheet.setColumnWidth(5, 15 * 256);  // 総労働時間
        sheet.setColumnWidth(6, 15 * 256);  // 深夜労働
        sheet.setColumnWidth(7, 15 * 256);  // 残業時間
        sheet.setColumnWidth(8, 14 * 256);  // 適用時給
        sheet.setColumnWidth(9, 15 * 256);  // 基本給
        sheet.setColumnWidth(10, 15 * 256); // 深夜手当
        sheet.setColumnWidth(11, 15 * 256); // 残業手当
        sheet.setColumnWidth(12, 14 * 256); // 通勤手当
        sheet.setColumnWidth(13, 18 * 256); // 総支給額
    }

    private static void createTitleAndMetaBlock(
            SXSSFSheet sheet,
            ExcelStyles styles,
            String targetYearMonth,
            String storeName
    ) {
        // Row 0: タイトル
        Row titleRow = sheet.createRow(0);
        titleRow.setHeightInPoints(28);
        Cell titleCell = titleRow.createCell(0);
        titleCell.setCellValue("月次給与台帳 (賃金台帳)");
        titleCell.setCellStyle(styles.titleStyle);
        sheet.addMergedRegion(new CellRangeAddress(0, 0, 0, 11));

        // 押印枠 (Row 0〜2, Col 12: 承認印, Col 13: 担当印)
        createHankoBox(sheet, styles, 12, "承認印");
        createHankoBox(sheet, styles, 13, "担当印");

        // Row 1: 対象年月 & 店舗名
        Row row1 = sheet.createRow(1);
        row1.setHeightInPoints(18);
        createLabelValue(row1, 0, "対象年月:", styles.metaLabelStyle);
        createLabelValue(row1, 1, targetYearMonth != null ? targetYearMonth : "-", styles.metaValueStyle);
        sheet.addMergedRegion(new CellRangeAddress(1, 1, 1, 2));

        createLabelValue(row1, 3, "対象店舗:", styles.metaLabelStyle);
        createLabelValue(row1, 4, storeName != null ? storeName : "全店共通", styles.metaValueStyle);
        sheet.addMergedRegion(new CellRangeAddress(1, 1, 4, 7));

        // Row 2: 出力日時 & 根拠法令
        Row row2 = sheet.createRow(2);
        row2.setHeightInPoints(18);
        createLabelValue(row2, 0, "出力日時:", styles.metaLabelStyle);
        createLabelValue(row2, 1, LocalDateTime.now().format(TIMESTAMP_FORMATTER), styles.metaValueStyle);
        sheet.addMergedRegion(new CellRangeAddress(2, 2, 1, 2));

        createLabelValue(row2, 3, "準拠基準:", styles.metaLabelStyle);
        createLabelValue(row2, 4, "労働基準法第108条（賃金台帳・割増計算規則準拠）", styles.metaValueStyle);
        sheet.addMergedRegion(new CellRangeAddress(2, 2, 4, 7));

        // Row 3: 区切り空白行
        Row blankRow = sheet.createRow(3);
        blankRow.setHeightInPoints(8);
    }

    private static void createHankoBox(SXSSFSheet sheet, ExcelStyles styles, int colIdx, String label) {
        Row row0 = sheet.getRow(0);
        Cell headerCell = row0.createCell(colIdx);
        headerCell.setCellValue(label);
        headerCell.setCellStyle(styles.hankoHeaderStyle);

        Row row1 = sheet.getRow(1);
        if (row1 == null) row1 = sheet.createRow(1);
        Cell bodyCell1 = row1.createCell(colIdx);
        bodyCell1.setCellStyle(styles.hankoBoxStyle);

        Row row2 = sheet.getRow(2);
        if (row2 == null) row2 = sheet.createRow(2);
        Cell bodyCell2 = row2.createCell(colIdx);
        bodyCell2.setCellStyle(styles.hankoBoxStyle);

        sheet.addMergedRegion(new CellRangeAddress(1, 2, colIdx, colIdx));
    }

    private static void createTableHeaderRow(SXSSFSheet sheet, ExcelStyles styles) {
        String[] headers = {
                "No.", "スタッフID", "氏名", "確定状態",
                "労働日数", "総労働時間", "深夜労働", "残業時間",
                "適用時給", "基本給", "深夜手当", "残業手当", "通勤手当", "総支給額"
        };

        Row headerRow = sheet.createRow(4);
        headerRow.setHeightInPoints(24);

        for (int i = 0; i < headers.length; i++) {
            Cell cell = headerRow.createCell(i);
            cell.setCellValue(headers[i]);
            cell.setCellStyle(styles.tableHeaderStyle);
        }
    }

    private static void createEmptyDataRow(SXSSFSheet sheet, ExcelStyles styles, int rowIdx) {
        Row emptyRow = sheet.createRow(rowIdx);
        emptyRow.setHeightInPoints(24);
        Cell cell = emptyRow.createCell(0);
        cell.setCellValue("該当する給与データが存在しません。");
        cell.setCellStyle(styles.centerTextStyle);
        sheet.addMergedRegion(new CellRangeAddress(rowIdx, rowIdx, 0, 13));
    }

    private static void createSummaryRows(
            SXSSFSheet sheet,
            ExcelStyles styles,
            int startRowIdx,
            int endRowIdx
    ) {
        int excelStartRow = startRowIdx + 1; // 1-indexed (Excel表示行)
        int excelEndRow = endRowIdx + 1;

        // 1. 合計行 (Row endRowIdx + 1)
        Row totalRow = sheet.createRow(endRowIdx + 1);
        totalRow.setHeightInPoints(22);

        Cell totalLabelCell = totalRow.createCell(0);
        totalLabelCell.setCellValue("総合計");
        totalLabelCell.setCellStyle(styles.summaryLabelStyle);
        for (int c = 1; c <= 3; c++) {
            Cell emptyCell = totalRow.createCell(c);
            emptyCell.setCellStyle(styles.summaryLabelStyle);
        }
        sheet.addMergedRegion(new CellRangeAddress(endRowIdx + 1, endRowIdx + 1, 0, 3));

        // 数式設定 (合計)
        createFormulaCell(totalRow, 4, String.format("SUM(E%d:E%d)", excelStartRow, excelEndRow), styles.summaryDaysStyle);
        createFormulaCell(totalRow, 5, String.format("SUM(F%d:F%d)", excelStartRow, excelEndRow), styles.summaryMinutesStyle);
        createFormulaCell(totalRow, 6, String.format("SUM(G%d:G%d)", excelStartRow, excelEndRow), styles.summaryMinutesStyle);
        createFormulaCell(totalRow, 7, String.format("SUM(H%d:H%d)", excelStartRow, excelEndRow), styles.summaryMinutesStyle);

        // 適用時給は合計対象外
        createTextCell(totalRow, 8, "-", styles.summaryTextStyle);

        createFormulaCell(totalRow, 9, String.format("SUM(J%d:J%d)", excelStartRow, excelEndRow), styles.summaryCurrencyStyle);
        createFormulaCell(totalRow, 10, String.format("SUM(K%d:K%d)", excelStartRow, excelEndRow), styles.summaryCurrencyStyle);
        createFormulaCell(totalRow, 11, String.format("SUM(L%d:L%d)", excelStartRow, excelEndRow), styles.summaryCurrencyStyle);
        createFormulaCell(totalRow, 12, String.format("SUM(M%d:M%d)", excelStartRow, excelEndRow), styles.summaryCurrencyStyle);
        createFormulaCell(totalRow, 13, String.format("SUM(N%d:N%d)", excelStartRow, excelEndRow), styles.summaryGrossPayStyle);

        // 2. 平均行 (Row endRowIdx + 2)
        Row avgRow = sheet.createRow(endRowIdx + 2);
        avgRow.setHeightInPoints(20);

        Cell avgLabelCell = avgRow.createCell(0);
        avgLabelCell.setCellValue("1人当たり平均");
        avgLabelCell.setCellStyle(styles.avgLabelStyle);
        for (int c = 1; c <= 3; c++) {
            Cell emptyCell = avgRow.createCell(c);
            emptyCell.setCellStyle(styles.avgLabelStyle);
        }
        sheet.addMergedRegion(new CellRangeAddress(endRowIdx + 2, endRowIdx + 2, 0, 3));

        // 数式設定 (平均)
        createFormulaCell(avgRow, 4, String.format("AVERAGE(E%d:E%d)", excelStartRow, excelEndRow), styles.avgDaysStyle);
        createFormulaCell(avgRow, 5, String.format("AVERAGE(F%d:F%d)", excelStartRow, excelEndRow), styles.avgMinutesStyle);
        createFormulaCell(avgRow, 6, String.format("AVERAGE(G%d:G%d)", excelStartRow, excelEndRow), styles.avgMinutesStyle);
        createFormulaCell(avgRow, 7, String.format("AVERAGE(H%d:H%d)", excelStartRow, excelEndRow), styles.avgMinutesStyle);
        createFormulaCell(avgRow, 8, String.format("AVERAGE(I%d:I%d)", excelStartRow, excelEndRow), styles.avgCurrencyStyle);
        createFormulaCell(avgRow, 9, String.format("AVERAGE(J%d:J%d)", excelStartRow, excelEndRow), styles.avgCurrencyStyle);
        createFormulaCell(avgRow, 10, String.format("AVERAGE(K%d:K%d)", excelStartRow, excelEndRow), styles.avgCurrencyStyle);
        createFormulaCell(avgRow, 11, String.format("AVERAGE(L%d:L%d)", excelStartRow, excelEndRow), styles.avgCurrencyStyle);
        createFormulaCell(avgRow, 12, String.format("AVERAGE(M%d:M%d)", excelStartRow, excelEndRow), styles.avgCurrencyStyle);
        createFormulaCell(avgRow, 13, String.format("AVERAGE(N%d:N%d)", excelStartRow, excelEndRow), styles.avgCurrencyStyle);
    }

    private static void createLabelValue(Row row, int colIdx, String text, CellStyle style) {
        Cell cell = row.createCell(colIdx);
        cell.setCellValue(text);
        cell.setCellStyle(style);
    }

    private static void createTextCell(Row row, int colIdx, String text, CellStyle style) {
        Cell cell = row.createCell(colIdx);
        cell.setCellValue(text);
        cell.setCellStyle(style);
    }

    private static void createNumericCell(Row row, int colIdx, double value, CellStyle style) {
        Cell cell = row.createCell(colIdx);
        cell.setCellValue(value);
        cell.setCellStyle(style);
    }

    private static void createFormulaCell(Row row, int colIdx, String formula, CellStyle style) {
        Cell cell = row.createCell(colIdx);
        cell.setCellFormula(formula);
        cell.setCellStyle(style);
    }

    /**
     * Excel帳票用スタイル集約ホルダークラス
     */
    private static final class ExcelStyles {
        final CellStyle titleStyle;
        final CellStyle metaLabelStyle;
        final CellStyle metaValueStyle;
        final CellStyle hankoHeaderStyle;
        final CellStyle hankoBoxStyle;
        final CellStyle tableHeaderStyle;
        final CellStyle leftTextStyle;
        final CellStyle centerTextStyle;
        final CellStyle centerNumberStyle;
        final CellStyle daysStyle;
        final CellStyle minutesStyle;
        final CellStyle currencyStyle;
        final CellStyle grossPayStyle;
        final CellStyle confirmedStyle;
        final CellStyle draftStyle;

        // 合計・平均スタイル
        final CellStyle summaryLabelStyle;
        final CellStyle summaryTextStyle;
        final CellStyle summaryDaysStyle;
        final CellStyle summaryMinutesStyle;
        final CellStyle summaryCurrencyStyle;
        final CellStyle summaryGrossPayStyle;

        final CellStyle avgLabelStyle;
        final CellStyle avgDaysStyle;
        final CellStyle avgMinutesStyle;
        final CellStyle avgCurrencyStyle;

        ExcelStyles(Workbook workbook) {
            DataFormat df = workbook.createDataFormat();

            // 共通フォント定義
            Font defaultFont = workbook.createFont();
            defaultFont.setFontName("Meiryo UI");
            defaultFont.setFontHeightInPoints((short) 10);

            Font boldFont = workbook.createFont();
            boldFont.setFontName("Meiryo UI");
            boldFont.setFontHeightInPoints((short) 10);
            boldFont.setBold(true);

            Font titleFont = workbook.createFont();
            titleFont.setFontName("Meiryo UI");
            titleFont.setFontHeightInPoints((short) 16);
            titleFont.setBold(true);
            titleFont.setColor(IndexedColors.DARK_BLUE.getIndex());

            Font whiteHeaderFont = workbook.createFont();
            whiteHeaderFont.setFontName("Meiryo UI");
            whiteHeaderFont.setFontHeightInPoints((short) 10);
            whiteHeaderFont.setBold(true);
            whiteHeaderFont.setColor(IndexedColors.WHITE.getIndex());

            Font confirmedFont = workbook.createFont();
            confirmedFont.setFontName("Meiryo UI");
            confirmedFont.setFontHeightInPoints((short) 10);
            confirmedFont.setBold(true);
            confirmedFont.setColor(IndexedColors.DARK_GREEN.getIndex());

            Font draftFont = workbook.createFont();
            draftFont.setFontName("Meiryo UI");
            draftFont.setFontHeightInPoints((short) 10);
            draftFont.setColor(IndexedColors.DARK_YELLOW.getIndex());

            Font italicFont = workbook.createFont();
            italicFont.setFontName("Meiryo UI");
            italicFont.setFontHeightInPoints((short) 9);
            italicFont.setItalic(true);

            // 1. タイトルスタイル
            titleStyle = workbook.createCellStyle();
            titleStyle.setFont(titleFont);
            titleStyle.setAlignment(HorizontalAlignment.LEFT);
            titleStyle.setVerticalAlignment(VerticalAlignment.CENTER);

            // 2. メタ情報スタイル
            metaLabelStyle = workbook.createCellStyle();
            metaLabelStyle.setFont(boldFont);
            metaLabelStyle.setAlignment(HorizontalAlignment.RIGHT);
            metaLabelStyle.setVerticalAlignment(VerticalAlignment.CENTER);

            metaValueStyle = workbook.createCellStyle();
            metaValueStyle.setFont(defaultFont);
            metaValueStyle.setAlignment(HorizontalAlignment.LEFT);
            metaValueStyle.setVerticalAlignment(VerticalAlignment.CENTER);

            // 3. 押印枠スタイル
            hankoHeaderStyle = workbook.createCellStyle();
            hankoHeaderStyle.setFont(boldFont);
            hankoHeaderStyle.setAlignment(HorizontalAlignment.CENTER);
            hankoHeaderStyle.setVerticalAlignment(VerticalAlignment.CENTER);
            hankoHeaderStyle.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
            hankoHeaderStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            applyThinBorders(hankoHeaderStyle);

            hankoBoxStyle = workbook.createCellStyle();
            applyThinBorders(hankoBoxStyle);

            // 4. テーブルヘッダースタイル
            tableHeaderStyle = workbook.createCellStyle();
            tableHeaderStyle.setFont(whiteHeaderFont);
            tableHeaderStyle.setFillForegroundColor(IndexedColors.DARK_BLUE.getIndex());
            tableHeaderStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            tableHeaderStyle.setAlignment(HorizontalAlignment.CENTER);
            tableHeaderStyle.setVerticalAlignment(VerticalAlignment.CENTER);
            applyThinBorders(tableHeaderStyle);

            // 5. データ行スタイル
            leftTextStyle = workbook.createCellStyle();
            leftTextStyle.setFont(defaultFont);
            leftTextStyle.setAlignment(HorizontalAlignment.LEFT);
            leftTextStyle.setVerticalAlignment(VerticalAlignment.CENTER);
            applyThinBorders(leftTextStyle);

            centerTextStyle = workbook.createCellStyle();
            centerTextStyle.setFont(defaultFont);
            centerTextStyle.setAlignment(HorizontalAlignment.CENTER);
            centerTextStyle.setVerticalAlignment(VerticalAlignment.CENTER);
            applyThinBorders(centerTextStyle);

            centerNumberStyle = workbook.createCellStyle();
            centerNumberStyle.setFont(defaultFont);
            centerNumberStyle.setAlignment(HorizontalAlignment.CENTER);
            centerNumberStyle.setVerticalAlignment(VerticalAlignment.CENTER);
            applyThinBorders(centerNumberStyle);

            daysStyle = workbook.createCellStyle();
            daysStyle.setFont(defaultFont);
            daysStyle.setDataFormat(df.getFormat("#,##0\"日\""));
            daysStyle.setAlignment(HorizontalAlignment.RIGHT);
            daysStyle.setVerticalAlignment(VerticalAlignment.CENTER);
            applyThinBorders(daysStyle);

            minutesStyle = workbook.createCellStyle();
            minutesStyle.setFont(defaultFont);
            minutesStyle.setDataFormat(df.getFormat("#,##0\"分\""));
            minutesStyle.setAlignment(HorizontalAlignment.RIGHT);
            minutesStyle.setVerticalAlignment(VerticalAlignment.CENTER);
            applyThinBorders(minutesStyle);

            currencyStyle = workbook.createCellStyle();
            currencyStyle.setFont(defaultFont);
            currencyStyle.setDataFormat(df.getFormat("#,##0\"円\""));
            currencyStyle.setAlignment(HorizontalAlignment.RIGHT);
            currencyStyle.setVerticalAlignment(VerticalAlignment.CENTER);
            applyThinBorders(currencyStyle);

            grossPayStyle = workbook.createCellStyle();
            grossPayStyle.setFont(boldFont);
            grossPayStyle.setDataFormat(df.getFormat("#,##0\"円\""));
            grossPayStyle.setAlignment(HorizontalAlignment.RIGHT);
            grossPayStyle.setVerticalAlignment(VerticalAlignment.CENTER);
            applyThinBorders(grossPayStyle);

            confirmedStyle = workbook.createCellStyle();
            confirmedStyle.setFont(confirmedFont);
            confirmedStyle.setAlignment(HorizontalAlignment.CENTER);
            confirmedStyle.setVerticalAlignment(VerticalAlignment.CENTER);
            applyThinBorders(confirmedStyle);

            draftStyle = workbook.createCellStyle();
            draftStyle.setFont(draftFont);
            draftStyle.setAlignment(HorizontalAlignment.CENTER);
            draftStyle.setVerticalAlignment(VerticalAlignment.CENTER);
            applyThinBorders(draftStyle);

            // 6. 合計スタイル (二重下線・太字)
            summaryLabelStyle = workbook.createCellStyle();
            summaryLabelStyle.setFont(boldFont);
            summaryLabelStyle.setAlignment(HorizontalAlignment.CENTER);
            summaryLabelStyle.setVerticalAlignment(VerticalAlignment.CENTER);
            summaryLabelStyle.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
            summaryLabelStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            applyDoubleBottomBorders(summaryLabelStyle);

            summaryTextStyle = workbook.createCellStyle();
            summaryTextStyle.setFont(boldFont);
            summaryTextStyle.setAlignment(HorizontalAlignment.CENTER);
            summaryTextStyle.setVerticalAlignment(VerticalAlignment.CENTER);
            summaryTextStyle.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
            summaryTextStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            applyDoubleBottomBorders(summaryTextStyle);

            summaryDaysStyle = workbook.createCellStyle();
            summaryDaysStyle.setFont(boldFont);
            summaryDaysStyle.setDataFormat(df.getFormat("#,##0\"日\""));
            summaryDaysStyle.setAlignment(HorizontalAlignment.RIGHT);
            summaryDaysStyle.setVerticalAlignment(VerticalAlignment.CENTER);
            summaryDaysStyle.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
            summaryDaysStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            applyDoubleBottomBorders(summaryDaysStyle);

            summaryMinutesStyle = workbook.createCellStyle();
            summaryMinutesStyle.setFont(boldFont);
            summaryMinutesStyle.setDataFormat(df.getFormat("#,##0\"分\""));
            summaryMinutesStyle.setAlignment(HorizontalAlignment.RIGHT);
            summaryMinutesStyle.setVerticalAlignment(VerticalAlignment.CENTER);
            summaryMinutesStyle.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
            summaryMinutesStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            applyDoubleBottomBorders(summaryMinutesStyle);

            summaryCurrencyStyle = workbook.createCellStyle();
            summaryCurrencyStyle.setFont(boldFont);
            summaryCurrencyStyle.setDataFormat(df.getFormat("#,##0\"円\""));
            summaryCurrencyStyle.setAlignment(HorizontalAlignment.RIGHT);
            summaryCurrencyStyle.setVerticalAlignment(VerticalAlignment.CENTER);
            summaryCurrencyStyle.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
            summaryCurrencyStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            applyDoubleBottomBorders(summaryCurrencyStyle);

            summaryGrossPayStyle = workbook.createCellStyle();
            summaryGrossPayStyle.setFont(boldFont);
            summaryGrossPayStyle.setDataFormat(df.getFormat("#,##0\"円\""));
            summaryGrossPayStyle.setAlignment(HorizontalAlignment.RIGHT);
            summaryGrossPayStyle.setVerticalAlignment(VerticalAlignment.CENTER);
            summaryGrossPayStyle.setFillForegroundColor(IndexedColors.LIGHT_YELLOW.getIndex());
            summaryGrossPayStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            applyDoubleBottomBorders(summaryGrossPayStyle);

            // 7. 平均スタイル
            avgLabelStyle = workbook.createCellStyle();
            avgLabelStyle.setFont(italicFont);
            avgLabelStyle.setAlignment(HorizontalAlignment.CENTER);
            avgLabelStyle.setVerticalAlignment(VerticalAlignment.CENTER);
            applyThinBorders(avgLabelStyle);

            avgDaysStyle = workbook.createCellStyle();
            avgDaysStyle.setFont(italicFont);
            avgDaysStyle.setDataFormat(df.getFormat("#,##0.0\"日\""));
            avgDaysStyle.setAlignment(HorizontalAlignment.RIGHT);
            avgDaysStyle.setVerticalAlignment(VerticalAlignment.CENTER);
            applyThinBorders(avgDaysStyle);

            avgMinutesStyle = workbook.createCellStyle();
            avgMinutesStyle.setFont(italicFont);
            avgMinutesStyle.setDataFormat(df.getFormat("#,##0.0\"分\""));
            avgMinutesStyle.setAlignment(HorizontalAlignment.RIGHT);
            avgMinutesStyle.setVerticalAlignment(VerticalAlignment.CENTER);
            applyThinBorders(avgMinutesStyle);

            avgCurrencyStyle = workbook.createCellStyle();
            avgCurrencyStyle.setFont(italicFont);
            avgCurrencyStyle.setDataFormat(df.getFormat("#,##0\"円\""));
            avgCurrencyStyle.setAlignment(HorizontalAlignment.RIGHT);
            avgCurrencyStyle.setVerticalAlignment(VerticalAlignment.CENTER);
            applyThinBorders(avgCurrencyStyle);
        }

        private static void applyThinBorders(CellStyle style) {
            style.setBorderTop(BorderStyle.THIN);
            style.setBorderBottom(BorderStyle.THIN);
            style.setBorderLeft(BorderStyle.THIN);
            style.setBorderRight(BorderStyle.THIN);
        }

        private static void applyDoubleBottomBorders(CellStyle style) {
            style.setBorderTop(BorderStyle.THIN);
            style.setBorderBottom(BorderStyle.DOUBLE);
            style.setBorderLeft(BorderStyle.THIN);
            style.setBorderRight(BorderStyle.THIN);
        }
    }
}
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
     * 月別給与の一括集計および計算 (Upsert)
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
        // 排他制御: 同時計算競合を防止する悲観的ロック (スタッフ単位)
        payrollMapper.lockStoreStaffForUpdate(staffId);

        // 確定済み給与の不変性保護 (CONFIRMED / PAID の場合は再計算・上書きを遮断, 行ロック付き照会)
        PayrollDto existingPayroll = payrollMapper.findByStaffAndMonthForUpdate(staffId, targetYearMonth);
        if (existingPayroll != null && !"DRAFT".equals(existingPayroll.getPayrollStatus())) {
            throw new IllegalStateException("指定年月 (" + targetYearMonth + ") の給与は既に確定済み(" +
                    existingPayroll.getPayrollStatus() + ") のため、再計算できません。(給与不変性保護)");
        }

        // 未処理勤怠の確認（打刻途中や未承認申請がある場合は計算中断）
        int unresolvedCount = payrollMapper.countUnresolvedAttendance(staffId, startDate, endDate);
        if (unresolvedCount > 0) {
            throw new IllegalStateException("未承認または打刻途中の勤怠が存在するため、給与計算を中断しました。(職員ID: " + staffId + ")");
        }

        List<AttendanceDto> attendances = attendanceMapper.findCompletedAttendancesForStaffAndPeriod(staffId, startDate, endDate);

        // 通勤手当上限額（1回の精算あたりの上限）の取得
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
            // 勤怠データの負値・異常値バリデーション (F-07 堅牢性保証)
            if (att.getNetWorkMinutes() < 0 || att.getNightWorkMinutes() < 0 || att.getOvertimeMinutes() < 0 || att.getTotalBreakMinutes() < 0) {
                throw new IllegalStateException("勤務時間または休憩時間に負の値が含まれているため、給与計算を中断しました。(勤怠ID: " + att.getAttendanceId() + ")");
            }

            LocalDate workDate = att.getWorkDate();
            Integer hourlyWageVal = payrollMapper.findHourlyWageForStaffOnDate(staffId, workDate);
            if (hourlyWageVal == null || hourlyWageVal <= 0) {
                throw new IllegalStateException("勤務日 (" + workDate + ") に有効な契約時給データが存在しません。");
            }
            int hourlyWage = hourlyWageVal;
            uniqueWages.add(hourlyWage);

            // 2026年東京都最低賃金 (1,280円) 割れを防止
            if (hourlyWage < 1280) {
                throw new IllegalStateException("契約時給 (" + hourlyWage + "円) が法定最低賃金 (1,280円) を下回っています。");
            }

            // 日別計算（端数は HALF_UP で厳密に丸め）
            PayrollCalculator.DailyPayResult dailyPay = PayrollCalculator.calculateDailyPay(
                    hourlyWage,
                    att.getNetWorkMinutes(),
                    att.getNightWorkMinutes(),
                    att.getOvertimeMinutes()
            );

            // 1日1回通勤手当支給および月額上限枠内での配分
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

        // 【通達 昭和62年(63年) 基発第150号 法定月間端数処理の適用】
        // 単一契約時給の場合は、月間割増手当総額の法定端数処理（30分境界丸め＋手当別50銭四捨五入）を反映
        if (appliedHourlyWage != null) {
            StatutoryPayrollResult statutoryResult = applyStatutoryMonthlyRounding(payroll);
            // 法定端数処理結果を手当額および総支給額に反映
            payroll.setOvertimeAllowance(statutoryResult.statutoryOvertimeAllowance());
            payroll.setNightAllowance(statutoryResult.statutoryNightAllowance());
            payroll.setGrossPay(payroll.getBasePay() + payroll.getNightAllowance() + payroll.getOvertimeAllowance() + payroll.getTransportAllowance());
        }

        payrollMapper.upsertPayroll(payroll);

        // upsert 후 payrollId 보장 (ON DUPLICATE KEY UPDATE 시 기존 레코드 ID 보존)
        if (payroll.getPayrollId() == null) {
            PayrollDto persisted = payrollMapper.findByStaffAndMonth(staffId, targetYearMonth);
            if (persisted != null) {
                payroll.setPayrollId(persisted.getPayrollId());
            }
        }

        // 日別詳細スナップショットの再保存
        payrollMapper.deleteDailyDetailsByPayrollId(payroll.getPayrollId());
        for (PayrollDailyDetailDto detail : detailsToInsert) {
            detail.setPayrollId(payroll.getPayrollId());
            payrollMapper.insertPayrollDailyDetail(detail);
        }

        return payroll;
    }

    /**
     * 【行政通達 昭和62年3月14日付 基発第150号 準拠】
     * 月間3大端数処理（時間外・深夜・休日の30分境界月間丸めおよび50銭境界手当集計）を適用した法定集計
     */
    public StatutoryPayrollResult applyStatutoryMonthlyRounding(PayrollDto payroll) {
        return applyStatutoryMonthlyRounding(payroll, 0);
    }

    /**
     * 【行政通達 昭和62年3月14日付 基発第150号 準拠】
     * 月間3大端数処理（時間外・深夜・休日の30分境界月間丸めおよび50銭境界手当集計、休日労働時間連動版）
     */
    public StatutoryPayrollResult applyStatutoryMonthlyRounding(PayrollDto payroll, int totalHolidayMinutes) {
        if (payroll == null) {
            throw new IllegalArgumentException("給与データは必須です。");
        }

        // ① 月間労働時間の端数処理（時間外・深夜・休日それぞれ30分未満切り捨て、30分以上1時間に切り上げ）
        int roundedOvertimeHours = PayrollCalculator.StatutoryRounding.roundMonthlyHours(payroll.getTotalOvertimeMinutes());
        int roundedNightHours = PayrollCalculator.StatutoryRounding.roundMonthlyHours(payroll.getTotalNightMinutes());
        int roundedHolidayHours = PayrollCalculator.StatutoryRounding.roundMonthlyHours(Math.max(0, totalHolidayMinutes));

        int hourlyWage = (payroll.getAppliedHourlyWage() != null) ? payroll.getAppliedHourlyWage() : 1280;

        // ② 1時間あたりの割増賃金額（50銭未満切り捨て、50銭以上1円切り上げ）
        // 時間外: 25%加算
        int overtimeHourlyPremium = PayrollCalculator.StatutoryRounding.roundHourlyRate(
                java.math.BigDecimal.valueOf(hourlyWage).multiply(java.math.BigDecimal.valueOf(0.25))
        );
        // 深夜: 25%加算
        int nightHourlyPremium = PayrollCalculator.StatutoryRounding.roundHourlyRate(
                java.math.BigDecimal.valueOf(hourlyWage).multiply(java.math.BigDecimal.valueOf(0.25))
        );
        // 休日: 35%加算 (労基法第37条第1項)
        int holidayHourlyPremium = PayrollCalculator.StatutoryRounding.roundHourlyRate(
                java.math.BigDecimal.valueOf(hourlyWage).multiply(java.math.BigDecimal.valueOf(0.35))
        );

        // ③ 月間割増手当総額の端数処理（項目別独立集計・50銭未満切り捨て、50銭以上1円切り上げ）
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
     * 給与確定ロック処理（単一レコードの帳簿不変性を保証、排他悲観적ロック適用）
     * 락 순서 역전(데드락) 방지를 위해 전역 락 계층(store_staff -> payroll)을 엄격 준수하고
     * 무잠금 초기 조회와 잠금 후 재조회 간 storeStaffId 일치 여부를 2중 검증(Double-Checked Lock)하여 TOCTOU를 방어합니다.
     */
    @Transactional
    public void confirmPayroll(Long payrollId, PayrollConfirmRequestDto confirmDto) {
        // [Step 1] storeStaffId 및 storeId 파악을 위한 초기 무잠금 조회 (Non-locking Read)
        PayrollDto initial = payrollMapper.findById(payrollId);
        if (initial == null) {
            throw new IllegalArgumentException("存在しない給与データです。(ID: " + payrollId + ")");
        }
        Long initialStaffId = initial.getStoreStaffId();
        Long initialStoreId = initial.getStoreId();

        // [Step 2] 전역 락 계층 1단계: store_staff 부모 행 배타적 락 선점 (Level 1 Lock)
        payrollMapper.lockStoreStaffForUpdate(initialStaffId);

        // [Step 3] 전역 락 계층 2단계: payroll 자식 행 배타적 락 획득 (Level 2 Lock)
        PayrollDto existing = payrollMapper.findByIdForUpdate(payrollId);
        if (existing == null) {
            throw new IllegalArgumentException("存在しない給与データです。(ID: " + payrollId + ")");
        }

        // [Step 3-1] TOCTOU 방어: 잠금 후 재조회한 storeStaffId 및 storeId가 초기 조회값과 일치하는지 엄밀 검증
        if (!initialStaffId.equals(existing.getStoreStaffId())) {
            throw new IllegalStateException("給与レコードの職員所属情報が変更されたため、確定処理を中断しました。(初期: " +
                    initialStaffId + ", ロック後: " + existing.getStoreStaffId() + ")");
        }
        if (!initialStoreId.equals(existing.getStoreId())) {
            throw new IllegalStateException("給与レコードの店舗情報が変更されたため、確定処理を中断しました。(初期店舗: " +
                    initialStoreId + ", ロック後店舗: " + existing.getStoreId() + ")");
        }

        // [Step 3-2] 급여 상태 재검증 (이미 다른 관리자에 의해 확정되었는지 여부)
        if (!"DRAFT".equals(existing.getPayrollStatus())) {
            throw new IllegalStateException("既に確定済みの給与データです。(現在の状態: " + existing.getPayrollStatus() + ")");
        }

        // [Step 4] 정산 대상 기간 종료의 검증 (마감 기간 종료 전 조기 확정 방지)
        if (!LocalDate.now().isAfter(existing.getRangeEndDate())) {
            throw new IllegalStateException("精算対象期間 (" + existing.getRangeEndDate() + ") が終了する前に給与を確定することはできません。");
        }

        // [Step 5] 미처리 근태 확인 (수정 신청 대기 또는 타각 진행 중 차단)
        int unresolved = payrollMapper.countUnresolvedAttendance(existing.getStoreStaffId(), existing.getRangeStartDate(), existing.getRangeEndDate());
        if (unresolved > 0) {
            throw new IllegalStateException("未処理の勤怠修正申請または勤務中打刻が存在するため、確定できません。");
        }

        // [Step 6] 최신 데이터 기반 재계산 (동일 트랜잭션 내 store_staff 락 재진입 안전)
        PayrollDto recalculated = calculatePayrollForStaff(
                existing.getStoreId(),
                existing.getStoreStaffId(),
                existing.getTargetYearMonth(),
                existing.getRangeStartDate(),
                existing.getRangeEndDate()
        );

        // [Step 7] 확인 금액과 재계산 금액 불일치 검증
        if (confirmDto != null && confirmDto.getExpectedGrossPay() != null) {
            int expected = confirmDto.getExpectedGrossPay();
            int actual = recalculated.getGrossPay();
            if (expected != actual) {
                throw new IllegalStateException("確認時と金額が一致しません。(確認額: " + expected + "円, 再計算額: " + actual + "円)。再確認が必要です。");
            }
        }

        payrollMapper.updatePayrollStatusConfirm(payrollId, LocalDateTime.now());
    }

    /**
     * 店舗全体の月次給与一括確定バッチ処理（同月全レコード悲観的ロックおよび一括確定）
     *
     * @param storeId 対象店舗ID
     * @param targetYearMonth 対象年月 (YYYY-MM)
     * @return 確定された給与件数
     */
    @Transactional
    public int confirmMonthlyPayroll(Long storeId, String targetYearMonth) {
        // [Step 1] 확정 대상 DRAFT 급여를 보유한 직원 ID 목록을 결정론적 오름차순으로 1차 선별
        List<Long> draftStaffIds = payrollMapper.findDraftStaffIdsByStoreAndMonth(storeId, targetYearMonth);
        if (draftStaffIds == null || draftStaffIds.isEmpty()) {
            List<PayrollDto> allPayrolls = payrollMapper.findPayrollsByStoreAndMonth(storeId, targetYearMonth);
            if (allPayrolls == null || allPayrolls.isEmpty()) {
                throw new IllegalStateException("指定年月 (" + targetYearMonth + ") の給与データが存在しません。先に一括計算を実行してください。");
            } else {
                throw new IllegalStateException("指定年月 (" + targetYearMonth + ") の給与は全件確定済みです。");
            }
        }

        // [Step 2] 전역 락 계층 1단계: 선별된 직원 부모 행(store_staff)들을 ID 오름차순으로 순차 선점 잠금 (Level 1 Lock)
        for (Long staffId : draftStaffIds) {
            payrollMapper.lockStoreStaffForUpdate(staffId);
        }

        // [Step 3] 전역 락 계층 2단계: 부모 락이 확보된 안전 상태에서 점포의 당월 급여 행들을 배타 락(Level 2 Lock)으로 재조회
        List<PayrollDto> payrolls = payrollMapper.lockStorePayrollsForUpdate(storeId, targetYearMonth);
        if (payrolls == null || payrolls.isEmpty()) {
            throw new IllegalStateException("指定年月 (" + targetYearMonth + ") の給与データが存在しません。");
        }

        List<PayrollDto> draftPayrolls = payrolls.stream()
                .filter(p -> "DRAFT".equals(p.getPayrollStatus()))
                .toList();

        if (draftPayrolls.isEmpty()) {
            throw new IllegalStateException("指定年月 (" + targetYearMonth + ") の給与は全件確定済みです。");
        }

        // [Step 3-1] 동시성 경계 검증: 부모 락을 잡지 않은 새로운 직원의 DRAFT가 그 사이에 추가되었는지 감지
        for (PayrollDto p : draftPayrolls) {
            if (!draftStaffIds.contains(p.getStoreStaffId())) {
                throw new IllegalStateException("月次確定処理中に新たな職員の給与が生成されました。(職員ID: " +
                        p.getStoreStaffId() + ")。データの整合性を保つため再実行してください。");
            }
        }

        LocalDate today = LocalDate.now();

        // 締め期間終了および未処理勤怠の全件バリデーション
        for (PayrollDto p : draftPayrolls) {
            if (!today.isAfter(p.getRangeEndDate())) {
                throw new IllegalStateException("精算対象期間 (" + p.getRangeEndDate() + ") が終了する前に月次給与を一括確定することはできません。(対象スタッフID: " + p.getStoreStaffId() + ")");
            }

            int unresolved = payrollMapper.countUnresolvedAttendance(p.getStoreStaffId(), p.getRangeStartDate(), p.getRangeEndDate());
            if (unresolved > 0) {
                throw new IllegalStateException("未処理の勤怠修正申請または打刻が存在するため、月次給与を一括確定できません。(対象スタッフID: " + p.getStoreStaffId() + ")");
            }
        }

        // 店舗・対象年月の一括CONFIRMED更新 (CAS)
        int updatedCount = payrollMapper.updateAllPayrollStatusConfirm(storeId, targetYearMonth, LocalDateTime.now());
        return updatedCount;
    }

    /**
     * スタッフ個人別給与明細詳細モーダル用データ取得（認可検証付き）
     */
    public PayrollDetailResponseDto getPayrollDetail(Long payrollId, Long currentMemberId) {
        if (currentMemberId != null) {
            storeAuthorizationService.validatePayrollOwnership(payrollId, currentMemberId);
        }
        return getPayrollDetail(payrollId);
    }

    /**
     * スタッフ個人別給与明細詳細モーダル用データ取得
     */
    public PayrollDetailResponseDto getPayrollDetail(Long payrollId) {
        PayrollDto payroll = payrollMapper.findById(payrollId);
        if (payroll == null) {
            throw new IllegalArgumentException("存在しない給与データです。(ID: " + payrollId + ")");
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

            // 元勤怠データから打刻時刻情報を補完
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
     * 給与台帳 Excel エクスポート（労働基準法第108条準拠・SXSSFストリーミング）
     * 読取一貫性(Snapshot Isolation)担保のため、readOnlyトランザクションを適用
     */
    @Transactional(readOnly = true)
    public byte[] exportPayrollToExcel(Long storeId, String targetYearMonth) throws IOException {
        List<PayrollDto> payrolls = getPayrollsByStoreAndMonth(storeId, targetYearMonth);
        String storeName = payrollMapper.findStoreNameById(storeId);
        return PayrollExcelExporter.exportToExcel(targetYearMonth, storeName, payrolls);
    }
}
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
     * 月別給与一覧取得 (オーナー店舗所有権検証必須)
     */
    @GetMapping("/list")
    public ResponseEntity<ApiResponse<List<PayrollDto>>> getPayrollList(
            @RequestParam("storeId") Long storeId,
            @RequestParam("yearMonth") String yearMonth,
            @AuthenticationPrincipal CustomUserDetails userDetails
    ) {
        storeAuthorizationService.validateStoreOwnership(storeId, userDetails.getMemberId());
        List<PayrollDto> list = payrollService.getPayrollsByStoreAndMonth(storeId, yearMonth);
        return ResponseEntity.ok(ApiResponse.ok("給与一覧を取得しました。", list));
    }

    /**
     * 月別給与一括計算 (Upsert, オーナー店舗所有権検証必須)
     */
    @PostMapping("/calculate")
    public ResponseEntity<ApiResponse<Void>> calculateMonthlyPayroll(
            @RequestBody PayrollCalculateRequestDto requestDto,
            @AuthenticationPrincipal CustomUserDetails userDetails
    ) {
        storeAuthorizationService.validateStoreOwnership(requestDto.getStoreId(), userDetails.getMemberId());
        payrollService.calculateMonthlyPayroll(requestDto.getStoreId(), requestDto.getTargetYearMonth());
        return ResponseEntity.ok(ApiResponse.ok("給与計算が完了しました。", null));
    }

    /**
     * 店舗全体の月次給与一括確定 (排他悲観的ロック適用・帳簿ロック, オーナー店舗所有権検証必須)
     */
    @PostMapping("/confirm-month")
    public ResponseEntity<ApiResponse<Integer>> confirmMonthlyPayroll(
            @RequestBody PayrollCalculateRequestDto requestDto,
            @AuthenticationPrincipal CustomUserDetails userDetails
    ) {
        storeAuthorizationService.validateStoreOwnership(requestDto.getStoreId(), userDetails.getMemberId());
        int confirmedCount = payrollService.confirmMonthlyPayroll(requestDto.getStoreId(), requestDto.getTargetYearMonth());
        return ResponseEntity.ok(ApiResponse.ok("月次給与の一括確定が完了しました。(確定件数: " + confirmedCount + "件)", confirmedCount));
    }

    /**
     * 給与確定処理 (帳簿ロック・排他制御適用, 給与データ所有権検証必須)
     */
    @PostMapping("/{payrollId}/confirm")
    public ResponseEntity<ApiResponse<Void>> confirmPayroll(
            @PathVariable("payrollId") Long payrollId,
            @RequestBody(required = false) PayrollConfirmRequestDto confirmDto,
            @AuthenticationPrincipal CustomUserDetails userDetails
    ) {
        storeAuthorizationService.validatePayrollOwnership(payrollId, userDetails.getMemberId());
        payrollService.confirmPayroll(payrollId, confirmDto);
        return ResponseEntity.ok(ApiResponse.ok("給与が確定されました。", null));
    }

    /**
     * スタッフ個人別給与明細詳細取得 (給与データ所有権検証必須)
     */
    @GetMapping("/{payrollId}/detail")
    public ResponseEntity<ApiResponse<PayrollDetailResponseDto>> getPayrollDetail(
            @PathVariable("payrollId") Long payrollId,
            @AuthenticationPrincipal CustomUserDetails userDetails
    ) {
        storeAuthorizationService.validatePayrollOwnership(payrollId, userDetails.getMemberId());
        PayrollDetailResponseDto detail = payrollService.getPayrollDetail(payrollId, userDetails.getMemberId());
        return ResponseEntity.ok(ApiResponse.ok("給与明細詳細を取得しました。", detail));
    }

    /**
     * 給与台帳 Excel エクスポート (オーナー店舗所有権検証必須)
     */
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
}
package com.example.kintai.domain.payroll.util;

import com.example.kintai.domain.payroll.dto.PayrollDto;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 給与台帳 (Payroll Ledger) Excel エクスポートユーティリティ単体テスト
 */
class PayrollExcelExporterTest {

    @Nested
    @DisplayName("給与データ出力正常系テスト")
    class ExportSuccessTests {

        @Test
        @DisplayName("複数件の給与データおよびヘッダー・要約数式行が正しく生成されること")
        void testExportToExcelWithData() throws IOException {
            List<PayrollDto> list = new ArrayList<>();

            PayrollDto p1 = new PayrollDto();
            p1.setStoreStaffId(101L);
            p1.setStaffName("田中 太郎");
            p1.setPayrollStatus("CONFIRMED");
            p1.setWorkDaysCount(20);
            p1.setTotalWorkMinutes(9600); // 160時間
            p1.setTotalNightMinutes(600);  // 10時間
            p1.setTotalOvertimeMinutes(1200); // 20時間
            p1.setAppliedHourlyWage(1200);
            p1.setBasePay(192000);
            p1.setNightAllowance(3000);
            p1.setOvertimeAllowance(30000);
            p1.setTransportAllowance(10000);
            p1.setGrossPay(235000);
            list.add(p1);

            PayrollDto p2 = new PayrollDto();
            p2.setStoreStaffId(102L);
            p2.setStaffName("佐藤 花子");
            p2.setPayrollStatus("DRAFT");
            p2.setWorkDaysCount(15);
            p2.setTotalWorkMinutes(7200);
            p2.setTotalNightMinutes(0);
            p2.setTotalOvertimeMinutes(0);
            p2.setAppliedHourlyWage(1150);
            p2.setBasePay(138000);
            p2.setNightAllowance(0);
            p2.setOvertimeAllowance(0);
            p2.setTransportAllowance(5000);
            p2.setGrossPay(143000);
            list.add(p2);

            byte[] excelBytes = PayrollExcelExporter.exportToExcel("2026-11", "渋谷道玄坂店", list);
            assertNotNull(excelBytes);
            assertTrue(excelBytes.length > 0);

            // XSSFWorkbook で読み込み検証
            try (Workbook workbook = new XSSFWorkbook(new ByteArrayInputStream(excelBytes))) {
                assertEquals(1, workbook.getNumberOfSheets());
                Sheet sheet = workbook.getSheetAt(0);
                assertEquals("給与台帳_202611", sheet.getSheetName());

                // タイトル行検証
                Row titleRow = sheet.getRow(0);
                assertNotNull(titleRow);
                assertEquals("月次給与台帳 (賃金台帳)", titleRow.getCell(0).getStringCellValue());

                // 店舗名・対象年月行検証
                Row metaRow1 = sheet.getRow(1);
                assertEquals("対象年月:", metaRow1.getCell(0).getStringCellValue());
                assertEquals("2026-11", metaRow1.getCell(1).getStringCellValue());
                assertEquals("対象店舗:", metaRow1.getCell(3).getStringCellValue());
                assertEquals("渋谷道玄坂店", metaRow1.getCell(4).getStringCellValue());

                // テーブルヘッダー行検証 (Row 4)
                Row headerRow = sheet.getRow(4);
                assertEquals("No.", headerRow.getCell(0).getStringCellValue());
                assertEquals("スタッフID", headerRow.getCell(1).getStringCellValue());
                assertEquals("氏名", headerRow.getCell(2).getStringCellValue());
                assertEquals("確定状態", headerRow.getCell(3).getStringCellValue());
                assertEquals("総支給額", headerRow.getCell(13).getStringCellValue());

                // データ行検証 (Row 5: 田中 太郎)
                Row row1 = sheet.getRow(5);
                assertEquals(1, (int) row1.getCell(0).getNumericCellValue());
                assertEquals(101, (long) row1.getCell(1).getNumericCellValue());
                assertEquals("田中 太郎", row1.getCell(2).getStringCellValue());
                assertEquals("確定", row1.getCell(3).getStringCellValue());
                assertEquals(20, (int) row1.getCell(4).getNumericCellValue());
                assertEquals(9600, (int) row1.getCell(5).getNumericCellValue());
                assertEquals(235000, (int) row1.getCell(13).getNumericCellValue());

                // データ行検証 (Row 6: 佐藤 花子)
                Row row2 = sheet.getRow(6);
                assertEquals(2, (int) row2.getCell(0).getNumericCellValue());
                assertEquals(102, (long) row2.getCell(1).getNumericCellValue());
                assertEquals("佐藤 花子", row2.getCell(2).getStringCellValue());
                assertEquals("未確定", row2.getCell(3).getStringCellValue());
                assertEquals(143000, (int) row2.getCell(13).getNumericCellValue());

                // 合計行検証 (Row 7: 総合計)
                Row totalRow = sheet.getRow(7);
                assertEquals("総合計", totalRow.getCell(0).getStringCellValue());
                assertEquals("SUM(E6:E7)", totalRow.getCell(4).getCellFormula());
                assertEquals("SUM(F6:F7)", totalRow.getCell(5).getCellFormula());
                assertEquals("SUM(N6:N7)", totalRow.getCell(13).getCellFormula());

                // 平均行検証 (Row 8: 1人当たり平均)
                Row avgRow = sheet.getRow(8);
                assertEquals("1人当たり平均", avgRow.getCell(0).getStringCellValue());
                assertEquals("AVERAGE(E6:E7)", avgRow.getCell(4).getCellFormula());
                assertEquals("AVERAGE(N6:N7)", avgRow.getCell(13).getCellFormula());
            }
        }

        @Test
        @DisplayName("データ件数0件の時も例外なく安全に案内行が出力されること")
        void testExportToExcelEmptyData() throws IOException {
            byte[] excelBytes = PayrollExcelExporter.exportToExcel("2026-11", "新宿店", Collections.emptyList());
            assertNotNull(excelBytes);
            assertTrue(excelBytes.length > 0);

            try (Workbook workbook = new XSSFWorkbook(new ByteArrayInputStream(excelBytes))) {
                Sheet sheet = workbook.getSheetAt(0);
                Row emptyRow = sheet.getRow(5);
                assertNotNull(emptyRow);
                assertEquals("該当する給与データが存在しません。", emptyRow.getCell(0).getStringCellValue());
                // 数式行が存在しないこと
                assertNull(sheet.getRow(6));
            }
        }

        @Test
        @DisplayName("ストリーム直接出力 (exportToStream) が正常に動作すること")
        void testExportToStream() throws IOException {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            PayrollExcelExporter.exportToStream("2026-12", "池袋店", Collections.emptyList(), out);
            byte[] bytes = out.toByteArray();
            assertTrue(bytes.length > 0);

            try (Workbook workbook = new XSSFWorkbook(new ByteArrayInputStream(bytes))) {
                assertEquals("給与台帳_202612", workbook.getSheetAt(0).getSheetName());
            }
        }
    }

    @Nested
    @DisplayName("大容量データストリーミングおよびウィンドウローリング検証")
    class LargeDataStreamingTests {

        @Test
        @DisplayName("ウィンドウサイズ(100行)を超える大量データ(250行)でもOOMなくストリーミング出力され、要約数式が整合すること")
        void testLargeDataExport() throws IOException {
            int staffCount = 250;
            List<PayrollDto> list = new ArrayList<>(staffCount);

            for (int i = 1; i <= staffCount; i++) {
                PayrollDto p = new PayrollDto();
                p.setStoreStaffId((long) (1000 + i));
                p.setStaffName("従業員_" + i);
                p.setPayrollStatus("CONFIRMED");
                p.setWorkDaysCount(21);
                p.setTotalWorkMinutes(10080);
                p.setTotalNightMinutes(300);
                p.setTotalOvertimeMinutes(600);
                p.setAppliedHourlyWage(1300);
                p.setBasePay(218400);
                p.setNightAllowance(1625);
                p.setOvertimeAllowance(16250);
                p.setTransportAllowance(15000);
                p.setGrossPay(251275);
                list.add(p);
            }

            byte[] excelBytes = PayrollExcelExporter.exportToExcel("2026-11", "大規模チェーン全店", list);
            assertNotNull(excelBytes);

            try (Workbook workbook = new XSSFWorkbook(new ByteArrayInputStream(excelBytes))) {
                Sheet sheet = workbook.getSheetAt(0);
                // データ開始行: 5 (Excel 6行目), 最終データ行: 5 + 250 - 1 = 254 (Excel 255行目)
                Row lastDataRow = sheet.getRow(254);
                assertNotNull(lastDataRow);
                assertEquals("従業員_250", lastDataRow.getCell(2).getStringCellValue());

                // 合計行: 255 (Excel 256行目)
                Row totalRow = sheet.getRow(255);
                assertNotNull(totalRow);
                assertEquals("SUM(N6:N255)", totalRow.getCell(13).getCellFormula());

                // 平均行: 256 (Excel 257行目)
                Row avgRow = sheet.getRow(256);
                assertNotNull(avgRow);
                assertEquals("AVERAGE(N6:N255)", avgRow.getCell(13).getCellFormula());
            }
        }
    }
}
