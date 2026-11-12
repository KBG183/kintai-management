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
