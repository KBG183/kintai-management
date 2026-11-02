package com.example.kintai.domain.payroll.util;

import com.example.kintai.domain.payroll.dto.PayrollDto;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.List;

/**
 * 給与台帳 (Payroll Ledger) Excel エクスポートユーティリティ
 */
public final class PayrollExcelExporter {

    private PayrollExcelExporter() {
    }

    public static byte[] exportToExcel(String targetYearMonth, List<PayrollDto> payrollList) throws IOException {
        try (Workbook workbook = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sheet = workbook.createSheet("給与台帳_" + targetYearMonth);

            // 헤더 스타일 설정
            CellStyle headerStyle = workbook.createCellStyle();
            Font headerFont = workbook.createFont();
            headerFont.setBold(true);
            headerFont.setColor(IndexedColors.WHITE.getIndex());
            headerStyle.setFont(headerFont);
            headerStyle.setFillForegroundColor(IndexedColors.DARK_BLUE.getIndex());
            headerStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            headerStyle.setAlignment(HorizontalAlignment.CENTER);
            headerStyle.setVerticalAlignment(VerticalAlignment.CENTER);

            // 숫자 (금액) 스타일
            CellStyle currencyStyle = workbook.createCellStyle();
            DataFormat dataFormat = workbook.createDataFormat();
            currencyStyle.setDataFormat(dataFormat.getFormat("#,##0\"円\""));
            currencyStyle.setAlignment(HorizontalAlignment.RIGHT);

            // 일반 숫자 (시간/일수) 스타일
            CellStyle numberStyle = workbook.createCellStyle();
            numberStyle.setAlignment(HorizontalAlignment.RIGHT);

            // 중앙 정렬 스타일
            CellStyle centerStyle = workbook.createCellStyle();
            centerStyle.setAlignment(HorizontalAlignment.CENTER);

            // 1. 헤더 생성
            String[] headers = {
                    "氏名", "状態", "勤務日数", "総労働時間(分)", "深夜労働(分)", 
                    "残業(分)", "基本給", "深夜手当", "残業手当", "通勤手当", "総支給額"
            };

            Row headerRow = sheet.createRow(0);
            headerRow.setHeightInPoints(24);
            for (int i = 0; i < headers.length; i++) {
                Cell cell = headerRow.createCell(i);
                cell.setCellValue(headers[i]);
                cell.setCellStyle(headerStyle);
            }

            // 2. 데이터 행 작성
            int rowIdx = 1;
            for (PayrollDto p : payrollList) {
                Row row = sheet.createRow(rowIdx++);

                // 氏名
                Cell c0 = row.createCell(0);
                c0.setCellValue(p.getStaffName() != null ? p.getStaffName() : "-");

                // 状態 (CONFIRMED -> 確定, DRAFT -> 未確定)
                Cell c1 = row.createCell(1);
                c1.setCellValue("CONFIRMED".equals(p.getPayrollStatus()) ? "確定" : "未確定(DRAFT)");
                c1.setCellStyle(centerStyle);

                // 勤務日数
                Cell c2 = row.createCell(2);
                c2.setCellValue(p.getWorkDaysCount() != null ? p.getWorkDaysCount() : 0);
                c2.setCellStyle(numberStyle);

                // 総労働時間(分)
                Cell c3 = row.createCell(3);
                c3.setCellValue(p.getTotalWorkMinutes() != null ? p.getTotalWorkMinutes() : 0);
                c3.setCellStyle(numberStyle);

                // 深夜労働(分)
                Cell c4 = row.createCell(4);
                c4.setCellValue(p.getTotalNightMinutes() != null ? p.getTotalNightMinutes() : 0);
                c4.setCellStyle(numberStyle);

                // 残業(分)
                Cell c5 = row.createCell(5);
                c5.setCellValue(p.getTotalOvertimeMinutes() != null ? p.getTotalOvertimeMinutes() : 0);
                c5.setCellStyle(numberStyle);

                // 基本給
                Cell c6 = row.createCell(6);
                c6.setCellValue(p.getBasePay() != null ? p.getBasePay() : 0);
                c6.setCellStyle(currencyStyle);

                // 深夜手当
                Cell c7 = row.createCell(7);
                c7.setCellValue(p.getNightAllowance() != null ? p.getNightAllowance() : 0);
                c7.setCellStyle(currencyStyle);

                // 残業手当
                Cell c8 = row.createCell(8);
                c8.setCellValue(p.getOvertimeAllowance() != null ? p.getOvertimeAllowance() : 0);
                c8.setCellStyle(currencyStyle);

                // 通勤手当
                Cell c9 = row.createCell(9);
                c9.setCellValue(p.getTransportAllowance() != null ? p.getTransportAllowance() : 0);
                c9.setCellStyle(currencyStyle);

                // 総支給額
                Cell c10 = row.createCell(10);
                c10.setCellValue(p.getGrossPay() != null ? p.getGrossPay() : 0);
                c10.setCellStyle(currencyStyle);
            }

            // 열 너비 자동 조정
            for (int i = 0; i < headers.length; i++) {
                sheet.autoSizeColumn(i);
                sheet.setColumnWidth(i, Math.max(sheet.getColumnWidth(i), 3500));
            }

            workbook.write(out);
            return out.toByteArray();
        }
    }
}
