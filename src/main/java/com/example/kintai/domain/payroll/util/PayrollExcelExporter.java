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
