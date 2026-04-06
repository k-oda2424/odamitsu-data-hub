package jp.co.oda32.domain.service.finance;

import jp.co.oda32.domain.model.finance.TInvoice;
import jp.co.oda32.domain.repository.finance.TInvoiceRepository;
import jp.co.oda32.dto.finance.InvoiceImportResult;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;

import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class InvoiceImportServiceTest {

    @Mock
    private TInvoiceRepository tInvoiceRepository;

    @InjectMocks
    private InvoiceImportService service;

    @Nested
    class ParseClosingDateTest {

        @Test
        void monthEnd_november() {
            Row row = createRow2("2025年11月30日締 今回請求分");
            assertEquals("2025/11/末", service.parseClosingDate(row));
        }

        @Test
        void nonMonthEnd_july20() {
            Row row = createRow2("2025年 7月20日締 今回請求分");
            assertEquals("2025/07/20", service.parseClosingDate(row));
        }

        @Test
        void february28_nonLeapYear() {
            Row row = createRow2("2025年 2月28日締 今回請求分");
            assertEquals("2025/02/末", service.parseClosingDate(row));
        }

        @Test
        void february29_leapYear() {
            Row row = createRow2("2024年 2月29日締 今回請求分");
            assertEquals("2024/02/末", service.parseClosingDate(row));
        }

        @Test
        void december31() {
            Row row = createRow2("2025年12月31日締 今回請求分");
            assertEquals("2025/12/末", service.parseClosingDate(row));
        }

        @Test
        void fullWidthDigits_nfkcNormalized() {
            Row row = createRow2("２０２５年１１月３０日締　今回請求分");
            assertEquals("2025/11/末", service.parseClosingDate(row));
        }

        @Test
        void nullRow_throwsException() {
            assertThrows(IllegalArgumentException.class, () -> service.parseClosingDate(null));
        }

        @Test
        void unparseable_throwsException() {
            Row row = createRow2("不正な文字列");
            assertThrows(IllegalArgumentException.class, () -> service.parseClosingDate(row));
        }
    }

    @Nested
    class ConvertPartnerCodeTest {

        @Test
        void numericCode_zeroPadded() {
            assertEquals("000029", service.convertPartnerCode("29"));
        }

        @Test
        void sixDigitCode_unchanged() {
            assertEquals("009896", service.convertPartnerCode("009896"));
        }

        @Test
        void angleBrackets_removed() {
            assertEquals("009896", service.convertPartnerCode("<009896>"));
        }

        @Test
        void code999999() {
            assertEquals("999999", service.convertPartnerCode("999999"));
        }

        @Test
        void threeDigitCode() {
            assertEquals("000181", service.convertPartnerCode("181"));
        }

        @Test
        void nonNumericCode_throwsException() {
            assertThrows(IllegalArgumentException.class, () -> service.convertPartnerCode("ABC"));
        }
    }

    @Nested
    class ImportFromExcelTest {

        @Test
        void division1_basicImport() throws Exception {
            byte[] excelBytes = createTestExcel("2026年 2月28日締 今回請求分", List.of(
                    new TestRow(14, "五日市 あかり園", 0, 0, 0, 36960, 3696, 40656, 40656),
                    new TestRow(30, "イズミテクノ", 85509, 0, 85509, 48048, 4804, 52852, 138361)
            ));

            MockMultipartFile file = new MockMultipartFile(
                    "file", "請求実績20260228分.xlsx",
                    "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                    excelBytes);

            when(tInvoiceRepository.findAll(any(org.springframework.data.jpa.domain.Specification.class)))
                    .thenReturn(List.of());
            when(tInvoiceRepository.saveAll(anyList())).thenAnswer(inv -> inv.getArgument(0));

            InvoiceImportResult result = service.importFromExcel(file, null);

            assertEquals("2026/02/末", result.getClosingDate());
            assertEquals(1, result.getShopNo());
            assertEquals(2, result.getTotalRows());
            assertEquals(2, result.getInsertedRows());
            assertEquals(0, result.getUpdatedRows());
            assertEquals(0, result.getSkippedRows());

            @SuppressWarnings("unchecked")
            ArgumentCaptor<List<TInvoice>> captor = ArgumentCaptor.forClass(List.class);
            verify(tInvoiceRepository).saveAll(captor.capture());
            List<TInvoice> saved = captor.getValue();
            assertEquals(2, saved.size());
            assertEquals("000014", saved.get(0).getPartnerCode());
            assertEquals("五日市 あかり園", saved.get(0).getPartnerName());
            assertEquals("2026/02/末", saved.get(0).getClosingDate());
            assertEquals(new BigDecimal("36960"), saved.get(0).getNetSales());
        }

        @Test
        void division2_matsuyama_shopNo2() throws Exception {
            byte[] excelBytes = createTestExcel("2026年 2月28日締 今回請求分", List.of(
                    new TestRow(181, "岩木屋商店", 37125, 36575, 0, 203165, 20316, 223481, 223481)
            ));

            MockMultipartFile file = new MockMultipartFile(
                    "file", "請求実績20260228分松山.xlsx",
                    "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                    excelBytes);

            when(tInvoiceRepository.findAll(any(org.springframework.data.jpa.domain.Specification.class)))
                    .thenReturn(List.of());
            when(tInvoiceRepository.saveAll(anyList())).thenAnswer(inv -> inv.getArgument(0));

            InvoiceImportResult result = service.importFromExcel(file, null);

            assertEquals(2, result.getShopNo());
            assertEquals(1, result.getInsertedRows());
        }

        @Test
        void matsuyama_999999_skipped() throws Exception {
            byte[] excelBytes = createTestExcel("2026年 2月28日締 今回請求分", List.of(
                    new TestRow(181, "岩木屋商店", 37125, 36575, 0, 203165, 20316, 223481, 223481),
                    new TestRow(999999, null, 27665, 116365, -88700, 89090, 8025, 97115, 8415)
            ));

            MockMultipartFile file = new MockMultipartFile(
                    "file", "請求実績20260228分松山.xlsx",
                    "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                    excelBytes);

            when(tInvoiceRepository.findAll(any(org.springframework.data.jpa.domain.Specification.class)))
                    .thenReturn(List.of());
            when(tInvoiceRepository.saveAll(anyList())).thenAnswer(inv -> inv.getArgument(0));

            InvoiceImportResult result = service.importFromExcel(file, null);

            assertEquals(2, result.getTotalRows());
            assertEquals(1, result.getInsertedRows());
            assertEquals(1, result.getSkippedRows());
        }

        @Test
        void division1_999999_imported_as_uezama() throws Exception {
            byte[] excelBytes = createTestExcel("2025年11月30日締 今回請求分", List.of(
                    new TestRow(999999, null, 19375, 96015, -76640, 70150, 6490, 76640, 0)
            ));

            MockMultipartFile file = new MockMultipartFile(
                    "file", "請求実績20251130分.xlsx",
                    "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                    excelBytes);

            when(tInvoiceRepository.findAll(any(org.springframework.data.jpa.domain.Specification.class)))
                    .thenReturn(List.of());
            when(tInvoiceRepository.saveAll(anyList())).thenAnswer(inv -> inv.getArgument(0));

            InvoiceImportResult result = service.importFromExcel(file, null);

            assertEquals(1, result.getShopNo());
            assertEquals(1, result.getInsertedRows());
            assertEquals(0, result.getSkippedRows());

            @SuppressWarnings("unchecked")
            ArgumentCaptor<List<TInvoice>> captor = ArgumentCaptor.forClass(List.class);
            verify(tInvoiceRepository).saveAll(captor.capture());
            assertEquals("999999", captor.getValue().get(0).getPartnerCode());
            assertEquals("上様", captor.getValue().get(0).getPartnerName());
        }

        @Test
        void upsert_existingRecord_updated_paymentDatePreserved() throws Exception {
            byte[] excelBytes = createTestExcel("2026年 2月28日締 今回請求分", List.of(
                    new TestRow(14, "五日市 あかり園", 0, 0, 0, 50000, 5000, 55000, 55000)
            ));

            MockMultipartFile file = new MockMultipartFile(
                    "file", "請求実績20260228分.xlsx",
                    "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                    excelBytes);

            TInvoice existingInvoice = TInvoice.builder()
                    .invoiceId(100)
                    .partnerCode("000014")
                    .partnerName("五日市 あかり園")
                    .closingDate("2026/02/末")
                    .previousBalance(BigDecimal.ZERO)
                    .totalPayment(BigDecimal.ZERO)
                    .carryOverBalance(BigDecimal.ZERO)
                    .netSales(new BigDecimal("36960"))
                    .taxPrice(new BigDecimal("3696"))
                    .netSalesIncludingTax(new BigDecimal("40656"))
                    .currentBillingAmount(new BigDecimal("40656"))
                    .shopNo(1)
                    .paymentDate(java.time.LocalDate.of(2026, 3, 15))
                    .build();

            when(tInvoiceRepository.findAll(any(org.springframework.data.jpa.domain.Specification.class)))
                    .thenReturn(List.of(existingInvoice));
            when(tInvoiceRepository.saveAll(anyList())).thenAnswer(inv -> inv.getArgument(0));

            InvoiceImportResult result = service.importFromExcel(file, null);

            assertEquals(0, result.getInsertedRows());
            assertEquals(1, result.getUpdatedRows());

            @SuppressWarnings("unchecked")
            ArgumentCaptor<List<TInvoice>> captor = ArgumentCaptor.forClass(List.class);
            verify(tInvoiceRepository).saveAll(captor.capture());
            TInvoice updated = captor.getValue().get(0);
            assertEquals(new BigDecimal("50000"), updated.getNetSales());
            assertEquals(java.time.LocalDate.of(2026, 3, 15), updated.getPaymentDate());
        }

        @Test
        void invalidExtension_throwsException() {
            MockMultipartFile file = new MockMultipartFile(
                    "file", "test.csv", "text/csv", new byte[0]);

            assertThrows(IllegalArgumentException.class, () -> service.importFromExcel(file, null));
        }

        @Test
        void nullFilename_throwsException() {
            MockMultipartFile file = new MockMultipartFile(
                    "file", null, "application/octet-stream", new byte[0]);

            assertThrows(IllegalArgumentException.class, () -> service.importFromExcel(file, null));
        }

        @Test
        void totalRow_skipped() throws Exception {
            byte[] excelBytes = createTestExcelWithTotal("2026年 2月28日締 今回請求分", List.of(
                    new TestRow(14, "五日市 あかり園", 0, 0, 0, 36960, 3696, 40656, 40656)
            ));

            MockMultipartFile file = new MockMultipartFile(
                    "file", "請求実績20260228分.xlsx",
                    "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                    excelBytes);

            when(tInvoiceRepository.findAll(any(org.springframework.data.jpa.domain.Specification.class)))
                    .thenReturn(List.of());
            when(tInvoiceRepository.saveAll(anyList())).thenAnswer(inv -> inv.getArgument(0));

            InvoiceImportResult result = service.importFromExcel(file, null);

            assertEquals(1, result.getTotalRows());
            assertEquals(1, result.getInsertedRows());
        }
    }

    // --- Helper methods ---

    private record TestRow(int code, String name, long prevBalance, long totalPayment,
                           long carryOver, long netSales, long tax, long netSalesInclTax,
                           long currentBilling) {}

    private Row createRow2(String titleText) {
        Workbook wb = new XSSFWorkbook();
        Sheet sheet = wb.createSheet();
        Row row = sheet.createRow(0);
        row.createCell(0).setCellValue(titleText);
        return row;
    }

    private byte[] createTestExcel(String title, List<TestRow> dataRows) throws Exception {
        return createTestExcelInternal(title, dataRows, false);
    }

    private byte[] createTestExcelWithTotal(String title, List<TestRow> dataRows) throws Exception {
        return createTestExcelInternal(title, dataRows, true);
    }

    private byte[] createTestExcelInternal(String title, List<TestRow> dataRows, boolean addTotal) throws Exception {
        try (Workbook wb = new XSSFWorkbook()) {
            Sheet sheet = wb.createSheet("Sheet1");

            // Row1: title
            sheet.createRow(0).createCell(0).setCellValue("請求一覧表");
            // Row2: closing date source
            sheet.createRow(1).createCell(0).setCellValue(title);
            // Row3: sub-header
            sheet.createRow(2);
            // Row4: header
            sheet.createRow(3);

            // Data rows (starting at row index 4)
            int rowIdx = 4;
            for (TestRow tr : dataRows) {
                Row row = sheet.createRow(rowIdx++);
                row.createCell(0).setCellValue(tr.code());
                if (tr.name() != null) {
                    row.createCell(1).setCellValue(tr.name());
                }
                row.createCell(5).setCellValue(tr.prevBalance());
                row.createCell(6).setCellValue(tr.totalPayment());
                row.createCell(8).setCellValue(tr.carryOver());
                row.createCell(9).setCellValue(tr.netSales());
                row.createCell(10).setCellValue(tr.tax());
                row.createCell(11).setCellValue(tr.netSalesInclTax());
                row.createCell(12).setCellValue(tr.currentBilling());
            }

            if (addTotal) {
                Row totalRow = sheet.createRow(rowIdx);
                totalRow.createCell(4).setCellValue("【総合計】");
                totalRow.createCell(5).setCellValue(99999);
            }

            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            wb.write(bos);
            return bos.toByteArray();
        }
    }
}
